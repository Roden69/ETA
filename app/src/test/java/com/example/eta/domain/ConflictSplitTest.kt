package com.example.eta.domain

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.startMinute
import com.example.eta.domain.planning.splitAround
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Splitting an activity around an appointment that runs through its middle. */
class ConflictSplitTest {

    private val now = Instant.parse("2026-10-18T06:00:00Z")
    private val date = LocalDate(2026, 10, 19)

    private fun block(
        start: LocalTime,
        duration: Duration,
        travelBefore: Duration? = null,
        returnAfter: Duration? = null,
        breakAfter: Duration? = null,
    ) = PlannedBlock(
        id = "a",
        itemId = "item-a",
        date = date,
        start = start,
        plannedDuration = duration,
        origin = BlockOrigin.RECURRING,
        travelBefore = travelBefore,
        returnAfter = returnAfter,
        breakAfter = breakAfter,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `an appointment in the middle leaves a front part and a back part of the rest`() {
        // 09:00–13:00 around a 10:00–11:00 appointment: 1h in front, 2h behind.
        val activity = block(LocalTime(9, 0), 4.hours)
        val appointment = block(LocalTime(10, 0), 1.hours)

        val split = activity.splitAround(appointment)
        assertEquals(60, split!!.front.endMinute() - split.front.startMinute())
        assertEquals(2.hours, split.back)
    }

    @Test
    fun `the back part is measured from the end of the appointment's container`() {
        // An appointment with 15 minutes of travel holds 09:45 already; the back
        // part starts only after its break too.
        val activity = block(LocalTime(9, 0), 4.hours)
        val appointment = block(LocalTime(10, 0), 1.hours, travelBefore = 15.minutes, breakAfter = 15.minutes)

        val split = activity.splitAround(appointment)!!
        // Ends 13:00, the appointment's container ends 11:15: 1h45 behind it.
        assertEquals(105.minutes, split.back)
        // The front ends where the appointment's container begins, 09:45.
        assertEquals(45, split.front.endMinute() - split.front.startMinute())
    }

    @Test
    fun `a remainder under the minimum block length is not a split`() {
        // 09:00–11:05 around 10:00–11:00: five minutes behind is no activity.
        val activity = block(LocalTime(9, 0), 2.hours + 5.minutes)
        val appointment = block(LocalTime(10, 0), 1.hours)
        assertNull(activity.splitAround(appointment))
    }

    @Test
    fun `a front part under the minimum block length is not a split`() {
        // Starts at 09:55, five minutes before the appointment: nothing worth keeping in front.
        val activity = block(LocalTime(9, 55), 3.hours)
        val appointment = block(LocalTime(10, 0), 1.hours)
        assertNull(activity.splitAround(appointment))
    }

    @Test
    fun `an activity that starts inside the appointment has no front to split from`() {
        val activity = block(LocalTime(10, 30), 3.hours)
        val appointment = block(LocalTime(10, 0), 1.hours)
        assertNull(activity.splitAround(appointment))
    }
}
