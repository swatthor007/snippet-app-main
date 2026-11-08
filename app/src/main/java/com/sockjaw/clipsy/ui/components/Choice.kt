package com.sockjaw.clipsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.sockjaw.clipsy.ui.theme.ClipsyColors

/** A value row that opens an [OptionSheet] of [options]. */
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    icon: ImageVector? = null,
    sheetSubtitle: String? = null,
    optionDetail: (T) -> String? = { null },
) {
    var open by remember { mutableStateOf(false) }
    ValueRow(label, optionLabel(selected), icon = icon, onClick = { open = true })
    if (open) {
        OptionSheet(label, options, selected, optionLabel, onPick, onDismiss = { open = false }, subtitle = sheetSubtitle, optionDetail = optionDetail)
    }
}

/** Black, glowing, inset-aware page root shared by every screen. */
@Composable
fun Modifier.screenRoot(): Modifier = this
    .fillMaxSize()
    .background(ClipsyColors.Background)
    .statusBarsPadding()
