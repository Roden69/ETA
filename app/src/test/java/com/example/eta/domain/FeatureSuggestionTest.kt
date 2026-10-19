package com.example.eta.domain

import com.example.eta.domain.setup.SuggestedFeature
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.dueFeatureSuggestion
import com.example.eta.domain.setup.withFeature
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureSuggestionTest {
    private val utc = TimeZone.UTC
    private val completed = Instant.parse("2026-10-01T10:00:00Z")
    private val setup = UserSetup.draft(completed)

    @Test
    fun `nothing is suggested before seven days have passed since the setup`() {
        assertNull(due(setup, LocalDate(2026, 10, 7)))
        assertEquals(SuggestedFeature.GROWTH_TASKS, due(setup, LocalDate(2026, 10, 8)))
    }

    @Test
    fun `the seven days count from the local day of completion`() {
        // 23:30 UTC on 1 October is already 2 October in Berlin: the clock starts there.
        val late = UserSetup.draft(Instant.parse("2026-10-01T23:30:00Z"))
        val berlin = TimeZone.of("Europe/Berlin")

        assertNull(dueFeatureSuggestion(late, LocalDate(2026, 10, 8), berlin, emptySet(), null))
        assertEquals(
            SuggestedFeature.GROWTH_TASKS,
            dueFeatureSuggestion(late, LocalDate(2026, 10, 9), berlin, emptySet(), null),
        )
    }

    @Test
    fun `after an offer the next one waits another seven days`() {
        val lastOffer = LocalDate(2026, 10, 8)

        assertNull(due(setup, LocalDate(2026, 10, 14), lastOfferedOn = lastOffer))
        assertEquals(
            SuggestedFeature.GROWTH_TASKS,
            due(setup, LocalDate(2026, 10, 15), lastOfferedOn = lastOffer),
        )
    }

    @Test
    fun `a feature that is already on is skipped`() {
        val withGrowth = setup.withFeature(SuggestedFeature.GROWTH_TASKS)

        assertEquals(SuggestedFeature.CONTRACTS, due(withGrowth, LocalDate(2026, 10, 8)))
    }

    @Test
    fun `an answered feature is never suggested again`() {
        val answered = setOf(SuggestedFeature.GROWTH_TASKS)

        assertEquals(SuggestedFeature.CONTRACTS, due(setup, LocalDate(2026, 10, 8), answered = answered))
    }

    @Test
    fun `features are offered in declaration order and then nothing is due`() {
        val day = LocalDate(2026, 10, 8)
        var answered = emptySet<SuggestedFeature>()
        val offered = mutableListOf<SuggestedFeature>()
        repeat(SuggestedFeature.entries.size) {
            val next = due(setup, day, answered = answered)!!
            offered += next
            answered = answered + next
        }

        assertEquals(SuggestedFeature.entries, offered)
        assertNull(due(setup, day, answered = answered))
    }

    @Test
    fun `a feature switched on by the user is not offered even if never answered`() {
        val allOn = SuggestedFeature.entries.fold(setup) { acc, feature -> acc.withFeature(feature) }

        assertNull(due(allOn, LocalDate(2026, 10, 30)))
    }

    private fun due(
        setup: UserSetup,
        today: LocalDate,
        answered: Set<SuggestedFeature> = emptySet(),
        lastOfferedOn: LocalDate? = null,
    ) = dueFeatureSuggestion(setup, today, utc, answered, lastOfferedOn)
}
