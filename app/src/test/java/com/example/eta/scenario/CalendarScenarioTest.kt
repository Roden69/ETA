package com.example.eta.scenario

import android.app.Application
import com.example.eta.data.repository.AppointmentDetails
import com.example.eta.data.repository.ConflictingBlock
import com.example.eta.data.repository.SeriesProposal
import com.example.eta.data.repository.SyncOutcome
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.MAKE_UP_NAME_PREFIX
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.Stage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Google Calendar as the app reads it: sync, appointments, series and the clashes
 * with the plan. The calendar is the harness's [FakeCalendar], so every answer is
 * fixed by the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalendarScenarioTest {

    private val tuesday = LocalDate(2026, 10, 20)
    private val thursday = LocalDate(2026, 10, 22)
    private val windowEnd = LocalDate(2026, 11, 9)

    @Test
    fun `a second sync of the same calendar adds no appointment twice`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", thursday, LocalTime(10, 0), 1.hours)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            step("Kalender verbinden und zweimal lesen") {
                assertTrue(container.calendarSyncService.connect() is SyncOutcome.Ok)
                container.calendarSyncService.refresh(tuesday, windowEnd)
                container.calendarSyncService.refresh(tuesday, windowEnd)
            }
            assertEquals(
                "one appointment, however often it was read",
                listOf("Zahnarzt"),
                pendingFor().map { it.title },
            )
        }
    }

    @Test
    fun `an appointment taken as a time blocker holds its hour and earns no points`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", thursday, LocalTime(10, 0), 1.hours)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            step("Kalender verbinden und lesen") {
                container.calendarSyncService.connect()
                container.calendarSyncService.refresh(tuesday, windowEnd)
            }
            val withoutBlocker = container.reevaluationService.preview(thursday, emptyList())
            val blocker = step("Donnerstag: als Zeitblocker übernehmen") {
                container.calendarImportService.import(
                    pendingOn(thursday).single(),
                    details(LocalTime(10, 0), 1.hours),
                    BlockOrigin.CALENDAR_BLOCKER,
                )
            }

            advanceTo(LocalDateTime(2026, 10, 22, 9, 0))
            step("Donnerstag: Zeitblocker abhaken") { tick(blocker.item) }

            at(LocalTime(20, 0))
            val settlement = step("Donnerstag Abend: abrechnen") { checkNotNull(evening(thursday) { settle() }) }

            assertEquals("a time blocker earns nothing", 0.0, settlement.harvest, TOLERANCE)
            assertEquals("the blocker holds one hour without earning points", withoutBlocker.unplannedHours - 1.0, settlement.unplannedHours, TOLERANCE)
            assertTrue("no harvest is booked for it", transactions().none { it.reason == PointsReason.HARVEST })
        }
    }

    @Test
    fun `a one-off appointment is a single card, never a series`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", thursday, LocalTime(10, 0), 1.hours)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            step("Kalender verbinden und lesen") {
                container.calendarSyncService.connect()
                container.calendarSyncService.refresh(tuesday, windowEnd)
            }
            val imported = step("Donnerstag: als Termin übernehmen") {
                container.calendarImportService.import(pendingOn(thursday).single(), details(LocalTime(10, 0), 1.hours))
            }
            assertEquals(
                BlockOrigin.CALENDAR_IMPORT,
                blocksOn(thursday).single { it.item.id == imported.item.id }.block.origin,
            )
            assertTrue(
                "a one-off appointment is no series",
                container.calendarSyncService.proposeSeries(pendingFor()).none { it is SeriesProposal.Offer },
            )
        }
    }

    @Test
    fun `a series is offered once, adopted as one standing card with its end, and skips its holiday`() {
        val calendar = FakeCalendar()
        calendar.series(
            title = "Analysis",
            start = LocalDate(2026, 10, 5),
            time = LocalTime(8, 0),
            duration = 90.minutes,
            rule = "FREQ=WEEKLY;BYDAY=MO;UNTIL=20261109T225959Z",
            exdates = listOf(LocalDate(2026, 11, 2)),
        )
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            val offers = step("Kalender lesen: die Serie wird angeboten") {
                container.calendarRefreshService.run()
                container.calendarSyncService.connect()
                container.calendarSyncService.refresh(tuesday, windowEnd)
                container.calendarSyncService.proposeSeries(pendingFor()).filterIsInstance<SeriesProposal.Offer>()
            }
            assertEquals("one offer for the whole series, not one per lecture", 1, offers.size)

            val offer = offers.single()
            step("Serie übernehmen") {
                container.calendarImportService.adoptSeries(offer.event, offer.pattern, details())
                container.scheduleMaintenance.topUpUntil(LocalDate(2026, 11, 30))
            }
            val lectures = container.planRepository.findRange(tuesday, LocalDate(2026, 11, 30))
                .filter { it.item.name == "Analysis" }
            assertEquals(
                "the lectures up to the last one, the holiday skipped",
                setOf(LocalDate(2026, 10, 26), LocalDate(2026, 11, 9)),
                lectures.map { it.block.date }.toSet(),
            )
            assertEquals(windowEnd, lectures.first().item.repeatUntil)

            step("Nächster Abgleich: keine zweite Anfrage") {
                container.calendarSyncService.refresh(tuesday, windowEnd)
                assertTrue(container.calendarSyncService.proposeSeries(pendingFor()).none { it is SeriesProposal.Offer })
            }
        }
    }

    @Test
    fun `a clash with a planned block is shown, and shortening the block makes room`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", tuesday, LocalTime(17, 30), 30.minutes)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            val sport = sportToday()
            val conflict = importDentistAndClash()
            assertEquals("the clash is the Sport session", sport.block.id, conflict.entry.block.id)
            assertNotNull("the session can be shortened in front of the appointment", conflict.shortened)

            step("Sport kürzen") {
                assertTrue(container.calendarImportService.shortenConflicting(conflict.entry, 15.minutes))
            }
            assertEquals(15.minutes, sportToday().block.plannedDuration)
        }
    }

    @Test
    fun `a clash can be split so the front stays and the back becomes a catch-up card`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", tuesday, LocalTime(17, 30), 30.minutes)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            sportToday()
            val conflict = importDentistAndClash()
            assertNotNull("the session has a stretch behind the appointment", conflict.split)

            step("Sport teilen") {
                assertTrue(
                    container.calendarImportService.splitConflicting(conflict.entry, front = 15.minutes, back = 15.minutes),
                )
            }
            assertEquals(15.minutes, sportToday().block.plannedDuration)
            assertTrue(
                "the back is a catch-up card, not a second block",
                Stage.entries.flatMap { items(it) }.any { it.name.startsWith(MAKE_UP_NAME_PREFIX) },
            )
        }
    }

    @Test
    fun `a clash can be answered by calling the session off`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", tuesday, LocalTime(17, 30), 30.minutes)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            sportToday()
            val conflict = importDentistAndClash()

            step("Sport absagen") { container.calendarImportService.cancelConflicting(conflict.entry) }
            assertTrue("the session is called off", sportToday().block.isDiscarded)
        }
    }

    @Test
    fun `a clash can be answered by moving the session after the appointment`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", tuesday, LocalTime(17, 30), 30.minutes)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung mit freiem Platz nach dem Termin") {
                val persona = SampleData.lenaSetup(clock.now())
                completeSetup(persona.copy(setup = persona.setup.copy(meals = com.example.eta.domain.setup.MealPlan.DailyCooking(emptyList()))))
            }
            sportToday()
            val conflict = importDentistAndClash()
            assertNotNull("the session can wait until the appointment is over", conflict.pushed)

            step("Sport danach verschieben") {
                assertTrue(container.calendarImportService.moveConflictingAfter(conflict.entry, LocalTime(18, 0)))
            }
            assertEquals(LocalTime(18, 0), sportToday().block.start)
        }
    }

    @Test
    fun `the background refresh tells about a new clash once`() {
        val calendar = FakeCalendar()
        calendar.event("Zahnarzt", tuesday, LocalTime(17, 30), 30.minutes)
        scenario(start = "2026-10-20T08:00", calendar = calendar) {
            step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
            sportToday()
            step("Kalender verbinden") { container.calendarSyncService.connect() }

            val announced = step("Hintergrundabgleich") { container.calendarRefreshService.run() }
            assertEquals(listOf("Zahnarzt"), announced.map { it.title })
            val again = step("Zweiter Abgleich") { container.calendarRefreshService.run() }
            assertTrue("one clash is one announcement", again.isEmpty())
        }
    }

    private suspend fun Scenario.pendingFor(): List<CalendarEvent> =
        container.calendarRepository.pendingFor(tuesday, windowEnd)

    private suspend fun Scenario.pendingOn(date: LocalDate): List<CalendarEvent> =
        pendingFor().filter { it.date == date }

    /** Reads the calendar, imports the dentist appointment and returns the first clash it made. */
    private suspend fun Scenario.importDentistAndClash(): ConflictingBlock = step("Zahnarzt übernehmen") {
        container.calendarSyncService.connect()
        container.calendarSyncService.refresh(tuesday, windowEnd)
        val event = pendingOn(tuesday).single { it.title == "Zahnarzt" }
        container.calendarImportService.import(event, details()).conflicts.single()
    }

    /** Lena's Sport session of today: the standing block the appointment collides with. */
    private suspend fun Scenario.sportToday() =
        blocksOn(tuesday).single { it.block.origin == BlockOrigin.RECURRING && it.item.name == "Sport" }

    private fun details(start: LocalTime = LocalTime(17, 30), duration: Duration = 30.minutes) = AppointmentDetails(
        category = Category.FOKUS,
        start = start,
        duration = duration,
    )

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
