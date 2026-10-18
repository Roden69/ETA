package com.example.eta.domain

import com.example.eta.domain.calendar.DateWindow
import com.example.eta.domain.calendar.planningWindow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-18 is a Sunday. */
class PlanningWindowTest {

    @Test
    fun `the window runs from today through the day before the next planning day`() {
        // Sunday planned on Sunday: the next planning day is a week away, so the
        // window is the seven days this Sunday opens — Sunday to Saturday.
        assertEquals(
            DateWindow(LocalDate(2026, 10, 18), LocalDate(2026, 10, 24)),
            planningWindow(LocalDate(2026, 10, 18), DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun `a planning made the day before the planning day stops on the day before the next one`() {
        // Saturday with Sunday as planning day: the next Sunday is tomorrow, so
        // the window is today alone. Nothing of the coming week leaks in.
        assertEquals(
            DateWindow(LocalDate(2026, 10, 17), LocalDate(2026, 10, 17)),
            planningWindow(LocalDate(2026, 10, 17), DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun `a Monday with Sunday as planning day reads Monday to Saturday`() {
        assertEquals(
            DateWindow(LocalDate(2026, 10, 19), LocalDate(2026, 10, 24)),
            planningWindow(LocalDate(2026, 10, 19), DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun `a planning day itself is not its own next planning day`() {
        // Thursday with Thursday as planning day: the window is the seven days
        // from today, Thursday included, ending on the Wednesday before next week's
        // planning. Never the Thursday a week on.
        val window = planningWindow(LocalDate(2026, 10, 22), DayOfWeek.THURSDAY)
        assertEquals(LocalDate(2026, 10, 22), window.from)
        assertEquals(LocalDate(2026, 10, 28), window.to)
        assertFalse(LocalDate(2026, 10, 29) in window)
    }

    @Test
    fun `the window always spans between one and seven days`() {
        var day = LocalDate(2026, 10, 1)
        repeat(60) {
            for (planningDay in DayOfWeek.entries) {
                val window = planningWindow(day, planningDay)
                val length = (window.to.toEpochDays() - window.from.toEpochDays()) + 1
                assertTrue("$day / $planningDay gave $length days", length in 1..7)
                assertTrue(day in window)
                assertEquals(planningDay, window.to.plusOneDay().dayOfWeek)
            }
            day = LocalDate.fromEpochDays(day.toEpochDays() + 1)
        }
    }

    @Test
    fun `dates on both ends are part of the window and the day after is not`() {
        val window = DateWindow(LocalDate(2026, 10, 18), LocalDate(2026, 10, 24))
        assertTrue(LocalDate(2026, 10, 18) in window)
        assertTrue(LocalDate(2026, 10, 24) in window)
        assertFalse(LocalDate(2026, 10, 25) in window)
    }

    private fun LocalDate.plusOneDay() = LocalDate.fromEpochDays(toEpochDays() + 1)
}
