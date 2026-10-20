package com.example.eta.scenario

import com.example.eta.domain.setup.UserSetup
import com.example.eta.ui.root.settingsViewModelFactory
import com.example.eta.ui.settings.SettingsMessage
import com.example.eta.ui.settings.SettingsViewModel
import com.example.eta.domain.subtask.SubtaskDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The settings page, for the duration of [body]: the same ViewModel the page uses,
 * so "Einstellungen speichern" runs the real save, including the re-laying of the
 * standing blocks.
 */
suspend fun Scenario.settings(body: suspend SettingsDriver.() -> Unit) {
    val vm = viewModel<SettingsViewModel>(settingsViewModelFactory(container))
    subscribed(vm.draft, vm.morningSteps) {
        awaitState(vm.draft, "the settings page loaded") { it != null }
        SettingsDriver(this@settings, vm).body()
    }
}

/** The actions of the settings page. */
class SettingsDriver internal constructor(
    private val scenario: Scenario,
    private val vm: SettingsViewModel,
) {
    /** Edits the draft with [transform] and presses "Einstellungen speichern". */
    suspend fun change(transform: (UserSetup) -> UserSetup) {
        // On the main thread, as a tap is; see [changeSettings].
        withContext(Dispatchers.Main) {
            vm.dismissMessage()
            vm.update(transform)
            vm.save()
        }
        scenario.awaitState(vm.message, "the settings saved") { it is SettingsMessage.Saved }
        vm.dismissMessage()
    }

    /** Replaces the morning routine with [steps] and waits until the page shows them. */
    suspend fun morningSteps(steps: List<SubtaskDraft>) {
        vm.saveMorningSteps(steps)
        scenario.awaitState(vm.morningSteps, "the morning routine saved") { saved ->
            saved.map { it.name } == steps.map { it.name }
        }
    }

    /** A switch of the advanced features is written at once, not on save. */
    suspend fun feature(transform: (UserSetup) -> UserSetup) {
        val expected = transform(checkNotNull(vm.draft.value))
        vm.setFeature(transform)
        scenario.awaitState(scenario.container.setupRepository.observe(), "the feature switch saved") {
            it?.contracts == expected.contracts && it?.growthTasks == expected.growthTasks
        }
    }
}

