package com.sockjaw.clipsy.ui.filesnippet

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sockjaw.clipsy.ui.components.ShareWarp
import com.sockjaw.clipsy.ui.components.shareWarpShadow
import com.sockjaw.clipsy.ui.components.shareWarpSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.view.View
import com.sockjaw.clipsy.service.FileShare
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.ui.components.CardShape
import com.sockjaw.clipsy.ui.components.fileKind
import com.sockjaw.clipsy.ui.components.rememberThumbnail
import com.sockjaw.clipsy.data.Thumbnails
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.Motion
import compose.icons.TablerIcons
import compose.icons.tablericons.FileText
import java.io.File

/**
 * The added file, held up so you can see it. It tumbles in once, then floats: a slow drift up
 * and down with small tilts on all three axes, each on its own period so the motion never
 * visibly repeats. An oval shadow on the ground tracks its height: bigger and darker as it
 * sinks, smaller and fainter as it rises. The oval is hard-edged. Tap to pause and resume.
 * With animations turned off it simply sits still. A long-press picks the file up so it can be
 * dropped into another app (split-screen, freeform or a floating window), like dragging from a file manager.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingHero(snippet: Snippet, modifier: Modifier = Modifier, warp: ShareWarp? = null) {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable(snippet.id) { mutableStateOf(reduced) }
    var floating by rememberSaveable(snippet.id) { mutableStateOf(!reduced) }

    // Entrance: swings in from a turned-away angle and settles, like something set down in front of you.
    val enter = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            enter.animateTo(1f, Motion.settle())
            played = true
        }
    }

    // Float: amplitude eases to zero when paused, so the file settles flat instead of freezing mid-tilt.
    val amp by animateFloatAsState(if (floating && !reduced) 1f else 0f, tween(600, easing = Motion.Standard), label = "floatAmp")
    val t = rememberInfiniteTransition(label = "float")
    val smooth = androidx.compose.animation.core.FastOutSlowInEasing
    val rx by t.animateFloat(-7f, 7f, infiniteRepeatable(tween(4300, easing = smooth), RepeatMode.Reverse), label = "rx")
    val ry by t.animateFloat(-11f, 11f, infiniteRepeatable(tween(5900, easing = smooth), RepeatMode.Reverse), label = "ry")
    val rz by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(3700, easing = smooth), RepeatMode.Reverse), label = "rz")
    val lift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3100, easing = smooth), RepeatMode.Reverse), label = "lift")

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val e = enter.value

    // Pick-up: while the file is being dragged away it lifts and dims, leaving a ghost behind, then settles back.
    var dragging by remember { mutableStateOf(false) }
    val pick by animateFloatAsState(if (dragging) 1f else 0f, tween(if (reduced) 0 else 220, easing = Motion.Standard), label = "pickUp")
    val dragEnd = remember {
        object : DragAndDropTarget {
            override fun onEnded(event: DragAndDropEvent) { dragging = false }
            override fun onDrop(event: DragAndDropEvent) = false
        }
    }
    val shadowColor = ClipsyColors.Text
    Column(
        modifier
            .fillMaxWidth()
            // Only here to hear the drag finish; it never accepts a drop.
            .dragAndDropTarget(shouldStartDragAndDrop = { dragging }, target = dragEnd)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !reduced,
                role = Role.Button,
                onClickLabel = if (floating) "Pause motion" else "Resume motion",
            ) { floating = !floating }
            .semantics { contentDescription = "${snippet.type.label}: ${snippet.name}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // When shared, only the file flies into the Share button; the shadow below shrinks in place.
        Box(
            Modifier.then(if (warp != null) Modifier.shareWarpSource(warp) else Modifier).graphicsLayer {
                cameraDistance = 14f * density
                val rise = (lift * 10f - 5f) * amp
                translationY = (rise - (1f - e) * 36f) * density
                clip = false
                rotationX = rx * amp + (1f - e) * 28f
                rotationY = ry * amp - (1f - e) * 70f
                rotationZ = rz * amp
                scaleX = (0.72f + 0.28f * e) * (1f + 0.07f * pick)
                scaleY = (0.72f + 0.28f * e) * (1f + 0.07f * pick)
                alpha = e * (1f - 0.55f * pick)
            }
                .dragAndDropSource { _ ->
                    // GLOBAL lets the drag leave this app; the URI flag grants the target read access to the file.
                    FileShare.clip(context, snippet)?.let {
                        dragging = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        DragAndDropTransferData(it, flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
                    }
                },
        ) {
            if (snippet.type == SnippetType.MEDIA) MediaCard(snippet) else DocumentCard(snippet)
        }
        // Ground shadow. 0 = file at its highest, 1 = lowest (closest to the ground); the tumble-in
        // starts high up, so the shadow grows in with it.
        val nearGround = ((lift * 10f - 5f) * amp + 5f) / 10f * e
        Canvas(
            Modifier
                .then(if (warp != null) Modifier.shareWarpShadow(warp) else Modifier)
                .graphicsLayer {
                    val s = 0.7f + 0.35f * nearGround
                    scaleX = s
                    scaleY = s
                    alpha = e * (1f - pick)
                }
                .size(width = 180.dp, height = 22.dp),
        ) {
            // A crisp, flat oval: no blur, no gradient.
            drawOval(shadowColor.copy(alpha = 0.10f * (0.55f + 0.45f * nearGround)))
        }
    }
}

@Composable
private fun MediaCard(snippet: Snippet) {
    val context = LocalContext.current
    val path = snippet.filePath
    val request = remember(path) { ImageRequest.Builder(context).data(path?.let(::File)).build() }
    Box(Modifier.heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .sizeIn(maxWidth = 260.dp, maxHeight = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ClipsyColors.CardBorder, RoundedCornerShape(14.dp)),
        )
    }
}

@Composable
private fun DocumentCard(snippet: Snippet) {
    val page = if (Thumbnails.isPdf(snippet)) rememberThumbnail(snippet) else null
    if (page != null) {
        // A PDF shows its first page.
        val context = LocalContext.current
        AsyncImage(
            model = remember(page) { ImageRequest.Builder(context).data(page).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(168.dp)
                .clip(CardShape)
                .border(1.dp, ClipsyColors.CardBorder, CardShape),
        )
        return
    }
    Box(
        Modifier
            .size(width = 112.dp, height = 144.dp)
            .clip(CardShape)
            .background(ClipsyColors.Card)
            .border(1.dp, ClipsyColors.CardBorder, CardShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(TablerIcons.FileText, null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(36.dp))
            Text(
                fileKind(snippet.mimeType, snippet.name),
                Modifier.clip(RoundedCornerShape(6.dp)).background(ClipsyColors.Accent).padding(horizontal = 8.dp, vertical = 3.dp),
                color = ClipsyColors.OnAccent,
                fontSize = ClipsyType.Caption,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
