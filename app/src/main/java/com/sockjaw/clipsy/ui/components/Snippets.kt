package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.AlignLeft
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Link
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Pin
import compose.icons.tablericons.Trash
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Share
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import compose.icons.tablericons.Check
import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.data.Thumbnails
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.data.Stats
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Mono
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun snippetIcon(snippet: Snippet): ImageVector = when {
    snippet.locked -> TablerIcons.Lock
    snippet.type == SnippetType.MEDIA -> TablerIcons.Photo
    snippet.type == SnippetType.DOCUMENT -> TablerIcons.FileText
    snippet.isLink -> TablerIcons.Link
    else -> TablerIcons.AlignLeft
}

fun fileKind(mime: String?, name: String): String = when {
    mime == "application/pdf" || name.endsWith(".pdf", true) -> "PDF"
    mime?.startsWith("image/") == true -> mime.substringAfter('/').uppercase().take(4)
    name.contains('.') -> name.substringAfterLast('.').uppercase().take(4)
    else -> "FILE"
}

/** The grey line under a snippet's name: kind and size, domain and uses, or uses; plus its folder. */
fun snippetMeta(s: Snippet, folderName: String? = null): String {
    val uses = if (s.useCount == 1) "1 use" else "${s.useCount} uses"
    val base = when {
        s.locked -> "Locked · $uses"
        s.type.isFile -> "${fileKind(s.mimeType, s.name)}, ${Stats.formatSize(s.sizeBytes)}"
        s.isLink -> "${Links.host(s.content.trim())} · $uses"
        else -> uses
    }
    return if (folderName != null) "$base · $folderName" else base
}

/** Empty ring when unselected, filled accent with a check when selected. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (selected) ClipsyColors.Accent else Color.Transparent, Motion.standard(Motion.QUICK), label = "markFill")
    val ring by animateColorAsState(if (selected) ClipsyColors.Accent else ClipsyColors.Handle, Motion.standard(Motion.QUICK), label = "markRing")
    Box(
        modifier.size(24.dp).clip(CircleShape).background(fill).border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(TablerIcons.Check, "Selected", tint = ClipsyColors.OnAccent, modifier = Modifier.size(15.dp))
    }
}

/** Copy and Share, always visible on a row so neither needs a swipe or an open screen. */
@Composable
fun RowActions(snippet: Snippet, onCopy: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
        RowActionButton(TablerIcons.Copy, "Copy ${snippet.name}", onCopy, size)
        RowActionButton(TablerIcons.Share, "Share ${snippet.name}", onShare, size)
    }
}

@Composable
private fun RowActionButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp) {
    // The icon follows [size]; the touch area is never smaller than 48dp.
    Box(
        Modifier.size(maxOf(size, 48.dp)).clip(CircleShape).pressable(pressedScale = 0.88f, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(size * 0.5f)) }
}

/** Dense one-line row for Library's compact view. */
@Composable
fun CompactSnippetRow(
    snippet: Snippet,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) ClipsyColors.AccentFaint else ClipsyColors.Card)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selecting) SelectionMark(selected) else Icon(snippetIcon(snippet), null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(24.dp))
        Text(snippet.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (snippet.pinned) Icon(TablerIcons.Pin, "Pinned", tint = ClipsyColors.Accent, modifier = Modifier.size(13.dp))
        snippet.keyword?.let { Text(it, fontFamily = Mono, fontSize = ClipsyType.Caption, color = ClipsyColors.Accent, maxLines = 1) }
        if (!selecting) RowActions(snippet, onCopy, onShare, size = 32.dp)
    }
}

