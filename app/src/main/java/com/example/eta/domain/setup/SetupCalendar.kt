package com.example.eta.domain.setup

import com.example.eta.domain.model.CalendarEvent
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** The setup week reads the calendar for this many days: today and the six after it. */
const val SETUP_CALENDAR_DAYS = 7

/** The last day the setup week reads, counting [today] as the first. */
fun setupCalendarLastDay(today: LocalDate): LocalDate =
    today.plus(DatePeriod(days = SETUP_CALENDAR_DAYS - 1))

/**
 * An appointment as weekday spans, the same shape the routines have, so it can be
 * compared with them. An all-day event has no hour to occupy and yields nothing;
 * the setup week shows it as a band at the top of its day instead.
 */
fun CalendarEvent.setupSpans(): List<SetupSpan> {
    if (allDay) return emptyList()
    return buildList { addSpan(title, date.dayOfWeek, start, duration) }
}

/** A placed routine that shares minutes with a visible appointment on the same weekday. */
data class CalendarClash(val routine: SetupSpan, val appointment: SetupSpan) {
    val weekday: DayOfWeek get() = routine.weekday

    /** The minutes both occupy; the warning names this stretch. */
    val fromMinute: Int get() = maxOf(routine.fromMinute, appointment.fromMinute)
    val toMinute: Int get() = minOf(routine.toMinute, appointment.toMinute)
}

/**
 * Routine placements over the visible appointments of the setup week.
 *
 * Only ever a warning: a meeting marked as free may well sit over a Sport slot the
 * user wants anyway. Sleep is not part of this check, since nights have their own
 * rule ([RoutineSetup.overlappingSleep]).
 */
fun RoutineSetup.calendarClashes(events: List<CalendarEvent>): List<CalendarClash> {
    val appointments = events.flatMap { it.setupSpans() }
    val routineSpans = placements.flatMap { placement ->
        routines.firstOrNull { it.id == placement.routineId }?.let { placement.spans(it) }.orEmpty()
    }
    return routineSpans.flatMap { routine ->
        appointments.filter { routine.overlaps(it) }.map { CalendarClash(routine, it) }
    }
}

/** The week template with its first routine slots filled in; see [RoutineSetup.prefilledWeek]. */
data class PrefilledWeek(
    val draft: RoutineSetup,
    /** The routines that were given a slot by this call. */
    val routineIds: Set<String>,
)

/** The hours a prefilled slot may start in: 07:00 through 22:00, so each ends by midnight. */
private const val PREFILL_FIRST_HOUR = 7
private const val PREFILL_LAST_HOUR = 22

/**
 * Starts the week template: every chosen routine that has never been placed gets
 * one hour in the first hour that is free of sleep, of other placements and of
 * the visible [events], counting the week from Monday. The user edits or removes
 * it like any placement.
 *
 * [alreadyPrefilled] are the routines that got their slot earlier. They are left
 * alone, so a slot the user deleted does not come back when the step is reopened.
 * A routine without any free hour stays unplaced and shows up as unscheduled.
 */
fun RoutineSetup.prefilledWeek(events: List<CalendarEvent>, alreadyPrefilled: Set<String>): PrefilledWeek {
    var current = this
    val appointments = events.flatMap { it.setupSpans() }
    val given = mutableSetOf<String>()
    for (routine in placeableRoutines) {
        if (routine.id in alreadyPrefilled || current.placements.any { it.routineId == routine.id }) continue
        val (weekday, start) = current.weeklySpans().plus(appointments).firstFreeHour() ?: continue
        current = current.withPlacement(
            RoutinePlacement(
                id = UUID.randomUUID().toString(),
                routineId = routine.id,
                weekday = weekday,
                start = start,
                duration = 1.hours,
            ),
        )
        given += routine.id
    }
    return PrefilledWeek(current, given)
}

private fun List<SetupSpan>.firstFreeHour(): Pair<DayOfWeek, LocalTime>? {
    for (weekday in WEEK) {
        for (hour in PREFILL_FIRST_HOUR..PREFILL_LAST_HOUR) {
            val candidate = SetupSpan("", weekday, hour * 60, hour * 60 + 60)
            if (none { it.overlaps(candidate) }) return weekday to LocalTime(hour, 0)
        }
    }
    return null
}
