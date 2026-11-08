package com.sockjaw.clipsy.ui.detail

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import com.sockjaw.clipsy.ui.components.RoundIconButton
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Hash
import com.sockjaw.clipsy.service.Template
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.sockjaw.clipsy.ui.components.FieldRow
import com.sockjaw.clipsy.ui.components.InlineContentEditor
import com.sockjaw.clipsy.ui.components.keepAboveKeyboard
import com.sockjaw.clipsy.ui.components.TokenText
import androidx.compose.foundation.layout.imePadding
import compose.icons.tablericons.ExternalLink
import compose.icons.tablericons.Share
import android.content.Intent
import androidx.core.net.toUri
import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.ui.components.InsecureLinkSheet
import com.sockjaw.clipsy.service.Share as ShareSheet
import com.sockjaw.clipsy.ui.components.InlineKeywordEditor
import com.sockjaw.clipsy.ui.components.MoveToFolderSheet
import com.sockjaw.clipsy.data.Keyword
import compose.icons.tablericons.Trash
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import com.sockjaw.clipsy.ui.components.ActionSheet
import com.sockjaw.clipsy.ui.components.BehaviourCard
import com.sockjaw.clipsy.ui.components.UsageSection
import com.sockjaw.clipsy.ui.components.CrossfadeText
import com.sockjaw.clipsy.ui.components.SheetAction
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.riseIn
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetRepository
import com.sockjaw.clipsy.data.Stats
import com.sockjaw.clipsy.data.UsageEvent
import com.sockjaw.clipsy.data.SettingsStore
import com.sockjaw.clipsy.ui.components.ClipsySwitch
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.SwitchRow
import com.sockjaw.clipsy.ui.components.TextAction
import com.sockjaw.clipsy.ui.components.ValueRow
import com.sockjaw.clipsy.ui.components.ChoiceRow
import com.sockjaw.clipsy.ui.components.screenRoot
import com.sockjaw.clipsy.ui.navigation.vmFactory
import com.sockjaw.clipsy.ui.lock.LocalUnlocker
import com.sockjaw.clipsy.ui.components.ImageLinkPrompt
import com.sockjaw.clipsy.ui.components.LinkPreviewCard
import com.sockjaw.clipsy.ui.components.LinkPeekOverlay
import com.sockjaw.clipsy.ui.components.PullToPreview
import com.sockjaw.clipsy.ui.components.linkPeekBackdrop
import com.sockjaw.clipsy.ui.components.linkPeekSource
import com.sockjaw.clipsy.ui.components.rememberLinkPeekState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.sockjaw.clipsy.ui.lock.LockedPanel
import com.sockjaw.clipsy.ui.lock.SecureWindow
import compose.icons.tablericons.Lock
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Mono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

data class DetailState(
    val loaded: Boolean = false,
    val snippet: Snippet? = null,
    val events: List<UsageEvent> = emptyList(),
    val excludedApps: Int = 0,
    val folders: List<Folder> = emptyList(),
)

