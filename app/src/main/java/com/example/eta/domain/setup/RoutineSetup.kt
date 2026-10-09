package com.example.eta.domain.setup

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.minuteOfDay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import java.util.UUID
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

const val SLEEP_ROUTINE_ID = "sleep"

data class SetupRoutine(
    val id: String,
    val name: String,
    val category: Category? = null,
    val role: ItemRole? = null,
)

val SLEEP_ROUTINE = SetupRoutine(SLEEP_ROUTINE_ID, "Schlafen")
val SUGGESTED_SETUP_ROUTINES = listOf(
    SetupRoutine("sport", "Sport", Category.FOKUS, ItemRole.SPORT),
    SetupRoutine("mindfulness", "Achtsamkeit", Category.ACHTSAM, ItemRole.MINDFULNESS),
)

/** What a night is seeded with before the user says otherwise. */
private val DEFAULT_WEEKDAY_NIGHT = NightTimes(LocalTime(23, 0), LocalTime(23, 0), LocalTime(7, 0))
private val DEFAULT_WEEKEND_NIGHT = NightTimes(LocalTime(0, 0), LocalTime(0, 0), LocalTime(9, 0))

/** One appointment of a selected routine on one weekday. Sleep is never one of these. */
data class RoutinePlacement(
    val id: String,
    val routineId: String,
    val weekday: DayOfWeek,
    val start: LocalTime,
    val duration: Duration,
)

/**
 * One night as the weekly calendar draws it: the sleep that **ends** on [wakeDay].
 *
 * Each wake day has a provisional night; the separate sleep-gap check enforces
 * the 24-hour rule when their times change. [startDay] is the evening before,
 * or the wake day itself where bedtime is after midnight.
 */
data class SleepNight(val wakeDay: DayOfWeek, val night: NightTimes) {
    val startDay: DayOfWeek get() = wakeDay.shifted(Math.floorDiv(night.sleepOffset(), MINUTES_PER_DAY))
    val start: LocalTime get() = night.sleep
    val duration: Duration get() = night.sleepDuration()

    /** The stretch as weekday spans: a night over midnight is two, never clipped. */
    fun spans(): List<SetupSpan> {
        val from = start.minuteOfDay()
        val end = from + duration.inWholeMinutes.toInt()
        return if (end <= MINUTES_PER_DAY) {
            listOf(SetupSpan(SLEEP_ROUTINE.name, startDay, from, end))
        } else {
            listOf(
                SetupSpan(SLEEP_ROUTINE.name, startDay, from, MINUTES_PER_DAY),
                SetupSpan(SLEEP_ROUTINE.name, startDay.shifted(1), 0, end - MINUTES_PER_DAY),
            )
        }
    }
}

/**
 * The first-run draft: which routines were chosen, where they sit in the week, and
 * the night underneath it all.
 *
 * Sleep is not a routine one places. The first page gives the weekday night and
 * the weekend night; the calendar lays one provisional [SleepNight] per day from
 * them, and each can then be corrected on its own (`UserSetup.nightOverrides`).
 */
