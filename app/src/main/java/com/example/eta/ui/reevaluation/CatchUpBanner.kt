package com.example.eta.ui.reevaluation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.CatchUpService
import com.example.eta.data.repository.PlanRepository
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * The missed evenings one catch-up walks through: oldest first, and whether the
 * planning of today is still owed once they are behind.
 */
data class CatchUpRun(
    val days: List<LocalDate>,
    val planToday: Boolean,
)

/**
 * The dashboard's missed evenings, and the start of catching them up.
 *
 * The list follows the settled days, so the card goes away as soon as the last
 * missed evening is booked. The run itself is read afresh when it starts, so a
 * plan confirmed since the card was drawn is honoured.
 */
class CatchUpBannerViewModel(
    private val catchUpService: CatchUpService,
    private val planRepository: PlanRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val days: StateFlow<List<LocalDate>> = planRepository.observeSettledDates()
        .map { catchUpService.dailyDays() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start(onReady: (CatchUpRun) -> Unit) {
        viewModelScope.launch {
            val today = clock.todayIn(timeZone)
            onReady(
                CatchUpRun(
                    days = catchUpService.dailyDays(),
                    planToday = planRepository.findDayPlan(today)?.isConfirmed != true,
                ),
            )
        }
    }
}

/**
 * The card that offers the oldest missed evening, on the dashboard.
 *
 * Only shown while something is missed; [days] is never empty when it is drawn.
 */
@Composable
fun CatchUpBanner(days: List<LocalDate>, onStart: () -> Unit) {
    val oldest = days.first()
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(
                text = "Tagesabschluss vom ${oldest.formatLong()} nachholen",
                style = EtaTheme.typography.heading,
            )
            EtaText(
                text = if (days.size == 1) {
                    "Der Abend ist ohne Abschluss vergangen. Nachholen rechnet ihn ab, wie er war."
                } else {
                    "${days.size} Abende sind ohne Abschluss vergangen. Sie kommen der Reihe nach, " +
                        "und jeden kannst du überspringen."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaButton(text = "Nachholen", onClick = onStart)
        }
    }
}
