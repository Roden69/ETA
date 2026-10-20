package com.example.eta.domain

import com.example.eta.domain.calendar.SeriesPattern
import com.example.eta.domain.calendar.SeriesReading
import com.example.eta.domain.calendar.readSeriesRule
import com.example.eta.domain.recurrence.Rhythm
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first occurrence of the series in these tests is Wednesday 2026-10-14, at
 * 08:00 Berlin time, unless a test says otherwise.
 */
class SeriesRuleTest {

    private val start = LocalDate(2026, 10, 14)
    private val berlin = TimeZone.of("Europe/Berlin")
    private val eight = LocalTime(8, 0)

    private fun read(vararg lines: String): SeriesReading =
        readSeriesRule(lines.toList(), start, berlin, eight)

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
        assertNull(pattern.lastDate)
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
    fun `a university rule with a UTC end instant ends on the local date of that instant`() {
        // 22:59:59 UTC on 5 February is 23:59:59 in Berlin, still the same day.
        val pattern = repeats(
            readSeriesRule(
                listOf("RRULE:FREQ=WEEKLY;WKST=MO;UNTIL=20270205T225959Z;BYDAY=MO"),
                LocalDate(2026, 10, 19),
                berlin,
                eight,
            ),
        )
        assertEquals(setOf(DayOfWeek.MONDAY), pattern.weekdays)
        assertEquals(LocalDate(2027, 2, 5), pattern.lastDate)
    }

    @Test
    fun `an UTC end instant that falls before the lesson on its last day ends the day before`() {
        // 00:00 UTC on 26 October is 01:00 in Berlin, before the 08:00 lesson of that day.
        val pattern = repeats(
            readSeriesRule(
                listOf("RRULE:FREQ=WEEKLY;UNTIL=20261026T000000Z;BYDAY=MO"),
                LocalDate(2026, 10, 19),
                berlin,
                eight,
            ),
        )
        assertEquals(LocalDate(2026, 10, 25), pattern.lastDate)
    }

    @Test
    fun `an end date without a time is the last day itself`() {
        val pattern = repeats(
            readSeriesRule(
                listOf("RRULE:FREQ=WEEKLY;UNTIL=20270205;BYDAY=MO"),
                LocalDate(2026, 10, 19),
                berlin,
                null,
            ),
        )
        assertEquals(LocalDate(2027, 2, 5), pattern.lastDate)
    }

    @Test
    fun `a count ends on the date of its last counted occurrence`() {
        // Wednesday 14th is not on the list, so the counted ones are Fri 16th, Mon 19th, Fri 23rd.
        val pattern = repeats(read("RRULE:FREQ=WEEKLY;COUNT=3;BYDAY=MO,FR"))
        assertEquals(LocalDate(2026, 10, 23), pattern.lastDate)
    }

    @Test
    fun `a count with an interval counts only the weeks the series repeats in`() {
        val pattern = repeats(read("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=2;BYDAY=WE"))
        assertEquals(LocalDate(2026, 10, 28), pattern.lastDate)
    }

    @Test
    fun `an exception date is no refusal, because the calendar already leaves that day out`() {
        val pattern = repeats(read("RRULE:FREQ=WEEKLY;BYDAY=WE", "EXDATE;VALUE=DATE:20261021"))
        assertEquals(setOf(DayOfWeek.WEDNESDAY), pattern.weekdays)
        assertNull(pattern.lastDate)
    }

    @Test
    fun `an extra date or an exception rule next to the rule is refused, not ignored`() {
        assertTrue(refused(read("RRULE:FREQ=WEEKLY", "RDATE:20261023T100000Z")).contains("RDATE"))
        assertTrue(refused(read("RRULE:FREQ=WEEKLY", "EXRULE:FREQ=WEEKLY")).contains("EXRULE"))
    }

    @Test
    fun `positional weekdays are refused because a weekday alone does not say which one`() {
        refused(read("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=1MO"))
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
        refused(readSeriesRule(emptyList(), start, berlin, eight))
    }
}
