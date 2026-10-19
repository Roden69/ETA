package com.example.eta.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/**
 * The phone's vibrator, for buzzes that must still be felt on a muted phone.
 *
 * Android treats a vibration like a sound: one without an alarm usage is a
 * notification or touch feedback, and those are suppressed when the ringer is
 * silent or on vibrate. Marking the buzz as an alarm keeps it, the same way the
 * sounds ride the alarm stream.
 */
internal fun alarmVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService<Vibrator>()
    }

/**
 * Vibrates [pattern] with alarm usage, repeating from index [repeat] (`-1` for
 * once). Never throws: a device without a vibrator simply stays quiet.
 */
@Suppress("DEPRECATION")
internal fun Vibrator.vibrateAsAlarm(pattern: LongArray, repeat: Int) {
    runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrate(
                VibrationEffect.createWaveform(pattern, repeat),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )

            // API 26–32: the usage travels as AudioAttributes, which vibrate accepts there.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> vibrate(
                VibrationEffect.createWaveform(pattern, repeat),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )

            else -> vibrate(pattern, repeat)
        }
    }
}
