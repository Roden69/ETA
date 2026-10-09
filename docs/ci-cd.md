# Android CI/CD

Workflow: [`.github/workflows/android.yml`](../.github/workflows/android.yml).
Ziel dieses Forks: [Roden69/ETA](https://github.com/Roden69/ETA).
Das Owner-Repository `Erbsenherr/Eta_productivity_application` ist Upstream;
dort müssen Signing-Secrets separat eingerichtet werden.

## Debug-APK

Jeder Push auf `main` und jeder Pull Request auf `main` startet Unit-Tests,
Android Lint und `assembleDebug`. Unter **Actions → Android CI/CD → Run workflow**
kann ein Debug-Build manuell gestartet werden, sobald der Workflow auf dem
Default-Branch vorhanden ist. Auch ein manueller Start auf einem Tag erzeugt
**keinen Release**.

Download: [Actions](https://github.com/Roden69/ETA/actions) → erfolgreicher Lauf →
**Artifacts → eta-debug-apk**. Im ZIP liegt `app-debug.apk`.
Die Aufbewahrungsfrist richtet sich nach den Actions-Einstellungen des Repositories.
Debug-Builds benötigen keine Repository-Secrets. GitHub-Runner erzeugen ihre eigenen
Debug-Schlüssel; diese APKs können anders signierte lokale Installationen nicht ersetzen.

## Neue Version veröffentlichen

Zuerst den Workflow nach `main` mergen. Einen geprüften `main`-Commit mit einem
neuen, aufsteigenden Tag markieren und ausschließlich diesen Tag pushen:

```bash
git fetch origin main
git tag -a v1.2.3 origin/main -m "Release v1.2.3"
git push origin v1.2.3
```

`v1.2.3` ist ein Beispiel, kein Test-Tag. Bereits veröffentlichte Tags nicht
verschieben, wiederverwenden oder per Force-Push überschreiben.
Der getaggte Commit muss den Workflow und die Gradle-Anpassung enthalten.

Ein Tag-Push baut nach Tests und Lint eine signierte Release-APK, prüft ihre
Signatur und veröffentlicht `Eta-v1.2.3.apk` unter
[Releases](https://github.com/Roden69/ETA/releases).
GitHub generiert die Release Notes automatisch. Normale Branch-Pushes,
Pull Requests und manuelle Starts veröffentlichen niemals einen Release.
Ein bestehender Release wird nicht überschrieben; der Veröffentlichungsjob
schlägt beim erneuten Erstellen desselben Releases fehl.

## Versionsschema

Erlaubt sind ausschließlich `vMAJOR.MINOR.PATCH`, ohne führende Nullen,
Prerelease-Zusätze oder Build-Metadaten. Die Grenzen sind:

- `MAJOR`: 0 bis 2099
- `MINOR`, `PATCH`: jeweils 0 bis 999

Gradle erhält `ETA_RELEASE_VERSION` ohne `v` und setzt:

```text
versionName = MAJOR.MINOR.PATCH
versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH + 1
```

Beispiel: `v1.2.3` ergibt `versionName=1.2.3`, `versionCode=1002004`.
Das Schema steigt mit der semantischen Version streng monoton; sein Maximum
ist Androids Grenze von `2100000000`. Veröffentlichungen müssen ebenfalls
aufsteigend erfolgen, damit installierte Apps aktualisiert werden können.
Ohne die Umgebungsvariable bleiben lokale Builds bei `1.0` / `1`.
Ungültige oder zu große Versionen werden von Gradle abgewiesen.

## Dauerhafter Signaturschlüssel

In diesem Fork sind vier Actions-Secrets vorgesehen:

- `ANDROID_KEYSTORE_BASE64`: Base64-Inhalt des privaten Keystores
- `ANDROID_KEYSTORE_PASSWORD`: Keystore-Passwort
- `ANDROID_KEY_ALIAS`: Schlüsselalias
- `ANDROID_KEY_PASSWORD`: Schlüsselpasswort

Die Einrichtung auf Daniels Rechner speichert den privaten Schlüssel unter
`~/.local/share/eta-signing/eta-release.jks`, Alias `eta-release`.
Die zufällig erzeugten Passwörter liegen in der geschützten
`~/.local/share/eta-signing/credentials.json`. Verzeichnisrechte: `0700`,
Dateirechte: `0600`. Beide Dateien liegen **außerhalb des Repositories**.
Die JSON-Datei enthält Klartext-Passwörter: nicht anzeigen, versenden oder
in Logs kopieren. Keystore und Zugangsdaten zusätzlich **verschlüsselt sichern**,
etwa auf einem Offline-Datenträger beziehungsweise im Passwortmanager.
GitHub gibt hinterlegte Secret-Werte nicht wieder zum Download heraus.

Bei Bedarf die vier Secrets aus diesen geschützten Dateien erneut setzen,
ohne Passwörter als Befehlsargumente oder in Logs auszugeben:

```bash
python3 - <<'PY'
import base64
import json
import subprocess
from pathlib import Path

credentials = json.loads((Path.home() / ".local/share/eta-signing/credentials.json").read_text())
values = {name: credentials[name] for name in (
    "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"
)}
values["ANDROID_KEYSTORE_BASE64"] = base64.b64encode(
    Path(credentials["ANDROID_KEYSTORE_PATH"]).read_bytes()
).decode()
for name, value in values.items():
    subprocess.run(
        ["gh", "secret", "set", name, "--repo", "Roden69/ETA"],
        input=value.encode(), check=True,
    )
PY
```

In Actions wird der Keystore ausschließlich während des Release-Schritts im
Runner-Temp-Verzeichnis rekonstruiert und beim Verlassen des Schritts entfernt,
auch bei Build-Fehlern. Er wird weder als Artifact hochgeladen noch gecacht.
Release-Builds deaktivieren den Gradle Configuration Cache und lesen den
Gradle-Cache nur; sie speichern keinen neuen Cache mit Signing-Daten.
Nur der separate Veröffentlichungsjob erhält `contents: write`.

Lokale Release-Builds bleiben ohne Signing-Variablen unsigniert.
Signierung wird nur mit allen vier Gradle-Umgebungsvariablen aktiviert:
`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`. Unvollständige Angaben oder eine fehlende Datei führen
zu einem verständlichen Fehler. Bei lokalen signierten Builds ebenfalls
`--no-configuration-cache` verwenden; keine Passwörter in Gradle-Dateien speichern.

`applicationId` bleibt `com.example.erik_iteration_2`. Updates benötigen trotzdem
**denselben Signaturschlüssel** wie die installierte APK. Der neue Release-Key
kann frühere Debug- oder vom Owner signierte APKs nicht aktualisieren.
Bei einem Signaturkonflikt nicht deinstallieren: dabei geht die lokale Datenbank
verloren. Den bisherigen Schlüssel verwenden, wenn diese Installationen weiter
aktualisiert werden müssen. Den Release-Schlüssel niemals pro Version neu erstellen.
Google-Calendar-Anmeldung benötigt zusätzlich einen Android-OAuth-Client für
Package und SHA-1 dieses Release-Schlüssels; siehe
[Calendar-Notiz](../.claude/rules/google-calendar.md).

## Fehlgeschlagener Build

1. Unter **Actions → fehlgeschlagener Lauf** den ersten fehlgeschlagenen Schritt
   und dessen Gradle-Ausgabe öffnen. `eta-debug-reports` beziehungsweise
   `eta-release-reports-<tag>` enthalten vorhandene Test-XML/HTML- und Lint-HTML-Berichte.
2. Lokal mit Java 25 und installiertem SDK 37 / Build Tools 36.0.0 prüfen:

   ```bash
   bash gradlew --console=plain --stacktrace testDebugUnitTest lint assembleDebug
   bash gradlew --console=plain assembleRelease
   ```

   Die zweite Zeile baut ohne Signing-Umgebung bewusst eine unsignierte APK.
3. Bei einem Release Version, alle vier Secrets, Keystore-Passwörter und Alias
   prüfen. Fehlende Secrets stoppen den Build vor Gradle; eine nicht überprüfbare
   APK wird nicht veröffentlicht. Keine Secrets zur Diagnose ausgeben.
4. Bei einer vorübergehenden Störung **Re-run failed jobs** verwenden, solange
   noch kein Release existiert. Bei Codefehlern korrigieren und eine neue Version
   taggen, nicht den alten Tag verschieben.

Die Pipeline verwendet den eingecheckten Gradle Wrapper 9.5.0, Java 25 entsprechend
`gradle/gradle-daemon-jvm.properties`, SDK-Paket `platforms;android-37.0` und
Build Tools 36.0.0. Java-11-Bytecode-Einstellungen und App-Abhängigkeiten bleiben
unverändert. Actions sind auf Commit-SHAs fixiert; bei Aktualisierungen SHA und
Versionskommentar gemeinsam ändern. Gradle-Caching und Wrapper-Prüfung kommen
von `gradle/actions/setup-gradle`, ohne zusätzlichen Gradle-Cache oder Build-Scan.
