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

data class RoutinePlacement(
    val id: String,
    val routineId: String,
    val weekday: DayOfWeek,
    val start: LocalTime,
    val duration: Duration,
)

data class RoutineSetup(
    val setup: UserSetup,
    val routines: List<SetupRoutine>,
    val placements: List<RoutinePlacement>,
) {
    companion object {
        fun draft(now: Instant): RoutineSetup = RoutineSetup(
            setup = UserSetup.draft(now).copy(
                bedPrepTime = LocalTime(23, 0),
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

    fun newPlacement(weekday: DayOfWeek, start: LocalTime): RoutinePlacement = RoutinePlacement(
        id = UUID.randomUUID().toString(),
        routineId = routines.firstOrNull { it.id != SLEEP_ROUTINE_ID }?.id ?: SLEEP_ROUTINE_ID,
        weekday = weekday,
        start = start,
        duration = 1.hours,
    )

    fun withPlacement(placement: RoutinePlacement): RoutineSetup {
        require(routines.any { it.id == placement.routineId }) { "Placement routine must be selected" }
        require(placement.duration >= 15.minutes && placement.duration <= 23.hours + 45.minutes) {
            "Placement duration must be between 15 minutes and 23 hours 45 minutes"
        }
        if (placement.routineId == SLEEP_ROUTINE_ID) {
            return withSleep(placement.start, placement.duration)
        }
        val index = placements.indexOfFirst { it.id == placement.id }
        return if (index < 0) copy(placements = placements + placement)
        else copy(placements = placements.toMutableList().also { it[index] = placement })
    }

    fun withoutPlacement(id: String): RoutineSetup = copy(placements = placements.filterNot { it.id == id })

    fun withSleep(start: LocalTime, duration: Duration): RoutineSetup {
        require(duration >= 15.minutes && duration <= 23.hours + 45.minutes) {
            "Sleep duration must be between 15 minutes and 23 hours 45 minutes"
        }
        val wakeSecond = Math.floorMod(start.toSecondOfDay() + duration.inWholeSeconds.toInt(), 24 * 60 * 60)
        return copy(setup = setup.copy(
            bedPrepTime = start,
            sleepTime = start,
            wakeTime = LocalTime.fromSecondOfDay(wakeSecond),
            weekendNight = null,
            morningDuration = Duration.ZERO,
        ))
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
        WEEK.forEach { weekday ->
            val night = setup.nightEndingOn(weekday)
            val offset = night.sleepOffset()
            val sleepDay = weekday.shifted(Math.floorDiv(offset, MINUTES_PER_DAY))
            val sleepStart = LocalTime.fromSecondOfDay(Math.floorMod(offset, MINUTES_PER_DAY) * 60)
            addAll(RoutinePlacement("sleep-$weekday", SLEEP_ROUTINE_ID, sleepDay, sleepStart, night.sleepDuration()).spans(SLEEP_ROUTINE))
        }
        placements.forEach { placement ->
            routines.firstOrNull { it.id == placement.routineId }?.let { addAll(placement.spans(it)) }
        }
    }.sortedWith(compareBy({ WEEK.indexOf(it.weekday) }, { it.fromMinute }))

    fun conflicts(): List<SetupConflict> {
        val spans = weeklySpans()
        return buildList {
            for (i in spans.indices) for (j in i + 1 until spans.size) {
                val first = spans[i]
                val second = spans[j]
                if (first.weekday == second.weekday && first.overlaps(second)) {
                    add(SetupConflict(first.weekday, first, second))
                }
            }
        }
    }

    val unscheduledRoutines: List<SetupRoutine>
        get() = routines.filter { it.id != SLEEP_ROUTINE_ID && placements.none { p -> p.routineId == it.id } }
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
