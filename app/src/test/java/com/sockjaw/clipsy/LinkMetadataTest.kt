package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.LinkMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkMetadataTest {
    private val base = "https://example.com/blog/post"

    @Test fun readsOpenGraphTags() {
        val html = """
            <html><head><title>Fallback title</title>
            <meta property="og:title" content="Shipping &amp; handling">
            <meta property="og:description" content="How we ship things.">
            <meta property="og:image" content="https://cdn.example.com/card.png">
            <meta property="og:site_name" content="Example Blog">
            </head><body></body></html>
        """.trimIndent()
        val p = LinkMetadata.parse(html, base)
        assertEquals("Shipping & handling", p.title)
        assertEquals("How we ship things.", p.description)
        assertEquals("https://cdn.example.com/card.png", p.imageUrl)
        assertEquals("Example Blog", p.siteName)
        assertNull(p.excerpt)
    }

    @Test fun fallsBackToTwitterCardsThenPlainTags() {
        val html = """<head><title> Plain
            title </title><meta name="twitter:image" content="/img/a.jpg"><meta name="description" content='Single quoted'></head>"""
        val p = LinkMetadata.parse(html, base)
        assertEquals("Plain title", p.title)
        assertEquals("Single quoted", p.description)
        assertEquals("https://example.com/img/a.jpg", p.imageUrl)
    }

    @Test fun attributeOrderAndCaseDoNotMatter() {
        val html = """<META CONTENT="Ordered" PROPERTY="og:title"/>"""
        assertEquals("Ordered", LinkMetadata.parse(html, base).title)
    }

    @Test fun resolvesRelativeAndProtocolRelativeImages() {
        assertEquals("https://example.com/a/b.png", LinkMetadata.resolve("/a/b.png", base))
        assertEquals("https://example.com/blog/b.png", LinkMetadata.resolve("b.png", base))
        assertEquals("https://cdn.x.com/b.png", LinkMetadata.resolve("//cdn.x.com/b.png", base))
        assertNull(LinkMetadata.resolve("javascript:alert(1)", base))
        assertNull(LinkMetadata.resolve("data:image/png;base64,AAAA", base))
    }

    @Test fun decodesEntities() {
        assertEquals("Tom & Jerry's “show”", LinkMetadata.decode("Tom &amp; Jerry&#39;s &ldquo;show&rdquo;"))
        assertEquals("A–B", LinkMetadata.decode("A&#x2013;B"))
        assertEquals("&unknown;", LinkMetadata.decode("&unknown;"))
    }

    @Test fun cleanFoldsWhitespaceStripsTagsAndTruncates() {
        assertEquals("Hello world", LinkMetadata.clean("  Hello\n <b>world</b> ", 50))
        assertEquals("abcd…", LinkMetadata.clean("abcdefgh", 5))
        assertNull(LinkMetadata.clean("   ", 10))
        assertNull(LinkMetadata.clean(null, 10))
    }

    @Test fun usesFirstParagraphsWhenThereIsNoDescription() {
        val html = """<head><title>T</title><script>var x = "<p>not this one is long enough to count, really</p>";</script></head>
            <body><nav><p>Menu</p></nav><p>Short.</p>
            <p>This is the first real paragraph of the article, with enough words to be useful.</p></body>"""
        val p = LinkMetadata.parse(html, base)
        assertNull(p.description)
        assertTrue(p.excerpt!!.startsWith("This is the first real paragraph"))
    }

    @Test fun fileNameComesFromThePath() {
        assertEquals("photo one.jpg", LinkMetadata.fileName("https://x.com/a/photo%20one.jpg?s=1"))
        assertNull(LinkMetadata.fileName("https://x.com/"))
    }
}
