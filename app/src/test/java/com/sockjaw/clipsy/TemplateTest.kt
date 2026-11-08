package com.sockjaw.clipsy

import com.sockjaw.clipsy.service.Template
import com.sockjaw.clipsy.service.TemplateContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class TemplateTest {
    // Noon UTC on Thursday 1 October 2026: the same calendar day in every time zone from -11 to +11.
    private val noon = 1790856000000L
    private fun render(text: String, ctx: TemplateContext = TemplateContext()) =
        Template.render(text, now = noon, context = ctx, locale = Locale.US)

    @Test fun dayMonthYear() {
        val tz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("Thursday, October 2026", render("{day}, {month} {year}").text)
            assertEquals("Friday", render("{day+1d}").text)
        } finally {
            TimeZone.setDefault(tz)
        }
    }

    @Test fun greetingFollowsTheHour() {
        assertEquals("Good evening", Template.greeting(4))
        assertEquals("Good morning", Template.greeting(5))
        assertEquals("Good morning", Template.greeting(11))
        assertEquals("Good afternoon", Template.greeting(12))
        assertEquals("Good afternoon", Template.greeting(16))
        assertEquals("Good evening", Template.greeting(17))
    }

    @Test fun appName() = assertEquals("Sent from WhatsApp", render("Sent from {app}", TemplateContext(app = "WhatsApp")).text)

    @Test fun missingValuesRenderAsNothing() =
        assertEquals("[][][]", render("[{app}][{clipboard}][{snippet:nope}]").text)

    @Test fun counterAndPadding() {
        val ctx = TemplateContext(counter = 7)
        assertEquals("#7 / INV-0007", render("#{counter} / INV-{counter:4}", ctx).text)
        assertTrue(Template.usesCounter("INV-{counter:4}"))
        assertFalse(Template.usesCounter("{date}"))
    }

    @Test fun pullsInAnotherSnippet() {
        val ctx = TemplateContext(snippet = { if (it == "sig") "Cheers, Jack" else null })
        assertEquals("Thanks!\nCheers, Jack", render("Thanks!\n{snippet:SIG}", ctx).text)
    }

    @Test fun referencesNeverChainOrLoop() {
        // a pulls in b, b pulls in a and c: only b's own text comes through, once.
        val bodies = mapOf("a" to "A[{snippet:b}]", "b" to "B{snippet:a}{snippet:c}{counter}{cursor}", "c" to "C")
        val r = render(bodies.getValue("a"), TemplateContext(counter = 3, snippet = { bodies[it] }))
        assertEquals("A[B]", r.text)
        assertNull(r.cursor)
    }

    @Test fun cursorStillMarksItsPlaceAfterAPulledInSnippet() {
        val r = render("{snippet:x}!{cursor}", TemplateContext(snippet = { "hello" }))
        assertEquals("hello!", r.text)
        assertEquals(6, r.cursor)
    }
}
