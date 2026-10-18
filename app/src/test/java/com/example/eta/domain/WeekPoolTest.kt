package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PullBlock
import com.example.eta.domain.planning.pullBlockOf
import com.example.eta.domain.planning.weekPool
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPoolTest {

    private val now = Instant.parse("2026-10-18T10:00:00Z")
    private val weekEnd = LocalDate(2026, 10, 25)

    private fun todo(name: String, unlock: LocalDate? = null) = Item.newTodo(
        name = name,
        category = Category.FOKUS,
        priority = Priority.MUST,
        targetDate = unlock,
        estimatedDuration = 1.hours,
        now = now,
    )

    @Test
    fun `a card unlocking on the last day of the week can still be pulled`() {
        assertNull(pullBlockOf(todo("Wäsche", unlock = weekEnd), weekEnd, openGoals = 0))
    }

    @Test
    fun `a card unlocking the day after the week ends is held back with its date`() {
        val later = LocalDate(2026, 10, 26)
        assertEquals(
            PullBlock.NotYetAvailable(later),
            pullBlockOf(todo("Vegane Messe", unlock = later), weekEnd, openGoals = 0),
        )
    }

    @Test
    fun `open goals of an earlier cycle hold back every card, with their count`() {
        assertEquals(PullBlock.OpenGoals(2), pullBlockOf(todo("Saugen"), weekEnd, openGoals = 2))
    }

    @Test
    fun `an unlock date that has already passed is no reason to hold a card back`() {
        assertNull(pullBlockOf(todo("Saugen", unlock = LocalDate(2026, 10, 1)), weekEnd, openGoals = 0))
    }

    @Test
    fun `the card's own unlock date is named before the week's open goals`() {
        val later = LocalDate(2026, 11, 13)
        assertEquals(
            PullBlock.NotYetAvailable(later),
            pullBlockOf(todo("Orthopädie", unlock = later), weekEnd, openGoals = 3),
        )
    }

    @Test
    fun `the pool keeps every ToDo visible, pullable or held back with reasons`() {
        val open = todo("Oma besuchen")
        val later = todo("NLR Prüfung", unlock = LocalDate(2026, 11, 13))
        val free = todo("Wischen")
        val pool = weekPool(listOf(open, later, free), weekEnd, openGoals = 0)

        assertEquals(listOf(open, free), pool.pullable)
        assertEquals(1, pool.held.size)
        assertEquals(later, pool.held.single().first)
        assertTrue(pool.held.single().second is PullBlock.NotYetAvailable)
    }

    @Test
    fun `with open goals nothing is pullable, and every ToDo says why`() {
        val pool = weekPool(listOf(todo("A"), todo("B")), weekEnd, openGoals = 1)

        assertTrue(pool.pullable.isEmpty())
        assertEquals(listOf(PullBlock.OpenGoals(1), PullBlock.OpenGoals(1)), pool.held.map { it.second })
    }

    @Test
    fun `recurring notes are not week goals and are left out of the pool`() {
        val note = Item.newRecurring(
            name = "Achtsamkeit",
            category = Category.ACHTSAM,
            recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.MONDAY),
            startTime = LocalTime(8, 0),
            estimatedDuration = 10.minutes,
            now = now,
        ).copy(stage = Stage.COLLECTION)

        val pool = weekPool(listOf(note), weekEnd, openGoals = 0)

        assertTrue(pool.pullable.isEmpty())
        assertTrue(pool.held.isEmpty())
    }
}
