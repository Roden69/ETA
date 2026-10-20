package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.weekStageOf
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekIntakeTest {

    private val now = Instant.parse("2026-10-18T10:00:00Z")

    @Test
    fun `a ToDo without its answers is kept in the Sammelliste rather than taken into the week`() {
        assertEquals(Stage.COLLECTION, weekStageOf(Item.newQuickTodo("Milch", now)))
    }

    @Test
    fun `a ToDo with category, priority and duration goes into the week`() {
        val answered = Item.newTodo(
            name = "Wäsche",
            category = Category.NEBENBEI,
            priority = Priority.WANT,
            targetDate = null,
            estimatedDuration = 1.hours,
            now = now,
        )
        assertEquals(Stage.WEEK, weekStageOf(answered))
    }

    @Test
    fun `answering a bare ToDo is what moves it into the week`() {
        val bare = Item.newQuickTodo("Milch", now)
        val answered = bare.concretized(
            category = Category.FOKUS,
            priority = Priority.MUST,
            targetDate = null,
            estimatedDuration = 30.minutes,
        )
        assertEquals(Stage.COLLECTION, weekStageOf(bare))
        assertEquals(Stage.WEEK, weekStageOf(answered))
    }
}
