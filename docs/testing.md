---
doc_type: guide
status: current
owner: eta-maintainers
summary: Testebenen von ETA, das Szenario-Harness für mehrtägige Abläufe, seine Invarianten und der Katalog geplanter Szenarien.
---

# Tests und Szenarien

Diese Anleitung beschreibt, wie ETA geprüft wird, wie ein mehrtägiger Ablauf als Test geschrieben wird und welche Abläufe noch fehlen. Sie ist ein Leitfaden, kein Statusbericht: der Stand einzelner Szenarien steht nur im Katalog am Ende, und nur dort wird er gepflegt.

## Testebenen

| Ebene | Was geprüft wird | Werkzeug | Status |
| --- | --- | --- | --- |
| E1 Regeln | Reine Funktionen in `domain/`: Punkte, Planung, Wiederholung, Abrechnung | JUnit 4 auf der JVM, Dateien unter [`app/src/test/java/com/example/eta/domain/`](../app/src/test/java/com/example/eta/domain/) | vorhanden |
| E2 Abläufe | Mehrtägige Abläufe über echte ViewModels und Dienste, mit Datenbank und Uhr | Szenario-Harness unter [`app/src/test/java/com/example/eta/scenario/`](../app/src/test/java/com/example/eta/scenario/), Robolectric | vorhanden |
| E3 Oberfläche | Echte Bildschirme mit echten ViewModels: Navigation, Wochenplanung unter der Woche, Serienangebot, Einstellungen, Abendablauf | Compose-Tests unter Robolectric in [`app/src/test/java/com/example/eta/ui/`](../app/src/test/java/com/example/eta/ui/) | vorhanden |
| E4 Gerät | Erststart, Planungsalarme, Benachrichtigungskanäle und die Dialoge der Wochenplanung auf echtem Android; dazu die manuelle [Gerätecheckliste](device-checklist.md) | `connectedDebugAndroidTest` im Workflow `android-emulator.yml` (jeder PR und nachts) | vorhanden |

E1 deckt die Regeln ab, E2 prüft, dass die Regeln im Zusammenspiel der Schichten bei einem echten Ablauf wirken. Ein Fehler, der nur durch die Reihenfolge von Eingaben über mehrere Tage entsteht, gehört in E2. Oberflächen- und Gerätefragen bleiben bei E3 und E4; Tests ersetzen keine Prüfung auf einem Gerät ([Entwicklung und Prüfung](development.md)).

## Ein Szenario schreiben

