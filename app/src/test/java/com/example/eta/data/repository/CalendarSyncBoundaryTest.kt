package com.example.eta.data.repository

import android.app.Application
import android.content.Intent
import com.example.eta.data.calendar.AuthOutcome
import com.example.eta.data.calendar.CalendarApi
import com.example.eta.data.calendar.CalendarAuth
import com.example.eta.data.calendar.DeclinedSeriesStore
import com.example.eta.data.calendar.RemoteCalendar
import com.example.eta.data.calendar.RemoteEvent
import com.example.eta.data.calendar.RemoteSeries
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.Category
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalendarSyncBoundaryTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val from = LocalDate(2026, 10, 20)
    private val through = LocalDate(2026, 11, 10)
    private val calendarId = "calendar@example.test"
    private val seriesId = "lectures"
    private val details = AppointmentDetails(Category.FOKUS, LocalTime(8, 0), 90.minutes)

    private class MutableClock(var instant: Instant) : Clock { override fun now() = instant }

    private inner class Remote : CalendarApi, CalendarAuth {
        var events = listOf(lecture(LocalDate(2026, 10, 26)), lecture(LocalDate(2026, 11, 2)), lecture(LocalDate(2026, 11, 9)))
        var master = RemoteSeries(listOf("RRULE:FREQ=WEEKLY;BYDAY=MO"), LocalDate(2026, 10, 5), LocalTime(8, 0))
        override suspend fun token() = AuthOutcome.Ok("test-token")
        override fun tokenFrom(data: Intent?) = AuthOutcome.Ok("test-token")
        override suspend fun calendars(token: String) = listOf(RemoteCalendar(calendarId, "Lectures", null, true, false))
        override suspend fun events(token: String, calendarId: String, from: LocalDate, to: LocalDate) =
            events.filter { it.date in from..to }
        override suspend fun seriesMaster(token: String, calendarId: String, seriesId: String) = master
    }

    private fun lecture(date: LocalDate) = RemoteEvent(
        calendarId, "lecture-$date", "Lecture", date, LocalTime(8, 0), 90.minutes, false, null, seriesId,
    )

    private fun container(remote: Remote, clock: Clock) = AppContainer(
        RuntimeEnvironment.getApplication(), sandbox = true, clock = clock, timeZone = zone,
        calendarApi = remote, calendarAuth = remote,
    )

    private inline fun <T> withApp(remote: Remote, clock: Clock, body: (AppContainer) -> T): T {
        val app = container(remote, clock)
        return try { body(app) } finally { app.close() }
    }

    private suspend fun AppContainer.offer(): SeriesProposal.Offer {
        calendarSyncService.connect()
        calendarSyncService.refresh(from, through)
        return calendarSyncService.proposeSeries(calendarRepository.pendingFor(from, through)).single() as SeriesProposal.Offer
    }

    @Test
    fun `offline top up never treats dates beyond the last successful window as holidays`() = runBlocking {
        val remote = Remote()
        val clock = MutableClock(Instant.parse("2026-10-20T06:00:00Z"))
        withApp(remote, clock) { app ->
            val offer = app.offer()
            val ids = app.calendarImportService.adoptSeries(offer.event, offer.pattern, details)
            clock.instant = Instant.parse("2026-10-27T06:00:00Z")
            app.scheduleMaintenance.topUp()
            assertTrue(app.planRepository.findDay(LocalDate(2026, 11, 16)).any { it.item.id in ids })
        }
    }

    @Test
    fun `an ignored occurrence cancelled remotely is not evidence that the lecture still exists`() = runBlocking {
        val remote = Remote()
        val clock = MutableClock(Instant.parse("2026-10-20T06:00:00Z"))
        withApp(remote, clock) { app ->
            val offer = app.offer()
            app.calendarRepository.ignore(app.calendarRepository.pendingFor(from, through).single { it.date == LocalDate(2026, 11, 2) })
            val ids = app.calendarImportService.adoptSeries(offer.event, offer.pattern, details)
            remote.events = remote.events.filterNot { it.date == LocalDate(2026, 11, 2) }
            app.calendarSyncService.refresh(from, through)
            app.scheduleMaintenance.topUp()
            assertFalse(app.planRepository.findDay(LocalDate(2026, 11, 2)).any { it.item.id in ids })
        }
    }

    @Test
    fun `a successful empty calendar removes future unchanged series occurrences`() = runBlocking {
        val remote = Remote()
        val clock = MutableClock(Instant.parse("2026-10-20T06:00:00Z"))
        withApp(remote, clock) { app ->
            val offer = app.offer()
            val ids = app.calendarImportService.adoptSeries(offer.event, offer.pattern, details)
            remote.events = emptyList()
            app.calendarSyncService.refresh(from, through)
            app.scheduleMaintenance.topUp()
            assertFalse(app.planRepository.findRange(from, through).any { it.item.id in ids })
        }
    }

    @Test
    fun `concurrent adoption of one series creates one group and returns its same ids`() = runBlocking {
        val remote = Remote()
        val clock = MutableClock(Instant.parse("2026-10-20T06:00:00Z"))
        withApp(remote, clock) { app ->
            val offer = app.offer()
            val results = coroutineScope {
                List(2) { async { app.calendarImportService.adoptSeries(offer.event, offer.pattern, details) } }.awaitAll()
            }
            assertEquals(results[0], results[1])
            assertEquals(results[0].size, app.itemRepository.observeRecurringDefinitions().first().count { it.name == "Lecture" })
        }
    }

    @Test
    fun `a sandbox decline and reset cannot change the real calendar decisions`() {
        val context = RuntimeEnvironment.getApplication()
        val real = DeclinedSeriesStore(context)
        real.decline(calendarId, seriesId)
        val remote = Remote()
        withApp(remote, MutableClock(Instant.parse("2026-10-20T06:00:00Z"))) { sandbox ->
            assertFalse(sandbox.declinedSeriesStore.isDeclined(calendarId, seriesId))
            sandbox.declinedSeriesStore.reset()
            assertTrue(real.isDeclined(calendarId, seriesId))
        }
    }

    @Test
    fun `a moved exception cannot truncate the last regular occurrence`() = runBlocking {
        val remote = Remote()
        remote.events = remote.events.mapIndexed { index, event ->
            if (index == 0) event.copy(start = LocalTime(10, 0)) else event
        }
        remote.master = RemoteSeries(
            listOf("RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261109T070000Z"),
            LocalDate(2026, 10, 5), LocalTime(8, 0),
        )
        withApp(remote, MutableClock(Instant.parse("2026-10-20T06:00:00Z"))) { app ->
            assertEquals(LocalDate(2026, 11, 9), app.offer().pattern.lastDate)
        }
    }

    @Test
    fun `a cancellation preserves a running occurrence and a future hand moved occurrence`() = runBlocking {
        val remote = Remote()
        val clock = MutableClock(Instant.parse("2026-10-20T06:00:00Z"))
        withApp(remote, clock) { app ->
            val offer = app.offer()
            val ids = app.calendarImportService.adoptSeries(offer.event, offer.pattern, details)
            val runningDay = LocalDate(2026, 10, 26)
            val movedDay = LocalDate(2026, 11, 2)
            val running = app.planRepository.findDay(runningDay).single { it.item.id in ids }.block
                .copy(timerWorked = 5.minutes, timerRunningSince = Instant.parse("2026-10-26T07:00:00Z"))
            val moved = app.planRepository.findDay(movedDay).single { it.item.id in ids }.block
                .copy(start = LocalTime(9, 0))
            app.planRepository.addBlock(running)
            app.planRepository.addBlock(moved)
            clock.instant = Instant.parse("2026-10-26T07:05:00Z")
            remote.events = remote.events.filter { it.date == LocalDate(2026, 11, 9) }
            app.calendarSyncService.refresh(runningDay, through)
            app.scheduleMaintenance.topUp()
            assertEquals(running, app.planRepository.findDay(runningDay).single { it.item.id in ids }.block)
            assertEquals(moved, app.planRepository.findDay(movedDay).single { it.item.id in ids }.block)
        }
    }
}
