package com.sockjaw.clipsy.service

import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.X
import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ClipsyApp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sockjaw.clipsy.ui.theme.ClipsyTheme
import com.sockjaw.clipsy.ui.theme.isDark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.sockjaw.clipsy.ui.theme.LocalPalette
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.text.TextStyle
import com.sockjaw.clipsy.ui.components.TokenText
import com.sockjaw.clipsy.ui.theme.Mono
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable

data class PillUi(
    val snippetId: Long,
    val title: String,
    val icon: ImageVector,
    /** "space", "enter" or null when only a tap inserts it. */
    val key: String?,
    val verb: String,
    val above: Boolean,
    val done: Boolean = false,
    /** Square preview for media and PDF snippets. */
    val thumb: ImageBitmap? = null,
    /** Media and documents get the tall card (preview on top, text below); text keeps the slim pill. */
    val card: Boolean = false,
    /** File type shown in the card's square when there's no preview, e.g. "XML". */
    val kind: String? = null,
    /** Text snippets: what will be typed, whitespace folded to single spaces. */
    val preview: String? = null,
    /** The preview is a link: one line is enough. */
    val link: Boolean = false,
)

/**
 * The floating suggestion pill drawn over other apps. Lives in an accessibility overlay
 * window (no draw-over-apps permission needed) that wraps its content, so it only takes
 * touches where the pill actually is. The window is removed once the pill has animated
 * out, so nothing lingers on screen or intercepts touches between suggestions.
 */
class SuggestionOverlay(
    private val service: AccessibilityService,
    private val onTap: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var ui by mutableStateOf<PillUi?>(null)
    private val density = service.resources.displayMetrics.density

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    val visibleSnippetId: Long? get() = ui?.takeIf { !it.done }?.snippetId
    val isShowing: Boolean get() = ui != null

    /** Top of the field the pill sits above, so the window can be re-placed once its height is known. */
    private var anchorTop = 0
    private var placedAbove = true

    /**
     * Estimated window height, including the 8dp padding around the pill: a text preview adds a
     * line or two. The real height is applied after layout (see [attach]), so this only has to be
     * close enough that the first frame doesn't jump.
     */
    private fun estimatedHeightDp(pill: PillUi): Int = when {
        pill.card -> CARD_HEIGHT_DP
        pill.preview == null -> 60
        pill.link -> 84
        else -> 102
    }

    fun show(pill: PillUi, anchor: Rect) {
        val pillHeight = (estimatedHeightDp(pill) * density).toInt()
        val pillWidth = ((if (pill.card) CARD_WIDTH_DP + 16 else 320) * density).toInt()
        val screenW = service.resources.displayMetrics.widthPixels
        val above = anchor.top - pillHeight > (48 * density)
        val x = (anchor.left - (8 * density).toInt()).coerceIn(0, (screenW - pillWidth).coerceAtLeast(0))
        val y = if (above) anchor.top - pillHeight else anchor.bottom
        anchorTop = anchor.top
        placedAbove = ui?.above ?: above
        val current = view
        if (current == null) {
            params.x = x
            params.y = y
            attach()
        } else if (ui == null && (params.x != x || params.y != y)) {
            // Still fading out from a previous field: move before showing again.
            params.x = x
            params.y = y
            wm.updateViewLayout(current, params)
        }
        ui = pill.copy(above = ui?.above ?: above)
    }

    fun markDone() { ui = ui?.copy(done = true) }

    /** Animates the pill out; the window is removed when the exit animation ends. */
    fun hide() { ui = null }

    fun destroy() {
        ui = null
        detach()
        owner.destroy()
    }

    private fun attach() {
        if (owner.lifecycle.currentState == Lifecycle.State.INITIALIZED) owner.start()
        val v = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                val store = (service.application as ClipsyApp).container.settings
                val themes = remember { store.settings.map { it.theme }.distinctUntilChanged() }
                val mode by themes.collectAsState(remember { store.settings.value.theme })
                ClipsyTheme(dark = mode.isDark(), fillBackground = false) {
                    Pill(ui, onTap, onDismiss, onHidden = { main.post { if (ui == null) detach() } })
                }
            }
        }
        // Above the field, the pill's bottom edge must sit on the field's top edge whatever its height
        // (a text preview makes it taller), so re-place the window whenever its measured height changes.
        v.addOnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (!placedAbove || bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            val y = (anchorTop - (bottom - top)).coerceAtLeast(0)
            if (params.y != y) {
                params.y = y
                runCatching { wm.updateViewLayout(view, params) }
            }
        }
        wm.addView(v, params)
        view = v
    }

    private fun detach() {
        view?.let { v ->
            v.disposeComposition()
            runCatching { wm.removeView(v) }
        }
        view = null
    }
}

