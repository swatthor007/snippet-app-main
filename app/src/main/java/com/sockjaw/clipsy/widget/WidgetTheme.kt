package com.sockjaw.clipsy.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.unit.ColorProvider
import com.sockjaw.clipsy.ui.theme.DarkPalette
import com.sockjaw.clipsy.ui.theme.LightPalette
import com.sockjaw.clipsy.ui.theme.Palette

/** The widget's colours, taken from the app's own light and dark palettes so both look the same. */
class WidgetColors(
    val card: androidx.glance.unit.ColorProvider,
    val tile: androidx.glance.unit.ColorProvider,
    val tileAccent: androidx.glance.unit.ColorProvider,
    val text: androidx.glance.unit.ColorProvider,
    val textSecondary: androidx.glance.unit.ColorProvider,
    val accent: androidx.glance.unit.ColorProvider,
    val onAccent: androidx.glance.unit.ColorProvider,
    val border: androidx.glance.unit.ColorProvider,
)

private fun Color.fade(alpha: Float) = copy(alpha = alpha)

fun widgetColors(look: WidgetLook, translucent: Boolean): WidgetColors {
    val a = if (translucent) 0.82f else 1f
    fun pair(day: (Palette) -> Color, alpha: Float = 1f) = when (look) {
        WidgetLook.AUTO -> dayNight(day = day(LightPalette).fade(alpha), night = day(DarkPalette).fade(alpha))
        WidgetLook.LIGHT -> ColorProvider(day(LightPalette).fade(alpha))
        WidgetLook.DARK -> ColorProvider(day(DarkPalette).fade(alpha))
    }
    return WidgetColors(
        card = pair({ it.card }, a),
        tile = pair({ it.cardAlt }, if (translucent) 0.9f else 1f),
        tileAccent = pair({ it.accentFaint }),
        text = pair({ it.text }),
        textSecondary = pair({ it.textSecondary }),
        accent = pair({ it.accent }),
        onAccent = pair({ it.onAccent }),
        border = pair({ it.cardBorder }),
    )
}
