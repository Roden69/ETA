package com.example.eta.ui.weekplanner

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.scenario.SampleData
import com.example.eta.scenario.SeedCard
import com.example.eta.ui.UiWorld
import com.example.eta.ui.UiWorldRule
import com.example.eta.ui.awaitGone
import com.example.eta.ui.awaitText
import com.example.eta.ui.dialogNode
import com.example.eta.ui.node
import com.example.eta.ui.root.EtaApp
import com.example.eta.ui.tap
import com.example.eta.ui.tapInDialog
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The mid-week top-up ("Woche ergänzen", #54b) through the app's own navigation:
 * the Listen tab's "Wochenliste füllen" opens it on the calendar page, and the
 * Sammelliste column is the page after it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekPlannerMidWeekTest {
    private val worlds = UiWorldRule()
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(worlds).around(compose)

    /** Wednesday, 21 October 2026: the week's cycle began on Sunday the 18th. */
    private fun setUpLena(): UiWorld {
        val world = UiWorld("2026-10-21T09:00").also { worlds.world = it }
        world.markIntroSeen()
        world.completeSetup(SampleData.lenaSetup(world.clock.now()))
        return world
    }

    private fun UiWorld.concretized(name: String): Item = Item.newTodo(
        name = name,
        category = Category.FOKUS,
        priority = Priority.MUST,
        targetDate = null,
        estimatedDuration = 60.minutes,
        now = clock.now(),
    )

    private fun startApp(world: UiWorld) {
        compose.setContent { EtaTheme(choice = DesignChoice()) { EtaApp(world.container) } }
    }

    /** From the dashboard to the mid-week top-up: the Listen tab's "Wochenliste füllen". */
    private fun openMidWeek() {
        compose.tap("Listen")
        compose.tap("Wochenliste")
        compose.tap("Wochenliste füllen")
    }

    private fun UiWorld.stageNames(stage: Stage): List<String> = runBlocking {
        container.itemRepository.observeStage(stage).first().map { it.name }
    }

    private fun UiWorld.weekItem(name: String): Item = runBlocking {
        container.itemRepository.observeStage(Stage.WEEK).first().single { it.name == name }
    }

    @Test
    fun `the top-up opens on the calendar page and says how many ToDos are ready`() {
        val world = setUpLena()
        runBlocking {
            world.seedCollection(
                listOf(
                    SeedCard(world.concretized("Steuerunterlagen sortieren")),
                    SeedCard(world.concretized("Fahrrad zur Werkstatt bringen")),
                ),
            )
        }
        startApp(world)

        openMidWeek()

        compose.awaitText("Woche ergänzen")
        compose.awaitText("2 ToDos bereit zum Einplanen.")
        compose.node("Weiter zu deinen ToDos").assertIsDisplayed()
        compose.onAllNodesWithText("Sammelliste").assertCountEquals(0)
    }

    @Test
    fun `Weiter zu deinen ToDos moves on to the Sammelliste column`() {
        val world = setUpLena()
        runBlocking {
            world.seedCollection(listOf(SeedCard(world.concretized("Steuerunterlagen sortieren"))))
        }
        startApp(world)
        openMidWeek()

        compose.tap("Weiter zu deinen ToDos")

        compose.awaitText("Sammelliste")
        compose.node("Diese Woche").assertIsDisplayed()
        compose.node("Steuerunterlagen sortieren").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a goal of an earlier cycle is shown at the top of the Sammelliste with both answers`() {
        val world = setUpLena()
        val goal = world.concretized("Altes Ziel")
        runBlocking {
            world.container.itemRepository.add(goal)
            // Taken on in the cycle that began on 11 October: an open goal of an earlier cycle.
            world.container.itemRepository.takeIntoWeek(goal, LocalDate(2026, 10, 11))
            world.container.itemRepository.add(world.concretized("Neues ToDo"))
        }
        startApp(world)
        openMidWeek()

        compose.awaitText("Gerade gesperrt: offene Ziele der letzten Woche.")
        compose.tap("Weiter zu deinen ToDos")

        compose.awaitText("Offen aus der letzten Woche")
        compose.node("In diese Woche übernehmen").performScrollTo().assertIsDisplayed()
        compose.node("Zurück in die Sammelliste").assertIsDisplayed()
        // The goal sits in the week's stage, so its name also appears in the week column.
        compose.onAllNodesWithText("Altes Ziel").assertCountEquals(2)
        compose.node("Erst abarbeiten, dann Neues").performScrollTo().assertIsDisplayed()
        compose.node(
            "Erst das offene Ziel der letzten Woche verplanen oder zurückgeben. " +
                "Es steht oben in der Sammelliste.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Zurück in die Sammelliste frees the column so its cards can be pulled`() {
        val world = setUpLena()
        val goal = world.concretized("Altes Ziel")
        runBlocking {
            world.container.itemRepository.add(goal)
            world.container.itemRepository.takeIntoWeek(goal, LocalDate(2026, 10, 11))
            world.container.itemRepository.add(world.concretized("Neues ToDo"))
        }
        startApp(world)
        openMidWeek()
        compose.tap("Weiter zu deinen ToDos")
        compose.awaitText("Offen aus der letzten Woche")

        compose.tap("Zurück in die Sammelliste")

        compose.awaitGone("Offen aus der letzten Woche")
        compose.awaitGone("Noch nicht in dieser Woche")
        // The card that was held is tappable now, and it goes into the week.
        compose.tap("Neues ToDo")
        compose.awaitGone("Noch nichts vorgenommen.")
        assertTrue("Neues ToDo is in the week", world.stageNames(Stage.WEEK).contains("Neues ToDo"))
        assertTrue(
            "the handed-back goal is in the Sammelliste",
            world.stageNames(Stage.COLLECTION).contains("Altes Ziel"),
        )
    }

    @Test
    fun `In diese Woche übernehmen takes the open goal into this week and opens the column`() {
        val world = setUpLena()
        val goal = world.concretized("Altes Ziel")
        runBlocking {
            world.container.itemRepository.add(goal)
            world.container.itemRepository.takeIntoWeek(goal, LocalDate(2026, 10, 11))
        }
        startApp(world)
        openMidWeek()
        compose.tap("Weiter zu deinen ToDos")
        compose.awaitText("Offen aus der letzten Woche")

        compose.tap("In diese Woche übernehmen")

        compose.awaitGone("Offen aus der letzten Woche")
        compose.awaitGone("Erst abarbeiten, dann Neues")
        assertEquals(LocalDate(2026, 10, 18), world.weekItem("Altes Ziel").weekStartedOn)
    }

    @Test
    fun `tapping an unfinished card opens the concretize form and cancel keeps it in the Sammelliste`() {
        val world = setUpLena()
        val unfinished = Item.newQuickTodo("Mail an Vermieter", world.clock.now())
        runBlocking { world.seedCollection(listOf(SeedCard(unfinished))) }
        startApp(world)
        openMidWeek()
        compose.tap("Weiter zu deinen ToDos")

        compose.awaitText("noch unvollständig — antippen zum Ausfüllen")
        compose.tap("Mail an Vermieter")
        compose.awaitText("ToDo ausfüllen")

        compose.tapInDialog("Abbrechen")

        compose.awaitGone("ToDo ausfüllen")
        compose.node("Mail an Vermieter").performScrollTo().assertIsDisplayed()
        assertTrue(
            "cancel keeps the card in the Sammelliste",
            world.stageNames(Stage.COLLECTION).contains("Mail an Vermieter"),
        )
    }

    @Test
    fun `saving the concretize form takes the unfinished card into the week`() {
        val world = setUpLena()
        val unfinished = Item.newQuickTodo("Mail an Vermieter", world.clock.now())
        runBlocking { world.seedCollection(listOf(SeedCard(unfinished))) }
        startApp(world)
        openMidWeek()
        compose.tap("Weiter zu deinen ToDos")
        compose.awaitText("noch unvollständig — antippen zum Ausfüllen")

        compose.tap("Mail an Vermieter")
        compose.awaitText("ToDo ausfüllen")
        compose.tapInDialog("Fokus — voller Einsatz")
        compose.tapInDialog("Muss geschehen")
        compose.tapInDialog("Speichern und in die Woche")

        compose.awaitGone("ToDo ausfüllen")
        compose.waitUntil(timeoutMillis = 10_000) {
            world.stageNames(Stage.WEEK).contains("Mail an Vermieter")
        }
        assertTrue("the saved card is concretized", world.weekItem("Mail an Vermieter").isConcretized)
    }
}
