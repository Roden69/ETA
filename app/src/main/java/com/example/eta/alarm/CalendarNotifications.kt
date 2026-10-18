package com.example.eta.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.example.eta.R
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong

private const val CHANNEL_ID = "calendar_conflicts"
private const val NOTIFICATION_ID = 6000

/**
 * The quiet word that a new appointment has landed on the plan.
 *
 * Its own low-importance channel: nothing about it is urgent, it is information
 * that waits in the shade until the evening or the week asks for it. One
 * notification per appointment, tagged by its id, so a second clash for another
 * one does not replace the first.
 */
object CalendarNotifications {

    fun showConflicts(context: Context, events: List<CalendarEvent>) {
        if (events.isEmpty()) return
        ensureChannel(context)
        events.forEach { event ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Neuer Termin überschneidet sich")
                .setContentText(
                    "»${event.title}« am ${event.date.formatLong()}, ${event.start.formatClock()} " +
                        "liegt auf geplanter Zeit.",
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        NOTIFICATION_ID,
                        launchIntent(context),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build()
            postNotification(context, event.id, NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kalenderkonflikte", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Neue Google-Kalender-Termine, die sich mit geplanter Zeit überschneiden."
                setSound(null, null)
            },
        )
    }
}
