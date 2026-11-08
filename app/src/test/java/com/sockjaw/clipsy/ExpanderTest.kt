package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.service.Detection
import com.sockjaw.clipsy.service.Expander
import com.sockjaw.clipsy.service.Rendered
import com.sockjaw.clipsy.service.Template
import com.sockjaw.clipsy.service.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExpanderTest {
    private val sig = Snippet(id = 1, name = "Sig", keyword = "sig", content = "Best, Ada")
    private val now = Snippet(id = 2, name = "Now", keyword = "brb", content = "back soon", expandAfter = ExpandAfter.INSTANT)
    private val lines = Snippet(id = 3, name = "Addr", keyword = "addr", content = "42 Lane", expandAfter = ExpandAfter.ENTER)
    private val lookup: (String) -> Snippet? = { k -> listOf(sig, now, lines).firstOrNull { it.keyword == k } }

    private fun detect(text: String, cursor: Int = text.length, prefix: String = "") = Expander.detect(text, cursor, prefix, lookup)

    @Test fun spaceAfterKeywordExpands() {
        val d = detect("thanks sig ")
        assertTrue(d is Detection.Expand)
        val m = (d as Detection.Expand).match
        assertEquals(Trigger.SPACE, m.trigger)
        assertEquals(7, m.start)
        assertEquals(11, m.replaceEnd)
    }

    @Test fun typedKeywordWaitingForSpaceIsSuggested() = assertTrue(detect("thanks sig") is Detection.Suggest)

    @Test fun instantKeywordExpandsWithoutSpace() = assertTrue(detect("ok brb") is Detection.Expand)

    @Test fun enterSnippetIgnoresSpace() {
        assertEquals(Detection.None, detect("my addr "))
        assertTrue(detect("my addr\n") is Detection.Expand)
    }

    @Test fun wholeWordsOnly() {
        assertEquals(Detection.None, detect("design "))
        assertEquals(Detection.None, detect("sigh "))
        // Caret in the middle of a longer word.
        assertEquals(Detection.None, detect("sigma", cursor = 3))
    }

    @Test fun matchingIsCaseInsensitive() = assertTrue(detect("SIG ") is Detection.Expand)

    @Test fun prefixIsRequiredWhenSet() {
        assertEquals(Detection.None, detect("sig ", prefix = ";"))
        val d = detect("hi ;sig ", prefix = ";") as Detection.Expand
        assertEquals(3, d.match.start)
        assertEquals(Detection.None, detect("a;sig ", prefix = ";"))
    }

    @Test fun applyReplacesAndKeepsSpace() {
        val text = "thanks sig and more"
        val m = (Expander.detect(text, 11, "", lookup) as Detection.Expand).match
        val (out, caret) = Expander.apply(text, m, Rendered("Best, Ada"))
        assertEquals("thanks Best, Ada and more", out)
        assertEquals(17, caret)
    }

    @Test fun applyHonoursCursorToken() {
        val m = (detect("x sig ") as Detection.Expand).match
        val (out, caret) = Expander.apply("x sig ", m, Rendered("Hi , bye", cursor = 3))
        assertEquals("x Hi , bye ", out)
        assertEquals(5, caret)
    }

    @Test fun removeDropsKeywordAndTrigger() {
        val m = (detect("see sig ") as Detection.Expand).match
        assertEquals("see " to 4, Expander.remove("see sig ", m))
    }

    @Test fun templateFillsTokens() {
        val r = Template.render("At {time+1h}, paste {clipboard}{cursor}!", now = 0L, clipboard = "X", locale = Locale.US)
        assertTrue(r.text.startsWith("At "))
        assertTrue(r.text.endsWith(", paste X!"))
        assertEquals(r.text.length - 1, r.cursor)
    }
}
