# Chat Tabs (Fabric, Minecraft 26.3)

[![Build](https://github.com/0eiki0/chattabs/actions/workflows/build.yml/badge.svg)](https://github.com/0eiki0/chattabs/actions/workflows/build.yml)

Teilt den Chat in Tabs auf: **System** (alles Übrige), Kanäle wie **Global** (`[G]`) / **Handel** (`[H]`) und automatisch ein **Msg-Tab pro Spieler** (`@Name`).

## Installation
Die `chattabs-<version>.jar` in den `mods`-Ordner legen. Benötigt nur den Fabric Loader (≥ 0.20), keine Fabric API.

## Bedienung
- Chat öffnen (T) → Tabs oben (oder unten) anklicken.
- Msg-Tabs mit ungelesenen Nachrichten **blinken**, andere Tabs zeigen `(Anzahl)` in Gelb.
- Rechtsklick / Mittelklick auf einen Msg-Tab schließt ihn.
- Schreibst du im Msg-Tab, wird automatisch `/msg <Spieler> <Text>` gesendet (Befehle mit `/` bleiben unverändert).
- **⚙** am Ende der Leiste öffnet die Einstellungen.

## Einstellungen (auch in `config/chattabs.json`)
| Option | Bedeutung |
|---|---|
| Tab-Leiste | Oben / Unten |
| Bei geschl. Chat | Nie / Bei Ungelesenem / Immer |
| Msg-Tabs, Blinken, bei Trennung schließen | an/aus |
| Max. Verlauf | gespeicherte Nachrichten insgesamt (Vanilla 100) |
| Msg-Erkennung | Regex; Spielername in `(?<player>…)` (weitere Alternativen: `player2`, `player3`, …) |
| Msg-Senden-Befehl | z. B. `/msg {player} {message}` oder `/tell {player} {message}` |

Pro Tab: **Name**, **Typ** (System = Rest, Kanal = Filter, Alle = alles), **Filter (Regex)** auf den Text ohne Farben,
**Sende-Präfix** (z. B. `/g `), **Blinken**. Reihenfolge per ↑/↓, löschen per ✕, neue Tabs mit „+ Tab“.
Die erste passende Kanal-Regel gewinnt; was nirgends passt, landet im System-Tab.

Beispiel-Filter: `^(?:\[[^\]]+\] )?\[G\] ` passt auf `[Freebuild] [G] Levlox: XD` (der graue Server-Präfix ist optional).

## Download
- **Neueste Testversion:** Reiter *Actions* → neuester „Build“-Lauf → unten bei *Artifacts* „chattabs“ herunterladen (ZIP mit der .jar).
- **Releases:** Wird ein Tag `v…` gepusht (z. B. `git tag v1.0.1 && git push origin v1.0.1`), baut GitHub automatisch ein Release mit der .jar.

## Selbst bauen
Benötigt JDK 25:
```
./gradlew build   # → build/libs/chattabs-<version>.jar
```
