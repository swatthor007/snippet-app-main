package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement.End
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.service.Template
import com.sockjaw.clipsy.ui.theme.Mono
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.pressable


/** Where each placeholder ({date}, {greeting}, {snippet:sig}…) sits in [text]. */
fun tokenRanges(text: String): List<IntRange> = Template.token.findAll(text).map { it.range }.toList()

/**
 * Shown either side of a token, only on screen, so its chip has room inside and a small gap to the
 * words next to it. Never stored: copying and expanding use the snippet's own text.
 */
private const val TOKEN_PAD = '\u2002' // en space

/**
 * Display text for [text]: each token padded with [TOKEN_PAD], coloured [fg], a touch bolder.
 * [toDisplay] maps every offset in [text] (0..length) to its offset in the result, keeping the
 * cursor outside chips: before a token's leading pad, after its trailing pad. [toOriginal] is the
 * reverse, with each pad mapping to the token edge beside it.
 */
private class PaddedTokens(text: String, fg: Color) {
    val toDisplay = IntArray(text.length + 1)
    val toOriginal: IntArray
    val display: AnnotatedString

    init {
        val ranges = tokenRanges(text)
        val back = ArrayList<Int>(text.length + ranges.size * 2 + 1)
        var shift = 0
        var next = 0
        display = buildAnnotatedString {
            fun plain(until: Int) {
                while (next < until) {
                    toDisplay[next] = next + shift
                    back += next
                    append(text[next])
                    next++
                }
            }
            for (r in ranges) {
                plain(r.first)
                // The cursor just before a token sits before its leading pad, outside the chip.
                val padAt = r.first + shift
                back += r.first
                append(TOKEN_PAD)
                shift++
                withStyle(SpanStyle(color = fg, fontWeight = FontWeight.Medium)) { plain(r.last + 1) }
                toDisplay[r.first] = padAt
                back += r.last + 1
                append(TOKEN_PAD)
                shift++
            }
            plain(text.length)
        }
        toDisplay[text.length] = text.length + shift
        back += text.length
        toOriginal = back.toIntArray()
    }
}

/** Colours the tokens and pads them; [tokenChips] draws the rounded chip behind each one. */
class TokenTransformation(private val fg: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val p = PaddedTokens(text.text, fg)
        return TransformedText(
            p.display,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = p.toDisplay[offset.coerceIn(0, p.toDisplay.lastIndex)]
                override fun transformedToOriginal(offset: Int) = p.toOriginal[offset.coerceIn(0, p.toOriginal.lastIndex)]
            },
        )
    }
}

/** [text] as shown read-only: tokens padded, coloured [fg], a touch bolder. */
fun tokenText(text: String, fg: Color): AnnotatedString = PaddedTokens(text, fg).display

/**
 * Draws a rounded chip behind each token in the laid-out display text, reaching most of the way
 * into its pads: roomy inside, a small gap outside. A token that wraps gets one chip per line.
 * [layout] is read at draw time, so pass the latest TextLayoutResult.
 */
fun Modifier.tokenChips(layout: () -> TextLayoutResult?, fill: Color): Modifier = drawBehind {
    val l = layout() ?: return@drawBehind
    val shown = l.layoutInput.text.text
    val padY = 1.dp.toPx()
    val radius = CornerRadius(6.dp.toPx())
    for (r in tokenRanges(shown)) {
        val end = r.last + 1
        val firstLine = l.getLineForOffset(r.first)
        val lastLine = l.getLineForOffset(end - 1)
        for (line in firstLine..lastLine) {
            val from = maxOf(r.first, l.getLineStart(line))
            val to = minOf(end, l.getLineEnd(line, visibleEnd = true))
            if (from >= to) continue
            // Character boxes, not offset positions: an offset at a soft line break is ambiguous and
            // reports the end of the line above, which stretched chips across the wrong words.
            val first = l.getBoundingBox(from)
            val last = l.getBoundingBox(to - 1)
            val left = minOf(first.left, last.left)
            val right = maxOf(first.right, last.right)
            // Reach into the pad on each side when it sits on this same line, leaving ~30% as the outer gap.
            val padBefore = if (from == r.first && from > 0 && shown[from - 1] == TOKEN_PAD && l.getLineForOffset(from - 1) == line) l.getBoundingBox(from - 1).width else 0f
            val padAfter = if (to == end && to < shown.length && shown[to] == TOKEN_PAD && l.getLineForOffset(to) == line) l.getBoundingBox(to).width else 0f
            // When the pad wrapped onto the other line, the token sits at a line edge: give it the
            // same inner room the pad would have (there's card padding beyond it to draw into).
            val edgeRoom = 5.dp.toPx()
            val x0 = left - if (padBefore > 0f) padBefore * 0.7f else edgeRoom
            val x1 = right + if (padAfter > 0f) padAfter * 0.7f else edgeRoom
            val top = l.getLineTop(line)
            val bottom = l.getLineBottom(line)
            drawRoundRect(fill, topLeft = Offset(x0, top + padY), size = Size(x1 - x0, bottom - top - padY * 2), cornerRadius = radius)
        }
    }
}

/** Snippet content shown read-only, with its tokens as coloured chips, as in the editor. */
@Composable
fun TokenText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ClipsyColors.Text,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fg = ClipsyColors.AccentStrong
    val fill = ClipsyColors.AccentFaint
    Text(
        remember(text, fg) { tokenText(text, fg) },
        modifier.tokenChips({ layout }, fill),
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}

