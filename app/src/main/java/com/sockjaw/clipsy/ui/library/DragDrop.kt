package com.sockjaw.clipsy.ui.library

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Private type so drags from other apps are ignored by Library's drop targets. */
const val SNIPPET_MIME = "application/x-clipsy-snippets"

private fun DragAndDropEvent.snippetIds(): List<Long>? {
    if (!mimeTypes().contains(SNIPPET_MIME)) return null
    val clip = toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 } ?: return null
    return clip.getItemAt(0).text?.split(',')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.isNotEmpty() }
}

/**
 * A long-press on the row starts a drag of the ids [onStart] returns (it should select the row
 * if it isn't already, and return everything selected). Taps and swipes are left alone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.snippetDragSource(onStart: () -> List<Long>): Modifier {
    val start by rememberUpdatedState(onStart)
    return dragAndDropSource { _ ->
        val ids = start()
        DragAndDropTransferData(
            clipData = ClipData(
                ClipDescription("Snippets", arrayOf(SNIPPET_MIME)),
                ClipData.Item(ids.joinToString(",")),
            ),
        )
    }
}

/** Accepts dragged snippets. [onHover] tracks whether one is over this target; [onDrop] gets their ids. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.snippetDropTarget(onHover: (Boolean) -> Unit, onDrop: (List<Long>) -> Unit): Modifier {
    val hover by rememberUpdatedState(onHover)
    val drop by rememberUpdatedState(onDrop)
    val haptics = LocalHapticFeedback.current
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                // A light tick as the dragged snippets cross into a folder, so it can be felt.
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                hover(true)
            }
            override fun onExited(event: DragAndDropEvent) = hover(false)
            override fun onEnded(event: DragAndDropEvent) = hover(false)
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover(false)
                val ids = event.snippetIds() ?: return false
                drop(ids)
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(SNIPPET_MIME) }, target = target)
}

/** Reports whether a snippet drag is in progress anywhere on screen, so the drop dock can show. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.snippetDragWatcher(onActive: (Boolean) -> Unit): Modifier {
    val active by rememberUpdatedState(onActive)
    val target = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) = active(true)
            override fun onEnded(event: DragAndDropEvent) = active(false)
            override fun onDrop(event: DragAndDropEvent): Boolean = false
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(SNIPPET_MIME) }, target = target)
}
