package com.sockjaw.clipsy.widget

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What the Snippets widget lists. */
@Serializable
enum class SnippetSource(val label: String, val detail: String) {
    MOST_USED("Most used", "The snippets you reach for most"),
    RECENT("Recently used", "What you used last"),
    PINNED("Pinned", "Snippets you pinned"),
    FOLDER("A folder", "Text snippets from one folder"),
    PICKED("Hand-picked", "Choose up to $MAX_PICKED yourself"),
}

@Serializable
enum class SnippetLayout(val label: String, val detail: String) {
    LIST("List", "Name and keyword, two columns"),
    GRID("Grid", "Icons with short names"),
    BUTTONS("Buttons", "Big, easy to hit"),
}

/** What the Files rack lists. */
@Serializable
enum class RackSource(val label: String, val detail: String) {
    ALL("All files", "Media and documents"),
    MEDIA("Media", "Images and GIFs"),
    DOCUMENTS("Documents", "PDFs and other files"),
    FOLDER("A folder", "Files from one folder"),
    PICKED("Hand-picked", "Choose up to $MAX_PICKED yourself"),
}

@Serializable
enum class RackSort(val label: String) { RECENT("Recently used"), MOST_USED("Most used"), NAME("Name") }

@Serializable
enum class RackLayout(val label: String, val detail: String) {
    SHELF("Shelf", "One row, page through the rest"),
    GRID("Grid", "As many rows as fit"),
    GALLERY("Gallery", "Larger previews"),
}

/** What a tap does. */
@Serializable
enum class TapAction(val label: String, val detail: String) {
    COPY("Copy", "Ready to paste anywhere"),
    SHARE("Share", "Opens the share sheet"),
    OPEN("Open in Clipsy", "Shows the snippet"),
}

@Serializable
enum class WidgetLook(val label: String) { AUTO("Match the phone"), LIGHT("Light"), DARK("Dark") }

const val MAX_PICKED = 8

@Serializable
data class SnippetsConfig(
    val source: SnippetSource = SnippetSource.MOST_USED,
    val folderId: Long? = null,
    val pickedIds: List<Long> = emptyList(),
    val layout: SnippetLayout = SnippetLayout.LIST,
    val tap: TapAction = TapAction.COPY,
    val look: WidgetLook = WidgetLook.AUTO,
    val translucent: Boolean = false,
)

@Serializable
data class RackConfig(
    val source: RackSource = RackSource.ALL,
    val folderId: Long? = null,
    val pickedIds: List<Long> = emptyList(),
    val sort: RackSort = RackSort.RECENT,
    val layout: RackLayout = RackLayout.SHELF,
    val tap: TapAction = TapAction.COPY,
    val look: WidgetLook = WidgetLook.AUTO,
    val translucent: Boolean = false,
)

/** One config per placed widget, kept until the widget is removed. */
class WidgetConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clipsy_widgets", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun snippets(id: Int): SnippetsConfig = read("s_$id") ?: SnippetsConfig()
    fun rack(id: Int): RackConfig = read("r_$id") ?: RackConfig()

    fun hasSnippets(id: Int) = prefs.contains("s_$id")
    fun hasRack(id: Int) = prefs.contains("r_$id")

    fun save(id: Int, config: SnippetsConfig) = prefs.edit().putString("s_$id", json.encodeToString(config)).apply()
    fun save(id: Int, config: RackConfig) = prefs.edit().putString("r_$id", json.encodeToString(config)).apply()

    fun remove(id: Int) = prefs.edit().remove("s_$id").remove("r_$id").apply()

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}
