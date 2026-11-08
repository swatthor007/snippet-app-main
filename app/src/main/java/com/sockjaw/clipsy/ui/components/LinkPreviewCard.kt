package com.sockjaw.clipsy.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sockjaw.clipsy.data.LinkKind
import com.sockjaw.clipsy.data.LinkPreview
import com.sockjaw.clipsy.data.Links
import com.sockjaw.clipsy.data.PreviewStatus
import com.sockjaw.clipsy.data.Stats
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.ClipsyType
import com.sockjaw.clipsy.ui.theme.LocalReducedMotion
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.Eye
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Link
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Refresh
import java.io.File

/**
 * What a saved link points at: its share image, site, title and a short preview of the content.
 * Shows a placeholder while the page is being read, and a retry when it couldn't be.
 */
@Composable
fun LinkPreviewCard(
    url: String,
    preview: LinkPreview?,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onPreview: (() -> Unit)? = null,
) {
    GroupedCard(modifier) {
        when {
            preview == null && loading -> LinkSkeleton(compact)
            preview == null || preview.status == PreviewStatus.FAILED -> LinkUnavailable(url, loading, onRefresh)
            else -> LinkContent(url, preview, loading, onRefresh, onPreview, compact)
        }
    }
}

@Composable
private fun LinkContent(url: String, p: LinkPreview, loading: Boolean, onRefresh: () -> Unit, onPreview: (() -> Unit)?, compact: Boolean) {
    val host = Links.host(url)
    if (compact) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PreviewImage(p, Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.siteName ?: host, color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.title ?: host, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    PreviewImage(p, Modifier.fillMaxWidth().aspectRatio(if (p.imagePath != null) 1.91f else 3.2f))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(p.siteName ?: host, color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.title ?: host, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        p.description?.let {
            Text(it, color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (p.kind != LinkKind.PAGE) {
            Text(
                listOfNotNull(p.mimeType?.substringAfter('/')?.uppercase(), p.sizeBytes.takeIf { it > 0 }?.let(Stats::formatSize)).joinToString(" · "),
                color = ClipsyColors.TextTertiary, fontSize = ClipsyType.Caption,
            )
        }
    }
    RowDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (loading) "Updating…" else Stats.relativeAge(p.fetchedAt).let { if (it == "now") "Updated just now" else "Updated $it ago" },
            Modifier.weight(1f), color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption,
        )
        if (onPreview != null) FooterAction(TablerIcons.Eye, "Preview", onClick = onPreview)
        FooterAction(TablerIcons.Refresh, "Refresh", enabled = !loading, onClickLabel = "Refresh preview", onClick = onRefresh)
    }
}

@Composable
private fun FooterAction(icon: ImageVector, label: String, enabled: Boolean = true, onClickLabel: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).pressable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = ClipsyColors.Accent, modifier = Modifier.size(16.dp))
        Text(label, color = ClipsyColors.Accent, fontSize = ClipsyType.Label)
    }
}

/** The share image, or a quiet tile with the right icon when there isn't one. */
@Composable
private fun PreviewImage(p: LinkPreview, modifier: Modifier) {
    val context = LocalContext.current
    val file = p.imagePath?.let(::File)?.takeIf { it.exists() }
    Box(modifier.background(ClipsyColors.CardAlt), contentAlignment = Alignment.Center) {
        if (file != null) {
            val request = remember(file) { ImageRequest.Builder(context).data(file).crossfade(true).build() }
            AsyncImage(request, contentDescription = "Preview image for ${p.title ?: p.url}", contentScale = if (p.kind == LinkKind.IMAGE) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                when (p.kind) { LinkKind.IMAGE -> TablerIcons.Photo; LinkKind.FILE -> TablerIcons.FileText; else -> TablerIcons.Link },
                null, tint = ClipsyColors.TextTertiary, modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun LinkSkeleton(compact: Boolean) {
    val reduced = LocalReducedMotion.current
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val alpha = if (reduced) 0.8f else pulse
    Column(Modifier.semantics { contentDescription = "Loading preview" }) {
        if (!compact) Box(Modifier.fillMaxWidth().aspectRatio(1.91f).graphicsLayer { this.alpha = alpha }.background(ClipsyColors.Track))
        Column(Modifier.padding(16.dp).graphicsLayer { this.alpha = alpha }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(0.3f).size(width = 80.dp, height = 10.dp).clip(RoundedCornerShape(5.dp)).background(ClipsyColors.Track))
            Box(Modifier.fillMaxWidth(0.85f).size(width = 240.dp, height = 16.dp).clip(RoundedCornerShape(5.dp)).background(ClipsyColors.Track))
            Box(Modifier.fillMaxWidth(0.6f).size(width = 180.dp, height = 12.dp).clip(RoundedCornerShape(5.dp)).background(ClipsyColors.Track))
        }
    }
}

@Composable
private fun LinkUnavailable(url: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable(enabled = !loading, onClickLabel = "Try again", pressedScale = 0.99f, onClick = onRefresh).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(TablerIcons.Link, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("No preview for ${Links.host(url)}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("The page couldn’t be reached. Tap to try again.", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
        }
        Icon(TablerIcons.Refresh, null, tint = ClipsyColors.Accent, modifier = Modifier.size(18.dp))
    }
}

/**
 * Shown when a link turns out to be an image: keep it as a link that opens online, or download a
 * copy and store it with your media.
 */
@Composable
fun ImageLinkPrompt(busy: Boolean, onSaveAsMedia: () -> Unit, onKeepLink: () -> Unit, modifier: Modifier = Modifier) {
    GroupedCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(TablerIcons.Photo, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("This link is an image", style = MaterialTheme.typography.titleMedium)
                    Text("Save a copy with your media, or keep it as a link that opens online.", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
                }
            }
            PrimaryButton(if (busy) "Downloading…" else "Save as media", onSaveAsMedia, enabled = !busy)
            Text(
                "Keep as link",
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                    .pressable(enabled = !busy, onClick = onKeepLink)
                    .padding(vertical = 12.dp),
                color = if (busy) ClipsyColors.TextTertiary else ClipsyColors.Accent,
                fontSize = ClipsyType.Body,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
