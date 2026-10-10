---
doc_type: guide
status: current
owner: eta-maintainers
summary: Orientierung zu Architektur, Datenfluss und fachlichen Invarianten der Eta-App.
---

# Architektur

Diese Seite bietet einen Wegweiser durch den Android-Code und erklärt dessen zentrale Grenzen. Sie ersetzt weder die detaillierten [Fachnotizen](../.claude/rules/) noch die ursprünglichen [Produktanforderungen](../Eta_doc/). Bei Änderungen gilt der aktuelle Quellcode; Notizen erläutern Entscheidungen und Randfälle.

## Einstieg und Verantwortlichkeiten

Der App-Code liegt unter [`app/src/main/java/com/example/eta`](../app/src/main/java/com/example/eta/). Die Oberfläche ist Jetpack Compose. [`EtaApp`](../app/src/main/java/com/example/eta/ui/root/EtaApp.kt) bestimmt aus dem Root-Zustand das aktuelle Ziel, richtet ViewModels ein und verbindet Ansichten mit der Navigation. [`AppContainer`](../app/src/main/java/com/example/eta/di/AppContainer.kt) ist die manuelle Dependency Injection: Er erstellt Datenbank, DAOs, Repositories, Dienste und Koordinatoren und reicht sie an die ViewModels. Es gibt hier keinen DI-Framework-Graphen.

Ein typischer Lesepfad: Compose-Ansicht in `ui/` → ViewModel → Repository oder fachlicher Dienst → DAO → Room. Die UI zeigt beobachtete Zustände und sendet Nutzeraktionen; fachliche Regeln liegen soweit möglich unabhängig in `domain/`. Persistenzabfragen und -schreibvorgänge liegen in `data/local/` und `data/repository/`. Die Trennung ist kein starres Schichtenframework: Repositories koordinieren auch mehrstufige fachliche Schreibvorgänge, und manche Regeln werden aus den Domänenfunktionen von ViewModels aufgerufen.

ViewModels kombinieren meist DAO-/Repository-`Flow`s mit fachlichen Berechnungen und exponieren `StateFlow` für Compose. Beobachtung folgt dem Lifecycle (`collectAsStateWithLifecycle`); Aktionen laufen in Coroutine-Scope und rufen schreibende APIs auf. Beispielhaft zeigen [`DashboardViewModel`](../app/src/main/java/com/example/eta/ui/dashboard/DashboardViewModel.kt) und [`ItemDao`](../app/src/main/java/com/example/eta/data/local/ItemDao.kt) den Übergang von Room-Flow zu Bildschirmzustand.

## Definitionen und Vorkommen

[`Item`](../app/src/main/java/com/example/eta/domain/model/Item.kt) ist die dauerhafte Definition: Was die Karte bedeutet, etwa Name, Kategorie, Wiederholungsregel und Standardattribute. [`PlannedBlock`](../app/src/main/java/com/example/eta/domain/model/PlannedBlock.kt) ist ein konkretes Vorkommen an einem Datum und einer Uhrzeit. Abschluss, tatsächliche Dauer, Absage und Ertrag gehören zum Vorkommen. Eine Definition kann viele Vorkommen erzeugen; ein einmaliges ToDo erhält ebenfalls einen Block, wenn es eingeplant wird.

Diese Unterscheidung ist eine wichtige Änderungsgrenze: Änderungen an der Definition können künftige Vorkommen beeinflussen; Tageskorrekturen betreffen den Block. Zum Beispiel liegt eine wiederkehrende Standarddauer am Item, während die tatsächlich geplante oder erfasste Dauer am Block liegt. Das Datenmodell sichert höchstens ein Vorkommen pro Item und Datum über einen eindeutigen Index ab. Wiederkehrende Definitionen werden nach Regeln expandiert und fehlende Vorkommen materialisiert; siehe [`RecurringSchedule.kt`](../app/src/main/java/com/example/eta/domain/recurrence/RecurringSchedule.kt), [`RecurrenceExpansion.kt`](../app/src/main/java/com/example/eta/domain/recurrence/RecurrenceExpansion.kt) und [`ScheduleMaintenance`](../app/src/main/java/com/example/eta/data/repository/ScheduleMaintenance.kt).

