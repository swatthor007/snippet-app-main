package com.sockjaw.clipsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.Thumbnails
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import java.io.File

/** The snippet's saved square thumbnail, made on first use if it doesn't exist yet. */
@Composable
fun rememberThumbnail(snippet: Snippet): File? {
    val context = LocalContext.current
    val thumb by produceState<File?>(
        initialValue = Thumbnails.fileFor(context, snippet)?.takeIf { it.exists() },
        snippet.id,
        snippet.filePath,
    ) {
        if (value == null) value = Thumbnails.ensure(context, snippet)
    }
    return thumb
}

/** Square thumbnail for a media or PDF snippet, with the type icon while it loads or if it can't. */
@Composable
fun SnippetThumb(snippet: Snippet, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = rememberThumbnail(snippet)
    var failed by remember(file) { mutableStateOf(false) }
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(ClipsyColors.CardAlt)
            .border(0.5.dp, ClipsyColors.CardBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (file == null || failed) {
            Icon(snippetIcon(snippet), null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(size * 0.5f))
        } else {
            val request = remember(file) { ImageRequest.Builder(context).data(file).crossfade(true).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.size(size),
            )
        }
    }
}

/** A thumbnail for media and PDFs, the icon tile for everything else. */
@Composable
fun SnippetLeading(snippet: Snippet, size: Dp, tileBackground: Color = ClipsyColors.CardAlt) {
    val linkImage = linkImageFor(snippet)
    if (Thumbnails.canPreview(snippet)) {
        SnippetThumb(snippet, size)
    } else if (linkImage != null) {
        // A link shows its page's share image instead of a generic icon.
        LinkThumbnail(linkImage, snippet, Modifier.size(size).clip(RoundedCornerShape(size / 4)), iconSize = size * 0.5f)
    } else {
        IconTile(snippetIcon(snippet), size = size, background = tileBackground)
    }
}

/** Fills its bounds with the snippet's thumbnail, cropped, square-cornered (the caller clips it). */
@Composable
fun SnippetImage(snippet: Snippet, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = rememberThumbnail(snippet)
    var failed by remember(file) { mutableStateOf(false) }
    Box(modifier.background(ClipsyColors.CardAlt), contentAlignment = Alignment.Center) {
        if (file == null || failed) {
            Icon(snippetIcon(snippet), null, tint = ClipsyColors.TextSecondary, modifier = Modifier.size(32.dp))
        } else {
            val request = remember(file) { ImageRequest.Builder(context).data(file).crossfade(true).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Saved share-image paths by link address, supplied once near the top so every list can use them. */
val LocalLinkImages = androidx.compose.runtime.staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** The share image saved for a link snippet, or null (not a link, locked, not looked up yet, or no image). */
@Composable
fun linkImageFor(snippet: Snippet): java.io.File? {
    if (snippet.locked || !snippet.isLink) return null
    val images = LocalLinkImages.current
    val path = remember(snippet.content, images) {
        com.sockjaw.clipsy.data.Links.parse(snippet.content)?.let { images[com.sockjaw.clipsy.data.Links.withScheme(it)] }
    }
    return path?.let { java.io.File(it) }
}

/** A link's share image, cropped to fill; the link icon shows if the file can't be read. */
@Composable
fun LinkThumbnail(file: java.io.File, snippet: Snippet, modifier: Modifier = Modifier, iconSize: Dp = 32.dp) {
    val context = LocalContext.current
    var failed by remember(file) { mutableStateOf(false) }
    Box(modifier.background(ClipsyColors.CardAlt), contentAlignment = Alignment.Center) {
        if (failed || !file.exists()) {
            Icon(snippetIcon(snippet), null, tint = ClipsyColors.Accent, modifier = Modifier.size(iconSize))
        } else {
            val request = remember(file) { ImageRequest.Builder(context).data(file).crossfade(true).build() }
            AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, onError = { failed = true }, modifier = Modifier.fillMaxSize())
        }
    }
}
