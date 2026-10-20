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
| E3 Oberfläche | Compose-Bildschirme unter Robolectric | `compose-ui-test-junit4` ist als Abhängigkeit eingetragen, noch ohne Tests | geplant |
| E4 Gerät | Emulator-Lauf nachts und Gerätecheckliste für Alarme, Kalender, Berechtigungen | `connectedDebugAndroidTest`, Checkliste im Entwicklungsleitfaden | geplant |

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

Die Prüfung läuft nach jedem Schritt in [`Invariants.kt`](../app/src/test/java/com/example/eta/scenario/Invariants.kt) und liest die Datenbank über Repositories, nie über ViewModels.

| # | Invariante | Prüfung |
| --- | --- | --- |
| 1 | Höchstens ein Block je `(Karte, Datum)` | Gruppierung der Blöcke nach Karte und Datum |
| 2 | Kein Block ohne Karte | Blöcke werden über ihre Karte gelesen; die Fremdschlüssel-Kaskade sorgt für den Rest |
| 3 | Jede ToDo in `Stage.WEEK` ist eingeplant, angeboten oder hat einen sichtbaren Grund | teilweise, siehe unten |
| 4 | Punktestand gleich Summe aller Buchungen; je abgerechnetem Tag höchstens ein `HARVEST` | aus den Zeilen nachgerechnet; die Abrechnung schreibt je Tag eine Zeile mit dem Datum als Notiz |
| 5 | Erledigte und verworfene Blöcke ändern sich bei späteren Schritten nicht | Vergleich mit dem Stand der letzten Prüfung |
| 6 | Kein Serienblock an Urlaubstagen oder nach `repeatUntil` | Serienblöcke gegen Urlaube und Enddatum |

Zu Invariante 3: Der Tagesrevolver wird nicht nachgerechnet. Geprüft wird, dass jede nicht eingeplante Wochenkarte konkretisiert ist, oder einen der sichtbaren Gründe hat: Freischaltung nach heute, wartende Folgeaufgabe, oder eine höhere Prioritätsstufe, die angeboten wird. Die Stufenfolge ist die Reihenfolge von `Priority`.

Invarianten, die ein offener Fehler auf dem aktuellen Stand verletzt, werden nicht stillschweigend abgeschaltet. Sie stehen in `KnownOpenIssues` hinter einem Schalter, den die Korrektur des Fehlers auf `true` setzt; dann kommt der Regressionsschutz zusammen mit der Korrektur.

| Schalter | Fehler | Bedeutung |
| --- | --- | --- |
| `STALE_WEEK_GOALS_MUST_NOT_HOLD_POOL` | #54 | Ein Ziel einer früheren Woche hält die ganze Sammelliste zurück |

Die Fehler #53 (Kalenderserien als Einzeltermine) und #56 (Freizeit am Tag ist null und nicht änderbar) haben noch keine Invariante. Sie werden über Szenarien in Bereich D und E geprüft, sobald ihre Korrektur ansteht.

## Katalog geplanter Szenarien

Die Bereiche folgen dem Ablauf der Woche: Erfassen (A), Woche (B), Tag (C), Serien (D), Einrichtung bis Plan (E), Abend und Abrechnung (F), Motivation (G), Kalender (H), Alarme und Ton (I), System (J). Status: `vorhanden` läuft im Harness, `offen` ist noch zu schreiben, `offen #NN` ist zu schreiben und wartet auf die Korrektur des Fehlers.

| Bereich | Szenario | Status |
| --- | --- | --- |
| A Erfassen & Listen | Schnellerfassung legt eine Karte in der Sammelliste an | vorhanden |
| A Erfassen & Listen | Konkretisieren mit Kategorie, Priorität und Dauer | vorhanden |
| A Erfassen & Listen | Karte bleibt nach einem Monat ohne Einplanung in der Sperrliste | offen |
| A Erfassen & Listen | Folgeaufgaben-Kette mit drei Schritten | offen |
| A Erfassen & Listen | Unterpunkte einer Karte werden mitgeführt | offen |
| B Woche | Wochenplanung zieht eine Karte in die Woche | vorhanden |
| B Woche | Offene Ziele einer früheren Woche halten die Sammelliste nicht zurück | offen #54 |
| B Woche | Entnahme aus der Woche zurück in die Sammelliste | offen |
| B Woche | Freischaltung nach dem Wochenende hält die Karte zurück und nennt den Grund | offen |
| C Tag | Karte auf den Tagesplan setzen und den Plan bestätigen | vorhanden |
| C Tag | Überlappung verlangt eine Entscheidung | offen |
| C Tag | Abhaken am Tag selbst | vorhanden |
| C Tag | Nachholen eines verpassten Blocks | offen |
| D Wiederkehrend | Wöchentliche und zweiwöchentliche Serie legt Vorkommen an | offen |
| D Wiederkehrend | Urlaub unterdrückt Serienvorkommen | offen |
| D Wiederkehrend | Serie endet mit `repeatUntil` | offen |
| D Wiederkehrend | Kalenderserie wird als eine Serie übernommen, nicht als Einzeltermine | offen #53 |
| E Einstellungen → Plan | Einrichtung legt Tagesschema und Serien an | vorhanden |
| E Einstellungen → Plan | Freizeit am Tag kann nach der Einrichtung geändert werden | offen #56 |
| E Einstellungen → Plan | Änderung der Schlafzeiten wirkt auf die Vorkommen ab morgen | offen |
| F Abend & Abrechnung | Abrechnung bucht Punkte genau einmal | vorhanden |
| F Abend & Abrechnung | Zweite Abrechnung desselben Tages bucht nichts | vorhanden |
| F Abend & Abrechnung | Älteren Abend mit Losung nachholen | offen |
| G Motivation | Erfolgsliste zeigt erledigte Blöcke | vorhanden |
| G Motivation | Belohn-o-mat füllt sich aus der Abrechnung | offen |
| H Kalender | Termin wird als Zeitblocker ohne Punkte übernommen | offen |
| I Alarme & Ton | Wecker wird nach der Einrichtung gestellt (`alarms = true`) | offen |
| I Alarme & Ton | Tagesplanungs-Alarm zur eingestellten Zeit | offen |
| J System | Tageswechsel baut die Ansichten neu auf | offen |
| J System | Zeitzonenwechsel verschiebt keine Vergangenheit | offen |

## Ausführen

```bash
bash ./gradlew --console=plain testDebugUnitTest --tests "com.example.eta.scenario.*"
bash ./gradlew --console=plain testDebugUnitTest
```

Das Szenario `StandardFlowScenarioTest` soll unter einer Minute laufen. Ein Szenario, das den Zeitrahmen sprengt, deutet auf einen Zustand hin, der nie eintritt: Der Fehler nennt dann den wartenden Zustand.
