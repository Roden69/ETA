package com.example.eta.domain.planning

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Duration

/**
 * The break a card takes with it onto the day: its own "Pause danach" or, for a
 * ToDo without one, the default from the settings ("Pause nach Aufgaben").
 *
 * Asked once, where a card is placed. The block stores the answer, and every
 * later question — the Pausenchecker, the overlap fit, the containers the day is
 * laid out with — reads it from the block, so they all agree with the placement.
 * Only a ToDo gets the default: a standing task and a calendar import bring their
 * own margins or none, and their occurrences are never placed by hand.
 */
fun effectiveBreakAfter(ownBreak: Duration?, type: ItemType, setup: UserSetup?): Duration? {
    ownBreak?.let { return it }
    if (type != ItemType.TODO) return null
    return setup?.defaultBreakAfter?.takeIf { it > Duration.ZERO }
}

/** [effectiveBreakAfter] for a card that already exists. */
fun effectiveBreakAfter(item: Item, setup: UserSetup?): Duration? =
    effectiveBreakAfter(item.breakAfter, item.type, setup)
