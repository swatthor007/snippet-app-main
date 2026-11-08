package com.sockjaw.clipsy.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sockjaw.clipsy.ClipsyApp
import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.ui.components.FolderStyle
import com.sockjaw.clipsy.ui.components.snippetIcon
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Plus
import kotlinx.coroutines.flow.first

private val PAD = 12.dp
private val HEADER = 28.dp

/**
 * The home-screen Snippets widget: a header (what it shows, and a + to add one) over your snippets
 * as a list, a grid or big buttons. Tapping one copies it, shares it or opens it, as chosen.
 */
class SnippetsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val first = loadSnippets(context, widgetId)
        provideContent {
            val state = currentState<Preferences>()
            val stamp = state[StampKey]
            // Re-read the settings and snippets whenever the stamp changes (see StampKey).
            val data by produceState(first, stamp) { if (stamp != null) value = loadSnippets(context, widgetId) }
            SnippetsContent(data.items, data.config, data.folder, state[CopiedKey])
        }
    }
}

private class SnippetsData(val items: List<Snippet>, val config: SnippetsConfig, val folder: Folder?)

private suspend fun loadSnippets(context: Context, widgetId: Int): SnippetsData {
    val app = (context.applicationContext as ClipsyApp).container
    val config = WidgetConfigStore(context).snippets(widgetId)
    val all = app.repository.snippets.first()
    val folder = app.repository.folders.first().firstOrNull { it.id == config.folderId }
    return SnippetsData(WidgetData.snippets(all, config), config, folder)
}

class SnippetsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SnippetsWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetConfigStore(context).remove(it) }
    }
}

/** A folder's colour as an ARGB int, for tinting its icon bitmap. */
internal fun Folder.tintArgb(): Int = FolderStyle.color(color).let {
    (0xFF shl 24) or ((it.red * 255).toInt() shl 16) or ((it.green * 255).toInt() shl 8) or (it.blue * 255).toInt()
}

/** The rounded card every widget sits on: the system's own corner radius where it has one. */
@Composable
internal fun GlanceModifier.widgetCard(colors: WidgetColors): GlanceModifier =
    this.fillMaxSize()
        .appWidgetBackground()
        .background(colors.card)
        .cornerRadius(if (Build.VERSION.SDK_INT >= 31) 24.dp else 20.dp)

@Composable
internal fun SnippetsContent(items: List<Snippet>, config: SnippetsConfig, folder: Folder?, copiedId: Long?) {
    val colors = widgetColors(config.look, config.translucent)
    val size = LocalSize.current
    val innerW = (size.width - PAD * 2).value
    val bodyH = (size.height - PAD * 2 - HEADER - 6.dp).value

    Column(GlanceModifier.widgetCard(colors).padding(PAD)) {
        Header(config, folder, colors)
        Spacer(GlanceModifier.height(6.dp))
        if (items.isEmpty()) {
            Empty(config, colors)
        } else {
            when (config.layout) {
                SnippetLayout.LIST -> {
                    val cols = if (innerW >= 230f) 2 else 1
                    val rows = WidgetData.fit(bodyH, 40f, 4f)
                    Grid(items, cols, rows, 40.dp) { s, mod -> ListTile(s, config, colors, copiedId == s.id, mod) }
                }
                SnippetLayout.GRID -> {
                    val cols = WidgetData.fit(innerW, 64f, 4f)
                    val rows = WidgetData.fit(bodyH, 62f, 4f)
                    Grid(items, cols, rows, 62.dp) { s, mod -> GridTile(s, config, colors, copiedId == s.id, mod) }
                }
                SnippetLayout.BUTTONS -> {
                    val cols = if (innerW >= 230f) 2 else 1
                    val rows = WidgetData.fit(bodyH, 44f, 6f)
                    Grid(items, cols, rows, 44.dp, gap = 6.dp) { s, mod -> ButtonTile(s, config, colors, copiedId == s.id, mod) }
                }
            }
        }
    }
}

