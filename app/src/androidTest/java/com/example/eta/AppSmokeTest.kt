package com.example.eta

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Facts about the installed app that hold on any device. Read-only: nothing here
 * touches the user's data, so it may run on a physical phone.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `installed app keeps the package id of the original install`() {
        assertEquals("com.example.erik_iteration_2", context.packageName)
    }

    @Test
    fun `startup creates every notification channel the alarms post to`() {
        val manager = checkNotNull(context.getSystemService<NotificationManager>())
        listOf(
            "planning_v3",
            "task_start_v2",
            "task_end_v2",
            "still_active",
            "pomodoro",
            "reminders",
            "wake",
        ).forEach { channelId ->
            assertNotNull("Missing notification channel $channelId", manager.getNotificationChannel(channelId))
        }
    }
}
