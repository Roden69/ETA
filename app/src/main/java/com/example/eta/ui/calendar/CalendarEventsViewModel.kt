package com.example.eta.ui.calendar

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.AppointmentDetails
import com.example.eta.data.repository.CalendarImportService
import com.example.eta.data.repository.CalendarRepository
import com.example.eta.data.repository.CalendarSyncService
import com.example.eta.data.repository.ConflictingBlock
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.SeriesOutcome
import com.example.eta.data.repository.SeriesProposal
import com.example.eta.data.repository.SyncOutcome
import com.example.eta.domain.calendar.SeriesPattern
import com.example.eta.domain.calendar.SeriesReading
import com.example.eta.domain.calendar.pendingClashes
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.ui.attributes.AppointmentAttributes
import com.example.eta.ui.format.formatClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The appointment that was just imported, and what it landed on.
 *
 * Held until every collision has an answer: the imported block keeps its slot
 * whatever happens — a calendar entry is a fact — but the recurring task now
 * underneath it is a decision, and the user makes it one at a time.
 */
data class ConflictPrompt(
    val appointment: String,
    /** The appointment's hours, "12:00–13:00", so each collision can name its own. */
    val time: String,
    val conflicts: List<ConflictingBlock>,
    /**
     * Why the last answer did not take, if it did not.
     *
     * Only "verschieben" and "aufteilen" can fail, and only for a reason the screen
     * can name: answering an earlier conflict filled the stretch this one was going
     * to move into, or the block already has a catch-up. Saying so beats moving the
     * block somewhere nobody chose.
     */
    val refusal: String? = null,
)

/** A series the user asked to take on, once Google has said how it repeats. */
data class SeriesOffer(val event: CalendarEvent, val pattern: SeriesPattern)

/** Why a series could not be taken on, shown on the card it was asked from. */
data class SeriesNotice(val eventId: String, val reason: String)

data class CalendarStepUiState(
    val connected: Boolean = false,
    /** A sync is under way. The first one runs on the way into the screen. */
    val syncing: Boolean = true,
    val events: List<CalendarEvent> = emptyList(),
    /** Why the last sync did not work, if it did not. */
    val error: String? = null,
    /**
     * For each undecided appointment that lands on planned time: the blocks it
     * would clash with, as the card names them. Only clashing events are keys.
     */
    val clashes: Map<String, List<String>> = emptyMap(),
) {
    /** Nothing left to decide — everything the calendar offers is accounted for. */
    val settled: Boolean get() = !syncing && events.isEmpty()
}

