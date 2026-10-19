package com.example.eta.domain.setup

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The optional features the app suggests once the minimal setup is behind the user.
 *
 * Only features that are off by default and are not part of the basic routine.
 * Flexibility switches are left out on purpose: they loosen the rules, and nobody
 * should be nudged towards that.
 */
enum class SuggestedFeature(val label: String, val summary: String) {
    GROWTH_TASKS(
        label = "Timer-Tasks",
        summary = "Aufgaben, bei denen Zeit zählt – und die mit einer Zielzeit wachsen.",
    ),
    CONTRACTS(
        label = "Verträge",
        summary = "Versprechen an dich selbst, mit Unterschrift und täglicher Frage.",
    ),
    REWARDS(
        label = "Belohn-o-mat",
        summary = "Langfristige Belohnungen, die sich Abend für Abend füllen.",
    ),
    NOTES(
        label = "Notiz-Merger",
        summary = "Notizen sammeln und zu einer Notiz zusammenführen.",
    ),
}

/** Days after the setup before the first suggestion, and between two suggestions. */
const val FEATURE_SUGGESTION_GAP_DAYS = 7

fun UserSetup.isOn(feature: SuggestedFeature): Boolean = when (feature) {
    SuggestedFeature.GROWTH_TASKS -> growthTasks
    SuggestedFeature.CONTRACTS -> contracts
    SuggestedFeature.REWARDS -> rewards
    SuggestedFeature.NOTES -> notes
}

fun UserSetup.withFeature(feature: SuggestedFeature): UserSetup = when (feature) {
    SuggestedFeature.GROWTH_TASKS -> copy(growthTasks = true)
    SuggestedFeature.CONTRACTS -> copy(contracts = true)
    SuggestedFeature.REWARDS -> copy(rewards = true)
    SuggestedFeature.NOTES -> copy(notes = true)
}

/**
 * The feature to suggest today, or null when nothing is due.
 *
 * Due only once [FEATURE_SUGGESTION_GAP_DAYS] have passed since the setup was
 * completed, and then at most one suggestion per [FEATURE_SUGGESTION_GAP_DAYS]
 * days: [lastOfferedOn] is the day of the last answer, "Später" included. Of the
 * features still off, the first in declaration order that was not answered
 * before is the one offered; a feature that is already on or was answered
 * ("Ausprobieren" or "Nicht mehr vorschlagen") never comes back.
 *
 * Nothing is switched on here; that is the user's tap.
 */
fun dueFeatureSuggestion(
    setup: UserSetup,
    today: LocalDate,
    timeZone: TimeZone,
    answered: Set<SuggestedFeature>,
    lastOfferedOn: LocalDate?,
): SuggestedFeature? {
    val gap = DatePeriod(days = FEATURE_SUGGESTION_GAP_DAYS)
    val setupDay = setup.completedAt.toLocalDateTime(timeZone).date
    if (today < setupDay.plus(gap)) return null
    if (lastOfferedOn != null && today < lastOfferedOn.plus(gap)) return null
    return SuggestedFeature.entries.firstOrNull { feature ->
        !setup.isOn(feature) && feature !in answered
    }
}
