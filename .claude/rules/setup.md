---
paths:
  - "app/src/**/domain/setup/**"
  - "app/src/**/ui/setup/**"
  - "app/src/**/SetupRepository.kt"
  - "app/src/**/Setup*Test.kt"
  - "app/src/**/RoutineSetupTest.kt"
---

### First setup: routines and sleep, then a weekly calendar

The first-run intro still precedes setup. `SetupScreen` has **two confirmed steps**,
followed by the upstream standing-week review and the existing tutorial:

1. **Choose routines and say when you sleep.** Sport and Achtsamkeit are optional
   suggestions; a text field adds custom routines, such as Yoga machen or Lernen.
   Empty names and duplicate normalized names add nothing. Typing a suggestion's
   name selects that suggestion rather than creating a second routine. Confirming
   also adds any name still in the input field. Returning from the calendar keeps
   the draft; removing a routine removes its placements too. **Schlafen is the one
   routine that is always there** and is not placed: `SleepRhythmCard` asks for the
   weekday night ("Unter der Woche") and the weekend night ("Am Wochenende"), each
   as bedtime and wake-up, plus which days are the weekend. Defaults 23:00–07:00 and
   00:00–09:00, weekend Saturday and Sunday. Bedtime and wake-up may not be the same
   hour (that is no night, or a whole day of it); the page says so and ignores it.
   The first page starts with the optional Google Calendar connection, one sentence on
   why, then the routines, under three headings: "Feste Grundkonfiguration" (sleep),
   "Vorschläge" (Sport, Achtsamkeit) and "Eigene Routinen".
