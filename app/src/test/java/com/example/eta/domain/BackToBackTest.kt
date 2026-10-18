package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.BackToBack
import com.example.eta.domain.planning.backToBackPairs
import com.example.eta.domain.planning.checkBreaks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hint for activities that run into each other without a gap (step 47, #41). */
class BackToBackTest {

    private val now = Instant.parse("2026-10-13T06:00:00Z")
    private val day = LocalDate(2026, 10, 13)

    private fun entry(
        name: String,
        start: LocalTime,
        duration: Duration,
        travelBefore: Duration? = null,
        breakAfter: Duration? = null,
        role: ItemRole? = null,
        discardedAt: Instant? = null,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = name,
            itemId = "item-$name",
            date = day,
            start = start,
            plannedDuration = duration,
            travelBefore = travelBefore,
            breakAfter = breakAfter,
            origin = BlockOrigin.DRAGGED,
            discardedAt = discardedAt,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$name",
            type = ItemType.TODO,
            name = name,
            stage = Stage.DAY,
            category = Category.FOKUS,
            role = role,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private val nine = LocalTime(9, 0)
    private val ten = LocalTime(10, 0)

    @Test
    fun `two tasks that touch are named with the time they meet`() {
        val pairs = backToBackPairs(
            listOf(entry("Arbeit", nine, 1.hours), entry("Sport", ten, 1.hours)),
        )

        assertEquals(listOf(BackToBack("Arbeit", "Sport", atMinute = 600)), pairs)
    }

    @Test
    fun `a gap of any length between two tasks is not back-to-back`() {
        val pairs = backToBackPairs(
            listOf(entry("Arbeit", nine, 1.hours), entry("Sport", LocalTime(10, 5), 1.hours)),
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `a break planned after the first task answers the question`() {
        val pairs = backToBackPairs(
            listOf(
                entry("Arbeit", nine, 1.hours, breakAfter = 10.minutes),
                entry("Sport", LocalTime(10, 10), 1.hours),
            ),
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `a journey into the next task is planned time and still back-to-back`() {
        // Sport starts at 10:10 but its journey begins at 10:00, straight after Arbeit.
        val pairs = backToBackPairs(
            listOf(
                entry("Arbeit", nine, 1.hours),
                entry("Sport", LocalTime(10, 10), 1.hours, travelBefore = 10.minutes),
            ),
        )

        assertEquals(listOf(BackToBack("Arbeit", "Sport", atMinute = 600)), pairs)
    }

    @Test
    fun `a rest or a neutral block between two tasks breaks the chain`() {
        val withBreak = backToBackPairs(
            listOf(
                entry("Arbeit", nine, 1.hours),
                entry("Pause", ten, 15.minutes, role = ItemRole.BREAK),
                entry("Sport", LocalTime(10, 15), 1.hours),
            ),
        )
        val withNeutral = backToBackPairs(
            listOf(
                entry("Arbeit", nine, 1.hours),
                entry("Bettfertig", ten, 30.minutes, role = ItemRole.BED_PREP),
                entry("Sport", LocalTime(10, 30), 1.hours),
            ),
        )

        assertTrue(withBreak.isEmpty())
        assertTrue(withNeutral.isEmpty())
    }

    @Test
    fun `a called-off block is not part of the day`() {
        val pairs = backToBackPairs(
            listOf(
                entry("Arbeit", nine, 1.hours),
                entry("Sport", ten, 1.hours, discardedAt = now),
            ),
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `overlapping tasks are left to the overlap warning`() {
        val pairs = backToBackPairs(
            listOf(entry("Arbeit", nine, 2.hours), entry("Sport", ten, 1.hours)),
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `a chain of three gives two pairs in the order of the day`() {
        val pairs = backToBackPairs(
            listOf(
                entry("Sport", LocalTime(11, 0), 1.hours),
                entry("Arbeit", nine, 1.hours),
                entry("Lesen", ten, 1.hours),
            ),
        )

        assertEquals(
            listOf(
                BackToBack("Arbeit", "Lesen", atMinute = 600),
                BackToBack("Lesen", "Sport", atMinute = 660),
            ),
            pairs,
        )
    }

    @Test
    fun `the Pausenchecker carries the pairs and keeps its own findings`() {
        val findings = checkBreaks(
            listOf(entry("Arbeit", nine, 1.hours), entry("Sport", ten, 1.hours)),
            perTwoHours = 15.minutes,
            maxStretch = 3.hours,
        )

        assertEquals(listOf(BackToBack("Arbeit", "Sport", atMinute = 600)), findings.backToBack)
        assertEquals(120, findings.taskMinutes)
        assertEquals(15, findings.neededMinutes)
    }
}
