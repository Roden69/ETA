---
paths:
  - "app/src/**/ui/lists/**"
---

### The "Smart toDos" (Listen) tab

**The Sperrliste is the last box** since step 36, after Termine, at the user's
request; the order of the others is `Konzept.md`'s.

`ui/lists/` shows the six lists of `Konzept.md` stacked on one screen, and is
**read-only about placement** — since step 35 with three long presses that *make*
something new, under the rules of the list it is made for; see *Step 35*. It is read-only because every one of these lists is what a phase left behind
— the Sperrliste of the weekly sweep, the Wochenliste of the weekly planning, the
Tagesliste of the day planner — so *moving* a card here would be a second route
around the rules those phases enforce. The way to move a card is to run the phase
that moves it. Keep it that way when adding to this screen.

It needed no new data at first: `observeStage`, `observeDay`, `observeDayPlan` and
`observeErfolgsliste` already carried everything. Sections collapse, and since
step 31 **every one starts closed and then stays the way it was left**, across
restarts: each is a `Fold` (`ui/components/Fold.kt`, `rememberFold("lists.<SECTION>")`),
which writes the answer to `SharedPreferences` the moment it changes.
`rememberSaveable` would only have survived a rotation. Use `rememberFold` for any
new section that should be remembered, with the screen as the key's prefix. The
one gesture from the concept: **long-pressing "Liste für Morgen" opens the
planner**, which is how that list is reopened for editing.

Two sections are not among the concept's six, and both exist because nothing else
on the screen could hold them:

- **`ListSection.TERMINE`** — imported appointments **strictly after today**,
  cancelled ones left out. Every other list here is today, tomorrow or an undated
  backlog, so an appointment three weeks out was in the database, drawn in a
  planner nobody has opened, and visible nowhere. Strictly after, because today is
  the Tagesliste's and tomorrow the Morgenliste's. The rows lead with the **date**,
  unlike every other list here, since the day is the thing being looked up.
  Read-only: it is a block, and the planner owns when it happens.
- **`ListSection.WIEDERKEHREND`** — every live recurring definition, and unlike the
  lists around it **editing is its purpose**: a day holds one occurrence, and this
  is where the task itself is. Everything the questionnaire laid down appears here
  without any migration, those rows always having been ordinary definitions. See
  *The standing schedule* under *Step 18*. Since step 25 the box opens with the
  **incomplete recurring notes** (moved out of the Sammelliste on this screen only —
  they still sit in `Stage.COLLECTION` and on its one-month clock) and the
  **Wochenschema** below them — see *Step 25*.

**The chart's colours are named under it** (issue #35). `WeekSchemeChart` draws
free time as the plain surface, what is booked as the grey, each task in its own
colour (`ChartBar`, one per task, in lanes where stretches overlap) and the form in
edit as the accent, with a collision in the danger colour. Its `WeekLegend` lists
exactly those colours with the task's name, so a bar is found by colour and name.
Editor previews and the list use the same free-time colour.

**Attributes are editable, placement is not.** A wrong duration or priority is a
typo, and running a whole planning phase to fix a typo is the tail wagging the dog.
Tapping a row opens `ItemDetailDialog`, which offers **Bearbeiten** and
**Erledigt** on the **Sammelliste** and the **Wochenliste** only, with deleting in
a wastebasket in the corner — see *Step 21*. Tages- and Morgenliste show the name
and nothing else; the Sperrliste is a rule with a date on it; the Erfolgsliste has
no card behind its rows at all, so a long name there gets three lines rather than a
popup that could add nothing.

**A row of the Tagesliste leads into today's planner** (after step 38). Its popup
— tap or long press, the same window as everywhere on this tab — carries "Im
Tagesplan öffnen" (`ItemDetailDialog.onOpenPlanner`), and the planner opens
**centred on that entry**: `OpenedRow.plannerMinute` is the middle of the block,
handed up as `onOpenToday(minute)`, held in `MainScaffold` as `plannerFocus` and
passed to `DayPlannerScreen(focusMinute = …)`. A minute rather than a block id on
purpose: a finished block is not among the planner's blocks (it is a freed
stretch) and a cancelled one is not drawn at all, and both are rows of this list
— a place in the day is the one thing all three have. The focus is cleared when
the flow is left, so "Heute umplanen" opens an hour before now as it always did.
The tab still moves nothing itself; this is the way to the screen that does.

