package com.sockjaw.clipsy.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.LocalContext
import com.sockjaw.clipsy.ui.theme.Haptics
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * The preview being shared shrinks and flies into the Share button, then the share sheet opens.
 * When the app comes back it pops back up in place, growing from small with a little overshoot. Skipped entirely with reduced motion.
 */
@Stable
class ShareWarp internal constructor(private val reduced: Boolean) {
    internal val progress = Animatable(0f)
    internal var source by mutableStateOf(Offset.Unspecified)
    internal var target by mutableStateOf(Offset.Unspecified)
    /** True while popping back in: scale only, no travel. */
    internal var returning by mutableStateOf(false)

    /** Plays the warp, then starts [intent]. */
    suspend fun launch(context: Context, intent: Intent) {
        if (!reduced && source != Offset.Unspecified && target != Offset.Unspecified) {
            // The vibration is timed to the flight, so start both together.
            Haptics.swoosh(context)
            progress.animateTo(1f, tween(340, easing = Motion.Exit))
        }
        context.startActivity(intent)
    }

    internal suspend fun restore(context: Context) {
        if (progress.value <= 0f) return
        Haptics.pop(context)
        returning = true
        try {
            progress.animateTo(0f, Motion.pop())
        } finally {
            returning = false
        }
    }
}

@Composable
fun rememberShareWarp(): ShareWarp {
    val reduced = LocalReducedMotion.current
    val warp = remember(reduced) { ShareWarp(reduced) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { warp.restore(context) } }
    return warp
}

/** The thing being shared: it travels to the target, shrinking and fading as it goes. */
fun Modifier.shareWarpSource(warp: ShareWarp): Modifier = this
    .onGloballyPositioned { warp.source = it.boundsInRoot().center }
    .graphicsLayer {
        val p = warp.progress.value
        if (warp.returning) {
            // Pops up where it belongs; the spring's overshoot (p below 0) swells it just past full size.
            val s = 1f - 0.92f * p
            scaleX = s
            scaleY = s
            alpha = (1f - p).coerceIn(0f, 1f)
        } else if (p > 0f && warp.source != Offset.Unspecified && warp.target != Offset.Unspecified) {
            // Ease the path so it lifts first and then dives, rather than sliding in a straight line.
            val d = warp.target - warp.source
            translationX = d.x * p
            translationY = d.y * p * p
            val s = 1f - 0.92f * p
            scaleX = s
            scaleY = s
            alpha = 1f - p * p
            rotationZ = -6f * sin(PI.toFloat() * p)
        }
    }

/** The Share button: records where it is and gives a small gulp as the preview arrives. */
fun Modifier.shareWarpTarget(warp: ShareWarp): Modifier = this
    .onGloballyPositioned { warp.target = it.boundsInRoot().center }
    .graphicsLayer {
        val p = warp.progress.value
        val bump = if (!warp.returning && p > 0.7f) sin(PI.toFloat() * (p - 0.7f) / 0.3f) else 0f
        scaleX = 1f + 0.35f * bump
        scaleY = 1f + 0.35f * bump
    }

/** The ground shadow under a shared preview: it stays put and shrinks away as the preview leaves. */
fun Modifier.shareWarpShadow(warp: ShareWarp): Modifier = graphicsLayer {
    val p = warp.progress.value
    if (p != 0f) {
        val s = if (warp.returning) 1f - p else (1f - p).coerceAtLeast(0f)
        scaleX = s
        scaleY = s
        alpha = (1f - p).coerceIn(0f, 1f)
    }
}
