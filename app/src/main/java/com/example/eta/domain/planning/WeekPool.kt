package com.example.eta.domain.planning

import com.example.eta.domain.chain.isWaitingFollowUp
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.Stage
import kotlinx.datetime.LocalDate

/** Why a Sammelliste ToDo cannot be taken into the week right now. */
sealed interface PullBlock {
    /** Its unlock day lies after the week ends, so it cannot be planned this week. */
    data class NotYetAvailable(val from: LocalDate) : PullBlock

    /** Goals of an earlier cycle are still unplanned in the week list. */
    data class OpenGoals(val count: Int) : PullBlock

    /** A follow-up whose predecessor is not finished yet. */
    data object AfterPredecessor : PullBlock
}

/** What the weekly planning can offer from the Sammelliste, and what it holds back. */
data class WeekPool(
    val pullable: List<Item>,
    val held: List<Pair<Item, PullBlock>>,
)

/**
 * The reason [item] cannot go into the week ending on [weekEnd], or null when it can.
 *
 * A waiting follow-up comes first: nothing about the week makes it due. Then the
 * unlock day, which is a property of the card, before the open goals, which are a
 * property of the week — the card's own reason is the one waiting can resolve.
 */
fun pullBlockOf(item: Item, weekEnd: LocalDate, openGoals: Int): PullBlock? = when {
    item.isWaitingFollowUp() -> PullBlock.AfterPredecessor
    !item.isAvailableOn(weekEnd) -> PullBlock.NotYetAvailable(checkNotNull(item.availableFrom))
    openGoals > 0 -> PullBlock.OpenGoals(openGoals)
    else -> null
}

/**
 * The Sammelliste as the weekly planning shows it: every ToDo either pullable or
 * held back with its reason, so that nothing disappears from the screen silently.
 *
 * Recurring notes are left out: they are not week goals, they belong to the
 * standing schedule. The order of the input is kept.
 */
fun weekPool(collection: List<Item>, weekEnd: LocalDate, openGoals: Int): WeekPool {
    val pullable = mutableListOf<Item>()
    val held = mutableListOf<Pair<Item, PullBlock>>()
    collection.filter { it.type == ItemType.TODO }.forEach { item ->
        val block = pullBlockOf(item, weekEnd, openGoals)
        if (block == null) pullable += item else held += item to block
    }
    return WeekPool(pullable, held)
}

/**
 * The stage a card lands in when it is taken into the week.
 *
 * Every ToDo in `Stage.WEEK` is concretized: the revolver offers only finished cards,
 * so a bare one in the week could never be planned. An unfinished card therefore
 * stays in the Sammelliste until its answers are given, and only then goes on.
 */
fun weekStageOf(item: Item): Stage = if (item.isConcretized) Stage.WEEK else Stage.COLLECTION