/**
 * The content input used by the editor and in-place editing: tokens show as chips while typing.
 */
@Composable
fun TokenTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fg = ClipsyColors.AccentStrong
    val fill = ClipsyColors.AccentFaint
    val tokens = remember(fg) { TokenTransformation(fg) }
    Box(modifier.fillMaxWidth()) {
        if (value.text.isEmpty()) Text("What should this expand to?", color = ClipsyColors.TextTertiary, style = MaterialTheme.typography.bodyLarge)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ClipsyColors.Text),
            cursorBrush = SolidColor(ClipsyColors.Accent),
            visualTransformation = tokens,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth().tokenChips({ layout }, fill),
        )
    }
}

/** Replaces the selection with [token] and puts the cursor after it. */
fun TextFieldValue.insertToken(token: String): TextFieldValue {
    val text = text.replaceRange(selection.min, selection.max, token)
    return TextFieldValue(text, TextRange(selection.min + token.length))
}

/** The chips under a content field, in the order people reach for them. */
private val insertable = listOf(
    "Date" to "{date}", "Time" to "{time}", "Day" to "{day}", "Month" to "{month}", "Year" to "{year}",
    "Greeting" to "{greeting}", "Clipboard" to "{clipboard}", "Counter" to "{counter}", "App" to "{app}", "Cursor" to "{cursor}",
)

/**
 * The row of placeholder chips under a content field; it scrolls sideways. "Snippet…" asks the
 * caller to open a picker ([onPickSnippet]) rather than inserting anything itself.
 */
@Composable
fun TokenChips(onInsert: (String) -> Unit, onPickSnippet: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Insert", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Label)
        val chip = Modifier.clip(RoundedCornerShape(8.dp)).background(ClipsyColors.AccentFaint)
        insertable.take(6).forEach { (label, token) ->
            Text(label, chip.pressable(pressedScale = 0.9f) { onInsert(token) }.padding(horizontal = 10.dp, vertical = 6.dp), color = ClipsyColors.Accent, fontSize = ClipsyType.Caption)
        }
        Text("Snippet…", chip.pressable(pressedScale = 0.9f, onClick = onPickSnippet).padding(horizontal = 10.dp, vertical = 6.dp), color = ClipsyColors.Accent, fontSize = ClipsyType.Caption, fontWeight = FontWeight.Medium)
        insertable.drop(6).forEach { (label, token) ->
            Text(label, chip.pressable(pressedScale = 0.9f) { onInsert(token) }.padding(horizontal = 10.dp, vertical = 6.dp), color = ClipsyColors.Accent, fontSize = ClipsyType.Caption)
        }
    }
}

/**
 * Pick a snippet to pull in as {snippet:keyword}. Only text snippets with a keyword can be
 * referenced; [selfId] (the snippet being edited) is left out.
 */
@Composable
fun SnippetPickerSheet(snippets: List<Snippet>, selfId: Long, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val texts = snippets.filter { it.id != selfId && !it.type.isFile }
    val pickable = texts.filter { it.keyword != null && (query.isBlank() || it.name.contains(query, true) || it.keyword.contains(query, true)) }
        .sortedBy { it.name.lowercase() }
    val noKeyword = texts.count { it.keyword == null }
    ClipsySheet(onDismiss) { close ->
        SheetTitle("Insert a snippet", "Its text goes in when this one expands. Snippets inside it aren’t expanded again.")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillSearchField(query, { query = it }, "Search snippets", Modifier.fillMaxWidth())
            if (pickable.isEmpty()) {
                Text(
                    if (texts.isEmpty()) "No other text snippets yet." else "No text snippets with a keyword match.",
                    color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(4.dp),
                )
            } else {
                GroupedCard(Modifier.heightIn(max = 380.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        pickable.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable(pressedScale = 0.985f) { close { onPick(s.keyword!!) } }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.content.replace(Regex("\\s+"), " ").trim(), color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(s.keyword!!, fontFamily = Mono, fontSize = ClipsyType.Caption, color = ClipsyColors.Accent)
                            }
                        }
                    }
                }
            }
            if (noKeyword > 0) {
                Text(
                    "$noKeyword text ${if (noKeyword == 1) "snippet has" else "snippets have"} no keyword. Give one a keyword to insert it here.",
                    color = ClipsyColors.TextTertiary, fontSize = ClipsyType.Caption, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Content editing in place on the snippet screen. Blank content can't be saved. */
@Composable
fun InlineContentEditor(
    current: String,
    snippets: List<Snippet>,
    selfId: Long,
    modifier: Modifier = Modifier,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    var picking by remember { mutableStateOf(false) }
    if (picking) SnippetPickerSheet(snippets, selfId, onPick = { value = value.insertToken("{snippet:$it}") }, onDismiss = { picking = false })
    var shake by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier.shakeOn(shake)) {
        TokenTextField(value, { value = it }, Modifier.heightIn(min = 120.dp).padding(16.dp).focusRequester(focus))
        RowDivider()
        TokenChips({ value = value.insertToken(it) }, onPickSnippet = { picking = true })
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = End) {
            TextAction("Cancel", onCancel)
            TextAction("Save", {
                if (value.text.isBlank()) {
                    shake++
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSave(value.text)
                }
            }, bold = true)
        }
    }
}
