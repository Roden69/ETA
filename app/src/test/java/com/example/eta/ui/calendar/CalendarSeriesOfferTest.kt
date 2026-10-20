package com.example.eta.ui.calendar

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.example.eta.data.calendar.RemoteEvent
import com.example.eta.data.calendar.RemoteSeries
import com.example.eta.scenario.SampleData
import com.example.eta.ui.EtaUiHost
import com.example.eta.ui.FixedCalendarApi
import com.example.eta.ui.UiWorld
import com.example.eta.ui.UiWorldRule
import com.example.eta.ui.awaitGone
import com.example.eta.ui.awaitText
import com.example.eta.ui.tap
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.root.calendarViewModelFactory
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarSeriesOfferTest {
    private val worlds = UiWorldRule()
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(worlds).around(compose)

    @Test
    fun `one offer appears per series and keeping it single survives a new ViewModel`() {
        val firstDay = LocalDate(2026, 10, 22)
        val events = listOf(0, 7, 14).map { offset ->
            RemoteEvent(
                calendarId = FixedCalendarApi.CALENDAR_ID,
                eventId = "series-$offset",
                title = "Teamrunde",
                date = firstDay.plus(DatePeriod(days = offset)),
                start = LocalTime(10, 0),
                duration = 1.hours,
                allDay = false,
                updatedAt = null,
                seriesId = "team-series",
            )
        }
        val api = FixedCalendarApi(
            events = events,
            series = mapOf("team-series" to RemoteSeries(
                recurrence = listOf("RRULE:FREQ=WEEKLY;BYDAY=TH"),
                start = firstDay,
            )),
        )
        val world = UiWorld("2026-10-21T09:00", calendarApi = api).also { worlds.world = it }
        world.markIntroSeen()
        world.completeSetup(SampleData.lenaSetup(world.clock.now()))
        runBlocking { world.container.calendarSyncService.connect() }

        compose.setContent {
            val generation = remember { mutableIntStateOf(0) }
            EtaTheme(choice = DesignChoice()) {
                Column {
                    BasicText("Neue ViewModel-Instanz", Modifier.clickable { generation.intValue++ })
                    key(generation.intValue) {
                        EtaUiHost(world, world.locals()) {
                            CalendarStepScreen(
                                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    key = "calendar-${generation.intValue}",
                                    factory = calendarViewModelFactory(
                                        world.container,
                                        LocalDate(2026, 10, 22),
                                        LocalDate(2026, 11, 5),
                                    ),
                                ),
                                onDone = {},
                            )
                        }
                    }
                }
            }
        }

        compose.awaitText("Teamrunde")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("wiederholt sich jeden Do 10:00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, compose.onAllNodesWithText("Einzeln lassen").fetchSemanticsNodes().size)
        compose.tap("Einzeln lassen")
        compose.awaitGone("Einzeln lassen")

        compose.onAllNodesWithText("Neue ViewModel-Instanz")[0].performClick()
        compose.awaitText("Termine")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Einzeln lassen").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(0, compose.onAllNodesWithText("Einzeln lassen").fetchSemanticsNodes().size)
    }
}
