package com.example.eta.scenario

import android.app.Application
import com.example.eta.data.repository.CatchUpResult
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.MAKE_UP_NAME_PREFIX
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.PointsTransaction
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.reevaluation.FREE_UNPLANNED_HOURS
import com.example.eta.domain.reevaluation.FREE_TIME_FORGONE_PER_HOUR
import com.example.eta.domain.streak.CATCH_UP_PHRASE
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.dashboard.DashboardViewModel
import com.example.eta.ui.root.dashboardViewModelFactory
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The evening, the settlement and the catch-up, one behaviour per test, on the
 * real wiring. Points come only from the evening; the rest of the books are the
 * penalties the setup configures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EveningScenarioTest {

    @Test
    fun `ticking a block books no points, only the evening settlement does`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        val monday = LocalDate(2026, 10, 19)
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val card = step("Karte konkretisieren und ziehen") {
            val added = quickAdd("Steuererklärung")
            val concretized = concretize(added, Category.FOKUS, Priority.MUST, 1.hours)
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
        val balanceBeforeTick = balance()
        step("Montag: abhaken") { tick(card) }
        assertEquals("a tick books nothing", balanceBeforeTick, balance(), TOLERANCE)
        assertTrue("no harvest before the evening", harvestOn(monday).isEmpty())

        at(LocalTime(20, 0))
        step("Montag Abend: abrechnen") { evening { settle() } }
        assertEquals("the evening books the harvest", 1, harvestOn(monday).size)
    }

    @Test
    fun `a planned ToDo dropped in the evening returns to the Sammelliste and keeps its waiting time`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val card = step("Karte konkretisieren und ziehen") {
            val concretized = concretize(quickAdd("Fenster putzen"), Category.NEBENBEI, Priority.WANT, 45.minutes)
            weekPlanner { pull(concretized) }
            concretized
        }
        val waitingSince = checkNotNull(container.itemRepository.findById(card.id)).enteredCollectionAt

        at(LocalTime(20, 0))
        step("Sonntag Abend: Montag planen") {
            planDay(PlannerDay.TOMORROW) {
                place(card, LocalTime(10, 0))
                confirm()
            }
        }

        nextDay(LocalTime(21, 0))
        step("Montag Abend: verwerfen") { evening { discard(card) } }

        val returned = checkNotNull(container.itemRepository.findById(card.id))
        assertEquals(Stage.COLLECTION, returned.stage)
        assertNotNull("the card entered the Sammelliste with a time", waitingSince)
        assertEquals("the waiting time survives a round trip through the plan", waitingSince, returned.enteredCollectionAt)
    }

    @Test
    fun `a dropped standing task offers one make-up ToDo, and asking again returns the same card`() = scenario(
        start = "2026-10-20T09:00",
    ) {
        val tuesday = LocalDate(2026, 10, 20)
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val lernen = step("Dienstag: Lernen ist eingeplant") {
            blocksOn(tuesday).first { it.item.name == "Lernen" }.item
        }

        at(LocalTime(21, 0))
        val made = step("Dienstag Abend: Lernen verworfen, Nachholen angeboten") {
            evening {
                discard(lernen)
                makeUp(lernen)
            }
        }
        assertEquals(MAKE_UP_NAME_PREFIX + "Lernen", made.name)

        val again = container.dayClosingService.makeUp(blocksOn(tuesday).first { it.item.id == lernen.id })
        assertEquals("the same make-up card comes back", made.id, again.id)
        assertEquals(1, items(made.stage).count { it.name == made.name })
    }

    @Test
    fun `yesterday's unsettled evening is caught up on its own screen, without asking the contracts`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        val monday = LocalDate(2026, 10, 19)
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val card = step("Karte konkretisieren und ziehen") {
            val concretized = concretize(quickAdd("Steuerunterlagen"), Category.FOKUS, Priority.MUST, 1.hours)
            weekPlanner { pull(concretized) }
            concretized
        }
        val contract = step("Sonntag: Vertrag unterschreiben") {
            signContract(slot = 1, title = "Kein Zucker", endsOn = LocalDate(2026, 11, 30))
        }
        at(LocalTime(20, 0))
        step("Sonntag Abend: Montag planen") {
            planDay(PlannerDay.TOMORROW) {
                place(card, LocalTime(10, 0))
                confirm()
            }
        }

        nextDay(LocalTime(11, 30))
        step("Montag: abhaken, Abend vergessen") { tick(card) }

        advanceTo(LocalDateTime(2026, 10, 20, 8, 0))
        step("Dienstag früh: Montag nachholen") {
            assertTrue("yesterday is offered for catch-up", container.catchUpService.dailyDays().contains(monday))
            evening(monday) {
                assertTrue("a caught-up evening asks no contract", contractsAsked().isEmpty())
                assertNotNull("the caught-up evening settles", settle())
            }
        }

        assertEquals("the ticked hour is paid on catch-up", 1, harvestOn(monday).size)
        assertTrue("no contract is paid on catch-up", transactions().none { it.reason == PointsReason.CONTRACT })
        assertNull(
            "the contract is not checked on the caught-up day",
            container.contractRepository.findRunning().single { it.id == contract.id }.lastCheckedOn,
        )
    }

    @Test
    fun `an evening older than six days is caught up only with the phrase, and only once`() = scenario(
        start = "2026-10-05T09:00",
    ) {
        val monday = LocalDate(2026, 10, 5)
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val card = step("Karte konkretisieren und ziehen") {
            val concretized = concretize(quickAdd("Brief schreiben"), Category.FOKUS, Priority.MUST, 1.hours)
            weekPlanner { pull(concretized) }
            concretized
        }
        step("Montag: heute planen") {
            planDay(PlannerDay.TODAY) {
                place(card, LocalTime(10, 0))
                confirm()
            }
        }
        at(LocalTime(11, 30))
        step("Montag: abhaken, Abend vergessen") { tick(card) }

        advanceTo(LocalDateTime(2026, 10, 20, 8, 0))
        step("Dienstag: der Montag ist älter als sechs Tage") {
            assertTrue("it is offered among the older days", container.catchUpService.openDays().contains(monday))
            assertTrue("it is not offered on the dashboard", !container.catchUpService.dailyDays().contains(monday))
        }

        step("Falsche Losung") {
            val refused = catchUp(monday, "falsche Losung")
            assertTrue("a wrong phrase is refused", refused is CatchUpResult.Refused)
        }
        assertTrue("nothing is booked for a wrong phrase", harvestOn(monday).isEmpty())

        step("Richtige Losung") {
            val resolved = catchUp(monday, CATCH_UP_PHRASE)
            assertTrue("the right phrase settles the day", resolved is CatchUpResult.Resolved)
        }
        assertEquals("the made-up hour is paid once", 1, harvestOn(monday).size)

        step("Zweiter Versuch") {
            val again = catchUp(monday, CATCH_UP_PHRASE)
            assertTrue("a settled day is refused", again is CatchUpResult.Refused)
        }
        assertEquals(1, harvestOn(monday).size)
    }

    @Test
    fun `the streak counts consecutive settled evenings, and a skipped evening ends it`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        repeat(FOUR_EVENINGS) { index ->
            if (index == 0) at(LocalTime(20, 0)) else nextDay(LocalTime(20, 0))
            step("Abend ${index + 1}: abrechnen") { evening { settle() } }
        }

        nextDay(LocalTime(9, 0))
        step("Donnerstag früh: vier Abende in Folge") {
            assertEquals(FOUR_EVENINGS, dashboardStreak())
        }

        nextDay(LocalTime(9, 0))
        step("Freitag früh: Donnerstag ausgelassen") {
            assertEquals("a skipped evening ends the run", 0, dashboardStreak())
        }
    }

    @Test
    fun `a cancelled hour costs the configured rate, and unplanned hours cost it beyond two free hours`() = scenario(
        start = "2026-10-19T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        step("Einstellungen: Abzug je Stunde") {
            val setup = checkNotNull(container.setupRepository.find())
            container.setupRepository.saveSettings(
                setup.copy(cancellationPenaltyPerHour = CANCELLATION_RATE, unplannedPenaltyPerHour = UNPLANNED_RATE),
            )
        }
        val card = step("Karte konkretisieren und planen") {
            val concretized = concretize(quickAdd("Präsentation"), Category.FOKUS, Priority.MUST, 2.hours)
            weekPlanner { pull(concretized) }
            concretized
        }
        step("Montag: heute planen") {
            planDay(PlannerDay.TODAY) {
                place(card, LocalTime(10, 0))
                confirm()
            }
        }

        at(LocalTime(20, 0))
        step("Montag Abend: abgesagt") { evening { discard(card) } }
        val settlement = step("Montag Abend: abrechnen") { checkNotNull(evening { settle() }) }

        assertEquals("the two planned hours were called off", 2.0, settlement.cancelledHours, TOLERANCE)
        assertEquals("called off at the configured rate", 2.0 * CANCELLATION_RATE, settlement.cancellationPenalty, TOLERANCE)
        assertTrue("the day has unplanned hours beyond the free two", settlement.unplannedHours > FREE_UNPLANNED_HOURS)
        assertEquals(
            "unplanned hours cost the configured rate beyond the free two",
            (settlement.unplannedHours - FREE_UNPLANNED_HOURS) * UNPLANNED_RATE,
            settlement.unplannedPenalty,
            TOLERANCE,
        )
        assertEquals(-2.0 * CANCELLATION_RATE, ledgerRow(PointsReason.CANCELLATION).amount, TOLERANCE)
        assertEquals(-settlement.unplannedPenalty, ledgerRow(PointsReason.UNPLANNED_TIME).amount, TOLERANCE)
    }

    @Test
    fun `a given-up free time pays four points an hour, for the free time the day provides`() = scenario(
        start = "2026-10-19T09:00",
    ) {
        val monday = LocalDate(2026, 10, 19)
        step("Einrichtung") {
            completeSetup(SampleData.lenaSetup(clock.now()))
        }
        val freeTime = step("Montag: Freizeit ist eingeplant") {
            blocksOn(monday).first { it.item.role == ItemRole.FREE_TIME }.item
        }
        val freeHours = checkNotNull(container.setupRepository.find()).freeTime.duration.inWholeMinutes / 60.0

        at(LocalTime(20, 0))
        step("Montag Abend: Freizeit verworfen") { evening { discard(freeTime) } }
        val settlement = step("Montag Abend: abrechnen") { checkNotNull(evening { settle() }) }

        assertEquals(freeHours * FREE_TIME_FORGONE_PER_HOUR, settlement.freeTimeForgone, TOLERANCE)
        assertEquals(freeHours * FREE_TIME_FORGONE_PER_HOUR, ledgerRow(PointsReason.FREE_TIME_FORGONE).amount, TOLERANCE)
    }

    private suspend fun Scenario.harvestOn(date: LocalDate): List<PointsTransaction> =
        transactions().filter { it.reason == PointsReason.HARVEST && it.note == date.toString() }

    /** The one ledger row of [reason]; each test books at most one of them. */
    private suspend fun Scenario.ledgerRow(reason: PointsReason): PointsTransaction =
        transactions().single { it.reason == reason }

    /** The streak as the dashboard shows it for today, once the dashboard has loaded. */
    private suspend fun Scenario.dashboardStreak(): Int {
        val vm = viewModel<DashboardViewModel>(dashboardViewModelFactory(container))
        return subscribed(vm.uiState) {
            awaitState(vm.uiState, "the dashboard of ${clock.today} loaded") { it.today == clock.today }.streak.length
        }
    }

    private companion object {
        const val TOLERANCE = 0.0001
        const val FOUR_EVENINGS = 4
        const val CANCELLATION_RATE = 2.0
        const val UNPLANNED_RATE = 3.0
    }
}
