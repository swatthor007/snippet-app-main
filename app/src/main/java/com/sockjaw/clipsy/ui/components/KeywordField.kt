package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.data.KeywordIssue
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Mono
import com.sockjaw.clipsy.ui.theme.Motion

/** What a keyword is, shown under every keyword field. */
const val KEYWORD_HELP = "Type this word in any app and Clipsy replaces it with the snippet. Letters, numbers and underscores; it only matches as a whole word."

/**
 * The one keyword input used everywhere: accent label, accent outline while focused, and
 * the explanation underneath, which turns into the error when the keyword can't be used.
 * Never pre-filled; [example] only shows as a hint.
 */
@Composable
fun KeywordField(
    value: String,
    onValueChange: (String) -> Unit,
    issue: KeywordIssue?,
    modifier: Modifier = Modifier,
    example: String = "sig",
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(
        when {
            issue != null -> ClipsyColors.Danger
            focused -> ClipsyColors.Accent
            else -> ClipsyColors.Accent.copy(alpha = 0.35f)
        },
        Motion.standard(Motion.QUICK),
        label = "keywordBorder",
    )
    val helpColor by animateColorAsState(if (issue != null) ClipsyColors.Danger else ClipsyColors.TextSecondary, Motion.standard(), label = "keywordHelp")

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(CardShape)
                .background(ClipsyColors.AccentFaint.copy(alpha = 0.55f))
                .border(1.dp, border, CardShape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Keyword", Modifier.width(76.dp), color = ClipsyColors.Accent, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("e.g. $example", color = ClipsyColors.TextTertiary, fontFamily = Mono, fontSize = ClipsyType.Label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(it.trim()) },
                    singleLine = true,
                    interactionSource = source,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ClipsyColors.Accent, fontFamily = Mono, fontSize = ClipsyType.Label),
                    cursorBrush = SolidColor(ClipsyColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .semantics {
                            contentDescription = "Keyword"
                            if (issue != null) error(issue.message)
                        },
                )
            }
            if (value.isNotEmpty()) {
                val ok = issue == null
                val color by animateColorAsState(if (ok) ClipsyColors.Accent else ClipsyColors.Danger, label = "free")
                CrossfadeText(if (ok) "Available" else if (issue == KeywordIssue.TAKEN) "Taken" else "Invalid", color = color, fontSize = ClipsyType.Caption)
            }
        }
        CrossfadeText(
            issue?.message ?: KEYWORD_HELP,
            color = helpColor,
            fontSize = ClipsyType.Caption,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
