package com.sockjaw.clipsy.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Returns a function that opens a file picker for any file type.
 *
 * Tries, in order: the system document picker (ACTION_OPEN_DOCUMENT), ACTION_GET_CONTENT, then a
 * chooser of every app that can provide a file. Each step runs only if the one before it fails to
 * start, which happens on some older and manufacturer-modified builds.
 */
@Composable
fun rememberFilePicker(onPicked: (Uri) -> Unit, onUnavailable: () -> Unit = {}): () -> Unit =
    rememberPicker(mediaOnly = false, onPicked, onUnavailable)

/**
 * Images and GIFs only. Uses the system photo picker where there is one (it falls back to the
 * document picker by itself), then GET_CONTENT for images as a last resort.
 */
@Composable
fun rememberMediaPicker(onPicked: (Uri) -> Unit, onUnavailable: () -> Unit = {}): () -> Unit =
    rememberPicker(mediaOnly = true, onPicked, onUnavailable)

/**
 * ACTION_OPEN_DOCUMENT for any file, kept minimal: AndroidX's OpenDocument always adds
 * EXTRA_MIME_TYPES, and the match-everything type combined with that extra crashes some
 * manufacturers' file managers.
 */
private class OpenAnyDocument : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

@Composable
private fun rememberPicker(mediaOnly: Boolean, onPicked: (Uri) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val unavailable = rememberUpdatedState(onUnavailable)
    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) callback.value(uri)
    }
    val openDocument = rememberLauncherForActivityResult(OpenAnyDocument()) { uri ->
        if (uri != null) callback.value(uri)
    }
    // Last resort: a chooser, so a broken default file manager can be bypassed for another app.
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.takeIf { r.resultCode == Activity.RESULT_OK }?.let { callback.value(it) }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback.value(uri)
    }
    return remember(mediaOnly) {
        {
            // Some phones (MIUI and other trimmed builds) replace or lock down the system picker and
            // throw something other than ActivityNotFoundException when it's launched, so any failure
            // moves on to the next picker instead of crashing.
            val opened = runCatching {
                if (mediaOnly) {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    openDocument.launch(Unit)
                }
            }.isSuccess ||
                runCatching { getContent.launch(if (mediaOnly) "image/*" else "*/*") }.isSuccess ||
                runCatching {
                    val pick = Intent(Intent.ACTION_GET_CONTENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(if (mediaOnly) "image/*" else "*/*")
                    chooser.launch(Intent.createChooser(pick, null))
                }.isSuccess
            if (!opened) unavailable.value()
        }
    }
}
