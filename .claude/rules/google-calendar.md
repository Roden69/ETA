---
paths:
  - "app/src/**/data/calendar/**"
  - "app/src/**/ui/calendar/**"
  - "app/src/**/*Calendar*.kt"
  - "app/src/**/ConflictRelief*.kt"
---

### Google Calendar, read-only (step 15)

Read-only in the strong sense: Eta never writes to a Google calendar, and nothing it
imports may change what Google holds. What it takes from an event is **name, day,
start and length**; category, the journeys, the break and the end sound are the
user's, and are asked for on import. It never became a screen of its own — what the
user asked for is a step inside each planning phase, offering every event to be taken
on or ignored.

**The invariant the whole feature rests on: every event is accounted for.** It is
either a card in the app or explicitly ignored, so anything the calendar offers that
is in neither state is by definition *new* — which is what makes "has something been
put in the calendar for tomorrow since yesterday" answerable without asking Google
what the app used to know. `EventDecision` is those three states, and the "Ignorierte
Events" list in the settings tab is where the third can be taken back.

**Before the ToDos are placed, both times.** That is the user's own rule and it is the
right one: an appointment settled *before* the day or the week is filled is part of
the picture, one settled after it is a collision. So the weekly phase gained a
`Step.CALENDAR` **ahead of** `Step.PLAN`, the mid-week top-up included, and the daily
flow became reevaluation → concretize → **calendar** → planner. The daily one covers
**tomorrow alone** and **skips itself when it has nothing to say** — not connected, or
nothing undecided, calling `onDone` on its first settled sync, because a step that
stopped every evening to report that there was nothing to report would be a tax on
the feature. The skip fires **once**: after the user has decided something the screen
stays and waits for "Weiter", so a decision is never followed by the screen vanishing
out from under it.

**No duplicates, by construction.** `calendar_events.id` **is** the remote identity —
`calendarId + "|" + eventId` — so the same appointment fetched twice lands on the same
row. Events are fetched with `singleEvents=true`, so a recurring appointment arrives
as one dated occurrence per day, each with its own id: one row means one appointment
on one day. A re-sync refreshes the *facts* and never the two fields the user owns
(`decision`, `itemId`), so an appointment moved in Google shows its new time without
being offered a second time. What a sync **does** delete is an event the calendar no
longer offers *and* nobody has decided about: an ignored one has to stay ignored, or a
hiccup would offer it again, and an imported one has become a card the user may
already have worked from.

**What an imported block is and may do.** An ordinary `Item` of type TODO in
`Stage.DAY` carrying `Priority.URGENT_MUST` — the same reasoning as a ToDo dropped
straight onto a slot: a fixed day and hour says something stronger than any of the
four tiers, and a priority is carried at all only because `isConcretized` asks a ToDo
for one. `AppointmentAttributes` is therefore the third shared attribute form, and the
one that asks **no** priority and **no** target date. The Sperrliste is not consulted
(`ItemRepository.addWithoutLockCheck`): the ban exists to stop an idea being hoarded
and retyped, and an appointment somebody put in a calendar is a fact about the world
that refusing to show would not move. The block is `BlockOrigin.CALENDAR_IMPORT`,
which already meant square corners and immovable; added here is **ochre**
(`colors.calendar`) in place of a category colour. Its **time is still editable** in
`BlockEditDialog`, exactly as a recurring occurrence's is; what it cannot do is be
dragged. **All-day events** arrive with a *suggestion* — 09:00 for an hour — flagged
`allDay`, and the form says so, since `Planungsphase.md` asks that the planner not
silently swallow a whole day for one of these.

**Conflicts.** An imported block is placed **at the hour the calendar gives**, never
at the next free one — an appointment does not slide. So it can land on something, and
`CalendarImportService.import` hands back whatever it landed on rather than resolving
it: the appointment is a fact, but what happens to the Sport session now underneath it
is a decision only the user can make. Collisions are compared as **containers**, so an
appointment planned into somebody else's commute is caught.

**A collision is usually partial, and the answers have to be too.** An appointment at
noon does not cancel a morning, it shortens it. `domain/planning/ConflictRelief.kt`
holds the arithmetic for the two partial answers:

