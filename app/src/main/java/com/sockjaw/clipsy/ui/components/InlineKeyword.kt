package com.sockjaw.clipsy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement.spacedBy
import com.sockjaw.clipsy.data.Keyword
import com.sockjaw.clipsy.data.KeywordIssue
import com.sockjaw.clipsy.data.SnippetRepository

/**
 * Keyword editing in place, so adding or changing a keyword never leaves the snippet's screen.
 * Done on the keyboard or Save stores it (a blank keyword clears it); an invalid or taken one shakes.
 */
@Composable
fun InlineKeywordEditor(
    snippetId: Long,
    current: String?,
    repo: SnippetRepository,
    example: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(current.orEmpty()) }
    var taken by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(value) { taken = repo.isKeywordTaken(value, snippetId) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val issue = Keyword.validate(value) ?: if (taken) KeywordIssue.TAKEN else null

    fun commit() {
        if (issue != null) {
            shake++
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSave(value)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .keepAboveKeyboard()
            .shakeOn(shake),
        verticalArrangement = spacedBy(0.dp),
    ) {
        KeywordField(value, { value = it }, issue, example = example, focusRequester = focus, imeAction = ImeAction.Done, onDone = ::commit)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction("Cancel", onCancel)
            TextAction("Save", ::commit, bold = true)
        }
    }
}
