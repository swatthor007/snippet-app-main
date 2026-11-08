package com.sockjaw.clipsy.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.LibraryView
import com.sockjaw.clipsy.data.SettingsStore
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetRepository
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.ui.home.matches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All"), TEXT("Text"), LINKS("Links"), MEDIA("Media"), DOCUMENTS("Documents"), PINNED("Pinned"), LOCKED("Locked")
}
enum class LibrarySort(val label: String) { RECENT("Last used"), NAME("Name"), USES("Most used") }

data class FolderTile(val folder: Folder, val count: Int)

data class LibraryState(
    val loaded: Boolean = false,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val view: LibraryView = LibraryView.LIST,
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    val items: List<Snippet> = emptyList(),
    val folders: List<FolderTile> = emptyList(),
    val currentFolder: Folder? = null,
    val selection: Set<Long> = emptySet(),
) {
    val selecting: Boolean get() = selection.isNotEmpty()
}

/** Snippets in scope: the whole library at the root, or one folder's. */
fun libraryScope(all: List<Snippet>, folderId: Long?): List<Snippet> =
    if (folderId == null) all else all.filter { it.folderId == folderId }

fun Snippet.inFilter(f: LibraryFilter): Boolean = when (f) {
    LibraryFilter.ALL -> true
    LibraryFilter.TEXT -> type == SnippetType.TEXT && !isLink
    LibraryFilter.LINKS -> isLink
    LibraryFilter.MEDIA -> type == SnippetType.MEDIA
    LibraryFilter.DOCUMENTS -> type == SnippetType.DOCUMENT
    LibraryFilter.PINNED -> pinned
    LibraryFilter.LOCKED -> locked
}

fun libraryCounts(scope: List<Snippet>): Map<LibraryFilter, Int> =
    LibraryFilter.entries.associateWith { f -> scope.count { it.inFilter(f) } }

fun libraryItems(all: List<Snippet>, folderId: Long?, filter: LibraryFilter, query: String, sort: LibrarySort): List<Snippet> {
    val filtered = libraryScope(all, folderId).filter { it.inFilter(filter) && (query.isBlank() || it.matches(query)) }
    // Pinned snippets always lead; the chosen sort orders each group.
    val pinnedFirst = compareByDescending<Snippet> { it.pinned }
    return when (sort) {
        LibrarySort.RECENT -> filtered.sortedWith(pinnedFirst.thenByDescending { it.lastActivity })
        LibrarySort.NAME -> filtered.sortedWith(pinnedFirst.thenBy { it.name.lowercase() })
        LibrarySort.USES -> filtered.sortedWith(pinnedFirst.thenByDescending { it.useCount })
    }
}

private data class Params(
    val query: String,
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val selection: Set<Long>,
    val view: LibraryView,
)

class LibraryViewModel(
    private val folderId: Long?,
    snippets: Flow<List<Snippet>>,
    folders: Flow<List<Folder>>,
    private val repo: SnippetRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val sort = MutableStateFlow(LibrarySort.RECENT)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val params = combine(query, filter, sort, selection, settings.settings.map { it.libraryView }) { q, f, s, sel, v ->
        Params(q, f, s, sel, v)
    }

    val state: StateFlow<LibraryState> = combine(snippets, folders, params) { all, fs, p ->
        val scope = libraryScope(all, folderId)
        val ids = all.mapTo(HashSet()) { it.id }
        LibraryState(
            loaded = true,
            query = p.query,
            filter = p.filter,
            sort = p.sort,
            view = p.view,
            counts = libraryCounts(scope),
            items = libraryItems(all, folderId, p.filter, p.query, p.sort),
            folders = fs.map { f -> FolderTile(f, all.count { it.folderId == f.id }) },
            currentFolder = fs.firstOrNull { it.id == folderId },
            // A snippet deleted while selected drops out of the selection.
            selection = p.selection.filterTo(HashSet()) { it in ids },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: LibraryFilter) { filter.value = f }
    fun setSort(s: LibrarySort) { sort.value = s }
    fun setView(v: LibraryView) { settings.update { it.copy(libraryView = v) } }
    fun togglePin(s: Snippet) { viewModelScope.launch { repo.setPinned(s.id, !s.pinned) } }

    fun toggleSelect(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun select(id: Long) { selection.value += id }
    fun selectAll() { selection.value = state.value.items.mapTo(HashSet()) { it.id } }
    fun clearSelection() { selection.value = emptySet() }

    /** Moves [ids] (or the selection) into a folder, or out of any folder when null. */
    fun move(ids: Collection<Long>, folderId: Long?) {
        viewModelScope.launch { repo.move(ids.toList(), folderId) }
        clearSelection()
    }

    /** Pins everything selected, or unpins when all of it is already pinned. */
    fun pinSelected() {
        val chosen = state.value.items.filter { it.id in selection.value }
        val pin = chosen.any { !it.pinned }
        viewModelScope.launch { repo.setPinned(chosen.map { it.id }, pin) }
    }
}
