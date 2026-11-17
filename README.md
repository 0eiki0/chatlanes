# Chat Tabs (Fabric, Minecraft 26.3)

[![Build](https://github.com/0eiki0/chattabs/actions/workflows/build.yml/badge.svg)](https://github.com/0eiki0/chattabs/actions/workflows/build.yml)

Teilt den Chat in Tabs auf: **System** (alles Übrige), Kanäle wie **Global** (`[G]`) / **Handel** (`[H]`) und automatisch ein **Msg-Tab pro Spieler** (`@Name`).
Läuft auch zusammen mit der CubesideMod.

## Installation
Die `chattabs-<version>.jar` in den `mods`-Ordner legen. Benötigt **Fabric Loader ≥ 0.20** und **Fabric API**. [Mod Menu](https://modrinth.com/mod/modmenu) ist optional.

## Bedienung
- Chat öffnen (T) → Tabs direkt über dem Chat anklicken.
- Msg-Tabs mit ungelesenen Nachrichten **blinken**, andere Tabs zeigen `(Anzahl)` in Gelb.
- Msg-Tabs schließt du mit dem **×** am Tab (oder per Rechts-/Mittelklick). Sie bleiben auch nach einem Neustart zu, bis der Spieler dir wieder schreibt.
- Das Chatfeld hat bei offenem Chat immer die volle Höhe, damit die Tabs immer an derselben Stelle sitzen (abschaltbar unter *Allgemein → Feste Chatgröße*).
- **Chat durchsuchen:** im offenen Chat **Strg+F** drücken oder „Suchen“ in der Tab-Leiste anklicken. Gesucht wird im ganzen gespeicherten Verlauf (alle Tabs oder nur der aktuelle); ein Klick auf einen Treffer öffnet den passenden Tab und springt zur Nachricht.
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
- **Tab bearbeiten**: Name, Uhrzeit (Standard/An/Aus), Typ (System / Kanal / Alle), Erkennung, Wechsel-Befehl, Sende-Präfix, Blinken, Zähler „(3)“ an/aus, Farbe, **Ton mit Lautstärke** (rund 20 Töne, ▶ zum Probehören), **Zusätzliche Nachrichten** (eigene Regeln, mit denen Nachrichten, die sonst im System-Tab landen, in diesen Kanal kommen – per Kennung, Text, Regex oder **Befehl-Ausgabe**, z. B. die komplette Ausgabe von `/where`, Zeitfenster pro Befehl einstellbar (0,1–60 s), funktioniert auch mit Makro-Mods; optional zusätzlich in System) und ein **Live-Test** („✔ Landet im Tab …“)
  - *Kennung*: einfach z. B. `[G]` eintragen – Server-Präfixe wie `[Freebuild]` davor werden übersprungen
  - *Enthält Text*: alle Nachrichten mit diesem Text
  - *Regex*: regulärer Ausdruck für Profis
- **Allgemein**: Nach Updates suchen (Meldung im Chat, wenn auf GitHub eine neuere Version liegt), Sprache der Mod (Automatisch wie Minecraft / Deutsch / English), Tab-Leiste oben/unten, feste Chatgröße, Anzeige bei geschlossenem Chat, Ungelesen-Zähler, Verlaufsgröße, **Uhrzeit vor Nachrichten** (an/aus, Format `[14:05]`, `[14:05:33]`, `14:05`, `14:05:33`)
- **Automatik**: **Standard-Tab** (bei jedem Spielstart ausgewählt), **automatisch zurück** in den Standard-Tab nach einstellbarer Zeit ohne eigene Nachricht, **System-Ausgabe kurz anzeigen** (nach Befehlen oder Block-Klicks, z. B. LogBlock mit Holzspitzhacke/Bedrock, springt die Ansicht kurz in den System-Tab und danach zurück; Dauer, Reaktionszeit, Items und Ausnahme-Befehle einstellbar)
- **Kanäle & Verlauf**: Kanal beim Tab-Klick wechseln, Server-Bestätigung ausblenden, Nachrichten speichern, gespeicherten Verlauf löschen (gespeichert in `config/chattabs/history/`)
- **Private Nachrichten**: Msg-Tabs an/aus, Blinken, Uhrzeit, Ton mit Lautstärke, beim Trennen schließen, Senden-Befehl, Erkennung mit Live-Test

Alles wird in `config/chattabs.json` gespeichert.

## Download
- **Releases:** Nach jedem Push auf `main` erstellt GitHub automatisch ein Release mit der fertigen `.jar` – rechts auf der Repo-Seite unter *Releases*.
  - Neue Version in `gradle.properties` → Release `v<Version>` (z. B. `v1.6.0`)
  - Version unverändert → Release `v<Version>-build.<Nummer>`
- **Testversionen anderer Branches:** Reiter *Actions* → Lauf auswählen → unten bei *Artifacts* „chattabs“.

## Selbst bauen
Benötigt JDK 25:
```
./gradlew build   # → build/libs/chattabs-<version>.jar
```