data class RoutineSetup(
    val setup: UserSetup,
    val routines: List<SetupRoutine>,
    val placements: List<RoutinePlacement>,
) {
    companion object {
        fun draft(now: Instant): RoutineSetup = RoutineSetup(
            setup = UserSetup.draft(now).copy(
                bedPrepTime = DEFAULT_WEEKDAY_NIGHT.bedPrep,
                sleepTime = DEFAULT_WEEKDAY_NIGHT.sleep,
                wakeTime = DEFAULT_WEEKDAY_NIGHT.wake,
                weekendNight = DEFAULT_WEEKEND_NIGHT,
                weekendDays = DEFAULT_WEEKEND,
                nightOverrides = emptyMap(),
                morningDuration = Duration.ZERO,
                meals = MealPlan.DailyCooking(emptyList()),
                housekeeping = null,
                sport = null,
                freeTime = DailySlot(LocalTime(7, 0), Duration.ZERO),
                mindfulness = null,
                work = WorkSchedule.None,
            ),
            routines = listOf(SLEEP_ROUTINE),
            placements = emptyList(),
        )
    }

    fun toggleRoutine(routine: SetupRoutine): RoutineSetup {
        if (routines.any { it.id == routine.id }) return removeRoutine(routine.id)
        val duplicate = routines.firstOrNull { normalizeName(it.name) == normalizeName(routine.name) }
        if (duplicate != null) {
            return if (routine in SUGGESTED_SETUP_ROUTINES && duplicate != routine) {
                activateSuggestion(duplicate, routine)
            } else {
                this
            }
        }
        return copy(routines = routines + routine)
    }

    fun addRoutine(name: String): RoutineSetup {
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return this
        val suggestion = SUGGESTED_SETUP_ROUTINES.firstOrNull { normalizeName(it.name) == normalized }
        val existing = routines.firstOrNull { normalizeName(it.name) == normalized }
        if (existing != null) {
            return if (suggestion != null && existing != suggestion) activateSuggestion(existing, suggestion) else this
        }
        return copy(routines = routines + (suggestion ?: SetupRoutine(UUID.randomUUID().toString(), name.trim())))
    }

    private fun activateSuggestion(existing: SetupRoutine, suggestion: SetupRoutine): RoutineSetup =
        copy(
            routines = routines.map { if (it.id == existing.id) suggestion else it },
            placements = placements.map { placement ->
                if (placement.routineId == existing.id) placement.copy(routineId = suggestion.id) else placement
            },
        )

    fun removeRoutine(id: String): RoutineSetup {
        if (id == SLEEP_ROUTINE_ID) return this
        return copy(
            routines = routines.filterNot { it.id == id },
            placements = placements.filterNot { it.routineId == id },
        )
    }

    /** The routines that can be put into the calendar — everything but sleep. */
    val placeableRoutines: List<SetupRoutine>
        get() = routines.filter { it.id != SLEEP_ROUTINE_ID }

    /** A one-hour placement of the first chosen routine; null while none was chosen. */
    fun newPlacement(weekday: DayOfWeek, start: LocalTime): RoutinePlacement? {
        val routine = placeableRoutines.firstOrNull() ?: return null
        return RoutinePlacement(
            id = UUID.randomUUID().toString(),
            routineId = routine.id,
            weekday = weekday,
            start = start,
            duration = 1.hours,
        )
    }

    fun withPlacement(placement: RoutinePlacement): RoutineSetup {
        require(placement.routineId != SLEEP_ROUTINE_ID) { "Sleep is edited per night, not placed" }
        require(routines.any { it.id == placement.routineId }) { "Placement routine must be selected" }
        require(placement.duration >= 15.minutes && placement.duration <= 23.hours + 45.minutes) {
            "Placement duration must be between 15 minutes and 23 hours 45 minutes"
        }
        val index = placements.indexOfFirst { it.id == placement.id }
        return if (index < 0) copy(placements = placements + placement)
        else copy(placements = placements.toMutableList().also { it[index] = placement })
    }

    fun withoutPlacement(id: String): RoutineSetup = copy(placements = placements.filterNot { it.id == id })

    // --- Sleep: the pattern from the first page ---------------------------------

    /** The ordinary night, for the days that are not the weekend. No winding down: it is not asked. */
    fun withWeekdayNight(sleep: LocalTime, wake: LocalTime): RoutineSetup {
        requireDistinct(sleep, wake)
        return withSetup(setup.copy(bedPrepTime = sleep, sleepTime = sleep, wakeTime = wake))
    }

    /** The night that ends on the weekend days. */
    fun withWeekendNight(sleep: LocalTime, wake: LocalTime): RoutineSetup {
        requireDistinct(sleep, wake)
        return withSetup(setup.copy(weekendNight = NightTimes(sleep, sleep, wake)))
    }

    /**
     * Which days are the weekend. Refuses none and refuses all seven: a weekend of
     * no days is no weekend, and one of every day leaves no ordinary night to ask.
     */
    fun withWeekendDays(days: Set<DayOfWeek>): RoutineSetup {
        if (days.isEmpty() || days.size == WEEK.size) return this
        return withSetup(setup.copy(weekendDays = days))
    }

    // --- Sleep: the individual nights of the calendar ---------------------------

    /**
     * Sets the night that ends on [wakeDay] on its own. A night put back exactly
     * where the pattern has it stops being a correction, so a later change to the
     * pattern carries it along again.
     */
    fun withNight(wakeDay: DayOfWeek, sleep: LocalTime, wake: LocalTime): RoutineSetup {
        requireDistinct(sleep, wake)
        val pattern = setup.patternNightEndingOn(wakeDay)
        val overrides = if (pattern.sleep == sleep && pattern.wake == wake) {
            setup.nightOverrides - wakeDay
        } else {
            setup.nightOverrides + (wakeDay to NightTimes(sleep, sleep, wake))
        }
        return copy(setup = setup.copy(nightOverrides = overrides))
    }

    /** All seven nights of the week, one per wake day, in week order. */
    fun sleepNights(): List<SleepNight> = WEEK.map { SleepNight(it, setup.nightEndingOn(it)) }

    /**
     * The wake days whose night runs into another night. Two nights on top of each
     * other would count the same hours of sleep twice, so this is a reason not to
     * finish, unlike an overlap with an ordinary routine, which is only a warning.
     */
    fun overlappingSleep(): Set<DayOfWeek> {
        val nights = sleepNights().map { it to it.spans() }
        return buildSet {
            for (i in nights.indices) for (j in i + 1 until nights.size) {
                if (nights[i].second.any { a -> nights[j].second.any { b -> a.overlaps(b) } }) {
                    add(nights[i].first.wakeDay)
                    add(nights[j].first.wakeDay)
                }
            }
        }
    }

    private fun withSetup(next: UserSetup): RoutineSetup {
        // Corrections that now equal the pattern stop being corrections.
        val overrides = next.nightOverrides.filterNot { (day, night) ->
            val pattern = next.patternNightEndingOn(day)
            pattern.sleep == night.sleep && pattern.wake == night.wake
        }
        return copy(setup = next.copy(nightOverrides = overrides))
    }

    private fun requireDistinct(sleep: LocalTime, wake: LocalTime) {
        require(sleep != wake) { "A night needs a bedtime different from the wake time" }
    }

    fun recurringItems(now: Instant): List<Item> = placements.mapNotNull { placement ->
        val routine = routines.firstOrNull { it.id == placement.routineId } ?: return@mapNotNull null
        if (routine.id == SLEEP_ROUTINE_ID) return@mapNotNull null
        Item.newRecurring(
            id = "setup:routine-${placement.id}",
            name = routine.name,
            category = routine.category,
            recurrenceRule = RecurrenceRule.Weekly(placement.weekday),
            startTime = placement.start,
            estimatedDuration = placement.duration,
            now = now,
            role = routine.role,
        )
    }

    fun weeklySpans(): List<SetupSpan> = buildList {
        sleepNights().forEach { addAll(it.spans()) }
        placements.forEach { placement ->
            routines.firstOrNull { it.id == placement.routineId }?.let { addAll(placement.spans(it)) }
        }
    }.sortedWith(compareBy({ WEEK.indexOf(it.weekday) }, { it.fromMinute }))

    /** Overlaps of routines with each other and with sleep; sleep with sleep is [overlappingSleep]. */
    fun conflicts(): List<SetupConflict> {
        val spans = weeklySpans()
        return buildList {
            for (i in spans.indices) for (j in i + 1 until spans.size) {
                val first = spans[i]
                val second = spans[j]
                if (first.label == SLEEP_ROUTINE.name && second.label == SLEEP_ROUTINE.name) continue
                if (first.weekday == second.weekday && first.overlaps(second)) {
                    add(SetupConflict(first.weekday, first, second))
                }
            }
        }
    }

    val unscheduledRoutines: List<SetupRoutine>
        get() = placeableRoutines.filter { routine -> placements.none { it.routineId == routine.id } }
}

fun RoutinePlacement.spans(routine: SetupRoutine): List<SetupSpan> {
    val length = duration.inWholeMinutes.toInt()
    if (length <= 0) return emptyList()
    val from = start.minuteOfDay()
    val end = from + length
    return if (end <= MINUTES_PER_DAY) {
        listOf(SetupSpan(routine.name, weekday, from, end))
    } else {
        listOf(
            SetupSpan(routine.name, weekday, from, MINUTES_PER_DAY),
            SetupSpan(routine.name, weekday.shifted(1), 0, end - MINUTES_PER_DAY),
        )
    }
}

private val NAME_WHITESPACE = Regex("\\s+")
private fun normalizeName(name: String): String = name.trim().replace(NAME_WHITESPACE, " ").lowercase()
