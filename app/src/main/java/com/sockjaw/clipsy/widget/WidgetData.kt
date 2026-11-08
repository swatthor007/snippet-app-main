package com.sockjaw.clipsy.widget

import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import kotlin.math.ceil

/**
 * Which snippets a widget shows, and how they're laid out on its page. Pure functions so the
 * rules can be tested without a launcher. Locked snippets never appear: a widget sits on the
 * home screen, where the text would be readable by anyone holding the phone.
 */
object WidgetData {
    /** Text snippets for the Snippets widget, in display order. */
    fun snippets(all: List<Snippet>, config: SnippetsConfig): List<Snippet> {
        val text = all.filter { it.type == SnippetType.TEXT && !it.locked }
        return when (config.source) {
            SnippetSource.MOST_USED -> text.sortedWith(compareByDescending<Snippet> { it.useCount }.thenByDescending { it.lastActivity })
            SnippetSource.RECENT -> text.sortedByDescending { it.lastActivity }
            SnippetSource.PINNED -> text.filter { it.pinned }.sortedByDescending { it.lastActivity }
            SnippetSource.FOLDER -> text.filter { it.folderId == config.folderId && config.folderId != null }
                .sortedWith(compareByDescending<Snippet> { it.pinned }.thenBy { it.name.lowercase() })
            SnippetSource.PICKED -> config.pickedIds.mapNotNull { id -> text.firstOrNull { it.id == id } }
        }
    }

    /** Media and documents for the Files rack, in display order. */
    fun files(all: List<Snippet>, config: RackConfig): List<Snippet> {
        val files = all.filter { it.type.isFile }
        val picked = when (config.source) {
            RackSource.ALL -> files
            RackSource.MEDIA -> files.filter { it.type == SnippetType.MEDIA }
            RackSource.DOCUMENTS -> files.filter { it.type == SnippetType.DOCUMENT }
            RackSource.FOLDER -> files.filter { it.folderId == config.folderId && config.folderId != null }
            RackSource.PICKED -> return config.pickedIds.mapNotNull { id -> files.firstOrNull { it.id == id } }
        }
        return when (config.sort) {
            RackSort.RECENT -> picked.sortedByDescending { it.lastActivity }
            RackSort.MOST_USED -> picked.sortedWith(compareByDescending<Snippet> { it.useCount }.thenByDescending { it.lastActivity })
            RackSort.NAME -> picked.sortedBy { it.name.lowercase() }
        }
    }

    /** How many fixed-size cells fit in [available] dp, at least one. */
    fun fit(available: Float, cell: Float, gap: Float = 0f): Int =
        ((available + gap) / (cell + gap)).toInt().coerceAtLeast(1)

    fun pageCount(items: Int, perPage: Int): Int = if (items <= 0) 1 else ceil(items / perPage.toDouble()).toInt()

    /** The items on [page] (0-based; out-of-range pages clamp to the last one). */
    fun <T> page(items: List<T>, perPage: Int, page: Int): List<T> {
        val p = page.coerceIn(0, pageCount(items.size, perPage) - 1)
        return items.drop(p * perPage).take(perPage)
    }

    /**
     * What decides whether a widget needs redrawing. Use counts and last-used times are left out
     * on purpose: tapping a tile bumps them, and the list shouldn't reshuffle under your finger.
     */
    fun signature(all: List<Snippet>): Int =
        all.map { listOf(it.id, it.name, it.keyword, it.type, it.pinned, it.folderId, it.locked, it.filePath, it.content.length, it.createdAt) }.hashCode()
}
