package com.example.eta.scenario

import android.app.Application
import com.example.eta.data.repository.SignResult
import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.BREACH_LOCK
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.ui.planner.PlannerDay
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motivation: the Belohn-o-mat, the self-contracts and the growth tasks, each
 * through the evening that moves them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MotivationScenarioTest {

    @Test
    fun `the Belohn-o-mat fills from the evening's harvest, and a full reward can be redeemed`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Belohn-o-mat: Kinoabend für eine Stunde") {
            container.rewardRepository.create(name = "Kinoabend", cost = ONE_POINT, itemIds = emptyList())
        }
        val card = step("Karte konkretisieren und planen") {
            val concretized = concretize(quickAdd("Steuererklärung"), Category.FOKUS, Priority.MUST, 1.hours)
            weekPlanner { pull(concretized) }
            concretized
        }
        at(LocalTime(20, 0))
        step("Sonntag Abend: Montag planen") {
            planDay(PlannerDay.TOMORROW) {
                place(card, LocalTime(10, 0))
                confirm()
            }
        }

        nextDay(LocalTime(11, 30))
        step("Montag: abhaken") { tick(card) }
        assertEquals("the reward waits for the evening", 0.0, rewardNamed("Kinoabend").progress, TOLERANCE)

        at(LocalTime(20, 0))
        step("Montag Abend: abrechnen") { evening { settle() } }
        assertTrue("the harvest fills the reward", rewardNamed("Kinoabend").isFull)
        assertEquals(ONE_POINT, rewardNamed("Kinoabend").progress, TOLERANCE)

        step("Kinoabend einlösen") { container.rewardRepository.redeem(rewardNamed("Kinoabend").id) }
        assertNotNull("the reward is taken", rewardNamed("Kinoabend").redeemedAt)
    }

    @Test
    fun `a breach served on probation is unpaid, and fourteen kept evenings put the contract back into force`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val contract = step("Sonntag: Vertrag unterschreiben") {
            signContract(slot = 1, title = "Kein Zucker", endsOn = LocalDate(2026, 11, 30))
        }

        at(LocalTime(20, 0))
        step("Sonntag Abend: Bruch, weiter einhalten") {
            evening {
                answer(contract, kept = false, keepServing = true)
                settle()
            }
        }
        step("Sonntag Abend: der Vertrag steht auf Bewährung") {
            assertEquals(ContractState.PROBATION, currentContract(contract).state)
            assertFalse("a probation may not come back yet", restart(contract))
        }
        assertTrue("a probation pays nothing", transactions().none { it.reason == PointsReason.CONTRACT })

        repeat(EVENINGS_BEFORE_REINSTATEMENT) { index ->
            nextDay(LocalTime(20, 0))
            step("Abend ${index + 1} auf Bewährung: gehalten") { evening { settle() } }
        }
        step("13 Abende gehalten: noch nicht wieder in Kraft") {
            assertFalse(restart(contract))
        }

        nextDay(LocalTime(20, 0))
        step("14. Abend in Folge gehalten: wieder in Kraft") {
            evening { settle() }
            assertTrue("fourteen kept evenings in a row reinstate it", restart(contract))
            assertEquals(ContractState.ACTIVE, currentContract(contract).state)
        }
        assertEquals("no contract is paid before the reinstatement", 0, transactions().count { it.reason == PointsReason.CONTRACT })
        nextDay(LocalTime(20, 0))
        step("Der Tag nach der Wiedereinsetzung: bezahlt") {
            evening { settle() }
            assertEquals("a reinstated contract pays again", 1, transactions().count { it.reason == PointsReason.CONTRACT })
        }
    }

    @Test
    fun `a breach without holding it on closes the slot for a month`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val contract = step("Sonntag: Vertrag unterschreiben") {
            signContract(slot = 1, title = "Kein Zucker", endsOn = LocalDate(2026, 11, 30))
        }

        at(LocalTime(20, 0))
        step("Sonntag Abend: Bruch, nicht weiter") {
            evening {
                answer(contract, kept = false, keepServing = false)
                settle()
            }
        }
        step("Sonntag Abend: gebrochen, nicht auf Bewährung") {
            assertEquals(ContractState.BROKEN, currentContract(contract).state)
            assertFalse("a broken contract cannot be reinstated", restart(contract))
            assertFalse("nothing to let go of", abandon(contract))
        }
        step("Der Slot bleibt einen Monat gesperrt") {
            val result = trySignContract(slot = 1, title = "Neuer Vertrag", endsOn = LocalDate(2026, 12, 31))
            assertTrue("the slot is locked for a month", result is SignResult.Refused)
        }
    }

    @Test
    fun `a probation waits for its month, and can then be let go, which frees the slot`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val contract = step("Sonntag: Vertrag unterschreiben") {
            signContract(slot = 1, title = "Kein Zucker", endsOn = LocalDate(2026, 11, 30))
        }
        at(LocalTime(20, 0))
        step("Sonntag Abend: Bruch, auf Bewährung") {
            evening {
                answer(contract, kept = false, keepServing = true)
                settle()
            }
        }

        val monthIsUp = contract.signedOn.plus(BREACH_LOCK)
        step("Vor Ablauf des Monats: keine Entscheidung") {
            assertFalse("the month is not up yet", abandon(contract))
        }

        advanceTo(monthIsUp.atTime(9, 0))
        step("Nach einem Monat: aufkündigen") {
            assertTrue("the month is up, the promise can be let go", abandon(contract))
            assertEquals(ContractState.BROKEN, currentContract(contract).state)
        }
        step("Der Slot ist wieder frei") {
            val result = trySignContract(slot = 1, title = "Neuer Vertrag", endsOn = LocalDate(2026, 12, 31))
            assertTrue("a let-go contract frees its slot", result is SignResult.Signed)
        }
    }

    @Test
    fun `an unanswered contract counts as kept, so the evening pays it`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val contract = step("Sonntag: Vertrag unterschreiben") {
            signContract(slot = 1, title = "Kein Zucker", endsOn = LocalDate(2026, 11, 30))
        }

        at(LocalTime(20, 0))
        step("Sonntag Abend: nichts beantwortet") { evening { settle() } }

        val payout = transactions().filter { it.reason == PointsReason.CONTRACT }
        assertEquals("the unanswered contract is paid", 1, payout.size)
        assertEquals(contract.dailyPayout, payout.single().amount, TOLERANCE)
    }

    @Test
    fun `a growth task grows after the completions its rule asks for, and the later blocks take the new length`() =
        scenario(start = "2026-10-18T09:00") {
            val wednesday = LocalDate(2026, 10, 21)
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            val stretch = step("Wachstumsaufgabe anlegen: 15 Minuten, alle zwei Absolvierungen plus 5") {
                val task: Item = Item.newRecurring(
                    name = "Dehnen",
                    category = Category.ACHTSAM,
                    recurrenceRule = RecurrenceRule.Daily,
                    startTime = LocalTime(7, 30),
                    estimatedDuration = 15.minutes,
                    now = clock.now(),
                ).withGrowth(
                    GrowthSetting(start = 15.minutes, target = 60.minutes, increment = 5.minutes, dynamic = false, every = 2),
                )
                container.itemRepository.add(task)
                container.scheduleMaintenance.topUp()
                task
            }

            nextDay(LocalTime(8, 0))
            step("Montag: Dehnen abhaken") { tick(stretch) }
            at(LocalTime(20, 0))
            step("Montag Abend: abrechnen") { evening { settle() } }
            assertEquals("the first completion does not grow it yet", 15.minutes, definitionOf(stretch).estimatedDuration)

            nextDay(LocalTime(8, 0))
            step("Dienstag: Dehnen abhaken") { tick(stretch) }
            at(LocalTime(20, 0))
            step("Dienstag Abend: abrechnen") { evening { settle() } }
            assertEquals("the second completion grows it by its increment", 20.minutes, definitionOf(stretch).estimatedDuration)
            assertEquals(
                "the block laid down ahead takes the new length",
                20.minutes,
                blocksOn(wednesday).first { it.item.id == stretch.id }.block.plannedDuration,
            )
        }

    private suspend fun Scenario.rewardNamed(name: String) =
        container.rewardRepository.observe().let { flow ->
            awaitState(flow, "the reward \"$name\"") { rewards -> rewards.any { it.reward.name == name } }
        }.single { it.reward.name == name }.reward

    private suspend fun Scenario.currentContract(contract: Contract): Contract =
        container.contractRepository.observeAll().let { flow ->
            awaitState(flow, "the contract \"${contract.title}\"") { list -> list.any { it.id == contract.id } }
        }.single { it.id == contract.id }

    private suspend fun Scenario.restart(contract: Contract): Boolean =
        container.contractRepository.restartProbation(currentContract(contract))

    private suspend fun Scenario.abandon(contract: Contract): Boolean =
        container.contractRepository.abandonProbation(currentContract(contract))

    private suspend fun Scenario.definitionOf(item: Item): Item =
        checkNotNull(container.itemRepository.findById(item.id)) { "\"${item.name}\" vanished" }

    private companion object {
        const val TOLERANCE = 0.0001
        const val ONE_POINT = 1.0
        const val EVENINGS_BEFORE_REINSTATEMENT = 13
    }
}
