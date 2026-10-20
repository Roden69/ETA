package com.example.eta.data.repository

import android.app.PendingIntent
import android.content.Intent
import com.example.eta.data.calendar.AuthOutcome
import com.example.eta.data.calendar.CalendarAuth
import com.example.eta.domain.calendar.SeriesPattern
import com.example.eta.domain.calendar.SeriesReading
import com.example.eta.domain.calendar.readSeriesRule
import com.example.eta.domain.model.CalendarEvent
import java.io.IOException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What a sync ended in. */
sealed interface SyncOutcome {
    data class Ok(val summary: CalendarSyncSummary) : SyncOutcome

    /**
     * Google wants to show the user an account picker or a consent screen.
     *
     * Handed up rather than resolved here: only an Activity can launch a
     * `PendingIntent`, and a service that tried would have to hold one.
     */
    data class NeedsConsent(val pendingIntent: PendingIntent) : SyncOutcome

    data class Failed(val reason: String) : SyncOutcome

    /** No account connected. Not a failure — the ordinary state before setup. */
    data object NotConnected : SyncOutcome
}

/** What reading a series' master from Google came to. */
sealed interface SeriesOutcome {
    /** Google answered; [reading] says whether Eta can take the series on. */
    data class Ok(val reading: SeriesReading) : SeriesOutcome

    /** As for [SyncOutcome.NeedsConsent]: only an Activity can show the screen. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : SeriesOutcome

    data class Failed(val reason: String) : SeriesOutcome
}

/**
 * A recurring series among the undecided appointments, and what the calendar step
 * says about it. One per series, never per occurrence.
 *
 * [event] is the series' earliest undecided occurrence: the one the card names.
 */
sealed interface SeriesProposal {
    val event: CalendarEvent

    /** Eta can take the series on as recurring tasks, with [pattern] as its rhythm. */
    data class Offer(override val event: CalendarEvent, val pattern: SeriesPattern) : SeriesProposal

