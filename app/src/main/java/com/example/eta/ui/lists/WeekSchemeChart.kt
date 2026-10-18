package com.example.eta.ui.lists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.recurrence.freeSpans
import com.example.eta.domain.recurrence.overlapLanes
import com.example.eta.domain.setup.WEEK
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DayOfWeek

/** One task's stretch on one weekday: the colour it is drawn in, and the name the legend gives it. */
data class ChartBar(
    val range: IntRange,
    val color: Color,
    val label: String,
)

/** One line of a legend: what a colour means in the picture beside it. */
data class LegendEntry(
    val color: Color,
    val label: String,
    /** Drawn with a frame, for a colour that is the background itself: free time. */
    val outlined: Boolean = false,
)

/**
 * The Wochenschema: seven columns, Monday on the left, midnight at the top.
 *
 * **One meaning for each colour, in every caller.** Free time is the plain
 * surface, what is booked (the night included) the quiet grey behind it, a task
 * its own colour, the form being filled in the accent and a collision the danger
 * colour. The legend under the picture names exactly the colours it uses, so a
 * bar can be matched to its task by colour and by the name beside it.
 *
 * [tasks] are the standing tasks themselves, each stretch with its colour and
 * name — the chart over the list of standing tasks. Stretches that overlap on a
 * day sit side by side in lanes rather than covering each other. Without [tasks]
 * (an editor's preview) only what is booked and the form's own mark are drawn.
 *
 * [draft] is what a form being filled in would add, drawn in the accent colour
 * over both, so a recurring task can be moved until it sits in free time. On
 * [clashDays] — the weekdays on which it collides with another standing task —
 * the mark turns red instead: the whole mark rather than the overlapping part,
 * since a quarter of an hour is a sliver at this scale and would not be seen.
 *
 * No hour labels, as asked — it answers "where is the week still free", and a grid
 * of hours would turn a glance into a reading.
 */
@Composable
fun WeekSchemeChart(
    booked: Map<DayOfWeek, List<IntRange>>,
    modifier: Modifier = Modifier,
    draft: Map<DayOfWeek, List<IntRange>> = emptyMap(),
    height: Dp = 140.dp,
    clashDays: Set<DayOfWeek> = emptySet(),
    tasks: Map<DayOfWeek, List<ChartBar>>? = null,
) {
    val bookedColor = EtaTheme.colors.border
    val freeColor = EtaTheme.colors.surface
    val draftColor = EtaTheme.colors.accent
    val clashColor = EtaTheme.colors.danger
    val freeByDay = WEEK.associateWith { freeSpans(booked[it].orEmpty()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                // The picture has no words, so a screen reader gets the one sentence
                // it stands for rather than nothing.
                .semantics { contentDescription = "Wochenschema: freie Zeiten der Woche" },
        ) {
            val gap = 4.dp.toPx()
            val laneGap = 0.5.dp.toPx()
            val columnWidth = (size.width - gap * (WEEK.size - 1)) / WEEK.size
            val perMinute = size.height / MINUTES_PER_DAY
            val corner = CornerRadius(3.dp.toPx())

            fun span(x: Float, width: Float, range: IntRange, color: Color, inset: Float = 0f) {
                val top = range.first * perMinute
                val bottom = (range.last + 1) * perMinute
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x + inset, top),
                    size = Size(width - 2 * inset, (bottom - top).coerceAtLeast(1f)),
                    cornerRadius = corner,
                )
            }

            WEEK.forEachIndexed { index, day ->
                val x = index * (columnWidth + gap)
                drawRoundRect(
                    color = bookedColor,
                    topLeft = Offset(x, 0f),
                    size = Size(columnWidth, size.height),
                    cornerRadius = corner,
                )
                freeByDay.getValue(day).forEach { span(x, columnWidth, it, freeColor) }

                val bars = tasks?.get(day).orEmpty()
                if (bars.isNotEmpty()) {
                    val (lanes, count) = overlapLanes(bars.map { it.range })
                    val laneWidth = columnWidth / count
                    bars.forEachIndexed { i, bar ->
                        span(x + lanes[i] * laneWidth, laneWidth, bar.range, bar.color, inset = laneGap)
                    }
                }

                val markColor = if (day in clashDays) clashColor else draftColor
                draft[day].orEmpty().forEach { span(x, columnWidth, it, markColor, inset = columnWidth * 0.2f) }
            }
        }
        WeekLegend(
            entries = buildList {
                tasks?.values?.flatten()?.map { it.label to it.color }?.distinct()?.forEach { (label, color) ->
                    add(LegendEntry(color, label))
                }
                add(LegendEntry(freeColor, "Frei", outlined = true))
                if (booked.values.any { it.isNotEmpty() }) add(LegendEntry(bookedColor, "Belegt"))
                if (draft.values.any { it.isNotEmpty() }) add(LegendEntry(draftColor, "Diese Aufgabe"))
                if (clashDays.isNotEmpty()) add(LegendEntry(clashColor, "Überschneidung"))
            },
        )
    }
}

/**
 * A legend for colours that stand for something in a picture: a swatch and what it
 * means, two to a line. Its entries are the caller's, so it names what is drawn.
 */
@Composable
fun WeekLegend(entries: List<LegendEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = EtaTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
            ) {
                pair.forEach { entry -> LegendItem(entry, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendItem(entry: LegendEntry, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(entry.color, EtaTheme.shapes.small)
                .then(
                    if (entry.outlined) {
                        Modifier.border(1.dp, EtaTheme.colors.border, EtaTheme.shapes.small)
                    } else {
                        Modifier
                    },
                ),
        )
        Spacer(Modifier.size(EtaTheme.spacing.sm))
        EtaText(
            text = entry.label,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
