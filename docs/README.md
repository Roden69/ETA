---
doc_type: documentation-index
status: current
owner: eta-maintainers
summary: Einstieg in ETA, seine Quellen und den gemeinsamen Dokumentationsworkflow.
title: ETA Dokumentation
---

# ETA Dokumentation

ETA ist eine Android-Produktivitätsapp mit Aufgabenlisten, Tages- und Wochenplanung, Routinen, Punkten und Selbstverträgen. Diese Dokumentation ist der gemeinsame Einstieg für Menschen und KI-Agenten.

## Startpunkte

- [Architektur](architecture.md): Zuständigkeiten, Datenfluss und zentrale Invarianten.
- [Entwicklung und Prüfung](development.md): Build, Tests, Gerätekontrolle und sicherer Umgang mit App-Daten.
- [Dokumentation pflegen](workflow.md): Quellenrangfolge, Status, Änderungsreview und nachvollziehbare Prüfung.
- [Dokumentationsentscheidung](decisions/0001-docs-as-code.md): gewählter Aufbau und Alternativen.

## Welche Quelle beantwortet welche Frage?

| Frage | Kanonische Quelle | Einordnung |
| --- | --- | --- |
| Was soll die App leisten? | [`Eta_doc/Konzept.md`](../Eta_doc/Konzept.md) und die weiteren Produktspezifikationen | Anforderungen; kein Beweis der Umsetzung |
| Wie ist ein Fachbereich umgesetzt und warum? | Die passenden Dateien unter [`.claude/rules/`](../.claude/rules/) | Fachnotizen; konkrete Aussagen mit dem Code abgleichen |
| Was tut die aktuelle Implementierung? | [`app/src/`](../app/src/) und ihre Tests | Tatsächliche Implementierung; Laufzeitverhalten gesondert prüfen |
| Welche Konfiguration und Schemaversion liegen vor? | Buildkonfiguration und [`app/schemas/`](../app/schemas/) | Beim Website-Build als Projektreferenz abgeleitet |
| Warum wurde früher etwas so entschieden? | Historische `step-*`- und `steps-*`-Notizen | Historie; keine automatisch aktuellen Anweisungen |
| Was wurde in einer konkreten App-Version geliefert? | Die vorhandenen [Patchnotes](../patchnotes/README.md) | Änderungsnachweis einer Version, kein aktuelles Gesamthandbuch |
| Was könnte als Nächstes geändert werden? | [`update.txt`](../update.txt) | Informeller Backlog, kein Changelog und keine umgesetzte Funktion |

Die vorhandenen Quellverzeichnisse bleiben erhalten. Die Website rendert dieselben Dateien; Änderungen an einer Darstellung werden niemals zurück in die kanonischen Quellen synchronisiert.

## Durchsuchbare Website

```bash
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
python3 scripts/documentation.py check
.venv-docs/bin/python scripts/documentation.py serve --port 8000
```

Danach [http://127.0.0.1:8000/](http://127.0.0.1:8000/) öffnen. Die Vorschau wird nur lokal bereitgestellt; nach Quellenänderungen den Befehl neu starten. Ein statischer Build entsteht mit:

```bash
.venv-docs/bin/python scripts/documentation.py build
```

Die Website liegt unter `build/documentation/site/`, die vorbereiteten Markdown-Projektionen unter `build/documentation/source/`. Beide sind generiert und werden nicht bearbeitet oder eingecheckt. Python 3.11 oder neuer ist erforderlich; der Dokumentationsbuild benötigt weder Android SDK noch Gradle.

### Bereiche der gebauten Website

Diese Links existieren nach dem Build; die kanonischen Quellen sind oben beziehungsweise in der [Themenzuordnung](documentation-map.json) angegeben.

- [Produktspezifikationen](specifications/index.md)
- [Fachnotizen](notes/index.md)
- [Generierte Projektreferenz](reference/project.md)
- [Historisches Archiv](history/index.md)
- [Änderungsnotizen](changes/index.md)

## Einstieg für KI-Agenten

1. Diesen Einstieg und den [Pflegeworkflow](workflow.md) lesen.
2. In [`documentation-map.json`](documentation-map.json) die betroffenen Themen bestimmen.
3. Nur die passenden kanonischen Fachnotizen, Anforderungen und Implementierungsstellen nachladen.
4. Historische Notizen als Kontext verwenden, nicht als aktuellen Ist-Zustand übernehmen.
5. Änderungen an Verhalten und Dokumentation gemeinsam prüfen; Belege und offene Unsicherheit nennen.

Die gebaute Website stellt `/llms.txt` als kuratierten Einstieg und `/markdown/` als Markdown-Ansicht bereit. Historische Notizen sind nicht im kuratierten Index und nicht in der Standardsuche enthalten, bleiben aber im Archiv mit Warnhinweis lesbar. Ein Index garantiert weder, dass ein Agent die Inhalte geladen hat, noch dass er sie korrekt anwendet.

## CI und Veröffentlichung

Der Dokumentationsworkflow prüft die Quellen, baut die Website und stellt sie als GitHub-Actions-Artefakt bereit. Auf Pull Requests ermittelt er zusätzlich Dokumente, die anhand der geänderten Pfade geprüft werden sollen. Inhaltliche Aktualität und die Begründung für eine nicht notwendige Dokuänderung bleiben Review-Aufgaben.

GitHub Pages oder ein öffentlicher Hostingdienst werden nicht automatisch aktiviert. Vor einer Veröffentlichung alle mitgebauten Fachnotizen und Exporte auf sensible Inhalte prüfen; Zugriffsschutz muss auch Suchindizes, Markdown-Ansichten und Vorschauartefakte umfassen.
