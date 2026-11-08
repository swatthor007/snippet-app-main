package com.sockjaw.clipsy.ui.library

import androidx.compose.foundation.layout.heightIn
import com.sockjaw.clipsy.ui.components.FadeScaleVisibility
import com.sockjaw.clipsy.ui.components.PrimaryButton
import com.sockjaw.clipsy.ui.components.SegmentedToggle
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.SheetTitle
import com.sockjaw.clipsy.ui.components.ClipsySheet
import compose.icons.tablericons.Check
import compose.icons.tablericons.PinnedOff
import compose.icons.tablericons.AdjustmentsHorizontal
import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.sockjaw.clipsy.ui.components.tint
import com.sockjaw.clipsy.ui.components.vector
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.LibraryView
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.service.Share
import com.sockjaw.clipsy.ui.components.ActionSheet
import com.sockjaw.clipsy.ui.components.CardShape
import com.sockjaw.clipsy.ui.components.CompactSnippetRow
import com.sockjaw.clipsy.ui.components.FilterPill
import com.sockjaw.clipsy.ui.components.FolderNameSheet
import com.sockjaw.clipsy.ui.components.MoveToFolderSheet
import com.sockjaw.clipsy.ui.components.OptionSheet
import com.sockjaw.clipsy.ui.components.PillSearchField
import com.sockjaw.clipsy.ui.components.ReportAtTop
import com.sockjaw.clipsy.ui.components.RoundIconButton
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SheetAction
import com.sockjaw.clipsy.ui.components.SnippetCard
import com.sockjaw.clipsy.ui.components.SwipeSnippetRow
import com.sockjaw.clipsy.ui.components.TabBarHeight
import com.sockjaw.clipsy.ui.components.bleed
import com.sockjaw.clipsy.ui.components.TabContentPadding
import com.sockjaw.clipsy.ui.components.TabTitle
import com.sockjaw.clipsy.ui.components.TabTitleGap
import com.sockjaw.clipsy.ui.components.TextAction
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.components.cardSegment
import com.sockjaw.clipsy.ui.components.screenRoot
import com.sockjaw.clipsy.ui.navigation.vmFactory
import com.sockjaw.clipsy.ui.lock.LocalUnlocker
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.riseIn
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Dots
import compose.icons.tablericons.Folder
import compose.icons.tablericons.FolderPlus
import compose.icons.tablericons.LayoutGrid
import compose.icons.tablericons.LayoutList
import compose.icons.tablericons.List
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Pin
import compose.icons.tablericons.Search
import compose.icons.tablericons.Share
import compose.icons.tablericons.Stack2
import compose.icons.tablericons.Trash

private fun LazyGridScope.fullWidth(key: Any, content: @Composable LazyGridItemScope.() -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)

/**
 * The whole library at the root (with a strip of folders on top), or one folder's snippets when
 * [folderId] is set. Long-press selects and drags; the action bar and drop dock appear with it.
 */
