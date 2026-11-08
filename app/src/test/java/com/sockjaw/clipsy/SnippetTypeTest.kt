package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.SnippetType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetTypeTest {
    @Test fun imagesAndGifsAreMedia() {
        assertEquals(SnippetType.MEDIA, SnippetType.forMime("image/png"))
        assertEquals(SnippetType.MEDIA, SnippetType.forMime("image/gif"))
    }

    @Test fun everythingElseIsADocument() {
        assertEquals(SnippetType.DOCUMENT, SnippetType.forMime("application/pdf"))
        assertEquals(SnippetType.DOCUMENT, SnippetType.forMime("video/mp4"))
        assertEquals(SnippetType.DOCUMENT, SnippetType.forMime(null))
    }

    @Test fun onlyTextIsNotAFile() {
        assertFalse(SnippetType.TEXT.isFile)
        assertTrue(SnippetType.MEDIA.isFile)
        assertTrue(SnippetType.DOCUMENT.isFile)
    }

    @Test fun oldBackupsWithFileStillDecode() =
        assertEquals(SnippetType.DOCUMENT, Json.decodeFromString<SnippetType>("\"FILE\""))
}
