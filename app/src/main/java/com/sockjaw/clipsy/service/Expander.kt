package com.sockjaw.clipsy.service

import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.Snippet

enum class Trigger { TYPED, SPACE, ENTER }

/**
 * A keyword found in a text field.
 * [start] includes the prefix; [keywordEnd] is just past the keyword; [replaceEnd] also covers
 * the space or newline that fired it.
 */
data class Match(
    val snippet: Snippet,
    val start: Int,
    val keywordEnd: Int,
    val replaceEnd: Int,
    val trigger: Trigger,
) {
    val typedLength: Int get() = keywordEnd - start
}

sealed interface Detection {
    /** Replace now. */
    data class Expand(val match: Match) : Detection
    /** Keyword is typed but waits for its trigger key: offer it in the suggestion pill. */
    data class Suggest(val match: Match) : Detection
    data object None : Detection
}

object Expander {
    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    fun detect(text: CharSequence, cursor: Int, prefix: String, lookup: (String) -> Snippet?): Detection {
        if (cursor <= 0 || cursor > text.length) return Detection.None
        val last = text[cursor - 1]
        if (last == ' ' || last == '\n') {
            val m = wordEndingAt(text, cursor - 1, prefix, lookup) ?: return Detection.None
            val wanted = if (last == ' ') ExpandAfter.SPACE else ExpandAfter.ENTER
            if (m.snippet.expandAfter != wanted) return Detection.None
            return Detection.Expand(m.copy(replaceEnd = cursor, trigger = if (last == ' ') Trigger.SPACE else Trigger.ENTER))
        }
        val m = wordEndingAt(text, cursor, prefix, lookup) ?: return Detection.None
        return if (m.snippet.expandAfter == ExpandAfter.INSTANT) Detection.Expand(m) else Detection.Suggest(m)
    }

    private fun wordEndingAt(text: CharSequence, end: Int, prefix: String, lookup: (String) -> Snippet?): Match? {
        // Whole words only: the caret must not sit inside a longer word.
        if (end < text.length && isWordChar(text[end])) return null
        var start = end
        while (start > 0 && isWordChar(text[start - 1])) start--
        if (start == end) return null
        var tokenStart = start
        if (prefix.isNotEmpty()) {
            if (start < prefix.length || !text.regionMatches(start - prefix.length, prefix, 0, prefix.length)) return null
            tokenStart = start - prefix.length
            if (tokenStart > 0 && isWordChar(text[tokenStart - 1])) return null
        }
        val snippet = lookup(text.substring(start, end).lowercase()) ?: return null
        return Match(snippet, tokenStart, end, end, Trigger.TYPED)
    }

    /** Returns the field's new text and where the caret goes. */
    fun apply(text: String, match: Match, rendered: Rendered): Pair<String, Int> {
        val delimiter = if (match.trigger != Trigger.TYPED && match.snippet.keepSpace) {
            text.substring(match.keywordEnd, match.replaceEnd)
        } else {
            ""
        }
        val insert = rendered.text + delimiter
        val newText = text.substring(0, match.start) + insert + text.substring(match.replaceEnd)
        return newText to match.start + (rendered.cursor ?: insert.length)
    }

    /** Removes the keyword (and its trigger key), for file snippets that don't insert text. */
    fun remove(text: String, match: Match): Pair<String, Int> =
        (text.substring(0, match.start) + text.substring(match.replaceEnd)) to match.start
}
