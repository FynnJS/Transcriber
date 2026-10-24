# Transcriber – Implementationsplan

Android-App zur Transkription von WhatsApp-Sprachnachrichten · Stand: 23.10.2026 · Autor: Fynn

## 1. Überblick und getroffene Entscheidungen

| Thema | Entscheidung |
| --- | --- |
| Zweck | Sprachnachrichten per Teilen-Funktion aus WhatsApp transkribieren, inkognito abspielen, Verlauf anzeigen |
| Transkription | On-device mit whisper.cpp, keine Cloud |
| Modell | *small* (quantisiert) als Standard, *base* als wählbare Schnell-Option, multilingual mit Auto-Spracherkennung (umstellbar auf Deutsch), Download beim ersten Start |
| Eingang | Teilen-Ziel mit schlanker Overlay-Activity (Bottom-Sheet-Optik), Foreground-Service als Fallback bei längerer Verarbeitung |
| Umfang | Genau eine Sprachnachricht pro Teilen-Vorgang |
| Inkognito | WhatsApp erfährt von der Wiedergabe nichts, da die Datei im eigenen Player läuft (kein blauer Mikrofon-Haken beim Absender) |
| Speicherung | Audio und Transkript, getrennte Aufbewahrungsfristen in den Einstellungen |
| Verlauf | Datum, Dauer, Vorschau, Label (schon in Version 1), Volltextsuche, Abspielen, Kopieren, Teilen, Löschen |
| Tech-Stack | Kotlin, Jetpack Compose, Room, Hilt, DataStore, Media3, WorkManager, whisper.cpp über JNI |
| Design | Clean und modern, Dark Mode als Standard, umstellbar auf Light Mode, Logo aus `Logo.png` |
| Android-Version | minSdk 26 (Android 8), Ziel-ABI `arm64-v8a` |
| Repository | Privates GitHub-Repo `Transcriber` mit den Branches `main` und `develop` |

Grundprinzip für die Reihenfolge: zuerst ein dünner, durchgehender Ablauf (Teilen → Transkript sichtbar), danach Verlauf, Einstellungen und Feinschliff.

## 2. Schritt 0: Repository und Git-Setup

Aktuelle Projektstruktur: `docs/Design_System.md`, `Logo.png`, `.gitignore`, `AGENTS.md`, `README.md`. Git ist noch nicht initialisiert.

1. `.gitignore` prüfen, bevor etwas committet wird. Mindestens enthalten sein sollten: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.apk`, `*.aab`, `*.jks`, `*.keystore`, `*.bin` (Whisper-Modelle gehören nie ins Repo), `.cxx/`, `.externalNativeBuild/`.
2. Lokal initialisieren und ersten Commit anlegen:

```
git init -b main
git add .
git commit -m "chore: initial project structure"
git checkout -b develop
```

3. Privates Repo auf GitHub anlegen und beide Branches pushen. Mit GitHub CLI (nach `gh auth login`):

```
gh repo create Transcriber --private --source=. --remote=origin
git push -u origin main
git push -u origin develop
```

Alternativ das Repo im Browser anlegen (privat, ohne README) und danach `git remote add origin git@github.com:FynnJS/Transcriber.git` verwenden.

4. Auf GitHub unter Settings → Branches: `develop` als Standard-Branch setzen, `main` schützen (nur per Pull Request).
5. Workflow festlegen: Feature-Branches `feature/<name>` von `develop`, Merge per Pull Request nach `develop`, Releases per Pull Request von `develop` nach `main` mit Tag (`v1.0.0`). Commit-Nachrichten nach Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`).
6. `README.md` und `AGENTS.md` aktualisieren: Projektziel, Tech-Stack, Architektur, Branch-Workflow und Commit-Regeln, Verweis auf `docs/Design_System.md`.

**Fertig, wenn:** Repo ist privat auf GitHub, `main` und `develop` existieren, `develop` ist Standard-Branch.

## 3. Schritt 1: Projekt-Grundgerüst

