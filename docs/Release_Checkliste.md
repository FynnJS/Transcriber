# Release-Checkliste (echtes Gerät)

Alles, was auf dem Emulator geprüft werden konnte, ist abgehakt. Diese Liste deckt die
Punkte ab, die echte Hardware brauchen: Biometrie, Aufbewahrungsfristen und die
Notification-Berechtigung.

## 1. Build erzeugen

```bash
export JAVA_HOME="$HOME/Android Studio/jbr"   # JDK >= 17
./gradlew :app:assembleRelease
```

Ergebnis: `app/build/outputs/apk/release/app-release-unsigned.apk` (arm64-v8a, R8 + Ressourcen-Shrinking).

Signieren entweder über Gradle oder manuell:

```bash
# einmalig: Schlüssel anlegen (nie einchecken, *.jks ist bereits ignoriert)
keytool -genkeypair -v -keystore release.jks -alias transcriber \
        -keyalg RSA -keysize 4096 -validity 10000

# Variante A: keystore.properties im Projektwurzelverzeichnis (ignoriert)
#   storeFile=release.jks
#   storePassword=…
#   keyAlias=transcriber
#   keyPassword=…
./gradlew :app:assembleRelease          # signiert automatisch, sobald die Datei existiert

# Variante B: nachträglich signieren
$ANDROID_HOME/build-tools/<version>/apksigner sign \
  --ks release.jks --out transcriber-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Installieren: `adb install -r transcriber-release.apk`

## 2. Vorbereitung auf dem Gerät

- [ ] Gerät entsperrt, mindestens ein Fingerabdruck/Gesicht registriert (Einstellungen → Sicherheit).
- [ ] Genug Speicher: Modell 182 MB (Small), optional 57 MB (Base).
- [ ] WLAN aktiv, App einmal starten, Onboarding abschließen, Modell laden lassen.
- [ ] Eine Sprachnachricht über WhatsApp teilen → Transkript erscheint im Overlay (Basisfall).

## 3. Biometrie-App-Sperre

- [ ] Einstellungen → Datenschutz → **App-Sperre** einschalten (Schalter ist jetzt aktiv, kein Hinweistext mehr).
- [ ] App in den Hintergrund schicken und wieder öffnen → Sperrbildschirm erscheint, Inhalt (Verlauf) ist verdeckt.
- [ ] Entsperren mit Fingerabdruck → App öffnet sich auf dem zuletzt sichtbaren Tab.
- [ ] Abbrechen/Abbruch-Geste → App bleibt gesperrt, kein Zurück in den Verlauf.
- [ ] Falscher Finger/Fehlversuch → Fehlermeldung, weiterhin gesperrt.
- [ ] Gerät sperren und entsperren, App erneut öffnen → erneute Abfrage.
- [ ] App-Sperre ausschalten → kein Sperrbildschirm mehr.
- [ ] Alle Fingerabdrücke in den Systemeinstellungen entfernen → App darf nicht in einen ungesperrten Zustand fallen (Hinweistext in den Einstellungen prüfen).

## 4. Aufbewahrungsfristen

Einstellungen → Aufbewahrung (Audio / Transkript).

- [ ] **Audio = Sofort**: Nachricht teilen, Transkription abwarten → Detailansicht zeigt keinen Player mehr, `files/audio/` ist leer, Transkript bleibt erhalten.
- [ ] **Audio = 30 Tage**: Nachricht teilen und transkribieren → Audiodatei bleibt abspielbar.
- [ ] **Ablauf simulieren**: Gerätedatum 31 Tage vorstellen, App vollständig beenden und neu starten (löst `CleanupWorker` über `runNow()` aus) → alte Audiodatei ist gelöscht, Verlaufseintrag und Transkript bleiben sichtbar; Datum danach zurücksetzen.
- [ ] **Transkript = Nie** (Standard): Einträge überleben den simulierten Ablauf.
- [ ] **Transkript = 30 Tage**: gleicher Ablauf, jetzt verschwindet auch der Eintrag aus dem Verlauf.
- [ ] Nach dem Löschen: Suche findet die alten Begriffe nicht mehr (FTS-Index).

## 5. Notification-Berechtigung

- [ ] Einstellungen → Apps → Transcriber → Benachrichtigungen **aus**; App-Daten löschen, damit die Abfrage erneut erscheint.
- [ ] App starten → Berechtigungsdialog → **Don't allow**.
- [ ] Nachricht teilen → Transkription läuft trotzdem durch, Fortschritt ist im Overlay sichtbar, kein Absturz.
- [ ] Während der Transkription App verlassen → Dienst läuft weiter, kommt nach Rückkehr zum Ergebnis.
- [ ] Berechtigung nachträglich erteilen → nächste Transkription zeigt Fortschritts-Notification (inkl. Dateiname, ohne Transkriptinhalt).

## 6. Zusätzlich sinnvoll

- [ ] Echte WhatsApp-Sprachnachricht (OGG/Opus, mehrere Minuten) teilen → Dauer, Fortschritt, Transkriptqualität.
- [ ] Einstellungen → **Bildschirmfotos verhindern**: Screenshot über die Systemfunktion → schwarzes Bild; wieder ausschalten → Screenshot funktioniert.
- [ ] Modellwechsel Small ↔ Base auf dem Gerät (Download, Umschalten, neue Transkription, alter Eintrag behält sein Modell).
- [ ] App-Sprache/Theme umschalten und App neu starten → Einstellungen bleiben erhalten.
- [ ] Flugmodus: Transkription funktioniert weiterhin (nur der Modelldownload braucht Netz).

## 7. Release-Build prüfen

- [ ] `unzip -l app-release.apk | grep lib/` zeigt **nur** `arm64-v8a`.
- [ ] `apksigner verify --print-certs` bestätigt die eigene Signatur (nicht den Debug-Key).
- [ ] Keine Debug-Marker: `aapt2 dump badging` zeigt `application-debuggable` nicht.
- [ ] Backup-Regeln greifen: Einstellungen → Apps → Transcriber → „Daten sichern" deaktiviert bzw. ein Backup enthält weder Datenbank noch Audio.
