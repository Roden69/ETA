package com.example.eta.data.repository

import com.example.eta.data.calendar.CalendarApi
import com.example.eta.data.calendar.DeclinedSeriesStore
import com.example.eta.data.calendar.RemoteEvent
import com.example.eta.data.calendar.RemoteSeries
import com.example.eta.data.local.CalendarDao
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.CalendarSeries
import com.example.eta.domain.model.CalendarSource
import com.example.eta.domain.model.EventDecision
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.recurrence.matches
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** What one round of syncing produced. */
data class CalendarSyncSummary(
    val calendars: Int,
    val events: Int,
    val pending: Int,
)

/**
 * The calendars, their events, and what has been decided about each.
 *
 * Read-only about Google: nothing here ever writes to a calendar. What it does
 * own is the *decision* — every event is either a card in the app or explicitly
 * ignored, and that is the invariant the two planning steps lean on. An event in
 * neither state is by definition new, which is how "has something been added for
 * tomorrow" is answerable without asking Google what the app knew yesterday.
 */
class CalendarRepository(
    private val dao: CalendarDao,
    private val api: CalendarApi,
    private val declinedSeries: DeclinedSeriesStore,
    private val clock: Clock = Clock.System,
) {

    fun observeSources(): Flow<List<CalendarSource>> = dao.observeSources()

    fun observePending(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> =
        dao.observePending(from, to)

    fun observeIgnored(): Flow<List<CalendarEvent>> = dao.observeIgnored()

    /**
     * Whether a Google account has been connected.
     *
     * Read off the calendar list rather than a flag of its own: connecting always
     * fetches it, and disconnecting clears it, so the list *is* the record.
     * A second boolean could only disagree with it — the same reasoning that
     * keeps the weekly devaluation reading the ledger instead of a stored mark.
     */
    suspend fun isConnected(): Boolean = dao.countSources() > 0

    suspend fun setEnabled(source: CalendarSource, enabled: Boolean) {
        dao.upsertSource(source.copy(enabled = enabled, updatedAt = clock.now()))
    }

    /**
     * Refreshes the list of calendars.
     *
     * [CalendarSource.enabled] is the one field carried over from what is already
     * stored: it is the only one the user wrote, and a refresh must not quietly
     * switch a calendar back on. A calendar seen for the first time is on only if
     * it is the account's own primary one — a foreign or subscribed calendar
     * counting towards the day by default is exactly what the user asked to be
     * able to avoid.
     */
    suspend fun refreshCalendars(token: String): Int {
        val remote = api.calendars(token)
        if (remote.isEmpty()) return 0

        val known = dao.findSources().associateBy { it.id }
        val now = clock.now()
        dao.upsertSources(
            remote.map { calendar ->
                CalendarSource(
                    id = calendar.id,
                    displayName = calendar.displayName,
                    accountName = calendar.accountName ?: known[calendar.id]?.accountName,
                    isPrimary = calendar.isPrimary,
                    isForeign = calendar.isForeign,
                    enabled = known[calendar.id]?.enabled ?: calendar.isPrimary,
                    updatedAt = now,
                )
            },
        )
        val keep = remote.map { it.id }
        dao.deleteSourcesNotIn(keep)
        // A calendar that is gone takes its undecided events with it; anything
        // already imported has become a card and stands on its own.
        dao.deleteEventsOfCalendarsNotIn(keep)
        return remote.size
    }

    /**
     * Reads the switched-on calendars over a stretch of days and files what it
     * finds.
     *
     * Idempotent, and that is the whole design: the row id is the remote identity,
     * so the same appointment fetched twice lands on the same row. A row that
     * already carries a decision keeps it — only the *facts* are refreshed, so an
     * appointment moved in Google shows its new time without being offered for
     * import a second time.
     *
     * The window read is never shorter than the schedule's horizon from today. An
     * adopted series' holidays are the days inside the window where Google shows
     * no occurrence, so the schedule can only tell them apart where a sync has read
     * the calendar — and the planning steps alone read only about a week ahead.
     */
    suspend fun syncEvents(token: String, from: LocalDate, to: LocalDate): Int {
        val calendars = dao.findEnabledSources()
        if (calendars.isEmpty()) return 0

        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val windowFrom = minOf(from, today)
        val windowTo = maxOf(to, today.plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)))

        val remote = calendars.flatMap { api.events(token, it.id, windowFrom, windowTo) }
        val remoteIds = remote.map { idOf(it) }.toSet()
        val known = dao.findForRange(windowFrom, windowTo).associateBy { it.id }
        val seriesItems = dao.findSeries().map { it.itemId }.toSet()
        val liveDefinitions = dao.findLiveSeries()
        val now = clock.now()

        dao.upsertEvents(
            remote.map { event ->
                val activeDefinitions = liveDefinitions
                    .filter { it.calendarId == event.calendarId && it.seriesId == event.seriesId }
                    .filter { it.repeatUntil == null || event.date <= it.repeatUntil }
                    .filter { RecurrenceRule.decode(it.recurrenceRule).matches(event.date) }
                val adoptedItemId = activeDefinitions.firstOrNull()?.itemId
                val row = known[idOf(event)]
                val staleAdoption = row?.decision == EventDecision.IMPORTED &&
                    row.itemId in seriesItems &&
                    (adoptedItemId == null || activeDefinitions.none { it.itemId == row.itemId })
                val existing = if (staleAdoption) {
                    row?.copy(decision = EventDecision.PENDING, itemId = null)
                } else {
                    row
                }
                merge(event, existing, adoptedItemId, now)
            },
        )
        // What the calendar no longer offers and nobody has decided about is gone.
        // Room binds an empty list as NULL, which would match nothing; no event id is
        // blank, so a blank stand-in keeps "nothing offered" meaning "delete all".
        dao.deletePendingNotIn(windowFrom, windowTo, remoteIds.ifEmpty { setOf("") }.toList())
        // An adopted series' occurrence Google no longer shows is gone too: its card
        // was the decision, and a holiday must not keep counting as an occurrence.
        dao.deleteEvents(
            dao.findAdoptedSeriesOccurrences(windowFrom, windowTo).filter { it.id !in remoteIds },
        )
        return remote.size
    }

    private fun idOf(event: RemoteEvent) = CalendarEvent.idOf(event.calendarId, event.eventId)

    /**
     * One occurrence, refreshed from Google and joined with what the user decided.
     *
     * A new occurrence of an adopted series is taken by its card at once
     * ([adoptedItemId]), so it is never offered as a pending appointment.
     */
    private fun merge(
        event: RemoteEvent,
        existing: CalendarEvent?,
        adoptedItemId: String?,
        now: Instant,
    ): CalendarEvent = CalendarEvent(
        id = idOf(event),
        calendarId = event.calendarId,
        eventId = event.eventId,
        seriesId = event.seriesId,
        title = event.title,
        date = event.date,
        start = event.start,
        duration = event.duration,
        allDay = event.allDay,
        remoteUpdatedAt = event.updatedAt,
        // The fields the user owns. Google has no opinion about any of them.
        decision = existing?.decision
            ?: if (adoptedItemId != null) EventDecision.IMPORTED else EventDecision.PENDING,
        itemId = existing?.itemId ?: adoptedItemId,
        conflictNotified = existing?.conflictNotified ?: false,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )

    private suspend fun adoptedSeries(): Map<String, String> =
        dao.findLiveSeries().associate { seriesKey(it.calendarId, it.seriesId) to it.itemId }

    /** Whether a series has been made into recurring cards. */
    suspend fun isSeriesAdopted(calendarId: String, seriesId: String): Boolean =
        seriesKey(calendarId, seriesId) in adoptedSeries()

    /**
     * Links the cards made from a series to it, and takes every undecided
     * occurrence of that series off the list.
     *
     * [itemIds] are the recurring cards, one per weekday. The occurrences point at
     * the first of them: the link only has to say "decided", not which weekday.
     */
    suspend fun adoptSeries(
        calendarId: String,
        seriesId: String,
        itemIds: List<String>,
    ) {
        require(itemIds.isNotEmpty()) { "A series is adopted into at least one card" }
        val now = clock.now()
        dao.upsertSeries(
            itemIds.map { itemId ->
                CalendarSeries(itemId = itemId, calendarId = calendarId, seriesId = seriesId, createdAt = now)
            },
        )
        dao.decideSeriesOccurrences(calendarId, seriesId, itemIds.first(), now)
    }

    /** The series master as Google keeps it: its rule and first date. */
    suspend fun readSeries(token: String, calendarId: String, seriesId: String): RemoteSeries =
        api.seriesMaster(token, calendarId, seriesId)

    /** Keeps a series single: its occurrences stay appointments, and it is not offered again. */
    fun declineSeries(calendarId: String, seriesId: String) =
        declinedSeries.decline(calendarId, seriesId)

    /** Whether the user has chosen to keep this series single. */
    fun isSeriesDeclined(calendarId: String, seriesId: String): Boolean =
        declinedSeries.isDeclined(calendarId, seriesId)

    /** Remembers that the user was told about this clash, so it is announced once. */
    suspend fun markConflictNotified(event: CalendarEvent) {
        dao.markConflictNotified(event.id)
    }

    /** Every stored occurrence of a series, whatever was decided about it. */
    suspend fun occurrencesOf(calendarId: String, seriesId: String): List<CalendarEvent> =
        dao.findSeriesOccurrences(calendarId, seriesId)

    /** Points an occurrence at another card: the series' own, once the series is adopted. */
    suspend fun relink(event: CalendarEvent, itemId: String) {
        dao.relink(event.id, itemId, clock.now())
    }

    private fun seriesKey(calendarId: String, seriesId: String?): String =
        if (seriesId == null) "" else "$calendarId|$seriesId"

    suspend fun pendingFor(from: LocalDate, to: LocalDate): List<CalendarEvent> =
        dao.findPending(from, to)

    /** Filed under "geht mich nichts an" — and so no longer new. */
    suspend fun ignore(event: CalendarEvent) {
        dao.upsertEvent(
            event.copy(decision = EventDecision.IGNORED, updatedAt = clock.now()),
        )
    }

    /** Taking that back: the event is undecided again and will be offered afresh. */
    suspend fun undecide(event: CalendarEvent) {
        dao.upsertEvent(
            event.copy(decision = EventDecision.PENDING, itemId = null, updatedAt = clock.now()),
        )
    }

    /** The link that keeps a second sync from planning the same appointment twice. */
    suspend fun markImported(event: CalendarEvent, itemId: String) {
        dao.upsertEvent(
            event.copy(
                decision = EventDecision.IMPORTED,
                itemId = itemId,
                updatedAt = clock.now(),
            ),
        )
    }

    /**
     * Forgets the account.
     *
     * Only what was read from Google goes; the cards already made from it stay,
     * because they are the user's plan rather than Google's data. Revoking the
     * grant itself happens in the Google account settings, and the screen says so
     * rather than pretending a local delete reaches that far.
     */
    suspend fun disconnect() {
        dao.clearEvents()
        dao.clearSources()
    }
}
