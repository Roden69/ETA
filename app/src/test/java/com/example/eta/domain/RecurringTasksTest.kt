package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.domain.recurrence.recurringTasks
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurringTasksTest {

    private val now = Instant.parse("2026-10-01T08:00:00Z")

    private fun definition(
        name: String,
        rule: RecurrenceRule,
        start: LocalTime,
        length: kotlin.time.Duration,
    ) = Item.newRecurring(
        name = name,
        category = Category.FOKUS,
        recurrenceRule = rule,
        startTime = start,
        estimatedDuration = length,
        now = now,
    )

    @Test
    fun `variants of one task are one task with each weekday's own hour and length`() {
        // The example from the issue: Mo, Mi, Fr with three different hours and lengths.
        val groups = groupRecurring(
            listOf(
                definition("Sport", RecurrenceRule.Weekly(DayOfWeek.FRIDAY), LocalTime(13, 0), 15.minutes),
                definition("Sport", RecurrenceRule.Weekly(DayOfWeek.WEDNESDAY), LocalTime(17, 0), 30.minutes),
                definition("Sport", RecurrenceRule.Weekly(DayOfWeek.MONDAY), LocalTime(14, 0), 1.hours),
            ),
        )

        val tasks = recurringTasks(groups)

        assertEquals(1, tasks.size)
        assertEquals(
            listOf(
                Triple(DayOfWeek.MONDAY, LocalTime(14, 0), 1.hours),
                Triple(DayOfWeek.WEDNESDAY, LocalTime(17, 0), 30.minutes),
                Triple(DayOfWeek.FRIDAY, LocalTime(13, 0), 15.minutes),
            ),
            tasks.single().rows.map { Triple(it.weekday, it.startTime, it.duration) },
        )
    }

    @Test
    fun `two hours on the same weekday are two rows on that day, earlier first`() {
        val groups = groupRecurring(
            listOf(
                definition("Achtsamkeit", RecurrenceRule.Weekly(DayOfWeek.MONDAY), LocalTime(18, 0), 10.minutes),
                definition("Achtsamkeit", RecurrenceRule.Weekly(DayOfWeek.MONDAY), LocalTime(8, 0), 10.minutes),
            ),
        )

        val rows = recurringTasks(groups).single().rows

        assertEquals(listOf(LocalTime(8, 0), LocalTime(18, 0)), rows.map { it.startTime })
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.MONDAY), rows.map { it.weekday })
    }

    @Test
    fun `rows run Monday to Sunday whatever order the definitions came in`() {
        val groups = groupRecurring(
            listOf(
                definition("Wäsche", RecurrenceRule.Weekly(DayOfWeek.SUNDAY), LocalTime(9, 0), 1.hours),
                definition("Wäsche", RecurrenceRule.Weekly(DayOfWeek.TUESDAY), LocalTime(9, 0), 1.hours),
            ),
        )

        assertEquals(
            listOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
            recurringTasks(groups).single().rows.map { it.weekday },
        )
    }

    @Test
    fun `tasks are ordered by their earliest hour, then by name`() {
        val groups = groupRecurring(
            listOf(
                definition("Zähne", RecurrenceRule.Weekly(DayOfWeek.MONDAY), LocalTime(7, 0), 5.minutes),
                definition("Lesen", RecurrenceRule.Weekly(DayOfWeek.MONDAY), LocalTime(21, 0), 30.minutes),
                definition("Dehnen", RecurrenceRule.Weekly(DayOfWeek.TUESDAY), LocalTime(7, 0), 10.minutes),
                definition("Dehnen", RecurrenceRule.Weekly(DayOfWeek.FRIDAY), LocalTime(20, 0), 10.minutes),
            ),
        )

        // "Dehnen" starts earliest (07:00, shared with "Zähne"), and by name it wins the tie.
        assertEquals(
            listOf("Dehnen", "Zähne", "Lesen"),
            recurringTasks(groups).map { it.name },
        )
    }

    @Test
    fun `a fortnightly variant keeps its own rows and does not merge with the weekly one`() {
        val groups = groupRecurring(
            listOf(
                definition("Hausputz", RecurrenceRule.Weekly(DayOfWeek.SATURDAY), LocalTime(10, 0), 2.hours),
                definition(
                    "Hausputz",
                    RecurrenceRule.Biweekly(DayOfWeek.SATURDAY, WeekParity.EVEN),
                    LocalTime(10, 0),
                    2.hours,
                ),
            ),
        )

        val rows = recurringTasks(groups).single().rows

        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.group.rhythm }.distinct().size)
    }
}
