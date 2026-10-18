package com.example.eta.domain.calendar

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.claimsItsSlot
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.containerStartMinute
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.overlaps

/**
 * Which planned blocks an undecided appointment would land on.
 *
 * Compared as the planner compares everything else: a block counts with its
 * journeys and break (its container), a finished or called-off block holds no
 * time and is left out, and a block on another day cannot clash. The appointment
 * itself is its plain stretch — it has no journey of its own in the calendar.
 *
 * Returns the clashing blocks per event id, and only for events that clash.
 * Events that are already decided are not asked about: an imported appointment
 * has its own block, which the planner already keeps clear of everything else.
 */
fun pendingClashes(
    events: List<CalendarEvent>,
    blocks: List<BlockWithItem>,
): Map<String, List<BlockWithItem>> {
    val standing = blocks.filter { it.claimsItsSlot() }
    return events
        .filter { it.isPending }
        .associate { event ->
            val start = event.start.minuteOfDay()
            val end = (start + event.duration.inWholeMinutes.toInt()).coerceAtMost(MINUTES_PER_DAY)
            event.id to standing
                .filter { it.block.date == event.date }
                .filter { entry ->
                    overlaps(
                        start,
                        end,
                        entry.block.containerStartMinute(),
                        entry.block.containerEndMinute(),
                    )
                }
                .sortedBy { it.block.containerStartMinute() }
        }
        .filterValues { it.isNotEmpty() }
}
