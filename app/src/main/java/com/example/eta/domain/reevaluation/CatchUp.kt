package com.example.eta.domain.reevaluation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * How far back the dashboard offers a missed evening: yesterday and the six days
 * before it. Inside this window a catch-up is one tap; older days stay behind the
 * phrase in the settings.
 */
const val DAILY_CATCH_UP_DAYS = 7

/**
 * The past days that had planned blocks and were never settled, oldest first.
 *
 * [daysBack] counts from [today]: 1 is yesterday. A day without a block has
 * nothing to settle and is left alone; a settled day is never offered again.
 */
fun unsettledDays(
    today: LocalDate,
    daysBack: IntRange,
    planned: Collection<LocalDate>,
    settled: Set<LocalDate>,
): List<LocalDate> {
    val plannedDays = planned.toSet()
    return daysBack
        .map { today.minus(DatePeriod(days = it)) }
        .filter { it in plannedDays && it !in settled }
        .sorted()
}