- **Kürzen** (`shortenedBefore`) ends the activity where the appointment's claim
  begins: 10:00–13:00 against an appointment at 12:00 becomes 10:00–12:00.
- **Verschieben** (`startedAfter`) keeps the activity whole and starts it once the
  appointment is over, for the mirror case where the appointment clips the front.
- **Fällt aus** — called off. Free, because it is in advance.
- **In die Woche** — a dragged ToDo goes back to the week list; a recurring occurrence
  cannot, so it is called off and a "Nachholen von …" takes its place there, through
  the same `DayClosingService.makeUp` the evening uses. Idempotent via `makeUpItemId`
  — and the block is **re-read between the two writes**, because `makeUp` writes to it
  as well, and calling off the stale copy would put `makeUpItemId` straight back to
  null.
- **Beides stehen lassen** — a real answer. A walk during a phone meeting is two
  things at once on purpose.

Four things about the partial two that are decisions rather than details:

- **Only the activity itself changes.** Anfahrt, Rückweg and Pause keep their lengths:
  they are what the task costs to reach and what it earns afterwards, and neither
  becomes different because the task got shorter. They move with it for free, the
  container being derived from the task's own start and end.
- **Measured against the appointment's container, not its start.** An appointment with
  travel in front of it genuinely holds that time, and cutting only as far as its
  start would leave the two still overlapping — the user would have answered the
  question and watched nothing happen. The block's own tail comes off for the same
  reason.
- **Offered only where the arithmetic allows**, so a button that appears is a button
  that works. Nothing under `MIN_BLOCK_DURATION` — a ten-minute remainder is a
  destroyed activity, not a shortened one, and destroying it is what the other answers
  are for. Nothing past midnight. And **verschieben** is checked with `canPlace`
  against the rest of the day and **checked again when it is applied**: answering one
  collision can fill the stretch the next one was going to move into, and the honest
  answer then is to say so and leave the conflict standing rather than to move the
  block somewhere nobody chose.
- **Not snapped to the grid.** The hour comes from a calendar, and rounding it down
  would silently throw minutes away.

An appointment that swallows a block **whole** offers neither partial answer — it falls
out of the two functions rather than needing a guard: there is nothing to keep in
front and nothing left behind. A block that is already completed offers neither
either; shortening what already happened would rewrite the record of the day rather
than make room in it.

**Talking to Google.** Three pieces, kept apart: `GoogleAuth` knows about tokens and
nothing about calendars, `CalendarRepository` about rows and decisions and nothing
about Play Services, and `CalendarSyncService` is the only thing that needs both — and
therefore the only place that has to turn three kinds of failure (no grant, no
network, a refused API) into a sentence a screen can print.

- **`AuthorizationClient` from `play-services-auth`, not an OAuth library.** It matches
  the app by **package name and signing certificate** against the Android OAuth client
  in the Cloud project, so **no client id and no secret live in this repository** —
  there is nothing here to leak. It hands back a fresh short-lived access token
  whenever asked, so there is no refresh token to store either. The first call raises a
  consent screen; every call after it returns silently while the grant stands, which is
  what makes the evening's automatic sync possible without interrupting anyone.
- The scope is **`calendar.readonly`**, not `calendar.events.readonly`: the narrower one
  reads events but cannot list *which calendars exist*, and choosing among them is the
  whole point of the settings section.
- **The REST API over plain `HttpURLConnection` and `org.json`.** Two endpoints and a
  handful of fields do not justify the megabytes and the second annotation processor
  the Google API client library brings, and this way every field the app depends on is
  visible in one file.
- **A `PendingIntent` travels up, never sideways.** Only an Activity can show Google's
  account picker, so `SyncOutcome.NeedsConsent` carries it as far as the composable,
  which launches it with `StartIntentSenderForResult`.
- **Never throws across a phase.** A planning phase that could not be entered because
  the flat's wifi was down would be one feature holding the whole app hostage.

