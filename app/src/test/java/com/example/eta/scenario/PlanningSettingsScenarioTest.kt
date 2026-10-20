package com.example.eta.scenario

import android.app.AlarmManager
import android.app.Application
import com.example.eta.alarm.PlanningAlarmContract
import com.example.eta.domain.planning.PlanningPhase
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Settings changes must update the alarm already armed in Android, not just the preview. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlanningSettingsScenarioTest {
    @Test
    fun `saving a new daily planning time re-arms the existing planning alarm`() = scenario(
        start = "2026-10-19T09:00",
        alarms = true,
    ) {
        step("Einrichtung und ursprünglichen Planungsalarm stellen") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            container.planningAlarmCoordinator.rescheduleAll()
            assertEquals(
                LocalDateTime(2026, 10, 19, 20, 0).toInstant(zone).toEpochMilliseconds(),
                dailyPlanningAlarmTime(),
            )
        }
        step("Tagesplanung auf 19:30 ändern und sichern") {
            changeSettings { it.copy(dailyPlanningTime = LocalTime(19, 30)) }
        }
        step("Der bereits gestellte Planungsalarm folgt der gespeicherten Zeit") {
            assertEquals(
                LocalDateTime(2026, 10, 19, 19, 30).toInstant(zone).toEpochMilliseconds(),
                dailyPlanningAlarmTime(),
            )
        }
    }

    private fun dailyPlanningAlarmTime(): Long {
        val manager = RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java)
        return shadowOf(manager).scheduledAlarms.single { alarm ->
            val intent = shadowOf(alarm.operation).savedIntent
            intent.action == PlanningAlarmContract.ACTION_RING &&
                intent.getStringExtra(PlanningAlarmContract.EXTRA_PHASE) == PlanningPhase.DAILY.name
        }.triggerAtMs
    }
}
