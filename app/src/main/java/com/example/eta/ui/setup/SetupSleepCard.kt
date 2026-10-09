package com.example.eta.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.RoutineSetup
import com.example.eta.domain.setup.SLEEP_ROUTINE
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.weekdayNight
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.components.EtaWeekdayPicker
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * When the user sleeps on a weekday and at the weekend — the first page's part of
 * sleep. Sleep is the one routine that cannot be left out: someone sleeps at least
 * once in 24 hours, so every day gets a night. These two answers only *seed* them;
 * the calendar on the next page lays one provisional night per day, and each can
 * be changed there without touching the others.
 */
@Composable
internal fun SleepRhythmCard(
    draft: RoutineSetup,
    onWeekdayNight: (sleep: LocalTime, wake: LocalTime) -> Unit,
    onWeekendNight: (sleep: LocalTime, wake: LocalTime) -> Unit,
    onWeekendDays: (Set<DayOfWeek>) -> Unit,
) {
    val setup = draft.setup
    val weekendDays = setup.weekendDays
    val weekdays = WEEK.filter { it !in weekendDays }.toSet()

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                EtaText(text = "${SLEEP_ROUTINE.name} · immer dabei", style = EtaTheme.typography.heading)
                EtaText(
                    text = "Mindestens einmal in 24 Stunden. Diese Zeiten legen die Nächte " +
                        "deiner Woche vorläufig an — auf der nächsten Seite kannst du jede " +
                        "Nacht einzeln anpassen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
            NightFields(
                title = "Unter der Woche",
                night = setup.weekdayNight,
                wakeDays = weekdays,
                onChange = onWeekdayNight,
            )
            NightFields(
                title = "Am Wochenende",
                night = setup.weekendNight ?: setup.weekdayNight,
                wakeDays = weekendDays,
                onChange = onWeekendNight,
            )
            EtaField(
                label = "Welche Tage sind dein Wochenende?",
                hint = "Die Tage, an denen du nach diesen Zeiten aufstehst — der Abend davor " +
                    "gehört jeweils dazu.",
            ) {
                EtaWeekdayPicker(
                    selected = weekendDays,
                    onToggle = { day ->
                        onWeekendDays(if (day in weekendDays) weekendDays - day else weekendDays + day)
                    },
                    days = WEEK,
                )
            }
        }
    }
}

/** Bedtime and wake-up of one kind of night, each labelled with the days it falls on. */
@Composable
private fun NightFields(
    title: String,
    night: NightTimes,
    wakeDays: Set<DayOfWeek>,
    onChange: (sleep: LocalTime, wake: LocalTime) -> Unit,
) {
    // The two hours cannot be the same: that would be no night, or a whole day of it.
    var notice by remember { mutableStateOf(false) }
    fun change(sleep: LocalTime, wake: LocalTime) {
        notice = sleep == wake
        if (!notice) onChange(sleep, wake)
    }

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        EtaText(text = title, style = EtaTheme.typography.bodyStrong)
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaField(label = "Schlafen gehen", modifier = Modifier.weight(1f)) {
                EtaTimePicker(value = night.sleep, onValueChange = { change(it, night.wake) })
            }
            EtaField(label = "Aufstehen", modifier = Modifier.weight(1f)) {
                EtaTimePicker(value = night.wake, onValueChange = { change(night.sleep, it) })
            }
        }
        // The days beside each hour, because an hour alone does not say which
        // day's it is — least of all one after midnight.
        EtaText(
            text = "Ins Bett: ${formatWeekdays(night.sleepDays(wakeDays))} · " +
                "Aufstehen: ${formatWeekdays(wakeDays)}",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )
        if (night.sleepsAfterMidnight) {
            EtaText(
                text = AFTER_MIDNIGHT_HINT,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
        EtaText(
            text = if (notice) {
                "Schlafen gehen und Aufstehen dürfen nicht dieselbe Uhrzeit sein."
            } else {
                "Nacht: ${night.sleepDuration().formatShort()}"
            },
            style = EtaTheme.typography.caption,
            color = if (notice) EtaTheme.colors.warning else EtaTheme.colors.textMuted,
        )
    }
}
