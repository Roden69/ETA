package com.example.eta.domain

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.recurrence.isLaidAfterEdit
import com.example.eta.domain.recurrence.isPlacedByHand
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

    /** An occurrence as the schedule laid it down: start and length equal the originals. */
    private fun block(
        itemId: String = "setup:bedprep",
        date: LocalDate = today,
        start: LocalTime = LocalTime(15, 0),
        duration: Duration = 1.hours,
        origin: BlockOrigin = BlockOrigin.RECURRING,
        flexible: Boolean = false,
        completedAt: Instant? = null,
        discardedAt: Instant? = null,
        timerGoal: Duration? = null,
        timerRunningSince: Instant? = null,
        originalStart: LocalTime? = start,
        originalDuration: Duration? = duration,
    ) = PlannedBlock(
        itemId = itemId,
        date = date,
        start = start,
        plannedDuration = duration,
        origin = origin,
        flexible = flexible,
        completedAt = completedAt,
        discardedAt = discardedAt,
        timerGoal = timerGoal,
        timerRunningSince = timerRunningSince,
        originalStart = originalStart,
        originalDuration = originalDuration,
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
    fun `a block moved by hand to another start is kept`() {
        val moved = block(start = LocalTime(16, 0), originalStart = LocalTime(15, 0))
        assertTrue(moved.isPlacedByHand())
        assertFalse(isRelayableToday(moved, edited, nowLocal))
    }

    @Test
    fun `a block resized by hand is kept`() {
        val resized = block(duration = 2.hours, originalDuration = 1.hours)
        assertTrue(resized.isPlacedByHand())
        assertFalse(isRelayableToday(resized, edited, nowLocal))
    }

    @Test
    fun `a flexible block made by hand is kept`() {
        val flexible = block(flexible = true)
        assertTrue(flexible.isPlacedByHand())
        assertFalse(isRelayableToday(flexible, edited, nowLocal))
    }

    @Test
    fun `a dragged block is kept`() {
        val dragged = block(origin = BlockOrigin.DRAGGED)
        assertFalse(isRelayableToday(dragged, edited, nowLocal))
    }

    @Test
    fun `a block still at the hour and length it was laid down with is not placed by hand`() {
        val untouched = block(start = LocalTime(15, 0), duration = 1.hours)
        assertFalse(untouched.isPlacedByHand())
        assertTrue(isRelayableToday(untouched, edited, nowLocal))
    }

    @Test
    fun `a block without a recorded original is not counted as moved`() {
        val legacy = block(start = LocalTime(16, 0), originalStart = null, originalDuration = null)
        assertFalse(legacy.isPlacedByHand())
        assertTrue(isRelayableToday(legacy, edited, nowLocal))
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
