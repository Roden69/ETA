package com.example.eta.alarm

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Vibrator
import com.example.eta.R
import com.example.eta.domain.audio.AudioMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class EtaSoundTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val vibrator get() = context.getSystemService(Vibrator::class.java)
    private val players = mutableListOf<MediaPlayer>()

    @Before
    fun setUp() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        EtaSound.setMode(context, AudioMode.SOUND)
        EtaSound.setIgnoresDoNotDisturb(context, false)
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1_000, 0) }
        ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
    }

    @After
    fun tearDown() {
        finishPlayers()
    }

    @Test
    fun `muted phone vibrates without playing task sounds even with DND override`() {
        for (ringer in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
            audio.ringerMode = ringer
            notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            EtaSound.setIgnoresDoNotDisturb(context, true)

            assertTaskOutput(AudioMode.VIBRATE)
        }
    }

    @Test
    fun `muted speech requests the vibration fallback instead of swallowing the announcement`() = runBlocking {
        for (ringer in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
            audio.ringerMode = ringer

            assertFalse(EtaSpeech.speak(context, "Jetzt: Beispielaufgabe."))
            assertTaskOutput(AudioMode.VIBRATE)
        }
    }

    @Test
    fun `DND without override suppresses sound speech and vibration`() = runBlocking {
        for (filter in listOf(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            NotificationManager.INTERRUPTION_FILTER_NONE,
        )) {
            notifications.setInterruptionFilter(filter)
            for (mode in listOf(AudioMode.SOUND, AudioMode.VIBRATE)) {
                EtaSound.setMode(context, mode)
                assertTrue(EtaSpeech.speak(context, "Jetzt: Beispielaufgabe."))
                assertTaskOutput(AudioMode.SILENT)
            }
        }
    }

    @Test
    fun `DND override permits audible tasks only on an unmuted phone`() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        EtaSound.setIgnoresDoNotDisturb(context, true)

        assertTaskOutput(AudioMode.SOUND)
    }

    @Test
    fun `normal phone still plays task start and end sounds`() {
        assertTaskOutput(AudioMode.SOUND)
    }

    @Test
    fun `ignoring do not disturb is off on a fresh install`() {
        context.getSharedPreferences("sound", Context.MODE_PRIVATE).edit().clear().commit()

        assertFalse(EtaSound.ignoresDoNotDisturb(context))
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertEquals(AudioMode.SILENT, EtaSound.effectiveMode(context))
    }

    @Test
    fun `explicit silent mode suppresses even vibration on a muted phone`() = runBlocking {
        EtaSound.setMode(context, AudioMode.SILENT)
        EtaSound.setIgnoresDoNotDisturb(context, true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT

        assertTrue(EtaSpeech.speak(context, "Jetzt: Beispielaufgabe."))
        assertTaskOutput(AudioMode.SILENT)
    }

    @Test
    fun `explicit vibration mode works on both normal and silent phones`() {
        EtaSound.setMode(context, AudioMode.VIBRATE)
        for (ringer in listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_SILENT)) {
            audio.ringerMode = ringer

            assertTaskOutput(AudioMode.VIBRATE)
        }
    }

    @Test
    fun `muted speech uses vibration even when DND is overridden`() = runBlocking {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        EtaSound.setIgnoresDoNotDisturb(context, true)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT

        assertFalse(EtaSpeech.speak(context, "Jetzt: Beispielaufgabe."))
        assertTaskOutput(AudioMode.VIBRATE)
    }

    private fun assertTaskOutput(expected: AudioMode) {
        for (sound in listOf(R.raw.task_start, R.raw.task_end)) {
            vibrator.cancel()
            finishPlayers()

            EtaSound.play(context, sound)

            assertEquals(
                "Unexpected vibration for $expected",
                expected == AudioMode.VIBRATE,
                shadowOf(vibrator).isVibrating,
            )
            if (expected == AudioMode.SOUND) {
                assertEquals(1, players.size)
                assertTrue("The task sound must actually start", players.single().isPlaying)
            } else {
                assertTrue("A quiet announcement must not create a sound player", players.isEmpty())
            }
        }
    }

    private fun finishPlayers() {
        players.forEach { shadowOf(it).invokeCompletionListener() }
        players.clear()
    }
}
