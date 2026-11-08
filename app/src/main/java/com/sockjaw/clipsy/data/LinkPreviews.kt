package com.sockjaw.clipsy.data

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds out what a saved link points at (title, description, share image, whether it's an
 * image) and remembers it. Nothing is fetched while the setting is off, and never for locked
 * snippets: callers only pass links from ordinary text snippets.
 */
class LinkPreviews(
    private val context: Context,
    db: ClipsyDatabase,
    private val settings: SettingsStore,
    private val repository: SnippetRepository,
) {
    private val dao = db.linkPreviewDao()
    private val gate = Semaphore(3)
    private val locks = ConcurrentHashMap<String, Mutex>()

    val enabled: Boolean get() = settings.settings.value.linkPreviews

    /** The key a link is stored under: trimmed, with a scheme. */
    fun key(link: String): String = Links.withScheme(link.trim())

    fun observe(link: String): Flow<LinkPreview?> = dao.observe(key(link))

    /** Every saved preview image, by link, so lists and the grid can show them without asking one by one. */
    val images: Flow<Map<String, String>> =
        dao.observeWithImages().map { list -> list.associate { it.url to it.imagePath!! } }

    suspend fun cached(link: String): LinkPreview? = dao.get(key(link))

    /**
     * The preview for [link]: from the cache when it's recent, otherwise from the web. Null when
     * previews are off. A failed look is kept for a few minutes so a dead link isn't retried on every screen.
     */
    suspend fun load(link: String, force: Boolean = false): LinkPreview? {
        if (!enabled) return null
        val key = key(link)
        return locks.getOrPut(key) { Mutex() }.withLock {
            val old = dao.get(key)
            val age = System.currentTimeMillis() - (old?.fetchedAt ?: 0)
            val fresh = old != null && when (old.status) {
                PreviewStatus.OK -> age < FRESH_MS
                PreviewStatus.FAILED -> age < RETRY_MS
            }
            if (fresh && !force) return@withLock old
            val made = withContext(Dispatchers.IO) { gate.withPermit { fetch(key, old) } }
            dao.put(made)
            made
        }
    }

    suspend fun keepAsLink(link: String) {
        val key = key(link)
        if (dao.get(key) == null) dao.put(LinkPreview(url = key, kind = LinkKind.IMAGE))
        dao.setKeepAsLink(key, true)
    }

    private fun fetch(key: String, old: LinkPreview?): LinkPreview {
        val host = Links.host(key)
        val page = runCatching { LinkFetcher.fetch(key) }.getOrElse {
            // Keep what we knew if a refresh fails; only a first look becomes "failed".
            return old?.copy(fetchedAt = System.currentTimeMillis()) ?: LinkPreview(url = key, status = PreviewStatus.FAILED)
        }
        val keep = old?.keepAsLink ?: false
        val mime = page.mime
        return when {
            mime != null && mime.startsWith("image/") -> {
                val file = cacheFile(page.finalUrl)
                val ok = file.exists() && isImage(file) || (LinkFetcher.download(page.finalUrl, file, MAX_PREVIEW_BYTES) != null && isImage(file))
                if (!ok) file.delete()
                LinkPreview(
                    url = key, kind = LinkKind.IMAGE, title = LinkMetadata.fileName(page.finalUrl) ?: host, siteName = host,
                    imageUrl = page.finalUrl, imagePath = file.takeIf { ok }?.absolutePath, mimeType = mime, sizeBytes = page.size, keepAsLink = keep,
                )
            }
            page.html != null -> {
                val parsed = LinkMetadata.parse(page.html, page.finalUrl)
                val image = parsed.imageUrl?.let { u ->
                    val file = cacheFile(u)
                    if ((file.exists() && isImage(file)) || (LinkFetcher.download(u, file, MAX_PREVIEW_BYTES) != null && isImage(file))) file else { file.delete(); null }
                }
                LinkPreview(
                    url = key, kind = LinkKind.PAGE, title = parsed.title ?: host, description = parsed.description ?: parsed.excerpt,
                    siteName = parsed.siteName ?: host, imageUrl = parsed.imageUrl, imagePath = image?.absolutePath, mimeType = mime, keepAsLink = keep,
                )
            }
            else -> LinkPreview(
                url = key, kind = LinkKind.FILE, title = LinkMetadata.fileName(page.finalUrl) ?: host, siteName = host,
                mimeType = mime, sizeBytes = page.size, keepAsLink = keep,
            )
        }
    }

    /**
     * Downloads an image link and stores it as a media snippet. [template] carries the name, keyword,
     * folder and so on; with an id it updates that snippet in place (a saved link becomes media), without
     * one it creates a new snippet. Returns the snippet's id, or null if the image couldn't be fetched.
     */
    suspend fun saveAsMedia(template: Snippet, preview: LinkPreview): Long? = withContext(Dispatchers.IO) {
        val source = preview.imageUrl ?: preview.url
        val dir = File(context.filesDir, "snippets").apply { mkdirs() }
        val temp = File(dir, "${UUID.randomUUID()}.download")
        val mime = LinkFetcher.download(source, temp, MAX_MEDIA_BYTES)
        if (mime == null || !isImage(temp)) { temp.delete(); return@withContext null }
        val name = withExtension(LinkMetadata.fileName(source)?.takeIf { it.isNotBlank() } ?: "image", mime)
        val dest = File(dir, "${UUID.randomUUID()}_${name.replace('/', '_')}")
        if (!temp.renameTo(dest)) { temp.delete(); return@withContext null }
        val id = repository.save(
            template.copy(
                type = SnippetType.MEDIA, content = "", filePath = dest.absolutePath, mimeType = mime, sizeBytes = dest.length(), fileMode = FileMode.PASTE,
            ),
        )
        repository.get(id)?.let { Thumbnails.ensure(context, it) }
        id
    }

    private fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "link_previews").apply { mkdirs() }, "$hash.img")
    }

    private fun isImage(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private companion object {
        const val FRESH_MS = 14L * 24 * 60 * 60 * 1000
        const val RETRY_MS = 10L * 60 * 1000
        const val MAX_PREVIEW_BYTES = 4L * 1024 * 1024
        const val MAX_MEDIA_BYTES = 25L * 1024 * 1024
    }
}
