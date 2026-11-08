package com.sockjaw.clipsy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.ClipsyApp
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.ui.components.ChoiceRow
import com.sockjaw.clipsy.ui.components.ClipsySheet
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.MoveToFolderSheet
import com.sockjaw.clipsy.ui.components.PrimaryButton
import com.sockjaw.clipsy.ui.components.RoundIconButton
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.SelectionMark
import com.sockjaw.clipsy.ui.components.SheetTitle
import com.sockjaw.clipsy.ui.components.SnippetLeading
import com.sockjaw.clipsy.ui.components.SwitchRow
import com.sockjaw.clipsy.ui.components.ValueRow
import com.sockjaw.clipsy.ui.components.snippetMeta
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.ClipsyTheme
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.isDark
import compose.icons.TablerIcons
import compose.icons.tablericons.Eye
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Hash
import compose.icons.tablericons.Click
import compose.icons.tablericons.SortDescending
import compose.icons.tablericons.Stack2
import compose.icons.tablericons.X
import kotlinx.coroutines.launch

/**
 * Where a widget is set up: what it shows, how it's laid out, what a tap does. A live preview sits
 * on top and follows every change. Opened by the launcher when a widget is added (before Android 12),
 * and from the widget's own "Reconfigure" afterwards.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Backing out must leave the widget unplaced, so the answer starts as "cancelled".
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        val isRack = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className == RackReceiver::class.java.name
        val container = (application as ClipsyApp).container
        val store = WidgetConfigStore(this)
        val reconfiguring = if (isRack) store.hasRack(widgetId) else store.hasSnippets(widgetId)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark()
            ClipsyTheme(dark = dark) {
                if (isRack) RackSetup(container, store, reconfiguring, ::finishWithOk, ::finish)
                else SnippetsSetup(container, store, reconfiguring, ::finishWithOk, ::finish)
            }
        }
    }

    private fun finishWithOk() {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    // The widget's id isn't a parameter of the setup screens, which only deal in configs.
    @Composable
    private fun SnippetsSetup(container: AppContainer, store: WidgetConfigStore, reconfiguring: Boolean, done: () -> Unit, cancel: () -> Unit) {
        var config by remember { mutableStateOf(store.snippets(widgetId)) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val all by container.repository.snippets.collectAsStateWithLifecycle(emptyList())
        val folders by container.repository.folders.collectAsStateWithLifecycle(emptyList())
        val context = LocalContext.current
        val text = remember(all) { all.filter { it.type == SnippetType.TEXT && !it.locked } }
        var picking by remember { mutableStateOf(false) }
        var folderSheet by remember { mutableStateOf(false) }

        SetupScaffold(
            title = "Snippets widget",
            confirmLabel = "Save",
            onConfirm = {
                scope.launch {
                    store.save(widgetId, config)
                    runCatching { SnippetsWidget().reload(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) }.onFailure { android.util.Log.w("Clipsy", "Couldn’t refresh the widget", it) }
                    done()
                }
            },
            onCancel = cancel,
            preview = { size ->
                WidgetPreview(size, listOf(config, all, folders)) {
                    SnippetsContent(WidgetData.snippets(all, config), config, folders.firstOrNull { it.id == config.folderId }, copiedId = null)
                }
            },
        ) {
            Section("Show") {
                ChoiceRow(
                    "Source", SnippetSource.entries, config.source, { it.label },
                    { config = config.copy(source = it) },
                    icon = TablerIcons.Stack2, optionDetail = { it.detail },
                )
                if (config.source == SnippetSource.FOLDER) {
                    RowDivider(60.dp)
                    ValueRow("Folder", folders.firstOrNull { it.id == config.folderId }?.name ?: "Choose…", icon = TablerIcons.Folder, onClick = { folderSheet = true })
                }
                if (config.source == SnippetSource.PICKED) {
                    RowDivider(60.dp)
                    ValueRow("Snippets", "${config.pickedIds.size} of $MAX_PICKED", icon = TablerIcons.Hash, onClick = { picking = true })
                }
            }
            Section("Layout") {
                LayoutChoice(SnippetLayout.entries, config.layout, { it.label }, { config = config.copy(layout = it) }) { kind, selected -> SnippetLayoutArt(kind, selected) }
            }
            Section("When tapped") {
                ChoiceRow("Action", TapAction.entries, config.tap, { it.label }, { config = config.copy(tap = it) }, icon = TablerIcons.Click, optionDetail = { it.detail })
            }
            Section("Look") {
                ChoiceRow("Theme", WidgetLook.entries, config.look, { it.label }, { config = config.copy(look = it) }, icon = TablerIcons.Eye)
                RowDivider(60.dp)
                SwitchRow("See-through background", config.translucent, { config = config.copy(translucent = it) })
            }
            Note("Locked snippets never appear on a widget.")
        }

        if (folderSheet) MoveToFolderSheet(container.repository, folders, config.folderId, onPick = { if (it != null) config = config.copy(folderId = it) }, onDismiss = { folderSheet = false }, title = "Show a folder")
        if (picking) PickSheet("Pick snippets", text, config.pickedIds, { config = config.copy(pickedIds = it) }) { picking = false }
    }

    @Composable
    private fun RackSetup(container: AppContainer, store: WidgetConfigStore, reconfiguring: Boolean, done: () -> Unit, cancel: () -> Unit) {
        var config by remember { mutableStateOf(store.rack(widgetId)) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val all by container.repository.snippets.collectAsStateWithLifecycle(emptyList())
        val folders by container.repository.folders.collectAsStateWithLifecycle(emptyList())
        val context = LocalContext.current
        val files = remember(all) { all.filter { it.type.isFile } }
        var previews by remember { mutableStateOf<Map<Long, android.graphics.Bitmap>>(emptyMap()) }
        var picking by remember { mutableStateOf(false) }
        var folderSheet by remember { mutableStateOf(false) }
        LaunchedEffect(all, config.source, config.folderId, config.pickedIds, config.layout, config.sort) {
            previews = loadPreviews(context, WidgetData.files(all, config), config)
        }

        SetupScaffold(
            title = "Files rack",
            confirmLabel = "Save",
            onConfirm = {
                scope.launch {
                    store.save(widgetId, config)
                    runCatching { RackWidget().reload(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) }.onFailure { android.util.Log.w("Clipsy", "Couldn’t refresh the widget", it) }
                    done()
                }
            },
            onCancel = cancel,
            preview = { size ->
                WidgetPreview(size, listOf(config, all, folders, previews)) {
                    RackContent(WidgetData.files(all, config), config, folders.firstOrNull { it.id == config.folderId }, previews, copiedId = null, pageState = 0)
                }
            },
        ) {
            Section("Show") {
                ChoiceRow("Source", RackSource.entries, config.source, { it.label }, { config = config.copy(source = it) }, icon = TablerIcons.Stack2, optionDetail = { it.detail })
                if (config.source == RackSource.FOLDER) {
                    RowDivider(60.dp)
                    ValueRow("Folder", folders.firstOrNull { it.id == config.folderId }?.name ?: "Choose…", icon = TablerIcons.Folder, onClick = { folderSheet = true })
                }
                if (config.source == RackSource.PICKED) {
                    RowDivider(60.dp)
                    ValueRow("Files", "${config.pickedIds.size} of $MAX_PICKED", icon = TablerIcons.Hash, onClick = { picking = true })
                } else {
                    RowDivider(60.dp)
                    ChoiceRow("Order", RackSort.entries, config.sort, { it.label }, { config = config.copy(sort = it) }, icon = TablerIcons.SortDescending)
                }
            }
            Section("Layout") {
                LayoutChoice(RackLayout.entries, config.layout, { it.label }, { config = config.copy(layout = it) }) { kind, selected -> RackLayoutArt(kind, selected) }
            }
            Section("When tapped") {
                ChoiceRow("Action", TapAction.entries, config.tap, { it.label }, { config = config.copy(tap = it) }, icon = TablerIcons.Click, optionDetail = { it.detail })
            }
            Section("Look") {
                ChoiceRow("Theme", WidgetLook.entries, config.look, { it.label }, { config = config.copy(look = it) }, icon = TablerIcons.Eye)
                RowDivider(60.dp)
                SwitchRow("See-through background", config.translucent, { config = config.copy(translucent = it) })
            }
            Note("Tap a file to copy it, then paste it into a chat, mail or notes app. Use ‹ › to page through more files.")
        }

        if (folderSheet) MoveToFolderSheet(container.repository, folders, config.folderId, onPick = { if (it != null) config = config.copy(folderId = it) }, onDismiss = { folderSheet = false }, title = "Show a folder")
        if (picking) PickSheet("Pick files", files, config.pickedIds, { config = config.copy(pickedIds = it) }) { picking = false }
    }

    /** The widget's current size on the home screen, for a preview that matches it. */
    private fun currentSize(): DpSize {
        val o = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: 110
        return DpSize(w.dp, h.dp)
    }

    @Composable
    private fun SetupScaffold(
        title: String,
        confirmLabel: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        preview: @Composable (DpSize) -> Unit,
        content: @Composable () -> Unit,
    ) {
        BackHandler(onBack = onCancel)
        Column(Modifier.fillMaxSize().background(ClipsyColors.Background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundIconButton(TablerIcons.X, "Close", onCancel)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            }
            // The preview stays put while the options scroll underneath.
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp)).background(ClipsyColors.CardAlt).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { preview(currentSize()) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) { content() }
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton(confirmLabel, onConfirm)
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        GroupedCard { content() }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.padding(horizontal = 4.dp), color = ClipsyColors.TextSecondary, fontSize = 12.sp)
}

