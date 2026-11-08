package com.sockjaw.clipsy.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Clipsy's motion identity.
 *
 * Personality: corporate utility. Quick and decisive, springs only on light elements
 * (pill, checks, toasts). Entrances decelerate, exits accelerate.
 */
object Motion {
    /** On-screen movement and state changes. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Things arriving: fast start, soft landing. */
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Things leaving: gentle start, quick departure. */
    val Exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    const val QUICK = 120
    const val BASE = 240
    const val SLOW = 380
    /** Per-item delay for list cascades; total stays under ~300 ms. */
    const val STAGGER = 35L

    fun <T> enter(duration: Int = SLOW, delay: Int = 0): FiniteAnimationSpec<T> = tween(duration, delay, Emphasized)
    fun <T> exit(duration: Int = QUICK + 40): FiniteAnimationSpec<T> = tween(duration, easing = Exit)
    fun <T> standard(duration: Int = BASE): FiniteAnimationSpec<T> = tween(duration, easing = Standard)
    /** Light elements: ~10% overshoot. */
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
    /** Medium elements (thumbs, indicators, rows): a hint of overshoot, settles fast. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
}

/** True when the user turned animations off (Developer options or Accessibility > Remove animations). */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(readReducedMotion(context)) }
    // The setting can change while the app is open (Quick Settings, Developer options): check again on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduced = readReducedMotion(context) }
    return reduced
}

private fun readReducedMotion(context: android.content.Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Clickable with press feedback: scales down to [pressedScale] while held and springs back on release.
 * [onLongClick], when given, fires on a long press instead.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    pressedScale: Float = 0.97f,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) pressedScale else 1f,
        animationSpec = if (pressed) tween(100, easing = Motion.Standard) else Motion.pop(),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (onLongClick == null) Modifier.clickable(source, LocalIndication.current, enabled, onClickLabel, role, onClick)
            else Modifier.combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        )
}

/**
 * First-appearance entrance: fade and rise, delayed by [index] × stagger.
 * Plays once per saved state, so returning to a screen doesn't replay it.
 */
@Composable
fun Modifier.riseIn(index: Int = 0): Modifier {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable { mutableStateOf(false) }
    // With animations off, content is simply there: no fade, no stagger.
    val progress = remember { Animatable(if (played || reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) { progress.snapTo(1f); played = true }
        else if (!played) {
            delay(index.coerceAtMost(8) * Motion.STAGGER)
            progress.animateTo(1f, Motion.enter())
            played = true
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = if (reduced) 0f else (1f - progress.value) * 16.dp.toPx()
    }
}
