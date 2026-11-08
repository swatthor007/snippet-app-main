package com.sockjaw.clipsy.ui.home

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.Clipboard
import compose.icons.tablericons.Notes
import compose.icons.tablericons.Paperclip
import compose.icons.tablericons.Search
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.Stats
import com.sockjaw.clipsy.ui.clipboard.ClipboardSheet
import com.sockjaw.clipsy.ui.components.GreenFab
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.PillSearchField
import com.sockjaw.clipsy.ui.components.RecentRow
import com.sockjaw.clipsy.ui.components.DailyBars
import com.sockjaw.clipsy.service.Share
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.os.Build
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.TabBarHeight
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.components.CardShape
import com.sockjaw.clipsy.ui.components.cardSegment
import com.sockjaw.clipsy.ui.components.TabContentPadding
import com.sockjaw.clipsy.ui.components.ReportAtTop
import androidx.compose.foundation.lazy.rememberLazyListState
import com.sockjaw.clipsy.ui.components.rememberFilePicker
import com.sockjaw.clipsy.ui.components.screenRoot
import com.sockjaw.clipsy.ui.navigation.vmFactory
import com.sockjaw.clipsy.ui.lock.LocalUnlocker
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.riseIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpen: (Snippet) -> Unit,
    onOpenFile: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onScrolledToTop: (Boolean) -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(container.visibleSnippets, container.repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val unlocker = LocalUnlocker.current

    // Read before the first frame so the capture card opens in the right state instead of swapping.
    var clipboard by remember { mutableStateOf(readClipboardText(context)) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var dragHovering by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { clipboard = readClipboardText(context) }

    fun importFile(uri: Uri) = scope.launch {
        runCatching { container.repository.importFile(uri) }
            .onSuccess { onOpenFile(it) }
            .onFailure { container.toaster.show("Couldn’t read that file", ToastKind.Error) }
    }

    val pickFile = rememberFilePicker(
        onPicked = { importFile(it) },
        onUnavailable = { container.toaster.show("No file picker on this device", ToastKind.Error) },
    )

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragHovering = true }
            override fun onExited(event: DragAndDropEvent) { dragHovering = false }
            override fun onEnded(event: DragAndDropEvent) { dragHovering = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragHovering = false
                val drag = event.toAndroidDragEvent()
                val uri = drag.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return false
                (context as? Activity)?.requestDragAndDropPermissions(drag)
                importFile(uri)
                return true
            }
        }
    }

    val searching = state.query.isNotBlank()
    var searchFocused by remember { mutableStateOf(false) }
    val searchActive = searchFocused || searching
    LaunchedEffect(searchActive) { container.searching.value = searchActive }
    DisposableEffect(Unit) { onDispose { container.searching.value = false } }
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    // Offer only once the list has loaded, so text that's already saved never flashes up.
    val clip = clipboard?.takeIf { state.loaded && it.trim() !in state.contents && it.hashCode() != settings.dismissedClip }
    // A copied link is looked up as soon as the prompt appears, so the prompt (and the sheet after it)
    // already know the page's title and image by the time you tap Add.
    val clipLink = remember(clip) { clip?.let { Links.parse(it) }?.let(container.links::key) }
    val clipPreview by remember(clipLink) { clipLink?.let(container.links::observe) ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsStateWithLifecycle(null)
    LaunchedEffect(clipLink, settings.linkPreviews) { if (clipLink != null && settings.linkPreviews) container.links.load(clipLink) }
    val listState = rememberLazyListState()
    ReportAtTop(listState, onScrolledToTop)

    LazyColumn(
        Modifier.screenRoot(),
        state = listState,
        contentPadding = TabContentPadding,
    ) {
        item(key = "header") {
            PillSearchField(state.query, vm::setQuery, "Search snippets", Modifier.fillMaxWidth().riseIn(0), onFocusChange = { searchFocused = it })
        }
        if (!searching) {
            item(key = "saved") { SavedCard(state, Modifier.animateItem().padding(top = 20.dp).riseIn(1)) }
            item(key = "capture") {
                CaptureSlot(
                    clipboard = clip,
                    hovering = dragHovering,
                    onChooseFile = pickFile,
                    onAddClipboard = { showSheet = true },
                    linkPreview = clipPreview?.takeIf { settings.linkPreviews },
                    onDismissClipboard = { clip?.let { c -> container.settings.update { it.copy(dismissedClip = c.hashCode()) } } },
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 20.dp)
                        .riseIn(2)
                        .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
                )
            }
        }
        item(key = "recentHeader") {
            Row(
                Modifier.animateItem().fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 10.dp).riseIn(3),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (searching) "Results" else "Recent", style = MaterialTheme.typography.titleLarge)
                if (!searching && state.total > 0) {
                    Text(
                        "All ${state.total}",
                        color = ClipsyColors.Accent,
                        fontSize = ClipsyType.Label,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onOpenLibrary).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 8.dp),
                    )
                }
            }
        }
        if (state.loaded && state.rows.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    if (searching) "Nothing matches “${state.query}”" else "No snippets yet",
                    if (searching) "Try a name, keyword or a word from the text." else "Tap New snippet to save your first one.",
                    searching,
                    Modifier.animateItem(),
                )
            }
        }
        itemsIndexed(state.rows, key = { _, s -> s.id }) { i, s ->
            Column(Modifier.animateItem().cardSegment(i == 0, i == state.rows.lastIndex).riseIn(4 + i)) {
                if (i > 0) RowDivider(startPadding = 60.dp)
                val copyRow = {
                    fun copy(shown: Snippet) {
                        val r = Share.copy(context, listOf(shown))
                        // Android 13+ shows its own clipboard confirmation.
                        if (r.copied > 0 && Build.VERSION.SDK_INT < 33) container.toaster.show("Copied “${s.name}”", ToastKind.Success)
                    }
                    unlocker?.reveal(s, "Copy “${s.name}”") { copy(it) } ?: copy(s)
                }
                RecentRow(
                    s,
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); copyRow() },
                    onCopy = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); copyRow() },
                ) { onOpen(s) }
            }
        }
        // Room for the New snippet button above the tab bar.
        item(key = "spacer") { Spacer(Modifier.height(TabBarHeight + 88.dp)) }
    }

    val sheetText = clipboard
    if (showSheet && sheetText != null) {
        ClipboardSheet(
            container,
            sheetText,
            onDismiss = { showSheet = false },
            onSaved = { name -> container.toaster.show("Saved “$name”", ToastKind.Success) },
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String, searching: Boolean, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp).riseIn(4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (searching) TablerIcons.Search else TablerIcons.Notes, null, tint = ClipsyColors.TextTertiary, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun SavedCard(state: HomeState, modifier: Modifier) {
    // Count up to the month's total the first time it's shown.
    val chars = remember { Animatable(0f) }
    LaunchedEffect(state.monthChars) { chars.animateTo(state.monthChars.toFloat(), tween(900, easing = Motion.Emphasized)) }
    GroupedCard(modifier) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Time saved this month", color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    Stats.formatSaved(chars.value.toLong()),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { contentDescription = "Time saved this month: ${Stats.formatSaved(state.monthChars)}" },
                )
                Text(
                    if (state.monthChars == 0L) "Expansions you use will show up here" else "${Stats.formatChars(state.monthChars)} characters you didn’t type, at an average 40 wpm",
                    color = ClipsyColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            DailyBars(state.daily, Modifier.size(width = 104.dp, height = 64.dp))
        }
    }
}
