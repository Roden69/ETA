package com.example.eta.ui.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.eta.domain.setup.RoutinePlacement
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.SetupRoutine
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.SleepNight
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.longestAwakeMinutes
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.domain.setup.patternNightEndingOn
import com.example.eta.domain.setup.spans
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.components.EtaWeekdayPicker
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.colorOf
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

private val DAY_COLUMN_WIDTH = 88.dp
private val HOUR_HEIGHT = 56.dp
private const val MINUTES_PER_DAY = 24 * 60
private val CALENDAR_HEIGHT = HOUR_HEIGHT * 24

/** Interactive weekly appointment calendar used on the second setup page. */
@Composable
internal fun SetupWeekStep(
    draft: RoutineSetup,
    onSavePlacement: (RoutinePlacement) -> Unit,
    onDeletePlacement: (String) -> Unit,
    onNightChange: (DayOfWeek, LocalTime, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<PlacementEdit?>(null) }
    var pendingNew by remember { mutableStateOf<RoutinePlacement?>(null) }
    var editingNight by remember { mutableStateOf<DayOfWeek?>(null) }
    val horizontal = rememberScrollState()
    val canPlace = draft.placeableRoutines.isNotEmpty()
    val createPlacement by rememberUpdatedState<(DayOfWeek, LocalTime) -> Unit>(
        fun(day: DayOfWeek, time: LocalTime) {
            // Nothing to place until a routine other than sleep was chosen.
            val created = draft.newPlacement(day, time) ?: return
            pendingNew = created
            editing = PlacementEdit(created, isNew = true)
        },
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        EtaText(
            text = if (canPlace) {
                "Tippe in den Kalender, um einen Termin einzutragen. Neue Termine dauern eine Stunde."
            } else {
                "Du hast keine weitere Routine gewählt. Geh zurück, um Sport, Achtsamkeit oder " +
                    "eine eigene Routine zu wählen — oder schließe das Setup mit deinen Nächten ab."
            },
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaButton(
            text = "Zeitplatz hinzufügen",
            style = EtaButtonStyle.Secondary,
            enabled = canPlace,
            onClick = { createPlacement(DayOfWeek.MONDAY, LocalTime(9, 0)) },
            modifier = Modifier.semantics { contentDescription = "Zeitplatz hinzufügen" },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(44.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(horizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WEEK.forEach { day ->
                    Box(
                        Modifier.width(DAY_COLUMN_WIDTH).padding(vertical = EtaTheme.spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        EtaText(day.formatLong(), style = EtaTheme.typography.label)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState(
                        initial = with(LocalDensity.current) { (HOUR_HEIGHT * 6).roundToPx() },
                    ),
                ),
        ) {
            Row {
                Column(
                    Modifier.width(44.dp).height(CALENDAR_HEIGHT),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    (0..24).forEach { hour ->
                        EtaText(
                            text = hour.toString().padStart(2, '0'),
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textMuted,
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    Row(Modifier.horizontalScroll(horizontal)) {
                        WeekGrid(
                            draft = draft,
                            pending = pendingNew,
                            onCreate = createPlacement,
                            onEdit = { placement ->
                                pendingNew = null
                                editing = PlacementEdit(placement, isNew = false)
                            },
                            onEditNight = { editingNight = it },
                        )
                    }
                }
            }
        }
        EtaText(
            text = "Jede Nacht ist vorläufig nach deinen Schlafzeiten eingetragen. Tippe auf einen " +
                "Schlafblock, um genau diese Nacht anzupassen.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )
        val conflicts = pendingNew?.let { draft.withPlacement(it).conflicts() } ?: draft.conflicts()
        if (conflicts.isNotEmpty()) {
            val shown = conflicts.take(2).joinToString("\n") { conflict ->
                "Überschneidung am ${conflict.weekday.formatLong()}: ${conflict.first.label} · ${conflict.second.label}"
            }
            val extra = conflicts.size - 2
            EtaText(
                text = shown + if (extra > 0) "\nund $extra weitere Überschneidungen" else "",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }
    }

    editing?.let { edit ->
        PlacementEditor(
            edit = edit,
            routines = draft.placeableRoutines,
            onDismiss = {
                if (edit.isNew) pendingNew = null
                editing = null
            },
            onSave = { placement ->
                onSavePlacement(placement)
                pendingNew = null
                editing = null
            },
            onDelete = {
                onDeletePlacement(edit.placement.id)
                pendingNew = null
                editing = null
            },
        )
    }

    editingNight?.let { wakeDay ->
        SleepEditor(
            draft = draft,
            wakeDay = wakeDay,
            onDismiss = { editingNight = null },
            onSave = { sleep, wake ->
                onNightChange(wakeDay, sleep, wake)
                editingNight = null
            },
        )
    }
}

private data class PlacementEdit(val placement: RoutinePlacement, val isNew: Boolean)
private data class Segment(
    val placement: RoutinePlacement,
    val routine: SetupRoutine,
    val weekday: DayOfWeek,
    val from: Int,
    val to: Int,
)
private data class LaneSegment(val segment: Segment, val lane: Int, val laneCount: Int)

@Composable
private fun WeekGrid(
    draft: RoutineSetup,
    pending: RoutinePlacement?,
    onCreate: (DayOfWeek, LocalTime) -> Unit,
    onEdit: (RoutinePlacement) -> Unit,
    onEditNight: (DayOfWeek) -> Unit,
) {
    val latestCreate by rememberUpdatedState(onCreate)
    val allPlacements = draft.placements + listOfNotNull(pending)
    BoxWithConstraints(Modifier.width(DAY_COLUMN_WIDTH * WEEK.size).height(CALENDAR_HEIGHT)) {
        val dayWidth = maxWidth / WEEK.size
        val gridColor = EtaTheme.colors.border
        Canvas(Modifier.matchParentSize()) {
            val hourPixels = HOUR_HEIGHT.toPx()
            val columnPixels = size.width / WEEK.size
            for (hour in 0..24) {
                val y = hour * hourPixels
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
            }
            WEEK.indices.forEach { index ->
                val x = index * columnPixels
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
            }
        }
        WEEK.forEach { day ->
            Box(
                modifier = Modifier
                    .offset(x = dayWidth * day.ordinal)
                    .width(dayWidth)
                    .height(CALENDAR_HEIGHT)
                    .pointerInput(day) {
                        detectTapGestures { position ->
                            val minute = (position.y / HOUR_HEIGHT.toPx() * 60).toInt()
                            val snapped = ((minute + 7) / 15 * 15).coerceIn(0, MINUTES_PER_DAY - 15)
                            latestCreate(day, LocalTime(snapped / 60, snapped % 60))
                        }
                    }
                    .semantics {
                        contentDescription = "${day.formatLong()}: Zeitplatz hinzufügen"
                        onClick(label = "Zeitplatz hinzufügen") {
                            latestCreate(day, LocalTime(9, 0))
                            true
                        }
                    },
            )
        }
        draft.sleepNights().forEach { sleepNight ->
            sleepNight.spans().forEach { span ->
                CalendarBlock(
                    label = "Schlafen",
                    weekday = span.weekday,
                    startMinute = span.fromMinute,
                    durationMinutes = span.toMinute - span.fromMinute,
                    width = dayWidth,
                    lane = 0,
                    laneCount = 1,
                    color = EtaTheme.colors.sleep,
                    description = "Schlafen, ${span.weekday.formatLong()} ${minuteClock(span.fromMinute)} " +
                        "bis ${minuteClock(span.toMinute % MINUTES_PER_DAY)}; Nacht zum " +
                        "${sleepNight.wakeDay.formatLong()}, antippen zum Ändern",
                    onClick = { onEditNight(sleepNight.wakeDay) },
                )
            }
        }
        segments(allPlacements, draft.routines).forEach { layout ->
            val segment = layout.segment
            CalendarBlock(
                label = segment.routine.name,
                weekday = segment.weekday,
                startMinute = segment.from,
                durationMinutes = segment.to - segment.from,
                width = dayWidth,
                lane = layout.lane,
                laneCount = layout.laneCount,
                color = colorOf(segment.routine.category),
                description = "${segment.routine.name}, ${segment.weekday.formatLong()} ${minuteClock(segment.from)} bis ${minuteClock(segment.to % MINUTES_PER_DAY)}",
                onClick = { onEdit(segment.placement) },
            )
        }
    }
}

private fun segments(placements: List<RoutinePlacement>, routines: List<SetupRoutine>): List<LaneSegment> {
    val byDay = placements.flatMap { placement ->
        val routine = routines.firstOrNull { it.id == placement.routineId } ?: return@flatMap emptyList()
        placement.spans(routine).map { span ->
            Segment(placement, routine, span.weekday, span.fromMinute, span.toMinute)
        }
    }.groupBy { it.weekday }
    return byDay.values.flatMap { daySegments ->
        val laneEnds = mutableListOf<Int>()
        val assigned = daySegments.sortedWith(compareBy(Segment::from, Segment::to)).map { segment ->
            val lane = laneEnds.indexOfFirst { it <= segment.from }.let { if (it < 0) laneEnds.size else it }
            if (lane == laneEnds.size) laneEnds += segment.to else laneEnds[lane] = segment.to
            segment to lane
        }
        val laneCount = laneEnds.size
        assigned.map { (segment, lane) -> LaneSegment(segment, lane, laneCount) }
    }
}

@Composable
private fun CalendarBlock(
    label: String,
    weekday: DayOfWeek,
    startMinute: Int,
    durationMinutes: Int,
    width: Dp,
    lane: Int,
    laneCount: Int,
    color: Color,
    description: String,
    onClick: () -> Unit,
) {
    val laneWidth = width / laneCount
    val top = HOUR_HEIGHT * (startMinute / 60f)
    val height = (HOUR_HEIGHT * durationMinutes / 60f).coerceAtLeast(18.dp)
    Box(
        modifier = Modifier
            .offset(x = width * weekday.ordinal + laneWidth * lane, y = top)
            .width(laneWidth)
            .height(height)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(EtaTheme.shapes.small)
            .background(color.copy(alpha = 0.78f))
            .border(1.dp, color, EtaTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(3.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        EtaText(text = label, style = EtaTheme.typography.caption, color = EtaTheme.colors.textPrimary)
    }
}

@Composable
private fun PlacementEditor(
    edit: PlacementEdit,
    routines: List<SetupRoutine>,
    onDismiss: () -> Unit,
    onSave: (RoutinePlacement) -> Unit,
    onDelete: () -> Unit,
) {
    var placement by remember(edit.placement.id) { mutableStateOf(edit.placement) }
    val selectedRoutine = routines.firstOrNull { it.id == placement.routineId } ?: routines.first()
    val endMinute = placement.start.toSecondOfDay() / 60 + placement.duration.inWholeMinutes.toInt()
    val endTime = LocalTime((endMinute % MINUTES_PER_DAY) / 60, endMinute % 60)
    val nextDay = endMinute >= MINUTES_PER_DAY

    EtaDialog(title = if (edit.isNew) "Zeitplatz" else "Zeitplatz bearbeiten", onDismiss = onDismiss) {
        EtaField(label = "Routine") {
            EtaChoice(
                options = routines.map { it to it.name },
                selected = selectedRoutine,
                onSelect = { placement = placement.copy(routineId = it.id) },
            )
        }
        EtaField(label = "Wochentag") {
            EtaWeekdayPicker(selected = placement.weekday, onSelect = { placement = placement.copy(weekday = it) }, days = WEEK)
        }
        EtaField(label = "Beginn") {
            EtaTimePicker(value = placement.start, onValueChange = { placement = placement.copy(start = it) })
        }
        EtaField(label = "Dauer", hint = "Endet um ${endTime.formatClock()}${if (nextDay) " am Folgetag" else ""}.") {
            EtaDurationPicker(
                value = placement.duration,
                onValueChange = { placement = placement.copy(duration = it.coerceAtMost(23.hours + 45.minutes)) },
                minimum = 15.minutes,
            )
        }
        if (!edit.isNew) {
            EtaButton(text = "Löschen", style = EtaButtonStyle.Secondary, onClick = onDelete, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            EtaButton(text = "Speichern", onClick = { onSave(placement) })
        }
    }
}

/**
 * One night on its own: when this night starts and when it ends. Sleep cannot be
 * deleted — somebody sleeps at least once in 24 hours — and cannot be moved to
 * another day either, since the night belongs to the day it ends on; what changes
 * is the hours. A night that would run into its neighbour is refused, because the
 * hours of both would be counted as slept twice.
 */
@Composable
private fun SleepEditor(
    draft: RoutineSetup,
    wakeDay: DayOfWeek,
    onDismiss: () -> Unit,
    onSave: (sleep: LocalTime, wake: LocalTime) -> Unit,
) {
    val current = draft.setup.nightEndingOn(wakeDay)
    val pattern = draft.setup.patternNightEndingOn(wakeDay)
    var sleep by remember(wakeDay) { mutableStateOf(current.sleep) }
    var wake by remember(wakeDay) { mutableStateOf(current.wake) }
    val same = sleep == wake
    val night = SleepNight(wakeDay, NightTimes(sleep, sleep, wake))
    val candidate = if (same) null else draft.withNight(wakeDay, sleep, wake)
    val overlapping = candidate != null && wakeDay in candidate.overlappingSleep()
    val awakeTooLong = candidate != null && candidate.setup.longestAwakeMinutes() > 24 * 60
    val corrected = current.sleep != pattern.sleep || current.wake != pattern.wake

    EtaDialog(title = "Schlaf bearbeiten", onDismiss = onDismiss) {
        EtaText(text = "Nacht zum ${wakeDay.formatLong()}", style = EtaTheme.typography.heading)
        EtaField(label = "Schlafen gehen") {
            EtaTimePicker(value = sleep, onValueChange = { sleep = it })
        }
        EtaField(label = "Aufstehen") {
            EtaTimePicker(value = wake, onValueChange = { wake = it })
        }
        when {
            same -> EtaText(
                text = "Schlafen gehen und Aufstehen dürfen nicht dieselbe Uhrzeit sein.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )

            overlapping -> EtaText(
                text = "Diese Nacht überschneidet sich mit einer anderen. Wähle andere Zeiten.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )

            awakeTooLong -> EtaText(
                text = "Zwischen zwei Schlafblöcken liegen mehr als 24 Stunden. " +
                    "Passe auch die benachbarten Nächte an, bevor du das Setup abschließt.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )

            else -> EtaText(
                text = "${night.startDay.formatLong()} ${sleep.formatClock()} bis " +
                    "${wakeDay.formatLong()} ${wake.formatClock()} · ${night.duration.formatShort()}",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
        }
        if (corrected) {
            EtaButton(
                text = "Wie die übrigen Tage",
                style = EtaButtonStyle.Secondary,
                onClick = { onSave(pattern.sleep, pattern.wake) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            EtaButton(text = "Speichern", enabled = !same && !overlapping, onClick = { onSave(sleep, wake) })
        }
    }
}

private fun minuteClock(minute: Int): String =
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
