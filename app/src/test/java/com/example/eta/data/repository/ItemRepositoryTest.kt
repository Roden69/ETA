package com.example.eta.data.repository

import android.app.Application
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ItemRepositoryTest {

    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val banClockStart = Instant.parse("2026-10-01T08:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun `an unfinished card planned out of the day keeps its Sperrliste clock when it falls back to the Sammelliste`() {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            val card = Item.newQuickTodo("Milch", now).copy(stage = Stage.DAY, enteredCollectionAt = banClockStart)
            runBlocking {
                container.itemRepository.add(card)
                container.itemRepository.moveTo(card, Stage.WEEK)
            }
            val stored = runBlocking { container.itemRepository.findById(card.id) }!!
            assertEquals(Stage.COLLECTION, stored.stage)
            assertEquals(banClockStart, stored.enteredCollectionAt)
        } finally {
            container.close()
        }
    }

    @Test
    fun `handing a card back to the Sammelliste on purpose restarts its Sperrliste clock`() {
        val container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        try {
            val card = Item.newQuickTodo("Milch", now).copy(stage = Stage.WEEK, enteredCollectionAt = banClockStart)
            runBlocking {
                container.itemRepository.add(card)
                container.itemRepository.moveTo(card, Stage.COLLECTION)
            }
            val stored = runBlocking { container.itemRepository.findById(card.id) }!!
            assertEquals(Stage.COLLECTION, stored.stage)
            assertEquals(now, stored.enteredCollectionAt)
        } finally {
            container.close()
        }
    }
}
