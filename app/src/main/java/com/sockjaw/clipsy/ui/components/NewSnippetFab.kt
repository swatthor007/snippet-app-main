package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.LocalPalette
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * New snippet, bottom right, where a thumb reaches without shifting grip. 56dp tall; shows its
 * label at the top of a list and tucks down to just the + once you scroll.
 */
@Composable
fun NewSnippetFab(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalPalette.current.isDark
    Row(
        modifier
            .height(56.dp)
            .widthIn(min = 56.dp)
            .shadow(if (dark) 0.dp else 8.dp, CircleShape, ambientColor = ClipsyColors.Accent, spotColor = ClipsyColors.Accent)
            .clip(CircleShape)
            .background(ClipsyColors.Accent)
            .pressable(pressedScale = 0.94f, onClick = onClick)
            .semantics { contentDescription = "New snippet" }
            .padding(start = 16.dp, end = if (expanded) 20.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(TablerIcons.Plus, null, tint = ClipsyColors.OnAccent, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            expanded,
            enter = expandHorizontally(Motion.enter(Motion.BASE)) + fadeIn(Motion.enter(Motion.BASE)),
            exit = shrinkHorizontally(Motion.exit()) + fadeOut(Motion.exit(Motion.QUICK)),
        ) {
            Text("New snippet", Modifier.padding(start = 10.dp), color = ClipsyColors.OnAccent, fontSize = ClipsyType.Body, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Reports whether the list is scrolled to the top (the FAB shows its label only there). */
@Composable
fun ReportAtTop(state: androidx.compose.foundation.lazy.grid.LazyGridState, onChange: (Boolean) -> Unit) {
    val callback by rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 24 }
            .distinctUntilChanged()
            .collect { callback(it) }
    }
}

@Composable
fun ReportAtTop(state: LazyListState, onChange: (Boolean) -> Unit) {
    val callback by rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 24 }
            .distinctUntilChanged()
            .collect { callback(it) }
    }
}