@Composable
fun LibraryScreen(
    container: AppContainer,
    folderId: Long?,
    onOpen: (Snippet) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onBack: () -> Unit,
    onScrolledToTop: (Boolean) -> Unit,
) {
    val vm: LibraryViewModel = viewModel(
        key = "library$folderId",
        factory = vmFactory { LibraryViewModel(folderId, container.visibleSnippets, container.visibleFolders, container.repository, container.settings) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val repo = container.repository

    var sortSheet by remember { mutableStateOf(false) }
    var moveSheet by remember { mutableStateOf(false) }
    var newFolderSheet by remember { mutableStateOf(false) }
    var renameSheet by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var deleteFolderSheet by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val searchActive = searchFocused || state.query.isNotBlank()
    LaunchedEffect(searchActive) { container.searching.value = searchActive }
    DisposableEffect(Unit) { onDispose { container.searching.value = false } }

    val gridState = rememberLazyGridState()
    ReportAtTop(gridState, onScrolledToTop)

    LaunchedEffect(state.selecting) { container.librarySelecting.value = state.selecting }
    DisposableEffect(Unit) { onDispose { container.librarySelecting.value = false } }
    BackHandler(state.selecting) { vm.clearSelection() }
    // A folder that was deleted (and is waiting out its undo window) takes its screen with it.
    LaunchedEffect(state.loaded, state.currentFolder) {
        if (folderId != null && state.loaded && state.currentFolder == null) onBack()
    }

    val viewChanged = state.sort != LibrarySort.RECENT || state.view != LibraryView.LIST
    val viewDescription = "Sort and layout. Sorted by ${state.sort.label.lowercase()}, ${state.view.name.lowercase()} view"

    // Links that have never been looked up get their image in the background, so the grid fills in by itself.
    val linkImages = com.sockjaw.clipsy.ui.components.LocalLinkImages.current
    val tried = remember { HashSet<String>() }
    LaunchedEffect(state.items, linkImages.size) {
        for (s in state.items) {
            if (s.locked || !s.isLink) continue
            val key = container.links.key(com.sockjaw.clipsy.data.Links.parse(s.content) ?: continue)
            if (key in linkImages || !tried.add(key)) continue
            container.links.load(key)
            if (tried.size >= 40) break
        }
    }

    val folderNames = remember(state.folders) { state.folders.associate { it.folder.id to it.folder.name } }
    val folderList = remember(state.folders) { state.folders.map { it.folder } }
    val chosen = state.items.filter { it.id in state.selection }
    val view = state.view

    fun dropInto(ids: List<Long>, target: Long?) {
        vm.move(ids, target)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val where = target?.let { folderNames[it] }
        container.toaster.show(if (where == null) "Moved ${ids.size} out of folders" else "Moved ${ids.size} to $where", ToastKind.Success)
    }

    fun dragIds(s: Snippet): List<Long> {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.select(s.id)
        return (state.selection + s.id).toList()
    }

    val unlocker = LocalUnlocker.current

    fun doCopy(list: List<Snippet>) {
        val r = Share.copy(context, list)
        if (r.copied > 0) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        container.toaster.show(
            when {
                r.copied == 0 -> "Couldn’t copy that"
                r.skippedFiles > 0 -> "Copied ${r.copied} text snippets, skipped ${r.skippedFiles} files"
                r.copied == 1 -> "Copied"
                else -> "Copied ${r.copied} snippets"
            },
            if (r.copied > 0) ToastKind.Success else ToastKind.Error,
        )
    }

    // Locked snippets ask the user to sign in first, then carry on with their text filled in.
    fun copy(list: List<Snippet>) = unlocker?.reveal(list, "Copy") { doCopy(it) } ?: doCopy(list)

    fun doShare(list: List<Snippet>) {
        Share.chooser(context, list)?.let(context::startActivity)
            ?: container.toaster.show("Nothing to share", ToastKind.Error)
    }

    fun share(list: List<Snippet>) = unlocker?.reveal(list, "Share") { doShare(it) } ?: doShare(list)

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (view == LibraryView.GRID) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
            modifier = Modifier.screenRoot().snippetDragWatcher { dragging = it },
            state = gridState,
            contentPadding = TabContentPadding,
            verticalArrangement = if (view == LibraryView.GRID) Arrangement.spacedBy(12.dp) else Arrangement.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            fullWidth("title") {
                when {
                    state.selecting -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.selection.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextAction("Select all", vm::selectAll)
                        TextAction("Done", vm::clearSelection, bold = true)
                    }
                    folderId != null -> Row(Modifier.fillMaxWidth().riseIn(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIconButton(TablerIcons.ArrowLeft, "Back", onBack)
                        state.currentFolder?.let { Icon(it.vector, null, tint = it.tint, modifier = Modifier.size(28.dp)) }
                        Text(state.currentFolder?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ViewOptionsButton(viewChanged, viewDescription) { sortSheet = true }
                        RoundIconButton(TablerIcons.Dots, "Folder options", { folderMenu = true })
                    }
                    else -> Row(Modifier.fillMaxWidth().riseIn(0), verticalAlignment = Alignment.CenterVertically) {
                        TabTitle("Library", Modifier.weight(1f))
                        ViewOptionsButton(viewChanged, viewDescription) { sortSheet = true }
                    }
                }
            }
            fullWidth("search") {
                PillSearchField(state.query, vm::setQuery, "Search by name, keyword or content", Modifier.fillMaxWidth().padding(top = TabTitleGap).riseIn(1), onFocusChange = { searchFocused = it })
            }
            if (folderId == null && state.query.isBlank()) {
                fullWidth("folders") {
                    LazyRow(
                        Modifier.bleed(20.dp).padding(top = 16.dp).riseIn(2),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.folders, key = { it.folder.id }) { t ->
                            FolderTileCard(t, onClick = { onOpenFolder(t.folder.id) }, onDropIds = { dropInto(it, t.folder.id) })
                        }
                        item(key = "new-folder") { NewFolderTile { newFolderSheet = true } }
                    }
                }
            }
            fullWidth("filters") {
                LazyRow(
                    Modifier.bleed(20.dp).padding(top = 4.dp, bottom = 4.dp).riseIn(2),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LibraryFilter.entries) { f ->
                        FilterPill("${f.label} ${state.counts[f] ?: 0}", state.filter == f) { vm.setFilter(f) }
                    }
                }
            }
            if (state.loaded && state.items.isEmpty()) {
                fullWidth("empty") {
                    val searching = state.query.isNotBlank() || state.filter != LibraryFilter.ALL
                    Column(
                        Modifier.animateItem().fillMaxWidth().padding(vertical = 32.dp).riseIn(4),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(if (searching) TablerIcons.Search else if (folderId != null) TablerIcons.Folder else TablerIcons.Stack2, null, tint = ClipsyColors.TextTertiary, modifier = Modifier.size(28.dp))
                        Text(
                            if (searching) "No snippets match" else if (folderId != null) "This folder is empty" else "Your library is empty",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                searching -> "Try another filter or search."
                                folderId != null -> "Tap New snippet to add one, or drag snippets here."
                                else -> "Tap New snippet to add one."
                            },
                            color = ClipsyColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            itemsIndexed(state.items, key = { _, s -> s.id }) { i, s ->
                val selected = s.id in state.selection
                val drag = Modifier.snippetDragSource { dragIds(s) }
                val click = { if (state.selecting) vm.toggleSelect(s.id) else onOpen(s) }
                val where = if (folderId == null) folderNames[s.folderId] else null
                when (view) {
                    // Each row is its own item so filtering, sorting and deleting animate row by row.
                    LibraryView.LIST -> Column(Modifier.animateItem().cardSegment(i == 0, i == state.items.lastIndex).then(drag).riseIn(4 + i)) {
                        if (i > 0) RowDivider(startPadding = 64.dp)
                        SwipeSnippetRow(
                            s,
                            onClick = click,
                            onTogglePin = { vm.togglePin(s) },
                            onDelete = { container.deleteWithUndo(s) },
                            folderName = where,
                            selecting = state.selecting,
                            selected = selected,
                            onCopy = { copy(listOf(s)) },
                            onShare = { share(listOf(s)) },
                        )
                    }
                    LibraryView.COMPACT -> Column(Modifier.animateItem().cardSegment(i == 0, i == state.items.lastIndex).then(drag).riseIn(4 + i)) {
                        if (i > 0) RowDivider(startPadding = 52.dp)
                        CompactSnippetRow(s, state.selecting, selected, click, onCopy = { copy(listOf(s)) }, onShare = { share(listOf(s)) })
                    }
                    LibraryView.GRID -> SnippetCard(s, where, state.selecting, selected, click, { copy(listOf(s)) }, { share(listOf(s)) }, Modifier.animateItem().then(drag))
                }
            }
            // Room for the New snippet button above the tab bar.
            fullWidth("spacer") { Spacer(Modifier.height(TabBarHeight + 88.dp)) }
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                visible = dragging,
                enter = slideInVertically(Motion.enter()) { it } + fadeIn(Motion.enter(Motion.BASE)),
                exit = slideOutVertically(Motion.exit()) { it } + fadeOut(Motion.exit()),
            ) {
                DropDock(state.folders.filter { it.folder.id != folderId }, showNoFolder = folderId != null, onDrop = ::dropInto)
            }
            AnimatedVisibility(
                visible = state.selecting && !dragging,
                enter = slideInVertically(Motion.enter()) { it } + fadeIn(Motion.enter(Motion.BASE)),
                exit = slideOutVertically(Motion.exit()) { it } + fadeOut(Motion.exit()),
            ) {
                SelectionBar(
                    allPinned = chosen.isNotEmpty() && chosen.all { it.pinned },
                    onMove = { moveSheet = true },
                    onPin = vm::pinSelected,
                    onCopy = { copy(chosen) },
                    onShare = { share(chosen) },
                    onDelete = { container.deleteWithUndo(chosen); vm.clearSelection() },
                )
            }
        }
    }

    if (sortSheet) {
        ViewOptionsSheet(state.sort, vm::setSort, view, vm::setView) { sortSheet = false }
    }
    if (moveSheet) {
        val common = chosen.map { it.folderId }.distinct().singleOrNull()
        MoveToFolderSheet(
            repo, folderList, common,
            onPick = { vm.move(state.selection, it) },
            onDismiss = { moveSheet = false },
            title = "Move ${chosen.size} to folder",
        )
    }
    if (newFolderSheet) {
        FolderNameSheet(repo, null, onDone = {}, onDismiss = { newFolderSheet = false })
    }
    val current = state.currentFolder
    if (renameSheet && current != null) {
        FolderNameSheet(repo, current, onDone = {}, onDismiss = { renameSheet = false })
    }
    if (folderMenu && current != null) {
        ActionSheet(
            title = current.name,
            message = null,
            actions = listOf(
                SheetAction("Edit folder", TablerIcons.Pencil) { renameSheet = true },
                SheetAction("Delete folder", TablerIcons.Trash, destructive = true) { deleteFolderSheet = true },
            ),
            onDismiss = { folderMenu = false },
        )
    }
    if (deleteFolderSheet && current != null) {
        val count = state.counts[LibraryFilter.ALL] ?: 0
        ActionSheet(
            title = "Delete “${current.name}”?",
            message = "You can undo this for a few seconds.",
            actions = buildList {
                add(SheetAction("Delete folder, keep snippets", TablerIcons.Folder) { container.deleteFolderWithUndo(current, withSnippets = false) })
                if (count > 0) add(SheetAction("Delete folder and its $count snippets", TablerIcons.Trash, destructive = true) { container.deleteFolderWithUndo(current, withSnippets = true) })
            },
            onDismiss = { deleteFolderSheet = false },
        )
    }
}

/** Sort and layout, in the header beside the title, styled like the other round header buttons. A dot shows when either isn't the default. */
@Composable
private fun ViewOptionsButton(changed: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).pressable(pressedScale = 0.92f, onClickLabel = "Change sort and layout", onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(ClipsyColors.CardAlt), contentAlignment = Alignment.Center) {
            Icon(TablerIcons.AdjustmentsHorizontal, null, tint = ClipsyColors.Text, modifier = Modifier.size(20.dp))
        }
        if (changed) Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(10.dp).clip(CircleShape).background(ClipsyColors.Accent).border(2.dp, ClipsyColors.Background, CircleShape))
    }
}

