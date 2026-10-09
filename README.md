# ETA

Android-Produktivitätsapp mit Aufgabenlisten, Tages- und Wochenplanung, Routinen, Belohnungspunkten und Selbstverträgen. Die Oberfläche ist deutsch; die Implementierung verwendet Kotlin, Compose Foundation und Room.

## Dokumentation

**[Gemeinsamer Einstieg für Menschen und KI-Agenten: docs/README.md](docs/README.md)**

- [Architektur und Zuständigkeiten](docs/architecture.md)
- [Entwicklung, Builds und Prüfung](docs/development.md)
- [Dokumentationspflege und KI-Workflow](docs/workflow.md)
- [Produktspezifikationen](Eta_doc/Konzept.md)
- [Themenzuordnung zu kanonischen Quellen](docs/documentation-map.json)

Bestehende Spezifikationen unter `Eta_doc/` und Fachnotizen unter `.claude/rules/` bleiben die Quellen. Eine durchsuchbare Website wird daraus erzeugt, nicht separat gepflegt.

### Lokale Dokumentationswebsite

Python 3.11 oder neuer:

```bash
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
python3 scripts/documentation.py check
.venv-docs/bin/python scripts/documentation.py serve --port 8000
```

[http://127.0.0.1:8000/](http://127.0.0.1:8000/) öffnen. Nach Quellenänderungen die Vorschau neu starten. Für einen statischen Build:

```bash
.venv-docs/bin/python scripts/documentation.py build
```

Ausgabe: `build/documentation/site/`. Die Website enthält Statushinweise, eine lokale Suche, ein getrenntes historisches Archiv, eine aus der Konfiguration generierte Projektreferenz und `llms.txt` mit Markdown-Zugängen für Agenten. Eine öffentliche Veröffentlichung ist nicht eingerichtet.

## Android-Build

Das Android SDK muss über die lokale `local.properties` oder die SDK-Umgebung verfügbar sein. Unter Linux den nicht ausführbar eingecheckten Wrapper durch Bash starten:

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleRelease
bash gradlew lint
```

Für Installation, Gerätekontrolle und Signatur-/Datenschutzgrenzen die [Entwicklungsanleitung](docs/development.md) verwenden. Dokumentationsprüfungen ersetzen weder App-Tests noch eine Prüfung auf einem Android-Gerät.
