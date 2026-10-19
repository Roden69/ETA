package com.example.eta.domain

import com.example.eta.domain.recurrence.overlapLanes
import org.junit.Assert.assertEquals
import org.junit.Test

/** The lanes the week chart draws overlapping tasks in (#35). */
class WeekLanesTest {

    @Test
    fun `stretches that do not meet share one lane`() {
        val (lanes, count) = overlapLanes(listOf(0..59, 60..119, 200..260))

        assertEquals(listOf(0, 0, 0), lanes)
        assertEquals(1, count)
    }

    @Test
    fun `an overlap takes a second lane beside the first`() {
        val (lanes, count) = overlapLanes(listOf(540..719, 600..660))

        assertEquals(listOf(0, 1), lanes)
        assertEquals(2, count)
    }

    @Test
    fun `a stretch reuses the first lane that has ended before it starts`() {
        // 9:00–11:59 and 10:00–10:59 need two lanes; 11:30–11:59 fits back into
        // the second one, which has been free since 10:59.
        val (lanes, count) = overlapLanes(listOf(540..719, 600..659, 690..719))

        assertEquals(listOf(0, 1, 1), lanes)
        assertEquals(2, count)
    }

    @Test
    fun `touching minutes overlap, the next minute does not`() {
        val (touching, touchingCount) = overlapLanes(listOf(100..200, 200..300))
        val (adjacent, adjacentCount) = overlapLanes(listOf(100..200, 201..300))

        assertEquals(listOf(0, 1), touching)
        assertEquals(2, touchingCount)
        assertEquals(listOf(0, 0), adjacent)
        assertEquals(1, adjacentCount)
    }

    @Test
    fun `an empty day needs no lane`() {
        val (lanes, count) = overlapLanes(emptyList())

        assertEquals(emptyList<Int>(), lanes)
        assertEquals(0, count)
    }
}
