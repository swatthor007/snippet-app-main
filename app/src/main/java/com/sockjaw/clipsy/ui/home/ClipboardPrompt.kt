package com.sockjaw.clipsy.ui.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import com.sockjaw.clipsy.ui.components.LinkThumbnail
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.PreviewStatus
import com.sockjaw.clipsy.data.LinkPreview
import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.ui.components.CardShape
import com.sockjaw.clipsy.ui.components.TextAction
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.Clipboard
import compose.icons.tablericons.Link
import com.sockjaw.clipsy.data.Links
import compose.icons.tablericons.Paperclip

/**
 * Both states of the Home capture card share this one height, so switching between them
 * never moves anything below.
 */
private val SlotHeight = 136.dp

private sealed interface SlotState {
    data object Drop : SlotState
    data class Clipboard(val text: String) : SlotState
}

/**
 * One card, one place. Nothing copied: a file drop zone (tap to choose). Something new copied:
 * offer to save it as a snippet. A file dragged over it always shows the drop state.
 */
@Composable
fun CaptureSlot(
    clipboard: String?,
    hovering: Boolean,
    onChooseFile: () -> Unit,
    onAddClipboard: () -> Unit,
    onDismissClipboard: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the copied link points at, once it has been looked up. */
    linkPreview: LinkPreview? = null,
) {
    val state: SlotState = if (clipboard != null && !hovering) SlotState.Clipboard(clipboard) else SlotState.Drop
    AnimatedContent(
        targetState = state,
        // Same-size crossfade: the new state fades up with a hint of scale, the old one fades out.
        transitionSpec = {
            (fadeIn(Motion.enter(Motion.BASE)) + scaleIn(Motion.enter(Motion.BASE), initialScale = 0.98f))
                .togetherWith(fadeOut(Motion.exit(Motion.QUICK)))
        },
        contentKey = { it::class },
        modifier = modifier.fillMaxWidth().height(SlotHeight),
        label = "captureSlot",
    ) { s ->
        when (s) {
            SlotState.Drop -> DropState(hovering, onChooseFile)
            is SlotState.Clipboard -> ClipboardState(s.text, linkPreview, onAddClipboard, onDismissClipboard)
        }
    }
}

@Composable
private fun DropState(hovering: Boolean, onClick: () -> Unit) {
    // A dashed outline is the familiar "drop here" cue; it turns solid accent while a file hovers.
    val rim by animateColorAsState(if (hovering) ClipsyColors.Accent else ClipsyColors.Handle, Motion.standard(), label = "rim")
    val bg by animateColorAsState(if (hovering) ClipsyColors.AccentFaint else ClipsyColors.Card, Motion.standard(), label = "dropBg")
    val lift by animateFloatAsState(if (hovering) 1.02f else 1f, Motion.settle(), label = "lift")
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(CardShape)
            .background(bg)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = rim,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = if (hovering) null else PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(TablerIcons.Paperclip, null, tint = ClipsyColors.Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(10.dp))
        Text(if (hovering) "Drop to add this file" else "Drop a file or tap to choose", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text("Images, GIFs, PDFs or any other file", color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ClipboardState(text: String, linkPreview: LinkPreview?, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val isLink = Links.parse(text) != null
    // Known once the page has been read: its real title, site and share image replace the bare URL.
    val known = linkPreview?.takeIf { isLink && it.status == PreviewStatus.OK && !it.title.isNullOrBlank() }
    val image = known?.imagePath?.let { java.io.File(it) }?.takeIf { it.exists() }
    val asSnippet = remember(text) { Snippet(name = "", content = text) }
    val preview = text.replace(Regex("\\s+"), " ").trim().let { if (it.length > 42) it.take(40).trimEnd() + "…" else it }
    Column(
        Modifier
            .fillMaxSize()
            .clip(CardShape)
            .background(ClipsyColors.AccentFaint)
            .border(1.dp, ClipsyColors.Accent.copy(alpha = 0.25f), CardShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (image != null) {
                LinkThumbnail(image, asSnippet, Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)), iconSize = 20.dp)
            } else {
                Icon(if (isLink) TablerIcons.Link else TablerIcons.Clipboard, null, tint = ClipsyColors.Accent, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        known != null -> "Add “${known.title}” as a snippet?"
                        isLink -> "Add this link as a snippet?"
                        else -> "Add “$preview” as a snippet?"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    known?.let { it.siteName ?: Links.host(text.trim()) } ?: "So you don’t have to copy and paste it every time.",
                    color = ClipsyColors.TextSecondary,
                    fontSize = ClipsyType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextAction("Not now", onDismiss)
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(ClipsyColors.Accent)
                    .pressable(onClick = onAdd)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Add", color = ClipsyColors.OnAccent, fontSize = ClipsyType.Label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