/** Fills a [cols]×[rows] page row by row; the cells share the width evenly. */
@Composable
private fun Grid(
    items: List<Snippet>,
    cols: Int,
    rows: Int,
    rowHeight: Dp,
    gap: Dp = 4.dp,
    cell: @Composable (Snippet, GlanceModifier) -> Unit,
) {
    val page = items.take(cols * rows)
    Column(GlanceModifier.fillMaxWidth()) {
        page.chunked(cols).forEachIndexed { r, rowItems ->
            if (r > 0) Spacer(GlanceModifier.height(gap))
            Row(GlanceModifier.fillMaxWidth().height(rowHeight)) {
                rowItems.forEachIndexed { c, s ->
                    if (c > 0) Spacer(GlanceModifier.width(gap))
                    cell(s, GlanceModifier.defaultWeight())
                }
                // A short last row keeps its cells the same width as the rows above.
                repeat(cols - rowItems.size) {
                    Spacer(GlanceModifier.width(gap))
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun Header(config: SnippetsConfig, folder: Folder?, colors: WidgetColors) {
    val context = LocalContext.current
    val title = when (config.source) {
        SnippetSource.FOLDER -> folder?.name ?: "Folder"
        else -> config.source.label
    }
    Row(GlanceModifier.fillMaxWidth().height(HEADER), verticalAlignment = Alignment.CenterVertically) {
        val open = if (config.source == SnippetSource.FOLDER && folder != null) WidgetLinks.folder(context, folder.id) else WidgetLinks.library(context)
        Row(
            GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(open)).semantics { contentDescription = "Open $title in Clipsy" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (config.source == SnippetSource.FOLDER && folder != null) {
                Image(
                    ImageProvider(WidgetBitmaps.icon(context, FolderStyle.icon(folder.icon), "folder:${folder.icon}", 16f, folder.tintArgb())),
                    contentDescription = null, modifier = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(title, maxLines = 1, style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        Box(
            GlanceModifier.size(28.dp).cornerRadius(14.dp).background(colors.tileAccent)
                .clickable(actionStartActivity(WidgetLinks.newSnippet(context, folder?.id)))
                .semantics { contentDescription = "New snippet" },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(WidgetBitmaps.icon(context, TablerIcons.Plus, "plus", 16f)), contentDescription = null, modifier = GlanceModifier.size(16.dp))
        }
    }
}

@Composable
private fun Empty(config: SnippetsConfig, colors: WidgetColors) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().clickable(actionStartActivity(WidgetLinks.library(context))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (config.source) {
                SnippetSource.PINNED -> "Nothing pinned yet"
                SnippetSource.FOLDER -> "This folder has no text snippets"
                SnippetSource.PICKED -> "No snippets picked"
                else -> "No snippets yet"
            },
            style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Text("Tap to open Clipsy", style = TextStyle(color = colors.textSecondary, fontSize = 12.sp))
    }
}

@Composable
private fun IconTile(icon: ImageVector, key: String, sizeDp: Float, colors: WidgetColors, copied: Boolean) {
    val context = LocalContext.current
    Box(
        GlanceModifier.size(sizeDp.dp).cornerRadius((sizeDp / 4).dp).background(if (copied) colors.accent else colors.tileAccent),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = if (copied) WidgetBitmaps.icon(context, TablerIcons.Check, "check", sizeDp * 0.56f, 0xFFFFFFFF.toInt())
        else WidgetBitmaps.icon(context, icon, key, sizeDp * 0.56f)
        Image(ImageProvider(glyph), contentDescription = null, modifier = GlanceModifier.size((sizeDp * 0.56f).dp))
    }
}

@Composable
private fun ListTile(s: Snippet, config: SnippetsConfig, colors: WidgetColors, copied: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxHeight().cornerRadius(12.dp).background(colors.tile)
            .onTap(context, config.tap, s, CopySnippetCallback::class.java)
            .semantics { contentDescription = "${config.tap.label} ${s.name}" }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(snippetIcon(s), if (s.isLink) "link" else "text", 28f, colors, copied)
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(if (copied) "Copied" else s.name, maxLines = 1, style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            if (s.keyword != null) Text(s.keyword, maxLines = 1, style = TextStyle(color = colors.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun GridTile(s: Snippet, config: SnippetsConfig, colors: WidgetColors, copied: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxHeight().cornerRadius(12.dp)
            .onTap(context, config.tap, s, CopySnippetCallback::class.java)
            .semantics { contentDescription = "${config.tap.label} ${s.name}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(snippetIcon(s), if (s.isLink) "link" else "text", 36f, colors, copied)
        Spacer(GlanceModifier.height(4.dp))
        Text(if (copied) "Copied" else s.name, maxLines = 1, style = TextStyle(color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ButtonTile(s: Snippet, config: SnippetsConfig, colors: WidgetColors, copied: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxHeight().cornerRadius(14.dp).background(if (copied) colors.accent else colors.tileAccent)
            .onTap(context, config.tap, s, CopySnippetCallback::class.java)
            .semantics { contentDescription = "${config.tap.label} ${s.name}" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                if (copied) "Copied" else s.name, maxLines = 1,
                style = TextStyle(color = if (copied) colors.onAccent else colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            if (s.keyword != null && !copied) Text(s.keyword, maxLines = 1, style = TextStyle(color = colors.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
        }
    }
}
