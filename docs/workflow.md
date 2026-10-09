---
doc_type: workflow
status: current
owner: eta-maintainers
summary: Arbeitsablauf zum Pflegen, Prüfen und Veröffentlichen der Eta-Dokumentation.
---

# Dokumentation pflegen

## Quellen und Kontext

Menschen und Agenten verwenden dieselben kanonischen Quellen: `Eta_doc/*.md` enthält Produktanforderungen, `.claude/rules/*.md` enthält thematische Implementierungsnotizen, und Quellcode sowie Konfiguration belegen das tatsächlich implementierte Verhalten. Diese Dateien bleiben an ihrem Ort und werden nicht für die Website umgeschrieben. Die Dokumentationsseite projiziert sie als lesbare Ansicht; ihre angezeigte Form ist keine zusätzliche Quelle der Wahrheit.

Lade für eine Aufgabe zuerst den passenden Eintrag aus [`documentation-map.json`](documentation-map.json): Er ordnet Quellpfade Themen und den Seitenansichten zu. Lies dann nur die einschlägigen kanonischen Anforderungen, Implementierungshinweise und Code-/Konfigurationsstellen. Erweitere den Kontext bei Bedarf anhand der Verweise, statt ganze Verzeichnisse pauschal einzulesen. Bei Unklarheiten gewinnt der aktuelle Code für das Laufzeitverhalten; Anforderungen und Notizen dürfen davon abweichen, und solche Abweichungen sind kenntlich zu machen statt stillschweigend zu verschmelzen.

Halte drei Arten von Aussagen auseinander:

- **Anforderung:** Was das Produkt tun soll; maßgeblich sind die Produktspezifikationen in `Eta_doc/`.
- **Implementiertes Verhalten:** Was der aktuelle Code tatsächlich tut; Code und Konfiguration sind dafür der Beleg.
- **Historischer Umsetzungsschritt:** Was bei einer früheren Änderung galt oder getan wurde. `step-*`-Notizen sind historische Einordnung und können veraltet sein; sie belegen nicht automatisch den aktuellen Zustand.

Kopiere keine Inhalte zwischen `AGENTS.md`, Anbieter-/Assistentenanweisungen (etwa `.claude/rules/`) und Website-Dokumentation. Diese Orte haben verschiedene Zwecke und Ladebedingungen. Verweise auf die kanonische Quelle und ergänze nur die jeweils nötige Orientierung. Ändert sich Verhalten, aktualisiere die betroffene kanonische Quelle im selben Verhaltensänderungs-Commit oder derselben Änderung; ändere nicht nur deren Projektion.

## Metadaten und Freigabe

Neue Leitfäden und ADRs unter `docs/` haben YAML-Frontmatter mit `doc_type`, `status`, `owner: eta-maintainers` und einer deutschsprachigen einzeiligen `summary`. Die vorhandenen Produktspezifikationen und Anbieterregeln behalten ihr bestehendes Format; ihre Website-Einordnung wird aus den Sammlungen in `documentation-map.json` abgeleitet. Diese Einordnung ist kein nachträglicher Laufzeitnachweis. Status beschreiben den redaktionellen Lebenszyklus:

- `proposed`: Entwurf, noch nicht als maßgebliche Dokumentation angenommen.
- `accepted`: fachlich angenommen; bei ADRs markiert dies die Entscheidung.
- `current`: aktuell gültige Orientierung oder Beschreibung.
- `historical`: bewusst als Rückblick behalten, nicht als aktuelle Anleitung verwenden.
- `superseded`: durch eine benannte neuere Quelle oder Entscheidung ersetzt.

Eine Annahme ist keine Ausführungsfreigabe: `accepted` erteilt weder Produkt- noch Betriebs- oder Änderungsberechtigungen. Bei Ablösung den Nachfolger verlinken und den alten Eintrag nicht kommentarlos als aktuell stehen lassen. Verwende keine erfundenen Laufzeitprüfungen oder Prüfzeitpunkte. Wenn ein Review-Datum sinnvoll ist, benenne es ausdrücklich als redaktionelles Review-Datum und nicht als Beleg für ausgeführte Verifikation.