/**
 * The calendar step, in both the places it appears.
 *
 * The weekly planning runs it over the stretch it reads — today through the day
 * before the next planning day — and the evening runs it over tomorrow, to catch
 * what was put in the calendar during the day. They differ only in [from] and
 * [to]: the question, "what does the calendar offer that this app has never been
 * told about", is the same one, and the answer is the same query.
 *
 * It runs **before** the day or the week is filled with ToDos, which is the whole
 * reason it exists as a step rather than as a background sync: an appointment
 * that arrives after the planning is a collision, and one that arrives before it
 * is simply part of the picture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarEventsViewModel(
    private val calendarRepository: CalendarRepository,
    private val planRepository: PlanRepository,
    private val syncService: CalendarSyncService,
    private val importService: CalendarImportService,
    private val scheduleMaintenance: ScheduleMaintenance,
    val from: LocalDate,
    val to: LocalDate,
) : ViewModel() {

    private val syncing = MutableStateFlow(true)
    private val connected = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val _consent = MutableStateFlow<PendingIntent?>(null)

    /** The screen Google wants shown. Only an Activity can launch it. */
    val consent: StateFlow<PendingIntent?> = _consent.asStateFlow()

    private val _conflicts = MutableStateFlow<ConflictPrompt?>(null)
    val conflicts: StateFlow<ConflictPrompt?> = _conflicts.asStateFlow()

    private val _seriesOffer = MutableStateFlow<SeriesOffer?>(null)

    /** The series dialog, once Google has said how the series repeats. */
    val seriesOffer: StateFlow<SeriesOffer?> = _seriesOffer.asStateFlow()

    private val _seriesNotice = MutableStateFlow<SeriesNotice?>(null)
    val seriesNotice: StateFlow<SeriesNotice?> = _seriesNotice.asStateFlow()

    private val _proposals = MutableStateFlow<List<SeriesProposal>>(emptyList())

    /**
     * The series the step offers, one per series: an offer to take it on, or the
     * reason Eta cannot. Read from the undecided appointments, without a tap.
     */
    val proposals: StateFlow<List<SeriesProposal>> = _proposals.asStateFlow()

    val uiState: StateFlow<CalendarStepUiState> = combine(
        calendarRepository.observePending(from, to),
        planRepository.observeRange(from, to),
        syncing,
        connected,
        error,
    ) { events, blocks, isSyncing, isConnected, failure ->
        CalendarStepUiState(
            connected = isConnected,
            syncing = isSyncing,
            events = events,
            error = failure,
            clashes = pendingClashes(events, blocks).mapValues { (_, entries) -> entries.map { it.describe() } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarStepUiState(),
    )

    init {
        viewModelScope.launch {
            // The stretch has to exist before a collision with it can be seen: an
            // appointment cannot clash with a recurring task nobody has laid down
            // yet. Idempotent, so calling it here costs nothing.
            scheduleMaintenance.topUpUntil(to)
            refresh()
        }
    }

    /**
     * Reads the calendar again.
     *
     * Silent about not being connected: the evening walks through this step every
     * day, and someone who never linked an account must not meet an error for a
     * feature they are not using.
     */
    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            error.value = null
            when (val outcome = syncService.refresh(from, to)) {
                is SyncOutcome.Ok -> {
                    connected.value = true
                    // The schedule follows what the calendar now shows, so a holiday of
                    // an adopted series stops carrying its block here, not at next start.
                    scheduleMaintenance.topUpUntil(to)
                    reloadProposals()
                }
                is SyncOutcome.NotConnected -> connected.value = false
                is SyncOutcome.NeedsConsent -> {
                    connected.value = true
                    _consent.value = outcome.pendingIntent
                }

                is SyncOutcome.Failed -> {
                    connected.value = calendarRepository.isConnected()
                    error.value = outcome.reason
                }
            }
            syncing.value = false
        }
    }

    /** The consent screen came back; carry on where the sync left off. */
    fun onConsent(data: Intent?) {
        _consent.value = null
        val series = seriesAwaitingConsent
        seriesAwaitingConsent = null
        viewModelScope.launch {
            when (val outcome = syncService.completeConsent(data)) {
                is SyncOutcome.Failed -> error.value = outcome.reason
                else -> {
                    refresh()
                    // The series question that needed the grant is asked again now.
                    series?.let(::requestSeries)
                }
            }
        }
    }

    /** The appointment whose series was being read when Google asked for consent. */
    private var seriesAwaitingConsent: CalendarEvent? = null

    fun dismissConsent() {
        _consent.value = null
        seriesAwaitingConsent = null
    }

    /** Not the app's business. It leaves the list and never comes back as new. */
    fun ignore(event: CalendarEvent) {
        viewModelScope.launch { calendarRepository.ignore(event) }
    }

    /**
     * Turns the event into a card on its day — and, if that lands on something,
     * asks what should happen to what was there.
     *
     * [origin] is [BlockOrigin.CALENDAR_BLOCKER] for "Als Zeitblocker (ohne
     * Punkte)": the same appointment, holding its time, paying nothing.
     */
    fun import(
        event: CalendarEvent,
        attributes: AppointmentAttributes,
        origin: BlockOrigin = BlockOrigin.CALENDAR_IMPORT,
    ) {
        viewModelScope.launch {
            val result = importService.import(
                event = event,
                details = attributes.toDetails(),
                origin = origin,
            )
            if (result.conflicts.isNotEmpty()) {
                _conflicts.value = ConflictPrompt(
                    appointment = event.title,
                    time = event.hours(),
                    conflicts = result.conflicts,
                )
            }
        }
    }

    /**
     * Asks Google how the series of [event] repeats, and offers to take it on.
     *
     * Only asked on demand: the sync does not read masters. A series Eta cannot
     * represent answers with the reason, and nothing is made.
     */
    fun requestSeries(event: CalendarEvent) {
        if (event.seriesId == null) return
        viewModelScope.launch {
            _seriesNotice.value = null
            when (val outcome = syncService.readSeries(event)) {
                is SeriesOutcome.Ok -> when (val reading = outcome.reading) {
                    is SeriesReading.Repeats -> _seriesOffer.value = SeriesOffer(event, reading.pattern)
                    is SeriesReading.Unsupported ->
                        _seriesNotice.value = SeriesNotice(event.id, reading.reason)
                }

                is SeriesOutcome.NeedsConsent -> {
                    seriesAwaitingConsent = event
                    _consent.value = outcome.pendingIntent
                }
                is SeriesOutcome.Failed -> _seriesNotice.value = SeriesNotice(event.id, outcome.reason)
            }
        }
    }

    fun dismissSeries() {
        _seriesOffer.value = null
    }

    /** Re-reads the offers from the undecided appointments as they stand now. */
    private suspend fun reloadProposals() {
        _proposals.value = syncService.proposeSeries(calendarRepository.pendingFor(from, to))
    }

    /** "Übernehmen" on an offer: the same form as from an occurrence, for the whole series. */
    fun openSeries(offer: SeriesProposal.Offer) {
        _seriesOffer.value = SeriesOffer(offer.event, offer.pattern)
    }

    /**
     * "Einzeln lassen" on an offer, and "Verstanden" on a refusal: the series stays a
     * list of single appointments and is not asked about again.
     */
    fun keepSeriesSingle(event: CalendarEvent) {
        val seriesId = event.seriesId ?: return
        viewModelScope.launch {
            calendarRepository.declineSeries(event.calendarId, seriesId)
            reloadProposals()
        }
    }

    /**
     * Makes the series into recurring tasks, one per weekday, with the hour and
     * length the form gives. The offer is cleared first, so a second tap cannot
     * make the same cards twice.
     */
    fun adoptSeries(attributes: AppointmentAttributes) {
        val offer = _seriesOffer.value ?: return
        _seriesOffer.value = null
        viewModelScope.launch {
            importService.adoptSeries(offer.event, offer.pattern, attributes.toDetails())
            reloadProposals()
        }
    }

    /** The collision's first answer: the other thing is not happening. */
    fun cancelConflict(entry: BlockWithItem) {
        viewModelScope.launch {
            importService.cancelConflicting(entry)
            dropConflict(entry)
        }
    }

    /** The second: do it another day, out of the week list. */
    fun carryConflictIntoWeek(entry: BlockWithItem) {
        viewModelScope.launch {
            importService.carryConflictingIntoWeek(entry)
            dropConflict(entry)
        }
    }

    /**
     * The third: keep the activity, but end it where the appointment begins.
     *
     * The common case, and the reason the two whole-activity answers were not
     * enough — an appointment at noon does not usually cancel a morning.
     */
    fun shortenConflict(conflict: ConflictingBlock) {
        val shortened = conflict.shortened ?: return
        viewModelScope.launch {
            importService.shortenConflicting(conflict.entry, shortened.plannedDuration)
            dropConflict(conflict.entry)
        }
    }

    /** The fourth: keep it whole, and start it once the appointment is over. */
    fun pushConflict(conflict: ConflictingBlock) {
        val pushed = conflict.pushed ?: return
        viewModelScope.launch {
            if (importService.moveConflictingAfter(conflict.entry, pushed.start)) {
                dropConflict(conflict.entry)
            } else {
                // Re-checked at the moment it is applied, not at import: an
                // earlier answer in this same dialog can have taken the stretch.
                _conflicts.value = _conflicts.value?.copy(
                    refusal = "Der Zeitraum ist inzwischen anderweitig belegt.",
                )
            }
        }
    }

    /**
     * The fifth: the part before the appointment stays, the part behind it becomes
     * a catch-up in the week list. The series is not touched.
     */
    fun splitConflict(conflict: ConflictingBlock) {
        val shortened = conflict.shortened ?: return
        val back = conflict.split ?: return
        // A second tap before the first has written would pass the repository's
        // "already has a catch-up" check too and make a second card.
        if (!splitting.add(conflict.entry.block.id)) return
        viewModelScope.launch {
            try {
                if (importService.splitConflicting(conflict.entry, shortened.plannedDuration, back)) {
                    dropConflict(conflict.entry)
                } else {
                    _conflicts.value = _conflicts.value?.copy(
                        refusal = "Für diese Aufgabe gibt es schon ein Nachholen. Wähle eine andere Antwort.",
                    )
                }
            } finally {
                splitting.remove(conflict.entry.block.id)
            }
        }
    }

    /** Blocks whose split is being written, by id. */
    private val splitting = mutableSetOf<String>()

    /** Leaving one alone is allowed; two things at once is sometimes simply true. */
    fun keepConflict(entry: BlockWithItem) = dropConflict(entry)

    private fun dropConflict(entry: BlockWithItem) {
        _conflicts.value = _conflicts.value?.let { prompt ->
            val left = prompt.conflicts.filterNot { it.entry.block.id == entry.block.id }
            if (left.isEmpty()) null else prompt.copy(conflicts = left, refusal = null)
        }
    }

    fun dismissConflicts() {
        _conflicts.value = null
    }

    private fun AppointmentAttributes.toDetails() = AppointmentDetails(
        category = category,
        start = start,
        duration = duration,
        travelBefore = travelBefore,
        returnAfter = returnAfter,
        breakAfter = breakAfter,
        endSound = endSound,
        extras = extras,
    )
}

/** "12:00–13:00". An appointment running past midnight is shown as ending at midnight. */
internal fun CalendarEvent.hours(): String {
    val end = (start.minuteOfDay() + duration.inWholeMinutes.toInt()).coerceAtMost(MINUTES_PER_DAY - 1)
    return start.formatClock() + "–" + minuteToLocalTime(end).formatClock()
}

private fun BlockWithItem.describe(): String =
    item.name + " " + block.start.formatClock() + "–" + minuteToLocalTime(block.endMinute()).formatClock()