Weitere Grenzen sind ähnlich bewusst: Schritte gehören zur Definition, ihre Erledigungsmarkierungen zum Block; die Einzelheiten stehen in [Schritte](../.claude/rules/step-23-subtasks.md). Notizen und Kalenderereignisse sind in [Dashboard](../.claude/rules/dashboard.md) beziehungsweise [Google Calendar](../.claude/rules/google-calendar.md) beschrieben.

## Fachlogik, Speicherung und Zustand

`domain/model/` enthält persistierte Modelle und deren Typen; Funktionen in Bereichen wie `domain/planning/`, `domain/recurrence/`, `domain/reward/` und `domain/reevaluation/` kapseln Regeln und Berechnungen. Dienste in `data/repository/` setzen diese Regeln in Abläufe mit Datenbankzugriff um. DAOs in `data/local/` formulieren Room-Abfragen und liefern häufig `Flow`s. [`ItemDao`](../app/src/main/java/com/example/eta/data/local/ItemDao.kt) ist ein konkretes Beispiel für beobachtete Kartenlisten und Filter.

Punkte sind ein fachlicher Ledger-/Ertragsfluss, nicht bloß ein UI-Zähler. Berechnungen liegen unter `domain/reward/` und `domain/reevaluation/`; Persistenz und Settlement werden durch Repository-Dienste koordiniert. Für Regeln und bewusst unterschiedliche Ertragsfälle siehe [Verträge](../.claude/rules/contracts.md), [Belohnungssystem](../.claude/rules/step-35-belohnomat.md) und [tägliche Reevaluation](../Eta_doc/Tägliche%20Reevaluation.md). Sichtbarkeitseinstellungen blenden Darstellungen aus, nicht die Verbuchung.

## Navigation und Planungsabläufe

Die Navigation und Bildschirmverzweigungen sind in [`EtaApp.kt`](../app/src/main/java/com/example/eta/ui/root/EtaApp.kt) zentral zusammengesetzt. Die Tabs und Phasen sind Compose-Zustand und gespeicherte Anwendungsdaten, kein eigenständiger Navigation-Graph. Der tägliche Ablauf führt durch Reevaluation, Konkretisierung, gegebenenfalls Kalenderabgleich und Tagesplanung; die Wochenansicht verwaltet Wochenplanung. Die Produktorientierung steht in [Planungsphase](../Eta_doc/Planungsphase.md), die aktuelle Ausgestaltung in [Tagesplaner-Notiz](../.claude/rules/day-planner.md) und [Kalender-Notiz](../.claude/rules/google-calendar.md).

## Kalender, Identität und Datenmigration

Google-Kalender wird schreibgeschützt importiert. Importierte Ereignisse werden als eigene Definition/Zuordnung und konkrete Kalenderblöcke repräsentiert; die Kalenderidentität verhindert Duplikate beim erneuten Synchronisieren. Nutzungsentscheidungen zu Ereignissen bleiben beim Nutzer. Konfliktbehandlung und Synchronisationsinvarianten sind in [Google Calendar](../.claude/rules/google-calendar.md) dokumentiert; Implementierungen liegen in `data/calendar/`, `data/repository/Calendar*` und `ui/calendar/`.

Persistenzidentität ist ebenfalls fachlich relevant: Die Android-`applicationId` bleibt aus Kompatibilitätsgründen beim früheren Paketnamen, damit Updates dieselbe installierte App und deren Daten erreichen; siehe [`app/build.gradle.kts`](../app/build.gradle.kts). Room-Schemaänderungen müssen mit expliziten Migrationen und exportierten Schemas zusammenpassen. [`EtaDatabase.kt`](../app/src/main/java/com/example/eta/data/local/EtaDatabase.kt) definiert Datenbank und Migrationen, `AppContainer` registriert sie; [Datenbanknotiz](../.claude/rules/database.md) erläutert den Prozess. Keine Migration durch Löschen oder Neuerstellen einer Nutzerdatenbank umgehen. Die Referenz-/Inventarseite der Dokumentationssite ist über den [Dokumentationseinstieg](README.md) erreichbar.
