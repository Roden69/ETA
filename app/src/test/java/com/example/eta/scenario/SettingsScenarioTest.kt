package com.example.eta.scenario

import android.app.Application
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.PointsReason
import com.example.eta.ui.planner.PlannerDay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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

/** Area E, Einstellungen bis Plan: what a change on the settings tab does to the days already laid down. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsScenarioTest {

    @Test
    fun `changing the wake time before the morning starts re-lays the morning of today`() = scenario(
        start = "2026-10-19T05:00",
    ) {
        step("Einrichtung vor dem Aufstehen") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val previous = roleBlockOn(ItemRole.MORNING, clock.today).block
        assertEquals(LocalTime(7, 0), previous.start)

        step("Aufstehen vor Beginn der Morgenroutine auf 08:00 ändern") {
            changeSettings { it.copy(wakeTime = LocalTime(8, 0)) }
        }
        step("Heute folgt die noch nicht begonnene Morgenroutine der neuen Zeit") {
            val morning = roleBlockOn(ItemRole.MORNING, clock.today).block
            assertEquals(LocalTime(8, 0), morning.start)
            assertEquals(previous.plannedDuration, morning.plannedDuration)
        }
    }

    @Test
    fun `a new wake time reaches today's open blocks and later days, but not a started block or a confirmed tomorrow`() =
        scenario(start = "2026-10-19T20:00") {
            step("Einrichtung, morgen bestätigt") {
                completeSetup(SampleData.lenaSetup(clock.now()))
                planDay(PlannerDay.TOMORROW) { confirm() }
            }
            val monday = LocalDate(2026, 10, 19)
            val tuesday = LocalDate(2026, 10, 20)
            val wednesday = LocalDate(2026, 10, 21)

            step("Schlafvorbereitung auf 21:30, Aufstehen auf 06:30") {
                changeSettings { it.copy(bedPrepTime = LocalTime(21, 30), wakeTime = LocalTime(6, 30)) }
            }

            step("Heute, noch offen: die Schlafvorbereitung ist neu gelegt") {
                assertEquals(LocalTime(21, 30), roleBlockOn(ItemRole.BED_PREP, monday).block.start)
            }
            step("Heute, schon begonnen: das Aufstehen bleibt bei 07:00") {
                assertEquals(LocalTime(7, 0), roleBlockOn(ItemRole.MORNING, monday).block.start)
            }
            step("Morgen, bestätigt: alles bleibt, wie es geplant war") {
                assertEquals(LocalTime(22, 0), roleBlockOn(ItemRole.BED_PREP, tuesday).block.start)
                assertEquals(LocalTime(7, 0), roleBlockOn(ItemRole.MORNING, tuesday).block.start)
            }
            step("Übermorgen: ganz nach den neuen Zeiten") {
                assertEquals(LocalTime(21, 30), roleBlockOn(ItemRole.BED_PREP, wednesday).block.start)
                assertEquals(LocalTime(6, 30), roleBlockOn(ItemRole.MORNING, wednesday).block.start)
            }
        }

    @Test
    fun `a bed time change does not move a bed preparation that has already begun`() = scenario(
        start = "2026-10-19T22:30",
    ) {
        step("Einrichtung, es ist 22:30: die Schlafvorbereitung läuft seit 22:00") {
            completeSetup(SampleData.lenaSetup(clock.now()))
        }
        step("Schlafvorbereitung auf 21:30") {
            changeSettings { it.copy(bedPrepTime = LocalTime(21, 30)) }
        }
        step("Heute bleibt bei 22:00") {
            assertEquals(LocalTime(22, 0), roleBlockOn(ItemRole.BED_PREP, clock.today).block.start)
        }
        step("Morgen folgt der neuen Zeit") {
            assertEquals(LocalTime(21, 30), roleBlockOn(ItemRole.BED_PREP, LocalDate(2026, 10, 20)).block.start)
        }
    }

    @Test
    fun `daily free time set to zero retires its item and blocks, and set to an hour brings them back`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung mit Freizeit") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tomorrow = LocalDate(2026, 10, 19)

        step("Freizeit auf null") {
            changeSettings { it.copy(freeTime = it.freeTime.copy(duration = Duration.ZERO)) }
        }
        step("Die Freizeit-Karte ist abgeschlossen, und kein Block bleibt") {
            assertNotNull(freeTimeItem().completedAt)
            assertTrue(blocksOn(tomorrow).none { it.item.role == ItemRole.FREE_TIME })
        }

        step("Freizeit auf eine Stunde") {
            changeSettings { it.copy(freeTime = it.freeTime.copy(duration = 60.minutes)) }
        }
        step("Die Freizeit-Karte ist wieder aktiv, mit einem Block je Tag") {
            assertNull(freeTimeItem().completedAt)
            assertEquals(60.minutes, roleBlockOn(ItemRole.FREE_TIME, tomorrow).block.plannedDuration)
        }

        step("Freizeit wieder auf null") {
            changeSettings { it.copy(freeTime = it.freeTime.copy(duration = Duration.ZERO)) }
        }
        step("Wieder abgeschlossen, keine Blöcke mehr") {
            assertNotNull(freeTimeItem().completedAt)
            assertTrue(blocksOn(tomorrow).none { it.item.role == ItemRole.FREE_TIME })
        }
    }

    @Test
    fun `the default break applies to a ToDo placed after it is set, and not to one already on the day`() = scenario(
        start = "2026-10-19T08:00",
    ) {
        step("Einrichtung und Karten in der Woche") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            seedCollection(SampleData.lenaCollection(clock.now(), zone))
            weekPlanner {
                pull(itemNamed("Balkon aufräumen"))
                pull(itemNamed("Linsensuppe ausprobieren"))
            }
        }
        val balcony = itemNamed("Balkon aufräumen")
        val soup = itemNamed("Linsensuppe ausprobieren")

        step("Standardpause 15 Minuten, Balkon auf den Tag") {
            changeSettings { it.copy(defaultBreakAfter = 15.minutes) }
            planDay(PlannerDay.TODAY) { place(balcony, LocalTime(9, 0)) }
        }
        step("Standardpause 30 Minuten, Linsensuppe auf den Tag") {
            changeSettings { it.copy(defaultBreakAfter = 30.minutes) }
            planDay(PlannerDay.TODAY) { place(soup, LocalTime(10, 30)) }
        }

        step("Der Balkon behält seine 15 Minuten, die Suppe bekommt 30") {
            assertEquals(15.minutes, blockOf(balcony, clock.today).breakAfter)
            assertEquals(30.minutes, blockOf(soup, clock.today).breakAfter)
        }
    }

    @Test
    fun `switching a feature off and back on keeps its data`() = scenario(start = "2026-10-18T09:00") {
        step("Einrichtung und ein Startguthaben") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            container.pointsRepository.record(40.0, PointsReason.CONTRACT, "Startguthaben")
        }
        val before = transactions()

        step("Punktesystem aus") { setFeature { it.copy(pointsSystem = false) } }
        step("Punktesystem wieder an") { setFeature { it.copy(pointsSystem = true) } }

        assertEquals("the ledger is untouched", before, transactions())
        assertEquals(40.0, balance(), TOLERANCE)
        assertTrue("the feature is switched back on", storedSetup().pointsSystem)
    }

    private suspend fun Scenario.freeTimeItem() =
        everyItem().single { it.role == ItemRole.FREE_TIME }

    private suspend fun Scenario.setFeature(transform: (com.example.eta.domain.setup.UserSetup) -> com.example.eta.domain.setup.UserSetup) {
        val vm = settingsVm()
        subscribed(vm.draft) {
            awaitState(vm.draft, "the settings draft loaded") { it != null }
            vm.setFeature(transform)
        }
        awaitState(container.setupRepository.observe(), "the feature switch stored") { stored ->
            stored != null && transform(stored) == stored
        }
    }


    private companion object {
        const val TOLERANCE = 0.0001
    }
}
