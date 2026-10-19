package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.calendar.pendingClashes
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.EventDecision
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarClashesTest {

    private val now = Instant.parse("2026-10-18T06:00:00Z")
    private val date = LocalDate(2026, 10, 19)

    private fun entry(
        id: String,
        start: LocalTime,
        duration: Duration = 1.hours,
        travelBefore: Duration? = null,
        onDate: LocalDate = date,
        completed: Boolean = false,
        discarded: Boolean = false,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = id,
            itemId = "item-$id",
            date = onDate,
            start = start,
            plannedDuration = duration,
            travelBefore = travelBefore,
            origin = BlockOrigin.RECURRING,
            completedAt = if (completed) now else null,
            discardedAt = if (discarded) now else null,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$id",
            type = ItemType.RECURRING,
            name = id,
            stage = Stage.DAY,
            category = Category.FOKUS,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private fun event(
        id: String,
        start: LocalTime,
        duration: Duration = 1.hours,
        decision: EventDecision = EventDecision.PENDING,
        onDate: LocalDate = date,
    ) = CalendarEvent(
        id = id,
        calendarId = "cal",
        eventId = id,
        title = id,
        date = onDate,
        start = start,
        duration = duration,
        allDay = false,
        remoteUpdatedAt = null,
        decision = decision,
        itemId = null,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a pending appointment that overlaps a planned block is reported with that block`() {
        val sport = entry("sport", LocalTime(12, 0), 1.hours)
        val clashes = pendingClashes(
            events = listOf(event("arzt", LocalTime(12, 30), 1.hours)),
            blocks = listOf(sport),
        )
        assertEquals(listOf("sport"), clashes.getValue("arzt").map { it.item.name })
    }

    @Test
    fun `touching at the edge is not a clash`() {
        val morning = entry("morning", LocalTime(10, 0), 2.hours)
        val clashes = pendingClashes(
            events = listOf(event("arzt", LocalTime(12, 0), 1.hours)),
            blocks = listOf(morning),
        )
        assertTrue(clashes.isEmpty())
    }

    @Test
    fun `the travel in front of a block counts, because the block holds that time`() {
        // The block itself starts at 12:30, but its journey starts at 12:00.
        val drive = entry("drive", LocalTime(12, 30), 1.hours, travelBefore = 30.minutes)
        val clashes = pendingClashes(
            events = listOf(event("arzt", LocalTime(11, 45), 30.minutes)),
            blocks = listOf(drive),
        )
        assertEquals(setOf("arzt"), clashes.keys)
    }

    @Test
    fun `a finished or called-off block holds no time and cannot clash`() {
        val done = entry("done", LocalTime(12, 0), 1.hours, completed = true)
        val dropped = entry("dropped", LocalTime(12, 0), 1.hours, discarded = true)
        val clashes = pendingClashes(
            events = listOf(event("arzt", LocalTime(12, 0), 1.hours)),
            blocks = listOf(done, dropped),
        )
        assertTrue(clashes.isEmpty())
    }

    @Test
    fun `a block on another day cannot clash with the appointment`() {
        val tomorrow = entry("tomorrow", LocalTime(12, 0), 1.hours, onDate = LocalDate(2026, 10, 20))
        val clashes = pendingClashes(
            events = listOf(event("arzt", LocalTime(12, 0), 1.hours)),
            blocks = listOf(tomorrow),
        )
        assertTrue(clashes.isEmpty())
    }

    @Test
    fun `an appointment that is already decided is not asked about again`() {
        val sport = entry("sport", LocalTime(12, 0), 1.hours)
        val clashes = pendingClashes(
            events = listOf(event("arzt", LocalTime(12, 0), 1.hours, decision = EventDecision.IGNORED)),
            blocks = listOf(sport),
        )
        assertTrue(clashes.isEmpty())
    }

    @Test
    fun `several blocks under one appointment come back in the order of the day`() {
        val late = entry("late", LocalTime(13, 0), 30.minutes)
        val early = entry("early", LocalTime(12, 0), 30.minutes)
        val clashes = pendingClashes(
            events = listOf(event("langer-termin", LocalTime(11, 30), 3.hours)),
            blocks = listOf(late, early),
        )
        assertEquals(listOf("early", "late"), clashes.getValue("langer-termin").map { it.item.name })
    }

    @Test
    fun `only the appointments that clash are in the result`() {
        val sport = entry("sport", LocalTime(12, 0), 1.hours)
        val clashes = pendingClashes(
            events = listOf(
                event("kollidiert", LocalTime(12, 0), 1.hours),
                event("frei", LocalTime(18, 0), 1.hours),
            ),
            blocks = listOf(sport),
        )
        assertEquals(setOf("kollidiert"), clashes.keys)
    }
}
