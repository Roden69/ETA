package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import androidx.room3.Transaction
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.CalendarSeries
import com.example.eta.domain.model.CalendarSource
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class LiveCalendarSeries(
    val itemId: String,
    val calendarId: String,
    val seriesId: String,
    val recurrenceRule: String,
    val repeatUntil: LocalDate?,
)
data class CalendarSeriesImportDate(
    val itemId: String,
    val date: LocalDate,
)

/** One day Google shows an occurrence of a series on, in a calendar that is switched on. */
data class CalendarSeriesDate(
    val calendarId: String,
    val seriesId: String,
    val date: LocalDate,
)


@Dao
interface CalendarDao {

    // --- the calendars themselves -------------------------------------------

    @Upsert
    suspend fun upsertSources(sources: List<CalendarSource>)

    @Upsert
    suspend fun upsertSource(source: CalendarSource)

    @Query("SELECT * FROM calendar_sources ORDER BY isPrimary DESC, displayName")
    fun observeSources(): Flow<List<CalendarSource>>

    @Query("SELECT * FROM calendar_sources")
    suspend fun findSources(): List<CalendarSource>

    @Query("SELECT * FROM calendar_sources WHERE enabled = 1")
    suspend fun findEnabledSources(): List<CalendarSource>

    @Query("SELECT COUNT(*) FROM calendar_sources")
    suspend fun countSources(): Int

    /** Calendars Google no longer offers. Their events go with them, in the repository. */
    @Query("DELETE FROM calendar_sources WHERE id NOT IN (:keep)")
    suspend fun deleteSourcesNotIn(keep: List<String>): Int

    @Query("DELETE FROM calendar_sources")
    suspend fun clearSources()

    // --- the events ----------------------------------------------------------

    @Upsert
    suspend fun upsertEvents(events: List<CalendarEvent>)

    @Upsert
    suspend fun upsertEvent(event: CalendarEvent)

    @Query("SELECT * FROM calendar_events WHERE date BETWEEN :from AND :to")
    suspend fun findForRange(from: LocalDate, to: LocalDate): List<CalendarEvent>

