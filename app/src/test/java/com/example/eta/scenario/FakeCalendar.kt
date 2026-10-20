package com.example.eta.scenario

import com.example.eta.data.calendar.ALL_DAY_SUGGESTED_DURATION
import com.example.eta.data.calendar.ALL_DAY_SUGGESTED_START
import com.example.eta.data.calendar.AuthOutcome
import com.example.eta.data.calendar.CalendarApi
import com.example.eta.data.calendar.CalendarAuth
import com.example.eta.data.calendar.RemoteCalendar
import com.example.eta.data.calendar.RemoteEvent
import com.example.eta.data.calendar.RemoteSeries
import android.content.Intent
import kotlin.time.Duration

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * A Google Calendar held in memory, read the way the app reads Google.
 *
 * It is the [CalendarApi] and the [CalendarAuth] of one scenario, so the real sync,
 * the real series offers and the real calendar step run over it. Nothing here
 * decides anything: it only answers what Google would answer. Single events and
 * all-day events come back as they are; a weekly series comes back expanded into
 * one dated occurrence per day it happens, each with the series' `recurringEventId`,
 * and the series master comes back with its `RRULE` and `EXDATE` lines.
 *
 * Google leaves a cancelled day and an `EXDATE` out of its expansion, so a holiday
 * is simply a day the series does not show; the app has to notice that by itself.
 *
 * Supported rules: `FREQ=WEEKLY` with optional `INTERVAL`, `BYDAY` (the weekday of
 * the start when missing), and `UNTIL` or `COUNT`. Anything else is not something
 * this fake needs, and it fails loudly rather than guessing.
 */
class FakeCalendar : CalendarApi, CalendarAuth {
    private val calendars = mutableListOf(
        RemoteCalendar(
            id = PRIMARY_CALENDAR,
            displayName = "Lena",
            accountName = PRIMARY_CALENDAR,
            isPrimary = true,
            isForeign = false,
        ),
    )
    private val singles = mutableListOf<RemoteEvent>()
    private val series = mutableListOf<FakeSeries>()

    /** Adds a calendar the account can see; it is switched off until the user switches it on. */
    fun addCalendar(id: String, name: String, foreign: Boolean = true) {
        calendars += RemoteCalendar(
            id = id,
            displayName = name,
            accountName = null,
            isPrimary = false,
            isForeign = foreign,
        )
    }

    /** A timed appointment on one day. Returns its event id. */
    fun event(
        title: String,
        date: LocalDate,
        start: LocalTime,
        duration: Duration,
        calendarId: String = PRIMARY_CALENDAR,
    ): String {
        val id = "single-${singles.size + 1}-${compact(date)}"
        singles += RemoteEvent(
            calendarId = calendarId,
            eventId = id,
            title = title,
            date = date,
            start = start,
            duration = duration,
            allDay = false,
            updatedAt = null,
        )
        return id
    }

    /** An all-day event on one day, offered with the app's suggested hour. Returns its event id. */
    fun allDay(title: String, date: LocalDate, calendarId: String = PRIMARY_CALENDAR): String {
        val id = "allday-${singles.size + 1}-${compact(date)}"
        singles += RemoteEvent(
            calendarId = calendarId,
            eventId = id,
            title = title,
            date = date,
            start = ALL_DAY_SUGGESTED_START,
            duration = ALL_DAY_SUGGESTED_DURATION,
            allDay = true,
            updatedAt = null,
        )
        return id
    }

    /**
     * A weekly series of timed occurrences, as Google keeps it.
     *
     * [rule] is the `RRULE` value without its prefix, e.g.
     * `FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261231T215959Z`. [exdates] are the days the
     * series leaves out. Returns the series id, the `recurringEventId` of its
     * occurrences.
     */
    fun series(
        title: String,
        start: LocalDate,
        time: LocalTime,
        duration: Duration,
        rule: String,
        exdates: List<LocalDate> = emptyList(),
        calendarId: String = PRIMARY_CALENDAR,
    ): String {
        val id = "series-${series.size + 1}-${title.lowercase().filter { it.isLetterOrDigit() }}"
        series += FakeSeries(
            calendarId = calendarId,
            id = id,
            title = title,
            start = start,
            time = time,
            duration = duration,
            rule = parseRule(rule, start),
            ruleText = rule,
            exdates = exdates.toSet(),
        )
        return id
    }

