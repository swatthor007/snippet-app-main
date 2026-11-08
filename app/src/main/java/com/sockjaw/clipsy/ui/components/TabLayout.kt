package com.sockjaw.clipsy.ui.components

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Home, Library and Settings all start their first element this far below the status bar,
 * so switching tabs never makes the top of the screen jump.
 */
val TabTopPadding = 28.dp

/** Space between a tab's title and the first thing under it. */
val TabTitleGap = 20.dp

val TabContentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = TabTopPadding, bottom = 24.dp)

/** The large title at the top of Library and Settings. No extra padding, so both sit on the same line. */
@Composable
fun TabTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.displaySmall, modifier = modifier)
}

/**
 * Lets a horizontally scrolling row run to the screen's edges instead of stopping at the page padding.
 * Give the row the same [horizontal] amount as its contentPadding so the first item still lines up.
 */
fun Modifier.bleed(horizontal: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
