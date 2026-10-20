package com.example.eta.domain.calendar

import com.example.eta.domain.recurrence.Rhythm
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * A Google series Eta can take on: every [weeks]-th week, on [weekdays], from
 * [start] — the series' own first date — and up to [lastDate] if it has one.
 */
data class SeriesPattern(
    val weeks: Int,
    val weekdays: Set<DayOfWeek>,
    val start: LocalDate,
    /** The last day the series is still on, from its end date or count; null when it runs on. */
    val lastDate: LocalDate? = null,
) {
    init {
        require(weeks >= 1) { "A series repeats every one or more weeks, got $weeks" }
        require(weekdays.isNotEmpty()) { "A series needs at least one weekday" }
    }

    /** The rhythm the recurring tasks are made with: weekly, or every N weeks anchored on [start]. */
    val rhythm: Rhythm get() = if (weeks == 1) Rhythm.Weekly else Rhythm.EveryWeeks(weeks, start)
}

/** What reading a series' recurrence came to. */
sealed interface SeriesReading {
    data class Repeats(val pattern: SeriesPattern) : SeriesReading

    /** Not converted, with a sentence the screen can show. Never a silent approximation. */
    data class Unsupported(val reason: String) : SeriesReading
}

private val WEEKDAY_CODES = mapOf(
    "MO" to DayOfWeek.MONDAY,
    "TU" to DayOfWeek.TUESDAY,
    "WE" to DayOfWeek.WEDNESDAY,
    "TH" to DayOfWeek.THURSDAY,
    "FR" to DayOfWeek.FRIDAY,
    "SA" to DayOfWeek.SATURDAY,
    "SU" to DayOfWeek.SUNDAY,
)

/** The rule parts Eta can represent. Anything else in a rule is refused, not ignored. */
private val CONVERTIBLE_PARTS = setOf("FREQ", "INTERVAL", "BYDAY", "WKST", "UNTIL", "COUNT")

/**
 * Extra dates Eta does not model, refused because a date added to a rhythm cannot
 * be seen in it. `EXDATE` is not here: a date the series leaves out simply has no
 * occurrence in Google, and the calendar step keeps that day free.
 */
private val REFUSED_PREFIXES = listOf("RDATE", "EXRULE")

/** A count above this is refused: counting walks the series day by day. */
private const val MAX_COUNTED_OCCURRENCES = 1000

private const val DAYS_PER_WEEK = 7

/**
 * Reads the `recurrence` lines of a Google series master.
 *
 * Only a **weekly** rule converts: `FREQ=WEEKLY` with an optional `INTERVAL`,
 * `BYDAY`, and an end, either `UNTIL` or `COUNT`. Daily, monthly and yearly rules,
 * positional weekdays (`1MO`) and rule parts Eta does not model are refused with the
 * reason, because each would turn into a different rhythm if it were squeezed into a
 * weekly one. A missing `BYDAY` means the weekday of [start], as RFC 5545 says for a
 * weekly rule.
 *
 * [start] is the series' own first date, which is what the every-N-weeks phase is
 * counted from — not the date of the occurrence the user happened to pick.
 * [timeZone] and [occurrenceTime] say where an `UNTIL` instant falls: the series
 * ends on the last day it still has an occurrence, which for a timed series can be
 * the day before the `UNTIL` date when that instant is earlier than the occurrence's
 * own time. A null [occurrenceTime] is an all-day series, which runs to the `UNTIL`
 * date itself.
 */