1. Neues Android-Studio-Projekt (Empty Activity mit Compose) direkt im bestehenden Projektordner anlegen. Vorschlag für Package und Application-ID: `dev.fynnjs.transcriber`.
2. Gradle mit Version Catalog (`libs.versions.toml`) einrichten. Abhängigkeiten: Compose (BOM), Navigation Compose, Hilt, Room (mit KSP), DataStore, Media3, WorkManager, Kotlin Coroutines.
3. `minSdk 26`, `targetSdk` auf die aktuelle Version, `abiFilters` auf `arm64-v8a` beschränken.
4. Paketstruktur: `data/` (Room, DataStore, Repositories), `domain/` (Modelle, Use Cases), `audio/` (Decoder, Player), `whisper/` (JNI-Wrapper), `service/`, `ui/` (Theme, Components, Screens), `di/`.
5. Launcher-Icon aus `Logo.png` erzeugen (Image Asset Studio). Für ein Adaptive Icon: Hintergrund in der Logo-Grünfarbe, Vordergrund mit Mikrofon und Sprechblase freigestellt.
6. App-Name "Transcriber" und Sprachressourcen (Deutsch als Standard, Strings konsequent in `strings.xml`) anlegen.

**Fertig, wenn:** Leere App startet auf einem Gerät, Hilt ist eingebunden, Icon ist sichtbar.

## 4. Schritt 2: Design-System und Theme

1. `docs/Design_System.md` ist die Quelle der Wahrheit für Farben, Typografie, Abstände und Formen. Das Logo-Grün ist die Primärfarbe.
2. Material-3-Theme mit festen Farbschemata (keine Dynamic Colors, damit das Branding konsistent bleibt): **Dunkel als Standard**, **Hell** als Alternative.
3. Theme-Auswahl über DataStore (Dunkel / Hell), beim Start synchron lesen, damit kein Aufblitzen im falschen Theme entsteht.
4. Wiederverwendbare Komponenten: TopBar, Verlaufskarte, Status-Chip, Label-Chip, Primär- und Sekundärbutton, Bottom-Sheet-Container, Leerzustand, Fehlerzustand.
5. Compose-Previews für jede Komponente in beiden Themes.

**Fertig, wenn:** Komponenten-Galerie (Previews) sieht in Dunkel und Hell sauber aus.

## 5. Schritt 3: Datenschicht

