package com.example.eta.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

enum class WeekParity { EVEN, ODD }

/**
 * How a recurring item repeats. Encoded to a single string column so the schema
 * stays flat; see [encode] and [decode] for the format.
 */
sealed class RecurrenceRule {

    /**
     * Every day. The only rule without a weekday — the setup questionnaire's
     * daily routines would otherwise need seven definitions each.
     */
    data object Daily : RecurrenceRule()

    data class Weekly(val weekday: DayOfWeek) : RecurrenceRule()

    data class Biweekly(
        val weekday: DayOfWeek,
        val parity: WeekParity,
    ) : RecurrenceRule()

    /** The [weekOfMonth]-th [weekday] of the month, 1-based. */
    data class Monthly(
        val weekday: DayOfWeek,
        val weekOfMonth: Int,
    ) : RecurrenceRule()

    /**
     * Every [weeks]-th week on [weekday], counted from [anchor].
     *
     * [anchor] is the first occurrence and falls on [weekday]; the weeks between
     * are counted in whole weeks from it, so a fortnightly rule anchored on one
     * Monday and a three-weekly one anchored on another are both exact, with no
     * ISO week-number parity to drift at a year's end. [weeks] is at least 2: one
     * week is [Weekly].
     */
    data class EveryWeeks(
        val weekday: DayOfWeek,
        val weeks: Int,
        val anchor: LocalDate,
    ) : RecurrenceRule() {
        init {
            require(weeks >= 2) { "EveryWeeks needs at least two weeks, got $weeks" }
            require(anchor.dayOfWeek == weekday) { "The anchor $anchor is not a $weekday" }
        }
    }

    fun encode(): String = when (this) {
        is Daily -> "D"
        is Weekly -> "W:$weekday"
        is Biweekly -> "B:$weekday:$parity"
        is Monthly -> "M:$weekday:$weekOfMonth"
        is EveryWeeks -> "E:$weekday:$weeks:$anchor"
    }

    companion object {
        fun decode(value: String): RecurrenceRule {
            val parts = value.split(':')
            if (parts[0] == "D") return Daily
            if (parts[0] == "E") {
                return EveryWeeks(DayOfWeek.valueOf(parts[1]), parts[2].toInt(), LocalDate.parse(parts[3]))
            }
            val weekday = DayOfWeek.valueOf(parts[1])
            return when (parts[0]) {
                "W" -> Weekly(weekday)
                "B" -> Biweekly(weekday, WeekParity.valueOf(parts[2]))
                "M" -> Monthly(weekday, parts[2].toInt())
                else -> error("Unknown recurrence rule: $value")
            }
        }
    }
}
