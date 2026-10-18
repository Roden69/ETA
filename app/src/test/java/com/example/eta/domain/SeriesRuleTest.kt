package com.example.eta.domain

import com.example.eta.domain.calendar.SeriesPattern
import com.example.eta.domain.calendar.SeriesReading
import com.example.eta.domain.calendar.readSeriesRule
import com.example.eta.domain.recurrence.Rhythm
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first occurrence of the series in these tests is Wednesday 2026-10-14. */
class SeriesRuleTest {

    private val start = LocalDate(2026, 10, 14)

    private fun read(vararg lines: String): SeriesReading = readSeriesRule(lines.toList(), start)

    private fun repeats(reading: SeriesReading): SeriesPattern {
        assertTrue("expected a converted series, got $reading", reading is SeriesReading.Repeats)
        return (reading as SeriesReading.Repeats).pattern
    }

    private fun refused(reading: SeriesReading): String {
        assertTrue("expected a refusal, got $reading", reading is SeriesReading.Unsupported)
        return (reading as SeriesReading.Unsupported).reason
    }

    @Test
    fun `a plain weekly rule without a day list repeats on the weekday of its first date`() {
        val pattern = repeats(read("RRULE:FREQ=WEEKLY"))
        assertEquals(1, pattern.weeks)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), pattern.weekdays)
        assertEquals(Rhythm.Weekly, pattern.rhythm)
    }

    @Test
    fun `weekly with an explicit interval of one is the ordinary weekly rhythm`() {
        val pattern = repeats(read("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,FR"))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), pattern.weekdays)
        assertEquals(Rhythm.Weekly, pattern.rhythm)
    }

    @Test
    fun `every third week is kept as three weeks, anchored on the series' own first date`() {
        val pattern = repeats(read("RRULE:FREQ=WEEKLY;INTERVAL=3;BYDAY=WE,FR"))
        assertEquals(3, pattern.weeks)
        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), pattern.weekdays)
        assertEquals(start, pattern.start)
        assertEquals(Rhythm.EveryWeeks(3, start), pattern.rhythm)
    }

    @Test
    fun `a fortnightly rule is every two weeks, not the old parity`() {
        val pattern = repeats(read("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=WE"))
        assertEquals(Rhythm.EveryWeeks(2, start), pattern.rhythm)
    }

    @Test
    fun `the rule parts are case-insensitive and the RFC's own week start is accepted`() {
        val pattern = repeats(read("RRULE:freq=weekly;interval=3;byday=we;WKST=MO"))
        assertEquals(3, pattern.weeks)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), pattern.weekdays)
    }

    @Test
    fun `daily, monthly and yearly rules are refused rather than squeezed into a week`() {
        assertTrue(refused(read("RRULE:FREQ=DAILY")).isNotBlank())
        assertTrue(refused(read("RRULE:FREQ=MONTHLY;BYDAY=2WE")).isNotBlank())
        assertTrue(refused(read("RRULE:FREQ=YEARLY")).isNotBlank())
    }

    @Test
    fun `a series with an end date or a count is refused`() {
        refused(read("RRULE:FREQ=WEEKLY;COUNT=10"))
        refused(read("RRULE:FREQ=WEEKLY;UNTIL=20261231T000000Z"))
    }

    @Test
    fun `positional weekdays are refused because a weekday alone does not say which one`() {
        refused(read("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=1MO"))
    }

    @Test
    fun `an exception next to the rule is refused, not ignored`() {
        refused(read("RRULE:FREQ=WEEKLY", "EXDATE;VALUE=DATE:20261021"))
        refused(read("RRULE:FREQ=WEEKLY", "RDATE:20261023T100000Z"))
    }

    @Test
    fun `a rule part Eta does not model is refused by name`() {
        val reason = refused(read("RRULE:FREQ=WEEKLY;BYMONTH=10"))
        assertTrue(reason.contains("BYMONTH"))
    }

    @Test
    fun `a week that does not start on Monday is refused for intervals above one`() {
        refused(read("RRULE:FREQ=WEEKLY;INTERVAL=3;WKST=SU;BYDAY=WE"))
    }

    @Test
    fun `a master without any recurrence line is refused`() {
        refused(readSeriesRule(emptyList(), start))
    }
}
