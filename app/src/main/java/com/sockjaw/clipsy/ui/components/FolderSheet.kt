package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.SnippetRepository
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Folder
import compose.icons.tablericons.FolderPlus
import kotlinx.coroutines.launch

/**
 * Picks the folder for one or more snippets: "No folder", each folder, or a new one made
 * right here. [selected] is the current folder id, or null for none; [onPick] gets the choice.
 */
@Composable
fun MoveToFolderSheet(
    repo: SnippetRepository,
    folders: List<Folder>,
    selected: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Move to folder",
) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var taken by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(name) { taken = name.isNotBlank() && repo.isFolderNameTaken(name) }
    LaunchedEffect(creating) { if (creating) focus.requestFocus() }

    ClipsySheet(onDismiss) { close ->
        SheetTitle(title)
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupedCard {
                FolderChoiceRow("No folder", selected == null, TablerIcons.Folder, ClipsyColors.TextSecondary) { onPick(null); close {} }
                folders.forEach { f ->
                    RowDivider()
                    FolderChoiceRow(f.name, selected == f.id, f.vector, f.tint) { onPick(f.id); close {} }
                }
            }
            GroupedCard {
                if (!creating) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).pressable(pressedScale = 0.985f) { creating = true }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(TablerIcons.FolderPlus, null, tint = ClipsyColors.Accent, modifier = Modifier.size(22.dp))
                        Text("New folder…", color = ClipsyColors.Accent, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val create = {
                        scope.launch {
                            val id = repo.createFolder(name)
                            if (id != null) { onPick(id); close {} }
                        }
                        Unit
                    }
                    FieldRow(
                        "Name", name, { name = it }, placeholder = "Folder name", focusRequester = focus,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { create() }),
                        trailing = { TextAction("Create", { create() }, enabled = name.isNotBlank() && !taken, bold = true) },
                    )
                }
            }
            if (taken) Text("A folder with that name already exists", color = ClipsyColors.Danger, fontSize = ClipsyType.Caption, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun FolderChoiceRow(label: String, on: Boolean, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).pressable(role = Role.RadioButton, pressedScale = 0.985f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FadeScaleVisibility(on) { Icon(TablerIcons.Check, "Selected", tint = ClipsyColors.Accent, modifier = Modifier.size(22.dp)) }
    }
}

/** Creates a folder, or edits one when [existing] is given: name, colour and icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderNameSheet(
    repo: SnippetRepository,
    existing: Folder?,
    onDone: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.color ?: 0) }
    var icon by remember { mutableStateOf(existing?.icon ?: "folder") }
    var taken by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(name) { taken = name.isNotBlank() && repo.isFolderNameTaken(name, existing?.id ?: 0) }
    LaunchedEffect(Unit) { if (existing == null) focus.requestFocus() }
    val ok = name.isNotBlank() && !taken
    val tint = FolderStyle.color(color)
    val haptics = LocalHapticFeedback.current

    ClipsySheet(onDismiss) { close ->
        SheetTitle(if (existing == null) "New folder" else "Edit folder")
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val save = {
                if (ok) scope.launch {
                    val id = if (existing == null) repo.createFolder(name, color, icon)
                    else existing.id.takeIf { repo.updateFolder(it, name, color, icon) }
                    if (id != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        close { onDone(id) }
                    }
                }
                Unit
            }
            GroupedCard {
                FieldRow(
                    "Name", name, { name = it }, placeholder = "Folder name", focusRequester = focus,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    trailing = {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(FolderStyle.icon(icon), null, tint = tint, modifier = Modifier.size(18.dp))
                        }
                    },
                )
            }
            if (taken) Text("A folder with that name already exists", color = ClipsyColors.Danger, fontSize = ClipsyType.Caption, modifier = Modifier.padding(horizontal = 4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Colour")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    FolderStyle.colors.forEachIndexed { i, c ->
                        val on = i == color
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (on) Modifier.border(3.dp, ClipsyColors.Sheet, CircleShape).border(5.dp, c, CircleShape) else Modifier)
                                .pressable(role = Role.RadioButton, pressedScale = 0.9f) { color = i }
                                .semantics { contentDescription = FolderStyle.colorNames[i]; selected = on },
                            contentAlignment = Alignment.Center,
                        ) { FadeScaleVisibility(on) { Icon(TablerIcons.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) } }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Icon")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    FolderStyle.icons.forEach { (key, vec) ->
                        val on = key == icon
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) tint.copy(alpha = 0.18f) else ClipsyColors.Card)
                                .border(if (on) 2.dp else 1.dp, if (on) tint else ClipsyColors.CardBorder, RoundedCornerShape(12.dp))
                                .pressable(role = Role.RadioButton, pressedScale = 0.92f) { icon = key }
                                .semantics { contentDescription = key; selected = on },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vec, null, tint = if (on) tint else ClipsyColors.TextSecondary, modifier = Modifier.size(22.dp)) }
                    }
                }
            }

            PrimaryButton(if (existing == null) "Create folder" else "Save", onClick = { save() }, enabled = ok)
        }
    }
}