@Composable
private fun Pill(ui: PillUi?, onTap: () -> Unit, onDismiss: () -> Unit, onHidden: () -> Unit) {
    // Keep the last pill so the exit animation still has something to draw.
    var last by remember { mutableStateOf(ui) }
    if (ui != null) last = ui
    val above = last?.above ?: true
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = ui != null
    // Once fully gone, let the overlay drop its window.
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) onHidden()
    }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(90)) +
            scaleIn(Motion.pop(), initialScale = 0.82f, transformOrigin = TransformOrigin(0.12f, if (above) 1f else 0f)) +
            slideInVertically(Motion.pop()) { if (above) it / 4 else -it / 4 },
        exit = fadeOut(Motion.exit(110)) + scaleOut(Motion.exit(110), targetScale = 0.94f),
    ) {
        last?.let { PillBody(it, onTap, onDismiss) }
    }
}

@Composable
private fun PillBody(ui: PillUi, onTap: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Swipe sideways to dismiss: follows the finger, flings away past a threshold, else settles back.
    val dragX = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 72.dp.toPx() }
    val offPx = with(LocalDensity.current) { 360.dp.toPx() }
    Box(
        Modifier
            .padding(8.dp)
            .graphicsLayer {
                translationX = dragX.value
                alpha = 1f - (kotlin.math.abs(dragX.value) / offPx).coerceIn(0f, 1f)
            }
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = !ui.done,
                state = rememberDraggableState { delta -> scope.launch { dragX.snapTo(dragX.value + delta) } },
                onDragStopped = { velocity ->
                    val gone = kotlin.math.abs(dragX.value) > dismissPx || kotlin.math.abs(velocity) > 1500f
                    if (gone) {
                        val dir = if (dragX.value + velocity * 0.05f < 0) -1f else 1f
                        dragX.animateTo(dir * offPx, Motion.exit(Motion.QUICK))
                        onDismiss()
                    } else {
                        dragX.animateTo(0f, Motion.settle())
                    }
                },
            ),
    ) {
        if (ui.card) FileCard(ui, onTap, onDismiss) else SlimPill(ui, onTap, onDismiss)
    }
}

/**
 * Text snippets: icon on the left, the name, a short preview of what will be typed (tokens as
 * chips, like in the app), then the hint. Corners keep the one-line pill's radius as it grows.
 */
