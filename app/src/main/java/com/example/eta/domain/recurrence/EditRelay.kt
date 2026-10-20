package com.example.eta.domain.recurrence

import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.planning.timerWasUsed
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime

/** When the block begins, as a wall-clock moment on its own date. */
fun PlannedBlock.startsAt(): LocalDateTime = date.atTime(start)

/**
 * Whether the user placed this occurrence by hand: dragged or made flexible
 * ([PlannedBlock.isMovable]), or moved or resized away from the hour and length
 * the schedule laid it down with ([PlannedBlock.originalStart],
 * [PlannedBlock.originalDuration], both stamped at materialization and never
 * written again). A block without a recorded original is not counted as moved.
 *
 * Known edge: a block laid down under a vacation's start override counts as
 * moved, so it is kept rather than re-laid.
 */
fun PlannedBlock.isPlacedByHand(): Boolean =
    isMovable ||
        (originalStart != null && start != originalStart) ||
        (originalDuration != null && plannedDuration != originalDuration)

/**
 * Whether an existing block of **today** is laid down again by an edit to the
 * definitions in [edited].
 *
 * Only what has not begun and was not placed by hand: open (neither completed nor
 * called off), no timer ever started, not moved by hand, and a start strictly
 * after [now]. A block starting exactly at [now] already counts as begun.
 * Everything else on today stays as it is.
 */
fun isRelayableToday(block: PlannedBlock, edited: Set<String>, now: LocalDateTime): Boolean =
    block.itemId in edited &&
        block.date == now.date &&
        block.isOpen &&
        !block.timerWasUsed &&
        !block.isPlacedByHand() &&
        block.startsAt() > now

/**
 * Whether a freshly laid occurrence may stay on today.
 *
 * An occurrence of an edited definition that would start before [now] is not laid
 * down today: it would be an open block the day has already passed, which the
 * evening would count as missed. Occurrences of other definitions, and every
 * occurrence on other days, are unaffected.
 */
fun isLaidAfterEdit(block: PlannedBlock, edited: Set<String>, now: LocalDateTime): Boolean =
    block.itemId !in edited || block.date != now.date || block.startsAt() >= now
