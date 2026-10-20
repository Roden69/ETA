package com.example.eta.data.repository

import android.app.Application
import android.content.Intent
import com.example.eta.data.calendar.AuthOutcome
import com.example.eta.data.calendar.CalendarApi
import com.example.eta.data.calendar.CalendarAuth
import com.example.eta.data.calendar.RemoteCalendar
import com.example.eta.data.calendar.RemoteEvent
import com.example.eta.data.calendar.RemoteSeries
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A weekly lecture with an end date and a holiday, as Google keeps it.
 *
 * The clock says Tuesday 2026-10-20 in Berlin. Mondays in the window are the 26th,
 * the 2nd (a holiday: Google has no occurrence) and the 9th, the last lecture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalendarSeriesOfferTest {

    private val previousZone = TimeZone.currentSystemDefault()
    private val now = Instant.parse("2026-10-20T07:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val from = LocalDate(2026, 10, 20)
    private val to = LocalDate(2026, 11, 9)
    private val calendarId = "primary@example.test"
    private val seriesId = "series-analysis"

    @Before
    fun pinBerlin() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreZone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(previousZone.id))
    }

    private fun lecture(day: LocalDate) = RemoteEvent(
        calendarId = calendarId,
        eventId = "analysis-$day",
        title = "Analysis",
        date = day,
        start = LocalTime(8, 0),
        duration = 90.minutes,
        allDay = false,
        updatedAt = null,
        seriesId = seriesId,
    )

    private val dentist = RemoteEvent(
        calendarId = calendarId,
        eventId = "dentist",
        title = "Zahnarzt",
        date = LocalDate(2026, 10, 22),
        start = LocalTime(10, 0),
        duration = 60.minutes,
        allDay = false,
        updatedAt = null,
    )

    private val universityRule = RemoteSeries(
        recurrence = listOf(
            "RRULE:FREQ=WEEKLY;WKST=MO;UNTIL=20261109T225959Z;BYDAY=MO",
            "EXDATE;TZID=Europe/Berlin:20261102T080000",
        ),
        start = LocalDate(2026, 10, 5),
    )

    private fun api(
        master: RemoteSeries = universityRule,
        occurrences: List<RemoteEvent> = listOf(
            lecture(LocalDate(2026, 10, 26)),
            lecture(LocalDate(2026, 11, 9)),
            dentist,
        ),
    ) = FakeCalendarApi(occurrences, mapOf(seriesId to master))

    private fun container(api: CalendarApi): AppContainer = AppContainer(
        RuntimeEnvironment.getApplication(),
        sandbox = true,
        clock = clock,
        calendarAuth = FixedAuth,
        calendarApi = api,
    )

    private fun details() = AppointmentDetails(
        category = Category.FOKUS,
        start = LocalTime(8, 0),
        duration = 90.minutes,
    )

    private suspend fun AppContainer.connectAndRead(): List<CalendarEvent> {
        calendarSyncService.connect()
        calendarSyncService.refresh(from, to)
        return calendarRepository.pendingFor(from, to)
    }

    private suspend fun AppContainer.analysisBlocks(until: LocalDate): List<PlannedBlock> =
        planRepository.findRange(from, until)
            .filter { it.item.name == "Analysis" && it.block.origin == BlockOrigin.RECURRING }
            .map { it.block }

    @Test
    fun `a recurring series is offered once and a one-off event is not`() = runBlocking {
        val container = container(api())
        try {
            val proposals = container.calendarSyncService.proposeSeries(container.connectAndRead())

            assertEquals(1, proposals.size)
            val offer = proposals.single() as SeriesProposal.Offer
            assertEquals("Analysis", offer.event.title)
            assertEquals(setOf(DayOfWeek.MONDAY), offer.pattern.weekdays)
            assertEquals(LocalDate(2026, 11, 9), offer.pattern.lastDate)
            assertTrue(proposals.none { it.event.seriesId == null })
        } finally {
            container.close()
        }
    }

    @Test
    fun `adopting the series lays its lectures up to the last one and skips the holiday`() = runBlocking {
        val container = container(api())
        try {
            val offer = container.calendarSyncService.proposeSeries(container.connectAndRead())
                .single() as SeriesProposal.Offer
            container.calendarImportService.adoptSeries(offer.event, offer.pattern, details())
            container.scheduleMaintenance.topUpUntil(LocalDate(2026, 11, 30))

            val blocks = container.analysisBlocks(LocalDate(2026, 11, 30))
            assertEquals(
                setOf(LocalDate(2026, 10, 26), LocalDate(2026, 11, 9)),
                blocks.map { it.date }.toSet(),
            )
            val card = container.planRepository.findRange(from, LocalDate(2026, 11, 30))
                .first { it.item.name == "Analysis" }.item
            assertEquals(LocalDate(2026, 11, 9), card.repeatUntil)
            assertTrue(container.calendarRepository.isSeriesAdopted(calendarId, seriesId))
        } finally {
            container.close()
        }
    }

    @Test
    fun `an earlier single import of a lecture gives way to the card from tomorrow on`() = runBlocking {
        val container = container(api())
        try {
            val pending = container.connectAndRead()
            val single = pending.first { it.date == LocalDate(2026, 10, 26) }
            container.calendarImportService.import(single, details())

            val offer = container.calendarSyncService
                .proposeSeries(container.calendarRepository.pendingFor(from, to))
                .single() as SeriesProposal.Offer
            container.calendarImportService.adoptSeries(offer.event, offer.pattern, details())

            val onTheDay = container.planRepository.findDay(LocalDate(2026, 10, 26))
                .filter { it.item.name == "Analysis" }
            assertEquals(listOf(BlockOrigin.RECURRING), onTheDay.map { it.block.origin })
            assertTrue(
                container.calendarRepository.occurrencesOf(calendarId, seriesId)
                    .filter { it.date == LocalDate(2026, 10, 26) }
                    .all { it.itemId != single.itemId },
            )
        } finally {
            container.close()
        }
    }

    @Test
    fun `a declined series is kept single and never offered again`() = runBlocking {
        val container = container(api())
        try {
            val offer = container.calendarSyncService.proposeSeries(container.connectAndRead())
                .single() as SeriesProposal.Offer
            container.calendarRepository.declineSeries(offer.event.calendarId, seriesId)

            container.calendarSyncService.refresh(from, to)
            assertTrue(
                container.calendarSyncService
                    .proposeSeries(container.calendarRepository.pendingFor(from, to))
                    .isEmpty(),
            )
            assertEquals(
                setOf("analysis-2026-10-26", "analysis-2026-11-09", "dentist"),
                container.calendarRepository.pendingFor(from, to).map { it.eventId }.toSet(),
            )
        } finally {
            container.close()
        }
    }

    @Test
    fun `a series Eta cannot represent is refused with the reason, and not offered again once acknowledged`() =
        runBlocking {
            val refusing = RemoteSeries(
                recurrence = listOf("RRULE:FREQ=WEEKLY;BYDAY=MO", "RDATE:20261116T080000Z"),
                start = LocalDate(2026, 10, 5),
            )
            val container = container(api(master = refusing))
            try {
                val refusal = container.calendarSyncService.proposeSeries(container.connectAndRead())
                    .single() as SeriesProposal.Refused
                assertTrue(refusal.reason.contains("RDATE"))

                container.calendarRepository.declineSeries(calendarId, seriesId)
                assertNull(
                    container.calendarSyncService
                        .proposeSeries(container.calendarRepository.pendingFor(from, to))
                        .firstOrNull(),
                )
            } finally {
                container.close()
            }
        }
}

private object FixedAuth : CalendarAuth {
    override suspend fun token(): AuthOutcome = AuthOutcome.Ok("test-token")
    override fun tokenFrom(data: Intent?): AuthOutcome = AuthOutcome.Ok("test-token")
}

/** A calendar that answers from fixed data, the way Google would for the requested window. */
private class FakeCalendarApi(
    private val occurrences: List<RemoteEvent>,
    private val masters: Map<String, RemoteSeries>,
) : CalendarApi {
    override suspend fun calendars(token: String) = listOf(
        RemoteCalendar(
            id = "primary@example.test",
            displayName = "Privat",
            accountName = "primary@example.test",
            isPrimary = true,
            isForeign = false,
        ),
    )

    override suspend fun events(token: String, calendarId: String, from: LocalDate, to: LocalDate) =
        occurrences.filter { it.calendarId == calendarId && it.date >= from && it.date <= to }

    override suspend fun seriesMaster(token: String, calendarId: String, seriesId: String): RemoteSeries =
        masters.getValue(seriesId)
}
