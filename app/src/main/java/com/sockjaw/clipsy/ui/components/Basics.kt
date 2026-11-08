package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Search
import compose.icons.tablericons.X
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Mono
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable

/**
 * Shape rule: cards and grouped lists 16dp, sheets 24dp, anything you tap to act is a pill.
 * Inputs live inside cards and take the card's shape.
 */
val CardShape = RoundedCornerShape(16.dp)

@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(ClipsyColors.Card)
            .border(1.dp, ClipsyColors.CardBorder, CardShape),
        content = content,
    )
}

/**
 * One row of a card rendered as its own lazy item, so rows can animate in, out and around.
 * Only the first and last rows get rounded corners.
 */
@Composable
fun Modifier.cardSegment(isFirst: Boolean, isLast: Boolean): Modifier {
    val r = 16.dp
    val shape: Shape = RoundedCornerShape(
        topStart = if (isFirst) r else 0.dp, topEnd = if (isFirst) r else 0.dp,
        bottomStart = if (isLast) r else 0.dp, bottomEnd = if (isLast) r else 0.dp,
    )
    return this.fillMaxWidth().clip(shape).background(ClipsyColors.Card)
}

@Composable
fun RowDivider(startPadding: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(1.dp)
            .background(ClipsyColors.Separator),
    )
}

/** Group heading above a card: sentence case, quiet, no tracking. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = ClipsyColors.TextSecondary,
    )
}

@Composable
fun PillSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(ClipsyColors.Card)
            .border(1.dp, ClipsyColors.CardBorder, CircleShape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(TablerIcons.Search, null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ClipsyColors.Text),
                cursorBrush = SolidColor(ClipsyColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChange(it.isFocused) }
                    .semantics { contentDescription = placeholder },
            )
        }
        FadeScaleVisibility(value.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .pressable(onClickLabel = "Clear search") { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(TablerIcons.X, "Clear search", tint = ClipsyColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun GreenFab(onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = TablerIcons.Plus, description: String = "New snippet") {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ClipsyColors.Accent)
            .pressable(pressedScale = 0.92f, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = ClipsyColors.OnAccent, modifier = Modifier.size(22.dp))
    }
}

/** Primary full-width action. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val bg by animateColorAsState(if (enabled) ClipsyColors.Accent else ClipsyColors.CardAlt, Motion.standard(), label = "btnBg")
    val fg by animateColorAsState(if (enabled) ClipsyColors.OnAccent else ClipsyColors.TextTertiary, Motion.standard(), label = "btnFg")
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = ClipsyType.Body, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** Segmented control whose indicator slides between options. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClipsyColors.Track)
            .border(1.dp, ClipsyColors.CardBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        val haptics = LocalHapticFeedback.current
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, Motion.settle(), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(ClipsyColors.Card)
                .border(1.dp, ClipsyColors.CardBorder, RoundedCornerShape(9.dp)),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) ClipsyColors.Text else ClipsyColors.TextSecondary, Motion.standard(), label = "segText")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .pressable(role = Role.Tab, pressedScale = 0.96f) {
                            if (i != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(i)
                        }
                        .semantics { this.selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = ClipsyType.Label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = color)
                }
            }
        }
    }
}

/** Material-proportioned switch: 48x28 track, a small check in the thumb when on. The thumb springs across. */
@Composable
fun ClipsySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    val track by animateColorAsState(if (checked) ClipsyColors.Accent else ClipsyColors.SwitchOff, Motion.standard(), label = "track")
    val thumbX by animateDpAsState(if (checked) 23.dp else 3.dp, Motion.settle(), label = "thumb")
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .pressable(role = Role.Switch, pressedScale = 0.94f) {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onCheckedChange(!checked)
            }
            .semantics {
                contentDescription = label
                toggleableState = ToggleableState(checked)
                stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFFAFAFB)),
            contentAlignment = Alignment.Center,
        ) {
            FadeScaleVisibility(checked) { Icon(TablerIcons.Check, null, tint = ClipsyColors.Accent, modifier = Modifier.size(14.dp)) }
        }
    }
}

/** A 40dp round tonal button: back arrows, close, overflow menus. */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 40dp to look at, 48dp to hit.
    Box(
        modifier.size(48.dp).clip(CircleShape).pressable(pressedScale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(ClipsyColors.CardAlt), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = ClipsyColors.Text, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, icon: ImageVector? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        ClipsySwitch(checked, onCheckedChange, label)
    }
}

@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.pressable(pressedScale = 0.985f, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value.isNotEmpty()) {
            CrossfadeText(value, color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
        if (onClick != null) {
            Icon(TablerIcons.ChevronRight, null, tint = ClipsyColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, size: Dp = 32.dp, tint: Color = ClipsyColors.Accent, background: Color = ClipsyColors.CardAlt) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) ClipsyColors.Accent.copy(alpha = 0.14f) else ClipsyColors.Card, Motion.standard(), label = "pillBg")
    val border by animateColorAsState(if (selected) ClipsyColors.Accent.copy(alpha = 0.6f) else ClipsyColors.Separator, Motion.standard(), label = "pillBorder")
    val fg by animateColorAsState(if (selected) ClipsyColors.Accent else ClipsyColors.TextMuted, Motion.standard(), label = "pillFg")
    // The pill is 34dp tall; the touch area around it is 48dp.
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .pressable(role = Role.Tab, pressedScale = 0.95f, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(34.dp).clip(CircleShape).background(bg).border(1.dp, border, CircleShape).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
        Text(label, fontSize = ClipsyType.Label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = fg)
        }
    }
}

/** Left-label / right-aligned input row used in the editor and clipboard sheet. */
@Composable
fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.width(76.dp), color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = ClipsyColors.TextTertiary, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (mono) ClipsyColors.Accent else ClipsyColors.Text,
                    fontFamily = if (mono) Mono else null,
                ),
                cursorBrush = SolidColor(ClipsyColors.Accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .semantics { contentDescription = label },
            )
        }
        trailing?.invoke()
    }
}