    override suspend fun calendars(token: String): List<RemoteCalendar> = calendars.toList()

    override suspend fun events(
        token: String,
        calendarId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<RemoteEvent> {
        val single = singles.filter { it.calendarId == calendarId && it.date >= from && it.date <= to }
        val recurring = series.filter { it.calendarId == calendarId }.flatMap { it.occurrences(from, to) }
        return (single + recurring).sortedWith(compareBy({ it.date }, { it.start }))
    }

    override suspend fun seriesMaster(token: String, calendarId: String, seriesId: String): RemoteSeries {
        val found = series.single { it.calendarId == calendarId && it.id == seriesId }
        return RemoteSeries(
            recurrence = listOf("RRULE:${found.ruleText}") +
                found.exdates.sorted().map { "EXDATE;TZID=$ZONE:${compact(it)}T${compactTime(found.time)}" },
            start = found.start,
        )
    }

    override suspend fun token(): AuthOutcome = AuthOutcome.Ok(TOKEN)

    override fun tokenFrom(data: Intent?): AuthOutcome = AuthOutcome.Ok(TOKEN)

    companion object {
        const val PRIMARY_CALENDAR = "lena@example.test"
        const val ZONE = "Europe/Berlin"
        private const val TOKEN = "fake-token"

        internal fun compact(date: LocalDate): String = date.toString().replace("-", "")

        private fun compactTime(time: LocalTime): String =
            "%02d%02d00".format(time.hour, time.minute)

        private fun parseRule(text: String, start: LocalDate): FakeRule {
            val parts = text.split(';').associate { part ->
                part.substringBefore('=') to part.substringAfter('=', missingDelimiterValue = "")
            }
            require(parts["FREQ"] == "WEEKLY") { "FakeCalendar expands weekly rules only: $text" }
            val interval = parts["INTERVAL"]?.toInt() ?: 1
            val weekdays = parts["BYDAY"]?.split(',')?.map { it.toWeekday() }?.toSet()
                ?: setOf(start.dayOfWeek)
            val until = parts["UNTIL"]?.let { LocalDate.parse(it.take(8).let(::dashed)) }
            val count = parts["COUNT"]?.toInt()
            require(until == null || count == null) { "A rule ends by UNTIL or COUNT, not both: $text" }
            return FakeRule(weekdays, interval, until, count)
        }

        private fun dashed(compact: String) = "${compact.take(4)}-${compact.substring(4, 6)}-${compact.substring(6, 8)}"

        private fun String.toWeekday(): DayOfWeek = when (this) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> error("unknown BYDAY code $this")
        }
    }
}

private class FakeRule(
    val weekdays: Set<DayOfWeek>,
    val interval: Int,
    val until: LocalDate?,
    val count: Int?,
)

private class FakeSeries(
    val calendarId: String,
    val id: String,
    val title: String,
    val start: LocalDate,
    val time: LocalTime,
    val duration: Duration,
    val rule: FakeRule,
    val ruleText: String,
    val exdates: Set<LocalDate>,
) {
    /**
     * The occurrences between [from] and [to], both days included, as Google expands
     * them: an excluded day is left out, and a counted rule still counts it.
     */
    fun occurrences(from: LocalDate, to: LocalDate): List<RemoteEvent> {
        val last = rule.until?.let { minOf(it, to) } ?: to
        val result = mutableListOf<RemoteEvent>()
        var day = start
        var index = 0
        while (day <= last) {
            if (day.dayOfWeek in rule.weekdays && weekOffset(day) % rule.interval == 0) {
                index++
                if (rule.count != null && index > rule.count) break
                val excluded = day in exdates
                if (!excluded && day >= from) result += occurrence(day)
            }
            day = day.plus(DatePeriod(days = 1))
        }
        return result
    }

    private fun weekOffset(day: LocalDate): Int = (start.daysUntil(day) + start.dayOfWeek.ordinal) / 7

    private fun occurrence(day: LocalDate) = RemoteEvent(
        calendarId = calendarId,
        eventId = "${id}_${FakeCalendar.compact(day)}",
        title = title,
        date = day,
        start = time,
        duration = duration,
        allDay = false,
        updatedAt = null,
        seriesId = id,
    )
}
