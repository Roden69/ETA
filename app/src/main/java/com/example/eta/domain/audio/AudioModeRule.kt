package com.example.eta.domain.audio

/**
 * How Eta makes itself heard — every sound it has except the wake alarm.
 *
 * [SOUND] is what there was. [VIBRATE] buzzes where a sound would have played;
 * [SILENT] does neither and leaves the banner to say it. Chosen on the "Audio"
 * page of the settings and kept in `SharedPreferences`, for the reason the
 * Do-Not-Disturb switch is: the sounds are played from receivers that have a
 * context and nothing else.
 */
enum class AudioMode(val label: String) {
    SOUND("Ton"),
    VIBRATE("Vibration"),
    SILENT("Still"),
}

/** The phone's ringer switch, as far as Eta's sounds are concerned. */
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/**
 * What Eta does for one announcement, given the mode the user chose and the
 * phone's state. Sounds and speech both follow this.
 *
 * - [AudioMode.SILENT] wins over everything: the user asked for no vibration.
 * - Do-Not-Disturb suppresses sound *and* vibration unless [ignoresDoNotDisturb]
 *   is set. That override bypasses only the Do-Not-Disturb check, never the ringer.
 * - A phone on silent or vibrate never plays a sound: it buzzes instead, even when
 *   the chosen mode is [AudioMode.SOUND]. Muting the phone is asking it to be quiet.
 */
fun effectiveAudioMode(
    selected: AudioMode,
    ringer: RingerMode,
    doNotDisturb: Boolean,
    ignoresDoNotDisturb: Boolean,
): AudioMode {
    if (selected == AudioMode.SILENT) return AudioMode.SILENT
    if (doNotDisturb && !ignoresDoNotDisturb) return AudioMode.SILENT
    return if (ringer != RingerMode.NORMAL) AudioMode.VIBRATE else selected
}
