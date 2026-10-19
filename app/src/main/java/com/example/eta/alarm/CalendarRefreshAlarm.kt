package com.example.eta.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.example.eta.EtaApplication
import com.example.eta.data.repository.CalendarRefreshService
import com.example.eta.data.repository.CalendarRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CalendarRefreshContract {
    const val ACTION_RING = "com.example.eta.CALENDAR_REFRESH_RING"
    const val REQUEST_CODE = 1300

    /** About an hour between two reads of the calendar, while an account is connected. */
    val INTERVAL = 60.minutes

    /** Inexact on purpose: the read may come a few minutes late, and needs no exact-alarm permission. */
    val WINDOW = 10.minutes
}

/**
 * Reads the calendar in the background, while an account is connected.
 *
 * One alarm, chained: each run arms the next one, so the rhythm survives a run
 * that found nothing to do. `setWindow` only — the calendar is not something to
 * wake the phone for at an exact minute, and the exact-alarm permission is not
 * worth asking for it.
 *
 * Re-armed on app start and after boot without moving an alarm that is already
 * standing: an app opened every half hour must not push the next read back each
 * time. Disconnecting cancels it. The sandbox never arms one.
 */
class CalendarRefreshCoordinator(
    private val context: Context,
    private val calendarRepository: CalendarRepository,
    private val refresh: CalendarRefreshService,
    private val clock: Clock = Clock.System,
    private val enabled: Boolean = true,
) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    /** When the standing alarm is due; a time in the past means the chain broke. */
    private val prefs = context.applicationContext.getSharedPreferences("calendar_refresh", Context.MODE_PRIVATE)

    /**
     * Keeps a standing alarm, arms one if none is standing or the last one was
     * lost, cancels when not connected.
     *
     * A delivered alarm leaves its PendingIntent behind, so the intent alone cannot
     * tell a standing alarm from a run that was killed before it armed the next
     * one. The due time kept with each arming can: if it has passed, nothing is
     * standing any more.
     */
    suspend fun reschedule() {
        if (!enabled) return
        val manager = alarmManager ?: return
        if (!calendarRepository.isConnected()) {
            cancel(manager)
            return
        }
        val dueAt = prefs.getLong(KEY_DUE_AT, 0L)
        if (dueAt > clock.now().toEpochMilliseconds() && refreshIntent(create = false) != null) return
        arm(manager)
    }

    /**
     * Arms the next run first, then reads: the read makes several requests and may
     * outlast the receiver's time, and a run cut short must not end the chain.
     */
    suspend fun onRing() {
        if (!enabled) return
        val manager = alarmManager
        if (enabled && manager != null) {
            if (calendarRepository.isConnected()) arm(manager) else cancel(manager)
        }
        val fresh = refresh.run()
        CalendarNotifications.showConflicts(context, fresh)
    }

    private fun arm(manager: AlarmManager) {
        val at = clock.now().plus(CalendarRefreshContract.INTERVAL).toEpochMilliseconds()
        manager.setWindow(
            AlarmManager.RTC_WAKEUP,
            at,
            CalendarRefreshContract.WINDOW.inWholeMilliseconds,
            refreshIntent(create = true)!!,
        )
        prefs.edit { putLong(KEY_DUE_AT, at) }
    }

    private fun cancel(manager: AlarmManager) {
        refreshIntent(create = false)?.let { intent ->
            manager.cancel(intent)
            intent.cancel()
        }
        prefs.edit { remove(KEY_DUE_AT) }
    }

    /** The same intent every time, so the alarm is found, replaced or cancelled by it. */
    private fun refreshIntent(create: Boolean): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            CalendarRefreshContract.REQUEST_CODE,
            Intent(context, CalendarRefreshReceiver::class.java)
                .setAction(CalendarRefreshContract.ACTION_RING),
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
                PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val KEY_DUE_AT = "dueAt"
    }
}

class CalendarRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarRefreshContract.ACTION_RING) return
        val app = context.applicationContext as? EtaApplication ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.container.calendarRefreshCoordinator.onRing()
            } finally {
                pending.finish()
            }
        }
    }
}
