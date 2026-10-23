# AGENTS.md

## Sprache

- UI-Texte, Kommentare in `docs/` und Nutzerkommunikation: **Deutsch**.
- Code-Bezeichner, Klassennamen und Commit-Nachrichten: **Englisch**.
- Alle sichtbaren Strings liegen in `app/src/main/res/values/strings.xml` – keine
  hartkodierten UI-Texte in Composables.

## Dokumentation

- `README.md`, `AGENTS.md` und alle Dateien in `docs/` werden bei jeder Änderung im selben
  Commit mitgepflegt. Der Agent hält sie immer auf dem neuesten Stand.
- Ändern sich Verhalten, Befehle, Architektur, Konventionen oder Abhängigkeiten, gehört die
  passende Doku-Aktualisierung zur selben Änderung – nicht in einen späteren Commit.
- Die Doku beschreibt den aktuellen Stand von `develop`. Veraltete Angaben werden korrigiert
  statt durch neue Zusätze überdeckt.
- Eine Änderung ohne aktualisierte Doku gilt als unfertig.

## Commits

- Conventional Commits, Betreff im Imperativ und auf Englisch, z. B.
  `feat(settings): Add audio retention selection`.
- Nur als **FynnJS** committen. Keine Co-Author-Zeilen, keine
  „Generated with …“-Fußzeilen, keine weiteren Autoren.
- Mehrzeilige Bodies als Stichpunkte (`- …`).
- Feature-Branches `feature/<name>` von `develop`; `main` nur per Pull Request.
- Kein `git add -A`; nur die eigenen Dateien stagen.

## Architekturregeln

- Schichten: `ui → domain ← data`, `service` nutzt `domain`/`audio`/`whisper`.
  Die UI spricht ausschließlich mit Repositories/ViewModels, nie direkt mit Room.
- `HistoryRepository` ist die einzige Schnittstelle für Verlaufseinträge.
- Hilt für DI, Konstruktor-Injection, keine Service-Locator.
- Coroutines/Flow; kein blockierendes I/O auf dem Main-Thread (Ausnahme: einmaliges
  synchrones Lesen der Theme-Einstellung beim Start).
- Room-Schemas werden exportiert (`app/schemas`) und bei Änderungen als Migration gepflegt.
- Design-Tokens nur aus `ui/theme/` beziehen; keine Dynamic Colors.
- Keine neuen Netzwerkzugriffe außer dem Modelldownload. Keine Analytics/Crash-SDKs.

## Build & Tests

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

`JAVA_HOME` muss auf ein JDK ≥ 17 zeigen (Android Studio JBR funktioniert).
`gradle/gradle-daemon-jvm.properties` ist lokale Konfiguration, ist in `.gitignore` und wird
nicht committet; der Daemon nutzt die lokal installierte JDK.

NDK 28.2 und CMake 3.22 sind über die SDK-Installation vorhanden. whisper.cpp liegt als
Submodule unter `third_party/whisper.cpp` (gepinnt); `*.bin`-Modelle sind nie eingecheckt.

## Sicherheit

- Keystores, `local.properties` und Modell-Dateien niemals committen.
- Audio/Transkripte bleiben im app-privaten Speicher, keine Logs mit Transkriptinhalten.
- `FLAG_SECURE` und App-Sperre respektieren die Nutzereinstellung.
