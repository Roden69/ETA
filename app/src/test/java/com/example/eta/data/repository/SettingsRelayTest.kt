package com.example.eta.data.repository

import android.app.Application
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.setup.UserSetup
import java.util.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsRelayTest {

    /** 14:00 in Berlin on Tuesday 2026-10-20. */
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val today = LocalDate(2026, 10, 20)
    private val tomorrow = LocalDate(2026, 10, 21)
    private val dayAfter = LocalDate(2026, 10, 22)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private var previousZone: TimeZone? = null

    @Before
    fun pinZone() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(previousZone)
    }

    @Test
    fun `saving settings re-lays today's unstarted bedprep, keeps completed morning and confirmed tomorrow`() = runBlocking {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            val setup = UserSetup.draft(now)
            container.setupRepository.complete(setup, emptyList())

            val morning = container.planRepository.findDay(today)
                .single { it.block.itemId == "setup:morning" }.block
            container.planRepository.complete(morning)
            container.planRepository.confirmDayPlan(tomorrow)

            container.setupRepository.saveSettings(
                setup.copy(bedPrepTime = LocalTime(21, 0), wakeTime = LocalTime(6, 30)),
            )

            val bedprepToday = container.planRepository.findDay(today)
                .filter { it.block.itemId == "setup:bedprep" }
                .map { it.block }
            assertEquals(1, bedprepToday.size)
            assertEquals(LocalTime(21, 0), bedprepToday.single().start)
            assertEquals(2.hours, bedprepToday.single().plannedDuration)

            val morningToday = container.planRepository.findDay(today)
                .single { it.block.itemId == "setup:morning" }.block
            assertEquals(morning.id, morningToday.id)
            assertEquals(LocalTime(7, 0), morningToday.start)
            assertTrue(morningToday.isCompleted)

            val bedprepTomorrow = container.planRepository.findDay(tomorrow)
                .single { it.block.itemId == "setup:bedprep" }.block
            assertEquals(LocalTime(22, 0), bedprepTomorrow.start)

            val bedprepDayAfter = container.planRepository.findDay(dayAfter)
                .single { it.block.itemId == "setup:bedprep" }.block
            assertEquals(LocalTime(21, 0), bedprepDayAfter.start)
        } finally {
            container.close()
        }
    }

    @Test
    fun `editing a standing task on the Listen tab re-lays today's unstarted occurrence`() = runBlocking {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            val ids = container.recurringTaskService.createGroup(edit(LocalTime(15, 0)))
            val id = ids.single()
            assertEquals(
                listOf(LocalTime(15, 0)),
                container.planRepository.findDay(today).filter { it.block.itemId == id }.map { it.block.start },
            )

            container.recurringTaskService.saveGroup(ids, edit(LocalTime(16, 0)))

            val todays = container.planRepository.findDay(today).filter { it.block.itemId == id }.map { it.block }
            assertEquals(listOf(LocalTime(16, 0)), todays.map { it.start })
            assertTrue(todays.all { it.isOpen })
        } finally {
            container.close()
        }
    }

    @Test
    fun `an edit does not lay today's occurrence when it would start before now`() = runBlocking {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            val ids = container.recurringTaskService.createGroup(edit(LocalTime(15, 0)))
            val id = ids.single()

            container.recurringTaskService.saveGroup(ids, edit(LocalTime(9, 0)))

            val todays: List<PlannedBlock> = container.planRepository.findDay(today)
                .filter { it.block.itemId == id }
                .map { it.block }
            assertTrue(todays.isEmpty())
            // The task is weekly on Tuesday: the next occurrence after today is the 27th.
            val later = container.planRepository.findRange(tomorrow, LocalDate(2026, 10, 27))
                .filter { it.block.itemId == id }
            assertTrue(later.isNotEmpty())
            assertTrue(later.all { it.block.start == LocalTime(9, 0) })
        } finally {
            container.close()
        }
    }

    @Test
    fun `a block moved by hand today keeps its place when the standing task is edited`() = runBlocking {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            val ids = container.recurringTaskService.createGroup(edit(LocalTime(15, 0)))
            val id = ids.single()
            val block = container.planRepository.findDay(today).single { it.block.itemId == id }.block
            container.planRepository.addBlock(block.copy(start = LocalTime(17, 0), updatedAt = now))

            container.recurringTaskService.saveGroup(ids, edit(LocalTime(16, 0)))

            val todays = container.planRepository.findDay(today).filter { it.block.itemId == id }.map { it.block }
            assertEquals(listOf(LocalTime(17, 0)), todays.map { it.start })
        } finally {
            container.close()
        }
    }

    private fun edit(start: LocalTime) = RecurringEdit(
        name = "Lesen",
        note = null,
        category = Category.FOKUS,
        weekdays = setOf(DayOfWeek.TUESDAY),
        startTime = start,
        duration = 1.hours,
        travelBefore = null,
        returnAfter = null,
        breakAfter = null,
        endSound = false,
    )
}
