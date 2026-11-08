package com.sockjaw.clipsy.ui.settings

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.components.ValueRow
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.widget.RackReceiver
import com.sockjaw.clipsy.widget.SnippetsReceiver
import com.sockjaw.clipsy.widget.requestPinWidget
import compose.icons.TablerIcons
import compose.icons.tablericons.Files
import compose.icons.tablericons.LayoutGrid

/** Adds the home-screen widgets without leaving the app, where the launcher allows it. */
@Composable
fun WidgetsSection(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun add(receiver: Class<*>) {
        if (!requestPinWidget(context, receiver)) {
            container.toaster.show("Press and hold your home screen, tap Widgets, then find Clipsy.", ToastKind.Info)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Widgets")
        GroupedCard {
            ValueRow("Add Snippets widget", "", icon = TablerIcons.LayoutGrid, onClick = { add(SnippetsReceiver::class.java) })
            RowDivider(60.dp)
            ValueRow("Add Files rack", "", icon = TablerIcons.Files, onClick = { add(RackReceiver::class.java) })
        }
        Text(
            "Copy your top snippets, a folder, or your media and documents straight from the home screen.",
            Modifier.padding(horizontal = 4.dp),
            color = ClipsyColors.TextSecondary,
            fontSize = ClipsyType.Caption,
        )
    }
}
