package com.example.eta.domain

import com.example.eta.domain.audio.AudioMode
import com.example.eta.domain.audio.RingerMode
import com.example.eta.domain.audio.effectiveAudioMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one decision behind every sound and vibration Eta makes, except the wake
 * alarm: which mode plays, given the chosen mode, the ringer and Do-Not-Disturb.
 */
class AudioModeRuleTest {

    private fun decide(
        selected: AudioMode,
        ringer: RingerMode = RingerMode.NORMAL,
        doNotDisturb: Boolean = false,
        ignoresDoNotDisturb: Boolean = false,
    ): AudioMode = effectiveAudioMode(selected, ringer, doNotDisturb, ignoresDoNotDisturb)

    @Test
    fun `a normal ringer plays the chosen sound or vibration`() {
        assertEquals(AudioMode.SOUND, decide(AudioMode.SOUND))
        assertEquals(AudioMode.VIBRATE, decide(AudioMode.VIBRATE))
    }

    @Test
    fun `a silent or vibrate ringer buzzes instead of playing a sound`() {
        for (ringer in listOf(RingerMode.SILENT, RingerMode.VIBRATE)) {
            assertEquals("ringer $ringer, sound", AudioMode.VIBRATE, decide(AudioMode.SOUND, ringer))
            assertEquals("ringer $ringer, vibration", AudioMode.VIBRATE, decide(AudioMode.VIBRATE, ringer))
        }
    }

    @Test
    fun `still stays silent whatever the ringer and the override say`() {
        for (ringer in RingerMode.entries) {
            for (doNotDisturb in listOf(false, true)) {
                for (override in listOf(false, true)) {
                    assertEquals(
                        "ringer $ringer, DND $doNotDisturb, override $override",
                        AudioMode.SILENT,
                        decide(AudioMode.SILENT, ringer, doNotDisturb, override),
                    )
                }
            }
        }
    }

    @Test
    fun `do not disturb silences sound and vibration without the override`() {
        for (ringer in RingerMode.entries) {
            for (selected in listOf(AudioMode.SOUND, AudioMode.VIBRATE)) {
                assertEquals(
                    "$selected on ringer $ringer",
                    AudioMode.SILENT,
                    decide(selected, ringer, doNotDisturb = true),
                )
            }
        }
    }

    @Test
    fun `the override lets a normal ringer sound through do not disturb`() {
        assertEquals(AudioMode.SOUND, decide(AudioMode.SOUND, doNotDisturb = true, ignoresDoNotDisturb = true))
        assertEquals(AudioMode.VIBRATE, decide(AudioMode.VIBRATE, doNotDisturb = true, ignoresDoNotDisturb = true))
    }

    @Test
    fun `the override never unmutes a silent or vibrate ringer`() {
        for (ringer in listOf(RingerMode.SILENT, RingerMode.VIBRATE)) {
            assertEquals(
                "ringer $ringer under DND with override",
                AudioMode.VIBRATE,
                decide(AudioMode.SOUND, ringer, doNotDisturb = true, ignoresDoNotDisturb = true),
            )
        }
    }

    @Test
    fun `the override has no effect when do not disturb is off`() {
        assertEquals(AudioMode.SOUND, decide(AudioMode.SOUND, ignoresDoNotDisturb = true))
        assertEquals(AudioMode.VIBRATE, decide(AudioMode.SOUND, RingerMode.SILENT, ignoresDoNotDisturb = true))
    }
}
