package com.example.eta.data.repository

import com.example.eta.data.local.CalendarDao
import com.example.eta.data.local.ItemDao
import com.example.eta.domain.growth.placeDynamicGrowth
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.recurrence.isLaidAfterEdit
import com.example.eta.domain.recurrence.isRelayableToday
import com.example.eta.domain.recurrence.matches
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** How far ahead the standing schedule is kept materialized. */
const val SCHEDULE_HORIZON_DAYS = 21

/**
 * Keeps the standing schedule laid down ahead of today.
 *
 * Without this the recurring tasks quietly run out: the setup materialized a
 * fortnight and nothing extended it, so two weeks after answering the
 * questionnaire the day planner, the free-hour maths and the "now" box would all
 * have gone empty with no error to explain it.
 *
 * Cheap to call often — `expandRecurring` skips what already exists — so it runs
 * whenever the app starts and at the top of each planning phase, which is where
 * the concept always said expansion belonged.
 */
class ScheduleMaintenance(
    private val itemDao: ItemDao,
    private val planRepository: PlanRepository,
    private val vacationRepository: VacationRepository,
    private val calendarDao: CalendarDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /**
     * Materializes from today to the horizon. Returns how many blocks were added.
     * [keep] decides which new occurrences are laid down; the default keeps all.
     */
    suspend fun topUp(keep: (PlannedBlock) -> Boolean = { true }): Int =
        topUpUntil(clock.todayIn(timeZone).plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)), keep)

    /** Materializes at least as far as [until], for a range that reaches further. */
    suspend fun topUpUntil(until: LocalDate, keep: (PlannedBlock) -> Boolean = { true }): Int {
        val today = clock.todayIn(timeZone)
        val horizon = maxOf(until, today.plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)))
        // A standing task past its "Wiederholen bis" has nothing left to lay
        // down. Retiring it here takes it off the lists that describe the week
        // as it still is; expansion would pass over it either way.
        itemDao.retireEndedRecurring(today, clock.now())
        val definitions = itemDao.findRecurringDefinitions()
        val retainedImports = calendarDao.findRetainedIndividualSeriesImports()
        val definitionIds = definitions.associateBy { it.id }
        val suppressed = retainedImports.mapNotNull { retained ->
            val definition = definitionIds[retained.itemId] ?: return@mapNotNull null
            val rule = definition.recurrenceRule ?: return@mapNotNull null
            if (rule.matches(retained.date)) retained.itemId to retained.date else null
        }.toSet()
        if (suppressed.isNotEmpty()) {
            planRepository.findRange(today, horizon)
                .filter { it.block.origin == BlockOrigin.RECURRING }
                .filter { it.block.isOpen && (it.item.id to it.block.date) in suppressed }
                .forEach { planRepository.removeBlock(it.block) }
        }
        val added = planRepository.materializeRecurring(
            definitions = definitions,
            from = today,
            to = horizon,
            vacations = vacationRepository.plansFrom(today),
            suppressed = suppressed,
            keep = keep,
        )
        // A dynamic growth task's hour is not a property of the task but of the
        // day it lands in, so a freshly materialized occurrence has no hour worth
        // keeping until this has run over it.
        relayDynamicGrowth(horizon)
        return added
    }

    /**
     * Puts every dynamic growth task of every day still open where the day says
     * it belongs.
     *
     * Recomputed rather than fixed at materialization, which is what the user
     * asked for: the whole point of dynamic placement is a gapless chain, and a
     * chain whose links were nailed down when they were created would come apart
     * the first time one of them grew. Cheap to call often — nothing is written
     * unless a block actually moved.
     *
     * **Only days that are still open.** Today is being lived and a confirmed
     * tomorrow is a promise already made; the same line every other edit to a
     * standing task respects. Returns how many blocks moved.
     */
    suspend fun relayDynamicGrowth(until: LocalDate? = null): Int {
        val from = planRepository.firstOpenDay()
        val to = maxOf(
            until ?: from,
            clock.todayIn(timeZone).plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)),
        )
        if (to < from) return 0

        val now = clock.now()
        val moved = planRepository.findRange(from, to)
            .groupBy { it.block.date }
            .values
            .flatMap { day -> placeDynamicGrowth(day) }
            .filter { it.moved }
            .map { move ->
                move.block.copy(start = minuteToLocalTime(move.toMinute), updatedAt = now)
            }
        if (moved.isEmpty()) return 0
        planRepository.addBlocks(moved)
        return moved.size
    }

    /**
     * Takes the still-open occurrences of [itemIds] off the days that are still
     * open and lays them down again from the definitions as they now stand.
     *
     * The rule every edit to a standing task follows. Days after today: as before,
     * what is still open from the first open day on. Today: only what has not
     * started yet is taken off ([isRelayableToday]), and an occurrence that would
     * start before now is not laid down again ([isLaidAfterEdit]). Today's
     * started, running, completed and called-off blocks stay.
     */
    suspend fun relayOccurrences(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        val now = clock.now().toLocalDateTime(timeZone)
        val edited = itemIds.toSet()
        planRepository.findDay(now.date)
            .map { it.block }
            .filter { isRelayableToday(it, edited, now) }
            .forEach { planRepository.removeBlock(it) }
        planRepository.clearUpcoming(itemIds, planRepository.firstOpenDay())
        topUp { isLaidAfterEdit(it, edited, now) }
    }
}
