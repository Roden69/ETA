package com.example.eta.ui.evening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.alarm.ClockApp
import com.example.eta.alarm.ClockAppAlarms
import com.example.eta.domain.planning.EveningStep
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalTime

/**
 * The evening after the day closing: the wake question first, then the wind-down.
 *
 * Nothing here blocks the phone or any app. The hint can be left with "Gute Nacht"
 * or switched off for good; the wake question is put once a night, whatever the
 * answer. With nothing to say the screen ends by itself.
 */
@Composable
fun EveningScreen(
    viewModel: EveningViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = state.steps.getOrNull(index)
    LaunchedEffect(state.loading, step) {
        if (!state.loading && step == null) onClose()
    }

    EtaScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            EtaText(text = "Abend", style = EtaTheme.typography.title)
            when (step) {
                EveningStep.WAKE_ASK -> state.wakeTime?.let { time ->
                    WakeAskCard(time = time, onNext = { index++ })
                }

                EveningStep.WAKE_NO_CLOCK_APP -> state.wakeTime?.let { time ->
                    WakeNoClockAppCard(time = time, onNext = { index++ })
                }

                EveningStep.WIND_DOWN -> WindDownCard(
                    routine = state.routine,
                    onGoodNight = onClose,
                    onHide = {
                        viewModel.hideWindDown()
                        onClose()
                    },
                )

                null -> Unit
            }
        }
    }
}

/**
 * "Soll ich den Wecker in der Uhr-App stellen?" — one alarm for tomorrow, handed
 * over once. The answer is shown where it came back, not as a silent success.
 */
@Composable
private fun WakeAskCard(time: LocalTime, onNext: () -> Unit) {
    val context = LocalContext.current
    val app = remember { ClockAppAlarms.chosen(context) }
    var handed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Wecker für morgen", style = EtaTheme.typography.heading)
            EtaText(
                text = "Soll ich deinen Wecker für ${time.formatClock()} in ${app.inPhrase()} stellen? " +
                    "Eta klingelt morgen dann nicht selbst.",
                style = EtaTheme.typography.body,
            )
            when (handed) {
                null -> Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Ja, stellen",
                        onClick = { handed = ClockAppAlarms.setOnce(context, time, app) },
                    )
                    EtaButton(text = "Nein", style = EtaButtonStyle.Secondary, onClick = onNext)
                }

                true -> {
                    EtaText(
                        text = "An ${app.label} übergeben. Prüf dort, ob der Wecker steht.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.success,
                    )
                    EtaButton(text = "Weiter", onClick = onNext)
                }

                false -> {
                    EtaText(
                        text = "${app.label} hat nicht geantwortet. Stell den Wecker dort selbst — " +
                            "Eta klingelt morgen nicht.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                    EtaButton(text = "Weiter", onClick = onNext)
                }
            }
        }
    }
}

/** The clock app was uninstalled since it was chosen: Eta rings after all, and says so. */
@Composable
private fun WakeNoClockAppCard(time: LocalTime, onNext: () -> Unit) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Wecker für morgen", style = EtaTheme.typography.heading)
            EtaText(
                text = "Die gewählte Uhr-App ist auf diesem Handy nicht mehr zu finden. " +
                    "Eta weckt dich morgen um ${time.formatClock()} selbst.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.warning,
            )
            EtaButton(text = "Weiter", onClick = onNext)
        }
    }
}

/** A calm end to the evening: no lock, no timer, only the choice to stop here. */
@Composable
private fun WindDownCard(
    routine: List<String>,
    onGoodNight: () -> Unit,
    onHide: () -> Unit,
) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Abendruhe", style = EtaTheme.typography.heading)
            EtaText(
                text = "Leg das Handy jetzt weg und lass den Abend ruhig ausklingen. Eta sperrt nichts.",
                style = EtaTheme.typography.body,
            )
            if (routine.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                    EtaText(text = "Deine Abendroutine", style = EtaTheme.typography.bodyStrong)
                    routine.forEach { step ->
                        EtaText(
                            text = "· $step",
                            style = EtaTheme.typography.body,
                            color = EtaTheme.colors.textSecondary,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(text = "Gute Nacht", onClick = onGoodNight)
                EtaButton(text = "Nicht mehr anzeigen", style = EtaButtonStyle.Secondary, onClick = onHide)
            }
        }
    }
}

private fun ClockApp.inPhrase(): String = when (this) {
    ClockApp.SYSTEM -> "der Standard-Uhr des Handys"
    ClockApp.GOOGLE -> "der Google Uhr"
    ClockApp.ANDROID -> "der Android-Uhr"
}
