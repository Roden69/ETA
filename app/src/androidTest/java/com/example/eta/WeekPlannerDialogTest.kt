package com.example.eta

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.eta.data.repository.WeekScope
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import com.example.eta.domain.setup.UserSetup
import com.example.eta.ui.components.LocalEtaClock
import com.example.eta.ui.root.weekPlannerViewModelFactory
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.weekplanner.WeekPlannerScreen
import com.example.eta.ui.weekplanner.WeekPlannerViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Dialog interactions use real Android windows, not Robolectric's window emulation. */
@RunWith(AndroidJUnit4::class)
class WeekPlannerDialogTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private lateinit var planner: WeekPlannerViewModel
    private val clock = object : Clock { override fun now() = Instant.parse("2026-10-21T07:00:00Z") }
    private val state = object : ExternalResource() {
        override fun before() {
            container = AppContainer(
                InstrumentationRegistry.getInstrumentation().targetContext,
                sandbox = true, clock = clock, timeZone = TimeZone.of("Europe/Berlin"),
            )
            runBlocking {
                container.setupRepository.complete(UserSetup.draft(clock.now()), emptyList())
                container.itemRepository.add(Item.newQuickTodo("Mail an Vermieter", clock.now()))
            }
        }
        override fun after() { container.close() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(state).around(compose)

    private fun openForm() {
        compose.setContent {
            EtaTheme(choice = DesignChoice()) {
                CompositionLocalProvider(LocalEtaClock provides clock) {
                    planner = viewModel(factory = weekPlannerViewModelFactory(container, WeekScope.MIDWEEK))
                    WeekPlannerScreen(viewModel = planner, calendarViewModel = null)
                }
            }
        }
        waitFor("Mail an Vermieter")
        compose.onNodeWithText("Mail an Vermieter").performScrollTo().performClick()
        waitFor("ToDo ausfüllen")
    }

    @Test
    fun cancellingConcretizingKeepsTheCardInCollection() {
        openForm()
        compose.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("ToDo ausfüllen").fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { container.itemRepository.observeStage(Stage.COLLECTION).first().any { it.name == "Mail an Vermieter" } })
        assertTrue(runBlocking { container.itemRepository.observeStage(Stage.WEEK).first().isEmpty() })
    }

    @Test
    fun savingConcretizingMovesTheAnsweredCardIntoTheWeek() {
        openForm()
        compose.onNode(hasText("Fokus — voller Einsatz") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNode(hasText("Muss geschehen") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNode(hasText("Speichern und in die Woche") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.waitUntil(15_000) {
            runBlocking { container.itemRepository.observeStage(Stage.WEEK).first().any { it.name == "Mail an Vermieter" && it.isConcretized } }
        }
        assertTrue(runBlocking { container.itemRepository.observeStage(Stage.COLLECTION).first().none { it.name == "Mail an Vermieter" } })
    }

    private fun waitFor(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
