package com.example.eta.domain

import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.EventDecision
import com.example.eta.domain.setup.RoutinePlacement
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.calendarClashes
import com.example.eta.domain.setup.prefilledWeek
import com.example.eta.domain.setup.setupCalendarLastDay
import com.example.eta.domain.setup.setupSpans
import com.example.eta.domain.setup.spans
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCalendarTest {
    private val now = Instant.parse("2026-10-05T08:00:00Z")
    private val monday = LocalDate(2026, 10, 5)

    @Test
    fun `the setup week reads seven days, today included`() {
        assertEquals(LocalDate(2026, 10, 11), setupCalendarLastDay(monday))
    }

    @Test
    fun `a timed appointment occupies the minutes of its own weekday`() {
        val spans = event("Zahnarzt", monday, LocalTime(9, 30), 45).setupSpans()

        assertEquals(1, spans.size)
        assertEquals(DayOfWeek.MONDAY, spans.single().weekday)
        assertEquals(570, spans.single().fromMinute)
        assertEquals(615, spans.single().toMinute)
    }

    @Test
    fun `an appointment running past midnight continues on the next weekday`() {
        val sunday = LocalDate(2026, 10, 11)
        val spans = event("Feier", sunday, LocalTime(23, 0), 90).setupSpans()

        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), spans.map { it.weekday })
        assertEquals(listOf(1380, 0), spans.map { it.fromMinute })
        assertEquals(listOf(1440, 30), spans.map { it.toMinute })
    }

    @Test
    fun `an all-day event occupies no hour`() {
        assertTrue(event("Geburtstag", monday, LocalTime(0, 0), 0, allDay = true).setupSpans().isEmpty())
    }

    @Test
    fun `a routine over an appointment on the same weekday and minutes is a clash`() {
        val draft = withSportAt(DayOfWeek.MONDAY, LocalTime(7, 0))
        val clashes = draft.calendarClashes(listOf(event("Zahnarzt", monday, LocalTime(7, 30), 60)))

        assertEquals(1, clashes.size)
        assertEquals(DayOfWeek.MONDAY, clashes.single().weekday)
        assertEquals("Sport", clashes.single().routine.label)
        assertEquals("Zahnarzt", clashes.single().appointment.label)
        assertEquals(450, clashes.single().fromMinute)
        assertEquals(480, clashes.single().toMinute)
    }

    @Test
    fun `touching a routine at its end is not a clash`() {
        val draft = withSportAt(DayOfWeek.MONDAY, LocalTime(7, 0))

        assertTrue(draft.calendarClashes(listOf(event("Termin", monday, LocalTime(8, 0), 30))).isEmpty())
    }

    @Test
    fun `the same minutes on another weekday are not a clash`() {
        val draft = withSportAt(DayOfWeek.MONDAY, LocalTime(7, 0))
        val tuesday = LocalDate(2026, 10, 6)

        assertTrue(draft.calendarClashes(listOf(event("Termin", tuesday, LocalTime(7, 30), 30))).isEmpty())
    }

    @Test
    fun `an all-day event is never a clash`() {
        val draft = withSportAt(DayOfWeek.MONDAY, LocalTime(7, 0))

        assertTrue(draft.calendarClashes(listOf(event("Urlaub", monday, LocalTime(0, 0), 0, allDay = true))).isEmpty())
    }

    @Test
    fun `a prefilled routine takes the first hour the appointments leave free`() {
        val draft = RoutineSetup.draft(now).addRoutine("Sport")
        val events = listOf(event("Meeting", monday, LocalTime(7, 0), 180))

        val week = draft.prefilledWeek(events, alreadyPrefilled = emptySet())
        val sport = week.draft.placements.single()

        assertEquals(setOf("sport"), week.routineIds)
        assertEquals(DayOfWeek.MONDAY, sport.weekday)
        assertEquals(LocalTime(10, 0), sport.start)
        assertEquals(1.hours, sport.duration)
    }

    @Test
    fun `prefilled routines never overlap each other`() {
        val draft = RoutineSetup.draft(now).addRoutine("Sport").addRoutine("Achtsamkeit")

        val week = draft.prefilledWeek(emptyList(), alreadyPrefilled = emptySet())
        val spans = week.draft.placements.flatMap { placement ->
            placement.spans(week.draft.routines.single { it.id == placement.routineId })
        }

        assertEquals(setOf("sport", "mindfulness"), week.routineIds)
        assertEquals(2, spans.size)
        assertFalse(spans[0].overlaps(spans[1]))
    }

    @Test
    fun `prefilling starts after the sleep of the night`() {
        val draft = RoutineSetup.draft(now).addRoutine("Sport")

        val sport = draft.prefilledWeek(emptyList(), alreadyPrefilled = emptySet()).draft.placements.single()

        // The Monday night ends at 07:00, so the first free hour is 07:00 itself.
        assertEquals(DayOfWeek.MONDAY, sport.weekday)
        assertEquals(LocalTime(7, 0), sport.start)
    }

    @Test
    fun `a routine that was prefilled before is not given a slot again`() {
        val draft = RoutineSetup.draft(now).addRoutine("Sport").addRoutine("Achtsamkeit")

        val week = draft.prefilledWeek(emptyList(), alreadyPrefilled = setOf("sport"))

        assertEquals(setOf("mindfulness"), week.routineIds)
        assertEquals(listOf("mindfulness"), week.draft.placements.map { it.routineId })
    }

    @Test
    fun `an all-day event does not push a prefilled slot`() {
        val draft = RoutineSetup.draft(now).addRoutine("Sport")
        val events = listOf(event("Urlaub", monday, LocalTime(0, 0), 0, allDay = true))

        val sport = draft.prefilledWeek(events, alreadyPrefilled = emptySet()).draft.placements.single()

        assertEquals(LocalTime(7, 0), sport.start)
    }

    private fun withSportAt(weekday: DayOfWeek, start: LocalTime): RoutineSetup =
        RoutineSetup.draft(now).addRoutine("Sport").withPlacement(
            RoutinePlacement(
                id = "placement",
                routineId = "sport",
                weekday = weekday,
                start = start,
                duration = 1.hours,
            ),
        )

    private fun event(title: String, date: LocalDate, start: LocalTime, minutes: Int, allDay: Boolean = false) =
        CalendarEvent(
            id = CalendarEvent.idOf("primary", title),
            calendarId = "primary",
            eventId = title,
            title = title,
            date = date,
            start = start,
            duration = minutes.minutes,
            allDay = allDay,
            remoteUpdatedAt = null,
            decision = EventDecision.PENDING,
            itemId = null,
            createdAt = now,
            updatedAt = now,
        )
}
