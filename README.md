# Chat Tabs (Fabric, Minecraft 26.3)

[![Build](https://github.com/0eiki0/chattabs/actions/workflows/build.yml/badge.svg)](https://github.com/0eiki0/chattabs/actions/workflows/build.yml)

Teilt den Chat in Tabs auf: **System** (alles Übrige), Kanäle wie **Global** (`[G]`) / **Handel** (`[H]`) und automatisch ein **Msg-Tab pro Spieler** (`@Name`).
Läuft auch zusammen mit der CubesideMod.

## Installation
Die `chattabs-<version>.jar` in den `mods`-Ordner legen. Benötigt **Fabric Loader ≥ 0.20** und **Fabric API**. [Mod Menu](https://modrinth.com/mod/modmenu) ist optional.

## Bedienung
- Chat öffnen (T) → Tabs direkt über dem Chat anklicken.
- Msg-Tabs mit ungelesenen Nachrichten **blinken**, andere Tabs zeigen `(Anzahl)` in Gelb.
- Msg-Tabs schließt du mit dem **×** am Tab (oder per Rechts-/Mittelklick).
- Das Chatfeld hat bei offenem Chat immer die volle Höhe, damit die Tabs immer an derselben Stelle sitzen (abschaltbar unter *Allgemein → Feste Chatgröße*).
- Schreibst du im Msg-Tab, wird automatisch `/msg <Spieler> <Text>` gesendet (Befehle mit `/` bleiben unverändert).
- Wählst du einen Kanal-Tab aus, wechselt die Mod auch auf dem Server in diesen Kanal (Global → `/ch global`, Handel → `/ch handel`; pro Tab einstellbar).
- Die Nachrichten aller Tabs werden pro Server gespeichert und beim nächsten Betreten wieder in die richtigen Tabs geladen (abschaltbar).

## Einstellungsmenü
Öffnen über **eine** dieser Möglichkeiten:
- Taste **K** (änderbar unter *Optionen → Steuerung → Chat Tabs*)
- Befehl **`/chattabs`**
- **„Optionen“** am Ende der Tab-Leiste
- **Mod Menu** → Chat Tabs → Einstellungs-Symbol

Im Menü:
- **Tabs**: hinzufügen, bearbeiten, verschieben (↑/↓), löschen
- **Tab bearbeiten**: Name, Typ (System / Kanal / Alle), Erkennung, Wechsel-Befehl, Sende-Präfix, Blinken, Zähler „(3)“ an/aus, Farbe und ein **Live-Test** („✔ Landet im Tab …“)
  - *Kennung*: einfach z. B. `[G]` eintragen – Server-Präfixe wie `[Freebuild]` davor werden übersprungen
  - *Enthält Text*: alle Nachrichten mit diesem Text
  - *Regex*: regulärer Ausdruck für Profis
- **Allgemein**: Tab-Leiste oben/unten, feste Chatgröße, Anzeige bei geschlossenem Chat, Ungelesen-Zähler, Verlaufsgröße
- **Kanäle & Verlauf**: Kanal beim Tab-Klick wechseln, Server-Bestätigung ausblenden, Nachrichten speichern, gespeicherten Verlauf löschen (gespeichert in `config/chattabs/history/`)
- **Private Nachrichten**: Msg-Tabs an/aus, Blinken, Ton, beim Trennen schließen, Senden-Befehl, Erkennung mit Live-Test

Alles wird in `config/chattabs.json` gespeichert.

## Download
- **Neueste Testversion:** Reiter *Actions* → neuester „Build“-Lauf → unten bei *Artifacts* „chattabs“ herunterladen (ZIP mit der .jar).
- **Releases:** Wird ein Tag `v…` gepusht (z. B. `git tag v1.0.1 && git push origin v1.0.1`), baut GitHub automatisch ein Release mit der .jar.

## Selbst bauen
Benötigt JDK 25:
```
./gradlew build   # → build/libs/chattabs-<version>.jar
```
