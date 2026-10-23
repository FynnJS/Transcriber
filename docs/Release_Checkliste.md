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
- [ ] Genug Speicher: Modell 182 MB (Small), optional 57 MB (Base), mit beiden Modellen zusammen 239 MB.
- [ ] WLAN aktiv, App einmal starten, Onboarding abschließen, Modell laden lassen (optional „Auch Base-Modell laden“ aktivieren → beide Modelle werden geladen, der Hinweistext und die Schaltfläche zeigen dann zusammen 239 MB).
- [ ] Nach dem Onboarding in den Einstellungen prüfen: Base steht als „Bereit“, falls die Option gewählt wurde.
- [ ] Eine Sprachnachricht über WhatsApp teilen → Transkript erscheint im Overlay (Basisfall).

## 3. Biometrie-App-Sperre

- [ ] Einstellungen → Datenschutz → **App-Sperre** einschalten (Schalter ist jetzt aktiv, kein Hinweistext mehr) → Biometrie-Abfrage erscheint, erst nach erfolgreicher Bestätigung steht der Schalter auf an.
- [ ] Biometrie-Abfrage beim Einschalten abbrechen → Schalter bleibt aus, App-Sperre ist nicht aktiv.
- [ ] App in den Hintergrund schicken (Home oder andere App) und wieder öffnen → Sperrbildschirm erscheint erneut, ohne Kaltstart; Inhalt (Verlauf) ist verdeckt.
- [ ] Gerät im entsperrten Zustand drehen → App bleibt entsperrt, keine erneute Abfrage.
- [ ] Entsperren mit Fingerabdruck → App öffnet sich auf dem zuletzt sichtbaren Tab.
- [ ] Abbrechen/Abbruch-Geste → App bleibt gesperrt, kein Zurück in den Verlauf.
- [ ] Falscher Finger/Fehlversuch → Fehlermeldung, weiterhin gesperrt.
- [ ] Gerät sperren und entsperren, App erneut öffnen → erneute Abfrage.
- [ ] App-Sperre ausschalten → Biometrie-Abfrage erscheint; erst nach Bestätigung steht der Schalter auf aus und es erscheint kein Sperrbildschirm mehr.
- [ ] Biometrie-Abfrage beim Ausschalten abbrechen → Schalter bleibt an, App-Sperre ist weiterhin aktiv.
- [ ] Alle Fingerabdrücke in den Systemeinstellungen entfernen → App darf nicht in einen ungesperrten Zustand fallen (Hinweistext in den Einstellungen prüfen).
- [ ] Nachricht über das Teilen-Overlay öffnen (App-Sperre aktiv) → das Overlay zeigt das Transkript ohne zusätzliche Abfrage; der Verlauf bleibt beim Zurückkehren gesperrt.

## 4. Aufbewahrungsfristen

Einstellungen → Aufbewahrung (Audio löschen / Transkript löschen).

- [ ] **Audio löschen = Sofort nach Transkription**: Nachricht teilen, Transkription abwarten → Detailansicht zeigt keinen Player mehr, `files/audio/` ist leer, Transkript bleibt erhalten.
- [ ] **Audio löschen = Nach 30 Tagen**: Nachricht teilen und transkribieren → Audiodatei bleibt abspielbar.
- [ ] **Ablauf simulieren**: Gerätedatum 31 Tage vorstellen, App vollständig beenden und neu starten (löst `CleanupWorker` über `runNow()` aus) → alte Audiodatei ist gelöscht, Verlaufseintrag und Transkript bleiben sichtbar; Datum danach zurücksetzen.
- [ ] **Transkript löschen = Nie** (Standard): Einträge überleben den simulierten Ablauf.
- [ ] **Transkript löschen = Nach 30 Tagen**: gleicher Ablauf, jetzt verschwindet auch der Eintrag aus dem Verlauf.
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
- [ ] App-Icon auf Homescreen und in den App-Infos prüfen: neues Logo, runde Maske schneidet die Sprechblase nicht an.
- [ ] Modellwechsel Small ↔ Base auf dem Gerät (Download, Umschalten, neue Transkription, alter Eintrag behält sein Modell).
- [ ] Einstellungen bei 200 % Systemschrift und ca. 320 dp Breite prüfen → Aufbewahrungszeilen bleiben lesbar und brechen nicht mitten im Wort um.
- [ ] App-Sprache/Theme umschalten und App neu starten → Einstellungen bleiben erhalten.
- [ ] Flugmodus: Transkription funktioniert weiterhin (nur der Modelldownload braucht Netz).

## 7. Release-Build prüfen

- [ ] `unzip -l app-release.apk | grep lib/` zeigt **nur** `arm64-v8a`.
- [ ] `apksigner verify --print-certs` bestätigt die eigene Signatur (nicht den Debug-Key).
- [ ] Keine Debug-Marker: `aapt2 dump badging` zeigt `application-debuggable` nicht.
- [ ] Backup-Regeln greifen: Einstellungen → Apps → Transcriber → „Daten sichern" deaktiviert bzw. ein Backup enthält weder Datenbank noch Audio.
