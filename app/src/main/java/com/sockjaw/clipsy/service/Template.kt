package com.sockjaw.clipsy.service

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A snippet's content with placeholders filled in; [cursor] is where {cursor} was, if present. */
data class Rendered(val text: String, val cursor: Int? = null)

/**
 * What placeholders draw on beyond the clock. Every field is optional: a missing value renders as
 * nothing rather than leaving the raw token in the text.
 */
data class TemplateContext(
    /** Text on the clipboard, for {clipboard}. Usually unreadable in the background on Android 10+. */
    val clipboard: String? = null,
    /** Name of the app being typed in, for {app}. */
    val app: String? = null,
    /** The number {counter} stands for this time. */
    val counter: Int? = null,
    /** Another snippet's content by keyword (lower-case), for {snippet:keyword}. */
    val snippet: (String) -> String? = { null },
)

object Template {
    /**
     * {date} {time} {day} {month} {year}, each with an optional offset like {time+1h} or {day+1d};
     * {greeting} {clipboard} {app} {cursor}; {counter} or {counter:4} (zero-padded to 4 digits);
     * {snippet:keyword}.
     */
    val token = Regex(
        "\\{(?:(date|time|day|month|year)(?:([+-]\\d+)([mhd]))?|(greeting|clipboard|app|cursor)|counter(?::(\\d))?|snippet:([A-Za-z0-9_]+))\\}",
    )

    /** True when [content] uses {counter}, so the caller knows to hand out a number. */
    fun usesCounter(content: String): Boolean = token.findAll(content).any { it.value.startsWith("{counter") }

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    fun render(
        content: String,
        now: Long = System.currentTimeMillis(),
        context: TemplateContext = TemplateContext(),
        locale: Locale = Locale.getDefault(),
    ): Rendered = renderInto(StringBuilder(), content, now, context, locale, nested = false)

    /** Kept for callers that only have the clipboard. */
    fun render(content: String, now: Long = System.currentTimeMillis(), clipboard: String?, locale: Locale = Locale.getDefault()): Rendered =
        render(content, now, TemplateContext(clipboard = clipboard), locale)

    /**
     * Renders [content] into [out]. A snippet pulled in by {snippet:…} is rendered with [nested] set:
     * its own {snippet:…} and {cursor} are dropped, so references never chain or loop, and its
     * {counter} is dropped because that number belongs to the other snippet.
     */
    private fun renderInto(
        out: StringBuilder,
        content: String,
        now: Long,
        ctx: TemplateContext,
        locale: Locale,
        nested: Boolean,
    ): Rendered {
        var cursor: Int? = null
        var last = 0
        for (m in token.findAll(content)) {
            out.append(content, last, m.range.first)
            last = m.range.last + 1
            val g = m.groupValues
            val timed = g[1]
            if (timed.isNotEmpty()) {
                val amount = g[2].toLongOrNull() ?: 0L
                val unitMs = when (g[3]) {
                    "m" -> 60_000L
                    "h" -> 3_600_000L
                    "d" -> 86_400_000L
                    else -> 0L
                }
                val at = Date(now + amount * unitMs)
                out.append(
                    when (timed) {
                        "date" -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(at)
                        "time" -> DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(at)
                        "day" -> SimpleDateFormat("EEEE", locale).format(at)
                        "month" -> SimpleDateFormat("LLLL", locale).format(at)
                        else -> SimpleDateFormat("yyyy", locale).format(at)
                    },
                )
                continue
            }
            when (g[4]) {
                "greeting" -> out.append(greeting(Calendar.getInstance(locale).apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)))
                "clipboard" -> out.append(ctx.clipboard.orEmpty())
                "app" -> out.append(ctx.app.orEmpty())
                "cursor" -> if (!nested && cursor == null) cursor = out.length
                "" -> when {
                    m.value.startsWith("{counter") -> if (!nested) ctx.counter?.let { n ->
                        val width = g[5].toIntOrNull() ?: 0
                        out.append(n.toString().padStart(width, '0'))
                    }
                    !nested -> ctx.snippet(g[6].lowercase())?.let { inner ->
                        renderInto(out, inner, now, ctx, locale, nested = true)
                    }
                }
            }
        }
        out.append(content, last, content.length)
        return Rendered(out.toString(), cursor)
    }
}
