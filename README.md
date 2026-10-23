# Transcriber

Android-App, die WhatsApp-Sprachnachrichten über die Teilen-Funktion entgegennimmt und
**vollständig offline** auf dem Gerät transkribiert (whisper.cpp). Keine Cloud, kein
Tracking, kein Netzwerk außer dem einmaligen Modelldownload.

## Funktionen

- Teilen-Ziel für `audio/*`: schlankes Bottom-Sheet-Overlay, eine Nachricht pro Vorgang
- Transkription on-device mit whisper.cpp (Modelle *small* als Standard, *base* als
  Schnell-Option; Spracherkennung automatisch oder Deutsch)
- Inkognito-Player für die lokale Audiodatei (WhatsApp erhält keine Wiedergabe-Info)
- Verlauf mit Volltextsuche (FTS), Labels, Kopieren, Teilen, Löschen
- Aufbewahrungsfristen für Audio und Transkript, tägliche Bereinigung
- Dunkles Theme als Standard, helles Theme optional
- Optionale App-Sperre (Biometrie; Ein- und Ausschalten bestätigen, nach jeder Rückkehr aus
  dem Hintergrund erneut entsperren) und Bildschirmfoto-Schutz

## Tech-Stack

Kotlin · Jetpack Compose (Material 3) · Room (FTS4) · Hilt · DataStore · Media3
(ExoPlayer) · WorkManager · whisper.cpp über JNI · minSdk 26, targetSdk 37,
Release-ABI `arm64-v8a` (Debug zusätzlich `x86_64` für Emulator-Tests).

## Architektur

```
app/src/main/java/dev/fynnjs/transcriber/
├── data/         Room, DataStore, Repositories, Download-Worker, Cleanup-Worker
├── domain/       Modelle, Repository-Schnittstellen, Use Cases
├── audio/        Opus→PCM-Decoder, Resampling, Player-Controller
├── whisper/      Kotlin-Wrapper um die JNI-Brücke
├── service/      Foreground-Service und Transkriptions-Warteschlange
├── ui/           Theme, Komponenten, Screens (Verlauf, Detail, Einstellungen, Overlay)
└── di/           Hilt-Module
app/src/main/cpp/ CMake + JNI-Brücke zu third_party/whisper.cpp
```

Ablauf: `ShareReceiverActivity` kopiert den Stream in den app-privaten Speicher, berechnet
den SHA-256 (Duplikaterkennung), legt einen `HistoryEntry` an und startet den
`TranscriptionService`. Der Service dekodiert nach 16 kHz mono Float-PCM und übergibt es an
whisper.cpp. Fortschritt und Ergebnis landen in Room, das Overlay beobachtet den Eintrag.

Details zu Farben, Typografie und Komponenten: [docs/Design_System.md](docs/Design_System.md).

## Build

Voraussetzungen: JDK 17+, Android SDK 37, NDK 28.2, CMake 3.22. Gradle 9.8.0 kommt
über den Wrapper, muss also nicht separat installiert werden.

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug          # Debug (arm64-v8a + x86_64)
./gradlew :app:assembleRelease        # Release (arm64-v8a, R8)
./gradlew test                        # Unit-Tests
./gradlew connectedDebugAndroidTest   # Instrumented-Tests (Gerät/Emulator)
```

`local.properties` mit `sdk.dir` wird nicht eingecheckt. Auch
`gradle/gradle-daemon-jvm.properties` bleibt lokal (ignoriert); der Gradle-Daemon nutzt die
installierte JDK ≥ 17. Der Release-Keystore liegt ausschließlich lokal bzw. in sicheren
CI-Secrets.

## Modelle

Die quantisierten ggml-Modelle werden beim ersten Start aus dem offiziellen
whisper.cpp-Repository (Hugging Face) geladen und per SHA-256 geprüft:

| Modell | Datei | Größe | SHA-256 |
| --- | --- | --- | --- |
| base | `ggml-base-q5_1.bin` | 59.707.625 B | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |
| small | `ggml-small-q5_1.bin` | 190.085.487 B | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |

Modelle gehören nie ins Repository (`*.bin` ist ignoriert).

## Branch-Workflow

- `develop` ist der Standard-Branch, `main` ist geschützt (nur Pull Requests).
- Feature-Branches `feature/<name>` von `develop`, Merge per PR nach `develop`.
- Release: PR von `develop` nach `main` plus Tag `vX.Y.Z`.
- Commit-Nachrichten: Conventional Commits, **auf Englisch**, Stichpunkte im Body,
  z. B. `feat(settings): Add audio retention selection`.
- Commits nur nach ausdrücklicher Bestätigung von FynnJS oder wenn Tests die Änderung und
  ihre Funktionalität vollständig bestätigen.
- Nur als FynnJS, ohne Co-Author-Zeilen oder „Generated with …“-Fußzeilen.
- Änderungen sinnvoll gesplittet committen: ein Commit pro eigenständiger Änderung.

## Dokumentation

`README.md`, `AGENTS.md` und alle Dateien in `docs/` beschreiben immer den aktuellen Stand
von `develop` und werden bei jeder Änderung im selben Commit mitgepflegt. Die
Doku-Pflege ist Pflicht, nicht optional.

## Datenschutz

Alle Audiodaten und Transkripte bleiben im app-privaten Speicher. `INTERNET` wird nur für
den Modelldownload verwendet. Keine Analyse-, Werbe- oder Crash-SDKs. `allowBackup` ist
deaktiviert, Datenbank und Audioordner sind von Backup und Geräteübertragung ausgeschlossen.

Die optionale App-Sperre schützt ausschließlich den Verlauf samt Detailansicht und
Einstellungen. Das Teilen-Overlay (`ShareReceiverActivity`) bleibt bewusst ungeschützt,
damit eine geteilte Nachricht ohne zusätzliche Hürde transkribiert werden kann.
