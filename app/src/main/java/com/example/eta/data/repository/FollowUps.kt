package com.example.eta.data.repository

import com.example.eta.data.local.ItemDao
import com.example.eta.domain.chain.followUpEnteredAt
import com.example.eta.domain.chain.followUpsAfter
import com.example.eta.domain.chain.isFinishedForChain
import com.example.eta.domain.model.Item
import kotlin.time.Instant

/**
 * Applies the chain rules to the follow-ups of [predecessorId], writing only the rows
 * that change. Called wherever a predecessor is checked off or ticked back, so the
 * rule lives in one place however the completion was reached.
 */
internal suspend fun ItemDao.applyFollowUpTransition(predecessorId: String, finished: Boolean, now: Instant) {
    val changed = followUpsAfter(predecessorId, finished, findFollowUpsOf(predecessorId), now)
    if (changed.isNotEmpty()) upsertAll(changed)
}

/**
 * Writes [names] as a chain after [predecessor], each waiting for the one before it.
 *
 * The first one is released at once only when the predecessor is already finished;
 * every later one waits for its own predecessor, so the order is enforced.
 */
internal suspend fun ItemDao.writeFollowUps(predecessor: Item, names: List<String>, now: Instant) {
    var previous = predecessor
    var finished = predecessor.isFinishedForChain()
    val rows = names.map { it.trim() }.filter { it.isNotEmpty() }.map { name ->
        Item.newFollowUp(name, previous, now, followUpEnteredAt(finished, now)).also {
            previous = it
            finished = false
        }
    }
    if (rows.isNotEmpty()) upsertAll(rows)
}
