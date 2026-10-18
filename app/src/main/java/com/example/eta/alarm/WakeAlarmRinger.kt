package com.example.eta.alarm

import android.content.Context
import android.media.MediaPlayer

/**
 * The sound and the buzz, held outside any screen.
 *
 * Process-wide rather than owned by [WakeAlarmActivity], because the two can come
 * apart: the full-screen intent may be refused, the activity may be rotated away
 * and rebuilt, and the "Aus" button lives on the notification, which is a
 * receiver. A ringer tied to a screen would keep playing after the screen was
 * gone, which is the one failure a wake alarm must not have.
 *
 * [start] is idempotent — a second call while it is already ringing does nothing
 * rather than layering a second player on top.
 */
object WakeAlarmRinger {

    private var player: MediaPlayer? = null

    val isRinging: Boolean get() = player != null

    fun start(context: Context) {
        if (player != null) return
        val uri = WakeAlarmNotifications.soundUri(context) ?: return

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(WakeAlarmNotifications.audioAttributes)
                setDataSource(context.applicationContext, uri)
                // Looping is what separates an alarm from a notification: a tone
                // that plays once and stops cannot wake anyone who slept through
                // its twenty seconds.
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrate(context)
    }

    fun stop(context: Context) {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        runCatching { alarmVibrator(context)?.cancel() }
    }

    /** Alarm usage, so the buzz is felt even when the phone is on silent. */
    private fun vibrate(context: Context) {
        alarmVibrator(context)?.vibrateAsAlarm(longArrayOf(0, 700, 900), repeat = 0)
    }
}
