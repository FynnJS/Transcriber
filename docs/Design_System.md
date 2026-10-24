# Transcriber – Design-System

Quelle der Wahrheit für Farben, Typografie, Abstände, Formen und Komponenten.
Umgesetzt in `ui/theme/` (Compose Material 3). Es gibt **keine Dynamic Colors**, damit das
Branding auf jedem Gerät gleich aussieht. **Dark Mode ist der Standard**, Light Mode ist
eine bewusste Alternative.

## Marke

| Rolle | Wert |
| --- | --- |
| Logo-Grün (Icon-Hintergrund) | `#16A64B` |
| Akzentgrün hell | `#1CB55A` |
| Akzentgrün dunkel | `#0E7A36` |
| Wortmarke | „Transcriber“, Satzschrift, Halbfett |
| Icon | Mikrofon + Sprechblase, weiß freigestellt auf Grün (`docs/Logo.png`) |

Das Logo ist einfarbig grün. Die Akzenttöne werden nur für Verläufe in UI-Elementen wie
der Waveform verwendet, nicht als Flächenfarbe in der App.

Das Onboarding zeigt das vollständige Logo als 104-dp-Kachel mit `extraLarge`-Radius
(32 dp) über dem Titel; die Kachel ersetzt dort die frühere Icon-Illustration.

## Farbschemata

### Dunkel (Standard)

| Rolle | Hex |
| --- | --- |
| primary / onPrimary | `#52E08D` / `#00391B` |
| primaryContainer / onPrimaryContainer | `#00522A` / `#A6F5C4` |
| secondary / onSecondary | `#B4CCBB` / `#1F3527` |
| secondaryContainer / onSecondaryContainer | `#354B3B` / `#CFE9D7` |
| tertiary / onTertiary | `#A3CDDB` / `#06323C` |
| background / onBackground | `#0F1511` / `#DFE5DE` |
| surface / onSurface | `#0F1511` / `#DFE5DE` |
| surfaceVariant / onSurfaceVariant | `#414942` / `#C0C9C1` |
| surfaceContainerLowest / Low / Base / High / Highest | `#0A100C` / `#171D19` / `#1B221D` / `#252C27` / `#303731` |
| outline / outlineVariant | `#8A938B` / `#414942` |
| error / errorContainer | `#FFB4AB` / `#93000A` |

### Hell

| Rolle | Hex |
| --- | --- |
| primary / onPrimary | `#0E8A3D` / `#FFFFFF` |
| primaryContainer / onPrimaryContainer | `#C6F2D8` / `#00210F` |
| secondary / onSecondary | `#4C6353` / `#FFFFFF` |
| secondaryContainer / onSecondaryContainer | `#D9E8DC` / `#0A1F12` |
| tertiary / onTertiary | `#3B6470` / `#FFFFFF` |
| background / onBackground | `#F7FBF8` / `#171D19` |
| surface / onSurface | `#F7FBF8` / `#171D19` |
| surfaceVariant / onSurfaceVariant | `#DCE5DE` / `#414942` |
| surfaceContainerLowest / Low / Base / High / Highest | `#FFFFFF` / `#F1F7F3` / `#EBF1EC` / `#E6ECE7` / `#E0E6E1` |
| outline / outlineVariant | `#717971` / `#C0C9C1` |
| error / errorContainer | `#BA1A1A` / `#FFDAD6` |

Statusfarben (zusätzlich, kein Material-Rollenpaar):

| Status | Dunkel | Hell |
| --- | --- | --- |
| Erfolg / Fertig | `#52E08D` | `#0E8A3D` |
| In Arbeit | tertiary `#A3CDDB` | tertiary `#3B6470` |
| Fehler | error | error |
| Warteschlange | onSurfaceVariant | onSurfaceVariant |

## Typografie

Systemschrift (Roboto / SansSerif), keine zusätzlichen Font-Dateien.

