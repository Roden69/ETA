package com.example.eta.data.repository

import android.app.Application
import com.example.eta.di.AppContainer
import com.example.eta.domain.setup.DailySlot
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
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
class SetupFreeTimeTest {
    private val now = Instant.parse("2026-10-20T08:00:00Z")
    private val today = LocalDate(2026, 10, 20)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private var previousZone: java.util.TimeZone? = null

    @Before
    fun pinTimeZone() {
        previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreTimeZone() {
        java.util.TimeZone.setDefault(previousZone)
    }

    private fun setup(freeTime: Duration) = UserSetup.draft(now).copy(
        freeTime = DailySlot(LocalTime(7, 0), freeTime),
    )

    @Test
    fun `saving a new free time lays its occurrences down from the first open day`() = runBlocking {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            container.setupRepository.complete(setup(Duration.ZERO), routineItems = emptyList())
            assertTrue(freeTimeBlocks(container).isEmpty())

            container.setupRepository.saveSettings(setup(30.minutes))

            val blocks = freeTimeBlocks(container)
            val firstOpen = container.planRepository.firstOpenDay()
            assertTrue("free time of 30 minutes must be laid down", blocks.isNotEmpty())
            assertTrue("nothing before today", blocks.none { it.block.date < today })
            assertTrue("occurrences from the first open day", blocks.any { it.block.date == firstOpen })
            assertTrue(blocks.all { it.block.plannedDuration == 30.minutes })
        } finally {
            container.close()
        }
    }

    @Test
    fun `saving zero free time again takes the open occurrences off the calendar`() = runBlocking {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            container.setupRepository.complete(setup(Duration.ZERO), routineItems = emptyList())
            container.setupRepository.saveSettings(setup(30.minutes))
            assertTrue(freeTimeBlocks(container).isNotEmpty())

            container.setupRepository.saveSettings(setup(Duration.ZERO))

            // Today is a day being lived and keeps what it has; the open days after it do not.
            val firstOpen = container.planRepository.firstOpenDay()
            assertTrue(freeTimeBlocks(container).all { it.block.date < firstOpen })
        } finally {
            container.close()
        }
    }

    @Test
    fun `free time goes zero, thirty minutes, zero and back without leftovers`() = runBlocking {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            container.setupRepository.complete(setup(Duration.ZERO), routineItems = emptyList())
            container.setupRepository.saveSettings(setup(30.minutes))
            container.setupRepository.saveSettings(setup(Duration.ZERO))
            container.setupRepository.saveSettings(setup(45.minutes))

            val firstOpen = container.planRepository.firstOpenDay()
            val open = freeTimeBlocks(container).filter { it.block.date >= firstOpen }
            assertTrue(open.isNotEmpty())
            assertTrue(open.all { it.block.plannedDuration == 45.minutes })
            assertEquals(
                "one occurrence per open day, no duplicates from the earlier answers",
                open.size,
                open.map { it.block.date }.distinct().size,
            )
        } finally {
            container.close()
        }
    }

    private suspend fun freeTimeBlocks(container: AppContainer) = container.planRepository
        .findRange(today, today.plus(DatePeriod(days = 21)))
        .filter { it.item.id == "setup:freetime" }
}
