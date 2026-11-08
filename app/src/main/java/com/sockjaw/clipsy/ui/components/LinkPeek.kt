package com.sockjaw.clipsy.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.ClipsyType
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.ExternalLink
import compose.icons.tablericons.X
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pull the page down past the top to preview its link. The content follows the finger with
 * growing resistance; past the threshold it ticks and asks you to let go, and letting go there
 * calls [onPreview]. Either way the page springs back into place.
 *
 * With [nudge], the page pulls itself partway down once, so people find out the gesture exists;
 * [onNudged] fires once it has played, or as soon as they touch the page instead.
 */
@Composable
fun PullToPreview(
    enabled: Boolean,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    nudge: Boolean = false,
    onNudged: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val threshold = with(density) { 96.dp.toPx() }
    val maxPull = with(density) { 220.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val currentOnPreview by androidx.compose.runtime.rememberUpdatedState(onPreview)
    val currentOnNudged by androidx.compose.runtime.rememberUpdatedState(onNudged)
    val reduced = LocalReducedMotion.current
    var nudging by remember { mutableStateOf<Job?>(null) }
    var handedOver by remember { mutableStateOf(false) }
    // A touch takes over from the nudge straight away, from wherever the page is.
    fun stopNudge() { nudging?.let { handedOver = true; it.cancel(); nudging = null; currentOnNudged() } }

    LaunchedEffect(enabled, nudge) {
        if (!enabled || !nudge || reduced) return@LaunchedEffect
        nudging = launch {
            // Let the page settle in first, so the nudge reads as its own moment.
            try {
                delay(900)
                animate(0f, threshold * 0.62f, animationSpec = tween(520, easing = Motion.Standard)) { v, _ -> pull = v }
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                delay(1100)
                animate(pull, 0f, animationSpec = spring(0.55f, Spring.StiffnessLow)) { v, _ -> pull = v.coerceAtLeast(0f) }
                nudging = null
                currentOnNudged()
            } finally {
                // Cut short by the page itself (the preview went away): don't leave it hanging open.
                if (!handedOver) pull = 0f
            }
        }
    }

    val connection = remember(enabled, threshold, maxPull) {
        object : NestedScrollConnection {
            fun setPull(value: Float) {
                pull = value.coerceIn(0f, maxPull)
                val nowArmed = pull >= threshold
                if (nowArmed != armed) {
                    armed = nowArmed
                    haptics.performHapticFeedback(if (nowArmed) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick)
                }
            }

            // Pushing back up takes the pull away before the page itself scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) stopNudge()
                if (!enabled || pull <= 0f || available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                val before = pull
                setPull(pull + available.y)
                return Offset(0f, pull - before)
            }

            // Whatever the page couldn't scroll past its top becomes pull, damped as it stretches.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                val resistance = 0.55f * (1f - pull / maxPull).coerceAtLeast(0.08f)
                setPull(pull + available.y * resistance)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                stopNudge()
                if (pull <= 0f) return Velocity.Zero
                if (armed) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnPreview()
                }
                armed = false
                // Springs back with a little life in it, starting at the speed the finger left at.
                scope.launch {
                    animate(pull, 0f, initialVelocity = available.y.coerceAtMost(0f), animationSpec = spring(0.72f, Spring.StiffnessMediumLow)) { v, _ -> pull = v.coerceAtLeast(0f) }
                }
                return available
            }
        }
    }

    val pulling by remember { derivedStateOf { pull > 0f } }
    Box(modifier.nestedScroll(connection)) {
        if (pulling) {
            // Sits centred in the gap the pull opens, moved at draw time so dragging never re-lays it out.
            Box(
                Modifier.fillMaxWidth().height(with(density) { threshold.toDp() }).graphicsLayer {
                    alpha = (pull / threshold).coerceIn(0f, 1f)
                    translationY = (pull - threshold) / 2f
                },
                contentAlignment = Alignment.Center,
            ) {
                CrossfadeText(
                    if (armed) "release to preview" else "scroll down to preview",
                    color = if (armed) ClipsyColors.Accent else ClipsyColors.TextSecondary,
                    fontSize = ClipsyType.Label,
                )
            }
        }
        Box(Modifier.graphicsLayer { translationY = pull }) { content() }
    }
}

