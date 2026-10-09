package com.example.eta.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.setup.SetupRoutine
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.SLEEP_ROUTINE
import com.example.eta.domain.setup.SLEEP_ROUTINE_ID
import com.example.eta.domain.setup.SUGGESTED_SETUP_ROUTINES
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaProgressBar
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.theme.EtaTheme

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var planningWeek by rememberSaveable { mutableStateOf(false) }
    var routineName by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = planningWeek && !saving) { planningWeek = false }

    EtaScreen(modifier = modifier.imePadding()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
        ) {
            EtaText(
                text = "Einrichtung · Schritt ${if (planningWeek) 2 else 1} von 2",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaProgressBar(fraction = if (planningWeek) 1f else 0.5f)

            if (planningWeek) {
                SetupWeekStep(
                    draft = draft,
                    onSavePlacement = viewModel::savePlacement,
                    onDeletePlacement = viewModel::deletePlacement,
                    onSleepChange = viewModel::changeSleep,
                    modifier = Modifier.weight(1f),
                )
                val unplanned = draft.unscheduledRoutines
                if (unplanned.isNotEmpty()) {
                    EtaText(
                        text = "Noch ohne Zeitplatz: ${unplanned.joinToString { it.name }}. " +
                            "Plane sie ein oder gehe zurück, um sie abzuwählen.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Zurück",
                        style = EtaButtonStyle.Secondary,
                        enabled = !saving,
                        onClick = { planningWeek = false },
                    )
                    EtaButton(
                        text = if (saving) "Wird gespeichert …" else "Fertig",
                        modifier = Modifier.weight(1f),
                        enabled = !saving && unplanned.isEmpty(),
                        onClick = viewModel::finish,
                    )
                }
            } else {
                RoutineSelectionStep(
                    draft = draft,
                    routineName = routineName,
                    onNameChange = { routineName = it },
                    onAdd = {
                        viewModel.addRoutine(routineName)
                        routineName = ""
                    },
                    onToggle = viewModel::toggleRoutine,
                    onRemove = viewModel::removeRoutine,
                    modifier = Modifier.weight(1f),
                )
                EtaButton(
                    text = "Bestätigen und Woche planen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // A typed routine must not disappear just because Add was not tapped.
                        if (routineName.isNotBlank()) viewModel.addRoutine(routineName)
                        routineName = ""
                        planningWeek = true
                    },
                )
            }
        }
    }
}

@Composable
private fun RoutineSelectionStep(
    draft: RoutineSetup,
    routineName: String,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (SetupRoutine) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
    ) {
        EtaText(text = "Deine Routinen", style = EtaTheme.typography.title)
        EtaText(
            text = "Welche Routinen gehören zu deiner Woche? Wähle Vorschläge aus " +
                "oder ergänze deine eigenen. Auf der nächsten Seite legst du ihre Zeiten fest.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                EtaText(text = "${SLEEP_ROUTINE.name} · immer dabei", style = EtaTheme.typography.heading)
                EtaText(
                    text = "Täglich von ${draft.setup.sleepTime.formatClock()} bis " +
                        "${draft.setup.wakeTime.formatClock()} Uhr. " +
                        "Du kannst die Schlafzeiten im Wochenplan anpassen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
        }
        SUGGESTED_SETUP_ROUTINES.forEach { routine ->
            key(routine.id) {
                EtaField(label = routine.name) {
                    EtaChoice(
                        options = listOf(false to "Nicht auswählen", true to "Auswählen"),
                        selected = draft.routines.any { it.id == routine.id },
                        onSelect = { selected ->
                            if (selected != draft.routines.any { it.id == routine.id }) onToggle(routine)
                        },
                        modifier = Modifier.semantics { contentDescription = "Routine ${routine.name}" },
                    )
                }
            }
        }
        EtaField(label = "Eigene Routine", hint = "Zum Beispiel Yoga machen oder Lernen.") {
            EtaTextField(
                value = routineName,
                onValueChange = onNameChange,
                placeholder = "Name der Routine",
                onImeAction = { if (routineName.isNotBlank()) onAdd() },
                modifier = Modifier.semantics { contentDescription = "Eigene Routine" },
            )
        }
        EtaButton(
            text = "Routine hinzufügen",
            style = EtaButtonStyle.Secondary,
            enabled = routineName.isNotBlank(),
            onClick = onAdd,
        )
        draft.routines.filter { routine ->
            routine.id != SLEEP_ROUTINE_ID && SUGGESTED_SETUP_ROUTINES.none { it.id == routine.id }
        }.forEach { routine ->
            key(routine.id) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaText(text = routine.name, style = EtaTheme.typography.heading)
                        EtaButton(
                            text = "Entfernen",
                            style = EtaButtonStyle.Secondary,
                            onClick = { onRemove(routine.id) },
                            modifier = Modifier.semantics { contentDescription = "${routine.name} entfernen" },
                        )
                    }
                }
            }
        }
        EtaText(
            text = "Deine Zeitplätze wiederholen sich jede Woche. " +
                "Routinen und Planungszeiten kannst du später unter Listen und Einstellungen ändern.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}
