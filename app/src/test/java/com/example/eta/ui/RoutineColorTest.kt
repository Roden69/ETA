package com.example.eta.ui

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.ui.theme.EtaRedColors
import com.example.eta.ui.theme.EtaRedDarkColors
import com.example.eta.ui.theme.LegacyDarkColors
import com.example.eta.ui.theme.LegacyLightColors
import com.example.eta.ui.theme.assignRoutineColors
import com.example.eta.ui.theme.ROUTINE_COLORS
import com.example.eta.ui.theme.isNameColoured
import com.example.eta.ui.theme.routineColorArgb
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineColorTest {
    private val now = Instant.parse("2026-10-19T08:00:00Z")

    private fun standing(name: String, category: Category? = null, role: ItemRole? = null) = Item.newRecurring(
        name = name,
        category = category,
        recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.MONDAY),
        startTime = LocalTime(9, 0),
        estimatedDuration = 1.hours,
        now = now,
        role = role,
    )

    @Test
    fun `a standing task without category is coloured by its name instead of grey`() {
        assertTrue(standing("Morgenroutine").isNameColoured())
        // Same name on another weekday: the very same colour, so the sketch and the list agree.
        assertEquals(
            routineColorArgb(standing("Morgenroutine").normalizedName),
            routineColorArgb(standing("  morgenROUTINE ").normalizedName),
        )
    }

    @Test
    fun `cards that have a colour source of their own keep it`() {
        assertFalse(standing("Sport", category = Category.FOKUS).isNameColoured())
        assertFalse(standing("Hausputz").copy(color = 0xFF00AA00.toInt()).isNameColoured())
        assertFalse(standing("Frei", role = ItemRole.FREE_TIME).isNameColoured())
        // A note without its form yet is "unsorted" and stays muted.
        val note = Item.newTodo("Idee", Category.FOKUS, Priority.MUST, null, 1.hours, now).copy(category = null)
        assertFalse(note.isNameColoured())
    }

    @Test
    fun `routines of one week never share a colour while the palette lasts`() {
        // The four routines of the owner's screenshot (#51), plus the rest of a week.
        val names = listOf(
            "bettfertig machen", "morgenroutine", "arbeiten", "pilates x yoga",
            "uni nachbereiten", "hausputz", "ra & nlr lernen", "sport",
        )
        val colours = assignRoutineColors(names)
        assertEquals(names.toSet(), colours.keys)
        assertEquals(names.size, colours.values.toSet().size)
        assertTrue(colours.values.all { it in ROUTINE_COLORS })
    }

    @Test
    fun `the assignment depends on the set of names and not on the order they arrive in`() {
        val names = listOf("hausputz", "morgenroutine", "arbeiten", "pilates x yoga")
        assertEquals(assignRoutineColors(names), assignRoutineColors(names.reversed()))
    }

    @Test
    fun `more routines than colours still get a palette colour each`() {
        val names = (1..ROUTINE_COLORS.size + 3).map { "routine $it" }
        val colours = assignRoutineColors(names)
        assertEquals(names.size, colours.size)
        assertTrue(colours.values.all { it in ROUTINE_COLORS })
        // Every colour is used before any is used twice.
        assertEquals(ROUTINE_COLORS.toSet(), colours.values.toSet())
    }

    @Test
    fun `the routine palette is distinct and keeps clear of the category colours`() {
        assertEquals(ROUTINE_COLORS.size, ROUTINE_COLORS.toSet().size)
        val reserved = listOf(LegacyLightColors, LegacyDarkColors, EtaRedColors, EtaRedDarkColors)
            .flatMap { listOf(it.fokus, it.nebenbei, it.achtsam, it.calendar, it.danger) }
            .map { it.toArgbInt() }
        assertTrue(ROUTINE_COLORS.none { it in reserved })
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
        fun channel(value: Float) = (value * 255f + 0.5f).toInt()
        return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }
}
