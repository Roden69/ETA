package com.example.eta.domain

import com.example.eta.domain.reevaluation.DAILY_CATCH_UP_DAYS
import com.example.eta.domain.reevaluation.unsettledDays
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Today is the 18th: yesterday is the 17th, seven days back the 11th, eight the 10th. */
class CatchUpTest {

    private val today = LocalDate(2026, 10, 18)

    private fun day(dayOfMonth: Int) = LocalDate(2026, 10, dayOfMonth)

    private val dailyWindow = 1..DAILY_CATCH_UP_DAYS

    @Test
    fun `the dashboard window is yesterday and the six days before it`() {
        assertEquals(7, DAILY_CATCH_UP_DAYS)
        assertEquals(listOf(day(17)), unsettledDays(today, dailyWindow, listOf(day(17)), emptySet()))
        assertEquals(listOf(day(11)), unsettledDays(today, dailyWindow, listOf(day(11)), emptySet()))
    }

    @Test
    fun `eight days back is outside the dashboard window`() {
        assertEquals(emptyList<LocalDate>(), unsettledDays(today, dailyWindow, listOf(day(10)), emptySet()))
    }

    @Test
    fun `today is never offered, even with blocks planned for it`() {
        assertEquals(emptyList<LocalDate>(), unsettledDays(today, dailyWindow, listOf(today), emptySet()))
    }

    @Test
    fun `a settled day is not offered again`() {
        val planned = listOf(day(17), day(16))
        assertEquals(listOf(day(16)), unsettledDays(today, dailyWindow, planned, setOf(day(17))))
    }

    @Test
    fun `a day without planned blocks has nothing to catch up`() {
        assertEquals(emptyList<LocalDate>(), unsettledDays(today, dailyWindow, emptyList(), emptySet()))
    }

    @Test
    fun `several missed days come oldest first`() {
        val planned = listOf(day(17), day(13), day(15))
        assertEquals(
            listOf(day(13), day(15), day(17)),
            unsettledDays(today, dailyWindow, planned, emptySet()),
        )
    }

    @Test
    fun `the settings window starts where the dashboard window ends`() {
        // Each unsettled day is offered by exactly one of the two paths.
        val planned = listOf(day(10), day(11), day(9))
        assertEquals(
            listOf(day(9), day(10)),
            unsettledDays(today, (DAILY_CATCH_UP_DAYS + 1)..30, planned, emptySet()),
        )
        assertEquals(
            listOf(day(11)),
            unsettledDays(today, dailyWindow, planned, emptySet()),
        )
    }
}
