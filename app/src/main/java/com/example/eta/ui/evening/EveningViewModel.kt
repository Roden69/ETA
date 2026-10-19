package com.example.eta.ui.evening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.alarm.EveningStore
import com.example.eta.data.repository.NightService
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.planning.EveningStep
import com.example.eta.domain.planning.WakeQuestion
import com.example.eta.domain.planning.eveningSteps
import com.example.eta.domain.planning.wakeQuestionFor
import com.example.eta.domain.setup.nightEndingOn
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class EveningUiState(
    val loading: Boolean = true,
    /** The screens to show tonight, in order. Empty when there is nothing to say. */
    val steps: List<EveningStep> = emptyList(),
    /** Tomorrow's wake time, when the clock-app question is put. */
    val wakeTime: LocalTime? = null,
    /** The steps of tonight's bed preparation, in order; empty when none are set. */
    val routine: List<String> = emptyList(),
)

/**
 * What comes after the day closing, once tomorrow's plan is confirmed.
 *
 * Decides the screens; the screen performs the answers. The wake question is put
 * at most once a night, so it is recorded as soon as it is decided to be asked.
 */
class EveningViewModel(
    private val setupRepository: SetupRepository,
    private val nightService: NightService,
    private val planRepository: PlanRepository,
    private val subtaskRepository: SubtaskRepository,
    private val eveningStore: EveningStore,
    /** Re-arms Eta's own wake alarm when the clock app it was handed to is gone. */
    private val rearmWakeAlarm: suspend () -> Unit = {},
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(EveningUiState())
    val uiState: StateFlow<EveningUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val today = clock.todayIn(timeZone)
            val tomorrow = today.plus(DatePeriod(days = 1))
            val setup = setupRepository.find()
            val question = if (setup == null) {
                WakeQuestion.NONE
            } else {
                wakeQuestionFor(
                    wakeAlarmOn = setup.wakeAlarm,
                    route = eveningStore.wakeRoute(),
                    clockAppAvailable = eveningStore.clockAppAvailable(),
                    tomorrowConfirmed = planRepository.findDayPlan(tomorrow)?.isConfirmed == true,
                    askedFor = eveningStore.wakeAskedFor(),
                    wakeDate = tomorrow,
                )
            }
            if (question != WakeQuestion.NONE) eveningStore.markWakeAsked(tomorrow)
            // "Eta klingelt deshalb selbst" must be true: the coordinator decided to stay
            // silent while the app was still there, so it has to decide again now.
            if (question == WakeQuestion.NO_CLOCK_APP) rearmWakeAlarm()

            val windDown = eveningStore.windDownEnabled()
            _state.value = EveningUiState(
                loading = false,
                steps = eveningSteps(question, windDown),
                wakeTime = setup
                    ?.takeIf { question != WakeQuestion.NONE }
                    ?.nightEndingOn(tomorrow, nightService.overrides())
                    ?.wake,
                routine = if (windDown) routineOf(today) else emptyList(),
            )
        }
    }

    /** The bed-preparation steps tonight, read off the day's blocks and their steps. */
    private suspend fun routineOf(today: LocalDate): List<String> {
        val steps = subtaskRepository.stepsByItem()
        return planRepository.findDay(today)
            .filter { it.item.role == ItemRole.BED_PREP }
            .flatMap { entry -> steps[entry.item.id].orEmpty() }
            .sortedBy { it.position }
            .map { it.name }
    }

    /** "Nicht mehr anzeigen": the hint is switched off for good. */
    fun hideWindDown() {
        eveningStore.setWindDownEnabled(false)
    }
}
