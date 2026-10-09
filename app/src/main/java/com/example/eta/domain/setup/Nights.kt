package com.example.eta.domain.setup

import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.minuteOfDay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * One night: winding down, lights out, getting up.
 *
 * A night **belongs to the day it ends on**. That is the only reading under which
 * "at the weekend" means what people mean by it: the nights that end on Saturday
 * and on Sunday morning — so going to bed late on Friday and Saturday, and
 * sleeping in on Saturday and Sunday, while Sunday evening already belongs to
 * Monday.
 *
 * The offsets are minutes from the **wake day's** midnight, negative for the
 * evening before. Everything that has to put a night on a calendar day works
 * from them, so "before or after midnight" is decided in one place.
 */
data class NightTimes(
    val bedPrep: LocalTime,
    val sleep: LocalTime,
    val wake: LocalTime,
) {
    /** When the lights go out: on the wake day itself if that is before getting up. */
    fun sleepOffset(): Int {
        val at = sleep.minuteOfDay()
        return if (at < wake.minuteOfDay()) at else at - MINUTES_PER_DAY
    }

    /** How long the night lasts, from falling asleep to getting up. */
    fun sleepDuration(): Duration = (wake.minuteOfDay() - sleepOffset()).minutes

    /** How long winding down lasts, from putting things away to lights out. */
    fun bedPrepDuration(): Duration {
        val from = bedPrep.minuteOfDay()
        val to = sleep.minuteOfDay()
        return (if (to >= from) to - from else to + MINUTES_PER_DAY - from).minutes
    }

    fun bedPrepOffset(): Int = sleepOffset() - bedPrepDuration().inWholeMinutes.toInt()

    /**
     * Whether the lights go out after midnight — on the wake day itself, with
     * the whole evening before it left free.
     */
    val sleepsAfterMidnight: Boolean get() = sleepOffset() >= 0

    /**
     * The weekdays going to bed falls on, for nights that end on [wakeDays]:
     * the day before each, or the wake day itself when it is after midnight.
     * What a form has to say next to the hour, since "01:00" alone does not
     * tell which day's one o'clock is meant.
     */
    fun sleepDays(wakeDays: Set<DayOfWeek>): Set<DayOfWeek> =
        wakeDays.map { it.shifted(Math.floorDiv(sleepOffset(), MINUTES_PER_DAY)) }.toSet()

    /** The same for winding down, which can start before midnight and end after. */
    fun bedPrepDays(wakeDays: Set<DayOfWeek>): Set<DayOfWeek> =
        wakeDays.map { it.shifted(Math.floorDiv(bedPrepOffset(), MINUTES_PER_DAY)) }.toSet()

    fun encode(): String =
        listOf(bedPrep, sleep, wake).joinToString(",") { it.toSecondOfDay().toString() }

    companion object {
        fun decode(value: String): NightTimes {
            val (bedPrep, sleep, wake) = value.split(',').map { LocalTime.fromSecondOfDay(it.toInt()) }
            return NightTimes(bedPrep, sleep, wake)
        }
    }
}

/**
 * The days a weekend night ends on, unless the user says otherwise — see
 * [UserSetup.weekendDays]. Saturday and Sunday: Friday and Saturday evening.
 */
val DEFAULT_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** [this] moved by [days], forwards or back, round the week. */
fun DayOfWeek.shifted(days: Int): DayOfWeek = WEEK[Math.floorMod(WEEK.indexOf(this) + days, WEEK.size)]

/** The night of an ordinary day — the three times the questionnaire always asks for. */
val UserSetup.weekdayNight: NightTimes get() = NightTimes(bedPrepTime, sleepTime, wakeTime)

/** The night that would end on [weekday] without an individual correction. */
fun UserSetup.patternNightEndingOn(weekday: DayOfWeek): NightTimes =
    weekendNight?.takeIf { weekday in weekendDays } ?: weekdayNight

/**
 * The night that ends on [weekday]: its own correction where one was made, else
 * the weekend's night where one was given, else the ordinary one.
 */
fun UserSetup.nightEndingOn(weekday: DayOfWeek): NightTimes =
    nightOverrides[weekday] ?: patternNightEndingOn(weekday)

/** In week order, so the same corrections are always the same string. */
fun encodeNightOverrides(overrides: Map<DayOfWeek, NightTimes>): String =
    WEEK.filter { it in overrides }.joinToString(";") { "${it.name}=${overrides.getValue(it).encode()}" }

fun decodeNightOverrides(value: String): Map<DayOfWeek, NightTimes> =
    value.split(';').filter { it.isNotEmpty() }.associate { entry ->
        val (day, night) = entry.split('=', limit = 2)
        DayOfWeek.valueOf(day) to NightTimes.decode(night)
    }

fun UserSetup.wakeTimeOn(weekday: DayOfWeek): LocalTime = nightEndingOn(weekday).wake

/** All seven nights of a week together, each at its own length. */
fun UserSetup.sleepMinutesPerWeek(): Int =
    WEEK.sumOf { nightEndingOn(it).sleepDuration().inWholeMinutes.toInt() }

/** Longest sleep-free interval in the repeating week, including Sunday into Monday. */
fun UserSetup.longestAwakeMinutes(): Int {
    val sleeps = WEEK.mapIndexed { index, day ->
        val night = nightEndingOn(day)
        val midnight = index * MINUTES_PER_DAY
        (midnight + night.sleepOffset()) until (midnight + night.wake.minuteOfDay())
    }.sortedBy { it.first }
    var end = sleeps.first().last + 1
    var longest = 0
    for (index in 1 until sleeps.size) {
        val sleep = sleeps[index]
        longest = maxOf(longest, sleep.first - end)
        end = maxOf(end, sleep.last + 1)
    }
    return maxOf(longest, sleeps.first().first + WEEK.size * MINUTES_PER_DAY - end)
}

/**
 * What a weekend night is seeded with when the option is switched on: an hour
 * later to bed and two hours longer in it. Derived from the weekday night so it
 * moves with whatever was already answered there.
 */
fun UserSetup.suggestedWeekendNight(): NightTimes {
    fun LocalTime.plusMinutes(minutes: Int) =
        LocalTime.fromSecondOfDay(Math.floorMod(toSecondOfDay() + minutes * 60, 24 * 60 * 60))
    return NightTimes(
        bedPrep = bedPrepTime.plusMinutes(60),
        sleep = sleepTime.plusMinutes(60),
        wake = wakeTime.plusMinutes(120),
    )
}