/** Tile for Library's grid view: a preview on top, name and keyword underneath. */
@Composable
fun SnippetCard(
    snippet: Snippet,
    folderName: String?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(if (selected) ClipsyColors.Accent else ClipsyColors.CardBorder, Motion.standard(Motion.QUICK), label = "cardBorder")
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) ClipsyColors.AccentFaint else ClipsyColors.Card)
            .border(if (selected) 2.dp else 1.dp, border, CardShape)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            val linkImage = linkImageFor(snippet)
            if (Thumbnails.canPreview(snippet)) {
                SnippetImage(snippet, Modifier.fillMaxSize())
            } else if (linkImage != null) {
                LinkThumbnail(linkImage, snippet, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(ClipsyColors.CardAlt), contentAlignment = Alignment.Center) {
                    Icon(snippetIcon(snippet), null, tint = ClipsyColors.Accent, modifier = Modifier.size(32.dp))
                }
            }
            if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
            // Copy and Share sit on the image, so the name below keeps the card's full width.
            if (!selecting) {
                Row(Modifier.align(Alignment.BottomEnd).padding(2.dp)) {
                    OverlayAction(TablerIcons.Copy, "Copy ${snippet.name}", onCopy)
                    OverlayAction(TablerIcons.Share, "Share ${snippet.name}", onShare)
                }
            }
            if (snippet.pinned) Icon(TablerIcons.Pin, "Pinned", tint = ClipsyColors.Accent, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(snippet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(snippet.keyword ?: "no keyword", fontFamily = Mono, fontSize = ClipsyType.Caption, maxLines = 1, color = if (snippet.keyword != null) ClipsyColors.Accent else ClipsyColors.TextTertiary)
                Text(snippetMeta(snippet, folderName), fontSize = ClipsyType.Caption, color = ClipsyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A round, see-through button for sitting on top of a picture: 48dp to touch, 30dp to see. */
@Composable
private fun OverlayAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).pressable(pressedScale = 0.88f, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** Compact row used in Home's Recent list. A long press copies the snippet. */
@Composable
fun RecentRow(snippet: Snippet, onLongClick: (() -> Unit)? = null, onCopy: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .pressable(pressedScale = 0.985f, onLongClickLabel = "Copy", onLongClick = onLongClick, onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SnippetLeading(snippet, 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(snippet.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            snippet.keyword?.let { Text(it, fontFamily = Mono, fontSize = ClipsyType.Caption, color = ClipsyColors.TextSecondary) }
        }
        Text(Stats.relativeAge(snippet.lastActivity), color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        // A visible Copy, so the long press isn't the only way to find it.
        if (onCopy != null) RowActionButton(TablerIcons.Copy, "Copy ${snippet.name}", onCopy, 36.dp)
    }
}

/** Library row with pin marker, uses and a swipe-to-reveal Pin / Delete pair. */
@Composable
fun SwipeSnippetRow(
    snippet: Snippet,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    folderName: String? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    onCopy: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { 148.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var pastThreshold by remember { mutableStateOf(false) }
    fun settleTo(target: Float) = scope.launch { offset.animateTo(target, Motion.settle()) }

    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp).background(ClipsyColors.Track).semantics {
        customActions = listOfNotNull(
            CustomAccessibilityAction(if (snippet.pinned) "Unpin" else "Pin") { onTogglePin(); true },
            onCopy?.let { c -> CustomAccessibilityAction("Copy") { c(); true } },
            onShare?.let { sh -> CustomAccessibilityAction("Share") { sh(); true } },
            CustomAccessibilityAction("Delete") { onDelete(); true },
        )
    }) {
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
            ActionButton("Pin", if (snippet.pinned) "Unpin" else "Pin", ClipsyColors.AccentFaint, ClipsyColors.AccentStrong, TablerIcons.Pin) {
                onTogglePin(); settleTo(0f)
            }
            ActionButton("Delete", "Delete", ClipsyColors.DangerBg, ClipsyColors.Danger, TablerIcons.Trash) { onDelete() }
        }
        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .fillMaxHeight()
                .background(if (selected) ClipsyColors.AccentFaint else ClipsyColors.Card)
                .draggable(
                    enabled = !selecting,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // Rubber-band past the actions so the drag never hits a hard wall.
                        val next = offset.value + delta
                        val clamped = if (next < -revealPx) -revealPx + (next + revealPx) * 0.25f else next.coerceAtMost(0f)
                        scope.launch { offset.snapTo(clamped.coerceAtLeast(-revealPx * 1.3f)) }
                        val past = clamped < -revealPx / 2
                        if (past != pastThreshold) {
                            pastThreshold = past
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    },
                    onDragStopped = { velocity ->
                        val open = offset.value < -revealPx / 2 || velocity < -1200f
                        settleTo(if (open && velocity < 1200f) -revealPx else 0f)
                    },
                )
                .clickable {
                    if (offset.value != 0f) settleTo(0f) else onClick()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(selecting, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                SelectionMark(selected, Modifier.padding(end = 0.dp))
            }
            SnippetLeading(snippet, 36.dp, tileBackground = ClipsyColors.AccentFaint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(snippet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (snippet.pinned) Icon(TablerIcons.Pin, "Pinned", tint = ClipsyColors.Accent, modifier = Modifier.size(13.dp))
                }
                val meta = snippetMeta(snippet, folderName)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(snippet.keyword ?: "no keyword", fontFamily = Mono, fontSize = ClipsyType.Caption, color = if (snippet.keyword != null) ClipsyColors.Accent else ClipsyColors.TextTertiary)
                    Text(meta, fontSize = ClipsyType.Caption, color = ClipsyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!selecting && onCopy != null && onShare != null) RowActions(snippet, onCopy, onShare)
        }
    }
}

@Composable
private fun ActionButton(
    desc: String,
    label: String,
    bg: Color,
    fg: Color,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(74.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClickLabel = desc, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, color = fg, fontSize = ClipsyType.Label, fontWeight = FontWeight.SemiBold)
    }
}
