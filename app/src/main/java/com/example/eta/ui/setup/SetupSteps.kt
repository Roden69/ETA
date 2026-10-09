package com.example.eta.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.bedPrepDuration
import com.example.eta.domain.setup.sleepDuration
import com.example.eta.domain.setup.suggestedWeekendNight
import com.example.eta.domain.setup.weekdayNight
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.components.EtaWeekdayPicker
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatWeekdays
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
fun SleepStep(draft: UserSetup, onChange: OnSetupChange) {
    StepCard(
        title = "Schlaf und Morgen",
        description = "Schlafzeiten werden im Tagesplaner farblich hinterlegt und aus den " +
            "freien Stunden herausgerechnet.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaField(label = "Bettfertig machen ab", hint = "Wird als tägliche Aufgabe angelegt.") {
                EtaTimePicker(
                    value = draft.bedPrepTime,
                    onValueChange = { time -> onChange { it.copy(bedPrepTime = time) } },
                )
            }
            EtaField(
                label = "Schlafen gehen",
                hint = AFTER_MIDNIGHT_HINT.takeIf { draft.weekdayNight.sleepsAfterMidnight },
            ) {
                EtaTimePicker(
                    value = draft.sleepTime,
                    onValueChange = { time -> onChange { it.copy(sleepTime = time) } },
                )
            }
            EtaField(label = "Aufstehen") {
                EtaTimePicker(
                    value = draft.wakeTime,
                    onValueChange = { time -> onChange { it.copy(wakeTime = time) } },
                )
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
            DerivedHint(
                "Nacht: ${draft.sleepDuration().formatShort()} · " +
                    "Bettfertig: ${draft.bedPrepDuration().formatShort()}",
            )

            val weekend = draft.weekendNight
            EtaField(
                label = "Am Wochenende",
                hint = "Eigene Schlafzeiten für die Tage, an denen du ausschlafen kannst.",
            ) {
                EtaChoice(
                    options = listOf(false to "Wie unter der Woche", true to "Andere Zeiten"),
                    selected = weekend != null,
                    onSelect = { differs ->
                        onChange {
                            it.copy(
                                weekendNight = if (differs) {
                                    it.weekendNight ?: it.suggestedWeekendNight()
                                } else {
                                    null
                                },
                            )
                        }
                    },
                )
            }
            if (weekend != null) {
                fun change(transform: (NightTimes) -> NightTimes) =
                    onChange { it.copy(weekendNight = it.weekendNight?.let(transform)) }

                // Which days those are is the user's to say: a week that works
                // Wednesday to Sunday has its weekend on Monday and Tuesday.
                val days = draft.weekendDays
                EtaField(
                    label = "Welche Tage sind dein Wochenende?",
                    hint = "Die Tage, an denen du nach diesen Zeiten aufstehst — der " +
                        "Abend davor gehört jeweils dazu.",
                ) {
                    EtaWeekdayPicker(
                        selected = days,
                        onToggle = { day ->
                            onChange {
                                val next = if (day in it.weekendDays) {
                                    it.weekendDays - day
                                } else {
                                    it.weekendDays + day
                                }
                                // A weekend of no days is "Wie unter der Woche",
                                // and that is the switch above.
                                if (next.isEmpty()) it else it.copy(weekendDays = next)
                            }
                        },
                        days = WEEK,
                    )
                }

                // The days are named beside each hour, because an hour alone
                // does not say which day's it is — least of all one after
                // midnight, which falls on the day of getting up.
                EtaField(
                    label = "Bettfertig machen ab (${formatWeekdays(weekend.bedPrepDays(days))})",
                ) {
                    EtaTimePicker(
                        value = weekend.bedPrep,
                        onValueChange = { time -> change { it.copy(bedPrep = time) } },
                    )
                }
                EtaField(
                    label = "Schlafen gehen (${formatWeekdays(weekend.sleepDays(days))})",
                    hint = AFTER_MIDNIGHT_HINT.takeIf { weekend.sleepsAfterMidnight },
                ) {
                    EtaTimePicker(
                        value = weekend.sleep,
                        onValueChange = { time -> change { it.copy(sleep = time) } },
                    )
                }
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

            if (draft.nightOverrides.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    EtaText(text = "Einzelne Nächte", style = EtaTheme.typography.heading)
                    DerivedHint(
                        "Diese Nächte hast du einzeln festgelegt. Sie folgen den Zeiten oben " +
                            "nicht mehr, bis du sie zurücksetzt.",
                    )
                    WEEK.filter { it in draft.nightOverrides }.forEach { day ->
                        val night = draft.nightOverrides.getValue(day)

                        // The night keeps no winding down of its own: it follows bedtime.
                        fun change(sleep: LocalTime, wake: LocalTime) {
                            if (sleep == wake) return
                            onChange {
                                it.copy(
                                    nightOverrides = it.nightOverrides +
                                        (day to NightTimes(bedPrep = sleep, sleep = sleep, wake = wake)),
                                )
                            }
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
        }
    }
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
