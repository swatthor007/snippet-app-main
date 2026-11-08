package com.sockjaw.clipsy.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.net.toUri
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.sockjaw.clipsy.ClipsyApp
import com.sockjaw.clipsy.WidgetActionActivity
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.service.Share
import kotlinx.coroutines.delay

internal val ItemId = ActionParameters.Key<Long>("clipsy.item")
internal val PageTarget = ActionParameters.Key<Int>("clipsy.page")

/** Widget state: the item just copied (shows a check for a moment) and the rack's current page. */
internal val CopiedKey = longPreferencesKey("copied")
internal val PageKey = intPreferencesKey("page")

/**
 * Bumped whenever a widget's settings or the snippets behind it change. A widget that is already
 * showing doesn't run provideGlance again on update(): it only recomposes. So the data is loaded
 * inside the content, keyed on this stamp, and bumping it is what makes the widget re-read everything.
 */
internal val StampKey = longPreferencesKey("stamp")

private const val CHECK_MS = 1500L

/** Deep links into the app, and the little activity that opens the share sheet for a tapped tile. */
object WidgetLinks {
    private fun view(context: Context, uri: String) = Intent(Intent.ACTION_VIEW, uri.toUri())
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun open(context: Context, s: Snippet) = view(context, if (s.type.isFile) "clipsy://file/${s.id}" else "clipsy://snippet/${s.id}")
    fun library(context: Context) = view(context, "clipsy://library")
    fun folder(context: Context, id: Long) = view(context, "clipsy://folder/$id")
    fun newSnippet(context: Context, folderId: Long?) = view(context, "clipsy://new" + (folderId?.let { "?folder=$it" } ?: ""))

    /** Opens the share sheet for one snippet. A data URI makes each tile's intent distinct. */
    fun share(context: Context, s: Snippet) = Intent(context, WidgetActionActivity::class.java)
        .setData("clipsy-widget://share/${s.id}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
}

/** The click action for a tile, per the widget's "On tap" setting. */
internal fun tapAction(context: Context, tap: TapAction, s: Snippet, copy: Class<out ActionCallback>): Action = when (tap) {
    TapAction.COPY -> actionRunCallback(copy, actionParametersOf(ItemId to s.id))
    TapAction.SHARE -> actionStartActivity(WidgetLinks.share(context, s))
    TapAction.OPEN -> actionStartActivity(WidgetLinks.open(context, s))
}

internal fun GlanceModifier.onTap(context: Context, tap: TapAction, s: Snippet, copy: Class<out ActionCallback>) =
    clickable(tapAction(context, tap, s, copy))

private fun container(context: Context) = (context.applicationContext as ClipsyApp).container

/** Copies the tapped snippet (text or file), then flashes a check on its tile. */
private suspend fun copyAndFlash(context: Context, glanceId: GlanceId, widget: GlanceAppWidget, id: Long) {
    val app = container(context)
    val snippet = app.repository.get(id) ?: return
    if (snippet.needsUnlock) return
    val result = Share.copy(context, listOf(snippet))
    if (result.copied == 0) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Couldn’t copy that", Toast.LENGTH_SHORT).show() }
        return
    }
    app.repository.markCopied(listOf(id))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < 33) Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Copied “${snippet.name}”", Toast.LENGTH_SHORT).show() }
    updateAppWidgetState(context, glanceId) { it[CopiedKey] = id }
    widget.update(context, glanceId)
    delay(CHECK_MS)
    updateAppWidgetState(context, glanceId) { it.remove(CopiedKey) }
    widget.update(context, glanceId)
}

class CopySnippetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        copyAndFlash(context, glanceId, SnippetsWidget(), parameters[ItemId] ?: return)
    }
}

class CopyFileCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        copyAndFlash(context, glanceId, RackWidget(), parameters[ItemId] ?: return)
    }
}

class RackPageCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val page = parameters[PageTarget] ?: return
        updateAppWidgetState(context, glanceId) { it[PageKey] = page }
        RackWidget().update(context, glanceId)
    }
}

/** Makes one placed widget re-read its settings and snippets and redraw. */
internal suspend fun GlanceAppWidget.reload(context: Context, glanceId: GlanceId) {
    updateAppWidgetState(context, glanceId) { it[StampKey] = System.nanoTime() }
    update(context, glanceId)
}

/** Redraws every placed widget, e.g. after snippets change. Cheap when none are placed. */
suspend fun refreshWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    suspend fun <W : GlanceAppWidget> reloadAll(widget: W) {
        runCatching { manager.getGlanceIds(widget.javaClass).forEach { widget.reload(context, it) } }
    }
    reloadAll(SnippetsWidget())
    reloadAll(RackWidget())
}