1. Room-Entity `HistoryEntry`: `id`, `createdAt`, `sourceFileName`, `durationMs`, `audioPath` (nullable), `audioHash`, `transcript` (nullable), `language`, `modelUsed`, `status` (`QUEUED`, `TRANSCRIBING`, `DONE`, `FAILED`), `errorMessage` (nullable), `label` (nullable).
2. Volltextsuche über eine FTS-Tabelle (FTS4) auf `transcript` und `label`.
3. DAO mit Flows: alle Einträge (nach Datum absteigend), Suche, Eintrag nach ID, Status-Updates, Löschen.
4. `HistoryRepository` als einzige Schnittstelle für die UI und den Service.
5. DataStore für Einstellungen: Theme, Modell (*small*/*base*), Sprache (Auto/Deutsch), Aufbewahrung Audio, Aufbewahrung Transkript.
6. Room-Schema exportieren (`exportSchema = true`), damit spätere Migrationen testbar sind.

**Fertig, wenn:** Unit-Tests für DAO und Repository laufen, FTS-Suche liefert Treffer.

## 6. Schritt 4: Teilen-Eingang und Overlay

1. `ShareReceiverActivity` mit Intent-Filter `ACTION_SEND`, `mimeType="audio/*"`. Theme mit transparentem Hintergrund, Darstellung als Bottom-Sheet.
2. Beim Empfang sofort den Stream in den app-privaten Speicher kopieren (`filesDir/audio/<uuid>.opus`), weil die Zugriffsrechte des Content-URIs nur kurz gelten. Das Kopieren läuft in einer Coroutine, die Activity wartet darauf.
3. SHA-256 der Datei berechnen. Existiert bereits ein Eintrag mit demselben Hash, wird dieser angezeigt, statt erneut zu transkribieren.
4. Neuen `HistoryEntry` mit Status `QUEUED` anlegen und den Transkriptions-Service starten.
5. Overlay-UI beobachtet den Eintrag per Flow: Fortschritt, danach Transkript, Abspielen, Label-Feld, Schließen.
6. Fehlerfälle: kein Audio, Datei nicht lesbar, zu wenig Speicher, Modell fehlt (dann Hinweis und Weiterleitung zum Modell-Download).

**Fertig, wenn:** Teilen aus WhatsApp öffnet das Overlay, die Datei liegt im app-privaten Speicher, ein Eintrag existiert.

## 7. Schritt 5: Audio-Dekodierung

1. `AudioDecoder`: `.opus` (OGG-Container) mit `MediaExtractor` und `MediaCodec` dekodieren.
2. Auf 16 kHz, Mono, Float-PCM umwandeln (Resampling und Downmix), weil Whisper genau dieses Format erwartet.
3. Dauer aus den Metadaten bestimmen und im Eintrag speichern.
4. Unit- und Instrumented-Tests mit echten Beispiel-Sprachnachrichten (kurz, lang, leise, mit Rauschen).
5. Fehlerfall sauber behandeln: nicht dekodierbare Datei führt zu Status `FAILED` mit Meldung.

**Fertig, wenn:** Eine WhatsApp-Sprachnachricht wird zuverlässig zu PCM-Daten.

## 8. Schritt 6: whisper.cpp einbinden

1. whisper.cpp als Git-Submodule unter `third_party/whisper.cpp` einbinden und auf einen festen Release-Tag pinnen (`git submodule add https://github.com/ggml-org/whisper.cpp third_party/whisper.cpp`).
2. Als Vorlage dient das Beispiel `examples/whisper.android` aus dem whisper.cpp-Repository: CMake-Konfiguration in `app/src/main/cpp`, JNI-Brücke und Kotlin-Wrapper `WhisperContext`.
3. Build für `arm64-v8a` mit Release-Optimierungen (`-O3`, NEON). Debug-Builds sind bei Whisper stark verlangsamt, daher auch zum Testen ein Release-nahes Build-Profil nutzen.
4. Wrapper-API: Modell laden, PCM transkribieren (Sprache `auto` oder `de`), Fortschritts-Callback, Abbruch, Kontext freigeben.
5. Threads begrenzen (z. B. auf die Anzahl der Performance-Kerne, maximal 4), um das Gerät nicht zu überhitzen.
6. Messwerte festhalten: Dauer für 30 Sekunden und 2 Minuten Audio mit *base* und *small* auf mindestens einem Mittelklasse-Gerät.

**Fertig, wenn:** Ein Test-PCM wird auf einem echten Gerät korrekt transkribiert.

## 9. Schritt 7: Modell-Verwaltung

1. `ModelRepository`: Zustand pro Modell (nicht geladen, lädt, bereit, Fehler), Ablage unter `filesDir/models/`.
2. Download der quantisierten ggml-Modelle (*small* und *base*) über WorkManager: fortsetzbar, mit Fortschritt. Vor dem Start prüft der Worker den freien Speicher und meldet fehlenden Platz als eigenen Fehler; gleichzeitig gestartete Downloads legen den Modellordner kollisionsfrei an. Download-URL und SHA-256-Prüfsumme stammen aus der Modell-Dokumentation des whisper.cpp-Repositorys und werden nach dem Download verifiziert.
3. Einführungsbildschirm beim ersten Start: Erklärung, Größe, Hinweis auf WLAN, Download starten. Die App funktioniert erst nach erfolgreichem Download des Standardmodells.
4. In den Einstellungen: Modell wechseln, weiteres Modell laden, Modell löschen.
5. Die `INTERNET`-Berechtigung wird nur für diesen Download genutzt (Transkription bleibt komplett offline).

**Fertig, wenn:** Erster Start lädt das Modell, Prüfsumme stimmt, Modellwechsel funktioniert.

## 10. Schritt 8: Transkriptions-Pipeline und Service

1. `TranscriptionService` als Foreground-Service mit Benachrichtigungskanal. Den passenden `foregroundServiceType` für das gewählte `targetSdk` festlegen: ab Android 15 mediaProcessing, darunter dataSync (der Typ wird beim Start des Service per SDK-Version gewählt), und samt passender Berechtigung im Manifest deklarieren.
2. `POST_NOTIFICATIONS` (Android 13 und höher) beim ersten Start anfragen und die Verweigerung sauber behandeln (Service läuft trotzdem, nur ohne sichtbare Benachrichtigung bei neueren Versionen: Hinweis im UI).
3. Eine Warteschlange, die Einträge nacheinander abarbeitet: `QUEUED` → `TRANSCRIBING` → `DONE` oder `FAILED`.
4. Fortschritt über den Whisper-Callback in den Eintrag und die Benachrichtigung schreiben. Längere Nachrichten laufen in überlappenden 10-Sekunden-Fenstern; der erkannte Text erscheint als Live-Vorschau im Eintrag, während der fertige Text aus denselben Fenstern zusammengesetzt wird.
5. Robustheit: Beim App-Start Einträge, die in `TRANSCRIBING` hängen geblieben sind (Prozess beendet), zurück auf `QUEUED` setzen und neu starten.
6. Leeres oder stilles Ergebnis als eigenen Zustand behandeln ("Kein Text erkannt").

**Fertig, wenn:** Teilen → Transkript erscheint im Overlay, auch wenn man währenddessen zu WhatsApp wechselt.

## 11. Schritt 9: Inkognito-Player

1. Player-Komponente auf Basis von Media3 (ExoPlayer), Quelle ist ausschließlich die Datei im app-privaten Speicher.
2. Bedienung: Play/Pause, Seek-Leiste, Position und Dauer, Tempo 1x / 1,5x / 2x.
3. Wiederverwendbar im Overlay und im Detailbildschirm. Nur ein Player gleichzeitig aktiv, Audiofokus über Media3.
4. Ist das Audio bereits gelöscht (Aufbewahrungsfrist), wird der Abspiel-Button ausgeblendet und stattdessen ein Hinweis gezeigt.
5. Der Datenschutzvorteil dokumentieren: Die App sendet nichts an WhatsApp, Absender sehen keine Wiedergabebestätigung.

**Fertig, wenn:** Nachricht lässt sich abspielen, vor- und zurückspulen und beschleunigen.

## 12. Schritt 10: Verlauf, Detailansicht und Label

1. `HistoryScreen`: Liste gruppiert nach Tag, Suchfeld (FTS über Transkript und Label), Leerzustand.
2. Verlaufskarte: Datum und Uhrzeit, Dauer, Label-Chip, Transkript-Vorschau (zwei bis drei Zeilen), Statuschip.
3. `DetailScreen`: vollständiges Transkript (markierbar), Player, Label bearbeiten, Transkript kopieren, Transkript als Text teilen, Eintrag löschen, bei `FAILED` erneut versuchen.
4. Label: frei editierbar, im Overlay direkt nach dem Teilen und im Detailbildschirm; zuletzt verwendete Labels als Vorschläge.
5. Löschen per Wischgeste mit Rückgängig-Snackbar, beim endgültigen Löschen auch die Audiodatei entfernen.

**Fertig, wenn:** Verlauf lässt sich durchsuchen, Label setzen, abspielen und Einträge löschen.

## 13. Schritt 11: Einstellungen und Aufbewahrung

1. Einstellungsbildschirm: Theme (Dunkel / Hell), Modell, Sprache (Auto / Deutsch), Aufbewahrung Audio (z. B. sofort nach Transkription / 7 / 30 / 90 Tage / nie, Standard 30 Tage), Aufbewahrung Transkript (7 / 30 / 90 Tage / nie, Standard nie), App-Sperre (Biometrie), Bildschirmfoto-Schutz, "Alles löschen".
2. `CleanupWorker` als täglicher periodischer WorkManager-Job und zusätzlich beim App-Start: löscht abgelaufene Audiodateien (Eintrag bleibt, `audioPath` wird leer) und abgelaufene Transkripte bzw. Einträge.
3. Beim Ändern der Frist wird die neue Regel sofort einmal angewendet.

**Fertig, wenn:** Fristen greifen nachweislich (Test mit künstlich zurückgesetzten Zeitstempeln).

## 14. Schritt 12: Datenschutz und Härtung

1. `allowBackup` ausschalten bzw. Datenbank und Audioordner in den Backup- und Datenübertragungsregeln ausschließen.
2. Keine Analyse- oder Tracking-Bibliotheken, keine Netzwerkzugriffe außer dem Modell-Download.
3. Berechtigungen minimal halten (`INTERNET`, `POST_NOTIFICATIONS`, Foreground-Service-Berechtigung).
4. Entscheidung: App-Sperre per Biometrie und Bildschirmfoto-Schutz (`FLAG_SECURE`) für den Verlauf. Umsetzung: `FLAG_SECURE` und Biometrie-Sperre, jeweils als Schalter in den Einstellungen.
5. Datenschutzerklärung schreiben (nötig für eine Veröffentlichung).

## 15. Schritt 13: Fehlerbehandlung und Randfälle

- Doppeltes Teilen derselben Nachricht: vorhandenen Eintrag zeigen (Hash-Vergleich).
- Sehr lange Nachrichten: Verarbeitung in Whisper-Fenstern, Speicherverbrauch des PCM-Puffers beachten.
- Wenig Speicherplatz: vor dem Kopieren prüfen und verständliche Meldung zeigen.
- Modell fehlt oder ist beschädigt: Weiterleitung zum Download bzw. erneuter Download.
- Prozess wird beendet: Wiederaufnahme der Warteschlange (siehe Schritt 8).
- Falscher Dateityp beim Teilen: freundliche Fehlermeldung statt Absturz.
- Rotation und Prozess-Tod in Overlay und Detailansicht (ViewModel und `SavedStateHandle`).

## 16. Schritt 14: Tests und Qualitätssicherung

1. Unit-Tests: Repository, Cleanup-Logik, Hash-Duplikat-Erkennung, Dekoder-Hilfsfunktionen.
2. Instrumented-Tests: Room mit FTS, Share-Intent mit Beispieldatei.
3. Manuelle Testmatrix: echtes Teilen aus WhatsApp und WhatsApp Business, Android 8, 13 und aktuelle Version, Dunkel und Hell, Offline, Benachrichtigungen verweigert, App im Hintergrund während der Transkription, wenig Speicher.
4. Performance-Messung (siehe Schritt 6) und Akku-/Wärmebeobachtung bei mehreren Nachrichten hintereinander.

## 17. Schritt 15: Release

1. Release-Build mit R8; Keep-Regeln für die JNI-Klassen nicht vergessen.
2. Signierung mit eigenem Keystore, der niemals ins Repository kommt (nur lokal oder in sicheren Secrets).
3. Versionierung (`versionName` / `versionCode`), Pull Request von `develop` nach `main`, Tag `v1.0.0`.
4. Verteilung zunächst als Test-APK oder interner Test, danach optional Play Store (Store-Eintrag, Screenshots in Dunkel und Hell, Datenschutzerklärung).

## 18. Geklärte Punkte

- App-Sperre und Bildschirmfoto-Schutz (Schritt 12): beides als Schalter in den Einstellungen.
- Application-ID (Schritt 1): `dev.fynnjs.transcriber`.
- Duplikat-Erkennung per Hash (Schritt 4): wie geplant.
- Foreground-Service-Typ (Schritt 8): `mediaProcessing` ab Android 15, darunter `dataSync`.
