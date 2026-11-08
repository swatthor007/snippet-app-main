package com.sockjaw.clipsy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.sockjaw.clipsy.ui.theme.LocalPalette
import androidx.compose.ui.graphics.vector.ImageVector
import com.sockjaw.clipsy.data.Folder
import compose.icons.TablerIcons
import compose.icons.tablericons.Bookmark
import compose.icons.tablericons.Briefcase
import compose.icons.tablericons.Code
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Folder as FolderIcon
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Home
import compose.icons.tablericons.Link
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MessageCircle
import compose.icons.tablericons.Phone
import compose.icons.tablericons.Photo
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Star
import compose.icons.tablericons.User
import compose.icons.tablericons.World

/** The colours and icons a folder can have. Colours are mid-tones that read on light and dark. */
object FolderStyle {
    val colors = listOf(
        Color(0xFF4F7CFF), // blue
        Color(0xFF14A8A0), // teal
        Color(0xFF2FA866), // green
        Color(0xFFD9A21B), // amber
        Color(0xFFE8772E), // orange
        Color(0xFFDC4B4B), // red
        Color(0xFFD4579C), // pink
        Color(0xFF8B62E0), // purple
        Color(0xFF7C8494), // graphite
    )

    val colorNames = listOf("Blue", "Teal", "Green", "Amber", "Orange", "Red", "Pink", "Purple", "Graphite")

    val icons: List<Pair<String, ImageVector>> = listOf(
        "folder" to TablerIcons.FolderIcon,
        "briefcase" to TablerIcons.Briefcase,
        "home" to TablerIcons.Home,
        "user" to TablerIcons.User,
        "heart" to TablerIcons.Heart,
        "star" to TablerIcons.Star,
        "bookmark" to TablerIcons.Bookmark,
        "mail" to TablerIcons.Mail,
        "phone" to TablerIcons.Phone,
        "chat" to TablerIcons.MessageCircle,
        "code" to TablerIcons.Code,
        "link" to TablerIcons.Link,
        "web" to TablerIcons.World,
        "doc" to TablerIcons.FileText,
        "photo" to TablerIcons.Photo,
        "cart" to TablerIcons.ShoppingCart,
    )

    /** The same hues, darkened until they clear 3:1 against the light card. Dark mode uses [colors] as they are. */
    val lightColors = listOf(
        Color(0xFF2F5FE0), // blue
        Color(0xFF0E8A83), // teal
        Color(0xFF1F8A4F), // green
        Color(0xFFA87400), // amber
        Color(0xFFC85A12), // orange
        Color(0xFFC23030), // red
        Color(0xFFB83A82), // pink
        Color(0xFF7048C8), // purple
        Color(0xFF5F6675), // graphite
    )

    fun color(index: Int, dark: Boolean = true): Color = (if (dark) colors else lightColors).let { it.getOrElse(index) { _ -> it[0] } }

    fun icon(key: String): ImageVector = icons.firstOrNull { it.first == key }?.second ?: TablerIcons.FolderIcon
}

/** The folder's colour, picked for the current light or dark theme. */
val Folder.tint: Color
    @Composable @ReadOnlyComposable get() = FolderStyle.color(color, LocalPalette.current.isDark)
val Folder.vector: ImageVector get() = FolderStyle.icon(icon)
