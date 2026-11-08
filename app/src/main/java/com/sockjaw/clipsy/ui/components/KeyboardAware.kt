package com.sockjaw.clipsy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * For an in-place editor on a scrolling screen that shrinks above the keyboard (imePadding):
 * once the keyboard has finished opening, scrolls so the whole editor, buttons included, sits
 * clear of it with [extra] room below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.keepAboveKeyboard(extra: Dp = 24.dp): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val keyboardUp = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(keyboardUp) {
        if (keyboardUp) {
            delay(250) // let the keyboard finish sliding in
            val room = with(density) { extra.toPx() }
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + room))
        }
    }
    return this.bringIntoViewRequester(requester).onSizeChanged { size = it }
}
