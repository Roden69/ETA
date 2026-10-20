---
doc_type: guide
status: current
owner: eta-maintainers
summary: Manuelle Prüfungen vor einer Veröffentlichung auf dem Xiaomi Poco F7 Pro (HyperOS 3, Android 16), die Emulator und Unit-Tests nicht abdecken.
---

# Gerätecheckliste

Diese Liste ergänzt die automatischen Prüfungen aus [Entwicklung und Prüfung](development.md). Sie gilt für das Gerät des Eigentümers, ein Xiaomi Poco F7 Pro mit HyperOS 3 und Android 16. Emulator und Unit-Tests belegen keinen dieser Punkte.

Vor dem Start: Debug-Build oder signierte APK installieren. Die Datenbank nicht löschen (siehe [Entwicklung](development.md#daten-und-signiersicherheit)). Zu jedem Punkt Datum, Build-Version und Ergebnis notieren; Abweichungen mit Issue-Nummer festhalten.

## 1. Stumm, Vibration und Nicht stören

Bezug: Issue #48. Der Build vom 19.10. prüft den Klingelmodus laut Fachnotiz bereits; auf dem Gerät ist das noch nicht bestätigt.

1. Klingelmodus **Klingeln**: Aufgabenstart und Aufgabenende sind hörbar.
2. Klingelmodus **Vibrieren**: Es vibriert, Eta spielt keinen Ton.
3. Klingelmodus **Lautlos**: Kein Ton. Prüfen, ob der Planungswecker klingelt, wie die Notiz zum Alarm vorsieht.
4. **Nicht stören** aktiv: Dasselbe Verhalten wie bei Lautlos; Aufgabenstart und -ende erscheinen als Benachrichtigung.

## 2. Planungswecker und Erinnerungstöne

Bezug: Issue #48.

1. Planungswecker für die tägliche und die wöchentliche Planung auslösen lassen (Uhrzeit vorstellen oder auf die nächste Zeit warten): Ton, Benachrichtigung und Hinweis erscheinen.
2. **Schlummern** antippen: Der Wecker kommt nach fünf Minuten wieder.
3. **NOTFALL** antippen und eine Stundenzahl wählen: Der Wecker kommt zur gewählten Zeit.
4. Die Erinnerungstöne spielen über den erwarteten Kanal (Lautstärke wie in der Notiz zum Alarm beschrieben).

## 3. Wecker über dem Sperrbildschirm

1. Gerät sperren, Wecker auslösen lassen: Der Weckbildschirm erscheint über dem Sperrbildschirm.
2. Ohne Entsperren den Wecker stoppen und schlummern: Beide Aktionen wirken.

## 4. Google-Anmeldung und Serienübernahme (Issue #53)

Voraussetzung: eine echte wiederkehrende Vorlesungsserie im Google-Kalender des Eigentümers.

1. Kalender-Einstellungen → **Mit Google verbinden**. Die Anmeldung schließt ohne Fehler ab.
2. Termine laden. Die Serie erscheint als Serie, nicht als Einzeltermine.
3. Serie als **Wiederkehrende Aufgabe übernehmen**. Danach steht sie als wiederkehrende Aufgabe in der Liste, nicht als einzelne Aufgabe mit Datum.
4. Serien mit Enddatum oder Anzahl übernimmt die App nicht; der Hinweis dazu muss erscheinen und der Termin bleibt einzeln.

## 5. Quick-Add-Widget

1. Widget auf dem Startbildschirm hinzufügen. Größe und Text sind lesbar.
2. Antippen öffnet die transparente Eingabe; eine Aufgabe anlegen, sie erscheint in den Listen.
3. Nach einem Neustart des Geräts erneut prüfen.

## 6. Übersicht der wiederkehrenden Tasks (Issues #55, #57)

1. Übersicht der **Wiederkehrenden Tasks** öffnen. Zeilen, Abstände und Zeiten sind vollständig lesbar, auch bei langen Namen.
2. Gruppen und Sperr-Zustände bleiben bei Dunkelmodus und großer Systemschrift lesbar.
3. Eine Aufgabe bearbeiten und zurückkehren: Die Übersicht zeigt die Änderung ohne Neustart der App.

## 7. Neustart des Geräts

1. Gerät neu starten, App nicht öffnen.
2. Nach dem Start den nächsten Planungswecker in der Statusleiste oder der Uhr-App prüfen.
3. Ein Wecker zur geplanten Zeit klingelt trotzdem.

## 8. Sicherung und Wiederherstellung

1. Sicherung über die Einstellungen erstellen und die Datei im Systemdialog speichern.
2. Wiederherstellen aus derselben Datei; danach die App neu starten.
3. Die Daten erscheinen vollständig. Vor dem Wiederherstellen keine App-Daten löschen.

## 9. Akku und Autostart (HyperOS)

Ohne diese Einstellungen können Wecker auf HyperOS ausbleiben.

1. Einstellungen → Apps → Eta → Akkunutzung → **Keine Einschränkungen**.
2. Einstellungen → Apps → Eta → Autostart → erlauben.
3. Berechtigung für exakte Wecker erteilen, falls der Dialog erscheint.
4. Danach Punkt 7 wiederholen, um die Wirkung zu prüfen.