class DetailViewModel(
    private val id: Long,
    private val repo: SnippetRepository,
    settings: SettingsStore,
) : ViewModel() {
    val state: StateFlow<DetailState> = combine(
        repo.snippet(id),
        repo.usageFor(id, System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000),
        settings.settings,
        repo.folders,
    ) { s, events, cfg, folders -> DetailState(true, s, events, cfg.excludedApps.size, folders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    fun togglePin() { state.value.snippet?.let { s -> viewModelScope.launch { repo.setPinned(s.id, !s.pinned) } } }
    fun setExpandAfter(e: ExpandAfter) { state.value.snippet?.let { s -> viewModelScope.launch { repo.save(s.copy(expandAfter = e)) } } }
    fun setKeepSpace(on: Boolean) { state.value.snippet?.let { s -> viewModelScope.launch { repo.save(s.copy(keepSpace = on)) } } }
    fun setName(name: String) { state.value.snippet?.let { s -> viewModelScope.launch { repo.save(s.copy(name = name.trim())) } } }
    fun setContent(content: String) {
        state.value.snippet?.let { s ->
            // Locked text is sealed again; this needs the vault open, which the screen has just made sure of.
            viewModelScope.launch { runCatching { if (s.locked) repo.saveLockedText(s, content) else repo.save(s.copy(content = content)) } }
        }
    }
    fun lock(text: String) { state.value.snippet?.let { s -> viewModelScope.launch { runCatching { repo.lock(s, text) } } } }
    fun unlockPermanently() { state.value.snippet?.let { s -> viewModelScope.launch { runCatching { repo.unlockPermanently(s) } } } }
    fun setExpandUnlocked(on: Boolean) {
        state.value.snippet?.let { s -> viewModelScope.launch { runCatching { repo.reveal(s)?.let { repo.lock(s, it, on) } } } }
    }
    fun setKeyword(keyword: String) { state.value.snippet?.let { s -> viewModelScope.launch { repo.save(s.copy(keyword = keyword)) } } }
    fun moveTo(folderId: Long?) { state.value.snippet?.let { s -> viewModelScope.launch { repo.move(listOf(s.id), folderId) } } }
}

@Composable
fun DetailScreen(container: AppContainer, id: Long, onBack: () -> Unit, onConverted: (Long) -> Unit = {}) {
    val vm: DetailViewModel = viewModel(key = "detail$id", factory = vmFactory { DetailViewModel(id, container.repository, container.settings) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var editingKeyword by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    var editingContent by remember { mutableStateOf(false) }
    var insecureEdit by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf(false) }
    var folderSheet by remember { mutableStateOf(false) }
    var resetCounter by remember { mutableStateOf(false) }
    val allSnippets by container.visibleSnippets.collectAsStateWithLifecycle(emptyList())

    val unlocker = LocalUnlocker.current
    val vaultState by container.vault.state.collectAsStateWithLifecycle()
    // The text of a locked snippet, once the user has signed in; dropped again when the vault locks.
    var revealed by remember(id) { mutableStateOf<String?>(null) }
    var confirmExpand by remember { mutableStateOf(false) }
    LaunchedEffect(vaultState.unlocked) { if (!vaultState.unlocked) revealed = null }

    val snippet = state.snippet
    var leaving by remember { mutableStateOf(false) }

    // Link preview: what the link points at, read once and kept. Never for locked snippets.
    val appSettings by container.settings.settings.collectAsStateWithLifecycle()
    val links = container.links
    val linkUrl = remember(snippet?.content, snippet?.locked) {
        snippet?.takeIf { !it.locked && it.isLink }?.let { links.key(Links.parse(it.content)!!) }
    }
    val previewing = linkUrl != null && appSettings.linkPreviews
    val preview by remember(linkUrl) { if (linkUrl != null) links.observe(linkUrl) else kotlinx.coroutines.flow.flowOf(null) }.collectAsStateWithLifecycle(null)
    var previewLoading by remember(linkUrl) { mutableStateOf(false) }
    var savingMedia by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun refreshPreview(force: Boolean) {
        val u = linkUrl ?: return
        scope.launch { previewLoading = true; links.load(u, force); previewLoading = false }
    }
    LaunchedEffect(linkUrl, previewing) { if (previewing) refreshPreview(force = false) }
    // A link saved with only its host as a name takes the page's own title.
    LaunchedEffect(preview?.title, snippet?.name) {
        val title = preview?.takeIf { it.status == com.sockjaw.clipsy.data.PreviewStatus.OK }?.title ?: return@LaunchedEffect
        val current = snippet ?: return@LaunchedEffect
        if (linkUrl != null && current.name.equals(Links.host(linkUrl), ignoreCase = true) && !title.equals(current.name, ignoreCase = true)) vm.setName(title.take(60))
    }
    LaunchedEffect(state.loaded, snippet) { if (state.loaded && snippet == null && !leaving) onBack() }
    if (snippet == null) return
    val text = if (snippet.locked) revealed.orEmpty() else snippet.content
    val hidden = snippet.locked && revealed == null
    SecureWindow(snippet.locked && revealed != null)
    fun saveContent(t: String) {
        val save = { vm.setContent(t); if (snippet.locked) revealed = t; editingContent = false }
        if (snippet.locked) unlocker?.require("Save changes", action = save) ?: save() else save()
    }
    /** Runs [use] with the snippet's text filled in, signing in first when it's locked. */
    fun withText(why: String, use: (Snippet) -> Unit) = unlocker?.reveal(snippet, why, use = use) ?: use(snippet)
    fun unlockToView() = unlocker?.require("Show “${snippet.name}”") {
        revealed = snippet.sealedContent?.let(container.vault::open)
        if (revealed == null) container.toaster.show("Couldn’t open that snippet", ToastKind.Error)
    }

    val peek = rememberLinkPeekState()
    val canPeek = previewing && preview?.status == com.sockjaw.clipsy.data.PreviewStatus.OK && !editingContent && !editingName && !editingKeyword
    fun openLink() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Links.withScheme(snippet.content.trim()).toUri())) }
            .onFailure { container.toaster.show("No app can open that link", ToastKind.Error) }
    }

    fun openPreview() {
        peek.open()
        // Found it on their own: no need to show them.
        container.settings.update { it.copy(previewNudgeSeen = true) }
    }

    Box(Modifier.fillMaxSize()) {
        PullToPreview(
            enabled = canPeek,
            onPreview = ::openPreview,
            modifier = Modifier.screenRoot().linkPeekBackdrop(peek),
            nudge = !appSettings.previewNudgeSeen,
            onNudged = { container.settings.update { it.copy(previewNudgeSeen = true) } },
        ) {
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    RoundIconButton(TablerIcons.ArrowLeft, "Back", onBack)
                    Icon(
                        TablerIcons.Share, "Share",
                        tint = ClipsyColors.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pressable { withText("Share “${snippet.name}”") { s -> ShareSheet.chooser(context, listOf(s))?.let(context::startActivity) } }
                            .padding(10.dp)
                            .size(22.dp),
                    )
                }

                Column(Modifier.riseIn(0), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (editingName) {
                        var name by remember(snippet.id) { mutableStateOf(snippet.name) }
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        val save = { if (name.isNotBlank()) vm.setName(name); editingName = false }
                        GroupedCard(Modifier.keepAboveKeyboard()) {
                            FieldRow(
                                "Name", name, { name = it }, placeholder = "Snippet name", focusRequester = focus,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { save() }),
                                trailing = { TextAction("Done", { save() }, bold = true) },
                            )
                        }
                    } else {
                        Text(
                            snippet.name,
                            Modifier.clip(RoundedCornerShape(10.dp)).pressable(pressedScale = 0.985f) { editingName = true },
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                    val created = DateFormat.format("d MMM yyyy", Date(snippet.createdAt))
                    Text("${if (snippet.locked) "Locked text snippet" else if (snippet.isLink) "Link" else "Text snippet"} · Created $created", color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }

                if (previewing && linkUrl != null) {
                    LinkPreviewCard(
                        linkUrl, preview, previewLoading, onRefresh = { refreshPreview(force = true) },
                        modifier = Modifier.riseIn(1).linkPeekSource(peek),
                        onPreview = ::openPreview,
                    )
                    if (preview?.kind == com.sockjaw.clipsy.data.LinkKind.IMAGE && preview?.keepAsLink == false && preview?.status == com.sockjaw.clipsy.data.PreviewStatus.OK) {
                        ImageLinkPrompt(
                            busy = savingMedia,
                            onSaveAsMedia = {
                                val p = preview ?: return@ImageLinkPrompt
                                // Set before the download starts, so a second tap can't start a second copy.
                                if (savingMedia) return@ImageLinkPrompt
                                savingMedia = true
                                scope.launch {
                                    val converted = links.saveAsMedia(snippet, p)
                                    savingMedia = false
                                    if (converted != null) {
                                        container.toaster.show("Saved as media", ToastKind.Success)
                                        onConverted(converted)
                                    } else {
                                        container.toaster.show("Couldn’t download that image", ToastKind.Error)
                                    }
                                }
                            },
                            onKeepLink = { scope.launch { links.keepAsLink(linkUrl) } },
                            modifier = Modifier.riseIn(1),
                        )
                    }
                }

                GroupedCard(Modifier.riseIn(1)) {
                    if (editingKeyword) {
                        InlineKeywordEditor(
                            snippet.id, snippet.keyword, container.repository, Keyword.suggest(snippet.name).ifEmpty { "meet_link" },
                            onSave = { vm.setKeyword(it); editingKeyword = false },
                            onCancel = { editingKeyword = false },
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                snippet.keyword ?: "Add a keyword",
                                fontFamily = if (snippet.keyword != null) Mono else null,
                                fontSize = ClipsyType.Label,
                                color = if (snippet.keyword != null) ClipsyColors.Accent else ClipsyColors.TextTertiary,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).pressable { editingKeyword = true }.padding(vertical = 4.dp),
                            )
                            if (snippet.isLink) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(8.dp)).pressable { openLink() }.heightIn(min = 48.dp).padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(TablerIcons.ExternalLink, "Open link", tint = ClipsyColors.Accent, modifier = Modifier.size(16.dp))
                                    Text("Open", color = ClipsyColors.Accent, fontSize = ClipsyType.Label)
                                }
                            }
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).pressable {
                                    withText("Copy “${snippet.name}”") { s ->
                                        ShareSheet.copy(context, listOf(s))
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        // Android 13+ shows its own clipboard confirmation.
                                        if (Build.VERSION.SDK_INT < 33) container.toaster.show("Copied", ToastKind.Success)
                                    }
                                }.heightIn(min = 48.dp).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(TablerIcons.Copy, "Copy expansion", tint = ClipsyColors.Accent, modifier = Modifier.size(16.dp))
                                Text("Copy", color = ClipsyColors.Accent, fontSize = ClipsyType.Label)
                            }
                        }
                    }
                    RowDivider()
                    if (hidden) {
                        LockedPanel(onUnlock = { unlockToView() })
                    } else if (editingContent) {
                        InlineContentEditor(
                            text, allSnippets.filter { !it.locked }, snippet.id, Modifier.keepAboveKeyboard(),
                            onSave = { t ->
                                val link = Links.parse(t)
                                if (link != null && Links.isCleartext(link)) insecureEdit = t else saveContent(t)
                            },
                            onCancel = { editingContent = false },
                        )
                    } else {
                        TokenText(
                            text,
                            Modifier.fillMaxWidth().pressable(pressedScale = 0.99f, onClickLabel = "Edit content") { editingContent = true }.padding(16.dp),
                            color = if (snippet.isLink) ClipsyColors.Accent else ClipsyColors.Text,
                        )
                    }
                }

                if (!hidden && Template.usesCounter(text)) {
                    GroupedCard(Modifier.riseIn(2)) {
                        ValueRow("Next {counter}", "${snippet.counter + 1}", icon = TablerIcons.Hash, onClick = { resetCounter = true })
                    }
                }

                BehaviourCard(
                    snippet, state.excludedApps, vm::setExpandAfter, vm::togglePin, vm::setKeepSpace,
                    folderName = state.folders.firstOrNull { it.id == snippet.folderId }?.name,
                    onFolder = { folderSheet = true },
                    modifier = Modifier.riseIn(2),
                    onLockChange = { on ->
                        if (on) {
                            // Signing in first also sets locking up the very first time.
                            unlocker?.require("Lock “${snippet.name}”") { vm.lock(snippet.content); revealed = snippet.content }
                        } else {
                            unlocker?.require("Remove the lock from “${snippet.name}”") { vm.unlockPermanently(); revealed = null }
                        }
                    },
                    onExpandUnlockedChange = { on ->
                        if (on) confirmExpand = true
                        else unlocker?.require("Change this lock") { vm.setExpandUnlocked(false) }
                    },
                )

                UsageSection(snippet, state.events, Modifier.riseIn(3))

                Text(
                    "Delete snippet",
                    Modifier.fillMaxWidth().riseIn(4).clip(RoundedCornerShape(14.dp)).pressable { confirmDelete = true }.padding(vertical = 14.dp),
                    color = ClipsyColors.Danger,
                    fontSize = ClipsyType.Body,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        if (linkUrl != null) LinkPeekOverlay(peek, linkUrl, preview?.title, onOpenExternal = ::openLink)
    }

    if (confirmExpand) {
        ActionSheet(
            title = "Expand without unlocking?",
            message = "The text stays hidden in Clipsy, but anyone who types “${snippet.keyword ?: "its keyword"}” on this phone will get it, even with Clipsy locked.",
            actions = listOf(SheetAction("Allow", TablerIcons.Lock) { unlocker?.require("Change this lock") { vm.setExpandUnlocked(true) } }),
            onDismiss = { confirmExpand = false },
        )
    }

    if (resetCounter) {
        ActionSheet(
            title = "Counter",
            message = "Goes up by one each time this snippet is typed or copied. Next: ${snippet.counter + 1}.",
            actions = listOf(SheetAction("Reset to 1", TablerIcons.Refresh) { container.resetCounter(snippet) }),
            onDismiss = { resetCounter = false },
        )
    }

    if (folderSheet) {
        MoveToFolderSheet(container.repository, state.folders, snippet.folderId, onPick = vm::moveTo, onDismiss = { folderSheet = false })
    }

    insecureEdit?.let { t ->
        InsecureLinkSheet(
            Links.parse(t)!!, "Drop link",
            onUseHttps = { insecureEdit = null; saveContent(Links.toHttps(t)) },
            onDrop = { insecureEdit = null; editingContent = false },
            onDismiss = { insecureEdit = null },
        )
    }

    if (confirmDelete) {
        ActionSheet(
            title = "Delete “${snippet.name}”?",
            message = "You can undo this for a few seconds.",
            actions = listOf(
                SheetAction("Delete snippet", TablerIcons.Trash, destructive = true) {
                    leaving = true
                    container.deleteWithUndo(snippet)
                    onBack()
                },
            ),
            onDismiss = { confirmDelete = false },
        )
    }
}
