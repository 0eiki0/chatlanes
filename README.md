# Chat Tabs (Fabric, Minecraft 26.3)

[![Build](https://github.com/0eiki0/chattabs/actions/workflows/build.yml/badge.svg)](https://github.com/0eiki0/chattabs/actions/workflows/build.yml)

Teilt den Chat in Tabs auf: **System** (alles Übrige), Kanäle wie **Global** (`[G]`) / **Handel** (`[H]`) und automatisch ein **Msg-Tab pro Spieler** (`@Name`).
Läuft auch zusammen mit der CubesideMod.

## Installation
Die `chattabs-<version>.jar` in den `mods`-Ordner legen. Benötigt **Fabric Loader ≥ 0.20** und **Fabric API**. [Mod Menu](https://modrinth.com/mod/modmenu) ist optional.

## Bedienung
- Chat öffnen (T) → Tabs direkt über dem Chat anklicken.
- Msg-Tabs mit ungelesenen Nachrichten **blinken**, andere Tabs zeigen `(Anzahl)` in Gelb.
- Rechtsklick oder Mittelklick auf einen Msg-Tab schließt ihn.
- Schreibst du im Msg-Tab, wird automatisch `/msg <Spieler> <Text>` gesendet (Befehle mit `/` bleiben unverändert).

## Einstellungsmenü
Öffnen über **eine** dieser Möglichkeiten:
- Taste **K** (änderbar unter *Optionen → Steuerung → Chat Tabs*)
- Befehl **`/chattabs`**
- **„Optionen“** am Ende der Tab-Leiste
- **Mod Menu** → Chat Tabs → Einstellungs-Symbol

Im Menü:
- **Tabs**: hinzufügen, bearbeiten, verschieben (↑/↓), löschen
- **Tab bearbeiten**: Name, Typ (System / Kanal / Alle), Erkennung, Sende-Präfix, Blinken, Farbe und ein **Live-Test** („✔ Landet im Tab …“)
  - *Kennung*: einfach z. B. `[G]` eintragen – Server-Präfixe wie `[Freebuild]` davor werden übersprungen
  - *Enthält Text*: alle Nachrichten mit diesem Text
  - *Regex*: regulärer Ausdruck für Profis
- **Allgemein**: Tab-Leiste oben/unten, Anzeige bei geschlossenem Chat, Ungelesen-Zähler, Verlaufsgröße
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
