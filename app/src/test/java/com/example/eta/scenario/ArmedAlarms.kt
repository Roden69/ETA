package com.example.eta.scenario

import android.app.AlarmManager
import kotlin.time.Instant
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** One alarm the system alarm manager holds: when it is due, and which intent it fires. */
data class ArmedAlarm(val due: Instant, val requestCode: Int, val action: String?)

/** Every alarm armed right now, read from Robolectric's alarm manager. */
fun armedAlarms(): List<ArmedAlarm> {
    val manager = RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java)
    return shadowOf(manager).scheduledAlarms.map { scheduled: ShadowAlarmManager.ScheduledAlarm ->
        val shadow = shadowOf(scheduled.operation)
        ArmedAlarm(
            due = Instant.fromEpochMilliseconds(scheduled.triggerAtTime),
            requestCode = shadow.requestCode,
            action = shadow.savedIntent?.action,
        )
    }
}
