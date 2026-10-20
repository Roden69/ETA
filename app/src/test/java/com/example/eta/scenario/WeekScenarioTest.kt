package com.example.eta.scenario

import android.app.Application
import com.example.eta.data.repository.WeekScope
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PullBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Area B, Woche: the weekly planning phase, the mid-week top-up, and the devaluation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WeekScenarioTest {

    @Test
    fun `the scheduled weekly planning pulls a goal into the week, stamped with the cycle it began in`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Sammelliste") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
        }
        val card = itemNamed("Zahnarzt anrufen")

        step("Wochenplanung am Sonntag: ziehen") { weekPlanner(WeekScope.SCHEDULED) { pull(card) } }

        val pulled = itemNamed("Zahnarzt anrufen")
        assertEquals(Stage.WEEK, pulled.stage)
        assertEquals("the planning day starts the cycle", LocalDate(2026, 10, 18), pulled.weekStartedOn)
    }

    @Test
    fun `the mid-week top-up pulls a goal the same way and keeps the cycle it belongs to`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Sammelliste") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
        }
        val card = itemNamed("Zahnarzt anrufen")
        step("Wochenplanung am Sonntag: nur Vorbereitung") { weekPlanner { } }

        advanceTo(LocalDateTime(2026, 10, 21, 10, 0))
        step("Mittwoch: Nachtrag ziehen") { weekPlanner(WeekScope.MIDWEEK) { pull(card) } }

        val pulled = itemNamed("Zahnarzt anrufen")
        assertEquals(Stage.WEEK, pulled.stage)
        assertEquals("the top-up belongs to the cycle begun on Sunday", LocalDate(2026, 10, 18), pulled.weekStartedOn)
    }

    @Test
    fun `an open goal of the previous cycle holds the Sammelliste, and taking it on frees the pool`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Sammelliste") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
        }
        val goal = itemNamed("Zahnarzt anrufen")
        step("Sonntag: das Ziel in die Woche") { weekPlanner { pull(goal) } }

        advanceTo(LocalDateTime(2026, 10, 25, 18, 0))
        val vm = weekPlannerVm()
        step("Nächster Sonntag: das offene Ziel steht oben und hält die Sammelliste") {
            subscribed(vm.uiState) {
                val state = awaitState(vm.uiState, "the open goal surfaced") { state ->
                    state.unfinished.any { it.id == goal.id }
                }
                assertFalse("no new goal while the old one is open", state.canTakeOnMore)
                assertTrue("nothing from the Sammelliste may be pulled", state.collection.isEmpty())
                assertTrue(
                    "the held cards name the open goals as their reason",
                    state.held.any { it.second is PullBlock.OpenGoals },
                )
            }
        }

        step("In diese Woche übernehmen: neuer Stempel, Pool frei") {
            subscribed(vm.uiState) {
                vm.takeUnfinishedIntoWeek(goal)
                awaitState(vm.uiState, "the pool freed after taking the goal on") { state ->
                    state.unfinished.isEmpty() && state.canTakeOnMore
                }
            }
            assertEquals(LocalDate(2026, 10, 25), itemNamed("Zahnarzt anrufen").weekStartedOn)
        }
    }

    @Test
    fun `an open goal of the previous cycle can be handed back to the Sammelliste, which frees the pool`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Sammelliste") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
        }
        val goal = itemNamed("Zahnarzt anrufen")
        step("Sonntag: das Ziel in die Woche") { weekPlanner { pull(goal) } }

        advanceTo(LocalDateTime(2026, 10, 25, 18, 0))
        val vm = weekPlannerVm()
        step("Nächster Sonntag: zurück in die Sammelliste") {
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "the open goal surfaced") { state -> state.unfinished.any { it.id == goal.id } }
                vm.returnToCollection(goal)
                awaitState(vm.uiState, "the pool freed after handing the goal back") { state ->
                    state.unfinished.isEmpty() && state.canTakeOnMore && state.collection.any { it.id == goal.id }
                }
            }
            assertNull("the cycle stamp goes with the commitment", itemNamed("Zahnarzt anrufen").weekStartedOn)
        }
    }

    @Test
    fun `a card whose unlock day lies after the week's end is held back and says from when`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Sammelliste") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
        }
        val later = itemNamed("Urlaubsplanung")
        val vm = weekPlannerVm()
        step("Wochenplanung: Urlaubsplanung ist noch nicht einplanbar") {
            subscribed(vm.uiState) {
                val state = awaitState(vm.uiState, "the held card with its unlock day") { state ->
                    state.held.any { it.first.id == later.id }
                }
                val block = state.held.single { it.first.id == later.id }.second
                assertEquals(PullBlock.NotYetAvailable(LocalDate(2026, 11, 1)), block)
                vm.pullIntoWeek(later)
            }
            assertEquals("a held card cannot be pulled", Stage.COLLECTION, itemNamed("Urlaubsplanung").stage)
        }
    }

    @Test
    fun `a card taken into the week goes back to the Sammelliste and its one-month clock restarts`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Sammelliste") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
        }
        val card = itemNamed("Zahnarzt anrufen")
        step("Sonntag: in die Woche") { weekPlanner { pull(card) } }

        at(LocalTime(19, 0))
        val vm = weekPlannerVm()
        step("Wieder zurück in die Sammelliste") {
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "the card in the week list") { state -> state.weekList.any { it.id == card.id } }
                vm.returnToCollection(card)
            }
            awaitStage(card, Stage.COLLECTION)
        }

        val back = itemNamed("Zahnarzt anrufen")
        assertNull(back.weekStartedOn)
        assertEquals(
            "handing it back on purpose restarts the one-month clock",
            kotlinx.datetime.LocalDateTime(2026, 10, 18, 19, 0).toInstant(zone),
            back.enteredCollectionAt,
        )
    }

    @Test
    fun `the week budget counts the 15 and 25 minute breaks that its tasks earn`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Sammelliste") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
        }
        val short = itemNamed("Balkon aufräumen")  // 45 minutes: earns no break
        val hour = itemNamed("Linsensuppe ausprobieren") // 60 minutes: earns 15
        val long = itemNamed("Steuerunterlagen sortieren") // 90 minutes: earns 25
        val vm = weekPlannerVm()
        step("Drei Karten in die Woche") {
            weekPlanner { pull(short); pull(hour); pull(long) }
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "the three cards in the week list") { it.weekList.size == 3 }
            }
        }
        step("Budget: Aufgabe plus verdiente Pause") {
            subscribed(vm.uiState) {
                val budget = awaitState(vm.uiState, "the budget counted") { it.weekList.size == 3 }.budget
                assertEquals(45 + (60 + 15) + (90 + 25), budget.plannedMinutes)
            }
        }
    }

    @Test
    fun `the devaluation is booked once in a week on its weekday, however often the app starts`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung und Startguthaben") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            container.pointsRepository.record(100.0, PointsReason.CONTRACT, "Startguthaben")
        }

        step("Sonntag: vor dem gewählten Wertverfalltag wird nichts abgezogen") {
            applyInflationOnAppStart()
            assertTrue(inflationRows().isEmpty())
            assertEquals(100.0, balance(), TOLERANCE)
        }

        advanceTo(LocalDateTime(2026, 10, 19, 9, 0))
        step("Montag, erster App-Start: 30 % werden abgezogen") { applyInflationOnAppStart() }
        step("Montag, weitere App-Starts: nichts mehr") {
            repeat(3) { applyInflationOnAppStart() }
        }
        assertEquals("one devaluation row for this week", 1, inflationRows().size)
        assertEquals(-30.0, inflationRows().single().amount, TOLERANCE)

        advanceTo(LocalDateTime(2026, 10, 26, 9, 0))
        step("Nächster Montag: eine neue Zeile, wieder nur eine") {
            repeat(2) { applyInflationOnAppStart() }
        }
        assertEquals("one row per week", 2, inflationRows().size)
        assertEquals(49.0, balance(), TOLERANCE)
    }

    private suspend fun Scenario.applyInflationOnAppStart() {
        container.weekPlanningService.applyInflation()
    }

    private suspend fun Scenario.inflationRows() =
        transactions().filter { it.reason == PointsReason.INFLATION }

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