Ein Szenario ist eine Robolectric-Testmethode, die `scenario(...)` aufruft. Der Körper läuft mit echter Datenbank (In-Memory), einem Uhr-Objekt in der Zeitzone des Geräts und den ViewModels aus denselben Fabriken wie die App ([`ui/root/ViewModelFactories.kt`](../app/src/main/java/com/example/eta/ui/root/ViewModelFactories.kt)).

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StandardFlowScenarioTest {
    @Test
    fun `quick add to settled evening credits the harvest once and lists it as done`() = scenario(
        start = "2026-10-18T09:00",
    ) {
        step("Einrichtung") { completeSetup(SampleData.lenaSetup(clock.now())) }
        val added = step("Schnellerfassung") { quickAdd("Steuererklärung") }
        val card = step("Konkretisieren") { concretize(added, Category.FOKUS, Priority.MUST, 1.hours) }
        step("Wochenplanung: ziehen") { weekPlanner { pull(card) } }

        at(LocalTime(20, 0))
        step("Sonntag Abend: Montag planen") {
            planDay(PlannerDay.TOMORROW) { place(card, LocalTime(10, 0)); confirm() }
        }

        nextDay(LocalTime(11, 30))
        step("Montag: abhaken") { tick(card) }

        at(LocalTime(20, 0))
        step("Montag Abend: abrechnen") { eveningChain { settle() } }
    }
}
```

Regeln für Szenarien:

- **Jeder Schritt ist `step("Name") { … }`.** Nach jedem Schritt laufen alle Invarianten gegen die Datenbank. Ein Fehler nennt den Schritt und den Tag und die Uhrzeit, zum Beispiel `Tag 2 (Mo 19.10.) 11:30 – Montag: abhaken: …`.
- **Zeit bewegen sich nur vorwärts.** `at(LocalTime)` bleibt am selben Tag, `nextDay(LocalTime)` geht an den folgenden Tag, `advanceTo(LocalDateTime)` springt beliebig vor. Ein Tageswechsel leert den ViewModel-Speicher, weil die App ihre ViewModels beim Tageswechsel neu baut (`rememberDateKey`).
- **Treiber sind dünne Hüllen.** Ein Treiber ruft das echte ViewModel oder den echten Dienst so auf, wie der Bildschirm es tut, und enthält keine eigene Regel. Neue Abläufe bekommen einen neuen Treiber in [`Drivers.kt`](../app/src/test/java/com/example/eta/scenario/Drivers.kt).
- **Testdaten nur über echte Fabriken.** Persona „Lena“ und die Sammelliste stehen in [`SampleData.kt`](../app/src/test/java/com/example/eta/scenario/SampleData.kt) und bauen mit `UserSetup.draft`, `Item.newTodo`, `Item.newQuickTodo`, `Item.newRecurring` und den übrigen Fabriken.

### Zustand lesen: `stateIn(WhileSubscribed)`

Viele ViewModel-Flows sind `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`. Sie rechnen nur, solange jemand sie einsammelt. Ihr `.value` ist vorher der Startwert. Deshalb gilt im Harness:

- `awaitState(flow, "Beschreibung") { Bedingung }` sammelt den Flow, bis die Bedingung erfüllt ist. Das Warten ist echte Zeit, standardmäßig 10 Sekunden; bei Ablauf schlägt der Test mit dem letzten gesehenen Wert fehl.
- `subscribed(flow) { … }` hält einen Flow gesammelt, während der Block läuft. Nur so sind `.value`-Lesezugriffe und Aktionen auf dem Zustand gültig.
- Jeder Treiber wartet, bis der Eintrag, auf den er reagieren will, im Zustand steht, bevor er handelt. Das entspricht dem Bildschirm, der gezeichnet sein muss, bevor ein Finger ihn erreicht.

## Invarianten

Die Prüfung läuft nach jedem Schritt in [`Invariants.kt`](../app/src/test/java/com/example/eta/scenario/Invariants.kt) und liest die Datenbank direkt, nie über ViewModels. Alle Karten werden in einem einzigen Lesezugriff geholt, damit die Prüfung keine halb geschriebene Änderung sieht.

| # | Invariante | Prüfung |
| --- | --- | --- |
| 1 | Höchstens ein Block je `(Karte, Datum)` | Gruppierung der Blöcke nach Karte und Datum |
| 2 | Kein Block ohne Karte | jeder Block gehört zu einer gespeicherten Karte |
| 3 | Jede ToDo in `Stage.WEEK` ist konkretisiert | Kategorie, Priorität und Dauer sind gesetzt (#54) |
| 4 | Punktestand gleich Summe aller Buchungen; je abgerechnetem Tag höchstens ein `HARVEST` | aus den Buchungszeilen nachgerechnet |
| 5 | Erledigte und verworfene Blöcke ändern sich bei späteren Schritten nicht | Vergleich mit dem Stand der letzten Prüfung; ein bewusst zurückgenommener Haken öffnet den Block wieder |
| 6 | Kein Serienblock an Urlaubstagen oder nach `repeatUntil` | Serienblöcke gegen Urlaube und Enddatum |
| 7 | Keine Karte geht verloren | jede ToDo steht in genau einer Stufe; eine ToDo in `Stage.DAY` hat einen Block |
| 8 | Die Sperrliste lässt sich nicht umgehen | eine vom Tag verworfene Karte behält ihr `enteredCollectionAt`; ein bewusstes Zurückgeben aus der Woche startet die Frist neu |

Bekannte Fehler werden nicht hinter Schaltern versteckt. Ein Test, der einen echten Fehler findet, bleibt rot, bis die Korrektur kommt.

Szenarien laufen mit einer harten Echtzeitgrenze (`scenario(deadline = …)`). Läuft sie ab, schlägt der Test mit einem Thread-Abzug fehl; Gradle bricht jeden Testlauf zusätzlich nach 25 Minuten ab.

## Katalog der Szenarien

Die Bereiche folgen dem Ablauf der Woche. Der Durchlauf über drei Wochen ([`ThreeWeekSimulationTest`](../app/src/test/java/com/example/eta/scenario/ThreeWeekSimulationTest.kt), 18.10.–08.11.2026 mit Zeitumstellung) verbindet sie; die Einzeltests zeigen bei einem Fehler direkt die Stelle.

| Bereich | Abgedeckt | Tests |
| --- | --- | --- |
| A Erfassen & Listen | Schnellerfassung, Konkretisieren, Sperrliste nach einem Monat und Wiederanlage, Folgeaufgaben-Kette (freigeben und wieder warten), Schritte reisen mit, Erfolgsliste ohne wiederkehrende Aufgaben | `CaptureScenarioTest`, `StandardFlowScenarioTest` |
| B Woche | Wochenplanung und Auffüllen unter der Woche, offene Vorwochenziele übernehmen oder zurückgeben, unfertige Karten erst konkretisieren, Freischaltung nach Wochenende, Rückgabe startet die Frist neu, Wochenbudget mit 15/25-Minuten-Pausen, Wertverfall genau einmal | `WeekScenarioTest`, `WeekPlannerViewModelTest`, `WeekPlannerMidWeekTest`, `WeekPlannerDialogTest` (Gerät) |
| C Tag | Prioritätsstufen und Überspringen, Überlappung fragt nach, zurück in Woche oder Sammelliste, Bestätigen ergibt die Liste für Morgen, heute umplanen, Pausenhinweis, Tageswechsel | `DayScenarioTest` |
| D Wiederkehrend | Anlegen für 14 Tage, wiederholtes Auffüllen ohne Duplikate, Bearbeiten wirkt heute und später, alle zwei Wochen, Urlaub, `repeatUntil`, gelöschter und verworfener Block | `RecurringScenarioTest`, `SettingsRelayTest` |
| E Einstellungen → Plan | Schlaf- und Weckzeiten wirken auf heutige, noch nicht begonnene Blöcke, nicht auf einen bestätigten Morgen; Freizeit 0 → 60 → 0; Standardpause nur für neu platzierte ToDos; Planungszeiten stellen die Alarme neu; Funktionen aus und an | `SettingsScenarioTest`, `PlanningSettingsScenarioTest`, `SetupFreeTimeTest`, `AppNavigationTest` |
| F Abend & Abrechnung | Punkte erst am Abend, zweite Abrechnung bucht nichts, Verwerfen und Nachholen, verpasste Abende (1–6 Tage, ältere mit Losung), Streak, Absage- und Leerlaufabzug, verzichtete Freizeit | `EveningScenarioTest`, `ReevaluationServiceTest` |
| G Motivation | Belohn-o-mat füllen und einlösen, Vertrag gehalten, gebrochen, auf Bewährung, wieder in Kraft, aufgegeben; Wachstumsaufgabe wächst und verlängert spätere Blöcke | `MotivationScenarioTest` |
| H Kalender | Abgleich ohne Duplikate, Zeitblocker ohne Punkte, Serie einmal angeboten und mit Ende und Ausnahmen übernommen, „Einzeln lassen“ bleibt, Konflikt kürzen, teilen, absagen oder verschieben, Hintergrundabgleich meldet einmal, Offline-Start verwirft keine ungelesenen Tage | `CalendarScenarioTest`, `CalendarSeriesOfferTest`, `CalendarSyncBoundaryTest` |
| I Alarme & Ton | Planungsalarme täglich und wöchentlich, Startalarm folgt einem verschobenen Block, Weckruf, Erinnerung, Sandbox stellt nichts; Klingelmodus Vibration und Lautlos spielen keinen Ton | `AlarmScenarioTest`, `EtaSoundTest`, `FirstRunSmokeTest` (Gerät) |
| J System | Erststart, Tutorial schreibt nichts in die echte Datenbank, Tageswechsel baut die Ansichten neu, Sicherung und Wiederherstellung, „Alles zurücksetzen“ samt abgelehnter Serien, Migration aller Schemaversionen | `SystemScenarioTest`, `EtaDatabaseMigrationTest`, `AppSmokeTest` (Gerät) |

Noch nicht automatisiert: ein Zeitzonenwechsel des Geräts, das Quick-Add-Widget und alles, was die [Gerätecheckliste](device-checklist.md) aufführt (echte Google-Anmeldung, Lautstärke auf HyperOS, Sperrbildschirm, Neustart).

## Ausführen

```bash
bash ./gradlew --console=plain testDebugUnitTest --tests "com.example.eta.scenario.*"
bash ./gradlew --console=plain testDebugUnitTest
```

Das Szenario `StandardFlowScenarioTest` soll unter einer Minute laufen. Ein Szenario, das den Zeitrahmen sprengt, deutet auf einen Zustand hin, der nie eintritt: Der Fehler nennt dann den wartenden Zustand.