/** Where the link card sits and what it looks like, so the preview can lift it off the page. */
@Stable
class LinkPeekState(internal val snapshot: GraphicsLayer) {
    /** True from the moment the preview is asked for until it has folded back into the card. */
    var active by mutableStateOf(false)
        private set
    internal var wantsOpen by mutableStateOf(false)
    internal val progress = Animatable(0f)
    internal var cardBounds by mutableStateOf(Rect.Zero)

    fun open() { active = true; wantsOpen = true }
    fun close() { wantsOpen = false }
    internal fun closed() { active = false }
}

@Composable
fun rememberLinkPeekState(): LinkPeekState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LinkPeekState(layer) }
}

/** Marks the card the preview flips out of. It steps aside while the preview is up. */
fun Modifier.linkPeekSource(state: LinkPeekState): Modifier = this
    .onGloballyPositioned { state.cardBounds = it.boundsInRoot() }
    .drawWithContent {
        if (!state.active) state.snapshot.record { this@drawWithContent.drawContent() }
        // Stays put until the flip actually starts, so the card never blinks out while the page is being set up.
        if (state.progress.value <= 0f) drawLayer(state.snapshot)
    }

/**
 * Everything behind the preview: blurred where Android can, otherwise just dimmed by the overlay.
 * Read at draw time only, so the page under it isn't recomposed on every frame of the flip.
 */
fun Modifier.linkPeekBackdrop(state: LinkPeekState): Modifier =
    if (Build.VERSION.SDK_INT < 31) this
    else graphicsLayer {
        val radius = 18.dp.toPx() * state.progress.value.coerceIn(0f, 1f)
        renderEffect = if (state.active && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }

/** Opening is unhurried so the turn reads clearly; closing is a touch quicker. Fixed durations look the same on every phone. */
private val FlipOpen = tween<Float>(720, easing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f))
private val FlipClose = tween<Float>(520, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))

/** A rounded rect at any spot inside the layer, so the card can grow without re-laying out what's in it. */
private data class FrameShape(val rect: Rect, val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(rect, CornerRadius(corner)))
}

/**
 * The web page behind a link card. The card lifts, flips over on its vertical axis and grows to a
 * portrait sheet with the live page on its back; closing plays it in reverse.
 *
 * The sheet is laid out once at its full size and the moving card is drawn as a clipped window
 * into it, so each frame only redraws: nothing recomposes and the web page never resizes mid-flip.
 */
