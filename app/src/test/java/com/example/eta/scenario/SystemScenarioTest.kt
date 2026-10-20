package com.example.eta.scenario

import android.app.Application
import android.net.Uri
import androidx.room3.Room
import com.example.eta.data.backup.BackupResult
import com.example.eta.data.backup.BackupService
import com.example.eta.data.local.EtaDatabase
import com.example.eta.domain.setup.SETUP_ID
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import com.example.eta.domain.tutorial.TutorialId
import com.example.eta.ui.dashboard.DashboardViewModel
import com.example.eta.ui.root.RootDestination
import com.example.eta.ui.root.RootViewModel
import com.example.eta.ui.root.dashboardViewModelFactory
import com.example.eta.ui.root.rootViewModelFactory
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The app around the plan: the first start, the practice day, the new day, the
 * backup and the reset. Disk-backed test databases are explicitly closed before
 * restoration and reopening, just as an app restart releases its old connections.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SystemScenarioTest {

    @Test
    fun `without a setup the app opens the setup, and the setup opens the dashboard`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        val root = viewModel<RootViewModel>(rootViewModelFactory(container))
        subscribed(root.destination) {
            awaitState(root.destination, "the setup screen") { it == RootDestination.Setup }
            step("Einrichtung abschließen") { completeSetup(SampleData.lenaSetup(clock.now())) }
            awaitState(root.destination, "the dashboard") { it == RootDestination.Dashboard }
        }
    }

    @Test
    fun `the tutorial's practice day writes nothing to the real database and arms no alarm`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        val persisted = openDatabase()
        try {
            val sentinel = Item.newQuickTodo("Private original task", clock.now())
            persisted.itemDao().upsert(sentinel)
            step("Übungstag im Tutorial anlegen und Alarme neu stellen") {
                checkNotNull(container.tutorialSeed).lay(TutorialId.QUICKSTART)
                container.planningAlarmCoordinator.rescheduleAll()
                container.taskStartCoordinator.reschedule()
                container.reminderCoordinator.reschedule()
            }
            assertEquals(
                listOf(sentinel),
                persisted.itemDao().observeByStage(Stage.COLLECTION).first(),
            )
            assertNull("the tutorial must not configure the real app", persisted.setupDao().find(SETUP_ID))
            assertTrue("no practice alarm reaches the system", armedAlarms().isEmpty())
        } finally {
            persisted.close()
        }
    }

    @Test
    fun `a new day rebuilds the screens for the new day`() = scenario(start = "2026-10-18T09:00") {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val sunday = viewModel<DashboardViewModel>(dashboardViewModelFactory(container))
        subscribed(sunday.uiState) {
            awaitState(sunday.uiState, "the dashboard of Sunday") { it.today == LocalDate(2026, 10, 18) }
        }

        nextDay(LocalTime(8, 0))
        val monday = viewModel<DashboardViewModel>(dashboardViewModelFactory(container))
        assertNotSame("the next day builds a new screen", sunday, monday)
        subscribed(monday.uiState) {
            awaitState(monday.uiState, "the dashboard of Monday") { it.today == LocalDate(2026, 10, 19) }
        }
    }

    @Test
    fun `a backup restores the database it was taken from, and not what came after`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        val context = RuntimeEnvironment.getApplication()
        val backup = BackupService(context)
        val file = File.createTempFile("eta-backup", ".zip")
        val target = Uri.fromFile(file)
        try {
            val original = Item.newQuickTodo("Brief schreiben", clock.now())
            val source = openDatabase()
            try {
                source.itemDao().upsert(original)
            } finally {
                source.close()
            }
            assertTrue("the backup is written", backup.export(target) is BackupResult.Ok)
            assertTrue("the backup contains the database", ZipFile(file).use { it.getEntry(EtaDatabase.NAME) != null })

            val changed = openDatabase()
            try {
                changed.itemDao().upsert(Item.newQuickTodo("Zweite Karte", clock.now()))
            } finally {
                changed.close()
            }
            assertTrue("the backup is restored", backup.import(target) is BackupResult.Ok)
            val restarted = openDatabase()
            try {
                assertEquals(
                    listOf(original),
                    restarted.itemDao().observeByStage(Stage.COLLECTION).first(),
                )
            } finally {
                restarted.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `reset everything clears the books and the local stores, the declined calendar series included`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung, Karte, abgelehnte Kalenderserie") {
            completeSetup(SampleData.lenaSetup(clock.now()))
            quickAdd("Steuererklärung")
            container.declinedSeriesStore.decline(calendarId = "primary", seriesId = "series-analysis")
        }
        val root = viewModel<RootViewModel>(rootViewModelFactory(container))
        subscribed(root.destination) {
            awaitState(root.destination, "the dashboard") { it == RootDestination.Dashboard }
            // The reset runs on the ViewModel's scope; the step's invariant check
            // must see the database after it, not halfway through it.
            step("Alles zurücksetzen") {
                root.resetEverything()
                awaitState(root.destination, "the setup screen again") { it == RootDestination.Setup }
            }
        }

        assertNull("the setup is gone", container.setupRepository.find())
        assertTrue("no card is left", Stage.entries.all { items(it).isEmpty() })
        assertEquals(0.0, balance(), TOLERANCE)
        assertFalse(
            "the declined calendar series is forgotten",
            container.declinedSeriesStore.isDeclined(calendarId = "primary", seriesId = "series-analysis"),
        )
    }

    private fun openDatabase(): EtaDatabase = Room.databaseBuilder(
        RuntimeEnvironment.getApplication(),
        EtaDatabase::class.java,
        EtaDatabase.NAME,
    ).build()

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
