---
doc_type: guide
status: current
owner: eta-maintainers
summary: Lokale Einrichtung, Validierung und sichere Release-Arbeit für Eta.
---

# Entwicklung

Diese Anleitung beschreibt den Android-Entwicklungsablauf im Repository. Für aktuelle Toolchain-, Schema- und Projektinventardaten siehe den [Dokumentationseinstieg](README.md), der auf die generierte Referenz verweist. Versions- und Zählwerte werden hier absichtlich nicht dupliziert.

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

Der [Dokumentationsworkflow](../.github/workflows/documentation.yml) prüft und baut die Dokumentation unabhängig von der Android-Toolchain. Er baut oder veröffentlicht keine APK und aktiviert kein öffentliches Dokumentationshosting. Android-Build- oder Release-Automation ist nur dann eingerichtet, wenn ein entsprechender Workflow tatsächlich im Repository vorhanden ist; keine Release-Berechtigungen oder Tag-Automation aus historischen Notizen ableiten. Die menschenlesbaren Änderungen einer gelieferten App-Version stehen in den vorhandenen [Patchnotes](../patchnotes/README.md).
