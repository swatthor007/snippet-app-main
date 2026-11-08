package com.sockjaw.clipsy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import java.io.File

/**
 * Widgets can only show drawables and bitmaps, not Compose vectors. This draws the app's own
 * Tabler icons into bitmaps (so a widget uses the same icons as the app) and makes the rounded
 * file previews, with the corners baked in because RemoteViews can't clip images before Android 12.
 */
object WidgetBitmaps {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024 / 4) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 4
    }

    /** A mid-tone blue that reads on both the light and the dark widget tile. */
    const val ICON_TINT = 0xFF5877E8.toInt()

    fun icon(context: Context, vector: ImageVector, key: String, sizeDp: Float, tint: Int = ICON_TINT): Bitmap {
        val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val cacheKey = "icon:$key:$px:$tint"
        cache.get(cacheKey)?.let { return it }
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = px / vector.viewportWidth
        canvas.scale(scale, scale)
        drawNodes(canvas, vector.root, tint)
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun drawNodes(canvas: Canvas, nodes: Iterable<VectorNode>, tint: Int) {
        for (node in nodes) {
            when (node) {
                is VectorGroup -> drawNodes(canvas, node, tint)
                is VectorPath -> {
                    val stroke = node.stroke
                    val fill = node.fill
                    val path = node.pathData.toPath().asAndroidPath()
                    if (fill is SolidColor && fill.value.alpha > 0f) {
                        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = tint
                            alpha = (node.fillAlpha * 255).toInt()
                        })
                    }
                    // Tabler draws an invisible 24×24 square first; skip strokes that can't be seen.
                    if (stroke is SolidColor && stroke.value.alpha > 0f) {
                        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            color = tint
                            alpha = (node.strokeAlpha * 255).toInt()
                            strokeWidth = node.strokeLineWidth
                            strokeCap = when (node.strokeLineCap) { StrokeCap.Round -> Paint.Cap.ROUND; StrokeCap.Square -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
                            strokeJoin = when (node.strokeLineJoin) { StrokeJoin.Round -> Paint.Join.ROUND; StrokeJoin.Bevel -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
                            strokeMiter = node.strokeLineMiter
                        })
                    }
                }
            }
        }
    }

    /** The file's saved preview as a square with rounded corners, or null when there is none. */
    fun preview(file: File, sizeDp: Float, radiusDp: Float, density: Float): Bitmap? {
        if (!file.exists()) return null
        val px = (sizeDp * density).toInt().coerceAtLeast(1)
        val key = "thumb:${file.path}:${file.lastModified()}:$px"
        cache.get(key)?.let { return it }
        val source = BitmapFactory.decodeFile(file.path) ?: return null
        val scaled = Bitmap.createScaledBitmap(source, px, px, true)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val r = radiusDp * density
        Canvas(out).drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, paint)
        if (scaled !== source) source.recycle()
        cache.put(key, out)
        return out
    }
}
