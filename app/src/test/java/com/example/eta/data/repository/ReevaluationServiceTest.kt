package com.example.eta.data.repository

import android.app.Application
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.RecurrenceRule
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReevaluationServiceTest {
    @Test
    fun `concurrent and reopened settlements credit the completed date only once`() = runBlocking {
        val now = Instant.parse("2026-10-18T20:00:00Z")
        val clock = object : Clock {
            override fun now(): Instant = now
        }
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            val date = LocalDate(2026, 10, 17)
            val item = Item.newRecurring(
                name = "Read",
                category = Category.FOKUS,
                recurrenceRule = RecurrenceRule.Daily,
                startTime = LocalTime(10, 0),
                estimatedDuration = 2.hours,
                now = now,
            )
            container.itemRepository.add(item)
            container.planRepository.addBlock(
                PlannedBlock(
                    itemId = item.id,
                    date = date,
                    start = LocalTime(10, 0),
                    plannedDuration = 2.hours,
                    origin = BlockOrigin.RECURRING,
                    completedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            List(2) {
                async(Dispatchers.Default) {
                    container.reevaluationService.settle(date, emptyList(), emptyMap())
                }
            }.awaitAll()
            val settledRows = container.pointsRepository.observeRecent().first()
            val harvest = settledRows.filter { it.reason == PointsReason.HARVEST }
            assertEquals(1, harvest.size)
            assertEquals(2.0, harvest.single().amount, 0.0001)
            assertTrue(container.planRepository.findDayPlan(date)?.isSettled == true)

            container.reevaluationService.settle(date, emptyList(), emptyMap())
            assertEquals(settledRows, container.pointsRepository.observeRecent().first())
        } finally {
            container.close()
        }
    }
}
