package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.Keyword
import com.sockjaw.clipsy.data.KeywordIssue
import com.sockjaw.clipsy.data.Stats
import com.sockjaw.clipsy.data.UsageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreLogicTest {
    @Test fun blankKeywordIsValid() = assertNull(Keyword.validate(""))

    @Test fun keywordRejectsSpacesAndSymbols() {
        assertEquals(KeywordIssue.BAD_CHARS, Keyword.validate("my link"))
        assertEquals(KeywordIssue.BAD_CHARS, Keyword.validate("a-b"))
    }

    @Test fun keywordNeedsTwoCharacters() = assertEquals(KeywordIssue.TOO_SHORT, Keyword.validate("a"))

    @Test fun keywordAcceptsUnderscores() = assertNull(Keyword.validate("personal_address"))

    @Test fun suggestBuildsSnakeCase() = assertEquals("home_address", Keyword.suggest("  Home address! "))

    @Test fun timeSavedMatchesDesign() {
        // 40.8k characters at 40 wpm is the "3.4 hours" shown on Home.
        assertEquals("3.4 hours", Stats.formatSaved(40_800))
        assertEquals("0 min", Stats.formatSaved(0))
    }

    @Test fun dailyBucketsAreOldestFirst() {
        val now = 10L * 24 * 60 * 60 * 1000 + 12 * 60 * 60 * 1000
        val day = 24L * 60 * 60 * 1000
        val events = listOf(
            UsageEvent(snippetId = 1, timestamp = now, charsSaved = 10),
            UsageEvent(snippetId = 1, timestamp = now - day, charsSaved = 5),
            UsageEvent(snippetId = 1, timestamp = now - 30 * day, charsSaved = 99),
        )
        val buckets = Stats.dailyChars(events, 7, now)
        assertEquals(7, buckets.size)
        assertEquals(10L, buckets.last())
        assertEquals(5L, buckets[5])
        assertEquals(15L, buckets.sum())
    }
}
