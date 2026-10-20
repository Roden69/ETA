package com.example.eta.domain

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.recurrence.isLaidAfterEdit
import com.example.eta.domain.recurrence.isRelayableToday
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditRelayTest {

    /** 14:00 in Berlin on Tuesday 2026-10-20. */
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val nowLocal = LocalDateTime(2026, 10, 20, 14, 0)
    private val today = LocalDate(2026, 10, 20)
    private val tomorrow = LocalDate(2026, 10, 21)
    private val edited = setOf("setup:bedprep")

    private fun block(
        itemId: String = "setup:bedprep",
        date: LocalDate = today,
        start: LocalTime = LocalTime(15, 0),
        completedAt: Instant? = null,
        discardedAt: Instant? = null,
        timerGoal: Duration? = null,
        timerRunningSince: Instant? = null,
    ) = PlannedBlock(
        itemId = itemId,
        date = date,
        start = start,
        plannedDuration = 1.hours,
        origin = BlockOrigin.RECURRING,
        completedAt = completedAt,
        discardedAt = discardedAt,
        timerGoal = timerGoal,
        timerRunningSince = timerRunningSince,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `an open block of today starting after now is relayable`() {
        assertTrue(isRelayableToday(block(start = LocalTime(14, 1)), edited, nowLocal))
    }

    @Test
    fun `a block starting exactly now has begun and is kept`() {
        assertFalse(isRelayableToday(block(start = LocalTime(14, 0)), edited, nowLocal))
    }

    @Test
    fun `a block that started before now is kept`() {
        assertFalse(isRelayableToday(block(start = LocalTime(13, 0)), edited, nowLocal))
    }

    @Test
    fun `a block with a running timer is kept`() {
        val running = block(timerGoal = 1.hours, timerRunningSince = now)
        assertFalse(isRelayableToday(running, edited, nowLocal))
    }

    @Test
    fun `a block whose timer was started and paused is kept`() {
        assertFalse(isRelayableToday(block(timerGoal = 1.hours), edited, nowLocal))
    }

    @Test
    fun `a completed block is kept`() {
        assertFalse(isRelayableToday(block(completedAt = now), edited, nowLocal))
    }

    @Test
    fun `a discarded block is kept`() {
        assertFalse(isRelayableToday(block(discardedAt = now), edited, nowLocal))
    }

    @Test
    fun `a block of a definition the edit does not touch is kept`() {
        assertFalse(isRelayableToday(block(itemId = "setup:morning"), edited, nowLocal))
    }

    @Test
    fun `a block on another day is not relayable by the today rule`() {
        assertFalse(isRelayableToday(block(date = tomorrow), edited, nowLocal))
    }

    @Test
    fun `a new occurrence of today that starts before now is not laid`() {
        assertFalse(isLaidAfterEdit(block(start = LocalTime(7, 0)), edited, nowLocal))
    }

    @Test
    fun `a new occurrence of today starting exactly now or later is laid`() {
        assertTrue(isLaidAfterEdit(block(start = LocalTime(14, 0)), edited, nowLocal))
        assertTrue(isLaidAfterEdit(block(start = LocalTime(14, 1)), edited, nowLocal))
    }

    @Test
    fun `new occurrences on later days and of other definitions are always laid`() {
        assertTrue(isLaidAfterEdit(block(date = tomorrow, start = LocalTime(7, 0)), edited, nowLocal))
        assertTrue(isLaidAfterEdit(block(itemId = "setup:morning", start = LocalTime(7, 0)), edited, nowLocal))
    }
}
