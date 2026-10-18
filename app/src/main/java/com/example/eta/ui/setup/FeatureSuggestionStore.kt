package com.example.eta.ui.setup

import android.content.Context
import androidx.core.content.edit
import com.example.eta.domain.setup.SuggestedFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

data class FeatureSuggestionState(
    /** Features answered with "Ausprobieren" or "Nicht mehr vorschlagen". Never offered again. */
    val answered: Set<SuggestedFeature> = emptySet(),
    /** The day of the last answer, "Später" included; the next offer waits a week from it. */
    val lastOfferedOn: LocalDate? = null,
)

/**
 * The answers to the feature suggestions.
 *
 * In `SharedPreferences`, like the tutorial's flags: a fact about this install, not
 * part of the user's data, so it does not travel in a backup. The suggestion itself
 * is worked out by `dueFeatureSuggestion`; this only remembers what was said.
 */
class FeatureSuggestionStore(context: Context) {

    private val prefs = context.getSharedPreferences("feature_suggestions", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<FeatureSuggestionState> = _state.asStateFlow()

    /** "Später": not this feature today, and no other suggestion for a week. */
    fun postpone(today: LocalDate) = write(_state.value.copy(lastOfferedOn = today))

    /** "Ausprobieren" or "Nicht mehr vorschlagen": the feature is answered for good. */
    fun answer(feature: SuggestedFeature, today: LocalDate) =
        write(_state.value.copy(answered = _state.value.answered + feature, lastOfferedOn = today))

    private fun write(next: FeatureSuggestionState) {
        prefs.edit {
            putStringSet(KEY_ANSWERED, next.answered.map { it.name }.toSet())
            putString(KEY_LAST_OFFERED, next.lastOfferedOn?.toString())
        }
        _state.value = next
    }

    private fun read(): FeatureSuggestionState = FeatureSuggestionState(
        answered = prefs.getStringSet(KEY_ANSWERED, emptySet()).orEmpty()
            .mapNotNull { name -> SuggestedFeature.entries.firstOrNull { it.name == name } }
            .toSet(),
        lastOfferedOn = prefs.getString(KEY_LAST_OFFERED, null)?.let(LocalDate::parse),
    )

    private companion object {
        const val KEY_ANSWERED = "answered"
        const val KEY_LAST_OFFERED = "lastOfferedOn"
    }
}
