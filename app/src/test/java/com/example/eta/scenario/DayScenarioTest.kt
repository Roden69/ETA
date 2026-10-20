package com.example.eta.scenario

import android.app.Application
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PlannerWarning
import com.example.eta.ui.planner.PlannerDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Area C, Tag: the revolver, day placement, confirmation and break warnings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DayScenarioTest {

    @Test
    fun `the revolver offers only the highest priority tier, and a skip works only when the settings allow it`() =
        scenario(start = "2026-10-18T18:00") {
            step("Einrichtung, Sammelliste, drei Prioritäten in der Woche") {
                completeSetup(SampleData.lenaSetup(clock.now()))
                seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
                weekPlanner {
                    pull(itemNamed("Zahnarzt anrufen"))
                    pull(itemNamed("Steuerunterlagen sortieren"))
                    pull(itemNamed("Fahrrad zur Werkstatt bringen"))
                }
            }
            at(LocalTime(20, 0))

            val vm = plannerVm(PlannerDay.TOMORROW)
            step("Morgen: nur die höchste Stufe, die andere wartet") {
                subscribed(vm.uiState) {
                    val state = awaitState(vm.uiState, "the revolver offers the top tier") { it.revolver.isNotEmpty() }
                    assertEquals(Priority.URGENT_MUST, state.revolverPriority)
                    assertEquals(listOf("Zahnarzt anrufen"), state.revolver.map { it.name })
                    assertEquals("two lower tiers wait behind it", 2, state.lockedPriorities)
                    assertFalse("the skip is not offered while the settings forbid it", state.canSkipPriority)
                }
            }

            step("Überspringen ohne Erlaubnis: die Stufe bleibt") {
                subscribed(vm.uiState) {
                    vm.skipPriority()
                    // Wait for a later settings emission, not the unchanged initial tier.
                    changeSettings { it.copy(allowTodoBesideWeek = true) }
                    val state = awaitState(vm.uiState, "the planner recomputed after the skip") {
                        it.setup?.allowTodoBesideWeek == true
                    }
                    assertEquals(Priority.URGENT_MUST, state.revolverPriority)
                    assertFalse(state.prioritySkipped)
                }
            }

            step("Erlaubt in den Einstellungen: Überspringen geht") {
                changeSettings { it.copy(allowPrioritySkip = true) }
                subscribed(vm.uiState) {
                    awaitState(vm.uiState, "the skip offered once allowed") { it.canSkipPriority }
                    vm.resetPriority()
                    vm.skipPriority()
                    val state = awaitState(vm.uiState, "the next tier offered after the skip") {
                        it.revolverPriority == Priority.MUST
                    }
                    assertEquals(listOf("Steuerunterlagen sortieren"), state.revolver.map { it.name })
                }
            }
        }

    @Test
    fun `placing a card onto occupied time asks first, and placing anyway writes it at that time`() =
        scenario(start = "2026-10-18T18:00") {
            step("Einrichtung und Zahnarzt in der Woche") {
                completeSetup(SampleData.lenaSetup(clock.now()))
                seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
                weekPlanner { pull(itemNamed("Zahnarzt anrufen")) }
            }
            at(LocalTime(20, 0))

            val dentist = itemNamed("Zahnarzt anrufen")
            val vm = plannerVm(PlannerDay.TOMORROW)
            step("20:30 morgen liegt in der Freizeit: Rückfrage statt Eintrag") {
                subscribed(vm.uiState, vm.overlapPrompt) {
                    awaitState(vm.uiState, "the dentist offered in the revolver") { state ->
                        state.revolver.any { it.id == dentist.id }
                    }
                    vm.place(dentist, 20 * 60 + 30)
                    awaitState(vm.overlapPrompt, "the overlap question") { it?.pending?.name == dentist.name }
                    assertTrue(vm.uiState.value.blocks.none { it.item.id == dentist.id })
                    vm.dismissOverlap()
                    awaitState(vm.overlapPrompt, "the question closed") { it == null }
                }
            }

            step("Dennoch setzen: die Karte steht um 20:30") {
                subscribed(vm.uiState, vm.overlapPrompt) {
                    vm.place(dentist, 20 * 60 + 30)
                    awaitState(vm.overlapPrompt, "the overlap question again") { it != null }
                    vm.placeAnyway()
                    val state = awaitState(vm.uiState, "the dentist placed at 20:30") { state ->
                        state.blocks.any { it.item.id == dentist.id }
                    }
                    assertEquals(LocalTime(20, 30), state.blocks.first { it.item.id == dentist.id }.block.start)
                }
            }
        }

    @Test
    fun `a card can be sent back to the week list, or to the Sammelliste, from the day`() = scenario(
        start = "2026-10-18T18:00",
    ) {
        step("Einrichtung und Zahnarzt in der Woche") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
            weekPlanner { pull(itemNamed("Zahnarzt anrufen")) }
        }
        at(LocalTime(20, 0))
        val dentist = itemNamed("Zahnarzt anrufen")
        val vm = plannerVm(PlannerDay.TOMORROW)

        step("Morgen eingeplant, dann zurück in die Woche") {
            planDay(PlannerDay.TOMORROW) { place(dentist, LocalTime(10, 0)) }
            subscribed(vm.uiState) {
                val entry = awaitState(vm.uiState, "the dentist on tomorrow's day") { state ->
                    state.blocks.any { it.item.id == dentist.id }
                }.blocks.first { it.item.id == dentist.id }
                vm.returnToWeek(entry.block.id)
            }
            awaitStage(dentist, Stage.WEEK)
        }

        step("Wieder eingeplant, dann ganz in die Sammelliste") {
            planDay(PlannerDay.TOMORROW) { place(dentist, LocalTime(10, 0)) }
            subscribed(vm.uiState) {
                val entry = awaitState(vm.uiState, "the dentist on tomorrow's day again") { state ->
                    state.blocks.any { it.item.id == dentist.id }
                }.blocks.first { it.item.id == dentist.id }
                vm.cancel(entry)
            }
            awaitStage(dentist, Stage.COLLECTION)
        }
    }

    @Test
    fun `confirming tomorrow makes the list for tomorrow, and reopening it brings the revolver back`() = scenario(
        start = "2026-10-18T20:00",
    ) {
        step("Einrichtung und Zahnarzt in der Woche") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
            weekPlanner { pull(itemNamed("Zahnarzt anrufen")) }
        }
        val dentist = itemNamed("Zahnarzt anrufen")
        val vm = plannerVm(PlannerDay.TOMORROW)

        step("Morgen planen und bestätigen") {
            planDay(PlannerDay.TOMORROW) {
                place(dentist, LocalTime(10, 0))
                confirm()
            }
            subscribed(vm.uiState) {
                awaitState(vm.uiState, "tomorrow confirmed") { it.isConfirmed }
            }
            val lists = smartListsVm()
            subscribed(lists.uiState) {
                val state = awaitState(lists.uiState, "the confirmed list for tomorrow") {
                    it.tomorrowConfirmed && it.tomorrowBlocks.any { entry -> entry.item.id == dentist.id }
                }
                assertEquals(LocalDate(2026, 10, 19), state.tomorrow)
            }
        }

        step("Lange gedrückt: der Plan öffnet sich wieder") {
            subscribed(vm.uiState) {
                vm.reopen()
                awaitState(vm.uiState, "tomorrow open again") { !it.isConfirmed }
            }
        }
    }

    @Test
    fun `replanning today opens today, and today is never locked by a confirmation`() = scenario(
        start = "2026-10-18T10:00",
    ) {
        step("Einrichtung und Zahnarzt in der Woche") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
            weekPlanner { pull(itemNamed("Zahnarzt anrufen")) }
            container.planRepository.confirmDayPlan(clock.today)
            assertTrue(container.planRepository.findDayPlan(clock.today)?.isConfirmed == true)
        }
        val dentist = itemNamed("Zahnarzt anrufen")

        step("Heute umplanen: die Karte auf den heutigen Tag") {
            planDay(PlannerDay.TODAY) { place(dentist, LocalTime(15, 0)) }
        }
        step("Heute umplanen: offen, nicht gesperrt") {
            val vm = plannerVm(PlannerDay.TODAY)
            subscribed(vm.uiState) {
                val state = awaitState(vm.uiState, "today's planner loaded") { it.isToday }
                assertEquals(clock.today, state.date)
                assertFalse(state.isConfirmed)
                assertTrue(state.blocks.any { it.item.id == dentist.id })
            }
        }
        assertEquals(BlockOrigin.DRAGGED, blockOf(dentist, clock.today).origin)
    }

    @Test
    fun `a run of tasks longer than the settings allow is flagged, and the warning can be waved away for the day`() =
        scenario(start = "2026-10-18T18:00") {
            step("Einrichtung und zwei Karten in der Woche") {
                completeSetup(SampleData.lenaSetup(clock.now()))
                seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
                weekPlanner {
                    pull(itemNamed("Präsentation für Montag"))
                    pull(itemNamed("Steuerunterlagen sortieren"))
                }
            }
            at(LocalTime(20, 0))
            val presentation = itemNamed("Präsentation für Montag")
            val taxes = itemNamed("Steuerunterlagen sortieren")
            step("Morgen: 08:00 bis 12:30 ohne eine einzige Pause") {
                planDay(PlannerDay.TOMORROW) {
                    place(presentation, LocalTime(8, 0))
                    place(taxes, LocalTime(11, 0))
                }
            }

            val vm = plannerVm(PlannerDay.TOMORROW)
            step("Pausenchecker: die Strecke ist länger als erlaubt") {
                subscribed(vm.uiState) {
                    val state = awaitState(vm.uiState, "the break checker reports the day") { it.breaks != null }
                    val stretches = state.breaks!!.longStretches
                    assertEquals("one run of tasks without rest", 1, stretches.size)
                    assertEquals(LocalTime(8, 0).hour * 60, stretches.single().fromMinute)
                }
            }
            step("Hinweis für den Tag weggeklickt") {
                subscribed(vm.uiState) {
                    awaitState(vm.uiState, "the warning shown") { it.breaks != null }
                    vm.dismissWarning(PlannerWarning.BREAK_STRETCH)
                    awaitState(vm.uiState, "the warning dismissed for the day") {
                        PlannerWarning.BREAK_STRETCH in it.dismissedWarnings
                    }
                }
            }
        }

    @Test
    fun `at midnight tomorrow moves on, and the day planned yesterday stays where it was`() = scenario(
        start = "2026-10-18T20:00",
    ) {
        step("Einrichtung und Zahnarzt in der Woche") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), clock.today, zone))
            weekPlanner { pull(itemNamed("Zahnarzt anrufen")) }
        }
        val dentist = itemNamed("Zahnarzt anrufen")
        val monday = LocalDate(2026, 10, 19)
        assertEquals(monday, plannerVm(PlannerDay.TOMORROW).date)

        step("Sonntag Abend: Montag planen") {
            planDay(PlannerDay.TOMORROW) { place(dentist, LocalTime(10, 0)) }
        }

        nextDay(LocalTime(7, 0))
        step("Montag früh: morgen ist Dienstag") {
            assertEquals(LocalDate(2026, 10, 20), plannerVm(PlannerDay.TOMORROW).date)
            assertEquals(LocalTime(10, 0), blockOf(dentist, monday).start)
        }
    }

}
