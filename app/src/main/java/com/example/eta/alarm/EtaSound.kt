package com.example.eta.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.RawRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.example.eta.domain.audio.AudioMode
import com.example.eta.domain.audio.RingerMode
import com.example.eta.domain.audio.effectiveAudioMode

/**
 * Every sound Eta makes, played by Eta rather than by a notification channel.
 *
 * Silent and vibrate ringer modes replace sounds with vibration. Do-Not-Disturb
 * suppresses both unless the user enables [ignoresDoNotDisturb]; that override
 * never overrides a muted ringer. The notification volume does not govern Eta.
 *
 * So the channels are silent and this plays the file itself:
 *
 * - **On the alarm stream** (`USAGE_ALARM`). Its volume is separate from the
 *   notification and ring volumes, which is the whole point — the app sounds the
 *   same however the notification slider was left. The price is that it follows
 *   the *alarm* slider instead, and that is said in the settings.
 * - **Checked against Do-Not-Disturb first.** The alarm stream deliberately cuts
 *   through most DND configurations, so Eta checks the interruption filter itself.
 *   [ignoresDoNotDisturb] bypasses only that check, not the ringer mode.
 *
 * A useful side effect: the sound no longer depends on the notification
 * permission. A refused permission costs the banner, and no longer the sound.
 *
 * The wake alarm is the one exception and keeps its own [WakeAlarmRinger]: an
 * alarm clock that stayed quiet under DND would not be an alarm clock.
 *
 * The mode itself is decided by [effectiveAudioMode], a pure function in
 * `domain/audio`; this object only reads the phone's state and plays.
 */
object EtaSound {

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Players still sounding. Held so the garbage collector cannot take one
     * mid-sound, and released when it completes.
     */
    private val playing = mutableSetOf<MediaPlayer>()

    /** Whether the user has asked the phone for quiet, in any of its forms. */
    fun isDoNotDisturbOn(context: Context): Boolean {
        val manager: NotificationManager = context.getSystemService() ?: return false
        return when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            -> false

            else -> true
        }
    }

    /**
     * Whether the user chose to be told through Do-Not-Disturb as well.
     *
     * Off unless switched on in the Alarme card. In `SharedPreferences` rather
     * than `user_setup`, because this object is called from receivers with a
     * context and nothing else, and the answer has to be there without a
     * database read between the alarm and its sound.
     */
    fun ignoresDoNotDisturb(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IGNORE_DND, false)

    fun setIgnoresDoNotDisturb(context: Context, ignore: Boolean) {
        prefs(context).edit { putBoolean(KEY_IGNORE_DND, ignore) }
    }

    /**
     * Whether the phone's ringer is switched to silent or to vibrate.
     *
     * The alarm stream these sounds play on has a slider of its own and ignores
     * that switch — which is why an alarm clock rings on a muted phone, and why
     * Eta's sounds did too. The user called that a bug, and for a task
     * announcement it is one: muting the phone is asking it to be quiet.
     */
    fun isRingerMuted(context: Context): Boolean = ringerMode(context) != RingerMode.NORMAL

    /** The phone's ringer switch, as the domain rule reads it. */
    internal fun ringerMode(context: Context): RingerMode {
        val audio: AudioManager = context.getSystemService() ?: return RingerMode.NORMAL
        return when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("sound", Context.MODE_PRIVATE)

    private const val KEY_IGNORE_DND = "ignoreDoNotDisturb"
    private const val KEY_MODE = "audioMode"

    fun mode(context: Context): AudioMode {
        val stored = prefs(context).getString(KEY_MODE, null)
        return AudioMode.entries.firstOrNull { it.name == stored } ?: AudioMode.SOUND
    }

    fun setMode(context: Context, mode: AudioMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /** One policy for sounds and speech; see [effectiveAudioMode]. */
    internal fun effectiveMode(context: Context): AudioMode = effectiveAudioMode(
        selected = mode(context),
        ringer = ringerMode(context),
        doNotDisturb = isDoNotDisturbOn(context),
        ignoresDoNotDisturb = ignoresDoNotDisturb(context),
    )

    /** One short buzz, after [effectiveMode] has allowed it. */
    private fun vibrate(context: Context) {
        alarmVibrator(context)?.vibrateAsAlarm(longArrayOf(0, 250, 150, 250), repeat = -1)
    }

    /** Announces [sound] once using the effective sound, vibration or silent mode. Never throws. */
    fun play(context: Context, @RawRes sound: Int) {
        val app = context.applicationContext
        when (effectiveMode(app)) {
            AudioMode.SILENT -> return
            AudioMode.VIBRATE -> return vibrate(app)
            AudioMode.SOUND -> Unit
        }
        val audio: AudioManager? = app.getSystemService()

        runCatching {
            val player = MediaPlayer.create(
                app,
                sound,
                attributes,
                audio?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE,
            ) ?: return
            val focus = audio?.let { requestFocus(it) }

            player.setOnCompletionListener { done ->
                synchronized(playing) { playing.remove(done) }
                runCatching { done.release() }
                if (audio != null && focus != null) abandonFocus(audio, focus)
            }
            synchronized(playing) { playing += player }
            player.start()
        }
    }

    /**
     * Asks music to duck for the length of the sound rather than talking over it.
     * Declined focus changes nothing: the sound plays either way. Shared with
     * [EtaSpeech], which stands in for a sound and should be made room for
     * the same way.
     */
    internal fun requestFocus(audio: AudioManager): Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
                .also { audio.requestAudioFocus(it) }
        } else {
            null
        }

    internal fun abandonFocus(audio: AudioManager, focus: Any) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focus is AudioFocusRequest) {
            runCatching { audio.abandonAudioFocusRequest(focus) }
        }
    }
}

/**
 * Posts a notification if the user allows it, and does nothing if not.
 *
 * Every alarm here posts from a receiver and has already done the part that
 * matters by then — rescheduled, played its sound — so a refused permission may
 * cost the banner and nothing else. The explicit check is also what the
 * `MissingPermission` lint asks for; a `runCatching` around `notify` swallowed
 * the same failure without saying so.
 */
internal fun postNotification(context: Context, tag: String?, id: Int, notification: Notification) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    try {
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    } catch (revoked: SecurityException) {
        // Revoked between the check and the call; the same outcome as refused.
    }
}

/**
 * A notification channel that makes no sound of its own — [EtaSound] plays it.
 *
 * A channel's sound is fixed when the channel is created, so every channel that
 * used to carry one needs a **new id** to become silent, and the old id is
 * deleted with it so no dead entry lingers in the system settings. That is what
 * [legacyIds] is for.
 */
internal fun ensureSilentChannel(
    context: Context,
    id: String,
    name: String,
    description: String,
    legacyIds: List<String> = emptyList(),
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager: NotificationManager = context.getSystemService() ?: return
    legacyIds.forEach { manager.deleteNotificationChannel(it) }
    manager.createNotificationChannel(
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            setSound(null, null)
        },
    )
}
