package com.example.eta.ui.root

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.eta.scenario.SampleData
import com.example.eta.ui.UiWorld
import com.example.eta.ui.UiWorldRule
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.theme.EtaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The app shell over the real wiring: the tab bar and the dashboard of a set-up app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppShellTest {
    private val worlds = UiWorldRule()
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(worlds).around(compose)

    @Test
    fun `a set-up app opens on the dashboard with its tab bar`() {
        val world = UiWorld("2026-10-18T09:00").also { worlds.world = it }
        world.markIntroSeen()
        world.completeSetup(SampleData.lenaSetup(world.clock.now()))

        compose.setContent { EtaTheme(choice = DesignChoice()) { EtaApp(world.container) } }

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Listen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Listen").assertIsDisplayed()
    }
}
