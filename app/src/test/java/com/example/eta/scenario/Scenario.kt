package com.example.eta.scenario

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.example.eta.di.AppContainer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import org.robolectric.RuntimeEnvironment

/** Real time one whole scenario may take, Room and Robolectric included, unless a test asks for more. */
private val DEFAULT_DEADLINE: Duration = 3.minutes

/**
 * Runs [body] as a multi-day scenario on the real wiring: a sandboxed
 * [AppContainer] over an in-memory database, a [ScenarioClock] in [zone], and
 * ViewModels built by the app's own factories.
 *
 * Use it from a Robolectric test, e.g.
 * `@Test fun \`...\`() = scenario("2026-10-18T09:00") { step("Einrichtung") { ... } }`.
 *
 * The whole run has a hard real-time [deadline]. Past it the test fails with a dump
 * of every thread (see [withDeadline]), so a hang names the code it is stuck in.
 *
 * @param start the scenario's first moment, `LocalDateTime.parse` format.
 * @param zone the time zone the device runs in, IANA id.
 * @param deadline real time the whole scenario may take.
 * @param calendar the Google Calendar the app reads; empty unless the test adds events.
 * @param alarms whether the alarm coordinators arm anything (off by default).
 */
fun scenario(
    start: String,
    zone: String = "Europe/Berlin",
    deadline: Duration = DEFAULT_DEADLINE,
    calendar: FakeCalendar? = null,
    alarms: Boolean = false,
    body: suspend Scenario.() -> Unit,
) {
    val scenario = Scenario(
        LocalDateTime.parse(start),
        TimeZone.of(zone),
        RuntimeEnvironment.getApplication(),
        calendar ?: FakeCalendar(),
        alarms,
    )
    withDeadline(deadline) {
        scenario.use {
            runBlocking { it.body() }
        }
    }
}

