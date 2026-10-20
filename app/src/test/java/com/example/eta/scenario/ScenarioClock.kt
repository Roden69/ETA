package com.example.eta.scenario

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * A clock the scenario moves by hand, in one zone.
 *
 * It never runs by itself and never goes backwards: a scenario that needs an
 * earlier moment describes a different day and should say so with a new step.
 */
class ScenarioClock(
    val zone: TimeZone,
    start: LocalDateTime,
) : Clock {
    private var current: Instant = start.toInstant(zone)

    override fun now(): Instant = current

    /** The wall-clock moment the scenario is at. */
    fun local(): LocalDateTime = current.toLocalDateTime(zone)

    /** The scenario's today. */
    val today: LocalDate get() = local().date

    /** Moves to [moment]. Fails when that lies before now. */
    fun advanceTo(moment: LocalDateTime) {
        val target = moment.toInstant(zone)
        require(target >= current) { "Scenario clock cannot go back from ${local()} to $moment" }
        current = target
    }

    /** Moves to [time] on the same day. Fails when that lies before now. */
    fun at(time: LocalTime) = advanceTo(today.atTime(time))

    /** Moves to [at] on the day after today, whatever the time of day is now. */
    fun nextDay(at: LocalTime) {
        current = today.plus(DatePeriod(days = 1)).atTime(at).toInstant(zone)
    }
}
