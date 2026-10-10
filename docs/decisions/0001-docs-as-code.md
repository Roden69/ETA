---
doc_type: adr
status: accepted
owner: eta-maintainers
summary: Entscheidung für versionierte Markdown-Quellen mit automatisierter Website-Projektion.
---

# ADR 0001: Dokumentation als Code

- **Status:** Angenommen
- **Entscheidung:** Markdown in Git als kanonische Dokumentation pflegen und vorhandene Produkt- und Themenquellen für eine menschenlesbare Zensical-Site projizieren; die Dokumentationswerkzeuge in CI prüfen lassen.

## Kontext

Eta hat bereits Produktspezifikationen in `Eta_doc/` und thematische Implementierungsnotizen in `.claude/rules/`. Menschen können diese Quellen lesen; Agenten erhalten die Notizen abhängig vom Werkzeug nicht automatisch. Eine zweite, unabhängig gepflegte Webdokumentation würde leicht von Anforderungen und Implementierung abweichen. Benötigt wird eine auffindbare Ansicht, ohne bestehende Quellen zu migrieren oder deren Zuständigkeit zu verwischen.

## Entscheidung

Markdown-Dateien im Repository bleiben kanonisch und werden nach Themen zugeordnet. Ein Dokumentationswerkzeug erzeugt daraus Projektionen unter `build/documentation/source`, und Zensical baut daraus die Site unter `build/documentation/site`. Workflow und Zuordnung erklären Kontextwahl, Pflege und die Grenzen automatischer Prüfung. CI führt Dokumentationsprüfungen aus; die Android-Build- und Release-Abläufe bleiben davon unabhängig. Generierte Projektionen werden nicht manuell bearbeitet.

Diese Entscheidung dokumentiert die gewählte Architektur, nicht deren Laufzeitnachweis: `accepted` bedeutet weder, dass eine Site bereits erfolgreich gebaut oder veröffentlicht wurde, noch aktiviert es GitHub Pages.

## Betrachtete Alternativen

- **Nur vorhandene Dateien ohne Projektionsansicht:** Einfach und ohne neue Werkzeuge, aber die verstreuten Quellen bieten keine zusammenhängende, für Menschen leicht navigierbare Ansicht.
- **Separat gepflegte Website-Inhalte oder Migration in ein neues Dokumentationssystem:** Bietet freie Informationsarchitektur, schafft jedoch doppelte Wahrheiten oder verlangt eine riskante Verlagerung bestehender kanonischer Quellen.
- **Nur Anbieterregeln bzw. AGENTS-Dateien:** Können Agenten Hinweise geben, sind aber keine geeignete Website und werden je nach Werkzeug unterschiedlich geladen.

## Folgen

- Anforderungen und bestehende Implementierungsnotizen bleiben an ihrem bisherigen Ort; ihre Projektion ist keine neue Autorität.
- Eine Themenzuordnung verbindet Quellen und Ansicht. Änderungen an Quellen müssen daher der Zuordnung und der lesbaren Erklärung gegenübergestellt werden.
- Menschen erhalten eine navigierbare Ansicht; Agenten können dieselben Quellpfade und die Pflegeanleitung gezielt abrufen.
- Ein automatischer Metadaten-, Link- und Zuordnungscheck findet strukturelle Fehler, beweist aber weder sachliche Richtigkeit noch tatsächliches Verhalten. Dafür bleibt der Vergleich mit Quellcode und Anforderungen notwendig.
- Zensical und Python-Dokumentationsabhängigkeiten verursachen Installations- und CI-Pflegeaufwand; generierte Ausgaben gehören nicht in den manuellen Redaktionsfluss.

## Bestätigung

Die Einführung wurde am 2026-10-10 lokal durch Quellenprüfung, Werkzeug-Regressionstests und einen strikten Zensical-Build geprüft. Im Browser wurden Desktop- und Mobilansicht, Suche, historische Statushinweise und die eingebundene Planer-Grafik ausgeübt; der Suchindex enthielt keine Seiten aus dem historischen Entwicklungsarchiv. `llms.txt` und eine Markdown-Fachnotiz wurden über den lokalen HTTP-Server abgerufen. Dies ist ein lokaler Einführungsnachweis, kein ausgeführter GitHub-Actions-Lauf und keine Prüfung sämtlicher fachlicher Aussagen.
