package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.Adjustments
import compose.icons.tablericons.Home
import compose.icons.tablericons.Stack2
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", TablerIcons.Home),
    Library("library", "Library", TablerIcons.Stack2),
    Settings("settings", "Settings", TablerIcons.Adjustments),
}

val TabBarHeight = 64.dp

@Composable
fun BottomTabBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ClipsyColors.NavBar)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ClipsyColors.Separator))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { tab -> TabItem(tab, tab == current) { onSelect(tab) } }
        }
    }
}

/**
 * No ripple here: the indicator behind the icon is the feedback. It shows faintly while a
 * finger (or hover, or keyboard focus) is on an unselected tab, and settles fully on selection.
 */
@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val engaged = pressed || hovered || focused
    val tint by animateColorAsState(
        when {
            selected -> ClipsyColors.Accent
            engaged -> ClipsyColors.Text
            else -> ClipsyColors.TextSecondary
        },
        Motion.standard(Motion.QUICK),
        label = "tabTint",
    )
    val indicator by animateFloatAsState(
        when {
            selected -> 1f
            engaged -> 0.55f
            else -> 0f
        },
        if (pressed) Motion.standard(Motion.QUICK) else Motion.settle(),
        label = "tabIndicator",
    )
    val indicatorColor = if (selected) ClipsyColors.AccentFaint else ClipsyColors.CardAlt
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, if (pressed) Motion.standard(90) else Motion.pop(), label = "tabPress")
    Column(
        Modifier
            .widthIn(min = 88.dp)
            .clickable(interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .graphicsLayer { scaleX = 0.6f + 0.4f * indicator; alpha = indicator }
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(tab.label, color = tint, fontSize = ClipsyType.Micro, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, bold: Boolean = false, enabled: Boolean = true) {
    val color by animateColorAsState(if (enabled) ClipsyColors.Accent else ClipsyColors.TextTertiary, Motion.standard(), label = "action")
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label, color = color, fontSize = ClipsyType.Body, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** The tab bar slides away on pushed screens and returns on tabs. */
@Composable
fun AnimatedTabBar(current: Tab?, onSelect: (Tab) -> Unit) {
    var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(current ?: Tab.Home) }
    if (current != null) last = current
    androidx.compose.animation.AnimatedVisibility(
        visible = current != null,
        enter = androidx.compose.animation.slideInVertically(Motion.enter()) { it } + androidx.compose.animation.fadeIn(Motion.enter(Motion.BASE)),
        exit = androidx.compose.animation.slideOutVertically(Motion.exit(Motion.BASE)) { it } + androidx.compose.animation.fadeOut(Motion.exit(Motion.BASE)),
    ) {
        BottomTabBar(last, onSelect)
    }
}
