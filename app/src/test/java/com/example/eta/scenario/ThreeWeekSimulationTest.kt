package com.example.eta.scenario

import android.app.Application
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.AddItemResult
import com.example.eta.data.repository.CatchUpResult
import com.example.eta.data.repository.SeriesProposal
import com.example.eta.data.repository.WeekScope
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.Vacation
import com.example.eta.domain.model.VacationRule
import com.example.eta.domain.model.VacationTreatment
import com.example.eta.domain.streak.CATCH_UP_PHRASE
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.ui.attributes.AppointmentAttributes
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.planner.PlannerDay
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Three weeks of Lena's planning, 18.10. to 08.11.2026, through the real ViewModels
 * and services in Europe/Berlin. The window contains the end of daylight saving time
 * on 25.10. Every step checks every invariant; each step's assertions name the
 * outcome the documented rules give.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ThreeWeekSimulationTest {
    private val calendar = SampleData.lenaCalendar()

    @Test
    fun `three weeks of Lena's planning hold every rule from one weekly plan to the next`() = scenario(
        start = "2026-10-18T09:00",
        deadline = 3.minutes,
        calendar = calendar,
    ) {
        val monday = LocalDate(2026, 10, 19)
        val monday26 = LocalDate(2026, 10, 26)
        val tuesday = LocalDate(2026, 10, 20)
        val wednesday = LocalDate(2026, 10, 21)
        val thursday = LocalDate(2026, 10, 22)
        val saturday = LocalDate(2026, 10, 24)
        val tuesday27 = LocalDate(2026, 10, 27)
        val monday02 = LocalDate(2026, 11, 2)
        val tuesday03 = LocalDate(2026, 11, 3)
        val thursday05 = LocalDate(2026, 11, 5)
        val saturday07 = LocalDate(2026, 11, 7)
        val seededIds = mutableSetOf<String>()

        // ---- So 18.10.: Einrichtung, erste Wochenplanung ----------------------------------

        step("Einrichtung") {
            val now = clock.now()
            val persona = SampleData.lenaSetup(now)
            completeSetup(persona.copy(setup = persona.setup.copy(growthTasks = true, allowPrioritySkip = true, allowTodoBesideWeek = true)))
            seedCollection(SampleData.lenaCollection(now, zone))
            seededIds += items(Stage.COLLECTION).map { it.id }
            container.itemRepository.add(SampleData.lenaGrowthTask(now))
            container.rewardRepository.create("Kinoabend", cost = 0.01, itemIds = emptyList())
            container.rewardRepository.create("Wellness", cost = 40.0, itemIds = emptyList())
            connectCalendar()
            container.scheduleMaintenance.topUp()
        }
        val fahrradEntered = card("Fahrrad zur Werkstatt bringen").enteredCollectionAt
        assertNotNull(fahrradEntered)

        at(LocalTime(18, 0))
        var committed = 0
        step("So 18:00 Wochenplanung: acht Karten ziehen, zwei unfertige über den GoalDialog") {
            weekPlanner {
                pull(card("Steuerunterlagen sortieren"))
                pull(card("Zahnarzt anrufen"))
                pull(card("Fahrrad zur Werkstatt bringen"))
                pull(card("Bewerbung überarbeiten"))
                pull(card("Kleiderschrank aussortieren"))
                pull(card("Linsensuppe ausprobieren"))
                goal(card("Pflaster kaufen"), TodoAttributes(category = Category.NEBENBEI, priority = Priority.WANT, duration = 15.minutes))
                goal(card("Mail an Vermieter"), TodoAttributes(category = Category.NEBENBEI, priority = Priority.WANT, duration = 20.minutes))
                // The GoalDialog of "Paket abholen" is cancelled: nothing is saved.
                committed = budget().plannedMinutes
            }
        }
        step("So 18:00 Wochenbudget zählt die Pausen") {
            // 90 and 120/150 min carry a break in the 60+ tiers: 115+15+30+145+205+75+15+20 = 620.
            assertEquals(620, committed)
        }
        step("So 18:00 Sperrliste: die Karte vom 15.09. ist nach einem Monat gesperrt") {
            assertTrue(items(Stage.LOCKED).any { it.name == "Idee: Gewürzregal" })
        }
        assertEquals("the cancelled goal stays in the Sammelliste", Stage.COLLECTION, card("Paket abholen").stage)

        at(LocalTime(20, 0))
        step("Sonntagabend eine zusätzliche Geburtstagskarte in die Woche nehmen") {
            weekPlanner { pull(card("Geburtstagskarte schreiben")) }
        }
        step("So 20:00 Montag planen: drei Karten, bestätigt") {
            planDay(PlannerDay.TOMORROW) {
                place(card("Zahnarzt anrufen"), LocalTime(9, 0))
                place(card("Fahrrad zur Werkstatt bringen"), LocalTime(12, 0))
                place(card("Geburtstagskarte schreiben"), LocalTime(18, 0))
                confirm()
            }
        }

        // ---- Mo 19.10.: der Tag wie geplant --------------------------------------------

        nextDay(LocalTime(9, 0))
        step("Mo 09:00 Tag wie geplant") {
            assertEquals(
                listOf(LocalTime(9, 0), LocalTime(12, 0), LocalTime(18, 0)),
                blocksOn(monday).filter { it.block.origin == BlockOrigin.DRAGGED }.map { it.block.start }.sorted(),
            )
        }
        at(LocalTime(11, 0))
        step("Mo 11:00 Zahnarzt abgehakt") { tick(card("Zahnarzt anrufen")) }
        at(LocalTime(18, 30))
        step("Mo 18:30 Geburtstagskarte abgehakt") { tick(card("Geburtstagskarte schreiben")) }

        at(LocalTime(20, 0))
        step("Mo 20:00 Abend: Fahrrad verwerfen, die Karte geht zurück in die Sammelliste") {
            eveningChain {
                discard(card("Fahrrad zur Werkstatt bringen"))
                settle()
            }
        }
        assertEquals(Stage.COLLECTION, card("Fahrrad zur Werkstatt bringen").stage)
        assertEquals(
            "the discarded card keeps its Sperrliste clock",
            fahrradEntered,
            card("Fahrrad zur Werkstatt bringen").enteredCollectionAt,
        )

        step("Mo 20:00 Dienstag planen; der Kalender zeigt am Dienstag keine Vorlesung") {
            planDay(PlannerDay.TOMORROW) {
                place(card("Steuerunterlagen sortieren"), LocalTime(10, 0))
                confirm()
            }
            calendarStep(tuesday, tuesday) {
                assertEquals("no lecture on a Tuesday", emptyList<SeriesProposal>(), offers())
            }
        }

        // ---- Di 20.10.: heute umplanen -------------------------------------------------

        nextDay(LocalTime(7, 0))
        at(LocalTime(11, 0))
        step("Di 11:00 Heute umplanen: Block verschieben, einen zurück in die Woche, Zwischenaufgabe") {
            planDay(PlannerDay.TODAY) {
                place(card("Bewerbung überarbeiten"), LocalTime(14, 0))
                move(blockWithItem(card("Steuerunterlagen sortieren"), tuesday), LocalTime(11, 30))
                returnToWeek(blockWithItem(card("Bewerbung überarbeiten"), tuesday))
                createTodo("Paket zur Post bringen", TodoAttributes(category = Category.NEBENBEI, priority = Priority.WANT, duration = 20.minutes), LocalTime(15, 0))
            }
            assertEquals(LocalTime(11, 30), blockOf(card("Steuerunterlagen sortieren"), tuesday).start)
            assertEquals(Stage.WEEK, card("Bewerbung überarbeiten").stage)
            assertEquals(Stage.DAY, card("Paket zur Post bringen").stage)
        }

        at(LocalTime(14, 0))
        step("Di 14:00 Einstellungen: Wecker, Morgenroutine, Freizeit 60 min, Standardpause 10 min") {
            settings {
                change {
                    it.copy(
                        wakeTime = LocalTime(6, 30),
                        freeTime = it.freeTime.copy(duration = 60.minutes),
                        defaultBreakAfter = 10.minutes,
                    )
                }
                morningSteps(
                    listOf(
                        SubtaskDraft(name = "Wasser trinken"),
                        SubtaskDraft(name = "Zähne putzen"),
                        SubtaskDraft(name = "Fenster lüften"),
                        SubtaskDraft(name = "Bett machen"),
                    ),
                )
            }
            assertEquals(10.minutes, container.setupRepository.find()?.defaultBreakAfter)
        }
        step("Di 14:00 handverschobener Block bleibt auf seiner Zeit") {
            assertEquals(LocalTime(11, 30), blockOf(card("Steuerunterlagen sortieren"), tuesday).start)
        }
        step("Di 14:00 neue Aufgabe am Tag bekommt die Standardpause von 10 min") {
            var added: Item? = null
            planDay(PlannerDay.TODAY) {
                added = createTodo("Akku laden", TodoAttributes(category = Category.NEBENBEI, priority = Priority.WANT, duration = 15.minutes), LocalTime(16, 0))
            }
            assertEquals(10.minutes, blockOf(checkNotNull(added), tuesday).breakAfter)
        }

        // ---- Mi 21.10.: Wochenliste füllen (MIDWEEK), Zahnarzt-Termin -------------------

        nextDay(LocalTime(7, 0))
        step("Mi 07:00 Kalender: die Vorlesung wird einmal angeboten, nicht je Termin") {
            calendarStep(wednesday, wednesday.plus(DatePeriod(days = 6))) {
                val offered = offers()
                assertEquals(1, offered.size)
                assertEquals("Vorlesung Statistik", offered.single().event.title)
            }
        }
        step("Mi 07:10 Zahnarzt-Termin als Termin übernehmen") {
            calendarStep(wednesday, wednesday) {
                val dentist = pendingAppointments(wednesday, wednesday).single { it.title == "Zahnarzt" }
                assertNull(import(dentist))
            }
        }
        step("Mi 07:20 Wochenliste füllen: drei Karten ziehen") {
            weekPlanner(WeekScope.MIDWEEK) {
                pull(card("Wohnung ausmisten"))
                pull(card("Fotos ordnen"))
                pull(card("Balkon aufräumen"))
            }
        }
        at(LocalTime(9, 0))
        step("Mi 09:00 Drop auf 10:00 kollidiert mit dem Zahnarzt: Frage statt Platzierung") {
            assertFalse("a drop onto the dentist is asked, not placed", placeToday(card("Fotos ordnen"), LocalTime(10, 0)))
        }
        step("Mi 09:05 Karte auf 11:00 gelegt") {
            planDay(PlannerDay.TODAY) { place(card("Fotos ordnen"), LocalTime(11, 0)) }
        }

        // ---- Do 22.10.: der Abend fällt aus ----------------------------------------------

        nextDay(LocalTime(9, 0))
        step("Do 09:00 Abend ausgelassen: der Donnerstag bleibt offen") {
            assertTrue(blocksOn(thursday).none { it.block.isCompleted })
        }

        // ---- Fr 23.10.: Nachholen, Tagesplan, Überlauf -----------------------------------

        nextDay(LocalTime(9, 0))
        step("Fr 09:00 Donnerstag nachholen") {
            assertTrue(container.catchUpService.dailyDays().contains(thursday))
            assertNotNull(evening(thursday) { settle() })
        }
        step("Fr 09:10 Tag planen: Linsensuppe 60 min um 11:30, bestätigt") {
            planDay(PlannerDay.TODAY) {
                place(card("Linsensuppe ausprobieren"), LocalTime(11, 30))
                confirm()
            }
        }
        at(LocalTime(20, 0))
        step("Fr 20:00 Timer mit Überlauf: 75 statt 60 min, dann abrechnen") {
            evening { overflow(card("Linsensuppe ausprobieren"), 75.minutes) }
            eveningChain { settle() }
        }
        assertEquals(75.minutes, blockOf(card("Linsensuppe ausprobieren"), LocalDate(2026, 10, 23)).actualDuration)

        // ---- Sa 24.10.: Wäsche verpasst, App über Mitternacht ----------------------------

        nextDay(LocalTime(9, 0))
        step("Sa 09:00 Wäsche steht am Samstag") {
            assertTrue(blocksOn(saturday).any { it.item.name == "Wäsche" })
        }
        at(LocalTime(20, 0))
        step("Sa 20:00 Wäsche verpasst: Nachholen angeboten und angelegt") {
            evening {
                discard(card("Wäsche"))
                val offer = makeUpOffers().single { it.entry.item.name == "Wäsche" }
                makeUp(offer.entry.item)
            }
        }
        assertTrue(items(Stage.COLLECTION).any { it.name == "Nachholen von Wäsche" })

        at(LocalTime(23, 50))
        nextDay(LocalTime(0, 5))
        step("So 00:05 App über Mitternacht: neues Datum, frische Ansichten") {
            assertEquals(LocalDate(2026, 10, 25), clock.today)
        }

        // ---- So 25.10. (Sommerzeit endet): Wochenplanung mit Vorwochen-Ziel --------------

        at(LocalTime(18, 0))
        step("So 25.10. 18:00 Wochenplanung: Ziel der letzten Woche wird gezeigt") {
            weekPlanner {
                assertTrue("an open goal of the previous cycle is shown", openGoals().isNotEmpty())
                for (goal in openGoals()) {
                    if (goal.name == "Bewerbung überarbeiten") returnToCollection(goal) else takeUnfinished(goal)
                }
            }
        }
        step("So 25.10. Sperrliste: die Karte vom 15.09. bleibt gesperrt, Wiederanlage wird abgewiesen") {
            assertTrue(items(Stage.LOCKED).any { it.name == "Idee: Gewürzregal" })
            val again = container.itemRepository.addQuickTodo("Idee: Gewürzregal")
            assertTrue("re-creating a locked card is refused: $again", again is AddItemResult.BlockedByLock)
        }

        // ---- Mo 26.10.: App zweimal gestartet, Inflation genau einmal ---------------------

        nextDay(LocalTime(8, 0))
        step("Mo 26.10. zwei App-Starts: Inflation wird genau einmal gebucht") {
            val before = transactions().count { it.reason == PointsReason.INFLATION }
            container.weekPlanningService.applyInflation()
            container.weekPlanningService.applyInflation()
            val after = transactions().count { it.reason == PointsReason.INFLATION }
            assertEquals(before + 1, after)
        }
        step("Mo 26.10. Vorlesung: das Montag-Vorkommen einzeln übernehmen, Serie bleibt angeboten") {
            calendarStep(monday26, monday26) {
                assertEquals(1, offers().size)
                val lecture = pendingAppointments(monday26, monday26).single { it.title == "Vorlesung Statistik" }
                assertNull(import(lecture))
            }
        }

        // ---- Di 27.10. 14:00: Kalender bringt einen Termin über den Block ----------------

        nextDay(LocalTime(7, 0))
        step("Di 27.10. 07:00 Balkon 15:00 planen") {
            planDay(PlannerDay.TODAY) { place(card("Balkon aufräumen"), LocalTime(15, 0)) }
        }
        calendar.event("Arzt Nachtermin", tuesday27, LocalTime(15, 0), 60.minutes)
        at(LocalTime(14, 0))
        step("Di 27.10. 14:00 Kalenderaktualisierung: der Termin über dem Block ist sichtbar") {
            val fresh = syncCalendar()
            assertTrue("the appointment over the planned block is reported: $fresh", fresh.any { it.title == "Arzt Nachtermin" })
            calendarStep(tuesday27, tuesday27) {
                assertTrue("the clash is shown", clashes().isNotEmpty())
            }
        }

        // ---- Mi 28.10.: Folgeaufgaben-Kette --------------------------------------------

        nextDay(LocalTime(9, 0))
        step("Mi 28.10. 09:00 Kopf der Kette planen") {
            planDay(PlannerDay.TODAY) {
                place(card("Wohnung ausmisten"), LocalTime(9, 0))
                confirm()
            }
        }
        at(LocalTime(11, 30))
        step("Mi 11:30 Schritt 1 abhaken: Schritt 2 wird freigegeben") {
            tick(card("Wohnung ausmisten"))
            assertNotNull(card("Keller sortieren").enteredCollectionAt)
        }
        step("Mi 11:31 Schritt 1 zurücknehmen: Schritt 2 wartet wieder") {
            untick(card("Wohnung ausmisten"))
            assertNull(card("Keller sortieren").enteredCollectionAt)
        }
        step("Mi 11:32 Schritt 1 wieder abhaken") { tick(card("Wohnung ausmisten")) }

        // ---- Do 29.10.: Serie übernommen; früherer Einzeltermin ersetzt ------------------

        nextDay(LocalTime(14, 0))
        step("Do 29.10. 14:00 Serie übernehmen; ein Einzeltermin ab morgen wird ersetzt") {
            calendarStep(LocalDate(2026, 10, 29), thursday05) {
                val offered = offers()
                assertEquals("still one offer for the series", 1, offered.size)
                val future = pendingAppointments(thursday05, thursday05).single { it.title == "Vorlesung Statistik" }
                assertNull(import(future))
                adopt(
                    offered.single() as SeriesProposal.Offer,
                    AppointmentAttributes(category = Category.FOKUS, start = LocalTime(10, 0), duration = 90.minutes),
                )
            }
        }
        step("Do 29.10. Serienkarte mit Endedatum; der Einzeltermin am 05.11. ist ersetzt") {
            val onFive = blocksOn(thursday05).single { it.item.name == "Vorlesung Statistik" }
            assertEquals(BlockOrigin.RECURRING, onFive.block.origin)
            assertEquals(LocalDate(2026, 12, 31), onFive.item.repeatUntil)
            assertTrue(blocksOn(monday26).any { it.item.name == "Vorlesung Statistik" && it.block.origin == BlockOrigin.CALENDAR_IMPORT })
        }

        // ---- Fr 30.10.: Abgabetag, Belohnung --------------------------------------------

        nextDay(LocalTime(9, 0))
        step("Fr 30.10. Abgabetag: Miete überweisen steht auf der Liste") {
            val deadline = checkNotNull(allCards().single { it.name == "Miete überweisen" }.deadlineAt)
            assertTrue(deadline.toString().startsWith("2026-10-30"))
        }
        step("Fr 30.10. Belohnung einlösen") {
            val reward = container.rewardRepository.observe().first().first { it.reward.name == "Kinoabend" }
            container.rewardRepository.redeem(reward.reward.id)
            assertNotNull(container.rewardRepository.observe().first().first { it.reward.name == "Kinoabend" }.reward.redeemedAt)
        }

        // ---- Sa 31.10.: Ganztägiger Termin, Vertrag gebrochen -----------------------------

        nextDay(LocalTime(9, 0))
        step("Sa 31.10. Ganztägiger Termin: Vorschlag 09:00 kollidiert mit der Morgenroutine, um 12:00 übernommen") {
            calendarStep(LocalDate(2026, 10, 31), LocalDate(2026, 10, 31)) {
                val allDay = pendingAppointments(LocalDate(2026, 10, 31), LocalDate(2026, 10, 31))
                    .single { it.title == "Geburtstag Mama" }
                assertTrue(allDay.allDay)
                assertEquals("an all-day event is suggested at 09:00", LocalTime(9, 0), allDay.start)
                assertNull(import(allDay, AppointmentAttributes(start = LocalTime(12, 0), duration = allDay.duration)))
            }
            assertEquals(LocalTime(12, 0), blocksOn(LocalDate(2026, 10, 31)).single { it.item.name == "Geburtstag Mama" }.block.start)
        }
        step("Sa 31.10. Vertrag unterschrieben, gebrochen: Bewährung") {
            settings { feature { it.copy(contracts = true) } }
            container.contractRepository.sign(
                slot = 1,
                title = "Kein Handy vor 22 Uhr",
                conditions = "Handy ab 22 Uhr im Flur",
                breachDefinition = "Handy nach 22 Uhr im Schlafzimmer",
                effort = ContractEffort.MITTEL,
                signature = "Lena",
                endsOn = LocalDate(2026, 12, 31),
            )
            val contract = container.contractRepository.observeAll().first().single()
            container.contractRepository.recordVerdict(contract, kept = false, date = LocalDate(2026, 10, 31), keepServing = true)
            assertEquals(ContractState.PROBATION, container.contractRepository.observeAll().first().single().state)
        }

        // ---- So 01.11.: Wochenplanung, Wachstum, Vertragsfunktion aus und an -------------

        nextDay(LocalTime(10, 30))
        step("So 01.11. 10:30 Laufen erledigt") { tick(card("Laufen")) }
        at(LocalTime(18, 0))
        step("So 01.11. nächste Woche: offene Ziele bewusst übernehmen und Versicherung hinzunehmen") {
            weekPlanner {
                for (goal in openGoals()) takeUnfinished(goal)
                pull(card("Versicherungen vergleichen"))
            }
        }
        at(LocalTime(20, 0))
        step("So 01.11. 20:00 Abrechnung: das Wachstum steigt um 5 min") {
            eveningChain { settle() }
            assertEquals(15.minutes, card("Laufen").estimatedDuration)
        }
        step("So 01.11. Vertragsfunktion aus und wieder an: die Daten bleiben") {
            settings { feature { it.copy(contracts = false) } }
            assertEquals(1, container.contractRepository.observeAll().first().size)
            settings { feature { it.copy(contracts = true) } }
            assertEquals(1, container.contractRepository.observeAll().first().size)
        }

        // ---- Mo 02.11.: Ausnahme (EXDATE) ------------------------------------------------

        nextDay(LocalTime(9, 0))
        step("Mo 02.11. Ausnahme: keine Vorlesung an diesem Tag") {
            calendarStep(monday02, monday02) { offers() }
            assertTrue(blocksOn(monday02).none { it.item.name == "Vorlesung Statistik" })
        }

        // ---- Di 03.–Do 05.11.: Urlaub mitten in der Woche --------------------------------

        nextDay(LocalTime(9, 0))
        step("Di 03.11. Urlaub anlegen: die Blöcke der Urlaubstage fallen weg") {
            val vacation = Vacation(
                label = "Urlaub", from = tuesday03, to = LocalDate(2026, 11, 4),
                createdAt = clock.now(), updatedAt = clock.now(),
            )
            val rules = container.itemRepository.observeRecurringDefinitions().first().map {
                VacationRule(vacationId = vacation.id, itemId = it.id, treatment = VacationTreatment.SUSPEND)
            }
            container.vacationRepository.save(vacation, rules)
            assertTrue(blocksOn(tuesday03).none { it.item.name == "Lernen" })
        }
        nextDay(LocalTime(9, 0))
        nextDay(LocalTime(9, 0))
        step("Do 05.11. Urlaub vorbei: Lernen wieder da; Urlaubsplanung ist ab heute ziehbar") {
            assertTrue(blocksOn(thursday05).any { it.item.name == "Lernen" })
            weekPlanner(WeekScope.MIDWEEK) { pull(card("Urlaubsplanung")) }
        }

        // ---- Fr 06.11.: morgen bestätigt, dann Einstellungen ändern ---------------------

        nextDay(LocalTime(9, 0))
        step("Fr 06.11. Samstag planen und bestätigen") {
            planDay(PlannerDay.TOMORROW) {
                place(card("Versicherungen vergleichen"), LocalTime(11, 15))
                confirm()
            }
        }
        val confirmedBefore = blocksOn(saturday07).map { it.block.id to it.block.start }
        step("Fr 06.11. Einstellungen ändern: der bestätigte Samstag bleibt") {
            settings { change { it.copy(wakeTime = LocalTime(7, 0)) } }
            assertEquals(confirmedBefore, blocksOn(saturday07).map { it.block.id to it.block.start })
        }

        // ---- So 08.11.: Abschluss --------------------------------------------------------

        nextDay(LocalTime(9, 0))
        nextDay(LocalTime(9, 0))
        step("So 08.11. Abschluss: Erfolgsliste, Punkte, keine verlorene Karte") {
            awaitErfolgsliste("Zahnarzt anrufen")
            awaitErfolgsliste("Geburtstagskarte schreiben")
            assertEquals(balance(), transactions().sumOf { it.amount }, TOLERANCE)
            for (name in listOf("Steuerunterlagen sortieren", "Pflaster kaufen", "Miete überweisen", "Winterreifen wechseln")) {
                assertTrue("\"$name\" is still on a list", allCards().any { it.name == name })
            }
        }
    }

    /** The card called [name], wherever it lies; fails when it is nowhere. */
    private suspend fun Scenario.card(name: String): Item =
        checkNotNull(allCards().firstOrNull { it.name == name }) { "\"$name\" is nowhere in the database" }

    private suspend fun Scenario.allCards(): List<Item> = container.database.itemDao().findAll()

    /** The planned block of [item] on [date] as a [BlockWithItem]. */
    private suspend fun Scenario.blockWithItem(item: Item, date: LocalDate): BlockWithItem =
        checkNotNull(blocksOn(date).firstOrNull { it.item.id == item.id }) { "no block of \"${item.name}\" on $date" }

    private companion object {
        const val TOLERANCE = 0.000001
    }
}
