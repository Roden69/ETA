package com.example.eta.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.eta.domain.setup.DEFAULT_WEEKEND
import com.example.eta.domain.setup.NightMode
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.bedPrepDuration
import com.example.eta.domain.setup.bedPrepIsNextDay
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.domain.setup.nightMode
import com.example.eta.domain.setup.rhythmDiverges
import com.example.eta.domain.setup.sleepDuration
import com.example.eta.domain.setup.withBedPrepNextDay
import com.example.eta.domain.setup.withNightEndingOn
import com.example.eta.domain.setup.withNightMode
import com.example.eta.domain.setup.withNightOverride
import com.example.eta.domain.setup.withSleepNextDay
import com.example.eta.domain.setup.withWeekdayNight
import com.example.eta.domain.setup.weekdayNight
import com.example.eta.ui.attributes.CheckRow
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaExpander
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.components.EtaWeekdayPicker
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatVeryShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.planner.RHYTHM_ADVICE
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalTime

/** Shared by the sleep and planning settings pages. */
typealias OnSetupChange = ((UserSetup) -> UserSetup) -> Unit

@Composable
private fun StepCard(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                EtaText(text = title, style = EtaTheme.typography.title)
                if (description != null) {
                    EtaText(
                        text = description,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun SleepStep(
    draft: UserSetup,
    onChange: OnSetupChange,
) {
    StepCard(
        title = "Schlaf und Morgen",
        description = "Schlafzeiten werden im Tagesplaner farblich hinterlegt und aus den " +
            "freien Stunden herausgerechnet.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            val mode = draft.nightMode
            // Asked before it is granted: the third option is offered and
            // advised against in the same breath.
            var confirmingPerDay by remember { mutableStateOf(false) }

            // With a night per weekday these three are answered seven times
            // further down, and asking them here as well would be an eighth
            // answer that means nothing.
            if (mode != NightMode.PER_DAY) {
                EtaField(label = "Bettfertig machen ab", hint = "Wird als tägliche Aufgabe angelegt.") {
                    EtaTimePicker(
                        value = draft.bedPrepTime,
                        onValueChange = { time -> onChange { it.copy(bedPrepTime = time) } },
                    )
                }
                NextDayBox(
                    checked = draft.weekdayNight.bedPrepIsNextDay,
                    onCheckedChange = { on ->
                        onChange { it.withWeekdayNight(it.weekdayNight.withBedPrepNextDay(on)) }
                    },
                )
                EtaField(
                    label = "Schlafen gehen",
                    hint = AFTER_MIDNIGHT_HINT.takeIf { draft.weekdayNight.sleepsAfterMidnight },
                ) {
                    EtaTimePicker(
                        value = draft.sleepTime,
                        onValueChange = { time -> onChange { it.copy(sleepTime = time) } },
                    )
                }
                NextDayBox(
                    checked = draft.weekdayNight.sleepsAfterMidnight,
                    onCheckedChange = { on ->
                        onChange { it.withWeekdayNight(it.weekdayNight.withSleepNextDay(on)) }
                    },
                )
                EtaField(label = "Aufstehen") {
                    EtaTimePicker(
                        value = draft.wakeTime,
                        onValueChange = { time -> onChange { it.copy(wakeTime = time) } },
                    )
                }
            }
            EtaField(
                label = "Morgenroutine",
                hint = "Wie lange sie dauert, ab dem Aufstehen — Anziehen, Frühstück, " +
                    "Ankommen. Ihre Schritte legst du in den Einstellungen oder im " +
                    "Reiter Listen an.",
            ) {
                EtaDurationPicker(
                    value = draft.morningDuration,
                    onValueChange = { duration -> onChange { it.copy(morningDuration = duration) } },
                )
            }
            if (mode != NightMode.PER_DAY) {
                DerivedHint(
                    "Nacht: ${draft.sleepDuration().formatShort()} · " +
                        "Bettfertig: ${draft.bedPrepDuration().formatShort()}",
                )
            }

            EtaField(
                label = "Schlafrhythmus",
                hint = "Ob jede Nacht gleich ist. Das Wochenende sind die Nächte von Freitag " +
                    "auf Samstag und von Samstag auf Sonntag.",
            ) {
                EtaChoice(
                    options = listOf(
                        NightMode.SAME to "Jede Nacht gleich",
                        NightMode.WEEKEND to "Anderer Schlafrhythmus am Wochenende",
                        NightMode.PER_DAY to "Für jeden Wochentag selbst entscheiden",
                    ),
                    selected = mode,
                    onSelect = { chosen ->
                        if (chosen == NightMode.PER_DAY && mode != NightMode.PER_DAY) {
                            confirmingPerDay = true
                        } else if (chosen != mode) {
                            onChange { it.withNightMode(chosen) }
                        }
                    },
                )
            }

            if (confirmingPerDay) {
                ConfirmDialog(
                    title = "Jeden Wochentag anders?",
                    message = "Davon wird abgeraten: Für den Schlafrhythmus ist es nicht " +
                        "empfohlen, an jedem Tag zu anderen Zeiten zu schlafen. Wenn es sich " +
                        "nicht vermeiden lässt, achte auf Schlafausgleich und plane " +
                        "gegebenenfalls einen Mittagsschlaf ein.",
                    confirm = "Trotzdem",
                    onDismiss = { confirmingPerDay = false },
                    onConfirm = {
                        onChange { it.withNightMode(NightMode.PER_DAY) }
                        confirmingPerDay = false
                    },
                )
            }

            val weekend = draft.weekendNight
            if (mode == NightMode.WEEKEND && weekend != null) {
                fun change(transform: (NightTimes) -> NightTimes) =
                    onChange { it.copy(weekendNight = it.weekendNight?.let(transform)) }

                // The days are named beside each hour, because an hour alone
                // does not say which day's it is — least of all one after
                // midnight, which falls on the day of getting up.
                val days = DEFAULT_WEEKEND
                EtaField(
                    label = "Bettfertig machen ab (${formatWeekdays(weekend.bedPrepDays(days))})",
                ) {
                    EtaTimePicker(
                        value = weekend.bedPrep,
                        onValueChange = { time -> change { it.copy(bedPrep = time) } },
                    )
                }
                NextDayBox(
                    checked = weekend.bedPrepIsNextDay,
                    onCheckedChange = { on -> change { it.withBedPrepNextDay(on) } },
                )
                EtaField(
                    label = "Schlafen gehen (${formatWeekdays(weekend.sleepDays(days))})",
                    hint = AFTER_MIDNIGHT_HINT.takeIf { weekend.sleepsAfterMidnight },
                ) {
                    EtaTimePicker(
                        value = weekend.sleep,
                        onValueChange = { time -> change { it.copy(sleep = time) } },
                    )
                }
                NextDayBox(
                    checked = weekend.sleepsAfterMidnight,
                    onCheckedChange = { on -> change { it.withSleepNextDay(on) } },
                )
                EtaField(label = "Aufstehen (${formatWeekdays(days)})") {
                    EtaTimePicker(
                        value = weekend.wake,
                        onValueChange = { time -> change { it.copy(wake = time) } },
                    )
                }
                DerivedHint(
                    "Nacht am Wochenende: ${weekend.sleepDuration().formatShort()} · " +
                        "Bettfertig: ${weekend.bedPrepDuration().formatShort()}",
                )
            }

            if (mode == NightMode.PER_DAY) {
                // A night belongs to the day it ends on, so that is what each
                // fold is named after; closed, it says its hours.
                WEEK.forEach { wakeDay ->
                    val night = draft.nightEndingOn(wakeDay)
                    fun change(transform: (NightTimes) -> NightTimes) =
                        onChange { it.withNightEndingOn(wakeDay, transform(it.nightEndingOn(wakeDay))) }

                    EtaExpander(
                        label = "Nacht auf ${wakeDay.formatLong()}",
                        summary = "${night.sleep.formatClock()} – ${night.wake.formatClock()} · " +
                            night.sleepDuration().formatShort(),
                    ) {
                        val one = setOf(wakeDay)
                        EtaField(
                            label = "Bettfertig machen ab (${formatWeekdays(night.bedPrepDays(one))})",
                        ) {
                            EtaTimePicker(
                                value = night.bedPrep,
                                onValueChange = { time -> change { it.copy(bedPrep = time) } },
                            )
                        }
                        NextDayBox(
                            checked = night.bedPrepIsNextDay,
                            onCheckedChange = { on -> change { it.withBedPrepNextDay(on) } },
                        )
                        EtaField(
                            label = "Schlafen gehen (${formatWeekdays(night.sleepDays(one))})",
                            hint = AFTER_MIDNIGHT_HINT.takeIf { night.sleepsAfterMidnight },
                        ) {
                            EtaTimePicker(
                                value = night.sleep,
                                onValueChange = { time -> change { it.copy(sleep = time) } },
                            )
                        }
                        NextDayBox(
                            checked = night.sleepsAfterMidnight,
                            onCheckedChange = { on -> change { it.withSleepNextDay(on) } },
                        )
                        EtaField(label = "Aufstehen (${wakeDay.formatVeryShort()})") {
                            EtaTimePicker(
                                value = night.wake,
                                onValueChange = { time -> change { it.copy(wake = time) } },
                            )
                        }
                        DerivedHint(
                            "Nacht: ${night.sleepDuration().formatShort()} · " +
                                "Bettfertig: ${night.bedPrepDuration().formatShort()}",
                        )
                    }
                }
            }

            if (draft.nightOverrides.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    EtaText(text = "Einzelne Nächte", style = EtaTheme.typography.heading)
                    DerivedHint(
                        "Diese Nächte hast du einzeln festgelegt. Sie folgen deinem Schlafrhythmus " +
                            "erst wieder, wenn du sie zurücksetzt.",
                    )
                    WEEK.filter { it in draft.nightOverrides }.forEach { day ->
                        val night = draft.nightOverrides.getValue(day)

                        // The night keeps no winding down of its own: it follows bedtime.
                        fun change(sleep: LocalTime, wake: LocalTime) {
                            if (sleep == wake) return
                            onChange { it.withNightOverride(day, sleep, wake) }
                        }
                        EtaText(text = "Nacht zum ${day.formatLong()}", style = EtaTheme.typography.bodyStrong)
                        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                            EtaField(label = "Schlafen gehen", modifier = Modifier.weight(1f)) {
                                EtaTimePicker(value = night.sleep, onValueChange = { change(it, night.wake) })
                            }
                            EtaField(label = "Aufstehen", modifier = Modifier.weight(1f)) {
                                EtaTimePicker(value = night.wake, onValueChange = { change(night.sleep, it) })
                            }
                        }
                        EtaButton(
                            text = "Auf die Zeiten oben zurücksetzen",
                            style = EtaButtonStyle.Secondary,
                            onClick = { onChange { it.copy(nightOverrides = it.nightOverrides - day) } },
                        )
                    }
                }
            }

            // More than an hour apart, whichever way it came about.
            if (mode != NightMode.SAME && draft.rhythmDiverges()) {
                EtaSurface(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = EtaTheme.colors.warning,
                    contentPadding = EtaTheme.spacing.md,
                ) {
                    EtaText(
                        text = RHYTHM_ADVICE,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                }
            }
        }
    }
}

/**
 * "Folgetag", under an hour of the night.
 *
 * Ticked, the hour lies after midnight — on the day of getting up. It ticks
 * itself when such an hour is picked on the dial, which is the point of it:
 * "01:00" then visibly means one at night, the thing that was probably meant.
 * Ticking it by hand moves the hour across midnight; see `withSleepNextDay`.
 */
@Composable
private fun NextDayBox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    CheckRow(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = "Folgetag",
        hint = if (checked) {
            "Die Uhrzeit liegt nach Mitternacht, also schon am Tag des Aufstehens."
        } else {
            "Ankreuzen, wenn die Uhrzeit nach Mitternacht liegt — etwa 01:00 Uhr nachts."
        },
    )
}

