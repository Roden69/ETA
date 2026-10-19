---
paths:
  - "app/src/**/ui/setup/**"
  - "app/src/**/domain/setup/**"
  - "app/src/**/ui/week/**"
  - "app/src/**/ui/weekplanner/**"
  - "app/src/**/ui/lists/WeekSchemeChart.kt"
  - "app/src/**/domain/recurrence/**"
  - "app/src/**/ui/attributes/**"
  - "app/src/**/alarm/EtaSound.kt"
  - "app/src/**/ui/planner/Timeline.kt"
---

### Step 46: the setup rebuilt, every other week, a task's own colour, audio modes

Two bugs, one change, the setup, three features from `update.txt`, four
questions put first. Database **version 32**: `items.color`.

#### The answers that are rules now

- **The Morgenroutine bug is parked** ("Überspring das erstmal"): after a debug
  reset, steps added to the morning routine are said not to be saved. The save
  path reads correct and could not be reproduced by reading; it is **open**.
- **A task wears its category's colour, or one of its own.**
- **"Termine" are imported Google appointments**, nothing else.
- **Vibration and silence apply to everything but the wake alarm.**

#### A colour of the task's own

`Item.color` (ARGB, null = the category's), carried in `ItemExtras.color`, set
by `ColorRow` in the Extras box (`ACCENT_PRESETS`, `EtaSwatch` — the swatch the
Design card uses, now shared).

- **`colorOf(item)`** in `EtaTheme.kt` is the one place a card's colour is
  decided. Every screen that asked `colorOf(x.category)` asks it now; a new one
  that asks by category will not see a task's own colour. **A standing task with
  neither colour nor category** — the routines the setup lays down — is coloured
  from its name (`Item.isNameColoured`, `routineColorArgb`, the eight
  `ROUTINE_COLORS`, picked by `String.hashCode` of `normalizedName`, so the same
  name has the same colour on every weekday and screen) instead of the grey of
  "unsorted" that made the whole week one grey (#51). Free time and a note not
  yet given its form keep the muted grey. `RoutineColorTest` pins this.
  The setup's week preview (`SetupWeekStep.WeekGrid`) draws `SetupRoutine`s, not
  `Item`s, so it spreads the same palette over the routines without a category
  itself (`assignRoutineColors`). It only knows the routines picked so far, so a
  routine's preview colour can differ from its colour in the week afterwards,
  where every standing task takes part in the spreading.
- **The Wochenschema over the Wiederkehrend list** draws each task as a bar in
  that colour (`weekTaskSpans`, `WeekSchemeChart(tasks = …)`), which is the
  colour of the dot in front of its row. Free time is no longer green there —
  green is a category's. The chart inside a form is unchanged.

#### Every other week

The model always had `RecurrenceRule.Biweekly(weekday, parity)`; nothing let
the user choose it.

- **`RhythmField`** under the weekdays: jede Woche · gerade KW · ungerade KW (a
  monthly rhythm a task still carries is listed as itself). ISO calendar week.
- **It rides in `ItemExtras.rhythm` and is not a column.** `withExtras` ignores
  it; the three save paths read it where they build the rules
  (`edit.extras.rhythm ?: …` in `saveGroup` / `createGroup`, `extras.rhythm` in
  `concretizeRecurring`). `RecurringAttributes.slots()` defaults to it, so the
  overlap check follows a rhythm changed in the open form.
- **Drawn hatched** in `DayTimeline` — leaning left in even weeks, right in odd
  — with the **name at the top (even) or the bottom (odd)**: in "Generell" a
  fortnightly task stands on its weekday every week, so two that alternate lie
  on top of each other, and blocks are translucent enough for both to read.

#### Woche: "Termine anzeigen", and any seven days

- **`blocksOn(…, appointments)`**: in "Generell", this week's
  `CALENDAR_IMPORT` blocks over the standing week. Shown and nothing more — a
  long press gets the one-off sentence.
- **`WeekTabViewModel(from = …)`**: the seven days from any date; `dateOf(day)`
  looks the date up instead of counting from Monday.
- **`WeekTabScreen(onlyView, showAppointments, header, footer)`**: one view
  offered with the other half of the switch greyed.
- **"Anpassungen vornehmen"** on the weekly planning's "Auffüllen" page lays
  that timetable **over** the phase (`weekAdjust`, `WeekAdjustScreen`) — over,
  not as a flow, so the pager keeps its page. "Aktuell" only: the phase plans
  one week.

#### Audio: Ton, Vibration, Still

`AudioMode` in `EtaSound`, in `SharedPreferences` beside the DND switch.
`play` returns for `SILENT` and buzzes for `VIBRATE` (not under DND unless
played through, not on a phone switched fully silent); `EtaSpeech.speak`
returns false for both, so the caller falls back to `play`. The settings page
"Ansagen" is called **"Audio"** and opens with `AudioModeBox`, applied at once.
**The wake alarm asks none of it.**

#### Setup integration after the routine-onboarding rebase

The first-run questionnaire has been replaced by the two confirmed steps in
`setup.md`: routines and sleep, then the weekly calendar. Its `SetupPage`,
`SetupPart`, checklist-specific pages and custom-task pages no longer exist.
The optional Google connection is retained on the first page.

- **"Folgetag" remains derived, not stored**, in the shared settings `SleepStep`.
  `withSleepNextDay` moves bedtime while keeping winding-down length;
  `withBedPrepNextDay` moves winding down and bedtime across midnight as needed.
  `SetupAndRhythmTest` covers both.
- **Morning steps remain editable in settings and Lists.** First-run setup does
  not silently create a morning task or winding-down task.
- **The standing-week review remains after completion.**
  `SetupViewModel.weekReview` is set before `SetupRepository.complete` publishes
  the setup row. `EtaApp` shows `SetupWeekReview` before the tutorial: the
  dashboard timetable, "Generell" only, appointments visible, with the existing
  four-page explanation. The flag remains in memory only.
- **Individual first-run nights use sparse corrections** over the standing
  pattern. The upstream full per-weekday schedule and next-day settings stay
  supported; schema 33 adds the correction column without changing schemas 28–32.


**Verified by the compiler, the suite (694; 10 in `SetupAndRhythmTest`),
`assembleRelease`, `lint` and the migration against `32.json`.** Nobody has seen
any of it — the questionnaire's pager with pages that come and go least of all.
