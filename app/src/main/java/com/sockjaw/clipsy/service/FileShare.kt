package com.sockjaw.clipsy.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sockjaw.clipsy.data.Snippet
import java.io.File

object FileShare {
    fun authority(context: Context) = "${context.packageName}.files"

    fun uri(context: Context, snippet: Snippet): Uri? {
        val file = snippet.filePath?.let(::File)?.takeIf { it.exists() } ?: return null
        return FileProvider.getUriForFile(context, authority(context), file, snippet.name)
    }

    /**
     * The snippet's file as a clipboard entry, labelled with the type stored when it was added.
     * ClipData.newUri would ask the provider, which guesses from the file extension and says
     * "binary" when there isn't one; chat apps then refuse to paste it as an image.
     */
    fun clip(context: Context, snippet: Snippet): ClipData? {
        val uri = uri(context, snippet) ?: return null
        val mime = snippet.mimeType ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        return ClipData(ClipDescription(snippet.name, arrayOf(mime)), ClipData.Item(uri))
    }

    /**
     * Sends the file straight to [pkg] (the app being typed in), e.g. WhatsApp's own "Send to"
     * screen, when that app takes this kind of file; otherwise null.
     */
    fun sendTo(context: Context, snippet: Snippet, pkg: String?): Intent? {
        if (pkg == null || pkg == context.packageName) return null
        val uri = uri(context, snippet) ?: return null
        val send = Intent(Intent.ACTION_SEND)
            .setType(snippet.mimeType ?: "*/*")
            .setPackage(pkg)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { clipData = ClipData.newRawUri(snippet.name, uri) }
        return send.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** A chooser for the snippet's file, startable from a service. */
    fun chooser(context: Context, snippet: Snippet): Intent? {
        val uri = uri(context, snippet) ?: return null
        val send = Intent(Intent.ACTION_SEND)
            .setType(snippet.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(snippet.name, uri) }
        return Intent.createChooser(send, snippet.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
