package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.Check
import compose.icons.tablericons.InfoCircle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.DarkPalette
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ToastKind { Info, Success, Error }

data class Toast(
    val message: String,
    val kind: ToastKind = ToastKind.Info,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
) {
    val durationMs: Long get() = if (actionLabel != null) UNDO_WINDOW_MS else 2_600
}

const val UNDO_WINDOW_MS = 4_500L

class Toaster {
    private val _current = MutableStateFlow<Toast?>(null)
    val current: StateFlow<Toast?> = _current.asStateFlow()

    fun show(message: String, kind: ToastKind = ToastKind.Info, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _current.value = Toast(message, kind, actionLabel, onAction)
    }

    fun dismiss(id: Long) = _current.update { if (it?.id == id) null else it }
}

/** Bottom toast that rises in with a light spring and drops away quickly. */
@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val toast by toaster.current.collectAsStateWithLifecycle()
    LaunchedEffect(toast?.id) {
        val t = toast ?: return@LaunchedEffect
        delay(t.durationMs)
        toaster.dismiss(t.id)
    }
    AnimatedContent(
        targetState = toast,
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
        transitionSpec = {
            (slideInVertically(Motion.pop()) { it } + fadeIn(Motion.standard(Motion.QUICK)) + scaleIn(Motion.pop(), initialScale = 0.92f))
                .togetherWith(slideOutVertically(Motion.exit()) { it / 2 } + fadeOut(Motion.exit()) + scaleOut(Motion.exit(), targetScale = 0.96f))
                .using(SizeTransform(clip = false))
        },
        label = "toast",
    ) { t ->
        if (t == null) {
            Box(Modifier)
        } else {
            ToastCard(t) { t.onAction?.invoke(); toaster.dismiss(t.id) }
        }
    }
}

@Composable
private fun ToastCard(toast: Toast, onAction: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(ClipsyColors.ToastBg)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (icon, tint) = when (toast.kind) {
            // The toast is dark in both themes, so it borrows the dark palette's accent and danger.
            ToastKind.Success -> TablerIcons.Check to DarkPalette.accent
            ToastKind.Error -> TablerIcons.AlertCircle to DarkPalette.danger
            ToastKind.Info -> TablerIcons.InfoCircle to ClipsyColors.ToastText
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(toast.message, Modifier.weight(1f).padding(vertical = 14.dp), fontSize = ClipsyType.Label, color = ClipsyColors.ToastText)
        if (toast.actionLabel != null) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).pressable(onClick = onAction).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(toast.actionLabel, color = DarkPalette.accent, fontSize = ClipsyType.Label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
