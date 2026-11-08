package com.sockjaw.clipsy.ui.components

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.animation.core.Animatable
import compose.icons.TablerIcons
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Lock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.Stats
import com.sockjaw.clipsy.data.UsageEvent
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Motion
import kotlinx.coroutines.delay

fun expandDetail(e: ExpandAfter): String = when (e) {
    ExpandAfter.SPACE -> "Type the keyword, then a space"
    ExpandAfter.ENTER -> "Type the keyword, then Enter (multi-line fields)"
    ExpandAfter.INSTANT -> "As soon as the keyword is typed"
}

/**
 * Usage over the last 30 days, the same for every snippet type. Time saved only makes sense
 * for text, so files show expansions and last use.
 */
@Composable
fun UsageSection(snippet: Snippet, events: List<UsageEvent>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("Usage", Modifier)
            Text("Last 30 days", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
        }
        GroupedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (snippet.type.isFile) Arrangement.spacedBy(40.dp) else Arrangement.SpaceBetween) {
                    Stat("${snippet.useCount}", if (snippet.useCount == 1) "Expansion" else "Expansions")
                    if (!snippet.type.isFile) Stat(Stats.formatSaved(events.sumOf { it.charsSaved.toLong() }), "Time saved")
                    Stat(if (snippet.lastUsedAt == null) "Never" else Stats.relativeAge(snippet.lastUsedAt), "Last used")
                }
                DailyBars(
                    Stats.dailyCounts(events, 30),
                    Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "Daily expansions over the last 30 days" },
                )
            }
        }
    }
}

/** Folder, expand after, where it works, and pinning: shared by text, media and documents. */
@Composable
fun BehaviourCard(
    snippet: Snippet,
    excludedApps: Int,
    onExpandAfter: (ExpandAfter) -> Unit,
    onTogglePin: () -> Unit,
    onKeepSpace: (Boolean) -> Unit,
    folderName: String?,
    onFolder: () -> Unit,
    modifier: Modifier = Modifier,
    /** Text snippets only: shows the Lock switch. */
    onLockChange: ((Boolean) -> Unit)? = null,
    onExpandUnlockedChange: ((Boolean) -> Unit)? = null,
) {
    GroupedCard(modifier) {
        ValueRow("Folder", folderName ?: "None", icon = TablerIcons.Folder, onClick = onFolder)
        RowDivider()
        ChoiceRow("Expand after", ExpandAfter.entries, snippet.expandAfter, { it.label }, onExpandAfter, optionDetail = ::expandDetail)
        RowDivider()
        SwitchRow("Keep the space after", snippet.keepSpace, onKeepSpace)
        RowDivider()
        ValueRow("Works in", if (excludedApps == 0) "All apps" else "All apps but $excludedApps")
        RowDivider()
        SwitchRow("Pinned", snippet.pinned, { onTogglePin() })
        if (onLockChange != null) {
            RowDivider()
            SwitchRow("Locked", snippet.locked, onLockChange, icon = TablerIcons.Lock)
            if (snippet.locked && onExpandUnlockedChange != null) {
                RowDivider()
                SwitchRow("Expand without unlocking", snippet.expandUnlocked, onExpandUnlockedChange)
                Text(
                    if (snippet.expandUnlocked) "The text stays hidden in Clipsy, but anyone typing the keyword on this phone gets it."
                    else "Typing the keyword shows a lock. Tap it to sign in and insert the text.",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    color = ClipsyColors.TextSecondary,
                    fontSize = ClipsyType.Caption,
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CrossfadeText(value, fontSize = 22.sp, style = TextStyle(fontWeight = FontWeight.SemiBold))
        Text(label, color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
    }
}

/**
 * One bar per day, oldest first. Bars grow from the baseline in a left-to-right wave; today is
 * the bright one and yesterday the middle tone. Used on Home (7 days) and on every snippet's
 * screen (30 days), so usage always reads the same way.
 */
@Composable
fun DailyBars(values: List<Long>, modifier: Modifier = Modifier) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val today = ClipsyColors.Accent
    val yesterday = ClipsyColors.ChartMid
    val rest = ClipsyColors.ChartLow
    val grow = remember(values.size) { values.map { Animatable(0f) } }
    LaunchedEffect(values) {
        // The whole wave takes about the same time however many bars there are.
        val step = 280L / values.size.coerceAtLeast(1)
        grow.forEachIndexed { i, a ->
            launch {
                delay(250L + i * step)
                a.animateTo(1f, Motion.enter(Motion.SLOW + 80))
            }
        }
    }
    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val barW = minOf(10.dp.toPx(), slot * 0.62f)
        val gap = if (n > 1) (size.width - barW * n) / (n - 1) else 0f
        val radius = CornerRadius(minOf(3.dp.toPx(), barW / 2))
        values.forEachIndexed { i, v ->
            val target = (size.height * (v.toFloat() / max)).coerceAtLeast(minOf(6.dp.toPx(), size.height))
            val h = target * grow[i].value
            val color = when (i) {
                n - 1 -> today
                n - 2 -> yesterday
                else -> rest
            }
            drawRoundRect(color, Offset(i * (barW + gap), size.height - h), Size(barW, h), radius)
        }
    }
}
