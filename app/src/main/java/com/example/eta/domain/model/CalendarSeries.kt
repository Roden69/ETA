package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant

/**
 * A Google series the user adopted as recurring tasks.
 *
 * One row per recurring card made from it: a series with several weekdays becomes
 * one card per weekday, and each of them is a row here. What the rows say is the
 * link that keeps the series' occurrences out of the undecided list — an
 * occurrence of an adopted series is decided by the series, not offered again.
 *
 * The rule itself is not stored. Google owns it; a later change there is not
 * followed, and the card keeps the rhythm it was made with.
 */
@Entity(
    tableName = "calendar_series",
    indices = [Index("calendarId", "seriesId")],
)
data class CalendarSeries(
    /** The recurring card this row belongs to. */
    @PrimaryKey val itemId: String,
    val calendarId: String,
    val seriesId: String,
    val createdAt: Instant,
)