@Composable
private fun SlimPill(ui: PillUi, onTap: () -> Unit, onDismiss: () -> Unit) {
    val shape = if (ui.preview != null && !ui.done) RoundedCornerShape(22.dp) else CircleShape
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .widthIn(max = 300.dp)
            .clip(shape)
            .shadow(if (LocalPalette.current.isDark) 0.dp else 6.dp, shape, ambientColor = ClipsyColors.Text, spotColor = ClipsyColors.Text)
            .background(ClipsyColors.Card)
            .border(1.dp, ClipsyColors.CardBorder, shape)
            .pressable(enabled = !ui.done, onClickLabel = "Insert ${ui.title}", pressedScale = 0.95f, onClick = onTap)
            .padding(start = 6.dp, end = if (ui.done) 16.dp else 4.dp, top = if (ui.preview != null && !ui.done) 10.dp else 6.dp, bottom = if (ui.preview != null && !ui.done) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Secondary layer: the icon swaps to a check that pops in on success.
        AnimatedContent(
            targetState = ui.done,
            transitionSpec = {
                (scaleIn(Motion.pop(), initialScale = 0.4f) + fadeIn(tween(90)))
                    .togetherWith(scaleOut(Motion.exit(90), targetScale = 0.6f) + fadeOut(Motion.exit(90)))
            },
            label = "pillIcon",
        ) { done ->
            val thumb = ui.thumb
            if (!done && thumb != null) {
                // What will be pasted, at a glance.
                Image(
                    thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, ClipsyColors.CardBorder, RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    Modifier.size(if (thumb != null) 40.dp else 32.dp).clip(CircleShape).background(if (done) ClipsyColors.Accent else ClipsyColors.AccentFaint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (done) TablerIcons.Check else ui.icon, null, tint = if (done) ClipsyColors.OnAccent else ClipsyColors.Accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ui.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ClipsyColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Folds away once inserted, so the pill shrinks back to its one-line "Inserted" form.
            AnimatedVisibility(ui.preview != null && !ui.done, enter = fadeIn(), exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit(90))) {
                TokenText(
                    ui.preview.orEmpty(),
                    Modifier.padding(bottom = 2.dp),
                    color = if (ui.link) ClipsyColors.Accent else ClipsyColors.TextMuted,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    maxLines = if (ui.link) 1 else 2,
                )
            }
            Hint(ui)
        }
        if (!ui.done) DismissButton(onDismiss)
    }
}

private const val CARD_WIDTH_DP = 200
private const val CARD_HEIGHT_DP = 296

/**
 * Media and documents: a tall card so you can see what's about to be pasted. Square preview on
 * top (the thumbnail, or the file type when there isn't one), name and hint underneath. On
 * insert, a check pops over the preview.
 */
@Composable
private fun FileCard(ui: PillUi, onTap: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val previewShape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(CARD_WIDTH_DP.dp)
            .shadow(if (LocalPalette.current.isDark) 0.dp else 8.dp, shape, ambientColor = ClipsyColors.Text, spotColor = ClipsyColors.Text)
            .clip(shape)
            .background(ClipsyColors.Card)
            .border(1.dp, ClipsyColors.CardBorder, shape)
            .pressable(enabled = !ui.done, onClickLabel = "Insert ${ui.title}", pressedScale = 0.96f, onClick = onTap)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(previewShape)
                .background(ClipsyColors.CardAlt)
                .border(0.5.dp, ClipsyColors.CardBorder, previewShape),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = ui.thumb
            if (thumb != null) {
                Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(ui.icon, null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(40.dp))
                    ui.kind?.let { Text(it, fontFamily = Mono, fontSize = 12.sp, color = ClipsyColors.TextSecondary) }
                }
            }
            // Success: the preview dims under an accent wash and a check pops in the middle.
            androidx.compose.animation.AnimatedVisibility(
                ui.done,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(Motion.enter(Motion.BASE)),
                exit = fadeOut(Motion.exit()),
            ) {
                Box(Modifier.fillMaxSize().background(ClipsyColors.Accent.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        ui.done,
                        enter = scaleIn(Motion.pop(), initialScale = 0.4f) + fadeIn(tween(90)),
                        exit = fadeOut(Motion.exit(90)),
                    ) {
                        Icon(TablerIcons.Check, null, tint = ClipsyColors.OnAccent, modifier = Modifier.size(44.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ui.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ClipsyColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Hint(ui)
            }
            if (!ui.done) DismissButton(onDismiss)
        }
    }
}

/** "space or tap to attach", or "Inserted" once done. */
@Composable
private fun Hint(ui: PillUi) {
    AnimatedContent(
        targetState = ui.done,
        transitionSpec = {
            (slideInVertically(Motion.enter(Motion.BASE)) { it } + fadeIn(Motion.enter(Motion.BASE)))
                .togetherWith(fadeOut(Motion.exit(90)))
        },
        label = "pillHint",
    ) { done ->
        if (done) {
            Text("Inserted", fontSize = 12.sp, color = ClipsyColors.Accent)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (ui.key != null) {
                    Text(
                        ui.key,
                        Modifier.clip(RoundedCornerShape(4.dp)).background(ClipsyColors.CardAlt).padding(horizontal = 5.dp, vertical = 1.dp),
                        fontFamily = Mono, fontSize = 11.sp, color = ClipsyColors.TextMuted,
                    )
                    Text("or tap to ${ui.verb}", fontSize = 12.sp, color = ClipsyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Tap to ${ui.verb}", fontSize = 12.sp, color = ClipsyColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .pressable(onClickLabel = "Dismiss suggestion", pressedScale = 0.9f, onClick = onDismiss)
            .semantics { contentDescription = "Dismiss suggestion" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(TablerIcons.X, null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(16.dp))
    }
}