fun readSeriesRule(
    recurrence: List<String>,
    start: LocalDate,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    occurrenceTime: LocalTime? = null,
): SeriesReading {
    val refused = recurrence.firstNotNullOfOrNull { line ->
        REFUSED_PREFIXES.firstOrNull { line.startsWith(it) }
    }
    if (refused != null) {
        return SeriesReading.Unsupported(
            "Die Serie nutzt $refused, das Eta nicht abbildet. Die Termine bleiben einzeln.",
        )
    }

    val rules = recurrence.filter { it.startsWith("RRULE:") }
    if (rules.size != 1) {
        return SeriesReading.Unsupported("Die Serie hat keine eindeutige Wiederholungsregel.")
    }
    val parts = rules.single().removePrefix("RRULE:").split(';')
        .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
        .associate { (key, value) -> key.uppercase() to value.uppercase() }

    val unknown = parts.keys - CONVERTIBLE_PARTS
    if (unknown.isNotEmpty()) {
        return SeriesReading.Unsupported(
            "Die Wiederholung nutzt ${unknown.sorted().joinToString()}, das Eta nicht abbildet.",
        )
    }
    val frequency = parts["FREQ"]
    if (frequency != "WEEKLY") {
        return SeriesReading.Unsupported(
            "Nur wöchentliche Serien werden übernommen (Google meldet ${frequency ?: "keine Häufigkeit"}).",
        )
    }

    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    if (interval < 1) return SeriesReading.Unsupported("Das Intervall der Serie ist ungültig.")
    val weekStart = parts["WKST"]
    if (interval > 1 && weekStart != null && weekStart != "MO") {
        return SeriesReading.Unsupported("Serien mit Wochenbeginn $weekStart werden nicht übernommen.")
    }

    val weekdays = parts["BYDAY"]?.split(',')?.map { code ->
        WEEKDAY_CODES[code]
            ?: return SeriesReading.Unsupported(
                "Der Wochentag »$code« ist keine einfache Angabe; Eta übernimmt nur Wochentage.",
            )
    }?.toSet() ?: setOf(start.dayOfWeek)

    val pattern = SeriesPattern(interval, weekdays, start)
    val until = parts["UNTIL"]
    val count = parts["COUNT"]
    if (until != null && count != null) {
        return SeriesReading.Unsupported("Die Serie nennt Enddatum und Anzahl zugleich; Eta übernimmt nur eines.")
    }
    val lastDate = when {
        until != null -> untilDate(until, timeZone, occurrenceTime)
            ?: return SeriesReading.Unsupported("Das Enddatum der Serie ist ungültig.")

        count != null -> {
            val n = count.toIntOrNull()?.takeIf { it >= 1 }
                ?: return SeriesReading.Unsupported("Die Anzahl der Termine der Serie ist ungültig.")
            if (n > MAX_COUNTED_OCCURRENCES) {
                return SeriesReading.Unsupported(
                    "Serien mit mehr als $MAX_COUNTED_OCCURRENCES Terminen werden nicht übernommen.",
                )
            }
            countedLastDate(n, pattern)
        }

        else -> null
    }
    return SeriesReading.Repeats(pattern.copy(lastDate = lastDate))
}

/**
 * The last day of a series that ends on [value], an iCalendar date or date-time.
 *
 * A date-time in UTC (`…Z`) is read in [timeZone]. The end is inclusive, so the
 * series is still on that day, unless the occurrence's own [occurrenceTime] falls
 * after the instant on that day, in which case the day before is the last one.
 */
private fun untilDate(value: String, timeZone: TimeZone, occurrenceTime: LocalTime?): LocalDate? {
    val day = parseCompactDate(value.take(8)) ?: return null
    if (value.length <= 8) return day
    if (value.getOrNull(8) != 'T') return null
    val clock = value.drop(9)
    if (clock.length != 6 && clock.length != 7) return null
    val digits = clock.take(6)
    if (!digits.all { it.isDigit() }) return null

    val until: LocalDateTime = if (clock.endsWith('Z')) {
        // Built from the digits: LocalTime.toString drops the seconds at midnight.
        val instant = "${day}T${digits.take(2)}:${digits.substring(2, 4)}:${digits.substring(4, 6)}Z"
        runCatching { Instant.parse(instant).toLocalDateTime(timeZone) }.getOrNull() ?: return null
    } else {
        LocalDateTime(day, parseCompactTime(digits) ?: return null)
    }
    if (occurrenceTime == null) return until.date
    val lastOccurrence = LocalDateTime(until.date, occurrenceTime)
    return if (lastOccurrence > until) until.date.plus(DatePeriod(days = -1)) else until.date
}

/** The date of the [count]-th occurrence of [pattern], counted from its first date. */
private fun countedLastDate(count: Int, pattern: SeriesPattern): LocalDate {
    var found = 0
    var day = pattern.start
    while (true) {
        if (pattern.occursOn(day)) {
            found++
            if (found == count) return day
        }
        day = day.plus(DatePeriod(days = 1))
    }
}

/** Whether the series has an occurrence on [day]: its weekday, in a week its interval reaches. */
private fun SeriesPattern.occursOn(day: LocalDate): Boolean {
    if (day < start || day.dayOfWeek !in weekdays) return false
    val weeksApart = mondayOf(start).daysUntil(mondayOf(day)) / DAYS_PER_WEEK
    return weeksApart % weeks == 0
}

private fun mondayOf(day: LocalDate): LocalDate =
    day.plus(DatePeriod(days = 1 - day.dayOfWeek.isoDayNumber))

/** `20270205` as a date, or null when the text is not one. */
private fun parseCompactDate(text: String): LocalDate? {
    if (text.length != 8 || !text.all { it.isDigit() }) return null
    return runCatching {
        LocalDate(text.take(4).toInt(), text.substring(4, 6).toInt(), text.substring(6, 8).toInt())
    }.getOrNull()
}

/** `225959` as a time, or null when the text is not one. */
private fun parseCompactTime(text: String): LocalTime? {
    if (text.length != 6 || !text.all { it.isDigit() }) return null
    return runCatching {
        LocalTime(text.take(2).toInt(), text.substring(2, 4).toInt(), text.substring(4, 6).toInt())
    }.getOrNull()
}
