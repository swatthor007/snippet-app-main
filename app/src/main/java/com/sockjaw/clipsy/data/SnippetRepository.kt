package com.sockjaw.clipsy.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.room.withTransaction
import com.sockjaw.clipsy.data.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** [name] with an extension for [mime] added when it has none, e.g. ("photo", "image/jpeg") -> "photo.jpg". */
internal fun withExtension(name: String, mime: String?): String {
    if (name.substringAfterLast('/').contains('.')) return name
    val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: return name
    return "$name.$ext"
}

@Serializable
private data class Backup(
    val version: Int = 2,
    val settings: AppSettings,
    val snippets: List<Snippet>,
    val folders: List<Folder> = emptyList(),
)

/**
 * Maps a backup's folder ids to the ids they have in this database. [backupFolders] are the ids
 * and names from the backup, [existingByName] maps lower-cased names already here to their ids,
 * and [create] makes a missing folder (keeping its colour and icon) and returns its new id. A snippet whose folder isn't in the
 * backup maps to null.
 */
internal suspend fun remapFolderIds(
    backupFolders: List<Folder>,
    existingByName: Map<String, Long>,
    create: suspend (Folder) -> Long,
): Map<Long, Long> {
    val made = HashMap<String, Long>()
    val out = HashMap<Long, Long>()
    for (f in backupFolders) {
        val key = f.name.trim().lowercase()
        out[f.id] = existingByName[key] ?: made.getOrPut(key) { create(f.copy(name = f.name.trim())) }
    }
    return out
}

