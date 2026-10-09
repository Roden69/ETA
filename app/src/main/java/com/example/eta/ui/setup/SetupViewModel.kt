package com.example.eta.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.SetupRepository
import com.example.eta.domain.setup.RoutinePlacement
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.longestAwakeMinutes
import com.example.eta.domain.setup.SetupRoutine
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
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

    /** The ordinary night from the first page; it lays one provisional night per weekday. */
    fun changeWeekdayNight(sleep: LocalTime, wake: LocalTime) =
        _draft.update { if (sleep == wake) it else it.withWeekdayNight(sleep, wake) }

    fun changeWeekendNight(sleep: LocalTime, wake: LocalTime) =
        _draft.update { if (sleep == wake) it else it.withWeekendNight(sleep, wake) }

    fun changeWeekendDays(days: Set<DayOfWeek>) = _draft.update { it.withWeekendDays(days) }

    /** One night of the calendar on its own, the one that ends on [wakeDay]. */
    fun changeNight(wakeDay: DayOfWeek, sleep: LocalTime, wake: LocalTime) =
        _draft.update { if (sleep == wake) it else it.withNight(wakeDay, sleep, wake) }

    fun finish() {
        val draft = _draft.value
        if (_saving.value || draft.unscheduledRoutines.isNotEmpty() ||
            draft.overlappingSleep().isNotEmpty() || draft.setup.longestAwakeMinutes() > 24 * 60
        ) return
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