**Connected** is read off the calendar list rather than a flag of its own: connecting
always fetches it and disconnecting clears it, so **the list is the record** — the same
reasoning that keeps the weekly devaluation reading the ledger. `calendar_sources`
holds one row per calendar the account can see, and `enabled` is the one field a
refresh preserves, being the only one the user wrote. A calendar seen for the first
time is on only if it is the account's own **primary** one: a subscribed holiday
calendar counting towards the day by default is exactly what the checkboxes exist to
prevent. **Disconnecting** deletes what was read from Google and nothing else; cards
already made from it stay, because they are the user's plan rather than Google's data,
and revoking the grant itself happens in the Google account settings, which the dialog
says rather than pretending a local delete reaches that far.

**Limit worth knowing: one grant covers one Google account.** That account's
sub-calendars and everything shared into it all appear, but a second Google account
needs a second grant, and `AuthorizationClient` offers no way to say which account a
*silent* token should belong to. If that matters, the Android **Calendar Provider**
(`READ_CALENDAR`) is the alternative: every calendar the phone already syncs, from
every account, no Cloud project at all — at the price of seeing only what the device
has synced.

**What the Google side needs, once:** a Cloud project with the **Calendar API**
enabled, `.../auth/calendar.readonly` on the OAuth consent screen, the account on the
**tester** list while the project is in testing, and an **Android** OAuth client
carrying this app's package name (`com.example.erik_iteration_2`, or
`com.example.erik_iteration_2.fork` for the fork build) and the SHA-1 of the keystore
it is signed with. One client per package and SHA-1 pair. None of that is configured
in the repository, which is the point of `AuthorizationClient`. The step-by-step
guide for own builds is `docs/calendar-oauth.md`. When authorization fails,
`GoogleAuth` shows a German hint (package, signature, tester) and the original
Play Services message.

### Calendar in the first setup (#30, #31)

The first page of the setup offers the connection before the routines. Once the user
reaches the week step, `SetupViewModel.enterWeek()` calls `CalendarSyncService.refresh`
for today and the six days after it, then reads the undecided events for that range
(`pendingFor`). Those events are drawn read-only behind the setup week. Setup-local
hiding is an in-memory set in `SetupViewModel`, not `CalendarRepository.ignore()`,
because `ignore()` is a permanent decision that would also drop the event from the
day and week planning steps. The setup never writes to Google, and a failed read only
shows a sentence.

### Background refresh, time blockers, series and conflicts (#15, #16, #17, #32, #33, #34)

- **The planning window** (`domain/calendar/PlanningWindow.kt`, `planningWindow`) is
  today through the day before the next weekly planning day, both included; the
  next planning day is the first one strictly after today, so on the planning day
  itself the window covers the seven days it opens. The week planning and "Woche
  ergänzen" read exactly this window and show its dates. Dated appointments are
  only ever read into it, never moved.
- **Background refresh** (`alarm/CalendarRefreshAlarm.kt`): one chained
  `setWindow` alarm (request code 1300, about every 60 min, 10 min window, no
  exact-alarm permission, no WorkManager) while an account is connected. Armed on
  app start (`EtaApplication`, `RootViewModel`), after boot and whenever the
  connection changes in the calendar box (connect, consent, disconnect). The due
  time is kept with each arming (SharedPreferences `calendar_refresh`): a standing
  alarm is not pushed back, but one whose due time has passed — a run killed
  before it re-armed — is armed again. A run arms the next one *before* it reads,
  so a slow network cannot end the chain. Disconnecting cancels alarm and intent;
  the sandbox never arms one. `CalendarRefreshService.run` refreshes the planning
  window and swallows consent/network failures (no Activity to ask). On the
  planning day the window ends the day before the next one; that day's own
  appointments come in through the evening's calendar step for tomorrow.
- **New clashes are announced once** (`domain/calendar/CalendarClashes.kt`,
  `pendingClashes`): an undecided appointment that lands on planned time gets one
  low-importance notification on its own soundless channel `calendar_conflicts`
  (id 6000, tagged by event id); `calendar_events.conflictNotified` records it.
  The calendar step names the blocks each appointment clashes with, and the week
  view ("Aktuell", with "Termine anzeigen") draws imported and undecided
  appointments with their time and marks overlaps with Eta blocks.
