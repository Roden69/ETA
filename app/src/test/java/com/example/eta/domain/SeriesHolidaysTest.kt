package com.example.eta.domain

import com.example.eta.domain.calendar.seriesHolidays
import com.example.eta.domain.model.RecurrenceRule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mondays in October and November 2026 are 19th, 26th and 2nd, 9th. */
class SeriesHolidaysTest {

    private val from = LocalDate(2026, 10, 20)
    private val to = LocalDate(2026, 11, 10)
    private val monday = RecurrenceRule.Weekly(DayOfWeek.MONDAY)

    @Test
    fun `a day the series card lays on but Google holds no occurrence for is a holiday`() {
        val holidays = seriesHolidays(
            rule = monday,
            repeatUntil = null,
            from = from,
            to = to,
            occurrences = setOf(LocalDate(2026, 10, 26), LocalDate(2026, 11, 9)),
        )
        assertEquals(setOf(LocalDate(2026, 11, 2)), holidays)
    }

    @Test
    fun `nothing is a holiday while Google reports no occurrence at all`() {
        // No evidence means nothing was synced for this series; blocks stay as they are.
        val holidays = seriesHolidays(monday, null, from, to, occurrences = emptySet())
        assertTrue(holidays.isEmpty())
    }

    @Test
    fun `days after the card's last day are not holidays, they are simply over`() {
        val holidays = seriesHolidays(
            rule = monday,
            repeatUntil = LocalDate(2026, 11, 2),
            from = from,
            to = to,
            occurrences = setOf(LocalDate(2026, 10, 26)),
        )
        assertEquals(setOf(LocalDate(2026, 11, 2)), holidays)
    }

    @Test
    fun `a fortnightly card only counts its own weeks as holidays`() {
        val fortnightly = RecurrenceRule.EveryWeeks(DayOfWeek.WEDNESDAY, 2, LocalDate(2026, 10, 14))
        val window = LocalDate(2026, 10, 14) to LocalDate(2026, 11, 1)
        assertEquals(
            setOf(LocalDate(2026, 10, 28)),
            seriesHolidays(fortnightly, null, window.first, window.second, setOf(LocalDate(2026, 10, 14))),
        )
        assertTrue(
            seriesHolidays(
                fortnightly,
                null,
                window.first,
                window.second,
                setOf(LocalDate(2026, 10, 14), LocalDate(2026, 10, 28)),
            ).isEmpty(),
        )
    }
}
