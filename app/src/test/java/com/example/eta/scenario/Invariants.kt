package com.example.eta.scenario

import com.example.eta.domain.chain.isWaitingFollowUp
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.Stage
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Invariants that must hold after every [Scenario.step], whatever the flow did.
 *
 * Each check reads the database through the repositories, never through the
 * ViewModels, so a broken screen cannot hide a broken ledger. A failure names the
 * step and the day and time it happened, e.g.
 * `Tag 9 (Di 27.10.) 14:00 – evening settled: more than one HARVEST for block …`.
 *
 * | # | Invariant | How it is checked |
 * |---|-----------|-------------------|
 * | 1 | At most one block per `(itemId, date)` | groups the blocks read by date |
 * | 2 | No block without its item | every block is read through its item; only a block whose item row is missing fails |
 * | 3 | Every TODO in `Stage.WEEK` is placed, offered, or has a visible reason | partial, see [check] |
 * | 4 | Balance equals the sum of transactions; one `HARVEST` per source block | recomputed from the rows |
 * | 5 | Completed and discarded blocks are unchanged by later steps | compares with the previous check |
 * | 6 | No recurring block on a vacation day or after `repeatUntil` | checks the recurring blocks against the standing rules |
 *
 * Invariants a known open bug breaks are gated by [KnownOpenIssues]; they are not
 * dropped silently.
 */
class Invariants internal constructor(private val scenario: Scenario) {
    /** Completed or discarded blocks as the previous check saw them. */
    private var frozen: Map<String, com.example.eta.domain.model.PlannedBlock> = emptyMap()

    /**
     * Runs every invariant and fails with all violations found.
     *
     * Invariant 3 is deliberately partial. The day revolver's own selection is not
     * re-run here. What is checked: every unplaced TODO in `Stage.WEEK` must be
     * concretized (category, priority and length), because the revolver can offer
     * nothing else. Every other unplaced card needs one of these visible reasons,
     * which the screens show, and the check accepts it:
     * - its unlock day lies after today ("freigeschaltet ab");
     * - it waits for its predecessor (a follow-up);
     * - a higher priority tier is still offered, so it is held back by the tier.
     */
    suspend fun check(step: String) {
        val container = scenario.container
        val today = scenario.clock.today
        val weekEnd = container.weekPlanningService.weekEnd()
        val blocks = container.planRepository.findRange(today.minus(HORIZON), today.plus(HORIZON))
        val items = Stage.entries.flatMap { container.itemRepository.observeStage(it).first() }
        val violations = mutableListOf<String>()

        // 1 — one block per (item, date)
        blocks.groupBy { it.block.itemId to it.block.date }
            .filterValues { it.size > 1 }
            .keys
            .forEach { (itemId, date) -> violations += "two blocks for item $itemId on $date (invariant 1)" }

        // 2 — every block belongs to an item that exists
        val itemIds = items.map { it.id }.toSet()
        blocks.filterNot { it.item.id in itemIds }.forEach {
            violations += "block ${it.block.id} has no item in the database (invariant 2)"
        }

        // 3 — every TODO in the week list is placed, offered or has a visible reason
        val placed = blocks.map { it.block.itemId }.toSet()
        items.filter { it.stage == Stage.WEEK && it.type == ItemType.TODO && it.id !in placed }
            .forEach { item ->
                val concretized = item.priority != null && item.category != null && item.estimatedDuration != null
                val unlocksLater = item.availableFrom?.let { it > today } == true
                val waits = item.isWaitingFollowUp()
                if (!concretized && !unlocksLater && !waits) {
                    violations += "\"${item.name}\" is in the week list without category, priority or length (invariant 3)"
                }
            }

        // 4 — ledger
        val transactions = container.pointsRepository.observeRecent(Int.MAX_VALUE).first()
        val balance = container.pointsRepository.observeBalance().first()
        val summed = transactions.sumOf { it.amount }
        if (abs(summed - balance) > TOLERANCE) {
            violations += "balance $balance differs from the sum of transactions $summed (invariant 4)"
        }
        // The evening writes one HARVEST per settled day, with the date as its note.
        transactions.filter { it.reason == PointsReason.HARVEST }
            .groupBy { it.note }
            .filterValues { it.size > 1 }
            .keys
            .forEach { day -> violations += "more than one HARVEST booked for $day (invariant 4)" }

        // 5 — completed and discarded blocks stay as they were
        val current = blocks.associate { it.block.id to it.block }
        frozen.forEach { (id, before) ->
            if (current[id] != before) {
                violations += "block of \"${before.itemId}\" on ${before.date} changed after it was closed (invariant 5)"
            }
        }
        frozen = blocks.map { it.block }.filterNot { it.isOpen }.associateBy { it.id }

        // 6 — recurring blocks respect vacations and their end date
        val vacations = container.vacationRepository.plansFrom(today.minus(HORIZON))
        blocks.filter { it.block.origin == BlockOrigin.RECURRING }.forEach { entry ->
            val until = entry.item.repeatUntil
            if (until != null && entry.block.date > until) {
                violations += "\"${entry.item.name}\" on ${entry.block.date} is after its end $until (invariant 6)"
            }
            if (vacations.any { it.covers(entry.block.date) }) {
                violations += "\"${entry.item.name}\" on ${entry.block.date} falls into a vacation (invariant 6)"
            }
        }

        if (KnownOpenIssues.STALE_WEEK_GOALS_MUST_NOT_HOLD_POOL) {
            checkStaleGoalsDoNotHoldThePool(violations, items, weekEnd)
        }

        if (violations.isNotEmpty()) {
            throw AssertionError(
                violations.joinToString(separator = "\n") { "${scenario.stamp()} – $step: $it" },
            )
        }
    }

    /** Issue #54: goals of an earlier cycle must not hold back a Sammelliste card that is free on its own. */
    private suspend fun checkStaleGoalsDoNotHoldThePool(
        violations: MutableList<String>,
        items: List<Item>,
        weekEnd: LocalDate,
    ) {
        val cycleStart = scenario.container.weekPlanningService.cycleStart()
        val stale = scenario.container.itemRepository.observeUnfinishedWeekGoals(cycleStart).first()
            .filter { (it.weekStartedOn ?: cycleStart) < cycleStart }
        if (stale.isEmpty()) return
        val freeOnItsOwn = items.count {
            it.stage == Stage.COLLECTION && it.type == ItemType.TODO &&
                it.isConcretized && it.isAvailableOn(weekEnd) && !it.isWaitingFollowUp()
        }
        if (freeOnItsOwn > 0) {
            violations += "${stale.size} goal(s) of an earlier cycle hold back $freeOnItsOwn free Sammelliste card(s) (issue #54)"
        }
    }

    private companion object {
        val HORIZON = DatePeriod(days = 400)
        const val TOLERANCE = 0.000001
    }
}

/**
 * Invariants that current `main` breaks because of an open bug.
 *
 * Each flag turns its check on. The fix for the named issue sets it to `true` in
 * the same change, so the regression guard arrives with the fix.
 */
object KnownOpenIssues {
    /**
     * Issue #54: a goal of an earlier cycle (`weekStartedOn` before this cycle)
     * holds back the whole Sammelliste in the weekly planning. Off until fixed.
     */
    const val STALE_WEEK_GOALS_MUST_NOT_HOLD_POOL = false
}
