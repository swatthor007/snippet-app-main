package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.data.Snippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test fun acceptsSchemeLinks() = assertEquals("https://a.b/c", Links.parse("  https://a.b/c "))
    @Test fun acceptsBareDomainWithPath() = assertEquals("github.com/x", Links.parse("github.com/x"))
    @Test fun acceptsTrailingSlash() = assertEquals("https://example.com/", Links.parse("https://example.com/"))
    @Test fun rejectsSentences() = assertNull(Links.parse("see https://a.b/c now"))
    @Test fun rejectsLooseDots() = assertNull(Links.parse("hello.world is nice"))
    @Test fun rejectsEmail() = assertNull(Links.parse("me@example.com"))
    @Test fun rejectsPlainWord() = assertNull(Links.parse("hello"))
    @Test fun hostStripsWwwAndPath() = assertEquals("github.com", Links.host("https://www.github.com/a/b?x=1"))
    @Test fun hostOfBareDomain() = assertEquals("github.com", Links.host("github.com/x"))
    @Test fun schemeAddedOnlyWhenMissing() {
        assertEquals("https://github.com/x", Links.withScheme("github.com/x"))
        assertEquals("http://a.b", Links.withScheme("http://a.b"))
    }
    @Test fun isLinkIsDerivedFromContent() {
        assertTrue(Snippet(name = "n", content = "https://a.b").isLink)
        assertFalse(Snippet(name = "n", content = "just text").isLink)
    }
}
