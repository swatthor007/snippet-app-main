package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.widget.RackConfig
import com.sockjaw.clipsy.widget.RackSort
import com.sockjaw.clipsy.widget.RackSource
import com.sockjaw.clipsy.widget.SnippetSource
import com.sockjaw.clipsy.widget.SnippetsConfig
import com.sockjaw.clipsy.widget.WidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetDataTest {
    private fun text(id: Long, name: String, uses: Int = 0, pinned: Boolean = false, folder: Long? = null, lastUsed: Long? = null, locked: Boolean = false) =
        Snippet(id = id, name = name, content = "x", useCount = uses, pinned = pinned, folderId = folder, lastUsedAt = lastUsed, createdAt = 0, locked = locked)

    private fun file(id: Long, name: String, type: SnippetType = SnippetType.DOCUMENT, uses: Int = 0, lastUsed: Long? = null) =
        Snippet(id = id, name = name, type = type, filePath = "/f/$id", useCount = uses, lastUsedAt = lastUsed, createdAt = 0)

    private val all = listOf(
        text(1, "Sig", uses = 5, lastUsed = 100),
        text(2, "Addr", uses = 9, pinned = true, folder = 7, lastUsed = 300),
        text(3, "Thanks", uses = 9, lastUsed = 200),
        text(4, "Secret", uses = 99, locked = true),
        file(10, "CV.pdf", uses = 2, lastUsed = 50),
        file(11, "logo.png", SnippetType.MEDIA, uses = 8, lastUsed = 400),
    )

    private fun ids(list: List<Snippet>) = list.map { it.id }

    @Test fun mostUsedBreaksTiesByRecency() {
        assertEquals(listOf(2L, 3L, 1L), ids(WidgetData.snippets(all, SnippetsConfig(source = SnippetSource.MOST_USED))))
    }

    @Test fun recentOrdersByLastActivity() {
        assertEquals(listOf(2L, 3L, 1L), ids(WidgetData.snippets(all, SnippetsConfig(source = SnippetSource.RECENT))))
    }

    @Test fun lockedSnippetsAndFilesNeverAppearInTheSnippetsWidget() {
        val shown = ids(WidgetData.snippets(all, SnippetsConfig(source = SnippetSource.MOST_USED)))
        assertEquals(false, 4L in shown)
        assertEquals(false, 10L in shown)
    }

    @Test fun pinnedAndFolderSourcesFilter() {
        assertEquals(listOf(2L), ids(WidgetData.snippets(all, SnippetsConfig(source = SnippetSource.PINNED))))
        assertEquals(listOf(2L), ids(WidgetData.snippets(all, SnippetsConfig(source = SnippetSource.FOLDER, folderId = 7))))
        assertEquals(emptyList<Long>(), ids(WidgetData.snippets(all, SnippetsConfig(source = SnippetSource.FOLDER, folderId = null))))
    }

    @Test fun pickedKeepsTheOrderChosenAndDropsMissingOnes() {
        val cfg = SnippetsConfig(source = SnippetSource.PICKED, pickedIds = listOf(3, 99, 1))
        assertEquals(listOf(3L, 1L), ids(WidgetData.snippets(all, cfg)))
    }

    @Test fun rackFiltersByKindAndSorts() {
        assertEquals(listOf(11L, 10L), ids(WidgetData.files(all, RackConfig(source = RackSource.ALL, sort = RackSort.RECENT))))
        assertEquals(listOf(11L), ids(WidgetData.files(all, RackConfig(source = RackSource.MEDIA))))
        assertEquals(listOf(10L), ids(WidgetData.files(all, RackConfig(source = RackSource.DOCUMENTS))))
        assertEquals(listOf(10L, 11L), ids(WidgetData.files(all, RackConfig(sort = RackSort.NAME))))
        assertEquals(listOf(11L, 10L), ids(WidgetData.files(all, RackConfig(sort = RackSort.MOST_USED))))
    }

    @Test fun fitCountsCellsAndGaps() {
        assertEquals(3, WidgetData.fit(226f, 64f, 2f))
        assertEquals(1, WidgetData.fit(10f, 64f))
        assertEquals(2, WidgetData.fit(84f, 40f, 4f))
    }

    @Test fun pagingClampsAndCounts() {
        val items = (1..11).toList()
        assertEquals(3, WidgetData.pageCount(items.size, 5))
        assertEquals(1, WidgetData.pageCount(0, 5))
        assertEquals(listOf(6, 7, 8, 9, 10), WidgetData.page(items, 5, 1))
        assertEquals(listOf(11), WidgetData.page(items, 5, 99))
        assertEquals(listOf(1, 2, 3, 4, 5), WidgetData.page(items, 5, -3))
    }

    @Test fun signatureIgnoresUsesButNotRenames() {
        val before = WidgetData.signature(all)
        val used = all.map { if (it.id == 1L) it.copy(useCount = 6, lastUsedAt = 999) else it }
        assertEquals(before, WidgetData.signature(used))
        val renamed = all.map { if (it.id == 1L) it.copy(name = "Signature") else it }
        assertNotEquals(before, WidgetData.signature(renamed))
    }
}
