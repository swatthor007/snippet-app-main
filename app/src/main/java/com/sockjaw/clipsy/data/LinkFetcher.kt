package com.sockjaw.clipsy.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale

/**
 * Plain HTTP for link previews: follows a few redirects, never goes to this phone's own network,
 * reads only the start of a page, and gives up quickly. No library, no cookies, no sign-in.
 */
object LinkFetcher {
    class Page(val finalUrl: String, val mime: String?, val size: Long, val html: String?)

    private const val CONNECT_MS = 6_000
    private const val READ_MS = 8_000
    private const val MAX_REDIRECTS = 5
    private const val MAX_HTML_BYTES = 400 * 1024
    private val userAgent = "Mozilla/5.0 (compatible; ClipsyLinkPreview/1.0; +Android)"

    /** Opens [url], following redirects; the body is read only for pages. Throws [IOException] on anything unusable. */
    fun fetch(url: String): Page {
        var current = upgrade(url)
        repeat(MAX_REDIRECTS + 1) {
            val c = open(current)
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                c.disconnect()
                current = upgrade(LinkMetadata.resolve(next, current) ?: throw IOException("Bad redirect"))
                return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code") }
            val mime = c.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            val length = c.contentLengthLong
            val isPage = mime == null || mime == "text/html" || mime == "application/xhtml+xml"
            val html = if (isPage) c.inputStream.use { readHtml(it, c.contentType) } else null
            c.disconnect()
            return Page(current, mime ?: "text/html", length.coerceAtLeast(0), html)
        }
        throw IOException("Too many redirects")
    }

    /** True when [url] (read as https) answers with a page or file; false on any failure. */
    fun httpsExists(url: String): Boolean = try { fetch(Links.toHttps(url)); true } catch (e: IOException) { false }

    /** Saves [url] to [dest] when it is smaller than [maxBytes]; returns its content type, or null if it didn't work. */
    fun download(url: String, dest: File, maxBytes: Long): String? {
        var current = upgrade(url)
        repeat(MAX_REDIRECTS + 1) {
            val c = open(current, accept = "image/*,*/*;q=0.5")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location") ?: return null
                    current = upgrade(LinkMetadata.resolve(next, current) ?: return null)
                    return@repeat
                }
                if (code !in 200..299) return null
                if (c.contentLengthLong > maxBytes) return null
                val mime = c.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                if (mime == null || !mime.startsWith("image/") || mime.contains("svg")) return null
                dest.parentFile?.mkdirs()
                val part = File(dest.path + ".part")
                var total = 0L
                c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) { part.delete(); return null }
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (!part.renameTo(dest)) { part.delete(); return null }
                return mime
            } catch (e: IOException) {
                return null
            } finally {
                c.disconnect()
            }
        }
        return null
    }

    private fun open(url: String, accept: String = "text/html,application/xhtml+xml,image/*;q=0.8,*/*;q=0.5"): HttpURLConnection {
        val u = URL(url)
        if (u.protocol != "https" && u.protocol != "http") throw IOException("Not a web link")
        if (isLocal(u.host)) throw IOException("Local address")
        return (u.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "${Locale.getDefault().toLanguageTag()},en;q=0.8")
        }
    }

    /** Always ask for https; plain http is blocked by the system and unsafe anyway. */
    private fun upgrade(url: String): String = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    /** True for this phone and its local network, which a link preview has no business reaching. */
    fun isLocal(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan")) return true
        return try {
            InetAddress.getAllByName(h).any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
        } catch (e: IOException) {
            false
        }
    }

    private fun readHtml(input: java.io.InputStream, contentType: String?): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < MAX_HTML_BYTES) {
            val n = input.read(buf, 0, minOf(buf.size, MAX_HTML_BYTES - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        val bytes = out.toByteArray()
        return String(bytes, charset(contentType, bytes))
    }

    private fun charset(contentType: String?, bytes: ByteArray): Charset {
        val declared = contentType?.let { Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?: Regex("<meta[^>]+charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
                .find(String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1))?.groupValues?.get(1)
        return runCatching { Charset.forName(declared ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
    }
}
