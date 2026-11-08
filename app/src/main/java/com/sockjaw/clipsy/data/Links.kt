package com.sockjaw.clipsy.data

object Links {
    private val scheme = Regex("^https?://[^\\s/?#]+[^\\s]*$", RegexOption.IGNORE_CASE)
    private val bare = Regex("^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,24}(?::\\d+)?(?:[/?#][^\\s]*)?$", RegexOption.IGNORE_CASE)

    /** The trimmed text when it is a single link, else null. Bare domains like `github.com/x` count. */
    fun parse(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() } || '@' in t.substringBefore('/')) return null
        return if (scheme.matches(t) || bare.matches(t)) t else null
    }

    /** The URL with a scheme, ready for ACTION_VIEW. */
    fun withScheme(url: String): String = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"

    /** True when a link is spelled out as plain `http://`; bare domains are opened over https. */
    fun isCleartext(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

    /** The same link over https; other links come back trimmed and unchanged. */
    fun toHttps(url: String): String = url.trim().let { if (isCleartext(it)) "https://" + it.substring(7) else it }

    /** "www.github.com/a/b" -> "github.com". */
    fun host(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':').removePrefix("www.")
}
