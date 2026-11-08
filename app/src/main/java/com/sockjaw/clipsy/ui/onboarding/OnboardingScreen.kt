package com.sockjaw.clipsy.ui.onboarding

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.ui.components.CardShape
import com.sockjaw.clipsy.ui.components.PrimaryButton
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.components.screenRoot
import com.sockjaw.clipsy.ui.settings.isServiceEnabled
import com.sockjaw.clipsy.ui.settings.openAccessibilitySettings
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.Mono
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.riseIn
import compose.icons.TablerIcons
import compose.icons.tablericons.AlignLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.Lock
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    var askedForService by rememberSaveable { mutableStateOf(false) }
    fun finish() { container.settings.update { it.copy(onboardingDone = true) }; onDone() }
    // Coming back from Accessibility settings with the service on: move straight to Home.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (askedForService && isServiceEnabled(context)) {
            container.toaster.show("Clipsy is on", ToastKind.Success)
            finish()
        }
    }

    Column(Modifier.screenRoot().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Column(Modifier.riseIn(0), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Type less.", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Save text or files once, give each a short keyword, and Clipsy swaps the keyword for the snippet in any app.",
                    color = ClipsyColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            ExpansionDemo(Modifier.riseIn(1))
            Column(Modifier.fillMaxWidth().riseIn(2), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("To turn it on", style = MaterialTheme.typography.titleMedium)
                listOf("Open Accessibility settings", "Tap Clipsy, then switch it on", "Come back here").forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).border(1.dp, ClipsyColors.Separator, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", color = ClipsyColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = ClipsyType.Caption)
                        }
                        Text(t, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Row(Modifier.riseIn(3), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(TablerIcons.Lock, null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(
                    "Everything stays on your phone. Password fields are never read.",
                    color = ClipsyColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp).riseIn(4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PrimaryButton("Open settings", onClick = {
                askedForService = true
                openAccessibilitySettings(context)
            })
            Text(
                if (askedForService) "Continue without it" else "Not now",
                color = ClipsyColors.Accent,
                fontSize = ClipsyType.Body,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).pressable { finish() }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private enum class DemoStep { Typing, Suggesting, Inserted }

/**
 * A working miniature of the real thing, built from the same pieces as the app: a text field,
 * the suggestion pill, and the swap. It plays twice, then rests on the finished state so it
 * never loops forever. With animations off it shows the finished state straight away.
 */
@Composable
private fun ExpansionDemo(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val before = "See you Friday. sig"
    val after = "See you Friday. Best, Jordan"
    var typed by remember { mutableIntStateOf(if (reduced) before.length else 0) }
    var step by remember { mutableStateOf(if (reduced) DemoStep.Inserted else DemoStep.Typing) }

    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(2) { round ->
            step = DemoStep.Typing
            typed = 0
            delay(500)
            while (typed < before.length) {
                typed++
                delay(if (before[typed - 1] == ' ') 110 else 55)
            }
            step = DemoStep.Suggesting
            delay(1100)
            step = DemoStep.Inserted
            if (round == 0) delay(1900)
        }
    }

    val text = if (step == DemoStep.Inserted) after else before.take(typed)
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Example: typing sig becomes Best, Jordan" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The field.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(CardShape)
                .background(ClipsyColors.Card)
                .border(1.dp, ClipsyColors.CardBorder, CardShape)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(
                targetState = step == DemoStep.Inserted,
                transitionSpec = { fadeIn(Motion.enter(Motion.BASE)).togetherWith(fadeOut(Motion.exit(Motion.QUICK))) },
                label = "demoText",
            ) { inserted ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (inserted) {
                        Text(after, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        val keywordStart = before.lastIndexOf("sig")
                        val shown = text
                        Text(shown.take(keywordStart.coerceAtMost(shown.length)), style = MaterialTheme.typography.bodyLarge)
                        if (shown.length > keywordStart) {
                            Text(shown.drop(keywordStart), style = MaterialTheme.typography.bodyLarge, fontFamily = Mono, color = ClipsyColors.Accent)
                        }
                    }
                    Box(Modifier.padding(start = 1.dp).width(1.5.dp).height(20.dp).background(ClipsyColors.Accent))
                }
            }
        }
        // The pill, as it appears over other apps.
        Box(Modifier.height(48.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = step != DemoStep.Typing,
                enter = fadeIn(tween(90)) + scaleIn(Motion.pop(), initialScale = 0.85f, transformOrigin = TransformOrigin(0.1f, 0f)) +
                    slideInVertically(Motion.pop()) { -it / 4 },
                exit = fadeOut(Motion.exit(110)) + scaleOut(Motion.exit(110), targetScale = 0.95f),
            ) {
                DemoPill(done = step == DemoStep.Inserted)
            }
        }
    }
}

@Composable
private fun DemoPill(done: Boolean) {
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(ClipsyColors.Card)
            .border(1.dp, ClipsyColors.CardBorder, CircleShape)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                (scaleIn(Motion.pop(), initialScale = 0.4f) + fadeIn(tween(90)))
                    .togetherWith(scaleOut(Motion.exit(90), targetScale = 0.6f) + fadeOut(Motion.exit(90)))
            },
            label = "demoIcon",
        ) { isDone ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (isDone) ClipsyColors.Accent else ClipsyColors.AccentFaint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isDone) TablerIcons.Check else TablerIcons.AlignLeft,
                    null,
                    tint = if (isDone) ClipsyColors.OnAccent else ClipsyColors.Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Email signature", fontSize = ClipsyType.Label, fontWeight = FontWeight.SemiBold)
            AnimatedContent(targetState = done, label = "demoHint") { isDone ->
                if (isDone) {
                    Text("Inserted", fontSize = ClipsyType.Caption, color = ClipsyColors.Accent)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            "space",
                            Modifier.clip(RoundedCornerShape(4.dp)).background(ClipsyColors.CardAlt).padding(horizontal = 5.dp, vertical = 1.dp),
                            fontFamily = Mono, fontSize = ClipsyType.Micro, color = ClipsyColors.TextMuted,
                        )
                        Text("or tap to insert", fontSize = ClipsyType.Caption, color = ClipsyColors.TextSecondary)
                    }
                }
            }
        }
    }
}
