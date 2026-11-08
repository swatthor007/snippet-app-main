package com.sockjaw.clipsy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.R
import com.sockjaw.clipsy.data.ThemeMode

/**
 * Neutral graphite with one cobalt accent. Every colour on screen comes from here, in both
 * light and dark, so the accent reads the same everywhere and nothing is hard-coded.
 */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val separator: Color,
    val cardBorder: Color,
    val accent: Color,
    val onAccent: Color,
    val accentStrong: Color,
    val accentFaint: Color,
    val chartMid: Color,
    val chartLow: Color,
    val danger: Color,
    val dangerBg: Color,
    val navBar: Color,
    val sheet: Color,
    val handle: Color,
    val switchOff: Color,
    val track: Color,
    val toastBg: Color,
    val toastText: Color,
    val isDark: Boolean,
)

val LightPalette = Palette(
    background = Color(0xFFF2F3F5),
    card = Color(0xFFFCFCFD),
    cardAlt = Color(0xFFE9EBEF),
    text = Color(0xFF16181D),
    textSecondary = Color(0xFF5B606B),
    textTertiary = Color(0xFF737883),
    textMuted = Color(0xFF3D424C),
    separator = Color(0xFFE3E5E9),
    cardBorder = Color(0xFFE5E7EB),
    accent = Color(0xFF2548C9),
    onAccent = Color(0xFFF7F8FF),
    accentStrong = Color(0xFF1B369C),
    accentFaint = Color(0xFFE4E9FA),
    chartMid = Color(0xFF7F95E0),
    chartLow = Color(0xFFBCC8EF),
    danger = Color(0xFFB42318),
    dangerBg = Color(0xFFFBE8E6),
    navBar = Color(0xFFF8F8FA),
    sheet = Color(0xFFF7F8FA),
    handle = Color(0xFFC6C9D0),
    switchOff = Color(0xFFD3D6DC),
    track = Color(0xFFE6E8EC),
    toastBg = Color(0xFF1F2127),
    toastText = Color(0xFFF1F2F4),
    isDark = false,
)

val DarkPalette = Palette(
    background = Color(0xFF111215),
    card = Color(0xFF191A1E),
    cardAlt = Color(0xFF24252A),
    text = Color(0xFFECEDEF),
    textSecondary = Color(0xFF9EA2AB),
    textTertiary = Color(0xFF80848E),
    textMuted = Color(0xFFC9CCD2),
    separator = Color(0xFF26272C),
    cardBorder = Color(0xFF222328),
    accent = Color(0xFF7E9BFF),
    onAccent = Color(0xFF0C1740),
    accentStrong = Color(0xFFB9C8FF),
    accentFaint = Color(0xFF1D2542),
    chartMid = Color(0xFF4F66B8),
    chartLow = Color(0xFF2A3354),
    danger = Color(0xFFFF8F80),
    dangerBg = Color(0xFF3A1E1B),
    navBar = Color(0xFF15161A),
    sheet = Color(0xFF18191D),
    handle = Color(0xFF3B3D43),
    switchOff = Color(0xFF3B3D43),
    track = Color(0xFF1D1E22),
    toastBg = Color(0xFF2B2D32),
    toastText = Color(0xFFEDEEF0),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/**
 * The type scale. Four steps below the headings, so a size is a choice and not a number:
 * Micro (tab labels), Caption (meta, hints), Label (controls, secondary rows), Body (reading text, actions).
 */
object ClipsyType {
    val Micro = 11.sp
    val Caption = 12.sp
    val Label = 14.sp
    val Body = 16.sp
}

/** Read-only accessors so call sites stay `ClipsyColors.Accent`. Capture into a val before drawing in a DrawScope. */
object ClipsyColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
    val Card: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
    val CardAlt: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cardAlt
    val Text: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.text
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textTertiary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textMuted
    val Separator: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.separator
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cardBorder
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
    val AccentStrong: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentStrong
    val AccentFaint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentFaint
    val ChartMid: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chartMid
    val ChartLow: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chartLow
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger
    val DangerBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.dangerBg
    val NavBar: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.navBar
    val Sheet: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sheet
    val Handle: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.handle
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.switchOff
    val Track: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.track
    val ToastBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.toastBg
    val ToastText: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.toastText
}

private fun geist(weight: Int) = Font(
    R.font.geist,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sans = FontFamily(geist(400), geist(500), geist(600), geist(700))

val Mono = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.geist_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

/**
 * Hierarchy comes from weight and colour more than size. Tight tracking on the large sizes,
 * neutral on body text.
 */
private val typography = Typography(
    // Line heights are relative (em) so a one-off fontSize override keeps sensible spacing.
    displaySmall = TextStyle(fontFamily = Sans, fontSize = 30.sp, lineHeight = 1.2.em, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontSize = 19.sp, lineHeight = 1.26.em, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontSize = ClipsyType.Body, lineHeight = 1.36.em, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = ClipsyType.Body, lineHeight = 1.38.em, letterSpacing = (-0.1).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = ClipsyType.Label, lineHeight = 1.42.em),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = ClipsyType.Caption, lineHeight = 1.36.em),
    labelLarge = TextStyle(fontFamily = Sans, fontSize = ClipsyType.Label, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = ClipsyType.Micro, fontWeight = FontWeight.Medium),
)

private fun Palette.toScheme() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, background = background, onBackground = text,
        surface = card, onSurface = text, surfaceContainer = card, surfaceContainerHigh = cardAlt,
        surfaceContainerLow = sheet, onSurfaceVariant = textSecondary, outline = separator, error = danger,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, background = background, onBackground = text,
        surface = card, onSurface = text, surfaceContainer = card, surfaceContainerHigh = cardAlt,
        surfaceContainerLow = sheet, onSurfaceVariant = textSecondary, outline = separator, error = danger,
    )
}

/** Whether this choice means dark right now; [ThemeMode.SYSTEM] follows the phone. */
@Composable
@ReadOnlyComposable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Follows the system light/dark setting unless [dark] says otherwise. [fillBackground] is off for the overlay pill. */
@Composable
fun ClipsyTheme(
    dark: Boolean = isSystemInDarkTheme(),
    fillBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkPalette else LightPalette
    // A neutral press tint instead of Material's default grey ripple.
    val ripple = RippleConfiguration(
        color = palette.text,
        rippleAlpha = RippleAlpha(draggedAlpha = 0.06f, focusedAlpha = 0.08f, hoveredAlpha = 0.04f, pressedAlpha = 0.07f),
    )
    MaterialTheme(colorScheme = palette.toScheme(), typography = typography) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalReducedMotion provides rememberReducedMotion(),
            LocalContentColor provides palette.text,
            LocalRippleConfiguration provides ripple,
        ) {
            if (fillBackground) Surface(color = palette.background, contentColor = palette.text, content = content) else content()
        }
    }
}
