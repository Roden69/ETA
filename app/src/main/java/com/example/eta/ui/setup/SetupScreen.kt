package com.example.eta.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import com.example.eta.domain.setup.SUGGESTED_SETUP_ROUTINES
import com.example.eta.ui.settings.CalendarSettingsBox
import com.example.eta.ui.settings.CalendarSettingsViewModel
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.longestAwakeMinutes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaProgressBar
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatMinutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import com.example.eta.ui.theme.EtaTheme

/** Routine selection and a weekly calendar, followed by the shared week review. */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    modifier: Modifier = Modifier,
    /**
     * The Google connection, offered first on the first page: the calendar is read
     * before routines are chosen, so the week template starts around appointments.
     * Null leaves it out.
     */
    calendarViewModel: CalendarSettingsViewModel? = null,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val hiddenEvents by viewModel.hiddenEvents.collectAsStateWithLifecycle()
    val calendarBusy by viewModel.calendarBusy.collectAsStateWithLifecycle()
    val calendarNote by viewModel.calendarNote.collectAsStateWithLifecycle()
    var planningWeek by rememberSaveable { mutableStateOf(false) }
    var routineName by rememberSaveable { mutableStateOf("") }
    // Owned here rather than by the routine step, which leaves the composition
    // while the week is open: the list comes back where it was left.
    val routineScroll = rememberScrollState()

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
                if (calendarBusy) {
                    EtaText(
                        text = "Termine werden geladen …",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                calendarNote?.let {
                    EtaText(text = it, style = EtaTheme.typography.caption, color = EtaTheme.colors.warning)
                }
                SetupWeekStep(
                    draft = draft,
                    onSavePlacement = viewModel::savePlacement,
                    onDeletePlacement = viewModel::deletePlacement,
                    onNightChange = viewModel::changeNight,
                    calendarEvents = calendarEvents,
                    hiddenEvents = hiddenEvents,
                    onHideEvent = viewModel::hideEvent,
                    onShowAllEvents = viewModel::showAllEvents,
                    modifier = Modifier.weight(1f),
                )
                val unplanned = draft.unscheduledRoutines
                val overlappingSleep = draft.overlappingSleep()
                val awakeMinutes = draft.setup.longestAwakeMinutes()
                if (unplanned.isNotEmpty()) {
                    EtaText(
                        text = "Noch ohne Zeitplatz: ${unplanned.joinToString { it.name }}. " +
                            "Plane sie ein oder gehe zurück, um sie abzuwählen. " +
                            "Fertig bleibt gesperrt, bis jede Routine einen Zeitplatz hat.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                }
                if (overlappingSleep.isNotEmpty()) {
                    EtaText(
                        text = sleepOverlapText(draft, overlappingSleep),
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                }
                if (awakeMinutes > 24 * 60) {
                    EtaText(
                        text = "Zwischen zwei Schlafblöcken liegen ${formatMinutes(awakeMinutes)} ohne Schlaf. " +
                            "Plane spätestens nach 24 Stunden wieder Schlaf ein. " +
                            "Fertig bleibt gesperrt, bis das passt.",
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
                        enabled = !saving && unplanned.isEmpty() && overlappingSleep.isEmpty() && awakeMinutes <= 24 * 60,
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
                    calendarViewModel = calendarViewModel,
                    onWeekdayNight = viewModel::changeWeekdayNight,
                    onWeekendNight = viewModel::changeWeekendNight,
                    onWeekendDays = viewModel::changeWeekendDays,
                    scroll = routineScroll,
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
                        viewModel.enterWeek()
                    },
                )
            }
        }
    }
}

/**
 * The blocking sleep warning, naming each night that runs into another one. This is
 * the only warning on this page that keeps "Fertig" disabled.
 */
private fun sleepOverlapText(draft: RoutineSetup, wakeDays: Set<DayOfWeek>): String {
    val nights = draft.sleepNights()
        .filter { it.wakeDay in wakeDays }
        .joinToString("; ") { night ->
            "Nacht zum ${night.wakeDay.formatLong()}, ${night.start.formatClock()} bis " +
                night.night.wake.formatClock()
        }
    return "Diese Nächte überschneiden sich: $nights. Fertig bleibt gesperrt, bis jede Nacht " +
        "für sich liegt. Passe sie an."
}

/**
 * The questionnaire's last screen: the standing week it has just laid down, as
 * the timetable the dashboard has under "Woche → Generell" — with a box at the
 * foot that explains it, page by page, the way the tutorial explains a screen.
 *
 * Here rather than as another page of questions, because this is where a week
 * is put right: what collides is dragged apart, not typed apart. [timetable] is
 * handed that box as its footer and draws the rest.
 */
@Composable
fun SetupWeekReview(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    timetable: @Composable (footer: @Composable () -> Unit) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    val notes = WEEK_REVIEW_NOTES
    val note = notes[step.coerceIn(0, notes.lastIndex)]

    // Back steps through the notes first; only the first one leaves the review.
    BackHandler(enabled = step > 0) { step -= 1 }

    EtaScreen(modifier = modifier) {
        timetable {
            EtaSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EtaTheme.spacing.lg),
                borderColor = EtaTheme.colors.accent,
                color = EtaTheme.colors.surfaceRaised,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaText(
                        text = "${note.first} · ${step + 1} von ${notes.size}",
                        style = EtaTheme.typography.label,
                        color = EtaTheme.colors.accent,
                    )
                    EtaText(
                        text = note.second,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        if (step > 0) {
                            EtaButton(
                                text = "Zurück",
                                style = EtaButtonStyle.Secondary,
                                onClick = { step -= 1 },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (step < notes.lastIndex) {
                            EtaButton(text = "Weiter", onClick = { step += 1 })
                        } else {
                            EtaButton(text = "Einrichtung abschließen", onClick = onDone)
                        }
                    }
                }
            }
        }
    }
}