### The standing schedule, as the list shows it

**One block per task, one row per weekday and variant** (`recurringTasks` in
`domain/recurrence/RecurringSchedule.kt`). A task whose weekdays have different
hours or lengths ("Mo 14:00 · 1:00 h, Mi 17:00 · 0:30 h, Fr 13:00 · 0:15 h") is one
block under its name; each row carries its own day, hour and length. Considered:
one line per variant with aligned lists ("Mo, Mi, Fr · 14:00, 17:00, 13:00 ·
1:00, 0:30, 0:15"). Rejected, because the columns only read correctly while their
order is kept, and an added or dropped weekday silently shifts the others. Rows never
merge variants into one value.

**Order:** tasks by their earliest hour, then name; rows Monday to Sunday, and by
hour within a day. The screen says so. Tapping a row opens the variant it belongs to
(`RecurringGroup`), so an edit changes that variant's hour and length only.

**A second time slot** ("Weiterer Zeitplatz" in the task's dialog) opens the create
form on a copy of the routine: same name, note, days and steps, a new definition. The
two definitions expand independently, so both lay down a block on the same day, and
each is checked off, settled and scored on its own. No duplicate-name check applies
to standing tasks, and the overlap warning is the only thing that compares them.

### Follow-up ToDos (Folgeaufgaben)

A ToDo can carry an ordered list of **follow-up steps** in the editor ("Danach").
Each step is a ToDo of its own, created with `Item.newFollowUp`, which takes the
predecessor's category, priority, length and journeys. `items.followUpOfId` is the
link, kept after the step is released.

- **Waiting** = `followUpOfId` set and `enteredCollectionAt` null. A waiting step is
  not pullable (`PullBlock.AfterPredecessor`), not a fold candidate, and does not age
  towards the Sperrliste, since the clock starts at `enteredCollectionAt`. The
  Sammelliste shows it as "danach: …".
- **Released** when the predecessor is **checked off** (a block completed through
  `PlanRepository.complete`, or "Erledigt" on the Listen tab, or a finished card via
  `ItemRepository.update`). It gets `enteredCollectionAt = now` and is offered:
  "»X« ist jetzt dran" with "In die Wochenliste" (same gate as every other pull). A step
  that was never filled in is not taken into the week: the notice asks for "Bearbeiten"
  first (`ItemRepository.takeIntoWeek` would keep it in the Sammelliste anyway, see
  `week-planning.md`, "Every ToDo in `Stage.WEEK` is concretized").
- **Only the next step** is released: a step waits for the one before it, so the
  order is enforced and any amount of time may pass between steps.
- **Ticking the predecessor back** makes an offered step that is still in the
  Sammelliste wait again (clock stopped). A step already taken into the week or a day
  stays where it is: the user committed to it.
- **Deleting the predecessor** releases its waiting step: a gone predecessor cannot hold it back.
  Deleting a waiting middle step reparents its successor to that step's predecessor, retaining
  the waiting state and order (A→B→C becomes A→C).
- **Folding** a card into a group ("Aufgaben als Schritte übernehmen", or dropping it
  onto another in the planner) does not complete it. Its waiting successor is retargeted
  to the surviving group and waits for that group's next relevant completion.
  A waiting step cannot be marked "Erledigt" on the Listen tab and is not offered for folding.
- Steps are created when the card is created; on an edit they are appended behind the
  existing chain. Blank names are dropped. Every user-chosen follow-up name is checked
  against the Sperrliste before any write; automatic make-up items retain their exemption.

The pure rules are `domain/chain/FollowUpChain.kt` (`isWaitingFollowUp`,
`isOfferedFollowUp`, `followUpsAfter`); the writes are `data/repository/FollowUps.kt`.
Known limit: a predecessor that is only checked off on its block is detected through
that block; an edit that adds steps to an already-finished card relies on
`isFinishedForChain` (stage DONE or `completedAt`) and does not see a block-only
check-off.