| Stil | Größe / Zeilenhöhe | Gewicht | Einsatz |
| --- | --- | --- | --- |
| displaySmall | 36 / 44 | SemiBold | Onboarding-Titel |
| headlineMedium | 28 / 36 | SemiBold | Bildschirmtitel |
| headlineSmall | 24 / 32 | SemiBold | Sheet-Titel |
| titleLarge | 22 / 28 | SemiBold | TopBar, Detailtitel |
| titleMedium | 16 / 24 | Medium | Karten-Label, Listentitel |
| bodyLarge | 16 / 24 | Regular | Transkript, Fließtext |
| bodyMedium | 14 / 20 | Regular | Beschreibungen |
| bodySmall | 12 / 16 | Regular | Metazeile (Datum, Dauer) |
| labelLarge | 14 / 20 | Medium | Buttons |
| labelMedium | 12 / 16 | Medium | Chips |
| labelSmall | 11 / 16 | Medium | Status-Chips |

Transkripte nutzen `bodyLarge` mit `lineHeight 24sp`; Vorschauen werden auf zwei bis drei
Zeilen gekürzt. Zahlen in Dauerangaben stehen in `labelMedium` mit tabellarischen Ziffern.

Überschriften laufen über `HeadingText`: Passt das längste Wort nicht in die verfügbare
Breite (sehr schmale Displays, große Systemschrift), sinkt die Schriftgröße in 1-sp-Schritten
bis auf 10 sp, statt das Wort mitten im Wort umzubrechen.

## Abstände

4-dp-Raster:

| Token | Wert | Einsatz |
| --- | --- | --- |
| xs | 4 dp | Icon zu Text in Chips |
| s | 8 dp | Chip-Innenabstand, enge Gruppen |
| m | 12 dp | Karten-Innenabstand vertikal |
| base | 16 dp | Standardabstand, Bildschirmrand |
| l | 24 dp | Abschnittsabstand |
| xl | 32 dp | Hero-/Leerzustand-Abstand |

Bildschirmränder: 16 dp horizontal, 12 dp zwischen Karten. TopBar-Inhalte 16 dp.
Visuelles Grundmaß: 8 dp für Touch-Ziele, Mindestgröße 48 dp.

## Formen

| Token | Radius | Einsatz |
| --- | --- | --- |
| extraSmall | 6 dp | Kleine Badges |
| small | 10 dp | Chips, Suchfeld |
| medium | 16 dp | Verlaufskarten, Textfelder |
| large | 24 dp | Bottom-Sheet, Dialoge |
| extraLarge | 32 dp | Onboarding-Karte |

## Komponenten

- **TopBar**: `CenterAlignedTopAppBar` bzw. `TopAppBar`, Fläche = `surface`,
  Titel `titleLarge`, Aktionen als 48-dp-Icon-Buttons.
- **Verlaufskarte**: `surfaceContainerLow`, 16-dp-Radius, 16-dp-Innenabstand,
  Zeile 1 Datum/Uhrzeit + Dauer, Zeile 2 Label-Chip + Status-Chip, darunter Vorschau
  (`maxLines = 3`, `TextOverflow.Ellipsis`). Wischgeste nach links zum Löschen.
- **Status-Chip**: pillenförmig, `labelSmall`, Punkt + Text; Farben wie Statusfarben,
  Hintergrund = Statusfarbe mit 16 % Alpha.
- **Label-Chip**: `secondaryContainer`, `labelMedium`, führendes Tag-Icon; editierbar.
  Lange Labels werden gekürzt, das Entfernen-Icon bleibt dabei sichtbar.
- **Detail-Aktionen**: Label-Chip und Label-Aktionen stehen in einer `FlowRow`; reicht die
  Breite nicht, rücken die Aktionen in die nächste Zeile, statt aus dem Bild zu scrollen.
