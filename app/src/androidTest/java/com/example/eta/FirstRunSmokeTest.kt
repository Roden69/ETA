package com.example.eta

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.getSystemService
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.eta.alarm.PlanningAlarmContract
import com.example.eta.alarm.PlanningAlarmReceiver
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * First start, the questionnaire and the relaunch to the dashboard, on an emulator.
 *
 * Destructive: wipes the app's database before each test. Skipped on every device
 * whose fingerprint does not name an emulator, so a connected phone is never touched.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunSmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(FreshEmulatorState()).around(composeRule)

    @Test
    fun firstLaunchShowsWelcomeBeforeQuestionnaire() {
        composeRule.waitUntil(LAUNCH_TIMEOUT_MILLIS) { welcomeShown() }
        composeRule.onNodeWithText(WELCOME_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(WELCOME_ACTION).assertIsDisplayed()
    }

    @Test
    fun answeredSetupOpensDashboardAndArmsPlanningAlarms() {
        composeRule.waitUntil(LAUNCH_TIMEOUT_MILLIS) { welcomeShown() }
        val context = targetContext()
        val container = (context.applicationContext as EtaApplication).container

        runBlocking {
            container.setupRepository.complete(UserSetup.draft(Clock.System.now()), emptyList())
        }

        composeRule.activityRule.scenario.close()
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntil(LAUNCH_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText(DASHBOARD_TAB).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitUntil(LAUNCH_TIMEOUT_MILLIS) {
                PlanningPhase.entries.all { planningAlarmArmed(context, it) }
            }
            val manager = checkNotNull(context.getSystemService<AlarmManager>())
            assertNotNull("No alarm clock is shown in the system", manager.nextAlarmClock)
        }
    }

    private fun welcomeShown(): Boolean =
        composeRule.onAllNodesWithText(WELCOME_TITLE).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val WELCOME_TITLE = "Willkommen bei Eta"
        const val WELCOME_ACTION = "Einrichtung starten"
        const val DASHBOARD_TAB = "Dashboard"
        const val LAUNCH_TIMEOUT_MILLIS = 15_000L
    }
}

private fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

/** Whether the system still holds the ring intent the planning scheduler armed for [phase]. */
private fun planningAlarmArmed(context: Context, phase: PlanningPhase): Boolean {
    val ring = Intent(context, PlanningAlarmReceiver::class.java).apply {
        action = PlanningAlarmContract.ACTION_RING
        putExtra(PlanningAlarmContract.EXTRA_PHASE, phase.name)
    }
    return PendingIntent.getBroadcast(
        context,
        PlanningAlarmContract.requestCode(phase),
        ring,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null
}

/**
 * Resets the app to a first start before the activity launches: empty database,
 * welcome page owed again, alarms cleared, notification permission granted so the
 * system dialog does not cover the questionnaire.
 *
 * Uses the app's own reset path rather than `pm clear`: the test runs inside the
 * app process, which `pm clear` would kill.
 */
private class FreshEmulatorState : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            assumeTrue(
                "Destructive test: runs only on an emulator, never on a physical device",
                isEmulator(),
            )
            val context = targetContext()
            check(context.packageName == "com.example.erik_iteration_2") {
                "Refusing to reset data of ${context.packageName}"
            }
            val container = (context.applicationContext as EtaApplication).container
            runBlocking {
                container.tutorialStore.reset()
                container.setupRepository.resetEverything()
                container.planningAlarmCoordinator.rescheduleAll()
                container.taskStartCoordinator.reschedule()
                container.reminderCoordinator.reschedule()
            }
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            base.evaluate()
        }
    }

    private fun isEmulator(): Boolean = with(Build.FINGERPRINT) {
        contains("generic") || contains("emulator") || contains("sdk_gphone")
    }
}
