package com.example.eta.domain.calendar

import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.recurrence.occurrencesBetween
import kotlinx.datetime.LocalDate

/**
 * The days a series card would lay a block on that Google holds no occurrence for.
 *
 * Google leaves a cancelled occurrence and an `EXDATE` out of its expanded events,
 * so a day the card runs on but the calendar does not show is a day the series does
 * not happen: a holiday, a cancelled lecture, or one moved elsewhere. [occurrences]
 * are the days Google does show the series on within [from]..[to], and the card's
 * own [repeatUntil] ends it.
 *
 * No occurrences at all is no evidence, not a holiday: nothing was read for the
 * series, so the card keeps laying its blocks as it did.
 */
fun seriesHolidays(
    rule: RecurrenceRule,
    repeatUntil: LocalDate?,
    from: LocalDate,
    to: LocalDate,
    occurrences: Set<LocalDate>,
): Set<LocalDate> {
    if (occurrences.isEmpty()) return emptySet()
    val last = repeatUntil?.let { minOf(it, to) } ?: to
    return rule.occurrencesBetween(from, last)
        .filterNot { it in occurrences }
        .toSet()
}
