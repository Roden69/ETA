package com.example.eta.domain

import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.recurrence.Rhythm
import com.example.eta.domain.recurrence.firstEveryWeeksDate
import com.example.eta.domain.recurrence.matches
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every N-th week, anchored on a date. 2026-10-14 is a Wednesday, 2026-10-18 a Sunday.
 */
class EveryWeeksRuleTest {

    private val wednesday = LocalDate(2026, 10, 14)

    private fun every(weeks: Int, anchor: LocalDate) =
        RecurrenceRule.EveryWeeks(anchor.dayOfWeek, weeks, anchor)

    @Test
    fun `every three weeks fires on the anchor and then on each third week`() {
        val rule = every(3, wednesday)
        assertTrue(rule.matches(LocalDate(2026, 10, 14)))
        assertTrue(rule.matches(LocalDate(2026, 11, 4)))
        assertTrue(rule.matches(LocalDate(2026, 11, 25)))
        assertTrue(rule.matches(LocalDate(2026, 12, 16)))
        // Across the year boundary, where ISO week parity would drift.
        assertTrue(rule.matches(LocalDate(2027, 1, 6)))
    }

    @Test
    fun `the weeks in between are skipped, including the ones one and two weeks on`() {
        val rule = every(3, wednesday)
        assertFalse(rule.matches(LocalDate(2026, 10, 21)))
        assertFalse(rule.matches(LocalDate(2026, 10, 28)))
        assertFalse(rule.matches(LocalDate(2026, 11, 11)))
        assertFalse(rule.matches(LocalDate(2026, 11, 18)))
    }

    @Test
    fun `nothing fires before the anchor`() {
        val rule = every(3, wednesday)
        assertFalse(rule.matches(LocalDate(2026, 10, 7)))
    }

    @Test
    fun `only the anchor's weekday fires`() {
        val rule = every(3, wednesday)
        assertFalse(rule.matches(LocalDate(2026, 11, 4).plusDays(-1)))
        assertFalse(rule.matches(LocalDate(2026, 11, 6)))
    }

    @Test
    fun `a Sunday anchor keeps its cycle over the week boundary`() {
        // Sunday closes the ISO week, so the cycle must not be counted from Monday.
        val rule = every(3, LocalDate(2026, 10, 18))
        assertTrue(rule.matches(LocalDate(2026, 11, 8)))
        assertFalse(rule.matches(LocalDate(2026, 11, 1)))
    }

    @Test
    fun `the first date of a weekday in an N-week rhythm follows the week of the anchor`() {
        // Monday of the anchor's week is before a Wednesday anchor: the first Monday is a cycle later.
        assertEquals(LocalDate(2026, 11, 2), firstEveryWeeksDate(wednesday, 3, DayOfWeek.MONDAY))
        // Wednesday itself is the anchor, and Friday of the same week is after it.
        assertEquals(wednesday, firstEveryWeeksDate(wednesday, 3, DayOfWeek.WEDNESDAY))
        assertEquals(LocalDate(2026, 10, 16), firstEveryWeeksDate(wednesday, 3, DayOfWeek.FRIDAY))
    }

    @Test
    fun `a rhythm of N weeks makes one rule per weekday, each anchored on its own first date`() {
        val rules = Rhythm.EveryWeeks(3, wednesday).rulesFor(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(
            listOf(
                RecurrenceRule.EveryWeeks(DayOfWeek.WEDNESDAY, 3, LocalDate(2026, 10, 14)),
                RecurrenceRule.EveryWeeks(DayOfWeek.FRIDAY, 3, LocalDate(2026, 10, 16)),
            ),
            rules,
        )
        assertTrue(rules.all { rule -> rule is RecurrenceRule.EveryWeeks && rule.matches(rule.anchor) })
    }

    @Test
    fun `the rule survives the round trip through its stored text`() {
        val rule = every(3, wednesday)
        assertEquals(rule, RecurrenceRule.decode(rule.encode()))
    }

    @Test
    fun `one week is not an N-week rule`() {
        val failure = runCatching { every(1, wednesday) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun LocalDate.plusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)
}