/** Renders the real widget content to a RemoteViews and shows it, so the preview is the widget itself. Taps are swallowed. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
private fun WidgetPreview(size: DpSize, key: Any, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
    LaunchedEffect(key, size) {
        views = runCatching { GlanceRemoteViews().compose(context, size, content = content).remoteViews }.getOrNull()
    }
    Box(Modifier.size(size.width, size.height)) {
        AndroidView(
            factory = { FrameLayout(it) },
            update = { frame ->
                val rv = views ?: return@AndroidView
                frame.removeAllViews()
                frame.addView(rv.apply(frame.context, frame), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                frame.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Widget preview" },
        )
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}

/** Three illustrated cards side by side, one per layout. */
@Composable
private fun <T> LayoutChoice(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, art: @Composable (T, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            val on = option == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) ClipsyColors.AccentFaint else ClipsyColors.CardAlt)
                    .border(if (on) 2.dp else 1.dp, if (on) ClipsyColors.Accent else ClipsyColors.CardBorder, RoundedCornerShape(14.dp))
                    .pressable(role = Role.RadioButton, pressedScale = 0.96f) { onPick(option) }
                    .semantics { this.selected = on }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(54.dp)) { art(option, on) }
                Text(label(option), fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) ClipsyColors.Accent else ClipsyColors.Text)
            }
        }
    }
}

