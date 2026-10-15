---
doc_type: guide
status: current
owner: eta-maintainers
summary: Aufgaben über GitHub Issues, lokale Einrichtung, Validierung und sichere Release-Arbeit für Eta.
---

# Entwicklung

Diese Anleitung beschreibt den Android-Entwicklungsablauf im Repository. Für aktuelle Toolchain-, Schema- und Projektinventardaten siehe den [Dokumentationseinstieg](README.md), der auf die generierte Referenz verweist. Versions- und Zählwerte werden hier absichtlich nicht dupliziert.

## Aufgaben über GitHub Issues

Die [GitHub Issues dieses Forks](https://github.com/Roden69/ETA/issues) sind die zentrale Aufgabenliste. Es gibt keine zusätzlich gepflegten Bug- oder Feature-Dateien. Ein Issue beschreibt ein unabhängig bearbeitbares Thema; zusammenhängende Symptome dürfen gemeinsam erfasst werden.

Beim [Anlegen eines Issues](https://github.com/Roden69/ETA/issues/new/choose) stehen drei deutschsprachige Formulare bereit:

| Typ | Bedeutung | Vorlage und automatisches Label |
| --- | --- | --- |
| Bug | Eine vorhandene Funktion verhält sich fehlerhaft. | [Bug melden](../.github/ISSUE_TEMPLATE/01-bug.yml), `bug` |
| Feature | Eine neue, bisher nicht vorhandene Funktion wird gewünscht. | [Feature wünschen](../.github/ISSUE_TEMPLATE/02-feature.yml), `feature` |
| Change | Vorhandenes Verhalten, Darstellung oder ein Ablauf soll angepasst werden, ohne zwingend fehlerhaft zu sein. | [Change vorschlagen](../.github/ISSUE_TEMPLATE/03-change.yml), `change` |

Nur die Kernbeschreibung ist Pflicht; Zusatzangaben können später ergänzt werden. Technische Ursachen oder betroffene Dateien müssen beim Erfassen noch nicht bekannt sein. Das Repository und seine Issues sind öffentlich: Screenshots, Termine und Fehlermeldungen vor dem Veröffentlichen von privaten Daten und Geheimnissen bereinigen.

Ein erfasstes Issue ist noch kein Umsetzungsauftrag oder zugesagter Termin. Besonders Feature-Wünsche können zunächst im Backlog bleiben. Beauftrage die Bearbeitung mit der Issue-Nummer, zum Beispiel:

```text
Behebe Issue #42 vollständig.
Implementiere das Feature aus Issue #43.
Setze den Change aus Issue #44 um.
Untersuche Issue #45 nur und schlage eine Lösung vor; ändere noch nichts.
```

Die Umsetzung erfolgt auf einem Themenbranch und wird im zugehörigen Pull Request mit dem Issue verknüpft. Der Abschluss nennt die Änderung, tatsächlich ausgeführte Prüfungen und gegebenenfalls noch offene Geräteprüfungen. Ein Issue wird erst geschlossen, wenn die beauftragte Arbeit abgeschlossen ist; ein bloßer Vorschlag oder erfolgreicher Build reicht nicht.

Die Formulare sind unter `.github/ISSUE_TEMPLATE/` versioniert und werden nach der Integration in den GitHub-Standardbranch verfügbar. In den Repository-Einstellungen müssen Issues aktiviert sein und die Labels `bug`, `feature` und `change` existieren; die Formular-Dateien allein erzeugen keine Labels. Die [Auswahlkonfiguration](../.github/ISSUE_TEMPLATE/config.yml) lenkt Meldungen auf die drei Vorlagen. GitHub erlaubt Personen mit Schreibrechten weiterhin leere Issues; dort muss der Typ manuell als Label gesetzt werden.

Issues halten Arbeitsaufträge und Entscheidungen fest. Dauerhaft gültige Anforderungen und Implementierungsnotizen bleiben in den bestehenden kanonischen Dokumentationsquellen; ihre Pflege beschreibt der [Dokumentationsworkflow](workflow.md).


## Lokale Voraussetzungen und SDK

Verwende eine passende Java-Installation und ein Android SDK, das die im App-Modul konfigurierte Compile-SDK-Plattform und Build-Tools bereitstellt. Die konkreten Werte ändern sich mit der Projektkonfiguration; maßgeblich sind [`app/build.gradle.kts`](../app/build.gradle.kts), [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) und die [Gradle-Wrapper-Konfiguration](../gradle/wrapper/gradle-wrapper.properties).

Android Studio ist optional; Gradle kann direkt verwendet werden. Unter Linux den Wrapper explizit über Bash starten, damit keine Ausführungsbit-/Dateisystembesonderheit den Aufruf verhindert:

```bash
bash ./gradlew --version
bash ./gradlew --console=plain testDebugUnitTest lint assembleDebug
```

Falls Gradle das SDK nicht findet, setze `ANDROID_HOME` oder `ANDROID_SDK_ROOT` auf den SDK-Ordner. Alternativ trage dessen lokalen Pfad in `local.properties` unter `sdk.dir` ein; diese Datei ist maschinenspezifisch und gehört nicht ins Repository. Stelle sicher, dass die erforderliche Plattform und Build-Tools installiert und Android-SDK-Lizenzen akzeptiert sind.

## Validierung und tatsächliche Oberfläche

Für die reguläre lokale Prüfung:

```bash
bash ./gradlew --console=plain testDebugUnitTest lint assembleDebug
```

Der lokale Release-Build verwendet die Versions- und Signierkonfiguration aus `app/build.gradle.kts`. Ist keine Release-Signierkonfiguration eingerichtet, entsteht eine unsignierte APK:

```bash
bash ./gradlew --console=plain assembleRelease
```

Eine APK kann eine bestehende Installation nur aktualisieren, wenn sie mit demselben Schlüssel signiert ist. Bei einer Signaturabweichung nicht deinstallieren, um das Update zu erzwingen: Dabei gingen die lokalen App-Daten verloren.

Tests, Kompilierung und Lint ersetzen keine Prüfung der wirklichen Android-Oberfläche. Bei Änderungen an Screens, Navigation, Berechtigungen, Alarmen, Kalenderintegration, Bedienabläufen oder Darstellung muss die tatsächliche Oberfläche auf einem physischen Android-Gerät mit der relevanten Nutzerroute geprüft werden. Instrumentierte Compose-Tests können ergänzen, aber sie allein belegen nicht, dass Layout, Systemdialoge und reale Geräteintegration wie beabsichtigt funktionieren.

## Daten- und Signiersicherheit

Bei Room-Änderungen gehören zum Arbeitsablauf die passende Migration, das aktualisierte exportierte Schema und die zugehörigen Migrationstests. Niemals lokale Nutzerdatenbanken löschen, zurücksetzen oder mit einer frisch erzeugten Datenbank ersetzen, um einen Test oder Versionswechsel „zu reparieren“. Migrationen sind besonders mit einer Kopie repräsentativer Daten zu prüfen; Backups und Originaldaten bleiben unangetastet. Die [Datenbanknotiz](../.claude/rules/database.md) erläutert die Schema-Invarianten.

Keystore-Dateien, Passwörter und Tokens sind Geheimnisse: nicht committen, in Tickets/Logs/Chat kopieren oder in Shell-History hinterlegen. Lokal benötigte Signierwerte ausschließlich über sichere lokale Eingaben oder geschützte Umgebungsvariablen bereitstellen. Niemals Geheimniswerte zur Diagnose ausgeben.

## CI und Veröffentlichung

Der [Dokumentationsworkflow](../.github/workflows/documentation.yml) prüft und baut die Dokumentation unabhängig von der Android-Toolchain. Er baut keine APK und aktiviert kein öffentliches Dokumentationshosting. Der [Android-CI/CD-Workflow](ci-cd.md) führt die Android-Prüfungen aus und veröffentlicht signierte Release-APKs bei passenden Tags. Die menschenlesbaren Änderungen einer gelieferten App-Version stehen in den vorhandenen [Patchnotes](../patchnotes/README.md).