## Änderung, Zuordnung und Prüfung

Bei jeder Dokumentationsänderung:

1. Ändere kanonische Anforderungen oder Implementierungsnotizen dort, wo die Aussage hingehört; bei Verhaltensänderungen geschieht dies gemeinsam mit der Verhaltensänderung.
2. Prüfe `docs/documentation-map.json`: Ordne den geänderten Quellpfad dem passenden Dokumentationsthema zu. Ergänze oder korrigiere die Zuordnung, wenn ein Thema oder eine kanonische Quelle neu ist.
3. Aktualisiere die menschenlesbare Erklärung, falls sie durch die Änderung unzutreffend oder unvollständig würde. Falls keine Dokumentation geändert wird, halte im Änderungskontext den konkreten Grund fest (zum Beispiel eine rein interne Änderung ohne Auswirkung auf Anforderungen, Bedienung oder Architektur); „keine Doku nötig“ ohne Begründung genügt nicht.
4. Bearbeite niemals generierte Dateien unter `build/documentation/source` oder `build/documentation/site`. Ändere deren kanonische Eingabe und lasse das Werkzeug die Projektion neu erzeugen.
5. Prüfe lokal mit den untenstehenden Befehlen. Der automatisierte Check prüft Metadaten, Links und die Zuordnung; er kann weder die Bedeutung einer Aussage noch tatsächliches Laufzeitverhalten beweisen. Vergleiche wichtige Aussagen daher mit Quellcode und Anforderungen und beschreibe nur tatsächlich ausgeführte Prüfungen als Verifikation.

Dokumentationswerkzeuge installieren:

```sh
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
```

Metadaten, Links und Zuordnung prüfen:

```sh
python3 scripts/documentation.py check
```

Die Zuordnung zu einer Git-Änderung lässt sich zusätzlich prüfen:

```sh
python3 scripts/documentation.py check --base origin/main
python3 -m unittest discover -s scripts/tests -v
```

Der erste Befehl nennt die zu prüfenden kanonischen Dokumente für Änderungen zwischen der gemeinsamen Git-Basis und `HEAD`. Er beweist nicht, dass diese Dokumente inhaltlich überprüft wurden, und erfasst keine uncommiteten Änderungen.

Projektionsquellen erzeugen und Zensical-Site bauen:

```sh
.venv-docs/bin/python scripts/documentation.py build
```

Lokale Vorschau (baut zunächst erneut und startet dann den HTTP-Server):

```sh
.venv-docs/bin/python scripts/documentation.py serve --port 8000
```

Die Ausgabe unter `build/documentation/` ist generiert. Diese Anleitung behauptet nicht, dass GitHub Pages aktiviert oder die Site anderweitig veröffentlicht ist.

## Pflege durch Menschen und Agenten

Verantwortlich sind die Eta-Maintainer (`eta-maintainers`). Beitragsleistende gleichen jede Aussage mit den kanonischen Quellen ab, halten Quell- und Projektionsänderungen zusammen und hinterlassen keine geheimen oder privaten Inhalte. Niemals Zugangsdaten, Tokens, private Nutzerdaten, lokale Maschinenpfade oder vertrauliche Betriebsdetails in Dokumentation, Beispielen, Screenshots oder generierten Artefakten veröffentlichen. Bei unklarer Freigabe weglassen und einen unbedenklichen Platzhalter verwenden.

Als Retrieval-Übung in einer frischen Sitzung ohne vorausgesetzten Gesprächskontext: Bitte einen Menschen oder Agenten, (1) eine konkrete Produktanforderung samt kanonischer Quelle zu finden, (2) deren implementiertes Verhalten anhand des Codes getrennt davon zu belegen und (3) eine `step-*`-Notiz als historisch oder aktuell einzuordnen. Wiederhole die Übung für mindestens ein Thema aus der Zuordnung. Scheitert sie, verbessere Index, Zuordnung oder Quellverweise; kopiere nicht vorsorglich ganze Quelltexte in zusätzliche Regeldateien.