@Composable
private fun SnippetLayoutArt(kind: SnippetLayout, on: Boolean) {
    val fill = if (on) ClipsyColors.Accent else ClipsyColors.Handle
    val soft = if (on) ClipsyColors.Accent.copy(alpha = 0.35f) else ClipsyColors.Separator
    Canvas(Modifier.fillMaxSize()) {
        val r = CornerRadius(4.dp.toPx())
        when (kind) {
            SnippetLayout.LIST -> repeat(2) { c -> repeat(2) { row ->
                val w = (size.width - 6.dp.toPx()) / 2
                val x = c * (w + 6.dp.toPx()); val y = row * 28.dp.toPx()
                drawRoundRect(soft, Offset(x, y), Size(w, 24.dp.toPx()), r)
                drawRoundRect(fill, Offset(x + 4.dp.toPx(), y + 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                drawRoundRect(fill, Offset(x + 20.dp.toPx(), y + 8.dp.toPx()), Size(w - 28.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            } }
            SnippetLayout.GRID -> repeat(4) { c ->
                val w = (size.width - 18.dp.toPx()) / 4
                val x = c * (w + 6.dp.toPx())
                drawRoundRect(fill, Offset(x + w / 2 - 8.dp.toPx(), 4.dp.toPx()), Size(16.dp.toPx(), 16.dp.toPx()), CornerRadius(4.dp.toPx()))
                drawRoundRect(soft, Offset(x, 28.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                drawRoundRect(soft, Offset(x, 40.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            SnippetLayout.BUTTONS -> repeat(2) { c -> repeat(2) { row ->
                val w = (size.width - 6.dp.toPx()) / 2
                drawRoundRect(if (row == 0) fill else soft, Offset(c * (w + 6.dp.toPx()), row * 28.dp.toPx()), Size(w, 24.dp.toPx()), CornerRadius(8.dp.toPx()))
            } }
        }
    }
}

@Composable
private fun RackLayoutArt(kind: RackLayout, on: Boolean) {
    val fill = if (on) ClipsyColors.Accent else ClipsyColors.Handle
    val soft = if (on) ClipsyColors.Accent.copy(alpha = 0.35f) else ClipsyColors.Separator
    Canvas(Modifier.fillMaxSize()) {
        fun tile(x: Float, y: Float, s: Float) {
            drawRoundRect(fill, Offset(x, y), Size(s, s), CornerRadius(4.dp.toPx()))
            drawRoundRect(soft, Offset(x, y + s + 3.dp.toPx()), Size(s, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        when (kind) {
            RackLayout.SHELF -> repeat(5) { tile(it * (size.width / 5) + 2.dp.toPx(), 12.dp.toPx(), 14.dp.toPx()) }
            RackLayout.GRID -> repeat(2) { r -> repeat(5) { tile(it * (size.width / 5) + 2.dp.toPx(), r * 28.dp.toPx(), 12.dp.toPx()) } }
            RackLayout.GALLERY -> repeat(3) { tile(it * (size.width / 3) + 4.dp.toPx(), 4.dp.toPx(), 26.dp.toPx()) }
        }
    }
}

/** Pick up to [MAX_PICKED] items; the order you tap is the order they appear. */
@Composable
private fun PickSheet(title: String, items: List<Snippet>, picked: List<Long>, onChange: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    ClipsySheet(onDismiss) {
        SheetTitle(title, "Up to $MAX_PICKED. They appear in the order you tap them.")
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(items, key = { it.id }) { s ->
                val on = s.id in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pressable(role = Role.Checkbox, pressedScale = 0.985f) {
                            onChange(if (on) picked - s.id else if (picked.size < MAX_PICKED) picked + s.id else picked)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SelectionMark(on)
                    SnippetLeading(s, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(snippetMeta(s), color = ClipsyColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Starts the Settings → Widgets "add to home screen" prompt for [receiver], where the launcher supports it. */
fun requestPinWidget(context: Context, receiver: Class<*>): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(android.content.ComponentName(context, receiver), null, null)
}
