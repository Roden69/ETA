# Release Notes

Was sich in diesem Fork pro Version ändert, geschrieben für die Menschen, die Eta benutzen. Die neueste Version steht oben. Die Nummern verweisen auf die [Issues](https://github.com/Roden69/ETA/issues). Änderungen vor dem Fork stehen in den [Patchnotes](patchnotes/README.md) des Originals.

Die APKs liegen unter [Releases](https://github.com/Roden69/ETA/releases) oder, für den neuesten Stand von `main`, als Artifact der [CI](https://github.com/Roden69/ETA/actions).

## Unveröffentlicht

Sammelpaket zu den Issues #4 bis #51 ([Pull Request #52](https://github.com/Roden69/ETA/pull/52)). Der Issue-Bot (#46) ist bewusst nicht dabei. Dazu kommen die Korrekturen am Planungsablauf (#53, #54, #56) und automatische Ablauftests.

### Vor dem Update

- Unter *Einstellungen → Deine Daten* **Sichern** drücken. Die Datenbank wird beim ersten Start auf Version 36 migriert (von 33, 34 oder 35). Zurück zu einer älteren App führt danach kein Weg.
- Eine APK aktualisiert nur eine Installation mit demselben Signaturschlüssel. Bei einem Konflikt **nicht deinstallieren** (das löscht die Datenbank), sondern *Eta Fork* daneben installieren.

### Neu

**Tagesablauf**
- **Tagesabschluss nachholen (#50):** Verpasste Abende der letzten sieben Tage lassen sich der Reihe nach abschließen, die ältesten zuerst, oder überspringen. Verträge werden dabei nicht gefragt.
- **Wecker in der Uhr-App (#18):** Beim Tagesabschluss bietet Eta an, den Wecker für morgen in deiner Uhr-App zu stellen (Standard-Uhr oder Google Uhr). Welche Uhr-App das ist und ob Eta selbst klingelt, stellst du unter *Einstellungen → Weckruf* ein.
- **Abendruhe-Hinweis (#44):** Nach dem Tagesabschluss zeigt Eta einen ruhigen Hinweis, das Handy wegzulegen. Eine eingerichtete Abendroutine steht dabei. Eta sperrt nichts; der Hinweis lässt sich in den Einstellungen abschalten.
- **Pausenhinweis (#41):** Folgen zwei Aufgaben ohne Lücke aufeinander, weist Eta darauf hin.
- **Pausen & Freizeit (#56):** Unter *Einstellungen → Pausen & Freizeit* stellst du die tägliche Freizeit und eine Standardpause nach Aufgaben ein, auch nach der Einrichtung. Die Standardpause gilt für ToDos ohne eigene „Pause danach“.

**Google Kalender**
- **Stündlicher Abgleich (#15):** Eta aktualisiert den Kalender regelmäßig von selbst.
- **Zeitblocker (#16):** Termine lassen sich als Zeitblocker behalten, ohne Punkte zu zählen.
- **Wiederkehrende Termine (#17, #53):** Der Kalenderschritt erkennt Serien wie Vorlesungen selbst und bietet jede Serie einmal als wiederkehrende Aufgabe an; „Einzeln lassen“ fragt nicht wieder. Serien mit Enddatum oder fester Anzahl enden mit dem letzten Termin, ausfallende Termine (etwa in den Ferien) bekommen keinen Block. Einzeltermine bleiben einzeln.
- **Konflikte (#32, #33):** Importierte Termine und Überschneidungen sind in Wochen- und Tagesplan sichtbar und lassen sich auflösen.
- **Kalender zuerst (#30, #31):** Die Einrichtung beginnt mit der Kalenderanbindung und zeigt deine Termine im Wochenraster. Überschneidungswarnungen nennen die beteiligten Einträge (#40).

**Aufgaben und Listen**
- **Folgeaufgaben (#19):** Eine mehrstufige Aufgabe lässt sich in Schritte teilen; jeder Schritt wartet auf den davor.
- **Zweiter Zeitplatz am Tag (#9):** Auch Achtsamkeit passt zweimal an einen Tag.
- **Gruppierung (#37):** Aufgaben mit wechselnden Uhrzeiten oder Dauern stehen übersichtlich je Wochentag.
- **Wochenliste (#49, #54):** Sie zeigt jede Karte der Sammelliste. Eine Karte, die nicht in die Woche darf, nennt den Grund. Eine Karte ohne Kategorie, Priorität oder Dauer fragt beim Ziehen in die Woche nach diesen Angaben; Karten, die schon ohne Angaben in der Wochenliste lagen, erscheinen wieder beim Konkretisieren.

**Woche und Plan**
- **Einzelne Tage und Nächte bearbeiten (#14, #45):** Ein Wochentag oder eine einzelne Schlafphase lässt sich direkt im Wochenplan ändern.
- **Legende (#35):** Die Wochenansicht erklärt ihre Farben.
- **Routinen in Farbe (#51):** Wiederkehrende Aufgaben ohne Kategorie sind nicht mehr grau, sondern je Name farbig, in Woche, Listen und Legende.
- **Schritte sichtbar (#43):** Die Schritte einer Routine erscheinen in Tages- und Wochenplan.
- **Planfenster (#34):** Die Wochenplanung reicht bis zum Tag vor dem nächsten Planungstag.
- **Offene Ziele der Vorwoche (#54):** Hält ein Ziel aus einer früheren Woche die Sammelliste zurück, steht es in der Wochenplanung ganz oben, mit „In diese Woche übernehmen“ und „Zurück in die Sammelliste“. Danach lässt sich die Woche sofort füllen.
- **Weiter zu deinen ToDos (#54):** „Wochenliste füllen“ beginnt mit dem Kalender und führt mit einem Knopf zu den ToDos, mit der Zahl der Karten, die du einplanen kannst.

**Einrichtung und Aussehen**
- **Eigene Routinen (#11):** Neben den Vorschlägen lassen sich beliebig viele eigene Routinen anlegen.
- **Klein anfangen (#13):** Die Einrichtung fragt nur das Nötigste; weitere Funktionen schlägt Eta später vor.
- **Wochenendtage (#12):** Welche Tage zum Wochenende gehören, legst du selbst fest, auch in den Einstellungen.
- **Tutorial (#38):** Das geführte Tutorial ist auf Woche und Planfenster abgestimmt.

**Ton**
- **Stumm und Vibration (#22, #27):** Bei stummem Handy vibriert Eta statt zu klingeln. Für „Nicht stören“ gilt standardmäßig *Still bleiben*, einstellbar unter *Einstellungen → Alarme*.

**Entwicklung**
- **Eta Fork (#47):** Die Variante `com.example.erik_iteration_2.fork` läuft neben dem Original, mit eigenen Daten.
- **APKs aus der CI (#23):** Jeder Lauf auf `main` stellt Debug- und Fork-APK bereit.
- **Automatische Ablauftests:** Mehrtägige Abläufe von der Sammelliste über Woche und Tag bis zur Abrechnung laufen bei jedem Pull Request mit; Tests auf einem Emulator laufen jede Nacht. Siehe [Tests und Szenarien](docs/testing.md).

### Geändert

- Der Weckruf schlummert zehn statt neun Minuten (#26).
- Die untere Navigationsleiste zeigt standardmäßig Icons und Namen (#29).
- System-Zurück verhält sich in der ganzen App gleich (#25).
- Die Anfahrtszeit lässt sich in 5-Minuten-Schritten einstellen (#36).
- Die Einstellungen erklären im Klartext, was 00:00 für Tagesgrenze und Schlafbeginn bedeutet (#39).
- Die deutschen Texte sind natürlicher formuliert; statt „Subtasks“ heißt es „Schritte“ (#28).
- Die Dokumentation erklärt die Einrichtung der Google-Anmeldung für eigene Schlüssel (#5).
- Schon vor diesem Paket vorhanden und deshalb nur abgehakt: das geführte Tutorial (#10), der Wochenrhythmus gerade oder ungerade (#20), die eigene Akzentfarbe unter *Einstellungen → Design* (#21) und die abschaltbaren erweiterten Optionen unter *Einstellungen → Advanced Features* (#24).
- Änderungen in den Einstellungen und an wiederkehrenden Aufgaben gelten auch für den heutigen Tag, für alles, was noch nicht begonnen hat. Begonnene, erledigte und von Hand verschobene Blöcke bleiben, ein bestätigter Morgen auch. Der Startalarm folgt dem neuen Block (#54).

### Behoben

- Schlaf nach Mitternacht wird dem richtigen Wochentag zugeordnet und beschriftet (#4).
- Ohne Dauer zeigt die Morgenroutine keinen Schritte-Editor mehr, der ins Leere speichert (#8).
- Die Scrollposition bleibt erhalten, wenn du aus einem Untermenü zurückkehrst (#7).
- Das Tutorial hakt bei teilweise getipptem Namen nicht mehr die falsche Aufgabe ab (#42).
- Ein Tag wird nicht doppelt abgerechnet, auch nicht bei gleichzeitigem Abschluss.
- Zeitblocker aus dem Kalender zählen nicht als „ungeplante Zeit“.
- „Wochenliste füllen“ zeigte keine ToDos, solange ein Ziel einer früheren Woche offen war (#54, #49).

### Noch auf dem Gerät zu prüfen

Diese Punkte sind getestet, aber noch nicht auf echter Hardware oder mit echtem Konto bestätigt:

- Stumm- und Vibrationsmodus auf dem Poco (#48, #6).
- Google-Anmeldung mit eigenem OAuth-Client, stündlicher Abgleich, Zeitblocker und Serien, auch das automatische Serienangebot mit einer echten Vorlesungsreihe (#5, #15 bis #17, #53).
- Die neuen Knöpfe und Dialoge der Wochenplanung (#54) und die Seite *Pausen & Freizeit* (#56) auf dem Handy.
- Wecker über die Uhr-App (#18), Wind-down (#44) und Nachholen verpasster Abende (#50) im echten Tagesablauf.

---

## So pflegst du diese Datei

1. Jede Änderung, die Nutzerinnen und Nutzer bemerken, kommt unter **Unveröffentlicht** in *Neu*, *Geändert* oder *Behoben*, mit Issue-Nummer, in Alltagssprache.
2. Beim Veröffentlichen wird **Unveröffentlicht** zu `## vX.Y.Z (JJJJ-MM-TT)`, und darüber entsteht eine neue leere Rubrik **Unveröffentlicht**.
3. Danach den Tag pushen, wie in [Android-CI/CD](docs/ci-cd.md#neue-version-veröffentlichen) beschrieben. GitHub erzeugt die Release Notes automatisch; wer die redaktionelle Fassung möchte, kopiert den fertigen Abschnitt dieser Datei in eine Datei und übernimmt ihn mit `gh release edit vX.Y.Z --notes-file notes.md`.
4. Hinweise zum Aktualisieren, etwa Schemaversionen oder Signaturwechsel, stehen immer ganz oben im jeweiligen Abschnitt.
