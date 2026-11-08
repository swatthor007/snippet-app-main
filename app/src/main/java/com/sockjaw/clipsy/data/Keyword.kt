package com.sockjaw.clipsy.data

enum class KeywordIssue(val message: String) {
    TOO_SHORT("Use at least 2 characters"),
    BAD_CHARS("Letters, numbers and underscores only"),
    TAKEN("Already used by another snippet"),
}

object Keyword {
    private val allowed = Regex("[A-Za-z0-9_]+")

    /** Blank input is valid: a snippet may have no keyword. */
    fun validate(keyword: String): KeywordIssue? = when {
        keyword.isEmpty() -> null
        !allowed.matches(keyword) -> KeywordIssue.BAD_CHARS
        keyword.length < 2 -> KeywordIssue.TOO_SHORT
        else -> null
    }

    /** Suggests a keyword from a name: "Home address" -> "home_address". */
    fun suggest(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24)
}