/** What the box at the foot of the week review says, a heading and a paragraph each. */
private val WEEK_REVIEW_NOTES = listOf(
    "Dein Wochenplan" to
        "Das ist deine feste Woche, so wie du sie gerade eingerichtet hast: ein Wochentag " +
        "pro Seite, die Nacht ist schattiert. Wische zur Seite oder tippe oben auf einen Tag. " +
        "Tippst du einen Block an, zeigt er seinen Namen.",
    "Das Schloss" to
        "Oben rechts steht »Gesperrt«. Tippe auf das Schloss, um die wiederkehrenden Tasks " +
        "zu entsperren: Dann verschiebst du sie mit dem Finger und ziehst sie mit zwei " +
        "Fingern länger oder kürzer. So löst du auch Überschneidungen aus der Einrichtung. " +
        "Gedrückt halten öffnet eine Task zum Bearbeiten; auf einer freien Stelle legt es " +
        "eine neue an.",
    "Aktuell und Generell" to
        "»Generell« ist dieser feste Plan — was du hier änderst, gilt für alle künftigen " +
        "Wochen. »Aktuell« zeigt später die laufende Woche mit allem, was dazukommt, und " +
        "ändert nur den einen Termin. Jetzt ist »Aktuell« ausgegraut: Dort lassen sich nur " +
        "bestehende Termine bearbeiten, und die gibt es noch nicht. »Termine anzeigen« " +
        "blendet übernommene Google-Termine ein.",
    "Später wiederfinden" to
        "Diese Ansicht findest du jederzeit im Dashboard unter »Woche«. Passt alles, " +
        "schließe die Einrichtung ab — ändern kannst du es immer.",
)

@Composable
private fun RoutineSelectionStep(
    draft: RoutineSetup,
    routineName: String,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (SetupRoutine) -> Unit,
    onRemove: (String) -> Unit,
    calendarViewModel: CalendarSettingsViewModel?,
    onWeekdayNight: (LocalTime, LocalTime) -> Unit,
    onWeekendNight: (LocalTime, LocalTime) -> Unit,
    onWeekendDays: (Set<DayOfWeek>) -> Unit,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
    ) {
        calendarViewModel?.let { calendar ->
            EtaText(
                text = "Optional: Verbinde deinen Google Kalender, damit deine Routinen um deine " +
                    "Termine herum geplant werden. Das geht auch später in den Einstellungen.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            CalendarSettingsBox(calendar)
        }
        EtaText(text = "Deine Routinen", style = EtaTheme.typography.title)
        EtaText(
            text = "Welche Routinen gehören zu deiner Woche? Wähle Vorschläge aus " +
                "oder ergänze deine eigenen. Auf der nächsten Seite legst du ihre Zeiten fest.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )

        SectionHeading(
            title = "Feste Grundkonfiguration",
            explanation = "Dein Schlaf steht in jeder Woche. Du kannst die Zeiten anpassen, aber nicht abwählen.",
        )
        SleepRhythmCard(
            draft = draft,
            onWeekdayNight = onWeekdayNight,
            onWeekendNight = onWeekendNight,
            onWeekendDays = onWeekendDays,
        )

        SectionHeading(
            title = "Vorschläge",
            explanation = "Optional. Wähle, was zu deiner Woche passt. Die Liste ist nicht vollständig.",
        )
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

        SectionHeading(
            title = "Eigene Routinen",
            explanation = "Beliebig viele, zum Beispiel Yoga oder Lernen. Sie werden wie andere " +
                "wiederkehrende Aufgaben eingeplant.",
        )
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
        draft.placeableRoutines.filter { routine ->
            SUGGESTED_SETUP_ROUTINES.none { it.id == routine.id }
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

/** A heading for one group of routines, with the one line that says what the group means. */
@Composable
private fun SectionHeading(title: String, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        EtaText(text = title, style = EtaTheme.typography.heading)
        EtaText(
            text = explanation,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}