    /** Eta cannot represent the series; [reason] says why, in a sentence a screen can show. */
    data class Refused(override val event: CalendarEvent, val reason: String) : SeriesProposal
}

/**
 * One round of talking to Google: a token, the calendar list, then the events.
 *
 * Keeps the two halves apart on purpose. [CalendarRepository] knows about rows
 * and decisions and nothing about Play Services; [CalendarAuth] knows about tokens
 * and nothing about calendars. This is the only place that needs both, and it is
 * also the only place that has to turn three different kinds of failure — no
 * grant, no network, a refused API — into something a screen can say.
 */
class CalendarSyncService(
    private val auth: CalendarAuth,
    private val repository: CalendarRepository,
) {

    /**
     * Connects for the first time, or re-connects: asks for the grant, then reads
     * the calendar list.
     *
     * The events are not fetched here — which calendars count has not been
     * answered yet, and reading everything only to throw most of it away would be
     * a slow way to start.
     */
    suspend fun connect(): SyncOutcome = withToken { token ->
        val calendars = repository.refreshCalendars(token)
        SyncOutcome.Ok(CalendarSyncSummary(calendars = calendars, events = 0, pending = 0))
    }

    /**
     * The consent screen's answer.
     *
     * Reads the calendar list with the token it came back with, which is what
     * turns "granted" into "connected" — the list is the record of the connection,
     * so a grant that never fetched one would leave the app looking unlinked.
     */
    suspend fun completeConsent(data: Intent?): SyncOutcome =
        when (val outcome = auth.tokenFrom(data)) {
            is AuthOutcome.Ok -> guarded {
                val calendars = repository.refreshCalendars(outcome.accessToken)
                SyncOutcome.Ok(
                    CalendarSyncSummary(calendars = calendars, events = 0, pending = 0),
                )
            }

            is AuthOutcome.NeedsConsent -> SyncOutcome.NeedsConsent(outcome.pendingIntent)
            is AuthOutcome.Failed -> SyncOutcome.Failed(outcome.reason)
        }

    /**
     * Reads how the series of [event] repeats, from its master.
     *
     * Never done by the sync itself: the occurrences a sync brings do not say the
     * rule, and the master is one extra request per series.
     */
    suspend fun readSeries(event: CalendarEvent): SeriesOutcome =
        when (val outcome = auth.token()) {
            is AuthOutcome.Ok -> try {
                SeriesOutcome.Ok(readRule(outcome.accessToken, event))
            } catch (error: IOException) {
                SeriesOutcome.Failed(error.message ?: "Keine Verbindung zu Google.")
            }

            is AuthOutcome.NeedsConsent -> SeriesOutcome.NeedsConsent(outcome.pendingIntent)
            is AuthOutcome.Failed -> SeriesOutcome.Failed(outcome.reason)
        }

    /**
     * The series among [pending] that the calendar step offers, one per series.
     *
     * Read without a tap, but only for series the user has neither adopted nor
     * kept single. A series whose master cannot be read right now is left out
     * silently: the next read of the step asks again, and a flaky network must not
     * turn into a refusal that is then remembered.
     *
     * Silent without a grant too: the step has already reported the missing grant
     * on its own sync, and a second consent screen must not appear from here.
     */
    suspend fun proposeSeries(pending: List<CalendarEvent>): List<SeriesProposal> {
        val candidates = undecidedSeries(pending)
        if (candidates.isEmpty()) return emptyList()
        val token = when (val outcome = auth.token()) {
            is AuthOutcome.Ok -> outcome.accessToken
            is AuthOutcome.NeedsConsent, is AuthOutcome.Failed -> return emptyList()
        }
        return candidates.mapNotNull { event ->
            try {
                when (val reading = readRule(token, event)) {
                    is SeriesReading.Repeats -> SeriesProposal.Offer(event, reading.pattern)
                    is SeriesReading.Unsupported -> SeriesProposal.Refused(event, reading.reason)
                }
            } catch (error: IOException) {
                null
            }
        }
    }

    /**
     * One earliest undecided occurrence per series that is neither adopted nor
     * declined, in the order the days come.
     */
    private suspend fun undecidedSeries(pending: List<CalendarEvent>): List<CalendarEvent> =
        pending
            .filter { it.seriesId != null }
            .sortedWith(compareBy({ it.date }, { it.start }))
            .groupBy { it.calendarId to it.seriesId.orEmpty() }
            .values
            .map { it.first() }
            .filterNot { event ->
                val seriesId = event.seriesId.orEmpty()
                repository.isSeriesAdopted(event.calendarId, seriesId) ||
                    repository.isSeriesDeclined(event.calendarId, seriesId)
            }

    /**
     * The series master's rule, read as the series of [event] is repeated.
     *
     * An all-day series has no hour of its own, so an `UNTIL` instant is read on
     * the day only; a timed one is read against the hour its occurrences start.
     */
    private suspend fun readRule(token: String, event: CalendarEvent): SeriesReading {
        val master = repository.readSeries(token, event.calendarId, requireNotNull(event.seriesId))
        val start = master.start
            ?: return SeriesReading.Unsupported("Der Anfang der Serie ist nicht bekannt.")
        return readSeriesRule(
            recurrence = master.recurrence,
            start = start,
            timeZone = TimeZone.currentSystemDefault(),
            occurrenceTime = if (event.allDay) null else event.start,
        )
    }

    /** Forgets the account. Nothing is asked of Google; see the repository. */
    suspend fun disconnect() = repository.disconnect()

    /**
     * Reads the switched-on calendars over [from]..[to].
     *
     * Silent when nothing is connected: the planning steps call this on the way
     * in, and a user who has never linked an account should not meet an error for
     * a feature they are not using.
     */
    suspend fun refresh(from: LocalDate, to: LocalDate): SyncOutcome {
        if (!repository.isConnected()) return SyncOutcome.NotConnected
        return withToken { token ->
            val calendars = repository.refreshCalendars(token)
            val events = repository.syncEvents(token, from, to)
            SyncOutcome.Ok(
                CalendarSyncSummary(
                    calendars = calendars,
                    events = events,
                    pending = repository.pendingFor(from, to).size,
                ),
            )
        }
    }

    private suspend fun withToken(block: suspend (String) -> SyncOutcome): SyncOutcome =
        when (val outcome = auth.token()) {
            is AuthOutcome.Ok -> guarded { block(outcome.accessToken) }
            is AuthOutcome.NeedsConsent -> SyncOutcome.NeedsConsent(outcome.pendingIntent)
            is AuthOutcome.Failed -> SyncOutcome.Failed(outcome.reason)
        }

    /**
     * Network failures become sentences rather than crashes.
     *
     * A sync runs on the way into a planning phase, and a phase that cannot be
     * entered because the flat's wifi is down would be the feature holding the
     * whole app hostage.
     */
    private suspend fun guarded(block: suspend () -> SyncOutcome): SyncOutcome =
        try {
            block()
        } catch (error: IOException) {
            SyncOutcome.Failed(error.message ?: "Keine Verbindung zu Google.")
        } catch (error: Exception) {
            SyncOutcome.Failed(error.message ?: "Der Kalender konnte nicht gelesen werden.")
        }
}
