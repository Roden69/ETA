package com.example.eta.domain.chain

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import kotlin.time.Instant

/**
 * A follow-up that still waits for its predecessor: it has no [Item.enteredCollectionAt],
 * so it is not plannable and its one-month clock has not started.
 */
fun Item.isWaitingFollowUp(): Boolean =
    followUpOfId != null && stage == Stage.COLLECTION && enteredCollectionAt == null

/**
 * A follow-up its predecessor has released and that is still in the Sammelliste:
 * the step that is due now, and the one the app offers to take into the week.
 */
fun Item.isOfferedFollowUp(): Boolean =
    followUpOfId != null && stage == Stage.COLLECTION && enteredCollectionAt != null

/** Whether a predecessor counts as finished for its chain. */
fun Item.isFinishedForChain(): Boolean = stage == Stage.DONE || completedAt != null

/**
 * The stamp a new follow-up starts with. Released at once when its predecessor is
 * already finished — otherwise it would wait for a completion that has happened.
 */
fun followUpEnteredAt(predecessorFinished: Boolean, now: Instant): Instant? =
    if (predecessorFinished) now else null

/**
 * The follow-ups of the predecessor [predecessorId] after it was checked off
 * ([predecessorFinished] true) or ticked back (false). Returns only the rows that
 * change; follow-ups of any other predecessor in [followUps] are ignored.
 *
 * - Checked off: every waiting follow-up is released, and its one-month clock starts
 *   [now]. Nothing else moves, so the chain goes one step at a time.
 * - Ticked back: an offered follow-up that has not been taken into a week or a day
 *   is waiting again, with its clock stopped. One already taken on stays where it
 *   is: the user has committed to it, and the clock does not run back.
 */
fun followUpsAfter(
    predecessorId: String,
    predecessorFinished: Boolean,
    followUps: List<Item>,
    now: Instant,
): List<Item> =
    followUps.filter { it.followUpOfId == predecessorId }.mapNotNull { child ->
        when {
            predecessorFinished && child.isWaitingFollowUp() ->
                child.copy(enteredCollectionAt = now, updatedAt = now)

            !predecessorFinished && child.isOfferedFollowUp() ->
                child.copy(enteredCollectionAt = null, updatedAt = now)

            else -> null
        }
    }
