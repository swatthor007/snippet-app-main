package com.sockjaw.clipsy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Square previews for media and PDF snippets, saved next to the files as PNGs so the pill,
 * lists and file screen don't decode full-size files. Made on import, or on first use for
 * snippets added before thumbnails existed.
 */
object Thumbnails {
    private const val SIZE = 256

    fun canPreview(snippet: Snippet): Boolean =
        snippet.type == SnippetType.MEDIA || (snippet.type == SnippetType.DOCUMENT && isPdf(snippet))

    fun isPdf(snippet: Snippet): Boolean =
        snippet.mimeType == "application/pdf" || snippet.name.endsWith(".pdf", ignoreCase = true)

    fun fileFor(context: Context, snippet: Snippet): File? =
        snippet.filePath?.let { File(File(context.filesDir, "thumbs"), File(it).name + ".png") }

    /** The thumbnail file, creating it if needed; null when this snippet can't have one. */
    suspend fun ensure(context: Context, snippet: Snippet): File? = withContext(Dispatchers.IO) {
        if (!canPreview(snippet)) return@withContext null
        val out = fileFor(context, snippet) ?: return@withContext null
        if (out.exists()) return@withContext out
        val src = snippet.filePath?.let(::File)?.takeIf { it.exists() } ?: return@withContext null
        val bitmap = runCatching { if (isPdf(snippet)) renderPdf(src) else decodeImage(src) }.getOrNull()
            ?: return@withContext null
        val square = squareCrop(bitmap, topAligned = isPdf(snippet))
        out.parentFile?.mkdirs()
        out.outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (square !== bitmap) bitmap.recycle()
        square.recycle()
        out
    }

    fun delete(context: Context, snippet: Snippet) {
        fileFor(context, snippet)?.delete()
    }

    /** Samples down while decoding so a 12 MP photo never lands in memory at full size. GIFs give their first frame. */
    private fun decodeImage(src: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        return BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** First page on white (PDF pages are transparent), at twice the thumbnail width for a crisp crop. */
    private fun renderPdf(src: File): Bitmap? =
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    // A malformed PDF can report a zero-size page; that would ask for an enormous bitmap.
                    if (page.width <= 0 || page.height <= 0) return null
                    val w = SIZE * 2
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceIn(1, w * 4)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    /** Centre crop for images; top crop for documents, where the title usually is. */
    private fun squareCrop(src: Bitmap, topAligned: Boolean): Bitmap {
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = if (topAligned) 0 else (src.height - side) / 2
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, Rect(left, top, left + side, top + side), Rect(0, 0, SIZE, SIZE), null)
        return out
    }
}
