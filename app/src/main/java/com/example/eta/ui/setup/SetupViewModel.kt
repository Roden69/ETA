package com.example.eta.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.CalendarRepository
import com.example.eta.data.repository.CalendarSyncService
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SyncOutcome
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.setup.RoutinePlacement
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.SetupRoutine
import com.example.eta.domain.setup.longestAwakeMinutes
import com.example.eta.domain.setup.prefilledWeek
import com.example.eta.domain.setup.setupCalendarLastDay
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class SetupViewModel(
    private val setupRepository: SetupRepository,
    private val calendarRepository: CalendarRepository,
    private val calendarSync: CalendarSyncService,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val _draft = MutableStateFlow(RoutineSetup.draft(clock.now()))
    val draft: StateFlow<RoutineSetup> = _draft.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Set before publishing setup so the root shows the standing-week review first. */
    private val _weekReview = MutableStateFlow(false)
    val weekReview: StateFlow<Boolean> = _weekReview.asStateFlow()

    private val today: LocalDate = clock.todayIn(timeZone)
    private val lastDay: LocalDate = setupCalendarLastDay(today)

    /**
     * The undecided appointments of the setup week, as the calendar last reported
     * them. Live, so a read finished in the background shows up in the grid.
     */
    val calendarEvents: StateFlow<List<CalendarEvent>> = calendarRepository
        .observePending(today, lastDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Appointments hidden for this setup only. Not `CalendarRepository.ignore`: that
     * decision is kept for good and would also remove the appointment from the
     * planning steps. Here it is a view choice that lasts as long as the draft.
     */
    private val _hiddenEvents = MutableStateFlow<Set<String>>(emptySet())
    val hiddenEvents: StateFlow<Set<String>> = _hiddenEvents.asStateFlow()

    private val _calendarBusy = MutableStateFlow(false)
    val calendarBusy: StateFlow<Boolean> = _calendarBusy.asStateFlow()

    /** What the last read of the calendar could not do, in a sentence. Null when all was well. */
    private val _calendarNote = MutableStateFlow<String?>(null)
    val calendarNote: StateFlow<String?> = _calendarNote.asStateFlow()

    /** Routines that already got their prefilled slot; see [enterWeek]. */
    private val prefilledRoutines = mutableSetOf<String>()

    fun endWeekReview() {
        _weekReview.value = false
    }

    fun toggleRoutine(routine: SetupRoutine) = _draft.update { it.toggleRoutine(routine) }

    fun addRoutine(name: String) = _draft.update { it.addRoutine(name) }

    fun removeRoutine(id: String) = _draft.update { it.removeRoutine(id) }

    fun savePlacement(placement: RoutinePlacement) = _draft.update { it.withPlacement(placement) }

    fun deletePlacement(id: String) = _draft.update { it.withoutPlacement(id) }

    /** The ordinary night from the first page; it lays one provisional night per weekday. */
    fun changeWeekdayNight(sleep: LocalTime, wake: LocalTime) =
        _draft.update { if (sleep == wake) it else it.withWeekdayNight(sleep, wake) }

    fun changeWeekendNight(sleep: LocalTime, wake: LocalTime) =
        _draft.update { if (sleep == wake) it else it.withWeekendNight(sleep, wake) }

    fun changeWeekendDays(days: Set<DayOfWeek>) = _draft.update { it.withWeekendDays(days) }

    /** One night of the calendar on its own, the one that ends on [wakeDay]. */
    fun changeNight(wakeDay: DayOfWeek, sleep: LocalTime, wake: LocalTime) =
        _draft.update { if (sleep == wake) it else it.withNight(wakeDay, sleep, wake) }

    fun hideEvent(eventId: String) = _hiddenEvents.update { it + eventId }

    fun showAllEvents() = _hiddenEvents.update { emptySet() }

    /**
     * Opens the week step: reads the coming week's appointments if a calendar is
     * connected, then fills the template with the routines that have no slot yet.
     *
     * Failure to read is a note, never a reason to stop: the setup goes on with
     * whatever the calendar last stored, or without it.
     */
    fun enterWeek() {
        viewModelScope.launch {
            _calendarBusy.value = true
            _calendarNote.value = describe(calendarSync.refresh(today, lastDay))
            val hidden = _hiddenEvents.value
            val events = calendarRepository.pendingFor(today, lastDay).filterNot { it.id in hidden }
            // No suspension between reading the draft and writing it back.
            val week = _draft.value.prefilledWeek(events, prefilledRoutines)
            _draft.value = week.draft
            prefilledRoutines += week.routineIds
            _calendarBusy.value = false
        }
    }

    fun finish() {
        val draft = _draft.value
        if (_saving.value || draft.unscheduledRoutines.isNotEmpty() ||
            draft.overlappingSleep().isNotEmpty() || draft.setup.longestAwakeMinutes() > 24 * 60
        ) return
        _saving.value = true
        _weekReview.value = true
        viewModelScope.launch {
            try {
                // Publishing the setup row unlocks the root screen only after its schedule exists.
                setupRepository.complete(draft.setup, draft.recurringItems(clock.now()))
            } finally {
                _saving.value = false
            }
        }
    }

    private fun describe(outcome: SyncOutcome): String? = when (outcome) {
        is SyncOutcome.Ok, SyncOutcome.NotConnected -> null
        is SyncOutcome.NeedsConsent ->
            "Google braucht eine neue Freigabe. Verbinde den Kalender auf der vorigen Seite erneut. " +
                "Die Einrichtung geht auch ohne Termine weiter."
        is SyncOutcome.Failed ->
            "Termine konnten nicht geladen werden: ${outcome.reason}. Die Einrichtung geht trotzdem weiter."
    }
}
