package com.example.eta.data.local

import android.app.Instrumentation
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EtaDatabaseMigrationTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val schemaAssets = "com.example.eta.data.local.EtaDatabase"
    private val schemaDirectory = File("schemas/$schemaAssets")
        .takeIf { it.isDirectory }
        ?: File("app/schemas/$schemaAssets")
    private val versions = schemaDirectory.listFiles()
        .orEmpty()
        .mapNotNull { Regex("([0-9]+)\\.json").matchEntire(it.name)?.groupValues?.get(1)?.toInt() }
        .sorted()
    private val firstVersion get() = versions.first()

    // KSP exports the schema of the current version on every build, so the
    // newest file is the version the app opens.
    private val latestVersion get() = versions.last()
    private val testDatabase = File(instrumentation.targetContext.cacheDir, "eta-migration-test.db")

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = testDatabase,
        driver = AndroidSQLiteDriver(),
        databaseClass = EtaDatabase::class,
    )

    private fun addSchemaAssets() {
        // MigrationTestHelper reads schemas through Android assets; package them only in a
        // temporary test archive instead of adding them to the release APK.
        val schemaArchive = File(testDatabase.parentFile, "eta-test-schemas.apk")
        ZipOutputStream(schemaArchive.outputStream()).use { archive ->
            schemaDirectory.listFiles().orEmpty().forEach { schema ->
                archive.putNextEntry(ZipEntry("assets/$schemaAssets/${schema.name}"))
                schema.inputStream().use { it.copyTo(archive) }
                archive.closeEntry()
            }
        }
        val assets = instrumentation.context.assets
        val addAssetPath = assets.javaClass.getMethod("addAssetPath", String::class.java)
        val cookie = addAssetPath.invoke(assets, schemaArchive.absolutePath) as Int
        assertTrue("Could not add exported schema assets", cookie != 0)
    }

    @Test
    fun `every migration matches its exported schema`() = runBlocking {
        assertTrue("No exported schema files found in $schemaDirectory", versions.isNotEmpty())
        assertEquals("Schema range must start at version 1", 1, firstVersion)
        assertEquals("There must be one schema for every version", (firstVersion..latestVersion).toList(), versions)

        addSchemaAssets()
        instrumentation.targetContext.deleteDatabase(testDatabase.name)
        var connection = migrationHelper.createDatabase(firstVersion)
        connection.close()
        for (version in (firstVersion + 1)..latestVersion) {
            connection = try {
                migrationHelper.runMigrationsAndValidate(
                    version,
                    ETA_DATABASE_MIGRATIONS.toList(),
                )
            } catch (failure: Exception) {
                throw AssertionError("Migration ${version - 1} -> $version failed Room validation", failure)
            }
            println("Room validated migration ${version - 1} -> $version")
            connection.close()
        }
    }

    @Test
    fun `early item data and later defaults survive all migrations`() = runBlocking {
        addSchemaAssets()
        instrumentation.targetContext.deleteDatabase(testDatabase.name)
        val connection = migrationHelper.createDatabase(firstVersion)
        connection.execSQL(
            """INSERT INTO items (id, type, name, normalizedName, stage, createdAt, updatedAt)
                VALUES ('migration-test-item', 'TODO', 'Migration item', 'migration item', 'COLLECTION', 1, 1)""",
        )
        connection.close()

        val migrated = migrationHelper.runMigrationsAndValidate(latestVersion, ETA_DATABASE_MIGRATIONS.toList())
        migrated.prepare("SELECT id, growthDynamic FROM items WHERE id = 'migration-test-item'").use { statement ->
            assertTrue("The version-1 item must remain present", statement.step())
            assertEquals("migration-test-item", statement.getText(0))
            assertEquals("New growthDynamic rows default to false", 0L, statement.getLong(1))
            assertTrue("Only one matching row is expected", !statement.step())
        }
        migrated.close()
    }

    @Test
    fun `fork version 28 preserves corrected nights and existing tasks on upgrade`() = runBlocking {
        addSchemaAssets()
        instrumentation.targetContext.deleteDatabase(testDatabase.name)
        val connection = migrationHelper.createDatabase(28)
        // The fork shipped a different version 28: night corrections, not flexibility switches.
        connection.execSQL("ALTER TABLE user_setup DROP COLUMN allowPrioritySkip")
        connection.execSQL("ALTER TABLE user_setup DROP COLUMN allowTodoBesideWeek")
        connection.execSQL("ALTER TABLE user_setup ADD COLUMN nightOverrides TEXT NOT NULL DEFAULT ''")
        connection.execSQL(
            """INSERT INTO user_setup (
                id, bedPrepTime, sleepTime, wakeTime, wakeAlarm, weekendDays, morningDuration,
                meals, freeTime, socialTimePerWeek, work, dailyPlanningTime, weeklyPlanningDay,
                weeklyPlanningTime, cancellationPenaltyPerHour, unplannedPenalty,
                unplannedPenaltyPerHour, stillActiveReminder, stillActivePerDay, taskAnnouncement,
                speakNotes, pointsSystem, growthTasks, contracts, rewards, completedAt, updatedAt,
                nightOverrides
            ) VALUES (
                0, 82800, 82800, 25200, 1, 'SATURDAY,SUNDAY', 0,
                'D:', '25200,0', 0, 'N', 72000, 'SUNDAY',
                64800, 1.0, 1, 1.5, 0, 3, 'SOUND',
                0, 1, 0, 0, 0, 1, 2, 'WEDNESDAY=1800,1800,30600'
            )""",
        )
        connection.execSQL(
            """INSERT INTO items (id, type, name, normalizedName, stage, createdAt, updatedAt,
                endSound, growthDynamic, growthEvery, growthProgress, quantityEvery,
                quantityProgress, routineMode)
                VALUES ('fork-task', 'TODO', 'Keep me', 'keep me', 'COLLECTION', 1, 2,
                    0, 0, 1, 0, 1, 0, 0)""",
        )
        connection.close()

        val migrated = migrationHelper.runMigrationsAndValidate(latestVersion, ETA_DATABASE_MIGRATIONS.toList())
        migrated.prepare(
            "SELECT nightOverrides, wakeTime, wakeAlarm, allowPrioritySkip, allowTodoBesideWeek " +
                "FROM user_setup WHERE id = 0",
        ).use { statement ->
            assertTrue(statement.step())
            assertEquals("WEDNESDAY=1800,1800,30600", statement.getText(0))
            assertEquals(25200L, statement.getLong(1))
            assertEquals(1L, statement.getLong(2))
            assertEquals(0L, statement.getLong(3))
            assertEquals(0L, statement.getLong(4))
        }
        migrated.prepare("SELECT name FROM items WHERE id = 'fork-task'").use { statement ->
            assertTrue(statement.step())
            assertEquals("Keep me", statement.getText(0))
        }
        migrated.close()
    }
}
