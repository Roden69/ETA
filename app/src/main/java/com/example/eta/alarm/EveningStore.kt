package com.example.eta.alarm

import android.content.Context
import androidx.core.content.edit
import com.example.eta.domain.planning.WakeRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/**
 * The evening's choices, kept on the phone rather than in the database: where the
 * wake alarm rings, whether the wind-down hint shows, and the night the clock-app
 * question was last put for. None of it is data the plan depends on, so none of it
 * needs the schema.
 *
 * The clock app itself is [ClockAppAlarms]' choice and is read from there.
 */
class EveningStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("evening", Context.MODE_PRIVATE)

    /** Eta by default: the alarm rings here unless the user hands it to a clock app. */
    fun wakeRoute(): WakeRoute =
        WakeRoute.entries.firstOrNull { it.name == prefs.getString(KEY_ROUTE, null) } ?: WakeRoute.ETA

    fun chooseWakeRoute(route: WakeRoute) {
        prefs.edit { putString(KEY_ROUTE, route.name) }
    }

    /** The clock app the wake alarm would be handed to: the one chosen for the clock-app box. */
    fun clockApp(): ClockApp = ClockAppAlarms.chosen(appContext)

    /** Whether that clock app is installed and takes alarms on this phone. */
    fun clockAppAvailable(): Boolean = ClockAppAlarms.available(appContext).contains(clockApp())

    fun wakeAskedFor(): LocalDate? =
        prefs.getString(KEY_ASKED_FOR, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun markWakeAsked(wakeDate: LocalDate) {
        prefs.edit { putString(KEY_ASKED_FOR, wakeDate.toString()) }
    }

    /** On by default; "Nicht mehr anzeigen" turns it off for good. */
    fun windDownEnabled(): Boolean = prefs.getBoolean(KEY_WIND_DOWN, true)

    private val _windDown = MutableStateFlow(windDownEnabled())

    /** The same flag as a flow, so a settings page already open follows the evening's answer. */
    val windDown: StateFlow<Boolean> = _windDown.asStateFlow()

    fun setWindDownEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WIND_DOWN, enabled) }
        _windDown.value = enabled
    }

    /** Back to a first start: the debug reset wipes the database, and this with it. */
    fun reset() {
        prefs.edit { clear() }
        _windDown.value = true
    }

    private companion object {
        const val KEY_ROUTE = "wakeRoute"
        const val KEY_ASKED_FOR = "wakeAskedFor"
        const val KEY_WIND_DOWN = "windDown"
    }
}
