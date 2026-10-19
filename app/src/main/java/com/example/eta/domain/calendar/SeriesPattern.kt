package com.example.eta.domain.calendar

import com.example.eta.domain.recurrence.Rhythm
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * A Google series Eta can take on: every [weeks]-th week, on [weekdays], from
 * [start] — the series' own first date.
 */
data class SeriesPattern(
    val weeks: Int,
    val weekdays: Set<DayOfWeek>,
    val start: LocalDate,
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
private val CONVERTIBLE_PARTS = setOf("FREQ", "INTERVAL", "BYDAY", "WKST")

/**
 * Reads the `recurrence` lines of a Google series master.
 *
 * Only a **weekly** rule converts: `FREQ=WEEKLY` with an optional `INTERVAL` and
 * `BYDAY`. Daily, monthly and yearly rules, positional weekdays (`1MO`), counts,
 * end dates and exceptions are refused with the reason, because each would turn
 * into a different rhythm if it were squeezed into a weekly one. A missing
 * `BYDAY` means the weekday of [start], as RFC 5545 says for a weekly rule.
 *
 * [start] is the series' own first date, which is what the every-N-weeks phase is
 * counted from — not the date of the occurrence the user happened to pick.
 */
fun readSeriesRule(recurrence: List<String>, start: LocalDate): SeriesReading {
    val exceptions = recurrence.firstOrNull { line ->
        EXCEPTION_PREFIXES.any { line.startsWith(it) }
    }
    if (exceptions != null) {
        return SeriesReading.Unsupported(
            "Die Serie hat Ausnahmen, die Eta nicht abbildet. Der Termin bleibt einzeln.",
        )
    }

    val rules = recurrence.filter { it.startsWith("RRULE:") }
    if (rules.size != 1) {
        return SeriesReading.Unsupported("Die Serie hat keine eindeutige Wiederholungsregel.")
    }
    val parts = rules.single().removePrefix("RRULE:").split(';')
        .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
        .associate { (key, value) -> key.uppercase() to value.uppercase() }

    if ("COUNT" in parts || "UNTIL" in parts) {
        return SeriesReading.Unsupported(
            "Serien mit Enddatum oder Anzahl werden nicht übernommen. Der Termin bleibt einzeln.",
        )
    }
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

    return SeriesReading.Repeats(SeriesPattern(interval, weekdays, start))
}

private val EXCEPTION_PREFIXES = listOf("EXDATE", "RDATE", "EXRULE")
