package com.sockjaw.clipsy.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import com.sockjaw.clipsy.ClipsyApp
import android.net.Uri
import com.sockjaw.clipsy.data.Snippet

/** Text that arrived through Android's share sheet, waiting to become a snippet. */
data class SharedText(val text: String, val subject: String? = null)

/** Sharing and copying snippets out of Clipsy. Files go as streams, text as text. */
object Share {
    /**
     * A text snippet's content as it would be typed: dates, greeting, other snippets and the next
     * {counter} filled in, {clipboard} from what's on the clipboard right now, {cursor} dropped (a
     * copy has no cursor) and {app} empty (there's no app being typed in).
     */
    fun rendered(context: Context, snippet: Snippet): String =
        (context.applicationContext as ClipsyApp).container.render(snippet, currentClipboard(context), app = null).text

    private fun currentClipboard(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    /** A chooser for one or many snippets; null when there is nothing shareable. */
    fun chooser(context: Context, snippets: List<Snippet>): Intent? {
        val files = snippets.filter { it.type.isFile }
        val text = snippets.filter { !it.type.isFile && !it.needsUnlock }.joinToString("\n\n") { rendered(context, it) }
        val uris = files.mapNotNull { FileShare.uri(context, it) }
        if (uris.isEmpty() && text.isBlank()) return null

        val send = when {
            uris.size == 1 && text.isBlank() ->
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(files.first().mimeType ?: "*/*")
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
            else -> Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
                .setType("*/*")
        }
        if (text.isNotBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
        if (uris.isNotEmpty()) {
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(snippets.first().name, uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
        }
        val title = if (snippets.size == 1) snippets[0].name else "${snippets.size} snippets"
        // Shown as the heading of the share sheet's preview on Android 10+.
        send.putExtra(Intent.EXTRA_TITLE, title)
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Copies snippets to the clipboard. Text is copied as text (several are joined with blank lines);
     * when there is no text, files are copied as files, which chat and notes apps can paste. In a
     * mix, the files are left out and counted in [CopyResult.skippedFiles].
     */
    fun copy(context: Context, snippets: List<Snippet>): CopyResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val texts = snippets.filter { !it.type.isFile && !it.needsUnlock }
        if (texts.isNotEmpty()) {
            // Render before writing: {clipboard} must read what was there before this copy replaces it.
            val text = texts.joinToString("\n\n") { rendered(context, it) }
            val clip = ClipData.newPlainText(texts.first().name, text)
            // Text from a locked snippet is hidden in the system's copy preview (Android 13+).
            if (Build.VERSION.SDK_INT >= 33 && texts.any { it.locked }) {
                clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
            cm.setPrimaryClip(clip)
            return CopyResult(texts.size, snippets.size - texts.size)
        }
        val clips = snippets.mapNotNull { FileShare.clip(context, it) }
        if (clips.isEmpty()) return CopyResult(0, snippets.size)
        // One entry carrying every file; the description lists each file's type.
        val types = clips.flatMap { c -> (0 until c.description.mimeTypeCount).map { c.description.getMimeType(it) } }.distinct()
        val clip = ClipData(ClipDescription(clips.first().description.label, types.toTypedArray()), clips.first().getItemAt(0))
        clips.drop(1).forEach { clip.addItem(it.getItemAt(0)) }
        cm.setPrimaryClip(clip)
        return CopyResult(clips.size, 0)
    }

    data class CopyResult(val copied: Int, val skippedFiles: Int)
}
