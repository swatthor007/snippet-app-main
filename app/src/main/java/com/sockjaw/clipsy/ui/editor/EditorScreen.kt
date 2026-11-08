package com.sockjaw.clipsy.ui.editor

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.sockjaw.clipsy.ui.components.InsecureLinkSheet
import com.sockjaw.clipsy.ui.components.CrossfadeText
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.components.rememberFilePicker
import com.sockjaw.clipsy.ui.components.shakeOn
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.riseIn
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sockjaw.clipsy.ui.components.TokenChips
import com.sockjaw.clipsy.ui.components.SnippetPickerSheet
import com.sockjaw.clipsy.ui.components.TokenTextField
import com.sockjaw.clipsy.ui.components.insertToken
import androidx.compose.foundation.layout.height
import compose.icons.tablericons.X
import com.sockjaw.clipsy.ui.components.RoundIconButton
import compose.icons.TablerIcons
import compose.icons.tablericons.Folder
import com.sockjaw.clipsy.ui.components.MoveToFolderSheet
import com.sockjaw.clipsy.ui.components.ValueRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.Keyword
import com.sockjaw.clipsy.data.KeywordIssue
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetRepository
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.ui.components.ChoiceRow
import com.sockjaw.clipsy.ui.components.FieldRow
import com.sockjaw.clipsy.ui.components.KeywordField
import com.sockjaw.clipsy.ui.components.ExpandingVisibility
import com.sockjaw.clipsy.ui.components.rememberMediaPicker
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.SegmentedToggle
import com.sockjaw.clipsy.ui.components.SwitchRow
import com.sockjaw.clipsy.ui.components.TextAction
import com.sockjaw.clipsy.ui.components.screenRoot
import com.sockjaw.clipsy.ui.navigation.vmFactory
import com.sockjaw.clipsy.ui.lock.LocalUnlocker
import com.sockjaw.clipsy.data.Links
import compose.icons.tablericons.Lock
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class EditorViewModel(private val id: Long, private val repo: SnippetRepository, defaultExpand: ExpandAfter, initialFolder: Long? = null) : ViewModel() {
    var loaded by mutableStateOf(id == 0L)
    private var original: Snippet? = null

    var name by mutableStateOf("")
    var keyword by mutableStateOf("")
    var content by mutableStateOf(TextFieldValue(""))
    var expandAfter by mutableStateOf(defaultExpand)
    var keepSpace by mutableStateOf(true)
    var pinned by mutableStateOf(false)
    var folderId by mutableStateOf(initialFolder)
    var keywordTaken by mutableStateOf(false)
    /** The last name filled in from a link's title; a name the user typed is never replaced. */
    var autoName by mutableStateOf("")
    var lockIt by mutableStateOf(false)
    var expandUnlocked by mutableStateOf(false)
    val isFile get() = original?.type?.isFile == true
    val isNew get() = id == 0L

    init {
        if (id != 0L) viewModelScope.launch {
            repo.get(id)?.let {
                original = it
                name = it.name; keyword = it.keyword.orEmpty(); content = TextFieldValue(it.content)
                expandAfter = it.expandAfter; keepSpace = it.keepSpace; pinned = it.pinned; folderId = it.folderId
            }
            loaded = true
        }
    }

    suspend fun refreshTaken() { keywordTaken = repo.isKeywordTaken(keyword, id) }

    val issue: KeywordIssue? get() = Keyword.validate(keyword) ?: if (keywordTaken) KeywordIssue.TAKEN else null
    val canSave get() = name.isNotBlank() && issue == null && (isFile || content.text.isNotBlank())

    fun insert(token: String) { content = content.insertToken(token) }

    suspend fun save(): Long {
        val base = original ?: Snippet(name = name)
        if (lockIt && !isFile) {
            // The vault is open here (the screen signed in before saving), so the text is sealed straight away.
            return repo.lock(
                base.copy(name = name.trim(), keyword = keyword, expandAfter = expandAfter, keepSpace = keepSpace, pinned = pinned, folderId = folderId),
                text = content.text,
                expandUnlocked = expandUnlocked,
            )
        }
        return repo.save(base.copy(name = name.trim(), keyword = keyword, content = content.text, expandAfter = expandAfter, keepSpace = keepSpace, pinned = pinned, folderId = folderId))
    }
}

