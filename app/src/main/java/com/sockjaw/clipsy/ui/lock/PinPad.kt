package com.sockjaw.clipsy.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sockjaw.clipsy.data.vault.PinKdf
import com.sockjaw.clipsy.data.vault.PinResult
import com.sockjaw.clipsy.ui.components.ClipsySheet
import com.sockjaw.clipsy.ui.components.SheetTitle
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.Backspace
import compose.icons.tablericons.Fingerprint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Six dots and a 3×4 keypad. Calls [onComplete] with the sixth digit and clears itself, so the
 * caller only decides what a PIN means. A change of [errorTick] shakes the dots and tints them red.
 * Digits and Backspace from a hardware keyboard work too; the pad takes focus when it appears.
 */
@Composable
fun PinPad(
    message: String?,
    errorTick: Int,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFingerprint: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    var digits by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    var failing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // The sheet's window may not be focusable yet on the first frame, so tolerate a failed request.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        failing = true
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        if (!reduced) for (target in listOf(-12f, 12f, -8f, 8f, 0f)) shake.animateTo(target, tween(55))
        delay(250)
        failing = false
    }

    fun press(d: String) {
        if (!enabled || digits.length >= PinKdf.PIN_LENGTH) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        digits += d
        if (digits.length == PinKdf.PIN_LENGTH) {
            val entered = digits
            onComplete(entered)
            scope.launch { delay(160); digits = "" }
        }
    }

    fun delete() {
        if (digits.isNotEmpty()) digits = digits.dropLast(1)
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val char = event.utf16CodePoint.toChar()
                when {
                    char in '0'..'9' -> { press(char.toString()); true }
                    event.key == Key.Backspace || event.key == Key.Delete -> { delete(); true }
                    else -> false
                }
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .offset(x = shake.value.dp)
                .padding(vertical = 18.dp)
                .semantics { contentDescription = "${digits.length} of ${PinKdf.PIN_LENGTH} digits entered" },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(PinKdf.PIN_LENGTH) { i ->
                val filled = i < digits.length
                val color by animateColorAsState(
                    when {
                        failing -> ClipsyColors.Danger
                        filled -> ClipsyColors.Accent
                        else -> ClipsyColors.Handle
                    },
                    Motion.standard(Motion.QUICK),
                    label = "pinDot",
                )
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
            }
        }
        Text(
            message.orEmpty(),
            Modifier.heightIn(min = 36.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = if (failing || message?.startsWith("Too many") == true) ClipsyColors.Danger else ClipsyColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(4.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("fp", "0", "del")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    when (key) {
                        "fp" -> if (onFingerprint != null) PadKey({ Icon(TablerIcons.Fingerprint, "Use fingerprint", tint = ClipsyColors.Accent, modifier = Modifier.size(28.dp)) }, "Use fingerprint") { onFingerprint() }
                        else Spacer(Modifier.size(width = 80.dp, height = 64.dp))
                        "del" -> PadKey({ Icon(TablerIcons.Backspace, null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(26.dp)) }, "Delete") { delete() }
                        else -> PadKey({ Text(key, style = MaterialTheme.typography.displaySmall) }, key) { press(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(content: @Composable () -> Unit, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 80.dp, height = 64.dp)
            .padding(4.dp)
            .clip(CircleShape)
            .pressable(role = Role.Button, pressedScale = 0.92f, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "0:25" style countdown, rounded up so it never reads 0:00 while still locked. */
fun formatWait(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(1)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Asks for the PIN to open locked snippets. */
@Composable
fun PinUnlockSheet(
    check: (String) -> PinResult,
    lockedUntil: Long,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
    onFingerprint: (() -> Unit)? = null,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var errorTick by remember { mutableStateOf(0) }
    var until by remember { mutableLongStateOf(lockedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (until > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val waiting = until > now
    ClipsySheet(onDismiss) { close ->
        SheetTitle("Enter your PIN", "Opens your locked snippets.")
        PinPad(
            message = if (waiting) "Too many tries. Try again in ${formatWait(until - now)}." else message,
            errorTick = errorTick,
            enabled = !waiting,
            onFingerprint = onFingerprint,
            onComplete = { pin ->
                when (val r = check(pin)) {
                    PinResult.Ok -> close { onUnlocked() }
                    is PinResult.Wrong -> {
                        errorTick++
                        message = if (r.triesBeforeLockout in 1..3) "Wrong PIN. ${r.triesBeforeLockout} ${if (r.triesBeforeLockout == 1) "try" else "tries"} left before a pause." else "Wrong PIN."
                    }
                    is PinResult.LockedOut -> { errorTick++; until = r.until; now = System.currentTimeMillis() }
                }
            },
        )
    }
}

/** Choose a new PIN: type it, then type it again. */
@Composable
fun PinCreateSheet(title: String, onCreated: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var errorTick by remember { mutableStateOf(0) }
    ClipsySheet(onDismiss) { close ->
        SheetTitle(
            if (first == null) title else "Enter it again",
            if (first == null) "Six digits. If you forget it, locked snippets can’t be recovered." else "Type the same PIN to confirm.",
        )
        PinPad(
            message = message,
            errorTick = errorTick,
            onComplete = { pin ->
                val firstPin = first
                when {
                    firstPin == null -> { first = pin; message = null }
                    firstPin == pin -> close { onCreated(pin) }
                    else -> { first = null; errorTick++; message = "Those didn’t match. Start again." }
                }
            },
        )
    }
}
