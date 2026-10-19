package com.example.eta.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.SetupRepository
import com.example.eta.domain.setup.SuggestedFeature
import com.example.eta.domain.setup.dueFeatureSuggestion
import com.example.eta.domain.setup.withFeature
import com.example.eta.domain.tutorial.TutorialId
import com.example.eta.ui.tutorial.TutorialStore
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * The dashboard's optional-feature card: offers at most one feature, and only when
 * [dueFeatureSuggestion] says so. Nothing here switches a feature on except the
 * user's "Ausprobieren".
 *
 * Keyed by date in the dashboard, so a new day re-asks [dueFeatureSuggestion].
 */
class FeatureSuggestionViewModel(
    private val setupRepository: SetupRepository,
    private val store: FeatureSuggestionStore,
    private val tutorialStore: TutorialStore,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val today: LocalDate = clock.todayIn(timeZone)

    /** The feature the card offers now, or null when there is nothing to offer. */
    val suggestion: StateFlow<SuggestedFeature?> = combine(setupRepository.observe(), store.state) { setup, answers ->
        setup?.let {
            dueFeatureSuggestion(it, today, timeZone, answers.answered, answers.lastOfferedOn)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _justEnabled = MutableStateFlow<SuggestedFeature?>(null)

    /** Set by "Ausprobieren" until the card is closed: the feature is on and its tutorial is offered. */
    val justEnabled: StateFlow<SuggestedFeature?> = _justEnabled.asStateFlow()

    /** Switches the feature on through the same setter the settings use. */
    fun tryFeature(feature: SuggestedFeature) {
        viewModelScope.launch {
            setupRepository.update { it.withFeature(feature) }
            store.answer(feature, today)
            _justEnabled.value = feature
        }
    }

    fun postpone() = store.postpone(today)

    fun neverSuggest(feature: SuggestedFeature) = store.answer(feature, today)

    /** Opens the feature's tutorial, where there is one, and closes the card. */
    fun openTutorial(feature: SuggestedFeature) {
        tutorialOf(feature)?.let { tutorialStore.request(it.name) }
        _justEnabled.value = null
    }

    fun closeEnabled() {
        _justEnabled.value = null
    }

    companion object {
        /** The tutorial that explains a feature; Notiz-Merger has none. */
        fun tutorialOf(feature: SuggestedFeature): TutorialId? = when (feature) {
            SuggestedFeature.GROWTH_TASKS -> TutorialId.GROWTH
            SuggestedFeature.CONTRACTS -> TutorialId.CONTRACTS
            SuggestedFeature.REWARDS -> TutorialId.REWARDS
            SuggestedFeature.NOTES -> null
        }
    }
}