/**
 * One scenario: its clock, its container, its ViewModels and the checks that run
 * after every [step].
 *
 * **Threads.** `Dispatchers.Main` is replaced by one dedicated thread, which is
 * what the app's main thread is to the ViewModels: their coroutines run there,
 * one at a time, while the scenario body suspends on the test thread. Room runs
 * its own queries and resumes the caller on the main thread, as it does in the app.
 *
 * **Reading state.** A ViewModel's `stateIn(WhileSubscribed)` flow computes only
 * while someone collects it, and its `.value` is the initial value until then. So
 * the harness never reads such a flow cold: [awaitState] collects it until a
 * predicate holds, and [subscribed] keeps it collected for a block. Every driver
 * waits for the state it is about to act on to be loaded (a card it expects to
 * see is there) before it calls the ViewModel, just as the screen waits to be
 * drawn before a finger can reach a button.
 *
 * **Days.** VMs are keyed by date in the app (`rememberDateKey`), so a day change
 * clears the [ViewModelStore] here: the next [viewModel] call builds a fresh
 * instance on the new day.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Scenario internal constructor(
    start: LocalDateTime,
    val zone: TimeZone,
    context: Context,
    calendar: FakeCalendar,
    alarms: Boolean,
) : AutoCloseable {
    private val previousDefaultZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    private val mainThread: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "scenario-main").apply { isDaemon = true }
    }
    private val mainDispatcher = mainThread.asCoroutineDispatcher()

    init {
        // Everything that reads the system zone (repositories' defaults, the
        // domain's `TimeZone.currentSystemDefault()`) must agree with the scenario.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone.id))
        Dispatchers.setMain(mainDispatcher)
    }

    /** The scenario's clock; it is the only clock any scenario component reads. */
    val clock = ScenarioClock(zone, start)

    /** The app's wiring over an in-memory database, alarms off. */
    val container = AppContainer(
        context,
        sandbox = true,
        clock = clock,
        timeZone = zone,
        alarms = alarms,
        calendarAuth = calendar,
        calendarApi = calendar,
    )

    /** Checks run after every [step]. */
    val invariants = Invariants(this)

    /** Real time one awaited state may take before the scenario fails. */
    var timeout: Duration = 10.seconds

    private val startDate = clock.today
    private val screen = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var store = ViewModelStore()
    private val modelJobs = mutableSetOf<Job>()

    /** "Tag 2 (Mo 19.10.) 11:30": the prefix every failure message carries. */
    fun stamp(): String {
        val now = clock.local()
        val (_, month, dayOfMonth) = now.date.toString().split('-')
        val day = startDate.daysUntil(now.date) + 1
        return "Tag $day (${germanWeekday(now.date.dayOfWeek)} $dayOfMonth.$month.) " +
            "${now.hour.pad()}:${now.minute.pad()}"
    }

    /**
     * Runs [action] as one named step of the scenario, then checks every
     * invariant against the database. A violation fails with the step's name and
     * the day and time it happened.
     */
    suspend fun <T> step(name: String, action: suspend Scenario.() -> T): T {
        val result = action()
        invariants.check(name)
        return result
    }

    /** Moves the clock to [moment]; a new day clears the ViewModels. */
    fun advanceTo(moment: LocalDateTime) = rollingDay { clock.advanceTo(moment) }

    /** Moves the clock to [time] on the same day. */
    fun at(time: kotlinx.datetime.LocalTime) = rollingDay { clock.at(time) }

    /** Moves the clock to the next day at [time]; always a new day, so the ViewModels are rebuilt. */
    fun nextDay(time: kotlinx.datetime.LocalTime) {
        store.clear()
        store = ViewModelStore()
        clock.nextDay(time)
    }

    /**
     * The ViewModel of [modelClass] built by [factory], kept for the day; the
     * same instance comes back on later calls, as it does for one screen.
     */
    fun <VM : ViewModel> viewModel(
        factory: ViewModelProvider.Factory,
        modelClass: Class<VM>,
        key: String = modelClass.name,
    ): VM = ViewModelProvider.create(store, factory)[key, modelClass].also {
        it.viewModelScope.coroutineContext[Job]?.let(modelJobs::add)
    }

    /** [viewModel] with the class inferred; [key] only needs setting when one class is kept twice. */
    inline fun <reified VM : ViewModel> viewModel(
        factory: ViewModelProvider.Factory,
        key: String = VM::class.java.name,
    ): VM = viewModel(factory, VM::class.java, key)

    /**
     * Waits until [flow] emits a value satisfying [predicate] and returns it.
     *
     * Fails with [what] and the last value seen when [timeout] passes first. The
     * flow is collected while waiting, so `stateIn(WhileSubscribed)` flows compute.
     */
    suspend fun <T> awaitState(flow: Flow<T>, what: String, predicate: (T) -> Boolean): T {
        var last: Any? = null
        try {
            return withTimeout(timeout) { flow.onEach { last = it }.first(predicate) }
        } catch (expired: TimeoutCancellationException) {
            throw AssertionError(
                "${stamp()}: $what was not reached within $timeout. Last value: $last",
                expired,
            )
        }
    }

    /**
     * Collects [flows] while [block] runs, the way a screen on display does. Use it
     * around any block that reads a ViewModel's `.value` or calls its actions.
     */
    suspend fun <T> subscribed(vararg flows: Flow<*>, block: suspend () -> T): T {
        val collectors: List<Job> = flows.map { flow -> screen.launch { flow.collect { } } }
        try {
            return block()
        } finally {
            collectors.forEach { it.cancelAndJoin() }
        }
    }

    override fun close() {
        runBlocking {
            withTimeout(10.seconds) {
                withContext(mainDispatcher) { store.clear() }
                screen.coroutineContext[Job]?.cancelAndJoin()
                modelJobs.forEach { it.cancelAndJoin() }
            }
        }
        container.close()
        mainThread.submit {}.get()
        Dispatchers.resetMain()
        mainThread.shutdownNow()
        java.util.TimeZone.setDefault(previousDefaultZone)
    }

    private inline fun rollingDay(move: () -> Unit) {
        val before = clock.today
        move()
        if (clock.today != before) {
            store.clear()
            store = ViewModelStore()
        }
    }

    private fun Int.pad() = toString().padStart(2, '0')

    private fun germanWeekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mo"
        DayOfWeek.TUESDAY -> "Di"
        DayOfWeek.WEDNESDAY -> "Mi"
        DayOfWeek.THURSDAY -> "Do"
        DayOfWeek.FRIDAY -> "Fr"
        DayOfWeek.SATURDAY -> "Sa"
        DayOfWeek.SUNDAY -> "So"
    }
}
