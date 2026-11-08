package com.sockjaw.clipsy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.sockjaw.clipsy.ClipsyApp
import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.Thumbnails
import com.sockjaw.clipsy.ui.components.FolderStyle
import com.sockjaw.clipsy.ui.components.fileKind
import com.sockjaw.clipsy.ui.components.snippetIcon
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronLeft
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val PAD = 12.dp
private val HEADER = 28.dp

/** Files get decoded ahead of time for this many items; a page never shows more than that. */
private const val PREVIEW_LIMIT = 24

/**
 * The home-screen Files rack: your media and documents as tiles with the file name underneath.
 * Tap one to copy it ready to paste into a chat, mail or notes app; page through the rest.
 */
class RackWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val first = loadRack(context, widgetId)
        provideContent {
            val state = currentState<Preferences>()
            val stamp = state[StampKey]
            // Re-read the settings and files whenever the stamp changes (see StampKey).
            val data by produceState(first, stamp) { if (stamp != null) value = loadRack(context, widgetId) }
            RackContent(data.items, data.config, data.folder, data.previews, state[CopiedKey], state[PageKey] ?: 0)
        }
    }
}

private class RackData(val items: List<Snippet>, val config: RackConfig, val folder: Folder?, val previews: Map<Long, Bitmap>)

private suspend fun loadRack(context: Context, widgetId: Int): RackData {
    val app = (context.applicationContext as ClipsyApp).container
    val config = WidgetConfigStore(context).rack(widgetId)
    val all = app.repository.snippets.first()
    val folder = app.repository.folders.first().firstOrNull { it.id == config.folderId }
    val items = WidgetData.files(all, config)
    return RackData(items, config, folder, loadPreviews(context, items, config))
}

class RackReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RackWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetConfigStore(context).remove(it) }
    }
}

private const val TILE = 48f
private const val GALLERY_TILE = 72f

/** Previews are decoded off the main thread, once, and handed to the widget as ready bitmaps. */
internal suspend fun loadPreviews(context: Context, items: List<Snippet>, config: RackConfig): Map<Long, Bitmap> {
    val density = context.resources.displayMetrics.density
    val tile = if (config.layout == RackLayout.GALLERY) GALLERY_TILE else TILE
    return withContext(Dispatchers.IO) {
        items.take(PREVIEW_LIMIT).mapNotNull { s ->
            val file = Thumbnails.ensure(context, s) ?: return@mapNotNull null
            WidgetBitmaps.preview(file, tile, 12f, density)?.let { s.id to it }
        }.toMap()
    }
}

