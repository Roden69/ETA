package com.example.eta.domain

import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.recurrence.expandRecurring
import com.example.eta.domain.setup.RoutinePlacement
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.SLEEP_ROUTINE_ID
import com.example.eta.domain.setup.SUGGESTED_SETUP_ROUTINES
import com.example.eta.domain.setup.recurringItems
import com.example.eta.domain.setup.spans
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSetupTest {
    private val now = Instant.parse("2026-10-05T08:00:00Z")

    @Test
    fun `same routine slots expand independently on the same date`() {
        val selected = RoutineSetup.draft(now).addRoutine("Sport")
        val routine = selected.routines.single { it.id == "sport" }
        val mondayMorning = selected.newPlacement(DayOfWeek.MONDAY, LocalTime(9, 0)).copy(
            routineId = routine.id,
            duration = 1.hours,
        )
        val mondayEvening = selected.newPlacement(DayOfWeek.MONDAY, LocalTime(18, 0)).copy(
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
        val placement = selected.newPlacement(DayOfWeek.TUESDAY, LocalTime(17, 0))
        val scheduled = selected.withPlacement(placement)
        val deselected = scheduled.removeRoutine("sport")

        assertEquals(listOf("Sport"), selected.unscheduledRoutines.map { it.name })
        assertTrue(deselected.placements.isEmpty())
        assertFalse(deselected.routines.any { it.id == "sport" })
    }

    @Test
    fun `overlapping placements of one routine remain conflicts and stable ids survive edits`() {
        val selected = RoutineSetup.draft(now).addRoutine("Sport")
        val first = selected.newPlacement(DayOfWeek.WEDNESDAY, LocalTime(17, 0))
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
}
