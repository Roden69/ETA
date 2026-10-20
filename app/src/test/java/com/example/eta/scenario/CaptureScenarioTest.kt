package com.example.eta.scenario

import android.app.Application
import com.example.eta.domain.chain.isWaitingFollowUp
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PullBlock
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.quickadd.QuickAddFeedback
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Area A, Erfassen & Listen: how a card is captured, kept, dropped and chained. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CaptureScenarioTest {

    @Test
    fun `quick add lands unconcretized in the Sammelliste and the concretize step lists it`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val added = step("Schnellerfassung") { quickAdd("Akku laden") }
        assertEquals(Stage.COLLECTION, added.stage)
        assertFalse("a jotted card has no category yet", added.isConcretized)

        step("Konkretisieren: die Karte steht in der Liste") {
            val vm = concretizeVm()
            subscribed(vm.pending) {
                awaitState(vm.pending, "\"Akku laden\" in the concretize step") { pending ->
                    pending.any { it.id == added.id }
                }
            }
        }
    }

    @Test
    fun `an unconcretized card cannot be taken into the week, and concretizing it takes it in`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val card = step("Schnellerfassung") { quickAdd("Mail an Vermieter") }

        step("Ziehen ohne Angaben wird abgelehnt") {
            val vm = weekPlannerVm()
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "the card offered in the Sammelliste column") { state ->
                    state.collection.any { it.id == card.id }
                }
                vm.pullIntoWeek(card)
            }
            assertEquals(Stage.COLLECTION, itemNamed("Mail an Vermieter").stage)
        }

        step("Ausgefüllt übernehmen: sie kommt in die Woche") {
            val vm = weekPlannerVm()
            subscribed(vm.uiState) {
                vm.concretizeGoal(
                    card,
                    card.name,
                    TodoAttributes(category = Category.NEBENBEI, priority = Priority.WANT, duration = 15.minutes),
                    intoWeek = true,
                )
                awaitStage(card, Stage.WEEK)
            }
        }
    }

    @Test
    fun `a card older than a month in the Sammelliste is banned by the weekly sweep, and its name is refused`() =
        scenario(start = "2026-10-18T09:00") {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            step("Sammelliste: eine Idee liegt seit 60 Tagen da") {
                seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
            }

            val banned = step("Wochenplanung öffnen: der Sweep sperrt die alte Idee") {
                val vm = weekPlannerVm()
                subscribed(vm.uiState) { awaitState(vm.banned, "the sweep reports one banned card") { it != null } }
            }
            assertEquals("exactly the one stale card is banned", 1, banned)
            assertEquals(Stage.LOCKED, itemNamed("Idee: Gewürzregal").stage)

            step("Gleicher Name per Schnellerfassung: abgelehnt") {
                val feedback = quickAddFeedback("Idee: Gewürzregal")
                assertTrue("the name is refused while it is locked", feedback is QuickAddFeedback.Blocked)
            }
            assertEquals(
                "the refused name leaves no second card behind",
                1,
                everyItem().count { it.name == "Idee: Gewürzregal" },
            )
        }

    @Test
    fun `a card dropped from the day returns to the Sammelliste with its original entry date`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val card = step("Konkretisieren und in die Woche") {
            val added = quickAdd("Pflaster kaufen")
            concretize(added, Category.NEBENBEI, Priority.WANT, 15.minutes).also {
                weekPlanner { pull(it) }
            }
        }
        val enteredAt = itemNamed("Pflaster kaufen").enteredCollectionAt
        assertNotNull(enteredAt)

        at(kotlinx.datetime.LocalTime(20, 0))
        step("Morgen: auf den Tagesplan, dann vom Tag genommen") {
            planDay(PlannerDay.TOMORROW) { place(card, kotlinx.datetime.LocalTime(10, 0)) }
            val vm = plannerVm(PlannerDay.TOMORROW)
            subscribed(vm.uiState) {
                val entry = awaitState(vm.uiState, "\"${card.name}\" on tomorrow's day") { state ->
                    state.blocks.any { it.item.id == card.id }
                }.blocks.first { it.item.id == card.id }
                vm.cancel(entry)
            }
            awaitStage(card, Stage.COLLECTION)
        }

        assertEquals(
            "the card keeps the moment it first entered the Sammelliste, so the ban cannot be dodged",
            enteredAt,
            itemNamed("Pflaster kaufen").enteredCollectionAt,
        )
    }

    @Test
    fun `a three step follow-up chain waits in the week pool until the step before is done`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Sammelliste mit Folgeaufgaben") { seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone)) }
        val first = itemNamed("Wohnung ausmisten")
        val second = itemNamed("Keller sortieren")
        val third = itemNamed("Dachboden leeren")

        step("Schritte 2 und 3 warten, mit Grund im Wochenplan") {
            assertTrue(second.isWaitingFollowUp())
            assertTrue(third.isWaitingFollowUp())
            val vm = weekPlannerVm()
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "step 2 held back with its reason") { state ->
                    state.held.any { (item, block) -> item.id == second.id && block == PullBlock.AfterPredecessor }
                }
            }
        }

        step("Schritt 1 in die Woche und auf den heutigen Tag") {
            weekPlanner { pull(first) }
            planDay(PlannerDay.TODAY) { place(first, kotlinx.datetime.LocalTime(14, 0)) }
        }

        step("Schritt 1 abhaken: Schritt 2 wird frei, Schritt 3 wartet weiter") {
            tick(first)
            assertFalse("step 2 is due now", itemNamed("Keller sortieren").isWaitingFollowUp())
            assertTrue("step 3 still waits for step 2", itemNamed("Dachboden leeren").isWaitingFollowUp())
            val vm = weekPlannerVm()
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "step 2 offered after step 1 is done") { state ->
                    state.collection.any { it.id == second.id } &&
                        state.held.any { it.first.id == third.id && it.second == PullBlock.AfterPredecessor }
                }
            }
        }

        step("Schritt 1 zurücknehmen: Schritt 2 wartet wieder") {
            untick(first)
            assertTrue("unticking makes the step wait again", itemNamed("Keller sortieren").isWaitingFollowUp())
            val vm = weekPlannerVm()
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "step 2 held back again after unticking step 1") { state ->
                    state.held.any { it.first.id == second.id && it.second == PullBlock.AfterPredecessor }
                }
            }
        }
    }

    @Test
    fun `the steps of a card travel with it into the day`() = scenario(start = "2026-10-18T09:00") {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Sammelliste") { seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone)) }
        val shopping = itemNamed("Einkaufsliste fürs Wochenende")

        step("Ziehen und auf den Tag setzen") {
            weekPlanner { pull(shopping) }
            planDay(PlannerDay.TODAY) { place(shopping, kotlinx.datetime.LocalTime(15, 0)) }
        }

        step("Auf dem Tagesplan stehen die drei Schritte") {
            val vm = plannerVm(PlannerDay.TODAY)
            subscribed(vm.uiState) {
                val state = awaitState(vm.uiState, "the steps of the shopping card on today's day") { state ->
                    state.blocks.any { it.item.id == shopping.id } && state.subtasks[shopping.id].orEmpty().size == 3
                }
                assertEquals(
                    listOf("Gemüse", "Brot", "Kaffee"),
                    state.subtasks.getValue(shopping.id).map { it.name },
                )
            }
        }
    }

    @Test
    fun `the Erfolgsliste shows a finished card and leaves out a finished standing task`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Sammelliste") { seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone)) }
        val dentist = itemNamed("Zahnarzt anrufen")

        step("Die Karte planen und abhaken") {
            weekPlanner { pull(dentist) }
            planDay(PlannerDay.TODAY) { place(dentist, kotlinx.datetime.LocalTime(15, 0)) }
            tick(dentist)
        }
        val morning = step("Die Morgenroutine abhaken, eine stehende Aufgabe") {
            val block = roleBlockOn(com.example.eta.domain.model.ItemRole.MORNING, clock.today)
            tick(block.item)
            block.item
        }

        val done = step("Erfolgsliste") { achievementsIncluding("Zahnarzt anrufen") }
        assertTrue(
            "a finished standing task is not an achievement",
            done.none { it.name == morning.name },
        )
    }
}