    /**
     * What a planning step asks about: everything still undecided in its stretch.
     *
     * Ordered by day and hour rather than by when it arrived — the screen reads
     * as a run of days, and an appointment's place in one is its time.
     */
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE decision = 'PENDING' AND date BETWEEN :from AND :to
        ORDER BY date, start
        """,
    )
    fun observePending(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE decision = 'PENDING' AND date BETWEEN :from AND :to
        """,
    )
    suspend fun findPending(from: LocalDate, to: LocalDate): List<CalendarEvent>

    /** The "Ignorierte Events" list, newest day first. */
    @Query("SELECT * FROM calendar_events WHERE decision = 'IGNORED' ORDER BY date DESC, start")
    fun observeIgnored(): Flow<List<CalendarEvent>>

    /**
     * Clears out events of a stretch that the calendar no longer has.
     *
     * Only the undecided ones. An ignored event has to stay ignored — otherwise a
     * hiccup in a sync would offer it again — and an imported one has become a
     * card the user may already have worked from.
     */
    @Query(
        """
        DELETE FROM calendar_events
        WHERE date BETWEEN :from AND :to
          AND decision = 'PENDING'
          AND calendarId IN (:calendarIds)
          AND id NOT IN (:keep)
        """,
    )
    suspend fun deletePendingNotIn(from: LocalDate, to: LocalDate, keep: List<String>, calendarIds: List<String>): Int

    @Query("DELETE FROM calendar_events WHERE calendarId NOT IN (:keep)")
    suspend fun deleteEventsOfCalendarsNotIn(keep: List<String>): Int

    /**
     * The occurrences of adopted series inside a stretch.
     *
     * Their card stands for them, so these rows are only Google's facts about the
     * series. One Google no longer shows (an `EXDATE` added later, a cancellation)
     * has no business staying behind as if it were still there.
     */
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE date BETWEEN :from AND :to
          AND seriesId IS NOT NULL
          AND decision = 'IMPORTED'
          AND itemId IN (SELECT itemId FROM calendar_series)
        """,
    )
    suspend fun findAdoptedSeriesOccurrences(from: LocalDate, to: LocalDate): List<CalendarEvent>

    @Delete
    suspend fun deleteEvents(events: List<CalendarEvent>)

    /** Rows and their authoritative coverage become visible together, never half a sync. */
    @Transaction
    suspend fun commitSnapshot(
        sources: List<CalendarSource>,
        events: List<CalendarEvent>,
        from: LocalDate,
        through: LocalDate,
        keep: List<String>,
        removedAdoptions: List<CalendarEvent>,
    ) {
        upsertEvents(events)
        deletePendingNotIn(from, through, keep, sources.map { it.id })
        deleteEvents(removedAdoptions)
        upsertSources(sources)
    }

    /**
     * Google's occurrence days of every series, in the calendars that are switched on.
     *
     * Whatever was decided about an occurrence, it is still a day Google showed the
     * series on — which is what tells a holiday apart from a day the card should run.
     */
    @Query(
        """
        SELECT calendar_events.calendarId AS calendarId,
               calendar_events.seriesId AS seriesId,
               calendar_events.date AS date
        FROM calendar_events
        INNER JOIN calendar_sources
          ON calendar_sources.id = calendar_events.calendarId
         AND calendar_sources.enabled = 1
         AND calendar_sources.syncToken = calendar_events.seenInSync
        WHERE calendar_events.seriesId IS NOT NULL
          AND calendar_events.date BETWEEN :from AND :to
        """,
    )
    suspend fun findSeriesDatesBetween(from: LocalDate, to: LocalDate): List<CalendarSeriesDate>

    /** Marks the undecided occurrences of an adopted series as taken by its card. */
    @Query(
        """
        UPDATE calendar_events
        SET decision = 'IMPORTED', itemId = :itemId, updatedAt = :now
        WHERE calendarId = :calendarId AND seriesId = :seriesId AND decision = 'PENDING'
        """,
    )
    suspend fun decideSeriesOccurrences(calendarId: String, seriesId: String, itemId: String, now: Instant): Int

    @Query("SELECT * FROM calendar_events WHERE calendarId = :calendarId AND seriesId = :seriesId")
    suspend fun findSeriesOccurrences(calendarId: String, seriesId: String): List<CalendarEvent>

    @Query("UPDATE calendar_events SET itemId = :itemId, updatedAt = :now WHERE id = :id")
    suspend fun relink(id: String, itemId: String, now: Instant)

    @Query("UPDATE calendar_events SET conflictNotified = 1 WHERE id = :id")
    suspend fun markConflictNotified(id: String)

    @Query("DELETE FROM calendar_events")
    suspend fun clearEvents()

    // --- the adopted series --------------------------------------------------

    @Upsert
    suspend fun upsertSeries(series: List<CalendarSeries>)

    @Query("SELECT * FROM calendar_series")
    suspend fun findSeries(): List<CalendarSeries>

    /**
     * The links whose card is still a standing task: not deleted, not ended. A
     * series whose card is gone is no longer adopted, and its occurrences are
     * offered again.
     */
    @Query(
        """
        SELECT calendar_series.itemId, calendar_series.calendarId, calendar_series.seriesId,
               items.recurrenceRule, items.repeatUntil
        FROM calendar_series
        INNER JOIN items ON items.id = calendar_series.itemId
        WHERE items.completedAt IS NULL
          AND items.recurrenceRule IS NOT NULL
        """,
    )
    suspend fun findLiveSeries(): List<LiveCalendarSeries>

    /** Individually imported events whose history must remain instead of being replaced. */
    @Query(
        """
        SELECT calendar_series.itemId AS itemId, calendar_events.date AS date
        FROM calendar_events
        INNER JOIN calendar_series
          ON calendar_series.calendarId = calendar_events.calendarId
         AND calendar_series.seriesId = calendar_events.seriesId
        WHERE calendar_events.decision = 'IMPORTED'
          AND calendar_events.itemId IS NOT NULL
          AND calendar_events.itemId NOT IN (SELECT itemId FROM calendar_series)
        """,
    )
    suspend fun findRetainedIndividualSeriesImports(): List<CalendarSeriesImportDate>
}
