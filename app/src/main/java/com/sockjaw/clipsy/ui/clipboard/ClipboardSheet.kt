package com.sockjaw.clipsy.ui.clipboard

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.Keyword
import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.data.LinkKind
import com.sockjaw.clipsy.data.LinkPreview
import com.sockjaw.clipsy.data.PreviewStatus
import com.sockjaw.clipsy.ui.components.LinkPreviewCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sockjaw.clipsy.ui.components.MoveToFolderSheet
import com.sockjaw.clipsy.ui.components.ValueRow
import compose.icons.TablerIcons
import compose.icons.tablericons.Folder
import com.sockjaw.clipsy.data.KeywordIssue
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.ui.components.ClipsySheet
import com.sockjaw.clipsy.ui.components.ClipsySwitch
import com.sockjaw.clipsy.ui.components.CrossfadeText
import com.sockjaw.clipsy.ui.components.ExpandingVisibility
import com.sockjaw.clipsy.ui.components.FieldRow
import com.sockjaw.clipsy.ui.components.KeywordField
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.PrimaryButton
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SegmentedToggle
import com.sockjaw.clipsy.ui.components.InsecureLinkSheet
import com.sockjaw.clipsy.ui.components.SheetTitle
import com.sockjaw.clipsy.ui.components.shakeOn
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import kotlinx.coroutines.launch

@Composable
fun ClipboardSheet(
    container: AppContainer,
    text: String,
    onDismiss: () -> Unit,
    onSaved: (name: String) -> Unit,
    title: String = "New from clipboard",
    suggestedName: String? = null,
) {
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val link = remember(text) { Links.parse(text) }
    var name by remember {
        mutableStateOf(
            suggestedName?.take(30)?.ifBlank { null }
                ?: link?.let { Links.host(it) }
                ?: text.lineSequence().first().trim().take(30),
        )
    }
    // A link's own title is the better name: it's read in the background and swapped in, unless
    // the user has already typed something of their own.
    val startName = remember { name }
    val linkSettings by container.settings.settings.collectAsStateWithLifecycle()
    val previewing = link != null && linkSettings.linkPreviews
    var preview by remember { mutableStateOf<LinkPreview?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var saveAsMedia by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(link, previewing) {
        if (link == null || !previewing) return@LaunchedEffect
        previewLoading = true
        val found = container.links.load(link)
        previewLoading = false
        preview = found
        val title = found?.takeIf { it.status == PreviewStatus.OK }?.title
        if (title != null && suggestedName == null && name == startName) name = title.take(60)
    }
    val folders by container.visibleFolders.collectAsStateWithLifecycle(emptyList())
    var folderId by remember { mutableStateOf<Long?>(null) }
    var folderSheet by remember { mutableStateOf(false) }
    // Left empty on purpose: the keyword is the user's choice. The name only feeds the hint.
    var keyword by remember { mutableStateOf("") }
    var expandAfter by remember { mutableStateOf(container.settings.settings.value.expandAfter) }
    var pinned by remember { mutableStateOf(true) }
    var taken by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }

    LaunchedEffect(keyword) { taken = repo.isKeywordTaken(keyword) }
    val issue = Keyword.validate(keyword) ?: if (taken) KeywordIssue.TAKEN else null
    val canSave = name.isNotBlank() && issue == null

    var insecure by remember { mutableStateOf(false) }
    fun save(content: String, close: (() -> Unit) -> Unit) {
        // Saving an image link downloads it, which takes a moment: one tap must be one snippet.
        saving = true
        scope.launch {
            val base = Snippet(name = name.trim(), keyword = keyword, content = content, expandAfter = expandAfter, pinned = pinned, folderId = folderId)
            val found = preview
            if (saveAsMedia && found != null && found.kind == LinkKind.IMAGE) {
                // The image link becomes a media snippet; if the download fails, keep the link instead.
                val media = container.links.saveAsMedia(base.copy(content = ""), found)
                if (media == null) repo.save(base)
            } else {
                repo.save(base)
            }
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            close { onSaved(name.trim()) }
        }
    }

    ClipsySheet(onDismiss) { close ->
        val lines = text.lines().size
        SheetTitle(title, if (link != null) "Link" else "$lines ${if (lines == 1) "line" else "lines"} of text")
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text,
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ClipsyColors.Card)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (previewing && link != null) {
                LinkPreviewCard(link, preview, previewLoading, onRefresh = {
                    scope.launch { previewLoading = true; preview = container.links.load(link, force = true); previewLoading = false }
                }, compact = true)
                if (preview?.kind == LinkKind.IMAGE && preview?.status == PreviewStatus.OK) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This link is an image", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Label, fontWeight = FontWeight.Medium)
                        SegmentedToggle(listOf("Keep as link", "Save as media"), if (saveAsMedia) 1 else 0, { saveAsMedia = it == 1 })
                    }
                }
            }

            Column(Modifier.shakeOn(shake), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupedCard {
                    FieldRow("Name", name, { name = it }, placeholder = "Snippet name")
                }
                KeywordField(keyword, { keyword = it }, issue, example = Keyword.suggest(name).ifEmpty { "home_address" })
            }

            GroupedCard {
                ValueRow("Folder", folders.firstOrNull { it.id == folderId }?.name ?: "None", icon = TablerIcons.Folder, onClick = { folderSheet = true })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Expand when", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Label, fontWeight = FontWeight.Medium)
                SegmentedToggle(
                    listOf("After space", "After enter", "Instantly"),
                    ExpandAfter.entries.indexOf(expandAfter),
                    { expandAfter = ExpandAfter.entries[it] },
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pin to Recent", style = MaterialTheme.typography.bodyLarge)
                ClipsySwitch(pinned, { pinned = it }, "Pin to Recent")
            }

            PrimaryButton(if (saving) "Saving…" else "Save snippet", enabled = !saving, onClick = {
                if (saving) return@PrimaryButton
                if (!canSave) {
                    shake++
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    return@PrimaryButton
                }
                if (link != null && Links.isCleartext(link)) insecure = true else save(text, close)
            })
        }
        if (insecure) {
            InsecureLinkSheet(
                link!!, "Don’t save this link",
                onUseHttps = { save(it, close) },
                onDrop = { close(onDismiss) },
                onDismiss = { insecure = false },
            )
        }
    }

    if (folderSheet) {
        MoveToFolderSheet(repo, folders, folderId, onPick = { folderId = it }, onDismiss = { folderSheet = false })
    }
}
