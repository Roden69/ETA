package com.example.eta.data.repository

import android.app.Application
import com.example.eta.di.AppContainer
import com.example.eta.domain.chain.isWaitingFollowUp
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FollowUpRepositoryTest {
    private val now = Instant.parse("2026-10-18T10:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private lateinit var container: AppContainer

    @Before
    fun open() {
        container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
    }

    @After
    fun close() = container.close()

    private fun todo(name: String) = Item.newTodo(
        name = name,
        category = Category.FOKUS,
        priority = Priority.MUST,
        targetDate = null,
        estimatedDuration = 1.hours,
        now = now,
    )

    private suspend fun collection(): List<Item> = container.itemRepository.observeStage(Stage.COLLECTION).first()

    private suspend fun byName(name: String): Item = collection().single { it.name == name }

    @Test
    fun `a banned step name refuses the whole chain and leaves nothing behind`() = runBlocking {
        container.itemRepository.addWithoutLockCheck(
            todo("Steuer").copy(stage = Stage.LOCKED, lockedUntil = now + 30.days),
        )

        val result = container.itemRepository.add(todo("Unterlagen"), followUps = listOf("Ordner", " steuer "))

        val blocked = result as com.example.eta.data.repository.AddItemResult.BlockedByLock
        assertEquals("steuer", blocked.lockedName)
        assertTrue(collection().isEmpty())
    }

    @Test
    fun `deleting a waiting middle step hands its successor to the step before it`() = runBlocking {
        container.itemRepository.add(todo("Waschen"), followUps = listOf("Aufhängen", "Zusammenlegen"))
        val washing = byName("Waschen")
        val last = byName("Zusammenlegen")

        container.itemRepository.delete(byName("Aufhängen"))

        val moved = container.itemRepository.findById(last.id)!!
        assertEquals(washing.id, moved.followUpOfId)
        assertTrue(moved.isWaitingFollowUp())
    }

    @Test
    fun `folding the predecessor keeps its successor waiting until the surviving group is done`() = runBlocking {
        container.itemRepository.add(todo("Waschen"), followUps = listOf("Aufhängen"))
        container.itemRepository.add(todo("Haushalt"))
        val washing = byName("Waschen")
        val group = byName("Haushalt")
        val hanging = byName("Aufhängen")

        container.itemRepository.retireFolded(listOf(washing.id), group.id)

        val waiting = container.itemRepository.findById(hanging.id)!!
        assertEquals(group.id, waiting.followUpOfId)
        assertTrue(waiting.isWaitingFollowUp())
        assertNotNull(container.itemRepository.findById(washing.id)!!.completedAt)

        container.itemRepository.update(group.copy(stage = Stage.DONE, completedAt = now))

        val released = container.itemRepository.findById(hanging.id)!!
        assertEquals(now, released.enteredCollectionAt)
        assertNull(released.completedAt)
    }
}