/**
 * Said under a bedtime that lies after midnight. Such an hour belongs to the
 * night it starts, not to the evening of the day it is written on: 01:00 for
 * the night into Sunday is Sunday at one, at the end of Saturday evening.
 */
internal const val AFTER_MIDNIGHT_HINT =
    "Nach Mitternacht — zählt als Ende des Abends davor und liegt im Kalender " +
        "schon auf dem Tag des Aufstehens."

@Composable
fun PlanningStep(draft: UserSetup, onChange: OnSetupChange) {
    StepCard(
        title = "Planungsphasen",
        description = "Zu diesen Zeiten meldet sich Eta, um den nächsten Tag " +
            "beziehungsweise die nächste Woche mit dir zu planen.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaField(label = "Tagesplanung", hint = "Jeden Abend zur selben Zeit.") {
                EtaTimePicker(
                    value = draft.dailyPlanningTime,
                    onValueChange = { time -> onChange { it.copy(dailyPlanningTime = time) } },
                )
            }
            EtaField(label = "Wochenplanung — Wochentag") {
                EtaWeekdayPicker(
                    selected = draft.weeklyPlanningDay,
                    onSelect = { day -> onChange { it.copy(weeklyPlanningDay = day) } },
                    days = WEEK,
                )
            }
            EtaField(label = "Wochenplanung — Uhrzeit") {
                EtaTimePicker(
                    value = draft.weeklyPlanningTime,
                    onValueChange = { time -> onChange { it.copy(weeklyPlanningTime = time) } },
                )
            }
        }
    }
}

/** The little derived line under a group of answers. */
@Composable
private fun DerivedHint(text: String) {
    EtaText(
        text = text,
        style = EtaTheme.typography.caption,
        color = EtaTheme.colors.textMuted,
    )
}
