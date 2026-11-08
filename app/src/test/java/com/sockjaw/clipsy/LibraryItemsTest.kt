package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.ui.library.LibraryFilter
import com.sockjaw.clipsy.ui.library.LibrarySort
import com.sockjaw.clipsy.ui.library.libraryCounts
import com.sockjaw.clipsy.ui.library.libraryItems
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryItemsTest {
    private val all = listOf(
        Snippet(id = 1, name = "Note", content = "hello there", folderId = 1),
        Snippet(id = 2, name = "Site", content = "https://a.b/c", folderId = 1),
        Snippet(id = 3, name = "Pic", type = SnippetType.MEDIA, folderId = 2, pinned = true),
        Snippet(id = 4, name = "Loose", content = "github.com/x"),
    )

    private fun ids(folder: Long?, f: LibraryFilter = LibraryFilter.ALL, q: String = "") =
        libraryItems(all, folder, f, q, LibrarySort.NAME).map { it.id }

    @Test fun rootShowsEverything() = assertEquals(setOf(1L, 2L, 3L, 4L), ids(null).toSet())

    @Test fun folderShowsOnlyItsSnippets() = assertEquals(setOf(1L, 2L), ids(1).toSet())
    @Test fun linksAreNotText() {
        assertEquals(listOf(1L), ids(null, LibraryFilter.TEXT))
        assertEquals(setOf(2L, 4L), ids(null, LibraryFilter.LINKS).toSet())
    }
    @Test fun filterAndQueryCombine() = assertEquals(listOf(2L), ids(1, LibraryFilter.LINKS, "site"))
    @Test fun countsFollowScope() {
        val c = libraryCounts(all.filter { it.folderId == 1L })
        assertEquals(2, c[LibraryFilter.ALL])
        assertEquals(1, c[LibraryFilter.LINKS])
        assertEquals(0, c[LibraryFilter.PINNED])
    }

    @Test fun pinnedLeadEverySort() {
        for (sort in LibrarySort.entries) {
            assertEquals(3L, libraryItems(all, null, LibraryFilter.ALL, "", sort).first().id)
        }
    }

    @Test fun sortStillOrdersTheRest() =
        assertEquals(listOf(3L, 4L, 1L, 2L), libraryItems(all, null, LibraryFilter.ALL, "", LibrarySort.NAME).map { it.id })
}
