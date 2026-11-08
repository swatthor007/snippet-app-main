package com.sockjaw.clipsy.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.util.Log
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.sockjaw.clipsy.data.Thumbnails
import kotlinx.coroutines.withContext
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.provider.Settings
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.ClipsyApp
import com.sockjaw.clipsy.UnlockActivity
import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.FileMode
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.ui.components.fileKind
import com.sockjaw.clipsy.ui.components.snippetIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches text fields for snippet keywords and replaces them. Password fields, Clipsy itself
 * and excluded apps are skipped; nothing leaves the device.
 */
private const val IDLE_HIDE_MS = 8_000L

/** How long a sign-in screen can take before its answer is ignored. */
private const val UNLOCK_PATIENCE_MS = 60_000L

class ExpansionService : AccessibilityService() {
    private val container get() = (application as ClipsyApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var byKeyword: Map<String, Snippet> = emptyMap()
    private var overlay: SuggestionOverlay? = null
    private var hideJob: Job? = null

    /** Our own SET_TEXT fires a text-changed event; ignore it. */
    private var suppressUntil = 0L

    /** What the pill would insert if tapped. */
    private var pending: Pair<AccessibilityNodeInfo, Match>? = null

    /** The keyword occurrence the user dismissed; it isn't offered again until they type a new one. */
    private var dismissed: Occurrence? = null
    private var idleJob: Job? = null

    /** A locked keyword waiting on the sign-in screen. */
    private data class AwaitingUnlock(val requestId: Long, val node: AccessibilityNodeInfo, val match: Match, val startedAt: Long)
    private var awaiting: AwaitingUnlock? = null
    private var nextRequest = 1L

    private data class Occurrence(val pkg: String?, val start: Int, val snippetId: Long)

    /** Decoded pill previews by snippet id; small (256px), so a plain map is fine. */
    private val thumbs = HashMap<Long, ImageBitmap>()

    override fun onServiceConnected() {
        overlay = SuggestionOverlay(this, onTap = ::onPillTapped, onDismiss = ::onPillDismissed)
        scope.launch {
            container.visibleSnippets.collect { list ->
                byKeyword = list.mapNotNull { s -> s.keyword?.let { it.lowercase() to s } }.toMap()
            }
        }
        scope.launch { container.unlockResults.collect(::onUnlockResult) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> onTextChanged(event)
            // Key presses on the keyboard send their own clicks and focus changes; ignore them.
            else -> if (pending != null && !isKeyboard(event) && shouldHideFor(event)) hidePill()
        }
    }

    /** Anything that means the user has moved on from the keyword they typed. */
    private fun shouldHideFor(event: AccessibilityEvent): Boolean = when (event.eventType) {
        // Focus moved to a different view.
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> event.source != pending?.first
        // A tap or scroll in the app: the field may have moved or the user is doing something else.
        AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SCROLLED -> event.packageName?.toString() != packageName
        // Caret moved away from the keyword (typing the trigger key keeps it within one character).
        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> pending?.second?.let { m ->
            event.source == pending?.first && event.toIndex !in m.start..(m.keywordEnd + 1)
        } ?: false
        // Another app or screen came up.
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
            event.packageName?.toString().let { it != null && it != pending?.first?.packageName?.toString() && it != packageName }
        // Keyboard closed.
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        else -> false
    }

    private fun isKeyboard(event: AccessibilityEvent): Boolean {
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val cls = event.className?.toString().orEmpty()
        return event.packageName?.toString() == ime || cls.contains("SoftInputWindow") || cls.contains("InputMethod")
    }

    override fun onInterrupt() = hidePill()

    override fun onDestroy() {
        overlay?.destroy()
        scope.cancel()
        super.onDestroy()
    }

    private fun onTextChanged(event: AccessibilityEvent) {
        if (SystemClock.uptimeMillis() < suppressUntil) return
        val pkg = event.packageName?.toString() ?: return
        val cfg = container.settings.settings.value
        if (pkg == packageName || pkg in cfg.excludedApps || event.isPassword) return hidePill()
        val node = event.source ?: return
        if (!node.isEditable || node.isPassword) return hidePill()
        if (Build.VERSION.SDK_INT >= 26 && node.isShowingHintText) return hidePill()
        val text = node.text?.toString() ?: return hidePill()
        val cursor = node.textSelectionEnd.let { if (it < 0) text.length else it }

        when (val d = Expander.detect(text, cursor, cfg.keywordPrefix) { byKeyword[it] }) {
            is Detection.Expand -> {
                // Dismissed from the pill: the user said no to this one, so the trigger key types normally.
                if (Occurrence(pkg, d.match.start, d.match.snippet.id) == dismissed) {
                    dismissed = null
                    hidePill()
                } else if (needsSignIn(d.match.snippet)) {
                    // A locked snippet never types itself: offer the lock pill and wait for a tap.
                    showPill(node, d.match)
                } else {
                    expand(node, text, d.match)
                }
            }
            is Detection.Suggest -> {
                val here = Occurrence(pkg, d.match.start, d.match.snippet.id)
                // A locked keyword always gets its pill: it is the only way to unlock it from here.
                if ((cfg.suggestionPill || needsSignIn(d.match.snippet)) && here != dismissed) showPill(node, d.match) else hidePill()
            }
            Detection.None -> {
                dismissed = null
                hidePill()
            }
        }
    }

    private fun showPill(node: AccessibilityNodeInfo, match: Match) {
        hideJob?.cancel()
        pending = node to match
        val s = match.snippet
        val key = when (s.expandAfter) {
            ExpandAfter.SPACE -> "space"
            ExpandAfter.ENTER -> "enter"
            ExpandAfter.INSTANT -> null
        }
        val signIn = needsSignIn(s)
        val verb = when {
            signIn -> "unlock"
            s.type == SnippetType.TEXT -> "insert"
            s.fileMode == FileMode.PASTE -> "attach"
            s.fileMode == FileMode.LINK -> "insert link"
            else -> "share"
        }
        val rect = Rect().also(node::getBoundsInScreen)
        val pill = PillUi(
            s.id, s.name, snippetIcon(s), if (signIn) null else key, verb, above = true, thumb = thumbs[s.id],
            card = s.type.isFile,
            kind = if (s.type.isFile) fileKind(s.mimeType, s.name) else null,
            preview = if (s.type.isFile || s.locked) null else s.content.replace(Regex("\\s+"), " ").trim().take(160).ifEmpty { null },
            link = s.isLink,
        )
        overlay?.show(pill, rect)
        // First time for this snippet: show the pill now, then drop the preview in once it's decoded.
        if (pill.thumb == null && Thumbnails.canPreview(s)) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    Thumbnails.ensure(this@ExpansionService, s)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
                } ?: return@launch
                thumbs[s.id] = bitmap
                if (pending?.second?.snippet?.id == s.id) overlay?.show(pill.copy(thumb = bitmap), rect)
            }
        }
        // An untouched suggestion steps aside on its own rather than breathing forever.
        idleJob?.cancel()
        idleJob = scope.launch { delay(IDLE_HIDE_MS); hidePill() }
    }

    private fun hidePill() {
        pending = null
        hideJob?.cancel()
        idleJob?.cancel()
        overlay?.hide()
    }

    private fun onPillDismissed() {
        pending?.let { (node, m) -> dismissed = Occurrence(node.packageName?.toString(), m.start, m.snippet.id) }
        hidePill()
    }

    private fun onPillTapped() {
        val (node, match) = pending ?: return hidePill()
        idleJob?.cancel()
        val text = if (node.refresh()) node.text?.toString() else null
        val keyword = match.snippet.keyword
        // Re-check: the field may have changed or gone since the pill appeared.
        val still = text != null && keyword != null && text.length >= match.keywordEnd &&
            text.substring(match.start, match.keywordEnd).endsWith(keyword, ignoreCase = true)
        if (!still) return hidePill()
        val typed = match.copy(replaceEnd = match.keywordEnd, trigger = Trigger.TYPED)
        if (needsSignIn(match.snippet)) return requestUnlock(node, typed)
        val ok = runCatching { expand(node, text!!, typed) }.isSuccess
        // Whatever happened, the pill must not stay up waiting.
        if (!ok || overlay?.visibleSnippetId == match.snippet.id) hidePill()
    }

    /** True for a locked snippet whose text can't be typed without signing in first. */
    private fun needsSignIn(s: Snippet): Boolean = s.locked && plainText(s) == null

    /** The text of a snippet that can be typed now: ordinary text, a locked snippet allowed to expand, or one the vault has open. */
    private fun plainText(s: Snippet): String? = when {
        !s.locked -> s.content
        s.expandUnlocked -> s.openSeal?.let(container.vault::openOpen)
        else -> null
    }

    private fun requestUnlock(node: AccessibilityNodeInfo, match: Match) {
        val id = nextRequest++
        // The vault may already be open from a recent visit to Clipsy: then no sign-in is needed.
        val open = match.snippet.sealedContent?.let(container.vault::open)
        if (open != null) return expand(node, node.text?.toString() ?: return hidePill(), match, open)
        awaiting = AwaitingUnlock(id, node, match, SystemClock.uptimeMillis())
        hidePill()
        runCatching { startActivity(UnlockActivity.intent(this, match.snippet.id, id)) }
            .onFailure { awaiting = null; Log.w("Clipsy", "Couldn’t open the unlock screen", it) }
    }

    private fun onUnlockResult(result: AppContainer.UnlockResult) {
        val wait = awaiting?.takeIf { it.requestId == result.requestId } ?: return
        awaiting = null
        val plain = result.text ?: return
        if (SystemClock.uptimeMillis() - wait.startedAt > UNLOCK_PATIENCE_MS) return
        scope.launch {
            // The sign-in screen covered the field; give it a moment to take focus back, then find it again.
            delay(150)
            val node = liveField(wait.node)
            val text = if (node.refresh()) node.text?.toString() else null
            val keyword = wait.match.snippet.keyword
            val still = text != null && keyword != null && text.length >= wait.match.keywordEnd &&
                text.substring(wait.match.start, wait.match.keywordEnd).endsWith(keyword, ignoreCase = true)
            if (!still) {
                // The field isn't where it was: don't type into something else. Put the text on the clipboard as a fallback.
                copySensitive(plain)
                return@launch
            }
            runCatching { expand(node, text!!, wait.match, plain) }
        }
    }

    /** Last resort when the field can't be found again: the user can paste it themselves. */
    private fun copySensitive(text: String) {
        val clip = ClipData.newPlainText("Clipsy", text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    private fun expand(node: AccessibilityNodeInfo, text: String, match: Match, plain: String? = null) {
        val s = match.snippet.let { if (it.locked) it.revealed(plain ?: plainText(it).orEmpty()) else it }
        when {
            s.type == SnippetType.TEXT -> insertText(node, text, match, container.render(s, readClipboard(), appName(node.packageName?.toString())))
            s.fileMode == FileMode.LINK && s.content.isNotBlank() -> insertText(node, text, match, Rendered(s.content.trim()))
            s.fileMode == FileMode.PASTE -> pasteFile(node, text, match)
            else -> shareFile(node, text, match)
        }
    }

    private fun insertText(node: AccessibilityNodeInfo, text: String, match: Match, rendered: Rendered) {
        val (newText, caret) = Expander.apply(text, match, rendered)
        if (!setText(node, newText, caret)) return hidePill()
        succeeded(match, rendered.text.length)
    }

    private fun pasteFile(node: AccessibilityNodeInfo, text: String, match: Match) {
        val s = match.snippet
        val clip = FileShare.clip(this, s) ?: return shareFile(node, text, match)
        // Clipboard first, so it's ready the moment the field is asked to paste.
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        val (without, caret) = Expander.remove(text, match)
        setText(node, without, caret)
        scope.launch {
            // A field only pastes while it has input focus, and many chat apps rebuild or refocus
            // their field right after its text is set. Let it settle, find the live field, make sure
            // it's focused, then paste; one retry if the app turns it down.
            var target = node
            var pasted = false
            for (attempt in 0..1) {
                delay(if (attempt == 0) 80L else 200L)
                target = liveField(target)
                if (!target.isFocused) target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                pasted = target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                if (pasted) break
            }
            Log.i("Clipsy", "File paste into ${target.packageName}: ${if (pasted) "accepted" else "refused"}")
            delay(350)
            target = liveField(target)
            // Refused, or pasted as text by a field that only takes text (most chat boxes take images
            // but not documents): undo any text, then hand the file to this same app to send, e.g.
            // WhatsApp's "Send to" screen. The share sheet is the last resort.
            if (!pasted || (target.text?.toString() ?: without) != without) {
                if (pasted) setText(target, without, caret)
                val app = target.packageName?.toString()
                Log.i("Clipsy", "File not taken by paste in $app; sending it to the app instead")
                (FileShare.sendTo(this@ExpansionService, s, app) ?: FileShare.chooser(this@ExpansionService, s))
                    ?.let { runCatching { startActivity(it) } }
            }
            succeeded(match, 0)
        }
    }

    /** The same field if it's still on screen, else whatever field now has input focus. */
    private fun liveField(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (node.refresh()) node
        else rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: node

    private fun shareFile(node: AccessibilityNodeInfo, text: String, match: Match) {
        val (without, caret) = Expander.remove(text, match)
        setText(node, without, caret)
        FileShare.chooser(this, match.snippet)?.let(::startActivity)
        succeeded(match, 0)
    }

    private fun succeeded(match: Match, insertedChars: Int) {
        pending = null
        idleJob?.cancel()
        if (container.settings.settings.value.haptics) tick()
        overlay?.let { o ->
            hideJob?.cancel()
            if (o.visibleSnippetId == match.snippet.id) {
                o.markDone()
                hideJob = scope.launch { delay(900); o.hide() }
            } else {
                o.hide()
            }
        }
        scope.launch { container.repository.recordExpansion(match.snippet.id, insertedChars - match.typedLength) }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String, caret: Int): Boolean {
        suppressUntil = SystemClock.uptimeMillis() + 300
        val ok = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) },
        )
        if (ok) {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
                },
            )
        }
        return ok
    }

    /** The app's name as the user knows it ("WhatsApp"), for {app}. */
    private fun appName(pkg: String?): String? = pkg?.let {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
    }

    /** Usually null in the background on Android 10+, in which case {clipboard} inserts nothing. */
    private fun readClipboard(): String? = runCatching {
        getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
    }.getOrNull()

    private fun tick() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(18, 120))
        }
    }
}