class SnippetRepository(
    private val context: Context,
    private val db: ClipsyDatabase,
    private val settings: SettingsStore,
    val vault: Vault,
) {
    private val snippetDao = db.snippetDao()
    private val usageDao = db.usageDao()
    private val folderDao = db.folderDao()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val snippets: Flow<List<Snippet>> = snippetDao.observeAll()

    val folders: Flow<List<Folder>> = folderDao.observeAll()

    fun snippet(id: Long): Flow<Snippet?> = snippetDao.observe(id)

    suspend fun get(id: Long): Snippet? = snippetDao.get(id)

    fun usageSince(since: Long): Flow<List<UsageEvent>> = usageDao.observeSince(since)

    fun usageFor(snippetId: Long, since: Long): Flow<List<UsageEvent>> =
        usageDao.observeForSnippet(snippetId, since)

    suspend fun isKeywordTaken(keyword: String, exceptId: Long = 0): Boolean =
        keyword.isNotEmpty() && snippetDao.countKeyword(keyword, exceptId) > 0

    /** Inserts or updates; returns the snippet id. Blank keywords are stored as null. */
    suspend fun save(snippet: Snippet): Long {
        val clean = snippet.copy(keyword = snippet.keyword?.trim()?.ifEmpty { null })
        return if (clean.id == 0L) {
            snippetDao.insert(clean)
        } else {
            db.withTransaction {
                val before = snippetDao.get(clean.id)?.keyword
                snippetDao.update(clean)
                // Other snippets that pull this one in by {snippet:keyword} follow the new keyword.
                val after = clean.keyword
                if (before != null && after != null && !before.equals(after, ignoreCase = true)) {
                    renameReferences(before, after)
                }
            }
            clean.id
        }
    }

    private suspend fun renameReferences(from: String, to: String) {
        val ref = Regex("\\{snippet:" + Regex.escape(from) + "\\}", RegexOption.IGNORE_CASE)
        for (s in snippetDao.all()) {
            if (s.type.isFile || !ref.containsMatchIn(s.content)) continue
            snippetDao.update(s.copy(content = ref.replace(s.content, "{snippet:$to}")))
        }
    }

    /** Hands out the next {counter} number for a snippet (the stored count plus one) and records it. */
    suspend fun takeCounter(id: Long): Int = db.withTransaction {
        snippetDao.bumpCounter(id)
        snippetDao.get(id)?.counter ?: 1
    }

    suspend fun resetCounter(id: Long) = snippetDao.resetCounter(id)

    /** Counts a copy or share from a list or widget as a use, so "Most used" and "Recent" follow what you actually reach for. */
    suspend fun markCopied(ids: List<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { snippetDao.markUsed(it, now) }
    }

    /** A locked snippet's text; null while the vault is locked or if it can't be read. */
    fun reveal(snippet: Snippet): String? =
        if (snippet.locked) snippet.sealedContent?.let(vault::open) else snippet.content

    /**
     * Encrypts a text snippet in place: its text moves into [Snippet.sealedContent] and the
     * visible content is emptied, so search and everything else that reads content sees nothing.
     * The vault must be unlocked. With [expandUnlocked] the text is also sealed with the
     * sign-in-free key so the typing service can insert it.
     */
    suspend fun lock(snippet: Snippet, text: String = snippet.content, expandUnlocked: Boolean = snippet.expandUnlocked): Long =
        save(
            snippet.copy(
                locked = true,
                content = "",
                sealedContent = vault.seal(text),
                expandUnlocked = expandUnlocked,
                openSeal = if (expandUnlocked) vault.sealOpen(text) else null,
            ),
        )

    /** Puts a locked snippet's text back in the clear. The vault must be unlocked. */
    suspend fun unlockPermanently(snippet: Snippet): Boolean {
        val text = reveal(snippet) ?: return false
        save(snippet.copy(locked = false, content = text, sealedContent = null, expandUnlocked = false, openSeal = null))
        return true
    }

    /** Saves new text for a locked snippet, sealed again. The vault must be unlocked. */
    suspend fun saveLockedText(snippet: Snippet, text: String) = lock(snippet, text)

    suspend fun setPinned(id: Long, pinned: Boolean) = snippetDao.setPinned(id, pinned)

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) = snippetDao.setPinned(ids, pinned)

    suspend fun move(ids: List<Long>, folderId: Long?) = snippetDao.moveTo(ids, folderId)

    suspend fun isFolderNameTaken(name: String, exceptId: Long = 0): Boolean =
        folderDao.countName(name.trim(), exceptId) > 0

    /** Returns the new folder's id, or null when the name is blank or already used. */
    suspend fun createFolder(name: String, color: Int = 0, icon: String = "folder"): Long? {
        val clean = name.trim()
        if (clean.isEmpty() || isFolderNameTaken(clean)) return null
        return folderDao.insert(Folder(name = clean, color = color, icon = icon))
    }

    /** Changes a folder's name, colour and icon; false when the name is blank or taken. */
    suspend fun updateFolder(id: Long, name: String, color: Int, icon: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || isFolderNameTaken(clean, id)) return false
        folderDao.get(id)?.let { folderDao.update(it.copy(name = clean, color = color, icon = icon)) }
        return true
    }

    suspend fun folderSnippets(id: Long): List<Snippet> = snippetDao.inFolder(id)

    /** Removes the folder; its snippets go back to the library root. */
    suspend fun deleteFolder(id: Long) = db.withTransaction {
        snippetDao.clearFolder(id)
        folderDao.get(id)?.let { folderDao.delete(it) }
    }

    /** Called by the expansion service each time a snippet fires. */
    suspend fun recordExpansion(snippetId: Long, charsSaved: Int) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            snippetDao.markUsed(snippetId, now)
            usageDao.insert(UsageEvent(snippetId = snippetId, timestamp = now, charsSaved = charsSaved.coerceAtLeast(0)))
        }
    }

    suspend fun delete(snippet: Snippet) = withContext(Dispatchers.IO) {
        db.withTransaction {
            snippetDao.delete(snippet)
            usageDao.deleteForSnippet(snippet.id)
        }
        Thumbnails.delete(context, snippet)
        snippet.filePath?.let { File(it).delete() }
    }

    /**
     * Copies a picked/shared/dropped file into app storage and creates a snippet for it.
     * [as] is the type the user chose; left null, images become Media and the rest Documents.
     */
    suspend fun importFile(uri: Uri, `as`: SnippetType? = null): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "file"
        var size = 0L
        // Ask only for the two columns every provider must support; some older providers
        // reject a null projection. A failed lookup just keeps the fallbacks above.
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        val mime = resolver.getType(uri) ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        name = withExtension(name, mime)
        val dir = File(context.filesDir, "snippets").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}_${name.replace('/', '_')}")
        resolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: error("Could not open $uri")
        val id = save(
            Snippet(
                name = name,
                type = `as`?.takeIf { it.isFile } ?: SnippetType.forMime(mime),
                filePath = dest.absolutePath,
                mimeType = mime,
                sizeBytes = if (size > 0) size else dest.length(),
                expandAfter = settings.settings.value.expandAfter,
            ),
        )
        // Make the preview now so the pill has it the first time the keyword is typed.
        get(id)?.let { Thumbnails.ensure(context, it) }
        id
    }

    /**
     * Gives saved files that have no extension one from their stored type. The file provider
     * reports a file's type from its extension, and without one chat apps refuse to paste it.
     * Safe to run on every start: it only touches files that need it.
     */
    suspend fun fixFileExtensions() = withContext(Dispatchers.IO) {
        for (s in snippetDao.observeAllOnce()) {
            val path = s.filePath ?: continue
            val file = File(path)
            val fixed = withExtension(file.name, s.mimeType)
            if (fixed == file.name || !file.exists()) continue
            val dest = File(file.parentFile, fixed)
            if (!file.renameTo(dest)) continue
            Thumbnails.delete(context, s) // keyed by the old file name; remade on next use
            // Only the stored file changes; the name the user sees stays as it is.
            snippetDao.update(s.copy(filePath = dest.absolutePath))
        }
    }

    /**
     * Locked snippets are left out unless [includeLocked] is set and the vault is open; then
     * their text is written in the clear, which is the only way a backup can carry it.
     */
    suspend fun exportJson(includeLocked: Boolean = false): String = withContext(Dispatchers.IO) {
        val all = snippetDao.observeAllOnce().mapNotNull { s ->
            when {
                !s.locked -> s
                includeLocked -> reveal(s)?.let { s.copy(locked = false, content = it, sealedContent = null, expandUnlocked = false, openSeal = null) }
                else -> null
            }
        }
        json.encodeToString(Backup(settings = settings.settings.value, snippets = all.map { it.copy(filePath = null) }, folders = folderDao.observeAll().first()))
    }

    /** Merges a backup: snippets whose keyword already exists are skipped. Returns how many were added. */
    suspend fun importJson(text: String): Int = withContext(Dispatchers.IO) {
        val backup = json.decodeFromString<Backup>(text)
        var added = 0
        val existing = folderDao.observeAll().first().associate { it.name.trim().lowercase() to it.id }
        val folderMap = remapFolderIds(backup.folders, existing) { folderDao.insert(it.copy(id = 0)) }
        for (s in backup.snippets) {
            if (s.type.isFile) continue // file bytes are not part of a backup
            val keyword = s.keyword
            if (keyword != null && isKeywordTaken(keyword)) continue
            // A backup never carries ciphertext, so anything locked in it is just text here.
            save(s.copy(id = 0, lastUsedAt = null, useCount = 0, folderId = s.folderId?.let(folderMap::get), locked = false, sealedContent = null, expandUnlocked = false, openSeal = null))
            added++
        }
        settings.update { backup.settings.copy(onboardingDone = it.onboardingDone) }
        added
    }

    private suspend fun SnippetDao.observeAllOnce(): List<Snippet> = observeAll().first()
}