/** Sort order and layout in one sheet; changes apply as you tap. */
@Composable
private fun ViewOptionsSheet(sort: LibrarySort, onSort: (LibrarySort) -> Unit, view: LibraryView, onView: (LibraryView) -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    ClipsySheet(onDismiss) { close ->
        SheetTitle("Sort and layout")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Sort by")
                GroupedCard {
                    LibrarySort.entries.forEachIndexed { i, option ->
                        if (i > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable(role = Role.RadioButton, pressedScale = 0.985f) { onSort(option) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            FadeScaleVisibility(option == sort) { Icon(TablerIcons.Check, "Selected", tint = ClipsyColors.Accent, modifier = Modifier.size(22.dp)) }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Layout")
                SegmentedToggle(
                    listOf("List", "Compact", "Grid"),
                    LibraryView.entries.indexOf(view),
                    { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onView(LibraryView.entries[it]) },
                )
            }
            PrimaryButton("Done", { close {} })
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent

@Composable
private fun FolderTileCard(tile: FolderTile, onClick: () -> Unit, onDropIds: (List<Long>) -> Unit) {
    var hover by remember { mutableStateOf(false) }
    val tint = tile.folder.tint
    val bg by animateColorAsState(if (hover) tint.copy(alpha = 0.16f) else ClipsyColors.Card, Motion.standard(), label = "tileBg")
    val rim by animateColorAsState(if (hover) tint else ClipsyColors.CardBorder, Motion.standard(), label = "tileRim")
    val lift by animateFloatAsState(if (hover) 1.04f else 1f, Motion.settle(), label = "tileLift")
    Row(
        Modifier
            .width(164.dp)
            .heightIn(min = 60.dp)
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(CardShape)
            .background(bg)
            .border(1.dp, rim, CardShape)
            .snippetDropTarget({ hover = it }, onDropIds)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(tile.folder.vector, null, tint = tile.folder.tint, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(tile.folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (tile.count == 1) "1 snippet" else "${tile.count} snippets", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
        }
    }
}

@Composable
private fun NewFolderTile(onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 60.dp)
            .clip(CardShape)
            .border(1.dp, ClipsyColors.Handle, CardShape)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(TablerIcons.FolderPlus, null, tint = ClipsyColors.Accent, modifier = Modifier.size(20.dp))
        Text("New folder", color = ClipsyColors.Accent, fontSize = ClipsyType.Label)
    }
}

/** Shown while dragging: one target per folder (and "No folder" when inside one). */
@Composable
private fun DropDock(folders: List<FolderTile>, showNoFolder: Boolean, onDrop: (List<Long>, Long?) -> Unit) {
    Column(Modifier.fillMaxWidth().background(ClipsyColors.NavBar).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ClipsyColors.Separator))
        Text("Drop on a folder to move", Modifier.padding(start = 20.dp, top = 10.dp), color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showNoFolder) item(key = "none") { DockChip("No folder", TablerIcons.Folder, ClipsyColors.TextSecondary) { onDrop(it, null) } }
            items(folders, key = { it.folder.id }) { t -> DockChip(t.folder.name, t.folder.vector, t.folder.tint) { onDrop(it, t.folder.id) } }
            if (!showNoFolder && folders.isEmpty()) item(key = "hint") {
                Text("Create a folder first to drop snippets into it", color = ClipsyColors.TextTertiary, fontSize = ClipsyType.Caption, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun DockChip(label: String, icon: ImageVector, tint: Color, onDrop: (List<Long>) -> Unit) {
    var hover by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (hover) tint.copy(alpha = 0.16f) else ClipsyColors.Card, Motion.standard(), label = "chipBg")
    val rim by animateColorAsState(if (hover) tint else ClipsyColors.Handle, Motion.standard(), label = "chipRim")
    val lift by animateFloatAsState(if (hover) 1.06f else 1f, Motion.settle(), label = "chipLift")
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, rim, CircleShape)
            .snippetDropTarget({ hover = it }, onDrop)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, fontSize = ClipsyType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SelectionBar(allPinned: Boolean, onMove: () -> Unit, onPin: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ClipsyColors.NavBar).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ClipsyColors.Separator))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            BarAction(TablerIcons.Folder, "Move", onMove)
            BarAction(if (allPinned) TablerIcons.PinnedOff else TablerIcons.Pin, if (allPinned) "Unpin" else "Pin", onPin)
            BarAction(TablerIcons.Copy, "Copy", onCopy)
            BarAction(TablerIcons.Share, "Share", onShare)
            BarAction(TablerIcons.Trash, "Delete", onDelete, danger = true)
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) ClipsyColors.Danger else ClipsyColors.Accent
    Column(
        Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).pressable(pressedScale = 0.94f, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = ClipsyType.Micro)
    }
}
