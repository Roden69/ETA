---
paths:
  - "app/src/**/domain/setup/**"
  - "app/src/**/ui/setup/**"
  - "app/src/**/SetupRepository.kt"
  - "app/src/**/Setup*Test.kt"
  - "app/src/**/RoutineSetupTest.kt"
---

### First setup: routines, then a weekly calendar

The first-run intro still precedes setup, and the existing tutorial follows its
completion. `SetupScreen` now has **two confirmed steps**, not the old questionnaire:

1. **Choose routines.** Schlafen is mandatory. Sport and Achtsamkeit are optional
   suggestions; a text field adds custom routines, such as Yoga machen or Lernen.
   Empty names and duplicate normalized names add nothing. Typing a suggestion's
   name selects that suggestion rather than creating a second routine. Confirming
   also adds any name still in the input field. Returning from the calendar keeps
   the draft; removing a routine removes its placements too.
2. **Schedule the week.** `SetupWeekStep` shows Monday–Sunday with an hourly 00–24
   grid. The viewport starts at 06:00; hours scroll vertically and the labelled day
   columns and their header scroll horizontally together. Tapping an empty time
   creates a temporary **one-hour** placement, snapped to 15 minutes, and opens its
   editor. The routine, weekday, start and duration can be changed. Existing blocks
   can be edited or deleted; cancel changes nothing. The alternative "Zeitplatz
   hinzufügen" button opens the same editor. Durations run from 15 minutes through
   23 hours 45 minutes. Overnight placements are split visually across weekdays,
   including Sunday into Monday. Overlapping placements occupy separate lanes and
   show a warning; warnings do not forbid intentional overlaps.

Sleep starts at **23:00–07:00 every day**. Tapping a sleep block changes the start
and duration for **all nights**, not just the tapped column. It cannot be removed
or turned into another routine. Sleep remains configuration, not a task to check
off. Different weekend nights can still be configured later in Settings.

Every selected non-sleep routine must have a placement before "Fertig" is enabled;
the screen names those still missing. No unselected cooking, housekeeping, work,
free-time, winding-down or morning tasks are silently added. Planning starts with
the existing defaults (daily 20:00, weekly Sunday 18:00), editable in Settings.

### Draft, definitions and completion

- **`RoutineSetup`**, `SetupRoutine` and `RoutinePlacement` in `RoutineSetup.kt`
  hold the in-memory first-run draft. `RoutineSetup.draft` disables the optional
  answers of `UserSetup.draft`; the latter remains the baseline configuration the
  tutorial uses. There is no new table or schema migration, and existing users
  keep their configuration and standing tasks.
- **`UserSetup`** remains the single-row Room entity (`SETUP_ID = 0`). Its encoded
  answers (`MealPlan`, `HousekeepingPlan`, `MindfulnessPlan`, `WorkSchedule`,
  `WeeklySlot`, `DailySlot`) and the settings' sleep/planning pages remain supported.
  `SetupEncodingTest` guards their round trips.
- **One recurring definition per placement**, with a stable
  `setup:routine-{placement.id}` id and a weekly recurrence on its chosen day.
  Multiple times of the same routine on the same day therefore remain separate
  occurrences without violating the `(itemId, date)` unique index. Sport carries
  `FOKUS` / `SPORT`, Achtsamkeit `ACHTSAM` / `MINDFULNESS`; custom routines carry no
  inferred category or role and can be edited later on Lists.
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
  deselection, overlap detection, stable identities and mandatory sleep.
- The old optional questionnaire pages, summary, `SetupPart` and `skipping` path
  have been removed. `SetupSteps.kt` retains only the shared sleep and planning
  settings pages. `SetupItems.kt` / `SetupSchedule.kt` still serve settings,
  tutorial configuration and existing encoded answers.

### Sleep and settings

`NightTimes(bedPrep, sleep, wake)` describes a night in offsets from the wake day's
midnight; offsets before midnight are negative. A night **belongs to the day it
ends on**. `UserSetup.weekendNight` is nullable; null means every night is alike.
`weekendDays` identifies the days it ends on, defaulting to Saturday and Sunday.
`nightEndingOn(weekday)` is the common decision point for shading, free-hour maths,
recurring framework tasks and the wake alarm.

With a separate weekend night, bed preparation and morning definitions are split
by wake day (`setup:bedprep-saturday`, `setup:morning-saturday`). Without it they
remain daily definitions. `isOwnedBySettings` identifies these definitions;
`saveSettings` regenerates only them, leaving first-run and later ordinary
routines alone. Their morning steps are copied across the new definitions.

The after-midnight hint and weekday labels in the settings page are derived from
`NightTimes.sleepDays` / `bedPrepDays`: 01:00 on the night into Sunday is Sunday,
not Saturday. `WeekendNightTest` keeps this boundary and the weekend wake alarm
covered.

### Verification

The two-step screen was exercised on a physical phone using a temporary,
in-process instrumentation configuration and `AppContainer(sandbox = true)`.
The scenario selected both suggestions, added custom routines, tapped a one-hour
slot, edited duration, cancelled and deleted slots, changed daily sleep, assigned
weekdays, completed setup and checked the stored Room definitions and materialized
occurrences. The user's `erik.db` was not reset. Temporary instrumentation sources,
manifest entries and dependencies are not part of the shipped implementation.
