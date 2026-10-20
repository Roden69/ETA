package com.example.eta.scenario

import android.app.Application
import com.example.eta.data.repository.RecurringEdit
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.Vacation
import com.example.eta.domain.model.VacationRule
import com.example.eta.domain.model.VacationTreatment
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Area D, Wiederkehrend: the standing schedule as it is laid down, kept, edited, paused and ended. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecurringScenarioTest {

    @Test
    fun `the setup lays the standing schedule down for fourteen days and no further`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }

        step("Vierzehn Tage Schlafvorbereitung, am fünfzehnten Tag noch keine") {
            for (offset in 0..14) {
                assertNotNull("bed preparation on day $offset", blocksOn(clock.today.plus(DatePeriod(days = offset)))
                    .firstOrNull { it.item.role == ItemRole.BED_PREP })
            }
            assertNull(
                "nothing beyond the fourteen days",
                blocksOn(clock.today.plus(DatePeriod(days = 15))).firstOrNull { it.item.role == ItemRole.BED_PREP },
            )
        }
    }

    @Test
    fun `repeated top-ups create no duplicate blocks`() = scenario(start = "2026-10-18T09:00") {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val from = clock.today
        val to = clock.today.plus(DatePeriod(days = 21))
        val before = container.planRepository.findRange(from, to).size

        step("Dreimal nachlegen") {
            val added = container.scheduleMaintenance.topUp()
            assertTrue("the first top-up fills days 15 to 21", added > 0)
            repeat(2) { assertEquals("a top-up over laid blocks adds nothing", 0, container.scheduleMaintenance.topUp()) }
        }
        assertTrue(container.planRepository.findRange(from, to).size > before)
    }

    @Test
    fun `a every-two-weeks rule lands on alternate Saturdays`() = scenario(start = "2026-10-18T09:00") {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val laundry = standingNamed("Wäsche")

        step("Wäsche: am 24. Oktober, am 31. nicht, am 7. November wieder") {
            container.scheduleMaintenance.topUp()
            assertEquals(1, blocksOf(laundry, LocalDate(2026, 10, 24), LocalDate(2026, 10, 24)).size)
            assertTrue(blocksOf(laundry, LocalDate(2026, 10, 31), LocalDate(2026, 10, 31)).isEmpty())
            assertEquals(1, blocksOf(laundry, LocalDate(2026, 11, 7), LocalDate(2026, 11, 7)).size)
        }
    }

    @Test
    fun `a vacation suppresses the standing occurrences, and they come back when it is deleted`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tuesday = liveDefinition("Lernen", DayOfWeek.TUESDAY)
        val vacationFrom = LocalDate(2026, 10, 20)
        val vacationTo = LocalDate(2026, 10, 23)

        step("Urlaub vom 20. bis 23. Oktober") {
            val vacation = Vacation(
                label = "Urlaub",
                from = vacationFrom,
                to = vacationTo,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            )
            val rules = standingDefinitions().map {
                VacationRule(vacationId = vacation.id, itemId = it.id, treatment = VacationTreatment.SUSPEND)
            }
            container.vacationRepository.save(vacation, rules)
        }
        step("Im Urlaub keine Serienvorkommen") {
            assertTrue(blocksOf(tuesday, vacationFrom, vacationTo).isEmpty())
            assertTrue(blocksOn(vacationFrom).none { it.block.origin == BlockOrigin.RECURRING })
        }

        step("Urlaub gelöscht: die Vorkommen kommen zurück") {
            val plan = container.vacationRepository.plansFrom(clock.today).single()
            container.vacationRepository.delete(plan)
            container.scheduleMaintenance.topUp()
            assertEquals(1, blocksOf(tuesday, vacationFrom, vacationFrom).size)
        }
    }

    @Test
    fun `a series with an end date lays nothing down after it`() = scenario(start = "2026-10-18T09:00") {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tuesday = liveDefinition("Lernen", DayOfWeek.TUESDAY)

        step("Lernen am Dienstag endet mit dem 27. Oktober") {
            container.recurringTaskService.saveGroup(
                listOf(tuesday.id),
                RecurringEdit(
                    name = "Lernen",
                    note = null,
                    category = Category.FOKUS,
                    weekdays = setOf(DayOfWeek.TUESDAY),
                    startTime = LocalTime(19, 30),
                    duration = 1.hours,
                    travelBefore = null,
                    returnAfter = null,
                    breakAfter = null,
                    endSound = false,
                    extras = ItemExtras(repeatUntil = LocalDate(2026, 10, 27)),
                ),
            )
            container.scheduleMaintenance.topUp()
        }
        step("Der 27. gehört noch dazu, der 3. November nicht mehr") {
            assertEquals(1, blocksOf(tuesday, LocalDate(2026, 10, 27), LocalDate(2026, 10, 27)).size)
            assertTrue(blocksOf(tuesday, LocalDate(2026, 11, 3), LocalDate(2026, 11, 3)).isEmpty())
        }
    }

    @Test
    fun `a deleted future block comes back on the next top-up, while a called-off one stays off`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tuesday = liveDefinition("Lernen", DayOfWeek.TUESDAY)
        val nextTuesday = LocalDate(2026, 10, 20)
        val tuesdayAfter = LocalDate(2026, 10, 27)

        step("Den Dienstag vom Tag nehmen: beim Nachlegen wieder da") {
            container.planRepository.removeBlock(blockOf(tuesday, nextTuesday))
            container.scheduleMaintenance.topUp()
            assertEquals(1, blocksOf(tuesday, nextTuesday, nextTuesday).size)
        }

        step("Den Dienstag abgesagt: beim Nachlegen bleibt er aus") {
            container.planRepository.discard(blockOf(tuesday, tuesdayAfter))
            container.scheduleMaintenance.topUp()
            assertTrue(
                "a called-off occurrence is not laid down again",
                blocksOf(tuesday, tuesdayAfter, tuesdayAfter).none { it.block.isOpen },
            )
        }
    }

    @Test
    fun `editing a standing task re-lays today's unstarted block and every later day`() = scenario(
        start = "2026-10-20T18:00",
    ) {
        step("Einrichtung am Dienstag um 18 Uhr") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tuesday = liveDefinition("Lernen", DayOfWeek.TUESDAY)

        step("Lernen am Dienstag auf 20:30 verschieben") {
            container.recurringTaskService.reschedule(
                listOf(tuesday.id),
                setOf(DayOfWeek.TUESDAY),
                LocalTime(20, 30),
                1.hours,
            )
        }
        step("Heute: der noch nicht begonnene Block ist neu gelegt") {
            assertEquals(LocalTime(20, 30), blockOf(tuesday, clock.today).start)
        }
        step("Der nächste Dienstag folgt") {
            assertEquals(LocalTime(20, 30), blockOf(tuesday, LocalDate(2026, 10, 27)).start)
        }
    }

    @Test
    fun `editing a standing task leaves today's block alone once it has begun`() = scenario(
        start = "2026-10-20T19:45",
    ) {
        step("Einrichtung am Dienstag, Lernen läuft seit 19:30") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tuesday = liveDefinition("Lernen", DayOfWeek.TUESDAY)

        step("Lernen am Dienstag auf 20:30 verschieben") {
            container.recurringTaskService.reschedule(
                listOf(tuesday.id),
                setOf(DayOfWeek.TUESDAY),
                LocalTime(20, 30),
                1.hours,
            )
        }
        step("Heute bleibt der begonnene Block auf 19:30") {
            assertEquals(LocalTime(19, 30), blockOf(tuesday, clock.today).start)
        }
        step("Der nächste Dienstag folgt") {
            assertEquals(LocalTime(20, 30), blockOf(tuesday, LocalDate(2026, 10, 27)).start)
        }
    }

    @Test
    fun `editing a standing task keeps a block moved by hand today`() = scenario(start = "2026-10-20T18:00") {
        step("Einrichtung am Dienstag um 18 Uhr") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tuesday = liveDefinition("Lernen", DayOfWeek.TUESDAY)
        step("Heutigen Block von Hand auf 21 Uhr verschieben") {
            val block = blockOf(tuesday, clock.today)
            container.planRepository.addBlock(
                block.copy(flexible = true, start = LocalTime(21, 0), originalStart = block.start),
            )
        }

        step("Lernen am Dienstag auf 20:30 verschieben") {
            container.recurringTaskService.reschedule(
                listOf(tuesday.id),
                setOf(DayOfWeek.TUESDAY),
                LocalTime(20, 30),
                1.hours,
            )
        }
        step("Der von Hand verschobene Block bleibt auf 21 Uhr") {
            assertEquals(LocalTime(21, 0), blockOf(tuesday, clock.today).start)
        }
    }

    @Test
    fun `a confirmed tomorrow keeps its standing occurrences when the task changes`() = scenario(
        start = "2026-10-19T20:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val tuesday = liveDefinition("Lernen", DayOfWeek.TUESDAY)
        val confirmedTuesday = LocalDate(2026, 10, 20)

        step("Morgen bestätigen") { planDay(com.example.eta.ui.planner.PlannerDay.TOMORROW) { confirm() } }

        step("Lernen am Dienstag auf 20:30 verschieben") {
            container.recurringTaskService.reschedule(
                listOf(tuesday.id),
                setOf(DayOfWeek.TUESDAY),
                LocalTime(20, 30),
                1.hours,
            )
        }
        step("Der bestätigte Dienstag bleibt auf 19:30") {
            assertEquals(LocalTime(19, 30), blockOf(tuesday, confirmedTuesday).start)
        }
        step("Der übernächste Dienstag folgt") {
            assertEquals(LocalTime(20, 30), blockOf(tuesday, LocalDate(2026, 10, 27)).start)
        }
    }

    private suspend fun Scenario.standingNamed(name: String): Item =
        standingDefinitions().single { it.name == name && it.completedAt == null }

}
