package com.example.eta.data.calendar

import android.content.Context
import androidx.core.content.edit

/**
 * The series the user chose to keep as single appointments.
 *
 * "Einzeln lassen" is remembered here, so the calendar step does not ask about the
 * same series again; a refused series that has been acknowledged is kept here too.
 * In `SharedPreferences`, like the other answers to questions the app asks: a fact
 * about this install, not part of the user's data. Keyed by calendar and series,
 * which is the remote identity of a series.
 */
class DeclinedSeriesStore(context: Context, sandbox: Boolean = false) {

    private val prefs = if (sandbox) null else context.getSharedPreferences("calendar_series", Context.MODE_PRIVATE)
    private val localDeclined = mutableSetOf<String>()

    fun isDeclined(calendarId: String, seriesId: String): Boolean =
        key(calendarId, seriesId) in declined()

    fun decline(calendarId: String, seriesId: String) {
        val value = key(calendarId, seriesId)
        if (prefs == null) localDeclined += value
        else prefs.edit { putStringSet(KEY_DECLINED, declined() + value) }
    }

    /** A copy: the set `SharedPreferences` returns must not be written to. */
    private fun declined(): Set<String> =
        prefs?.getStringSet(KEY_DECLINED, emptySet())?.toSet() ?: localDeclined.toSet()

    /** Back to a first start: no series has been kept single, so each is offered again. */
    fun reset() {
        localDeclined.clear()
        prefs?.edit { clear() }
    }

    private fun key(calendarId: String, seriesId: String) = "$calendarId|$seriesId"

    private companion object {
        const val KEY_DECLINED = "declined"
    }
}
