package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.effectiveBreakAfter
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultBreakTest {
    private val now = Instant.parse("2026-10-20T08:00:00Z")

    private fun setup(defaultBreak: Duration?) = UserSetup.draft(now).copy(defaultBreakAfter = defaultBreak)

    private fun todo(breakAfter: Duration? = null) = Item.newTodo(
        name = "Steuererklärung",
        category = Category.FOKUS,
        priority = Priority.URGENT_MUST,
        targetDate = LocalDate(2026, 10, 20),
        estimatedDuration = 1.hours,
        now = now,
    ).copy(breakAfter = breakAfter)

    @Test
    fun `a ToDo without its own break is placed with the default break`() {
        assertEquals(10.minutes, effectiveBreakAfter(todo(), setup(10.minutes)))
    }

    @Test
    fun `the card's own break beats the default`() {
        assertEquals(5.minutes, effectiveBreakAfter(todo(breakAfter = 5.minutes), setup(10.minutes)))
    }

    @Test
    fun `a standing task never takes the default break`() {
        val standing = Item.newRecurring(
            name = "Sport",
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = LocalTime(17, 0),
            estimatedDuration = 1.hours,
            now = now,
        )
        assertEquals(ItemType.RECURRING, standing.type)
        assertNull(effectiveBreakAfter(standing, setup(10.minutes)))
    }

    @Test
    fun `without a default there is no break`() {
        assertNull(effectiveBreakAfter(todo(), setup(null)))
    }

    @Test
    fun `a default of zero minutes is no default`() {
        assertNull(effectiveBreakAfter(todo(), setup(Duration.ZERO)))
    }

    @Test
    fun `the default lengthens the time a placed ToDo claims, not its duration`() {
        val end = 10 * 60
        val effective = effectiveBreakAfter(todo(), setup(10.minutes))
        assertEquals(end + 10, containerEndMinute(end, null, effective))
    }
}
