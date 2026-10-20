package com.example.eta.scenario

import android.app.Application
import com.example.eta.alarm.PlanningAlarmContract
import com.example.eta.alarm.ReminderAlarmContract
import com.example.eta.alarm.TaskStartAlarmContract
import com.example.eta.ui.root.settingsViewModelFactory
import com.example.eta.ui.settings.SettingsMessage
import com.example.eta.ui.settings.SettingsViewModel
import com.example.eta.domain.planning.PlanningPhase
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the alarms arm, read from the system's alarm manager: each alarm is found
 * by its request code or its intent action and checked by the moment it is due.
 * Only a scenario with `alarms = true` arms anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AlarmScenarioTest {

    @Test
    fun `the planning alarms are armed at the daily and the weekly time after the setup`() = scenario(
        start = "2026-10-18T09:00",
        alarms = true,
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Einrichtung: Planungsalarme stellen") { container.planningAlarmCoordinator.rescheduleAll() }

        assertEquals(
            "the daily planning rings at 20:00",
            listOf(moment(2026, 10, 18, 20, 0)),
            armedWithCode(PlanningAlarmContract.requestCode(PlanningPhase.DAILY)).map { it.due },
        )
        assertEquals(
            "the weekly planning rings on Sunday at 18:00",
            listOf(moment(2026, 10, 18, 18, 0)),
            armedWithCode(PlanningAlarmContract.requestCode(PlanningPhase.WEEKLY)).map { it.due },
        )
    }

    @Test
    fun `saving bedtime settings moves today's next task alarm with the unstarted block`() = scenario(
        start = "2026-10-20T21:30",
        alarms = true,
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        container.taskStartCoordinator.reschedule()
        assertEquals(
            listOf(moment(2026, 10, 20, 22, 0)),
            armedWithCode(TaskStartAlarmContract.REQUEST_CODE).map { it.due },
        )

        val settings = viewModel<SettingsViewModel>(settingsViewModelFactory(container))
        awaitState(settings.draft, "bedtime settings loaded") { it != null }
        settings.update { it.copy(bedPrepTime = LocalTime(22, 30)) }
        settings.save()
        awaitState(settings.message, "settings saved and alarms rearmed") { it == SettingsMessage.Saved }
        assertEquals(
            listOf(moment(2026, 10, 20, 22, 30)),
            armedWithCode(TaskStartAlarmContract.REQUEST_CODE).map { it.due },
        )
    }

    @Test
    fun `the wake alarm is armed at the next wake time once it is switched on`() = scenario(
        start = "2026-10-19T06:00",
        alarms = true,
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Weckruf einschalten") {
            val setup = checkNotNull(container.setupRepository.find())
            container.setupRepository.saveSettings(setup.copy(wakeAlarm = true))
            container.wakeAlarmCoordinator.reschedule()
        }
        assertTrue(
            "the wake alarm rings at 07:00 on Monday",
            armedAlarms().any { it.due == moment(2026, 10, 19, 7, 0) },
        )
    }

    @Test
    fun `a reminder is armed for its time`() = scenario(
        start = "2026-10-20T09:00",
        alarms = true,
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Erinnerung für 15:00") {
            container.reminderRepository.add("Medikament nehmen", moment(2026, 10, 20, 15, 0))
            container.reminderCoordinator.reschedule()
        }
        assertEquals(
            "the reminder rings at its time",
            listOf(moment(2026, 10, 20, 15, 0)),
            armedWithAction(ReminderAlarmContract.ACTION_RING).map { it.due },
        )
    }

    @Test
    fun `a scenario without alarms arms nothing, whatever is set up`() = scenario(start = "2026-10-18T09:00") {
        step("Einrichtung mit Weckruf und Erinnerung") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            val setup = checkNotNull(container.setupRepository.find())
            container.setupRepository.saveSettings(setup.copy(wakeAlarm = true))
            container.reminderRepository.add("Medikament nehmen", moment(2026, 10, 18, 15, 0))
        }
        step("Alle Alarme neu stellen") {
            container.planningAlarmCoordinator.rescheduleAll()
            container.taskStartCoordinator.reschedule()
            container.wakeAlarmCoordinator.reschedule()
            container.reminderCoordinator.reschedule()
            container.calendarRefreshCoordinator.reschedule()
        }
        assertTrue("nothing is armed", armedAlarms().isEmpty())
    }


    private fun moment(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime(year, month, day, hour, minute).toInstant(ZONE)

    private fun armedWithCode(requestCode: Int): List<ArmedAlarm> =
        armedAlarms().filter { it.requestCode == requestCode }

    private fun armedWithAction(action: String): List<ArmedAlarm> =
        armedAlarms().filter { it.action == action }


    private companion object {
        val ZONE: TimeZone = TimeZone.of("Europe/Berlin")
    }
}