- **Three decisions per appointment:** Ignorieren, Übernehmen (points by the
  hour as before) and **"Als Zeitblocker (ohne Punkte)"**, imported with
  `BlockOrigin.CALENDAR_BLOCKER`: it holds its time and is as immovable as an
  import (`BlockOrigin.isCalendarEvent`), and `yieldOf` pays 0 for it. It is
  outside the scoring altogether: unticked it still counts as held time
  (`heldItsTime`), so it is not charged as unplanned, and calling it off costs no
  cancellation (`cancellationCharged`). Without a local decision the normal points
  stay. Nothing is ever written to Google.
- **Series** (`domain/calendar/SeriesPattern.kt`, `SeriesHolidays.kt`): occurrences carry
  Google's `recurringEventId` as `calendar_events.seriesId`. Eta offers a series **by
  itself**: the calendar step (evening and week planner) reads the master of each series
  that has undecided occurrences and is neither adopted nor kept single, and shows **one**
  offer per series — "„Titel“ wiederholt sich jeden Mo 08:00 bis …, als wiederkehrende
  Aufgabe übernehmen?" with **Übernehmen** (opens the form for the whole series) and
  **Einzeln lassen**. `CalendarSyncService.proposeSeries` does the reading; a series Eta
  refuses shows its reason once, acknowledged with "Verstanden". "Einzeln lassen" and the
  acknowledgement are remembered per series in `DeclinedSeriesStore` (SharedPreferences,
  no migration), so the series is not asked again; "Alles zurücksetzen" clears them, so a
  fresh start offers every series again. The occurrence button "Als
  wiederkehrende Aufgabe übernehmen" stays as the manual path. One-off events are never
  turned into recurring tasks.
- **What converts:** `FREQ=WEEKLY` with `INTERVAL`, `BYDAY`, and an end. `UNTIL` ends the
  card on its last day (a UTC instant is read in the user's time zone, and the day before
  is the last one when the instant precedes the occurrence's own time); `COUNT` ends it on
  the date of the last counted occurrence. Both become the card's `repeatUntil`. `EXDATE`
  is accepted (see holidays). `RDATE`, `EXRULE`, non-weekly frequencies, positional
  weekdays and other rule parts are refused with the reason. Weekdays of one every-N-weeks
  series are still grouped by their phase, so they edit as one task.
- **Holidays** (`EXDATE`, a cancelled or moved occurrence): Google leaves these out of its
  expanded events, so the app reads them from what is missing. A sync always reads at
  least from today to the schedule horizon (`SCHEDULE_HORIZON_DAYS`, 21 days), and
  `ScheduleMaintenance.topUp` lays no block of an adopted card on a day inside that
  covered window where a switched-on calendar shows no occurrence of the series
  (`seriesHolidays`). An open block on such a day is removed; a completed or ticked-off
  one stays. Derived from the stored occurrences on every run, so nothing extra is
  stored. A series with no occurrence at all in the window is no evidence and keeps its
  blocks. Days beyond the horizon are not yet known and are handled once they come within
  it. A series that starts after the horizon lays blocks until its first occurrence is
  read, and corrects itself within the horizon.
- **Sync window:** undecided occurrences outside the planning stretch are stored but not
  shown until their day is in it; an occurrence of an adopted series that Google no longer
  shows is deleted. The background refresh tops the schedule up after a successful read,
  so a holiday drops off without waiting for the next app start.
- **Single imports** keep the rule above: today's and ticked-off ones stay as they are, and
  `ScheduleMaintenance.topUp` does not lay the series' block on their dates. Later changes
  to the series in Google are not followed; the card keeps the rhythm and end date it was
  made with.
- **Conflict answers while planning:** skip this occurrence, shorten, move after
  the appointment, or **split around it** (`splitAround` in `ConflictRelief.kt`):
  the block keeps the part in front, the part behind becomes a make-up ToDo in
  the week list (`Item.newMakeUpTodo`), because one block per `(itemId, date)`
  stays the rule. A block that already carries a make-up is not split again. The
  standing series is never changed by an answer; the appointment keeps priority.
- **System Back** in the calendar step returns to the concretizing step; only
  "Weiter" moves on to the planner.
