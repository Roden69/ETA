package com.example.eta.ui.weekplanner

import android.app.Application
import android.os.Looper
import com.example.eta.data.repository.WeekScope
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PullBlock
import com.example.eta.domain.setup.UserSetup
import com.example.eta.ui.attributes.TodoAttributes
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The weekly planning's two columns, driven through the real ViewModel over a real
 * database. Room and the ViewModel's `Dispatchers.Main` both deliver on the main
 * looper, so [settle] pumps it until the state the test waits for has arrived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WeekPlannerViewModelTest {

    /** Tuesday 20 October 2026; the weekly planning day (Sunday) began the cycle on 18 October. */
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val cycleStart = LocalDate(2026, 10, 18)
    private val lastCycle = LocalDate(2026, 10, 11)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private var previousZone: java.util.TimeZone? = null
    private lateinit var container: AppContainer
    private var observer: Job? = null

    @Before
    fun setUp() {
        previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
        container = AppContainer(RuntimeEnvironment.getApplication(), sandbox = true, clock = clock)
        runBlocking { container.setupRepository.saveSettings(UserSetup.draft(now)) }
    }

    @After
    fun tearDown() {
        observer?.cancel()
        container.close()
        java.util.TimeZone.setDefault(previousZone)
    }

    @Test
    fun `an unfinished goal of the last cycle holds the Sammelliste until it is handed back`() {
        val oldGoal = todo("Fenster putzen")
        val cardA = todo("Zahnarzt anrufen")
        val cardB = todo("Pfand wegbringen")
        runBlocking {
            container.itemRepository.add(oldGoal)
            container.itemRepository.takeIntoWeek(oldGoal, lastCycle)
            container.itemRepository.add(cardA)
            container.itemRepository.add(cardB)
        }
        val vm = viewModel()
        observe(vm)

        settle { vm.uiState.value.unfinished.size == 1 && vm.uiState.value.held.size == 2 }
        assertTrue(vm.uiState.value.collection.isEmpty())
        assertEquals(PullBlock.OpenGoals(1), vm.uiState.value.held.first().second)

        // A tap on a held card is refused by the screen's own gate: nothing moves.
        vm.pullIntoWeek(cardA)
        assertEquals(Stage.COLLECTION, stageOf(cardA))

        // Handing the old goal back is the way out; the column opens at once.
        vm.returnToCollection(oldGoal)
        settle { vm.uiState.value.unfinished.isEmpty() && vm.uiState.value.collection.size == 3 }

        vm.pullIntoWeek(cardA)
        settle { stageOf(cardA) == Stage.WEEK }
        assertEquals(cycleStart, weekStampOf(cardA))
    }

    @Test
    fun `taking an unfinished goal on re-stamps it to this cycle and opens the column`() {
        val oldGoal = todo("Fenster putzen")
        val cardA = todo("Zahnarzt anrufen")
        runBlocking {
            container.itemRepository.add(oldGoal)
            container.itemRepository.takeIntoWeek(oldGoal, lastCycle)
            container.itemRepository.add(cardA)
        }
        val vm = viewModel()
        observe(vm)
        settle { vm.uiState.value.unfinished.size == 1 && vm.uiState.value.held.size == 1 }

        vm.takeUnfinishedIntoWeek(oldGoal)

        settle { vm.uiState.value.unfinished.isEmpty() && vm.uiState.value.collection.size == 1 }
        assertEquals(Stage.WEEK, stageOf(oldGoal))
        assertEquals(cycleStart, weekStampOf(oldGoal))
    }

    @Test
    fun `a bare ToDo is not taken into the week before it is concretized`() {
        val bare = Item.newQuickTodo("Milch", now)
        val concretized = todo("Wäsche waschen")
        runBlocking {
            container.itemRepository.add(bare)
            container.itemRepository.add(concretized)
        }
        val vm = viewModel()
        observe(vm)
        settle { vm.uiState.value.collection.size == 2 }

        vm.pullIntoWeek(bare)
        vm.pullIntoWeek(concretized)
        settle { stageOf(concretized) == Stage.WEEK }

        assertEquals(Stage.COLLECTION, stageOf(bare))
    }

    @Test
    fun `saving the concretize dialog answers a bare card and takes it into the week`() {
        val bare = Item.newQuickTodo("Milch", now)
        runBlocking { container.itemRepository.add(bare) }
        val vm = viewModel()
        observe(vm)
        settle { vm.uiState.value.collection.any { it.id == bare.id } }

        vm.concretizeGoal(bare, "Milch kaufen", TodoAttributes(duration = 30.minutes), intoWeek = true)

        settle { stageOf(bare) == Stage.WEEK }
        val saved = runBlocking { container.itemRepository.findById(bare.id) }!!
        assertTrue(saved.isConcretized)
        assertEquals("Milch kaufen", saved.name)
        assertEquals(cycleStart, saved.weekStartedOn)
    }

    @Test
    fun `a bare card is not answered into the week while open goals hold the Sammelliste`() {
        val oldGoal = todo("Fenster putzen")
        val bare = Item.newQuickTodo("Milch", now)
        runBlocking {
            container.itemRepository.add(oldGoal)
            container.itemRepository.takeIntoWeek(oldGoal, lastCycle)
            container.itemRepository.add(bare)
        }
        val vm = viewModel()
        observe(vm)
        settle { vm.uiState.value.unfinished.size == 1 && vm.uiState.value.held.size == 1 }

        vm.concretizeGoal(bare, "Milch", TodoAttributes(), intoWeek = true)

        // The gate refuses before anything is written, so there is nothing to wait for.
        assertEquals(Stage.COLLECTION, stageOf(bare))
        assertFalse(runBlocking { container.itemRepository.findById(bare.id) }!!.isConcretized)
    }

    @Test
    fun `a stranded bare card in the week is answered in place and keeps its cycle`() {
        val stranded = Item.newQuickTodo("Milch", now).copy(stage = Stage.WEEK, weekStartedOn = lastCycle)
        runBlocking { container.itemRepository.add(stranded) }
        val vm = viewModel()
        observe(vm)
        settle { vm.uiState.value.unfinished.any { it.id == stranded.id } }

        vm.concretizeGoal(stranded, "Milch", TodoAttributes(duration = 30.minutes), intoWeek = false)

        settle { runBlocking { container.itemRepository.findById(stranded.id) }?.isConcretized == true }
        assertEquals(Stage.WEEK, stageOf(stranded))
        assertEquals(lastCycle, weekStampOf(stranded))
    }

    private fun todo(name: String) = Item.newTodo(
        name = name,
        category = Category.FOKUS,
        priority = Priority.MUST,
        targetDate = null,
        estimatedDuration = 1.hours,
        now = now,
    )

    private fun viewModel() = WeekPlannerViewModel(
        weekPlanningService = container.weekPlanningService,
        itemRepository = container.itemRepository,
        planRepository = container.planRepository,
        scheduleMaintenance = container.scheduleMaintenance,
        setupRepository = container.setupRepository,
        scope = WeekScope.SCHEDULED,
        clock = clock,
    )

    /** `uiState` is shared only while subscribed; the test subscribes for its whole run. */
    private fun observe(vm: WeekPlannerViewModel) {
        observer = CoroutineScope(Dispatchers.Default).launch { vm.uiState.collect { } }
    }

    private fun stageOf(item: Item): Stage? = runBlocking { container.itemRepository.findById(item.id)?.stage }

    private fun weekStampOf(item: Item): LocalDate? =
        runBlocking { container.itemRepository.findById(item.id)?.weekStartedOn }

    /** Pumps the main looper until [condition] holds, failing after five seconds. */
    private fun settle(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "week planner state never settled" }
            Thread.sleep(10)
        }
    }
}
