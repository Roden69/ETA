package com.example.eta.scenario

import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.TimeSpan
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WorkBlock
import com.example.eta.domain.setup.WorkSchedule
import com.example.eta.domain.setup.WeeklySlot
import com.example.eta.domain.subtask.SubtaskDraft
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.DatePeriod

/** What the setup questionnaire hands over: the answers, and the standing tasks it lays down. */
data class SetupPersona(
    val setup: UserSetup,
    val standingTasks: List<Item>,
    val morningSteps: List<SubtaskDraft>,
)

/**
 * A card for the Sammelliste together with what is stored with it: its steps, and
 * the follow-up chain that waits behind it.
 */
data class SeedCard(
    val item: Item,
    val subtasks: List<SubtaskDraft> = emptyList(),
    val followUps: List<String> = emptyList(),
)

/**
 * Sample data for scenarios: persona "Lena", built only through the real factories
 * (`UserSetup.draft`, `Item.newTodo`, `Item.newQuickTodo`, `Item.newRecurring`, …),
 * so every scenario starts from a state the app can actually reach.
 */
object SampleData {
    /**
     * Lena's setup: wakes at 07:00 on weekdays and 09:00 at the weekend, works
     * Mon/Wed/Fri 13:00–17:00 with a pause, has a three-step morning routine,
     * sport on Tue/Thu, weekly planning on Sunday 18:00, daily planning at 20:00
     * and the devaluation on Monday. Laundry every second Saturday and study on
     * Tue/Thu evenings come on top.
     */
    fun lenaSetup(now: Instant): SetupPersona {
        val lunchPause = TimeSpan(LocalTime(15, 0), LocalTime(15, 15))
        val work = WorkBlock(span = TimeSpan(LocalTime(13, 0), LocalTime(17, 0)), pause = lunchPause)
        val setup = UserSetup.draft(now).copy(
            wakeTime = LocalTime(7, 0),
            bedPrepTime = LocalTime(22, 0),
            sleepTime = LocalTime(23, 0),
            weekendNight = NightTimes(
                bedPrep = LocalTime(22, 30),
                sleep = LocalTime(23, 30),
                wake = LocalTime(9, 0),
            ),
            sport = WeeklySlot(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), LocalTime(17, 15), 1.hours),
            work = WorkSchedule.PerWeekday(
                mapOf(
                    DayOfWeek.MONDAY to listOf(work),
                    DayOfWeek.WEDNESDAY to listOf(work),
                    DayOfWeek.FRIDAY to listOf(work),
                ),
            ),
            weeklyPlanningDay = DayOfWeek.SUNDAY,
            weeklyPlanningTime = LocalTime(18, 0),
            dailyPlanningTime = LocalTime(20, 0),
            inflationDay = DayOfWeek.MONDAY,
        )
        val standing = listOf(
            Item.newRecurring(
                name = "Lernen",
                category = Category.FOKUS,
                recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.TUESDAY),
                startTime = LocalTime(19, 30),
                estimatedDuration = 1.hours,
                now = now,
            ),
            Item.newRecurring(
                name = "Lernen",
                category = Category.FOKUS,
                recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.THURSDAY),
                startTime = LocalTime(19, 30),
                estimatedDuration = 1.hours,
                now = now,
            ),
            Item.newRecurring(
                name = "Wäsche",
                category = Category.NEBENBEI,
                recurrenceRule = RecurrenceRule.EveryWeeks(DayOfWeek.SATURDAY, weeks = 2, anchor = LocalDate(2026, 10, 24)),
                startTime = LocalTime(15, 0),
                estimatedDuration = 1.hours,
                now = now,
            ),
        )
        val morning = listOf(
            SubtaskDraft(name = "Wasser trinken"),
            SubtaskDraft(name = "Zähne putzen"),
            SubtaskDraft(name = "Fenster lüften"),
        )
        return SetupPersona(setup, standing, morning)
    }

    /**
     * Lena's Sammelliste, about two dozen cards: twelve concretized cards of mixed
     * category, priority and length (15 minutes to 3 hours), six quick notes, two
     * with an unlock day, a three-step follow-up chain, one with steps, one
     * deadline, and one entered long ago that the Sperrliste should catch.
     */
    fun lenaCollection(now: Instant, zone: TimeZone): List<SeedCard> {
        val concretized = listOf(
            Triple("Steuerunterlagen sortieren", Category.FOKUS, Priority.MUST to 90),
            Triple("Zahnarzt anrufen", Category.NEBENBEI, Priority.URGENT_MUST to 15),
            Triple("Fahrrad zur Werkstatt bringen", Category.NEBENBEI, Priority.WANT to 30),
            Triple("Bewerbung überarbeiten", Category.FOKUS, Priority.URGENT_WANT to 120),
            Triple("Kleiderschrank aussortieren", Category.ACHTSAM, Priority.WANT to 180),
            Triple("Linsensuppe ausprobieren", Category.ACHTSAM, Priority.WANT to 60),
            Triple("Versicherungen vergleichen", Category.FOKUS, Priority.MUST to 150),
            Triple("Fotos ordnen", Category.ACHTSAM, Priority.WANT to 90),
            Triple("Geburtstagskarte schreiben", Category.ACHTSAM, Priority.MUST to 20),
            Triple("Balkon aufräumen", Category.NEBENBEI, Priority.WANT to 45),
            Triple("Lesezeichen aufräumen", Category.NEBENBEI, Priority.WANT to 15),
            Triple("Präsentation für Montag", Category.FOKUS, Priority.URGENT_MUST to 180),
        ).map { (name, category, priorityAndMinutes) ->
            SeedCard(
                Item.newTodo(
                    name = name,
                    category = category,
                    priority = priorityAndMinutes.first,
                    targetDate = null,
                    estimatedDuration = priorityAndMinutes.second.minutes,
                    now = now,
                ),
            )
        }
        val quick = listOf(
            "Pflaster kaufen",
            "Mail an Vermieter",
            "Paket abholen",
            "Akku laden",
            "Flaschen wegbringen",
            "Gutschein einlösen",
        ).map { SeedCard(Item.newQuickTodo(it, now)) }
        val unlocking = listOf(
            SeedCard(
                Item.newTodo(
                    name = "Urlaubsplanung",
                    category = Category.FOKUS,
                    priority = Priority.MUST,
                    targetDate = LocalDate(2026, 11, 5),
                    estimatedDuration = 1.hours,
                    now = now,
                ),
            ),
            SeedCard(
                Item.newTodo(
                    name = "Winterreifen wechseln",
                    category = Category.NEBENBEI,
                    priority = Priority.WANT,
                    targetDate = LocalDate(2026, 11, 20),
                    estimatedDuration = 30.minutes,
                    now = now,
                ),
            ),
        )
        val chain = SeedCard(
            item = Item.newTodo(
                name = "Wohnung ausmisten",
                category = Category.ACHTSAM,
                priority = Priority.MUST,
                targetDate = null,
                estimatedDuration = 2.hours,
                now = now,
            ),
            followUps = listOf("Keller sortieren", "Dachboden leeren"),
        )
        val withSteps = SeedCard(
            item = Item.newTodo(
                name = "Einkaufsliste fürs Wochenende",
                category = Category.NEBENBEI,
                priority = Priority.MUST,
                targetDate = null,
                estimatedDuration = 30.minutes,
                now = now,
            ),
            subtasks = listOf(SubtaskDraft(name = "Gemüse"), SubtaskDraft(name = "Brot"), SubtaskDraft(name = "Kaffee")),
        )
        val deadline = SeedCard(
            Item.newDeadline(
                name = "Miete überweisen",
                category = Category.FOKUS,
                deadlineAt = LocalDate(2026, 10, 30).atTime(12, 0).toInstant(zone),
                now = now,
            ),
        )
        val forgotten = SeedCard(Item.newQuickTodo("Idee: Gewürzregal", LocalDate(2026, 9, 15).atTime(9, 0).toInstant(zone)))
        return concretized + quick + unlocking + chain + withSteps + deadline + forgotten
    }

    /**
     * Lena's Google Calendar: the lecture series Mon/Thu 10:00–11:30 that ends at
     * the year's end and leaves out Mon 02.11., a dentist appointment on Wed 21.10.
     * and an all-day event on Sat 31.10.
     */
    fun lenaCalendar(): FakeCalendar = FakeCalendar().apply {
        series(
            title = "Vorlesung Statistik",
            start = LocalDate(2026, 10, 12),
            time = LocalTime(10, 0),
            duration = 90.minutes,
            rule = "FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261231T215959Z",
            exdates = listOf(LocalDate(2026, 11, 2)),
        )
        event("Zahnarzt", LocalDate(2026, 10, 21), LocalTime(10, 0), 60.minutes)
        allDay("Geburtstag Mama", LocalDate(2026, 10, 31))
    }

    /**
     * Lena's growth task: running, once a week on Sunday, starting at ten minutes and
     * growing by five each time it is done (every completion).
     */
    fun lenaGrowthTask(now: Instant): Item = Item.newRecurring(
        name = "Laufen",
        category = Category.FOKUS,
        recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.SUNDAY),
        startTime = LocalTime(10, 0),
        estimatedDuration = 10.minutes,
        now = now,
    ).withGrowth(GrowthSetting(start = 10.minutes, target = 60.minutes, increment = 5.minutes, dynamic = false, every = 1))
}
