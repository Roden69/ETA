package com.example.eta.ui.root

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.scenario.SampleData
import com.example.eta.ui.UiWorld
import com.example.eta.ui.UiWorldRule
import com.example.eta.ui.awaitText
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.theme.EtaTheme
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppNavigationTest {
    private val worlds = UiWorldRule()
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(worlds).around(compose)

    private fun start(sandbox: Boolean = true): UiWorld = UiWorld("2026-10-20T21:00", sandbox = sandbox).also {
        worlds.world = it
        it.markIntroSeen()
        it.completeSetup(SampleData.lenaSetup(it.clock.now()))
    }

    @Test
    fun `pause and daily free time edits persist through settings navigation`() {
        val world = start()
        compose.setContent { EtaTheme(choice = DesignChoice()) { EtaApp(world.container) } }
        click("Einstellungen")
        click("Pausen & Freizeit", scroll = true)
        increment("Standardpause für ToDos")
        increment("Dauer")
        click("Sichern", scroll = true)
        compose.waitUntil(10_000) {
            runBlocking {
                world.container.setupRepository.find()?.let {
                    it.defaultBreakAfter == 5.minutes && it.freeTime.duration == 105.minutes
                } == true
            }
        }
        val saved = runBlocking { checkNotNull(world.container.setupRepository.find()) }
        assertEquals(5.minutes, saved.defaultBreakAfter)
        assertEquals(105.minutes, saved.freeTime.duration)
        screenshot("settings-pauses")
    }

    @Test
    fun `the evening route reaches concretizing tomorrow confirmation and the wind-down screen`() {
        val world = start(sandbox = false)
        runBlocking {
            val setup = checkNotNull(world.container.setupRepository.find())
            world.container.setupRepository.saveSettings(setup.copy(pointsSystem = false, contracts = false))
            world.container.planRepository.findDay(world.clock.today).forEach {
                world.container.planRepository.complete(it.block)
            }
        }
        compose.setContent {
            EtaTheme(choice = DesignChoice()) {
                EtaApp(world.container, openPhase = PlanningPhase.DAILY)
            }
        }
        compose.awaitText("Tagesabschluss")
        click("Weiter")
        click("Weiter")
        click("Abschließen")
        compose.awaitText("Quick-Adds ausfüllen")
        click("Weiter zur Tagesplanung")
        // No account: the empty calendar step forwards to tomorrow's planner.
        compose.awaitText("Morgen planen")
        click("Bestätigen")
        compose.awaitText("Liste für Morgen")
        click("Passt so")
        compose.awaitText("Abendruhe")
        assertTrue(runBlocking { world.container.planRepository.findDayPlan(world.clock.today)?.isSettled == true })
        screenshot("evening-wind-down")
        click("Gute Nacht")
        compose.awaitText("Listen")
    }

    private fun click(text: String, scroll: Boolean = false) {
        compose.awaitText(text)
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    private fun increment(label: String) {
        compose.awaitText(label)
        val bottom = compose.onNodeWithText(label).performScrollTo().getUnclippedBoundsInRoot().bottom
        val buttons = compose.onAllNodesWithText("+")
        val nearest = (0 until buttons.fetchSemanticsNodes().size)
            .filter { buttons[it].getUnclippedBoundsInRoot().top >= bottom }
            .minBy { buttons[it].getUnclippedBoundsInRoot().top }
        buttons[nearest].performScrollTo().performClick()
    }

    private fun screenshot(name: String) {
        val file = File("build/reports/ui-smoke/$name.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
