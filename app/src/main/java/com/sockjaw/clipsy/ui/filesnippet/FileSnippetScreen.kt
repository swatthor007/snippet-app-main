package com.sockjaw.clipsy.ui.filesnippet

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ArrowLeft
import com.sockjaw.clipsy.ui.components.RoundIconButton
import compose.icons.tablericons.Share
import androidx.compose.runtime.rememberCoroutineScope
import com.sockjaw.clipsy.ui.components.rememberShareWarp
import com.sockjaw.clipsy.ui.components.shareWarpTarget
import compose.icons.tablericons.Trash
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.sockjaw.clipsy.ui.components.InsecureLinkSheet
import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.Keyword
import com.sockjaw.clipsy.service.Share as ShareSheet
import com.sockjaw.clipsy.ui.components.InlineKeywordEditor
import com.sockjaw.clipsy.ui.components.keepAboveKeyboard
import com.sockjaw.clipsy.ui.components.MoveToFolderSheet
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.sockjaw.clipsy.service.FileShare
import com.sockjaw.clipsy.ui.components.ActionSheet
import com.sockjaw.clipsy.ui.components.BehaviourCard
import com.sockjaw.clipsy.ui.components.UsageSection
import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.SettingsStore
import com.sockjaw.clipsy.data.UsageEvent
import kotlinx.coroutines.flow.combine
import com.sockjaw.clipsy.ui.components.ExpandingVisibility
import com.sockjaw.clipsy.ui.components.FadeScaleVisibility
import com.sockjaw.clipsy.ui.components.FieldRow
import com.sockjaw.clipsy.ui.components.SheetAction
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.riseIn
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.FileMode
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetRepository
import com.sockjaw.clipsy.data.Stats
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.TextAction
import com.sockjaw.clipsy.ui.components.ValueRow
import com.sockjaw.clipsy.ui.components.fileKind
import com.sockjaw.clipsy.ui.components.screenRoot
import com.sockjaw.clipsy.ui.navigation.vmFactory
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Mono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FileState(
    val loaded: Boolean = false,
    val snippet: Snippet? = null,
    val events: List<UsageEvent> = emptyList(),
    val excludedApps: Int = 0,
    val folders: List<Folder> = emptyList(),
)

class FileSnippetViewModel(private val id: Long, private val repo: SnippetRepository, settings: SettingsStore) : ViewModel() {
    val state: StateFlow<FileState> = combine(
        repo.snippet(id),
        repo.usageFor(id, System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000),
        settings.settings,
        repo.folders,
    ) { s, events, cfg, folders -> FileState(true, s, events, cfg.excludedApps.size, folders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FileState())

    private fun update(change: (Snippet) -> Snippet) {
        state.value.snippet?.let { viewModelScope.launch { repo.save(change(it)) } }
    }

    fun setMode(mode: FileMode) = update { it.copy(fileMode = mode) }
    fun setLink(link: String) = update { it.copy(content = link) }
    fun setExpandAfter(e: ExpandAfter) = update { it.copy(expandAfter = e) }
    fun setKeepSpace(on: Boolean) = update { it.copy(keepSpace = on) }
    fun setKeyword(keyword: String) = update { it.copy(keyword = keyword) }
    fun setName(name: String) = update { it.copy(name = name.trim()) }
    fun moveTo(folderId: Long?) { state.value.snippet?.let { viewModelScope.launch { repo.move(listOf(it.id), folderId) } } }
    fun togglePin() { state.value.snippet?.let { viewModelScope.launch { repo.setPinned(it.id, !it.pinned) } } }
}

@Composable
fun FileSnippetScreen(container: AppContainer, id: Long, onBack: () -> Unit) {
    val vm: FileSnippetViewModel = viewModel(key = "file$id", factory = vmFactory { FileSnippetViewModel(id, container.repository, container.settings) })
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var editingKeyword by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var folderSheet by remember { mutableStateOf(false) }
    val warp = rememberShareWarp()
    val scope = rememberCoroutineScope()

    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded, state.snippet) { if (state.loaded && state.snippet == null && !leaving) onBack() }
    val s = state.snippet ?: return
    var link by remember(s.id) { mutableStateOf(s.content) }
    var insecureLink by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(link) {
        if (link == s.content) return@LaunchedEffect
        delay(400)
        val typed = Links.parse(link)
        if (typed != null && Links.isCleartext(typed)) insecureLink = typed else vm.setLink(link.trim())
    }
    insecureLink?.let { typed ->
        InsecureLinkSheet(
            typed, "Drop link",
            onUseHttps = { link = it; insecureLink = null },
            onDrop = { link = ""; insecureLink = null },
            onDismiss = { insecureLink = null },
        )
    }
    val context = LocalContext.current

