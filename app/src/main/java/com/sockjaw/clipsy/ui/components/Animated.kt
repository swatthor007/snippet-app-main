package com.sockjaw.clipsy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.sockjaw.clipsy.ui.theme.Motion

/** Small icons and chips: pop in, shrink out. */
@Composable
fun FadeScaleVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = scaleIn(Motion.pop(), initialScale = 0.6f) + fadeIn(Motion.standard(Motion.QUICK)),
        exit = scaleOut(Motion.exit(), targetScale = 0.8f) + fadeOut(Motion.exit()),
    ) { content() }
}

/** Helper or error text under a field: slides open and closed with the surrounding layout. */
@Composable
fun ColumnScope.ExpandingVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = expandVertically(Motion.enter(Motion.BASE)) + fadeIn(Motion.enter(Motion.BASE)),
        exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit(Motion.QUICK)),
    ) { content() }
}

/** Text whose value rolls vertically when it changes (counts, labels like Free/Taken). */
@Composable
fun CrossfadeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = TextStyle.Default,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(Motion.enter(Motion.BASE)) { it / 2 } + fadeIn(Motion.enter(Motion.BASE)))
                .togetherWith(slideOutVertically(Motion.exit()) { -it / 2 } + fadeOut(Motion.exit(Motion.QUICK)))
        },
        label = "text",
    ) { Text(it, color = color, style = style, fontSize = fontSize) }
}

/** Error feedback: 3 damped horizontal oscillations, ~320 ms, no overshoot at rest. */
@Composable
fun Modifier.shakeOn(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        for (x in listOf(10f, -8f, 6f, -3f, 0f)) offset.animateTo(x, Motion.standard(64))
    }
    return graphicsLayer { translationX = offset.value * 1.dp.toPx() }
}
