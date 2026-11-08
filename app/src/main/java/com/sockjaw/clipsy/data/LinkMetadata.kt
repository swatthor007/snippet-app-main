package com.sockjaw.clipsy.data

import java.net.URI

/** What could be read from a page's HTML. */
data class ParsedPage(
    val title: String?,
    val description: String?,
    val siteName: String?,
    /** Absolute address of the share image. */
    val imageUrl: String?,
    /** The start of the page's own text, for pages with no description. */
    val excerpt: String?,
)

/**
 * Reads the share-card tags (Open Graph, Twitter cards, plain meta tags) out of a page.
 * Pure text in, values out, so it can be tested without a network.
 */
object LinkMetadata {
    private val metaTag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val titleTag = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val paragraph = Regex("<p\\b[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val noise = Regex("<(script|style|noscript|svg|template|head)\\b.*?</\\1>|<!--.*?-->", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tag = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")

    private const val MAX_TITLE = 140
    private const val MAX_DESCRIPTION = 400
    private const val MAX_EXCERPT = 240

    fun parse(html: String, baseUrl: String): ParsedPage {
        val meta = HashMap<String, String>()
        for (m in metaTag.findAll(html)) {
            val attrs = attributes(m.value)
            val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase() ?: continue
            val value = attrs["content"] ?: continue
            if (value.isNotBlank()) meta.putIfAbsent(key, value)
        }
        val imageSrc = linkTag.findAll(html).map { attributes(it.value) }
            .firstOrNull { it["rel"]?.lowercase() == "image_src" }?.get("href")

        val title = first(meta, "og:title", "twitter:title")
            ?: titleTag.find(html)?.groupValues?.get(1)
        val description = first(meta, "og:description", "twitter:description", "description")
        val image = first(meta, "og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src") ?: imageSrc

        return ParsedPage(
            title = clean(title, MAX_TITLE),
            description = clean(description, MAX_DESCRIPTION),
            siteName = clean(first(meta, "og:site_name", "application-name"), 60),
            imageUrl = image?.let { resolve(decode(it.trim()), baseUrl) },
            excerpt = if (description.isNullOrBlank()) excerpt(html) else null,
        )
    }

    private fun first(meta: Map<String, String>, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { meta[it]?.takeIf(String::isNotBlank) }

    private fun attributes(tagText: String): Map<String, String> =
        attribute.findAll(tagText).associate { m ->
            val v = m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] }
            m.groupValues[1].lowercase() to v
        }

    /** The first few paragraphs of readable text, for pages that have no description tag. */
    private fun excerpt(html: String): String? {
        val body = noise.replace(html, " ")
        val text = StringBuilder()
        for (p in paragraph.findAll(body)) {
            val t = clean(p.groupValues[1], MAX_EXCERPT) ?: continue
            if (t.length < 40) continue
            if (text.isNotEmpty()) text.append(' ')
            text.append(t)
            if (text.length >= MAX_EXCERPT) break
        }
        return text.takeIf { it.isNotEmpty() }?.toString()?.let { if (it.length > MAX_EXCERPT) it.take(MAX_EXCERPT - 1).trimEnd() + "…" else it }
    }

    /** Tags removed, entities decoded, whitespace folded, cut to [max] with an ellipsis. */
    fun clean(raw: String?, max: Int): String? {
        if (raw == null) return null
        val t = decode(tag.replace(raw, " ")).replace(spaces, " ").trim()
        if (t.isEmpty()) return null
        return if (t.length > max) t.take(max - 1).trimEnd() + "…" else t
    }

    private val named = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "copy" to "©", "middot" to "·")

    fun decode(s: String): String {
        if ('&' !in s) return s
        return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)?.let(::codePoint)
                body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePoint)
                else -> named[body]
            } ?: m.value
        }
    }

    private fun codePoint(cp: Int): String? = if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else null

    /** Turns "/img/a.png" or "//cdn.x.com/a.png" into a full address; null when it can't be made one. */
    fun resolve(ref: String, baseUrl: String): String? = runCatching {
        val r = URI(ref.replace(" ", "%20"))
        val full = if (r.isAbsolute) r else URI(baseUrl).resolve(r)
        full.toString().takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }.getOrNull()

    /** "https://x.com/a/photo.jpg?s=1" -> "photo.jpg"; null when the path has no file name. */
    fun fileName(url: String): String? =
        runCatching { URI(url).path.substringAfterLast('/').takeIf { it.isNotBlank() }?.let { java.net.URLDecoder.decode(it, "UTF-8") } }.getOrNull()
}