    Column(
        Modifier.screenRoot().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIconButton(TablerIcons.ArrowLeft, "Back", onBack)
            Icon(
                TablerIcons.Share, "Share",
                tint = ClipsyColors.Accent,
                modifier = Modifier
                    .shareWarpTarget(warp)
                    .clip(RoundedCornerShape(10.dp))
                    .pressable { ShareSheet.chooser(context, listOf(s))?.let { scope.launch { warp.launch(context, it) } } }
                    .padding(10.dp)
                    .size(22.dp),
            )
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FloatingHero(s, Modifier.padding(top = 8.dp, bottom = 8.dp), warp = warp)
            if (editingName) {
                var name by remember(s.id) { mutableStateOf(s.name) }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                val save = { if (name.isNotBlank()) vm.setName(name); editingName = false }
                GroupedCard(Modifier.fillMaxWidth().keepAboveKeyboard()) {
                    FieldRow(
                        "Name", name, { name = it }, placeholder = "Snippet name", focusRequester = focus,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        trailing = { TextAction("Done", { save() }, bold = true) },
                    )
                }
            } else {
                Text(
                    s.name,
                    Modifier.clip(RoundedCornerShape(10.dp)).pressable { editingName = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    textAlign = TextAlign.Center,
                )
            }
            Text("${s.type.label}: ${fileKind(s.mimeType, s.name)}, ${Stats.formatSize(s.sizeBytes)}", color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            if (editingKeyword) {
                InlineKeywordEditor(
                    s.id, s.keyword, container.repository, Keyword.suggest(s.name).ifEmpty { "my_file" },
                    onSave = { vm.setKeyword(it); editingKeyword = false },
                    onCancel = { editingKeyword = false },
                )
            } else if (s.keyword != null) {
                Text(s.keyword, fontFamily = Mono, color = ClipsyColors.Accent, fontSize = ClipsyType.Label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { editingKeyword = true }.padding(horizontal = 6.dp, vertical = 4.dp))
            } else {
                Text("Add a keyword", color = ClipsyColors.Accent, fontSize = ClipsyType.Label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { editingKeyword = true }.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }

        Column(Modifier.riseIn(1), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("When the keyword is typed")
            GroupedCard {
                FileMode.entries.forEachIndexed { i, mode ->
                    if (i > 0) RowDivider()
                    val on = s.fileMode == mode
                    Row(
                        Modifier.fillMaxWidth().pressable(role = Role.RadioButton, pressedScale = 0.985f) { vm.setMode(mode) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(mode.title, style = MaterialTheme.typography.bodyLarge)
                            Text(mode.subtitle, color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
                        }
                        FadeScaleVisibility(on) { Icon(TablerIcons.Check, "Selected", tint = ClipsyColors.Accent, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
            ExpandingVisibility(s.fileMode == FileMode.LINK) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupedCard {
                        FieldRow("Link", link, { link = it }, placeholder = "https://…", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                    }
                    Text(
                        "Paste a link to where this file lives (Drive, Dropbox…). Without one, Clipsy opens the share sheet.",
                        color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        BehaviourCard(
            s, state.excludedApps, vm::setExpandAfter, vm::togglePin, vm::setKeepSpace,
            folderName = state.folders.firstOrNull { it.id == s.folderId }?.name,
            onFolder = { folderSheet = true },
            modifier = Modifier.riseIn(2),
        )

        UsageSection(s, state.events, Modifier.riseIn(2))

        Text(
            "Delete file snippet",
            Modifier.fillMaxWidth().riseIn(3).clip(RoundedCornerShape(14.dp)).pressable { confirmDelete = true }.padding(vertical = 14.dp),
            color = ClipsyColors.Danger,
            fontSize = ClipsyType.Body,
            textAlign = TextAlign.Center,
        )
    }

    if (folderSheet) {
        MoveToFolderSheet(container.repository, state.folders, s.folderId, onPick = vm::moveTo, onDismiss = { folderSheet = false })
    }

    if (confirmDelete) {
        ActionSheet(
            title = "Delete “${s.name}”?",
            message = "The saved copy of the file goes too. You can undo this for a few seconds.",
            actions = listOf(
                SheetAction("Delete file snippet", TablerIcons.Trash, destructive = true) {
                    leaving = true
                    container.deleteWithUndo(s)
                    onBack()
                },
            ),
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
