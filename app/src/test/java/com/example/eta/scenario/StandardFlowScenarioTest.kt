package com.example.eta.scenario

import android.app.Application
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.Priority
import com.example.eta.ui.planner.PlannerDay
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
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
 * The happy path over three days on the real wiring: Lena sets up the app, adds a
 * card, concretizes it, pulls it into the week, plans tomorrow in the evening,
 * ticks it off the next day and settles that evening. The points arrive once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StandardFlowScenarioTest {
    @Test
    fun `quick add to settled evening credits the harvest once and lists it as done`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        val monday = LocalDate(2026, 10, 19)

        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val added = step("Schnellerfassung") { quickAdd("Steuererklärung") }
        val card = step("Konkretisieren") {
            concretize(added, Category.FOKUS, Priority.MUST, duration = 1.hours)
        }
        step("Wochenplanung: ziehen") {
            weekPlanner { pull(card) }
        }

        at(LocalTime(20, 0))
        step("Sonntag Abend: Montag planen") {
            planDay(PlannerDay.TOMORROW) {
                place(card, LocalTime(10, 0))
                confirm()
            }
        }
        assertEquals(LocalTime(10, 0), blockOf(card, monday).start)

        nextDay(LocalTime(11, 30))
        step("Montag: abhaken") { tick(card) }

        at(LocalTime(20, 0))
        val rowsBefore = transactions().size
        val balanceBefore = balance()
        val settlement = step("Montag Abend: abrechnen") { eveningChain { settle() } }
        assertNotNull("the evening settles the day once", settlement)

        // The settlement may also book charges for unplanned hours; the balance moves
        // by exactly the rows it wrote.
        val written = transactions().take(transactions().size - rowsBefore)
        val harvests = harvestOf(monday)
        assertEquals("exactly one harvest booked for the settled day", 1, harvests.size)
        assertTrue("the completed hour earns points", harvests.single().amount > 0.0)
        assertTrue("the settlement wrote the harvest", written.contains(harvests.single()))
        assertEquals(balanceBefore + written.sumOf { it.amount }, balance(), TOLERANCE)

        step("Montag Abend nochmals: keine zweite Gutschrift") {
            assertNull("a second settlement books nothing", eveningChain { settle() })
        }
        assertEquals(1, harvestOf(monday).size)

        step("Erfolgsliste") { awaitErfolgsliste("Steuererklärung") }
    }

    private suspend fun Scenario.harvestOf(date: LocalDate) =
        transactions().filter { it.reason == PointsReason.HARVEST && it.note == date.toString() }

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
