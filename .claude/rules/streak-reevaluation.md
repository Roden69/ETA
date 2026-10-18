---
paths:
  - "app/src/**/domain/streak/**"
  - "app/src/**/ui/reevaluation/**"
  - "app/src/**/CatchUpService.kt"
  - "app/src/**/PlanningPhaseService.kt"
---

### The streak, and what keeps a day honest

Three layers hold the daily phase together: **the alarm** rings every five minutes
until the phase is done, **`PhaseBox`** on the dashboard says what is still owed so
a dismissed notification does not leave the day untracked, and **the streak**
(`domain/streak/Streak.kt`) counts consecutive settled days.

`streakOf` measures from **yesterday** while today is still unsettled: a day being
lived has not been skipped, and a streak that read zero every morning would say
nothing. Miss an evening and the run is gone; there is no partial credit, and that
consequence is the mechanic rather than a side effect of one.

**"Done" means both halves.** `PlanningPhaseService.isCompleted(DAILY)` requires
today to be *settled* and tomorrow *confirmed* — `DayPlan` carries `settledAt`
beside `confirmedAt` for exactly this. Reading one as the other let a day be planned
ahead at lunchtime and then never settled: the alarm went quiet and the harvest
vanished without a word.

**Catch-up has two windows.** The last seven days (yesterday and the six before it)
are offered on the dashboard as "Tagesabschluss vom … nachholen" and settled by the
ordinary reevaluation, one tap each: a missed evening is the most common way the
rhythm breaks, so its repair is not hidden. They come oldest first, and each can be
skipped with "Überspringen", which leaves it unsettled. `CatchUpService.dailyDays()`
selects them with the pure rule `unsettledDays` (`domain/reevaluation/CatchUp.kt`).
A caught-up evening (`ReevaluationUiState.catchUp`, the date before today) asks no
contracts and pays none, exactly like the phrase route: a contract is checked on its
evening, and a late answer must not pay it or move `lastCheckedOn` back. Its journal
gets the note "Nachgeholt", and each missed day opens on its own first page.

Older days, up to 30, stay behind the phrase `TECHNISCHE SCHWIERIGKEITEN` in the
settings tab. `CatchUpService.catchUp` pays the day's harvest but **never contract
points** — a contract is attested to on the day, and saying a week later that it was
kept is a different act this service cannot witness. The journal keeps a note, so a
made-up day is never indistinguishable from a lived one. The list of those days stays
folded until the phrase is typed: the count and the warning have to be visible, but
thirty un-actionable rows should not push the rest of the screen away.

### Reevaluation: every block answered before moving on

The first step of the Tagesabschluss gates both the "Weiter" button and the pager
swipe until no block is `isOpen`. An unanswered block is neither harvest nor
forfeit and costs the points either way, and the streak rests on that settlement. A
day with nothing planned leaves the gate open; going back is never blocked. A group
whose steps are not all ticked offers to carry the rest over — see *Step 23*.

**Pager.** System Back turns back one page while the Tagesabschluss is not on its
first page, and only then leaves it. Each page keeps its own scroll position, held by
the screen rather than the page, because the pager composes only the pages near the
one in front.

### Making up a missed evening

- **The run.** `CatchUpBannerViewModel.start` reads the days afresh and yields a
  `CatchUpRun`: the days oldest first, and whether today's plan is still unconfirmed.
  Each day opens `ReevaluationScreen` for that date (`ReevaluationViewModel(date)`),
  with the same verdicts and the same booking as an evening. "Überspringen" and the
  final booking both move on. Once the list is empty the run ends in today's planner
  when today is unplanned, and on the dashboard otherwise.
- **Booked once.** The view model reads `DayPlan.settledAt` on every refresh. A day
  already booked, for instance by an alarm that opened the evening late, leaves the
  screen at once; a second booking while one is running is refused.
- **Not in the tutorial.** The dashboard offer is not composed in the sandbox.

### The evening after the day closing (#18, #44)

After tomorrow's plan is confirmed and the planner is closed, `ui/evening/EveningScreen`
shows what applies, in order (`eveningSteps` in `domain/planning/Evening.kt`):

1. **The wake question**, only with the clock app as wake route (`EveningStore`), the
   wake alarm wanted, and tomorrow confirmed: "Soll ich deinen Wecker für HH:MM in …
   stellen?" A yes hands one alarm to the clock app (`ClockAppAlarms.setOnce`: hour,
   minute, message, no weekdays). A clock app that has gone is named, and Eta rings
   instead. The question is put at most once a night, whatever the answer.
2. **The wind-down hint** ("Abendruhe"): a calm card with "Gute Nacht" and "Nicht mehr
   anzeigen", which switches the Settings toggle "Abendruhe-Hinweis" off. Today's
   bed-preparation steps are listed when they are set up. Nothing is locked.

With nothing to say, the screen ends at once and the flow returns to the dashboard.

