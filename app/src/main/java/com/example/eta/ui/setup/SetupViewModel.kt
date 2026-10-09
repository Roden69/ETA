package com.example.eta.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.SetupRepository
import com.example.eta.domain.setup.RoutinePlacement
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.SetupRoutine
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

class SetupViewModel(
    private val setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _draft = MutableStateFlow(RoutineSetup.draft(clock.now()))
    val draft: StateFlow<RoutineSetup> = _draft.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun toggleRoutine(routine: SetupRoutine) = _draft.update { it.toggleRoutine(routine) }

    fun addRoutine(name: String) = _draft.update { it.addRoutine(name) }

    fun removeRoutine(id: String) = _draft.update { it.removeRoutine(id) }

    fun savePlacement(placement: RoutinePlacement) = _draft.update { it.withPlacement(placement) }

    fun deletePlacement(id: String) = _draft.update { it.withoutPlacement(id) }

    fun changeSleep(start: LocalTime, duration: Duration) = _draft.update { it.withSleep(start, duration) }

    fun finish() {
        val draft = _draft.value
        if (_saving.value || draft.unscheduledRoutines.isNotEmpty()) return
        _saving.value = true
        viewModelScope.launch {
            try {
                // Publishing the setup row unlocks the root screen only after its schedule exists.
                setupRepository.complete(draft.setup, draft.recurringItems(clock.now()))
            } finally {
                _saving.value = false
            }
        }
    }
}
