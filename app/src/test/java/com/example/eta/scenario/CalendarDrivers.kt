package com.example.eta.scenario

import com.example.eta.data.repository.SeriesProposal
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.data.repository.ConflictingBlock
import com.example.eta.data.repository.SyncOutcome
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.ui.attributes.AppointmentAttributes
import com.example.eta.ui.calendar.CalendarEventsViewModel
import com.example.eta.ui.calendar.ConflictPrompt
import com.example.eta.ui.root.calendarViewModelFactory
import kotlinx.datetime.LocalDate

/*
 * Calendar drivers. The Google side is the [FakeCalendar] of the scenario; the
 * sync, the series offers and the import are the app's own services and the
 * calendar step's own ViewModel. A driver only sequences them the way a screen does.
 */

/**
 * Connects the scenario's Google account, as "Verbinden" does: the grant, then the
 * calendar list. Fails unless Google answers.
 */
suspend fun Scenario.connectCalendar() {
    val outcome = container.calendarSyncService.connect()
    check(outcome is SyncOutcome.Ok) { "${stamp()}: the calendar did not connect: $outcome" }
    check(container.calendarRepository.isConnected()) { "${stamp()}: connected, but no calendar was stored" }
}

/**
 * The background pass over the calendar, as the hourly refresh runs it: reads the
 * planning window and returns the undecided appointments that now clash with the
 * plan and were not announced before.
 */
suspend fun Scenario.syncCalendar(): List<CalendarEvent> = container.calendarRefreshService.run()

/**
 * The calendar step over [from]..[to], as the weekly planning, the evening or the
 * day planner opens it, for the duration of [body].
 */
suspend fun Scenario.calendarStep(
    from: LocalDate = clock.today,
    to: LocalDate = clock.today,
    body: suspend CalendarStepDriver.() -> Unit,
) {
    val vm = viewModel<CalendarEventsViewModel>(calendarViewModelFactory(container, from, to), key = "calendar-$from-$to")
    subscribed(vm.uiState) { CalendarStepDriver(this@calendarStep, vm).body() }
}

/** The actions of the calendar step, on a step that has read the calendar. */
class CalendarStepDriver internal constructor(
    private val scenario: Scenario,
    private val vm: CalendarEventsViewModel,
) {
    /** Waits until the step has read the calendar; returns the series it offers, one per series. */
    suspend fun offers(): List<SeriesProposal> {
        scenario.awaitState(vm.uiState, "the calendar step read the calendar") { !it.syncing }
        return vm.proposals.value
    }

    /** "Übernehmen" on [offer]: the series becomes recurring tasks, with [attributes] as its form. */
    suspend fun adopt(offer: SeriesProposal.Offer, attributes: AppointmentAttributes = AppointmentAttributes()) {
        vm.openSeries(offer)
        vm.adoptSeries(attributes)
        scenario.awaitState(vm.adoptingSeries, "\"${offer.event.title}\" adoption completed") { !it }
        scenario.awaitState(vm.proposals, "\"${offer.event.title}\" taken on as a series") { proposals ->
            proposals.none { it.event.seriesId == offer.event.seriesId }
        }
    }

    /** "Einzeln lassen" on [offer]: the series stays a list of single appointments and is not offered again. */
    suspend fun keepSingle(offer: SeriesProposal.Offer) {
        vm.keepSeriesSingle(offer.event)
        scenario.awaitState(vm.proposals, "\"${offer.event.title}\" kept single") { proposals ->
            proposals.none { it.event.seriesId == offer.event.seriesId }
        }
    }

    /**
     * "Als ToDo übernehmen" on a single appointment, or "Als Zeitblocker" with [origin].
     * Returns the conflict prompt when the appointment landed on planned time.
     */
    suspend fun import(
        event: CalendarEvent,
        attributes: AppointmentAttributes = AppointmentAttributes(start = event.start, duration = event.duration),
        origin: BlockOrigin = BlockOrigin.CALENDAR_IMPORT,
    ): ConflictPrompt? {
        scenario.awaitState(vm.uiState, "\"${event.title}\" offered by the calendar step") { state ->
            state.events.any { it.id == event.id }
        }
        vm.import(event, attributes, origin)
        scenario.awaitState(vm.uiState, "\"${event.title}\" taken off the list") { state ->
            state.events.none { it.id == event.id }
        }
        return vm.conflicts.value
    }

    /** The collisions the last import left open, one per planned block it landed on. */
    fun conflicts(): List<ConflictingBlock> = vm.conflicts.value?.conflicts.orEmpty()

    /** The appointments that land on planned time, with the blocks they clash with, as the step names them. */
    suspend fun clashes(): Map<String, List<String>> =
        scenario.awaitState(vm.uiState, "the calendar step read the calendar") { !it.syncing }.clashes
}

/** The pending appointments of [from]..[to] as the calendar step lists them right now. */
suspend fun Scenario.pendingAppointments(from: LocalDate, to: LocalDate): List<CalendarEvent> =
    container.calendarRepository.pendingFor(from, to)