@Composable
fun LinkPeekOverlay(state: LinkPeekState, url: String, title: String?, onOpenExternal: () -> Unit) {
    if (!state.active) return
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    LaunchedEffect(state.wantsOpen, reduced) {
        if (state.wantsOpen) {
            // Let the web page get built first: on slower phones that first frame is heavy, and
            // starting the turn during it makes the opening look like it skipped ahead.
            repeat(2) { withFrameNanos { } }
            if (reduced) state.progress.snapTo(1f) else state.progress.animateTo(1f, FlipOpen)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
            if (reduced) state.progress.snapTo(0f) else state.progress.animateTo(0f, FlipClose)
            state.closed()
        }
    }
    BackHandler(enabled = state.wantsOpen) { state.close() }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val top = WindowInsets.statusBars.getTop(density)
    val bottom = WindowInsets.navigationBars.getBottom(density)

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); size = it.size },
    ) {
        // The dim does the blur's job on phones that can't blur.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = state.progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.35f else 0.62f))
                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Close preview") { state.close() },
        )
        if (size.width == 0) return@Box

        val margin = with(density) { 16.dp.toPx() }
        val to = Rect(margin, top + margin * 1.5f, size.width - margin, size.height - bottom - margin * 1.5f)
        /** Where the card is right now, in the sheet's own coordinates. */
        fun frame(p: Float): Rect {
            val from = state.cardBounds.translate(-origin).translate(-to.topLeft)
            val end = Rect(Offset.Zero, to.size)
            return Rect(lerp(from.left, end.left, p), lerp(from.top, end.top, p), lerp(from.right, end.right, p), lerp(from.bottom, end.bottom, p))
        }

        val sheet = Modifier
            .offset { IntOffset(to.left.roundToInt(), to.top.roundToInt()) }
            .size(with(density) { to.width.toDp() }, with(density) { to.height.toDp() })
        // The shadow lives on its own flat layer: Android works a turned layer's shadow out in 3D and
        // throws it off to the side. This one grows and lifts with the card, and fades as it turns edge-on.
        Box(
            sheet.graphicsLayer {
                val p = state.progress.value
                val f = frame(p)
                transformOrigin = TransformOrigin(f.center.x / size.width, f.center.y / size.height)
                val lift = 1f + 0.06f * kotlin.math.sin(Math.PI * p).toFloat()
                val facing = kotlin.math.cos(Math.PI * p).toFloat()
                scaleX = lift * kotlin.math.abs(facing).coerceAtLeast(0.02f); scaleY = lift
                shadowElevation = lerp(0f, 28.dp.toPx(), p) * facing * facing
                shape = FrameShape(f, lerp(16.dp.toPx(), 24.dp.toPx(), p))
            },
        )
        Box(
            sheet
                .graphicsLayer {
                    val p = state.progress.value
                    val f = frame(p)
                    // Turns about the card's own middle, wherever it is on the way.
                    transformOrigin = TransformOrigin(f.center.x / size.width, f.center.y / size.height)
                    // Front face turns away to 90°, then the back face turns in from -90°, so neither reads mirrored.
                    rotationY = if (p >= 0.5f) 180f * p - 180f else 180f * p
                    cameraDistance = 14f * density.density
                    // Rises off the page as it turns, highest mid-flip.
                    val lift = 1f + 0.06f * kotlin.math.sin(Math.PI * p).toFloat()
                    scaleX = lift; scaleY = lift
                    shape = FrameShape(f, lerp(16.dp.toPx(), 24.dp.toPx(), p))
                    clip = true
                },
        ) {
            // Both faces stay composed so the page is already loading while the card turns.
            PagePeek(url, title, onOpenExternal, onClose = { state.close() }, Modifier.fillMaxSize().graphicsLayer { alpha = if (state.progress.value >= 0.5f) 1f else 0f })
            val cardColor = ClipsyColors.Card
            Canvas(Modifier.fillMaxSize()) {
                val p = state.progress.value
                if (p >= 0.5f) return@Canvas
                val f = frame(p)
                val cardWidth = state.cardBounds.width
                translate(f.left, f.top) {
                    drawRect(cardColor, size = f.size)
                    // The card as it was, kept at its own width and pinned to the top while the sheet grows under it.
                    val s = if (cardWidth > 0f) f.width / cardWidth else 1f
                    scale(s, s, pivot = Offset.Zero) { drawLayer(state.snapshot) }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PagePeek(url: String, title: String?, onOpenExternal: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val shownProgress by animateFloatAsState(progress / 100f, Motion.standard(Motion.QUICK), label = "load")
    val loaderAlpha by animateFloatAsState(if (loading) 1f else 0f, Motion.standard(), label = "loader")
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { webView?.apply { stopLoading(); destroy() } } }

    Column(modifier.background(ClipsyColors.Card)) {
        Box(Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = loaderAlpha }) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shownProgress.coerceIn(0.04f, 1f)).background(ClipsyColors.Accent))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title ?: Links.host(url), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Links.host(url), color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                TablerIcons.ExternalLink, "Open in browser", tint = ClipsyColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).pressable(onClick = onOpenExternal).padding(12.dp).size(20.dp),
            )
            Icon(
                TablerIcons.X, "Close preview", tint = ClipsyColors.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).pressable(onClick = onClose).padding(12.dp).size(20.dp),
            )
        }
        RowDivider(startPadding = 0.dp)
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        // Web pages stay in the preview; app links (intent:, mailto:, …) are ignored here.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.url.scheme !in setOf("http", "https")
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { loading = true }
                        override fun onPageFinished(view: WebView, url: String?) { loading = false }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                            if (newProgress >= 100) loading = false
                        }
                    }
                    loadUrl(Links.withScheme(url))
                    webView = this
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
