package com.example.eta.data.repository

import com.example.eta.domain.calendar.pendingClashes
import com.example.eta.domain.calendar.planningWindow
import com.example.eta.domain.model.CalendarEvent
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * The background pass over the calendar: read the planning window again, and say
 * which undecided appointments have landed on the plan since last time.
 *
 * Runs without an Activity, so it never asks anybody anything. Consent, network
 * and refusal failures come back as "nothing happened" and leave the last state
 * exactly as it was — the next run, or the planning step, reports them in place.
 * Nothing about the outcome is stored, so a silent failure cannot pose as a
 * status either.
 *
 * The window is [planningWindow]: today through the day before the next weekly
 * planning day, the same stretch the planning steps read.
 */
class CalendarRefreshService(
    private val syncService: CalendarSyncService,
    private val calendarRepository: CalendarRepository,
    private val planRepository: PlanRepository,
    private val setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /**
     * Refreshes the window and returns the undecided appointments that clash with
     * the plan and have not been announced before. Each is marked announced as it
     * is returned, so one clash is one notification, not one per run.
     */
    suspend fun run(): List<CalendarEvent> {
        if (!calendarRepository.isConnected()) return emptyList()
        val setup = setupRepository.find() ?: return emptyList()
        val window = planningWindow(clock.todayIn(timeZone), setup.weeklyPlanningDay)

        val outcome = syncService.refresh(window.from, window.to)
        if (outcome !is SyncOutcome.Ok) return emptyList()

        val pending = calendarRepository.pendingFor(window.from, window.to)
        val clashing = pendingClashes(pending, planRepository.findRange(window.from, window.to)).keys
        val fresh = pending.filter { it.id in clashing && !it.conflictNotified }
        fresh.forEach { calendarRepository.markConflictNotified(it) }
        return fresh
    }
}
