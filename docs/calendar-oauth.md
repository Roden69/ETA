---
doc_type: guide
status: current
owner: eta-maintainers
summary: Google-Kalender-Anmeldung für eigene Builds einrichten — Cloud-Projekt, Zustimmungsbildschirm, Testnutzer und ein Android-OAuth-Client pro Paket und Signatur.
---

# Google-Kalender für eigene Builds einrichten

Eta liest Google-Kalender schreibgeschützt über den Bereich
`https://www.googleapis.com/auth/calendar.readonly`. Die Anmeldung läuft über
Google Play-Dienste. Die App enthält dafür **keine Client-ID und kein Geheimnis**:
Google prüft beim Anmelden, ob das Paket und der Signaturschlüssel des laufenden
Builds zu einem Android-OAuth-Client im eigenen Google-Cloud-Projekt passen.
Deshalb braucht jeder Build, der sich anmelden soll, eine eigene Registrierung.

Die Anleitung gilt für jeden eigenen Build: den lokalen Debug-Build, den
Fork-Build aus [`app/build.gradle.kts`](../app/build.gradle.kts) (Build-Typ
`fork`, siehe [Entwicklung](development.md#fork-build-neben-dem-original)) und
signierte Release-Builds. Alle Werte unten sind Platzhalter; echte Fingerabdrücke,
Kontoadressen und Schlüssel gehören nicht ins Repository und nicht in Issues.

## 1. Cloud-Projekt anlegen

1. In der [Google Cloud Console](https://console.cloud.google.com/) ein Projekt
   anlegen, etwa „Eta-Kalender“.
2. Unter **APIs und Dienste → Bibliothek** die **Google Calendar API** aktivieren.

## 2. Zustimmungsbildschirm konfigurieren

1. Unter **Google Auth Platform** (früher „OAuth-Zustimmungsbildschirm“) App-Namen
   und Support-E-Mail eintragen.
2. Zielgruppe **Extern** wählen. Ein Konto außerhalb einer Google-Organisation
   braucht dafür Testnutzer (Schritt 4).
3. Unter **Datenzugriff** den Bereich
   `https://www.googleapis.com/auth/calendar.readonly` hinzufügen. Mehr Bereiche
   sind nicht nötig; Eta schreibt nie in Google-Kalender.
4. Unter **Zielgruppe → Testnutzer** jedes Google-Konto eintragen, das Eta
   benutzen soll. Solange der Status **Testing** ist, lehnt Google alle anderen
   Konten ab. Die Meldung lautet dann, das Konto sei nicht als Tester zugelassen.
   Bei Testing-Status können Zustimmungen für Kalenderbereiche nach einigen Tagen
   erneut abgefragt werden; das ist Googles Verhalten für Testanwendungen.

## 3. Android-OAuth-Client anlegen

Unter **Clients → Client erstellen** den Anwendungstyp **Android** wählen:

- **Paketname:** die Application-ID des Builds, den Sie benutzen
  - Debug und Release: `com.example.erik_iteration_2`
  - Fork-Build: `com.example.erik_iteration_2.fork`
- **SHA-1-Fingerabdruck des Signaturschlüssels:** siehe Schritt 3a

Jede Kombination aus **Paketname + SHA-1** braucht einen eigenen Client. Ein
Client für den Debug-Schlüssel hilft einem Release-Build nicht, und umgekehrt.
Ändert sich der Signaturschlüssel oder der Paketname, ist ein neuer Client nötig,
auch wenn der alte noch angezeigt wird. Die Client-Datei von Google (JSON) wird
von Eta **nicht** benötigt; die Registrierung genügt.

### 3a. SHA-1 des lokalen Debug-Schlüssels ermitteln

Der lokale Debug-Build wird mit dem Debug-Schlüssel dieses Rechners signiert
(`~/.android/debug.keystore`; unter Windows `%USERPROFILE%\.android\debug.keystore`):

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Die Zeile `SHA1:` mit dem Fingerabdruck (`AB:CD:…`) in den Client übernehmen.
Der Standardschlüssel ist auf jedem Rechner anders; ein APK von einem anderen
Rechner mit einem anderen Debug-Schlüssel meldet sich deshalb nicht an.

## 4. Testnutzer und Anmeldung prüfen

1. Das Konto aus Schritt 2.4 auf dem Gerät hinzufügen, das den Build installiert.
2. In Eta unter **Einstellungen → Kalender** die Google-Anmeldung starten.
3. Schlägt sie fehl, zeigt Eta einen Hinweis auf Paket, Signatur und Testnutzer
   und darunter die Originalmeldung von Google. Diese Originalmeldung hilft beim
   Suchen, etwa nach dem Paketnamen oder dem Wort „Testnutzer“.

## Besondere Fälle

**CI-Debug-APK (GitHub Actions).** Die CI signiert Debug-APKs mit einem geteilten
Schlüssel aus dem Secret `ANDROID_DEBUG_KEYSTORE_BASE64`. Dessen SHA-1 braucht
einen eigenen Client für `com.example.erik_iteration_2` (bzw. `.fork`). Den
Fingerabdruck liest man mit demselben `keytool`-Befehl aus dem Keystore, den
das Secret enthält. Pull Requests aus fremden Forks erhalten kein Secret und
damit einen Wegwerfschlüssel; deren APKs melden sich nicht an. Details:
[CI/CD](ci-cd.md#debug-apk).

**Signierter Release-Build.** Der Release-Schlüssel hat einen eigenen SHA-1.
Ohne Client für genau diesen Fingerabdruck schlägt die Anmeldung fehl. Den SHA-1
aus dem Keystore lesen, nicht aus den Secrets:

```bash
keytool -list -v -keystore <pfad-zum-keystore.jks> -alias <alias>
```

**Fork-Build neben dem Original.** Der Fork hat eine eigene Application-ID und
damit einen eigenen Client. Er teilt sich weder Konto-Zustimmung noch Daten mit
der Originalinstallation; die Kalenderauswahl muss im Fork neu getroffen werden.

## Was dieses Dokument nicht abdeckt

- Eine öffentliche Veröffentlichung der App braucht eine Google-Prüfung des
  Zustimmungsbildschirms. Für Testnutzer ist sie nicht nötig.
- Wie Eta den Kalender anschließend liest, steht in
  [`.claude/rules/google-calendar.md`](../.claude/rules/google-calendar.md).
