package com.sockjaw.clipsy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetRepository
import com.sockjaw.clipsy.data.Stats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val loaded: Boolean = false,
    val query: String = "",
    val total: Int = 0,
    val rows: List<Snippet> = emptyList(),
    val monthChars: Long = 0,
    val daily: List<Long> = List(7) { 0 },
    /** Every text snippet's content, so Home doesn't offer to save what's already saved. */
    val contents: Set<String> = emptySet(),
)

class HomeViewModel(snippets: Flow<List<Snippet>>, repo: SnippetRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<HomeState> = combine(
        snippets,
        query,
        repo.usageSince(Stats.startOfMonth()),
        repo.usageSince(Stats.startOfDay(System.currentTimeMillis()) - 6L * 24 * 60 * 60 * 1000),
    ) { all, q, month, week ->
        // Pinned snippets lead Recent, then most recently used.
        val sorted = all.sortedWith(compareByDescending<Snippet> { it.pinned }.thenByDescending { it.lastActivity })
        val rows = if (q.isBlank()) sorted.take(5) else all.filter { it.matches(q) }.sortedByDescending { it.lastActivity }
        HomeState(
            true, q, all.size, rows, month.sumOf { it.charsSaved.toLong() }, Stats.dailyChars(week, 7),
            all.filter { !it.type.isFile }.mapTo(HashSet()) { it.content.trim() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setQuery(q: String) { query.value = q }
}

fun Snippet.matches(q: String): Boolean =
    name.contains(q, ignoreCase = true) ||
        (keyword?.contains(q, ignoreCase = true) == true) ||
        content.contains(q, ignoreCase = true)
