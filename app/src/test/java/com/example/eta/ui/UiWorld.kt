package com.example.eta.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.eta.data.calendar.AuthOutcome
import com.example.eta.data.calendar.CalendarApi
import com.example.eta.data.calendar.CalendarAuth
import com.example.eta.data.calendar.RemoteCalendar
import com.example.eta.data.calendar.RemoteEvent
import com.example.eta.data.calendar.RemoteSeries
import com.example.eta.di.AppContainer
import com.example.eta.scenario.ScenarioClock
import com.example.eta.scenario.SeedCard
import com.example.eta.scenario.SetupPersona
import com.example.eta.ui.components.Features
import com.example.eta.ui.components.LocalDefaultBreakAfter
import com.example.eta.ui.components.LocalEtaClock
import com.example.eta.ui.components.LocalFeatures
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.theme.EtaTheme
import java.util.TimeZone
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone as KxTimeZone
import org.junit.runner.Description
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

/**
 * A calendar that answers from memory: one primary calendar, a fixed list of
 * occurrences, and series masters by id. The seam [AppContainer] takes in place
 * of Google's REST API.
 */
class FixedCalendarApi(
    private val events: List<RemoteEvent> = emptyList(),
    private val series: Map<String, RemoteSeries> = emptyMap(),
) : CalendarApi {
    override suspend fun calendars(token: String): List<RemoteCalendar> = listOf(PRIMARY)

    override suspend fun events(token: String, calendarId: String, from: LocalDate, to: LocalDate): List<RemoteEvent> =
        events.filter { it.calendarId == calendarId && it.date >= from && it.date <= to }

    override suspend fun seriesMaster(token: String, calendarId: String, seriesId: String): RemoteSeries =
        checkNotNull(series[seriesId]) { "no series master \"$seriesId\"" }

    companion object {
        const val CALENDAR_ID = "lena-primary"
        val PRIMARY = RemoteCalendar(
            id = CALENDAR_ID,
            displayName = "Lena",
            accountName = "lena@example.test",
            isPrimary = true,
            isForeign = false,
        )
    }
}

/** A token that is always granted: no account picker, no consent screen. */
object GrantedCalendarAuth : CalendarAuth {
    override suspend fun token(): AuthOutcome = AuthOutcome.Ok("fixed-token")

    override fun tokenFrom(data: Intent?): AuthOutcome = AuthOutcome.Ok("fixed-token")
}

/**
 * One app on a sandboxed database, with a clock the test moves by hand. The
 * container is the app's own wiring; the screens are rendered over it by
 * [EtaUiHost] or by [com.example.eta.ui.root.EtaApp] itself.
 *
 * Unlike [com.example.eta.scenario.Scenario] this does not replace
 * `Dispatchers.Main`: Compose's test rule drives the main looper, and the
 * ViewModels' `viewModelScope` runs on it, as they do in the app.
 */
class UiWorld(
    start: String,
    zone: String = "Europe/Berlin",
    calendarApi: CalendarApi = FixedCalendarApi(),
    context: Context = RuntimeEnvironment.getApplication(),
    sandbox: Boolean = true,
) : AutoCloseable {
    private val previousZone: TimeZone = TimeZone.getDefault()
    private val timeZone: KxTimeZone = KxTimeZone.of(zone)

    init {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    val clock = ScenarioClock(timeZone, LocalDateTime.parse(start))
    val container = AppContainer(
        context = context,
        sandbox = sandbox,
        clock = clock,
        timeZone = timeZone,
        alarms = false,
        calendarAuth = GrantedCalendarAuth,
        calendarApi = calendarApi,
    )

    /** The values the app's root provides, read from the stored setup, as [com.example.eta.ui.root.EtaApp] reads them. */
    fun locals(): Locals = runBlocking {
        val stored = checkNotNull(container.setupRepository.observe().first()) { "the setup is not complete" }
        Locals(
            features = Features(
                growthTasks = stored.growthTasks,
                contracts = stored.contracts,
                rewards = stored.rewards,
                statistics = stored.statistics,
                notes = stored.notes,
            ),
            pointsVisible = stored.pointsSystem,
            defaultBreakAfter = stored.defaultBreakAfter,
        )
    }

    /** Skips the first-run intro and the tutorial owed after a setup: what a returning user has. */
    fun markIntroSeen() {
        container.tutorialStore.introDone()
        container.tutorialStore.finish()
    }

    /** The setup questionnaire's last step, as [com.example.eta.scenario.completeSetup] does it. */
    fun completeSetup(persona: SetupPersona) = runBlocking {
        container.setupRepository.complete(persona.setup, persona.standingTasks)
        container.setupRepository.saveMorningSteps(persona.morningSteps)
    }

    /** Stores [cards] in the Sammelliste as the app stores them. */
    fun seedCollection(cards: List<SeedCard>) = runBlocking {
        cards.forEach { card -> container.itemRepository.add(card.item, card.subtasks, followUps = card.followUps) }
    }

    override fun close() {
        container.database.close()
        TimeZone.setDefault(previousZone)
    }

    /** What [EtaApp] provides as CompositionLocals, for a screen rendered on its own. */
    class Locals(
        val features: Features,
        val pointsVisible: Boolean,
        val defaultBreakAfter: Duration?,
    )
}

/**
 * Wraps [content] in the theme and the CompositionLocals the app provides at its
 * root, with the clock of [world]. Use it for a screen rendered on its own.
 */
@Composable
fun EtaUiHost(world: UiWorld, locals: UiWorld.Locals, content: @Composable () -> Unit) {
    EtaTheme(choice = DesignChoice()) {
        CompositionLocalProvider(
            LocalEtaClock provides world.clock,
            LocalFeatures provides locals.features,
            LocalPointsVisible provides locals.pointsVisible,
            LocalDefaultBreakAfter provides locals.defaultBreakAfter,
            content = content,
        )
    }
}

/**
 * Holds a test's [UiWorld] and closes it last. Chained outside the Compose rule, so
 * the activity and the ViewModel coroutines on it are gone before the database
 * closes; closing in `@After` would leave them querying a closed database.
 */
class UiWorldRule : TestRule {
    var world: UiWorld? = null

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                world?.close()
                world = null
            }
        }
    }
}