- **Modellauswahl (Detail)**: Ein sekundärer Dropdown-Button mit Chevron listet beide Modelle
  mit Größe und Zustand (Bereit, Nicht geladen, Wird geladen … Prozent, Fehler), wie in den
  Einstellungen. Die Auswahl gilt nur für diesen Eintrag. Ein nicht geladenes Modell wird
  sofort heruntergeladen; unter der Auswahl erscheinen Fortschrittsbalken und Prozentwert,
  und sobald das Modell bereit ist, startet die Transkription automatisch. Nach einem
  fehlgeschlagenen Download bietet die Auswahl „Laden“ erneut an. Die Auswahl steht unter
  der Fehlerkarte des Eintrags, also dort, wo eine Transkription erneut angestoßen wird.
- **Detail-Transkript**: Der Bereich zeigt das Transkript, sobald eines vorliegt. Der leere
  Zustand („Kein Text erkannt“) gehört ausschließlich zu `DONE`; solange ein Eintrag
  `QUEUED` oder `TRANSCRIBING` ist, steht dort ein Hinweis auf das ausstehende Transkript,
  und bei `FAILED` ohne Text erklärt allein der Fehlerzustand den Eintrag. Ein erneuter
  Versuch zeigt damit nie „Kein Text erkannt“, während die Transkription noch läuft.
  Während `TRANSCRIBING` wächst das Transkript als Live-Vorschau mit; ein Hinweis darunter
  („Live-Vorschau – der Text wird noch vervollständigt.“) grenzt ihn vom fertigen Text ab.
  Eine ältere Fassung bleibt sichtbar, bis das erste Fenster erkannt ist, danach gewinnt
  die Vorschau. Kopieren und Teilen beziehen sich weiterhin nur auf den fertigen Text.
- **Buttons**: primär gefüllt (`primary`), sekundär `outlined` mit `outline`,
  Textbutton für tertiäre Aktionen. Mindesthöhe 48 dp, Radius `full`; bei großen
  Schriftgrößen wachsen sie mit dem Text, statt ihn abzuschneiden.
- **Bottom-Sheet-Container**: `surfaceContainerLow`, obere Radien 24 dp, Griff 32 × 4 dp,
  Innenabstand 16 dp, `navigationBarsPadding`.
- **Teilen-Overlay**: Label-Chip in eigener Zeile, darunter Kopieren/Teilen und
  „In der App öffnen“ als `FlowRow` (rechtsbündig). Auf schmalen Displays bricht die
  Aktionszeile um, statt den Chip zu quetschen; „Schließen“ bleibt voll breit. Passt der
  Inhalt nicht auf den Bildschirm (kleine Displays, große Schrift), scrollt der Inhalt
  unter dem Griff; der Griff bleibt oben stehen. Das Transkript-Feld erscheint schon
  während der Transkription, sobald die Live-Vorschau Text enthält; Fortschritt und
  Live-Hinweis stehen darüber bzw. darunter.
- **Leerzustand**: zentriert, Icon in `primaryContainer`-Kreis (64 dp), Titel `titleMedium`,
  Text `bodyMedium` in `onSurfaceVariant`, optional Primärbutton.
- **Fehlerzustand**: `errorContainer`-Karte mit Icon, Titel, Meldung und Aktion
  (Erneut versuchen / Zum Download). „Erneut versuchen“ meldet nur dann „Transkription neu
  gestartet“, wenn das Modell bereit ist; fehlt oder lädt es noch, erklärt die Rückmeldung
  den fehlenden Download, statt einen Start zu behaupten.

## Bewegung

Standard-Material-Easing, 150–300 ms. Sheet-Einblendung von unten, Fortschritt als lineare
Animation. Keine dekorativen Dauer-Animationen, um Akku zu schonen.

## Barrierefreiheit

- Kontrast mindestens 4.5:1 für Text; Status nie nur über Farbe, immer mit Text/Icon.
- Touch-Ziele mindestens 48 dp.
- Inhaltsbeschreibungen für Play/Pause, Löschen, Einstellungen.
- Schriftgrößen skalieren mit den Systemeinstellungen (sp statt dp).