@Composable
fun EditorScreen(
    container: AppContainer,
    id: Long,
    initialFolder: Long?,
    onCancel: () -> Unit,
    onSaved: (Long) -> Unit,
    onFileImported: (Long) -> Unit,
) {
    val vm: EditorViewModel = viewModel(key = "editor$id", factory = vmFactory { EditorViewModel(id, container.repository, container.settings.settings.value.expandAfter, initialFolder) })
    val scope = rememberCoroutineScope()
    fun import(uri: android.net.Uri, type: SnippetType) = scope.launch {
        runCatching { container.repository.importFile(uri, type) }
            .onSuccess(onFileImported)
            .onFailure { container.toaster.show("Couldn’t read that file", ToastKind.Error) }
    }
    val noPicker = { container.toaster.show("No file picker on this device", ToastKind.Error) }
    val pickMedia = rememberMediaPicker(onPicked = { import(it, SnippetType.MEDIA) }, onUnavailable = noPicker)
    val pickDocument = rememberFilePicker(onPicked = { import(it, SnippetType.DOCUMENT) }, onUnavailable = noPicker)
    val haptics = LocalHapticFeedback.current
    val unlocker = LocalUnlocker.current
    val folders by container.visibleFolders.collectAsStateWithLifecycle(emptyList())
    var folderSheet by remember { mutableStateOf(false) }
    var pickingSnippet by remember { mutableStateOf(false) }
    val allSnippets by container.visibleSnippets.collectAsStateWithLifecycle(emptyList())
    var shake by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.keyword) { vm.refreshTaken() }
    // Paste a link and its page title becomes the name, unless the user has already typed one.
    val editorSettings by container.settings.settings.collectAsStateWithLifecycle()
    LaunchedEffect(vm.content.text, editorSettings.linkPreviews) {
        val link = Links.parse(vm.content.text)
        if (link == null || !editorSettings.linkPreviews || !vm.isNew || vm.isFile) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        val title = container.links.load(link)?.takeIf { it.status == com.sockjaw.clipsy.data.PreviewStatus.OK }?.title ?: return@LaunchedEffect
        if (vm.name.isBlank() || vm.name == vm.autoName) { vm.name = title.take(60); vm.autoName = vm.name }
    }
    if (!vm.loaded) return
    var insecureLink by remember { mutableStateOf<String?>(null) }
    val saveNow = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val save = { scope.launch { onSaved(vm.save()) }; Unit }
        // A locked snippet is encrypted as it's saved, so sign in (or set locking up) first.
        if (vm.lockIt && !vm.isFile) unlocker?.require("Lock this snippet", action = save) ?: save() else save()
    }
    insecureLink?.let { link ->
        InsecureLinkSheet(
            link, "Drop link",
            onUseHttps = { vm.content = TextFieldValue(Links.toHttps(link)); insecureLink = null; saveNow() },
            onDrop = { vm.content = TextFieldValue(""); insecureLink = null },
            onDismiss = { insecureLink = null },
        )
    }

    Column(Modifier.screenRoot().imePadding().padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIconButton(TablerIcons.X, "Close", onCancel)
            Text(if (vm.isNew) "New Snippet" else "Edit Snippet", style = MaterialTheme.typography.titleMedium)
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(ClipsyColors.Accent)
                    .pressable {
                        if (vm.canSave) {
                            val typed = if (vm.isFile) null else Links.parse(vm.content.text)
                            if (typed != null && Links.isCleartext(typed)) insecureLink = typed else saveNow()
                        } else {
                            shake++
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                    }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save", color = ClipsyColors.OnAccent, fontSize = ClipsyType.Label, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (vm.isNew) {
                Column(Modifier.riseIn(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedToggle(
                        listOf("Text", "Media", "Document"),
                        0,
                        {
                            when (it) {
                                1 -> pickMedia()
                                2 -> pickDocument()
                            }
                        },
                    )
                    Text(
                        "Media is images and GIFs. Documents are any other file, like PDFs.",
                        color = ClipsyColors.TextSecondary,
                        fontSize = ClipsyType.Caption,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Column(Modifier.riseIn(1).shakeOn(shake), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupedCard {
                        FieldRow("Name", vm.name, { vm.name = it }, placeholder = "Snippet name")
                    }
                    ExpandingVisibility(vm.name.isBlank() && shake > 0) {
                        Text("Give the snippet a name", color = ClipsyColors.Danger, fontSize = ClipsyType.Caption, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                KeywordField(vm.keyword, { vm.keyword = it }, vm.issue, example = Keyword.suggest(vm.name).ifEmpty { "meet_link" })
            }

            if (!vm.isFile) {
                Column(Modifier.riseIn(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Content")
                    GroupedCard {
                        TokenTextField(vm.content, { vm.content = it }, Modifier.heightIn(min = 140.dp).padding(16.dp))
                        RowDivider()
                        TokenChips(vm::insert, onPickSnippet = { pickingSnippet = true })
                    }
                }
            }

            Column(Modifier.riseIn(3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Behaviour")
                GroupedCard {
                    ValueRow("Folder", folders.firstOrNull { it.id == vm.folderId }?.name ?: "None", icon = TablerIcons.Folder, onClick = { folderSheet = true })
                    RowDivider()
                    ChoiceRow("Expand after", ExpandAfter.entries, vm.expandAfter, { it.label }, { vm.expandAfter = it }, optionDetail = { com.sockjaw.clipsy.ui.components.expandDetail(it) })
                    RowDivider()
                    SwitchRow("Keep the space after", vm.keepSpace, { vm.keepSpace = it })
                    RowDivider()
                    SwitchRow("Pin to Recent", vm.pinned, { vm.pinned = it })
                    if (!vm.isFile && vm.isNew) {
                        RowDivider()
                        SwitchRow("Lock (encrypt)", vm.lockIt, { vm.lockIt = it; if (!it) vm.expandUnlocked = false }, icon = TablerIcons.Lock)
                        if (vm.lockIt) {
                            RowDivider()
                            SwitchRow("Expand without unlocking", vm.expandUnlocked, { vm.expandUnlocked = it })
                            Text(
                                if (vm.expandUnlocked) "The text stays hidden in Clipsy, but anyone typing the keyword on this phone gets it."
                                else "Typing the keyword shows a lock. Tap it to sign in and insert the text.",
                                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                color = ClipsyColors.TextSecondary,
                                fontSize = ClipsyType.Caption,
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickingSnippet) {
        SnippetPickerSheet(allSnippets.filter { !it.locked }, id, onPick = { vm.insert("{snippet:$it}") }, onDismiss = { pickingSnippet = false })
    }
    if (folderSheet) {
        MoveToFolderSheet(container.repository, folders, vm.folderId, onPick = { vm.folderId = it }, onDismiss = { folderSheet = false })
    }
}