@Composable
internal fun RackContent(items: List<Snippet>, config: RackConfig, folder: Folder?, previews: Map<Long, Bitmap>, copiedId: Long?, pageState: Int) {
    val colors = widgetColors(config.look, config.translucent)
    val size = LocalSize.current
    val innerW = (size.width - PAD * 2).value
    val bodyH = (size.height - PAD * 2 - HEADER - 4.dp).value

    val gallery = config.layout == RackLayout.GALLERY
    val tile = if (gallery) GALLERY_TILE else TILE
    val cellW = if (gallery) 88f else 64f
    val cellH = tile + 4f + 15f
    val cols = WidgetData.fit(innerW, cellW, 2f)
    val rows = if (config.layout == RackLayout.SHELF) 1 else WidgetData.fit(bodyH, cellH, 6f)
    val perPage = cols * rows
    val pages = WidgetData.pageCount(items.size, perPage)
    val page = pageState.coerceIn(0, pages - 1)

    Column(GlanceModifier.widgetCard(colors).padding(PAD)) {
        RackHeader(config, folder, items.size, page, pages, colors)
        Spacer(GlanceModifier.height(4.dp))
        if (items.isEmpty()) {
            RackEmpty(config, colors)
        } else {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = if (rows == 1) Alignment.CenterVertically else Alignment.Top) {
                WidgetData.page(items, perPage, page).chunked(cols).forEachIndexed { r, rowItems ->
                    if (r > 0) Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth().height(cellH.dp)) {
                        rowItems.forEach { s -> FileTile(s, config, colors, previews[s.id], tile, copiedId == s.id, GlanceModifier.defaultWeight()) }
                        repeat(cols - rowItems.size) { Spacer(GlanceModifier.defaultWeight()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RackHeader(config: RackConfig, folder: Folder?, count: Int, page: Int, pages: Int, colors: WidgetColors) {
    val context = LocalContext.current
    val title = when (config.source) {
        RackSource.ALL -> "Files"
        RackSource.MEDIA -> "Media"
        RackSource.DOCUMENTS -> "Documents"
        RackSource.FOLDER -> folder?.name ?: "Folder"
        RackSource.PICKED -> "Files"
    }
    val open = if (config.source == RackSource.FOLDER && folder != null) WidgetLinks.folder(context, folder.id) else WidgetLinks.library(context)
    Row(GlanceModifier.fillMaxWidth().height(HEADER), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(open)).semantics { contentDescription = "Open $title in Clipsy" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = if (config.source == RackSource.FOLDER && folder != null) FolderStyle.icon(folder.icon) else TablerIcons.Files
            val tint = if (config.source == RackSource.FOLDER && folder != null) folder.tintArgb() else WidgetBitmaps.ICON_TINT
            Image(ImageProvider(WidgetBitmaps.icon(context, icon, "rack:$title", 16f, tint)), contentDescription = null, modifier = GlanceModifier.size(16.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text(title, maxLines = 1, style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            if (count > 0) {
                Spacer(GlanceModifier.width(6.dp))
                Text("$count", maxLines = 1, style = TextStyle(color = colors.textSecondary, fontSize = 12.sp))
            }
        }
        if (pages > 1) {
            PagerButton(TablerIcons.ChevronLeft, "chevron-left", "Previous page", enabled = page > 0, target = page - 1, colors = colors)
            Text("${page + 1}/$pages", style = TextStyle(color = colors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center), modifier = GlanceModifier.width(32.dp))
            PagerButton(TablerIcons.ChevronRight, "chevron-right", "Next page", enabled = page < pages - 1, target = page + 1, colors = colors)
        }
    }
}

@Composable
private fun PagerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, key: String, label: String, enabled: Boolean, target: Int, colors: WidgetColors) {
    val context = LocalContext.current
    Box(
        GlanceModifier.size(28.dp).cornerRadius(14.dp).background(colors.tile)
            .let { if (enabled) it.clickable(actionRunCallback<RackPageCallback>(actionParametersOf(PageTarget to target))) else it }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) WidgetBitmaps.ICON_TINT else 0x66888888
        Image(ImageProvider(WidgetBitmaps.icon(context, icon, key, 16f, tint)), contentDescription = null, modifier = GlanceModifier.size(16.dp))
    }
}

@Composable
private fun RackEmpty(config: RackConfig, colors: WidgetColors) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().clickable(actionStartActivity(WidgetLinks.library(context))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (config.source) {
                RackSource.MEDIA -> "No media yet"
                RackSource.DOCUMENTS -> "No documents yet"
                RackSource.FOLDER -> "This folder has no files"
                RackSource.PICKED -> "No files picked"
                RackSource.ALL -> "No files yet"
            },
            style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Text("Add files in Clipsy to keep them handy", style = TextStyle(color = colors.textSecondary, fontSize = 12.sp))
    }
}

@Composable
private fun FileTile(s: Snippet, config: RackConfig, colors: WidgetColors, preview: Bitmap?, tile: Float, copied: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxHeight().cornerRadius(12.dp)
            .onTap(context, config.tap, s, CopyFileCallback::class.java)
            .semantics { contentDescription = "${config.tap.label} ${s.name}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(GlanceModifier.size(tile.dp).cornerRadius(12.dp).background(colors.tile), contentAlignment = Alignment.Center) {
            if (preview != null) {
                Image(ImageProvider(preview), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                // No preview (a ZIP, a spreadsheet…): the file's icon over its type, e.g. "XLSX".
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(WidgetBitmaps.icon(context, snippetIcon(s), "kind:${s.type}", 22f)), contentDescription = null, modifier = GlanceModifier.size(22.dp))
                    Text(fileKind(s.mimeType, s.name), maxLines = 1, style = TextStyle(color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                }
            }
            if (copied) {
                Box(GlanceModifier.fillMaxSize().cornerRadius(12.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(WidgetBitmaps.icon(context, TablerIcons.Check, "check", 24f, 0xFFFFFFFF.toInt())), contentDescription = null, modifier = GlanceModifier.size(24.dp))
                }
            }
        }
        Spacer(GlanceModifier.height(3.dp))
        Text(
            if (copied) "Copied" else s.name.substringBeforeLast('.', s.name),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
            style = TextStyle(color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}
