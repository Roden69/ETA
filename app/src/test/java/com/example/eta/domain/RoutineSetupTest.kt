package com.example.eta.domain

import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.recurrence.expandRecurring
import com.example.eta.domain.setup.RoutinePlacement
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.SLEEP_ROUTINE_ID
import com.example.eta.domain.setup.SUGGESTED_SETUP_ROUTINES
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.longestAwakeMinutes
import com.example.eta.domain.setup.recurringItems
import com.example.eta.domain.setup.spans
import com.example.eta.domain.setup.wakeTimeOn
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSetupTest {
    private val now = Instant.parse("2026-10-05T08:00:00Z")

    @Test
    fun `same routine slots expand independently on the same date`() {
        val selected = RoutineSetup.draft(now).addRoutine("Sport")
        val routine = selected.routines.single { it.id == "sport" }
        val mondayMorning = selected.newPlacement(DayOfWeek.MONDAY, LocalTime(9, 0))!!.copy(
            routineId = routine.id,
            duration = 1.hours,
        )
        val mondayEvening = selected.newPlacement(DayOfWeek.MONDAY, LocalTime(18, 0))!!.copy(
            routineId = routine.id,
            duration = 1.hours,
        )
        val scheduled = selected.withPlacement(mondayMorning).withPlacement(mondayEvening)
        val definitions = scheduled.recurringItems(now)
        val blocks = expandRecurring(
            definitions = definitions,
            from = LocalDate(2026, 10, 5),
            to = LocalDate(2026, 10, 5),
            existing = emptySet(),
            now = now,
        )

        assertEquals(setOf("setup:routine-${mondayMorning.id}", "setup:routine-${mondayEvening.id}"), definitions.map { it.id }.toSet())
        assertEquals(setOf(LocalTime(9, 0), LocalTime(18, 0)), definitions.map { it.startTime }.toSet())
        assertEquals(2, blocks.size)
        assertEquals(2, blocks.map { it.itemId }.toSet().size)
        assertTrue(definitions.all { it.recurrenceRule == RecurrenceRule.Weekly(DayOfWeek.MONDAY) })
    }

    @Test
    fun `overnight placement wraps from sunday into monday`() {
        val routine = SUGGESTED_SETUP_ROUTINES.first()
        val placement = RoutinePlacement(
            id = "overnight",
            routineId = routine.id,
            weekday = DayOfWeek.SUNDAY,
            start = LocalTime(23, 30),
            duration = 2.hours,
        )
        assertEquals(
            listOf(DayOfWeek.SUNDAY to (1410 to 1440), DayOfWeek.MONDAY to (0 to 90)),
            placement.spans(routine).map { it.weekday to (it.fromMinute to it.toMinute) },
        )
    }

    @Test
    fun `deselection prunes its placements`() {
        val selected = RoutineSetup.draft(now).addRoutine("Sport")
        val placement = selected.newPlacement(DayOfWeek.TUESDAY, LocalTime(17, 0))!!
        val scheduled = selected.withPlacement(placement)
        val deselected = scheduled.removeRoutine("sport")

        assertEquals(listOf("Sport"), selected.unscheduledRoutines.map { it.name })
        assertTrue(deselected.placements.isEmpty())
        assertFalse(deselected.routines.any { it.id == "sport" })
    }

    @Test
    fun `overlapping placements of one routine remain conflicts and stable ids survive edits`() {
        val selected = RoutineSetup.draft(now).addRoutine("Sport")
        val first = selected.newPlacement(DayOfWeek.WEDNESDAY, LocalTime(17, 0))!!
        val second = first.copy(id = "second", start = LocalTime(17, 30))
        val scheduled = selected.withPlacement(first).withPlacement(second)
        val edited = scheduled.withPlacement(first.copy(start = LocalTime(18, 0), duration = 2.hours))

        assertEquals(1, scheduled.conflicts().size)
        assertEquals(setOf("setup:routine-${first.id}", "setup:routine-${second.id}"), edited.recurringItems(now).map { it.id }.toSet())
        assertEquals(LocalTime(18, 0), edited.recurringItems(now).single { it.id == "setup:routine-${first.id}" }.startTime)
        assertEquals(2, edited.placements.size)
        assertEquals(1, edited.withoutPlacement(second.id).placements.size)
    }

    @Test
    fun `sleep is protected and never emitted as a task`() {
        val draft = RoutineSetup.draft(now)
        assertEquals(draft, draft.removeRoutine(SLEEP_ROUTINE_ID))
        assertTrue(draft.recurringItems(now).isEmpty())
        assertTrue(draft.setup.recurringItems(now).isEmpty())
    }

    @Test
    fun `the first page lays one provisional night on every weekday`() {
        val draft = RoutineSetup.draft(now)
            .withWeekdayNight(LocalTime(22, 30), LocalTime(6, 30))
            .withWeekendNight(LocalTime(1, 0), LocalTime(10, 0))
        val nights = draft.sleepNights().associateBy { it.wakeDay }

        assertEquals(WEEK.toSet(), nights.keys)
        // Monday is woken from Sunday evening; Saturday goes to bed after midnight, on itself.
        assertEquals(DayOfWeek.SUNDAY, nights.getValue(DayOfWeek.MONDAY).startDay)
        assertEquals(LocalTime(22, 30), nights.getValue(DayOfWeek.MONDAY).start)
        assertEquals(8.hours, nights.getValue(DayOfWeek.MONDAY).duration)
        assertEquals(DayOfWeek.SATURDAY, nights.getValue(DayOfWeek.SATURDAY).startDay)
        assertEquals(9.hours, nights.getValue(DayOfWeek.SUNDAY).duration)
        assertTrue(draft.overlappingSleep().isEmpty())
    }

    @Test
    fun `one night can be corrected on its own and is no correction once put back`() {
        val corrected = RoutineSetup.draft(now)
            .withNight(DayOfWeek.TUESDAY, LocalTime(21, 0), LocalTime(5, 0))

        assertEquals(LocalTime(5, 0), corrected.setup.wakeTimeOn(DayOfWeek.TUESDAY))
        assertEquals(LocalTime(7, 0), corrected.setup.wakeTimeOn(DayOfWeek.WEDNESDAY))

        // A later change of the pattern carries the other nights along, not the corrected one.
        val moved = corrected.withWeekdayNight(LocalTime(22, 0), LocalTime(6, 0))
        assertEquals(LocalTime(5, 0), moved.setup.wakeTimeOn(DayOfWeek.TUESDAY))
        assertEquals(LocalTime(6, 0), moved.setup.wakeTimeOn(DayOfWeek.WEDNESDAY))

        val restored = moved.withNight(DayOfWeek.TUESDAY, LocalTime(22, 0), LocalTime(6, 0))
        assertTrue(restored.setup.nightOverrides.isEmpty())
    }

    @Test
    fun `a night that runs into its neighbour is reported, as sleep and not as a routine clash`() {
        assertTrue(RoutineSetup.draft(now).overlappingSleep().isEmpty())

        // Monday's night shortened to 23:00–23:30 on Monday itself, where Tuesday's begins.
        val draft = RoutineSetup.draft(now).withNight(DayOfWeek.MONDAY, LocalTime(23, 0), LocalTime(23, 30))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), draft.overlappingSleep())
        assertTrue(draft.conflicts().isEmpty())
    }

    @Test
    fun `nothing can be placed until a routine besides sleep is chosen`() {
        val draft = RoutineSetup.draft(now)
        assertNull(draft.newPlacement(DayOfWeek.MONDAY, LocalTime(9, 0)))
        assertNotNull(draft.addRoutine("Yoga machen").newPlacement(DayOfWeek.MONDAY, LocalTime(9, 0)))
    }

    @Test
    fun `a provisional night per day can still leave more than 24 hours awake`() {
        val draft = RoutineSetup.draft(now)
            .withWeekendNight(LocalTime(23, 0), LocalTime(7, 0))
            .withNight(DayOfWeek.TUESDAY, LocalTime(1, 0), LocalTime(9, 0))
            .withNight(DayOfWeek.WEDNESDAY, LocalTime(22, 0), LocalTime(23, 0))

        assertTrue(draft.overlappingSleep().isEmpty())
        assertEquals(37 * 60, draft.setup.longestAwakeMinutes())
    }

    @Test
    fun `the 24 hour sleep rule includes the repeating week boundary`() {
        val draft = RoutineSetup.draft(now)
            .withWeekendNight(LocalTime(23, 0), LocalTime(7, 0))
            .withNight(DayOfWeek.SUNDAY, LocalTime(1, 0), LocalTime(9, 0))
            .withNight(DayOfWeek.MONDAY, LocalTime(9, 0), LocalTime(10, 0))

        assertEquals(24 * 60, draft.setup.longestAwakeMinutes())
        assertEquals(
            24 * 60 + 5,
            draft.withNight(DayOfWeek.MONDAY, LocalTime(9, 5), LocalTime(10, 0)).setup.longestAwakeMinutes(),
        )
    }
}