2. **Schedule the week.** `SetupWeekStep` shows Monday–Sunday with an hourly 00–24
   grid. The viewport starts at 06:00; hours scroll vertically and the labelled day
   columns and their header scroll horizontally together. Tapping an empty time
   creates a temporary **one-hour** placement, snapped to 15 minutes, and opens its
   editor. The routine, weekday, start and duration can be changed. Existing blocks
   can be edited or deleted; cancel changes nothing. The alternative "Zeitplatz
   hinzufügen" button opens the same editor. Durations run from 15 minutes through
   23 hours 45 minutes. Overnight placements are split visually across weekdays,
   including Sunday into Monday. Overlapping placements occupy separate lanes and
   show a warning; warnings do not forbid intentional overlaps. With no routine
   besides sleep chosen there is nothing to place: the page says so and the tap and
   the button do nothing.
   **Calendar in the week (#30, #31).** When a calendar is connected, `SetupViewModel.enterWeek()`
   reads the next seven days (today and six after, `setupCalendarLastDay`) through
   `CalendarSyncService.refresh` and then prefills the template. Appointments are drawn
   behind everything in their weekday columns, read-only, in the calendar colour with
   their title. All-day events are a band at the top of their day and never warn. Tapping
   an appointment hides it for this setup only (`hideEvent`, in memory with the draft;
   "Alle einblenden" restores it). Hiding deliberately does **not** call
   `CalendarRepository.ignore()`: that decision is kept for good and would also remove
   the appointment from the planning steps. A failed read is one sentence above the grid,
   and the setup goes on without it.
   **Prefill.** Each selected routine that was never placed gets one hour in the first
   free hour from 07:00 to 22:00, counting Monday first, free of sleep, placements and
   visible appointments (`prefilledWeek`). A routine given a slot once is not refilled, so
   a deleted slot does not return. It is edited like any placement.

### Sleep: one night per weekday, each correctable

Somebody sleeps at least once in 24 hours, and **when** is the user's to say — so
sleep is not pinned to one hour. The week always has **seven nights, one per wake
day** (`SleepNight`, `RoutineSetup.sleepNights()`): a night belongs to the day it
ends on, and `startDay` is the evening before or, for a bedtime after midnight, the
wake day itself. Each night is individually editable; deleting the mandatory night
is not offered. Seven nights alone do not guarantee sleep within every 24 hours:
`UserSetup.longestAwakeMinutes()` merges their intervals and checks the cyclic week,
including Sunday into Monday. Setup completion and settings save refuse a gap
longer than 24 hours. Temporary gaps while editing remain editable so adjacent
nights can be corrected one after another.

- The first page's two answers only **seed** the nights: the calendar draws one
  provisional sleep block per night from them.
- **A tap on a sleep block opens `SleepEditor`** for exactly that night: bedtime and
  wake-up, the resulting span ("Montag 23:00 bis Dienstag 07:00 · 8:00 h"), and — once
  the night differs from the pattern — "Wie die übrigen Tage". Saving stores a
  correction in `UserSetup.nightOverrides[wakeDay]`. A night put back exactly where
  the pattern has it **stops being a correction**, so a later change to the pattern
  moves it along again; corrections the user made are otherwise kept when the first
  page's times change.
- **Two nights may not overlap** (`RoutineSetup.overlappingSleep()`): the hours of
  both would count as slept twice in the free-time maths. The editor refuses to save
  such a night, and "Fertig" is disabled with the days named if the first page makes
  two overlap. An overlap of sleep with an ordinary routine is only a warning, like
  any other overlap, and sleep against sleep is never reported as a routine clash.
- No winding down and no morning are asked: first-run nights have `bedPrep == sleep`
  and `morningDuration` zero, so the settings-owned definitions come out empty.

`nightEndingOn(weekday)` is the one place that decides a night — correction first
(`nightOverrides`), then the standing pattern (`patternNightEndingOn`): an explicit
`nightsByDay` entry where configured, otherwise the weekend or weekday night.
Everything downstream reads it: planner shading, weekly sleep totals, reevaluation,
the wake alarm and the recurring bed-preparation and morning definitions. Those
definitions are split by wake day whenever an explicit per-day schedule, a weekend
night or any correction is configured.

### Draft, definitions and completion

- **`RoutineSetup`**, `SetupRoutine`, `RoutinePlacement` and `SleepNight` in
  `RoutineSetup.kt` hold the in-memory first-run draft. `RoutineSetup.draft` disables
  the optional answers of `UserSetup.draft`; the latter remains the baseline
  configuration the tutorial uses.
- **Schema 33** adds `user_setup.nightOverrides` (`TEXT NOT NULL DEFAULT ''`):
  `DAY=bedPrep,sleep,wake` entries joined by `;` in week order
  (`encodeNightOverrides` / `decodeNightOverrides`, the same converter pair as
  `nightsByDay`). Upstream rows start with no corrections; the direct fork-28 upgrade
  retains existing corrections. See `database.md`.
- **`UserSetup`** remains the single-row Room entity (`SETUP_ID = 0`). Its encoded
  answers (`MealPlan`, `HousekeepingPlan`, `MindfulnessPlan`, `WorkSchedule`,
  `WeeklySlot`, `DailySlot`) and the settings' sleep/planning pages remain supported.
  `SetupEncodingTest` guards their round trips, `nightOverrides` included.
- **One recurring definition per placement**, with a stable
  `setup:routine-{placement.id}` id and a weekly recurrence on its chosen day.
  Multiple times of the same routine on the same day therefore remain separate
  occurrences without violating the `(itemId, date)` unique index. Sport carries
  `FOKUS` / `SPORT`, Achtsamkeit `ACHTSAM` / `MINDFULNESS`; custom routines carry no
  inferred category or role and can be edited later on Lists.
- **Every selected non-sleep routine must have a placement** before "Fertig" is
  enabled; the screen names those still missing. No unselected cooking, housekeeping,
  work, free-time, winding-down or morning tasks are silently added. Planning starts
  with the existing defaults (daily 20:00, weekly Sunday 18:00), editable in Settings.
- **`SetupRepository.complete(setup, routineItems)`** merges the supplied routine
  definitions with any settings-derived definitions, preserves existing
  `createdAt`, retires obsolete setup-owned definitions via `completedAt`, and
  clears only their still-open future occurrences. Completed and discarded blocks
  remain history. The first 14-day horizon is materialized with `insertMissing`;
  the setup row is published **last**, because it unlocks the root screen.
  Ordinary `ScheduleMaintenance.topUp()` continues the standing schedule afterwards.
- **Overlap checks** use `RoutineSetup.weeklySpans()` and `conflicts()`. Unlike the
  legacy answer-based helper, two blocks with the same routine name can conflict.
  `RoutineSetupTest` covers independent same-date occurrences, midnight wrapping,
  deselection, overlap detection, stable identities, the seven provisional nights,
  a single corrected night, sleep overlap and "nothing to place".
- **Warnings and blocking** (#40). Routine against routine, routine against sleep and
  routine against a visible appointment (`calendarClashes`) are warnings only. Each line
  names both blocks with weekday and times and says that you can still finish. Overlapping
  nights (`overlappingSleep`), a routine without a slot and a gap over 24 hours block
  "Fertig", and the page names the reason.
- **Feature suggestions (#13).** `dueFeatureSuggestion` (`FeatureSuggestion.kt`) offers at
  most one of growthTasks, contracts, rewards, notes: first 7 days after `completedAt`,
  then one per 7 days, only features that are off and not answered. Answers live in the
  SharedPreferences `feature_suggestions` (`FeatureSuggestionStore`). "Ausprobieren" enables
  the feature through `setupRepository.update { withFeature }` (the settings setter's path)
  and offers its tutorial. "Später" changes only the last-offer day, and "Nicht mehr
  vorschlagen" only the answers. Nothing is enabled without the tap, and the card is absent
  from the tutorial dashboard.
- Tests: `SetupCalendarTest` (appointment spans, clashes, prefill) and `FeatureSuggestionTest`
  (the schedule, including the 7-day gaps and time zone).
- The old optional questionnaire pages, summary, `SetupPart` and `skipping` path
  have been removed. `SetupSteps.kt` retains only the shared sleep and planning
  settings pages. `SetupItems.kt` / `SetupSchedule.kt` still serve settings,
  tutorial configuration and existing encoded answers.

### Sleep and settings

`NightTimes(bedPrep, sleep, wake)` describes a night in offsets from the wake day's
midnight; offsets before midnight are negative. A night **belongs to the day it
ends on**. `UserSetup.weekendNight` is nullable; null means every night is alike.
`weekendDays` identifies the days it ends on, defaulting to Saturday and Sunday.

With a separate weekend night, an explicit `nightsByDay` schedule or any correction,
bed preparation and morning definitions are split by wake day
(`setup:bedprep-saturday`, `setup:morning-saturday`). Otherwise they remain daily.
`isOwnedBySettings` identifies these definitions; `saveSettings` regenerates only
them, leaving first-run and later ordinary routines alone. Their morning steps are
copied across the new definitions.

The settings retain all three upstream rhythm modes and next-day controls.
**Individually corrected nights** appear under "Einzelne Nächte" with bedtime,
wake-up and a reset to the standing pattern. The wake-alarm card says
"zur Aufstehzeit des jeweiligen Tages" while per-day hours or corrections exist.

The after-midnight hint and weekday labels are derived from
`NightTimes.sleepDays` / `bedPrepDays`: 01:00 on the night into Sunday is Sunday,
not Saturday. `WeekendNightTest` keeps this boundary, the weekend wake alarm and a
corrected night's morning, alarm and weekly sleep covered.

### Verification

Before the upstream rebase, the first page, calendar and stored result were exercised on a physical
phone using a temporary, in-process instrumentation configuration and
`AppContainer(sandbox = true)`: weekday/weekend sleep, a selected suggestion and a
custom routine, the weekend-day picker, opening a night's editor, a correction and its
reset, refusing overlapping nights, placing routines, and completing setup with the
stored `nightOverrides` and materialized occurrences read back from Room. The 27→28
migration was opened through Room on a **copy** of the phone's real version-27
database. The user's `erik.db` was not reset. Temporary instrumentation sources,
manifest entries and dependencies are not part of the shipped implementation.
An additional phone scenario changed weekday/weekend presets and a single night
through actual taps on the clock dial, verified that other nights did not change,
reset the correction, and refused completion after a 41-hour sleep-free interval.
It then saved a valid corrected night and checked planner shading, the next wake
time and the settings save guard against the stored Room row.

After the rebase, a temporary Robolectric smoke exercised the real
`AppContainer(sandbox = true)` and Room schema 33: completing a Sport routine at
Tuesday 18:00, persisting a Wednesday-only sleep correction, completing again
without duplicate occurrences, and saving a standing-night edit without losing
the routine. The smoke source was removed. The merged Compose screens and
device alarm behavior have not been checked on a physical device.
