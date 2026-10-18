package com.example.eta.domain.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A stretch of days, both ends included. */
data class DateWindow(val from: LocalDate, val to: LocalDate) {
    init {
        require(from <= to) { "A window runs forwards: $from is after $to" }
    }

    operator fun contains(date: LocalDate): Boolean = date >= from && date <= to
}

/**
 * The calendar stretch a weekly planning on [today] reads.
 *
 * From today through the day before the next weekly planning day, both included.
 * The next planning day is the first one strictly after today, so a planning day
 * itself reads the seven days it opens, and the stretch never reaches into the
 * week the next planning will lay out. Dated appointments are only ever read
 * into this window; nothing here moves them.
 */
fun planningWindow(today: LocalDate, planningDay: DayOfWeek): DateWindow {
    var next = today.plus(DatePeriod(days = 1))
    while (next.dayOfWeek != planningDay) next = next.plus(DatePeriod(days = 1))
    return DateWindow(from = today, to = next.plus(DatePeriod(days = -1)))
}
