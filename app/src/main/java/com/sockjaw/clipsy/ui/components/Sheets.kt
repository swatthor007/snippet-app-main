package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sockjaw.clipsy.data.LinkFetcher
import com.sockjaw.clipsy.data.Links
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.LocalPalette
import com.sockjaw.clipsy.ui.theme.pressable
import kotlinx.coroutines.launch

/** Closes the sheet with its exit animation, then runs [after]. */
typealias CloseSheet = (after: () -> Unit) -> Unit

/**
 * The one bottom sheet used across the app: drag handle, 28dp top corners, dimmed scrim,
 * and a [CloseSheet] that animates out before the caller's action runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipsySheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: CloseSheet) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: CloseSheet = { after ->
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); after() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = ClipsyColors.Sheet,
        contentColor = ClipsyColors.Text,
        scrimColor = Color(0xFF0A0B0D).copy(alpha = if (LocalPalette.current.isDark) 0.6f else 0.36f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(ClipsyColors.Handle),
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { content(close) }
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Single-choice list in a sheet. The check pops onto the new choice before the sheet leaves. */
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    optionDetail: (T) -> String? = { null },
) {
    ClipsySheet(onDismiss) { close ->
        SheetTitle(title, subtitle)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            GroupedCard {
                options.forEachIndexed { i, option ->
                    if (i > 0) RowDivider()
                    val on = option == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .pressable(role = Role.RadioButton, pressedScale = 0.985f) {
                                onPick(option)
                                close {}
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge)
                            optionDetail(option)?.let { Text(it, color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption) }
                        }
                        FadeScaleVisibility(on) {
                            Icon(TablerIcons.Check, "Selected", tint = ClipsyColors.Accent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

data class SheetAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** A titled sheet with a short message and a stack of actions (replaces alert dialogs). */
@Composable
fun ActionSheet(title: String, message: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    ClipsySheet(onDismiss) { close ->
        SheetTitle(title, message)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GroupedCard {
                actions.forEachIndexed { i, action ->
                    if (i > 0) RowDivider()
                    val color = if (action.destructive) ClipsyColors.Danger else ClipsyColors.Text
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .pressable(pressedScale = 0.985f) { close(action.onClick) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        action.icon?.let {
                            IconTile(it, tint = if (action.destructive) ClipsyColors.Danger else ClipsyColors.Accent, background = if (action.destructive) ClipsyColors.DangerBg else ClipsyColors.CardAlt)
                        }
                        Text(action.label, color = color, fontSize = ClipsyType.Body, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Warns that a link is plain http (sent unencrypted), looks for an https version of the page, and
 * offers to switch to it or to drop the link. Swiping the sheet away does neither.
 */
@Composable
fun InsecureLinkSheet(link: String, dropLabel: String, onUseHttps: (String) -> Unit, onDrop: () -> Unit, onDismiss: () -> Unit) {
    var secure by remember(link) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(link) { secure = withContext(Dispatchers.IO) { LinkFetcher.httpsExists(link) } }
    val host = Links.host(link)
    val actions = buildList {
        if (secure == true) add(SheetAction("Use https://$host", TablerIcons.Check) { onUseHttps(Links.toHttps(link)) })
        add(SheetAction(dropLabel, destructive = true, onClick = onDrop))
    }
    ActionSheet(
        title = "This link isn’t secure",
        message = when (secure) {
            null -> "It starts with http://, so anyone on the network can read it. Looking for an https:// version…"
            true -> "It starts with http://, so anyone on the network can read it. A secure https:// version of the page exists."
            false -> "It starts with http://, so anyone on the network can read it, and no https:// version of the page answered."
        },
        actions = actions,
        onDismiss = onDismiss,
    )
}
