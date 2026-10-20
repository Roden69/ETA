package com.example.eta.data.calendar

import kotlinx.datetime.LocalDate

/**
 * The reads Eta makes of Google Calendar.
 *
 * [GoogleCalendarApi] is the one implementation the app ships. The seam exists so
 * the sync and the series offers can be tested over a fixed calendar, without a
 * network or a Google account.
 */
interface CalendarApi {
    /** Every calendar the account can see. */
    suspend fun calendars(token: String): List<RemoteCalendar>

    /** Every occurrence between [from] and [to], both days included. */
    suspend fun events(token: String, calendarId: String, from: LocalDate, to: LocalDate): List<RemoteEvent>

    /** The series master of one occurrence: its rule and its first date. */
    suspend fun seriesMaster(token: String, calendarId: String, seriesId: String): RemoteSeries
}
