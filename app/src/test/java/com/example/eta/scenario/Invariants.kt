package com.example.eta.scenario

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.Stage
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
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
 * | 3 | Every TODO in `Stage.WEEK` is concretized (category, priority and length) | reads the week list |
 * | 4 | Balance equals the sum of transactions; one `HARVEST` per source block | recomputed from the rows |
 * | 5 | Completed and discarded blocks are unchanged by later steps | compares with the previous check |
 * | 6 | No recurring block on a vacation day or after `repeatUntil` | checks the recurring blocks against the standing rules |
 * | 7 | No TODO is lost: each sits in exactly one of the five stages, and each TODO of `Stage.DAY` has a block | reads every stage list; blocks of any state count |
 * | 8 | The Sperrliste clock is kept: `enteredCollectionAt` never changes once set | snapshot compare, across every stage |
 */
class Invariants internal constructor(private val scenario: Scenario) {
    /** Completed or discarded blocks as the previous check saw them. */
    private var frozen: Map<String, PlannedBlock> = emptyMap()

    /** Each card's `enteredCollectionAt` as the first check that saw it set it. */
    private val enteredSnapshot = mutableMapOf<String, Instant>()

    /**
     * Runs every invariant and fails with all violations found.
     *
     * Invariant 3 is a hard rule: the revolver can offer nothing but concretized
     * cards, so an unconcretized card in the week list is a broken state, whatever
     * its unlock day or follow-up status says.
     */
    suspend fun check(step: String) {
        val container = scenario.container
        val today = scenario.clock.today
        val blocks = container.planRepository.findRange(today.minus(HORIZON), today.plus(HORIZON))
        val stages: Map<Stage, List<Item>> = Stage.entries.associateWith { container.itemRepository.observeStage(it).first() }
        val items = stages.values.flatten()
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

        // 3 — every TODO in the week list is concretized
        stages.getValue(Stage.WEEK)
            .filter { it.type == ItemType.TODO && !it.isConcretized }
            .forEach { violations += "\"${it.name}\" is in the week list without category, priority or length (invariant 3)" }

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
            val now = current[id]
            // Reopened on purpose (an untick): the block is open again and no longer closed.
            if (now != null && now.isOpen) return@forEach
            if (now != before) {
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

        // 7 — no TODO lost: one stage each, and every planned-for-today TODO has a block
        val seen = mutableSetOf<String>()
        stages.forEach { (stage, cards) ->
            cards.filter { it.type == ItemType.TODO }.forEach { card ->
                if (!seen.add(card.id)) {
                    violations += "\"${card.name}\" is listed in more than one stage, now $stage (invariant 7)"
                }
            }
        }
        val blocked = blocks.map { it.block.itemId }.toSet()
        stages.getValue(Stage.DAY)
            .filter { it.type == ItemType.TODO && it.id !in blocked }
            .forEach { violations += "\"${it.name}\" is in the day list without any block (invariant 7)" }

        // 8 — the Sperrliste clock is kept
        items.forEach { card ->
            // A follow-up's clock is stamped by its predecessor and undone by an untick.
            if (card.followUpOfId != null) return@forEach
            val entered = card.enteredCollectionAt ?: return@forEach
            val before = enteredSnapshot.putIfAbsent(card.id, entered)
            if (before != null && before != entered) {
                violations += "\"${card.name}\" changed its time in the Sammelliste from $before to $entered (invariant 8)"
            }
        }

        if (violations.isNotEmpty()) {
            throw AssertionError(
                violations.joinToString(separator = "\n") { "${scenario.stamp()} – $step: $it" },
            )
        }
    }

    private companion object {
        val HORIZON = DatePeriod(days = 400)
        const val TOLERANCE = 0.000001
    }
}
