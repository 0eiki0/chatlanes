# ChatLanes (Fabric, Minecraft 26.3)

> Früher „Chat Tabs“. Beim ersten Start übernimmt ChatLanes automatisch alle Einstellungen, Server-Profile und den gespeicherten Verlauf. Die alte `chattabs-*.jar` bitte aus dem `mods`-Ordner löschen.

[![Build](https://github.com/0eiki0/chatlanes/actions/workflows/build.yml/badge.svg)](https://github.com/0eiki0/chatlanes/actions/workflows/build.yml)

📖 **[Wiki mit Anleitung und Bildern (Deutsch / English)](https://github.com/0eiki0/chatlanes/wiki)**

Teilt den Chat in Tabs auf: **System** (alles Übrige), Kanäle wie **Global** (`[G]`) / **Handel** (`[H]`) und automatisch ein **Msg-Tab pro Spieler** (`@Name`).
Läuft auch zusammen mit der CubesideMod.

## Installation
Die `chatlanes-<version>.jar` in den `mods`-Ordner legen. Benötigt **Fabric Loader ≥ 0.20** und **Fabric API**. [Mod Menu](https://modrinth.com/mod/modmenu) ist optional.

## Bedienung
- Chat öffnen (T) → Tabs direkt über dem Chat anklicken.
- Msg-Tabs mit ungelesenen Nachrichten **blinken**, andere Tabs zeigen `(Anzahl)` in Gelb.
- Msg-Tabs schließt du mit dem **×** am Tab (oder per Rechts-/Mittelklick). Sie bleiben auch nach einem Neustart zu, bis der Spieler dir wieder schreibt.
- Das Chatfeld hat bei offenem Chat immer die volle Höhe, damit die Tabs immer an derselben Stelle sitzen (abschaltbar unter *Allgemein → Feste Chatgröße*).
- **Chat durchsuchen:** im offenen Chat **Strg+F** drücken oder die **Lupe** ganz vorne in der Tab-Leiste anklicken. Gesucht wird im ganzen gespeicherten Verlauf (alle Tabs oder nur der aktuelle); ein Klick auf einen Treffer öffnet den passenden Tab und springt zur Nachricht.
- Schickst du selbst `/msg Name …` (auch `/tell`, `/w`, `/r`), wechselt die Mod automatisch in den Tab `@Name` (abschaltbar).
- Schreibst du im Msg-Tab, wird automatisch `/msg <Spieler> <Text>` gesendet (Befehle mit `/` bleiben unverändert).
- Wählst du einen Kanal-Tab aus, wechselt die Mod auch auf dem Server in diesen Kanal (Global → `/ch global`, Handel → `/ch handel`; pro Tab einstellbar).
- Die Nachrichten aller Tabs werden pro Server gespeichert und beim nächsten Betreten wieder in die richtigen Tabs geladen (abschaltbar).

## Eigene Chatfenster und Filter
Neben den Tabs kann ChatLanes Nachrichten in **eigene Chatfenster** legen, die ständig sichtbar sind, z. B. ein kleines Fenster nur für Join/Leave-Nachrichten oder für Ankündigungen wie „[Advent 2026] Der Cubeside Weihnachtsmarkt hat geöffnet!“. Der Hauptchat bleibt dadurch übersichtlich.

- **Filter erstellen:** Im offenen Chat (T) mit **Rechtsklick** auf eine Nachricht klicken, im normalen Chat oder in einem Fenster.
  - *Vorlage*: **Ganze Nachricht** (z. B. nur, wenn Max_Mustermann „Hallo Welt!“ schreibt), **Ohne Absender** (der Text, egal von wem) oder **Nur Kennung** (alles mit z. B. `[Advent 2026]` am Anfang). Den Text kannst du danach noch anpassen, auch als Regex.
  - *Ziel*: **Neues Fenster**, **neuer Tab** oder ein bestehendes Fenster bzw. ein bestehender Kanal-Tab.
  - **Auch im normalen Chat anzeigen**: Aus = die Nachricht wird ins Fenster *verschoben*, An = sie erscheint zusätzlich dort, wo sie sonst landen würde.
  - Vorschau: wie viele Nachrichten im Verlauf schon passen.
  - Filter von Fenstern gelten für **jede** Nachricht, auch für Kanal-Nachrichten wie `[G]`, und haben Vorrang. Filter von Tabs gelten wie die „Zusätzlichen Nachrichten“ nur für Nachrichten, die sonst im System-Tab landen würden.
- **Fenster verschieben und Größe ändern:** Bei offenem Chat das Fenster an der **Titelleiste** ziehen, an der **Ecke unten rechts** die Größe ändern. Das Mausrad über einem Fenster scrollt nur dieses Fenster. Position und Größe werden pro Server-Profil gespeichert und passen sich der Fenstergröße und GUI-Skalierung an.
- **Jeder Kanal-Tab und jeder „Alle“-Tab kann ein Fenster sein:** *Tab bearbeiten → Anzeige: Eigenes Fenster*. Ein Fenster vom Typ **Alle** ist der komplette Chatverlauf in der Original-Reihenfolge, also quasi der Vanilla-Chat zum Nachkontrollieren.
- **Fenster …** (im Tab-Editor): **Textgröße**, **Hintergrund-Deckkraft**, **Anzeige bei geschlossenem Chat** (wie der Chat ausblenden, immer sichtbar, nur bei offenem Chat oder eine feste Zeit), *Auch im normalen Chat anzeigen*, *Position zurücksetzen*.
- Beliebig viele Fenster. Links und Befehle in Nachrichten lassen sich in Fenstern genauso anklicken wie im Chat, und die Suche (Strg+F) springt auch zu Nachrichten in Fenstern.
- Filter bearbeiten oder löschen: *Tab bearbeiten → Zusätzliche Nachrichten*.

## Server-Profile
- Jeder Server bekommt beim ersten Betreten automatisch ein **eigenes Profil** (Tabs, Kanäle und alle Einstellungen) – als Kopie des **Standard-Profils**. Das Standard-Profil gilt im Einzelspieler und ist die Vorlage für neue Server.
- Unter **Server-Profile …** (oben im Menü) siehst du alle bekannten Server: ChatLanes pro Server **an/aus**, Profil bearbeiten, auf Standard zurücksetzen (↺) oder entfernen (✕).
- **Auf neuen Servern:** einstellbar, ob ChatLanes auf einem unbekannten Server an oder aus ist. Aus = der Chat sieht aus wie in normalem Minecraft.
- Sprache und Update-Prüfung gelten für alle Profile gemeinsam.
- Gespeichert in `config/chatlanes/servers.json` und `config/chatlanes/profiles/`.

## Einstellungsmenü
Öffnen über **eine** dieser Möglichkeiten:
- Taste **K** (änderbar unter *Optionen → Steuerung → ChatLanes*)
- Befehl **`/chatlanes`** (der alte Befehl `/chattabs` geht weiterhin)
- **„Optionen“** am Ende der Tab-Leiste
- **Mod Menu** → ChatLanes → Einstellungs-Symbol

Im Menü:
- **Tabs**: hinzufügen, bearbeiten, verschieben (↑/↓), löschen
- **Anordnen**: Tabs per Drag & Drop sortieren und auf eine **obere** (über dem Chat) und **untere Leiste** (über dem Eingabefeld) verteilen – auch die Msg-Tabs; pro Leiste einstellbar, ob sie bei geschlossenem Chat immer, nur bei Ungelesenem oder gar nicht angezeigt wird
- **Tab bearbeiten**: Name, Uhrzeit (Standard/An/Aus), Typ (System / Kanal / Alle), Erkennung, Wechsel-Befehl, Sende-Präfix, **Anzeige in der Tab-Leiste oder als eigenes Fenster** (siehe oben), Blinken, Zähler „(3)“ an/aus, Farbe, **Ton mit Lautstärke** (rund 20 Töne, ▶ zum Probehören), **Zusätzliche Nachrichten** (eigene Regeln, mit denen Nachrichten, die sonst im System-Tab landen, in diesen Kanal kommen – per Kennung, Text, Regex oder **Befehl-Ausgabe**, z. B. die komplette Ausgabe von `/where`, Zeitfenster pro Befehl einstellbar (0,1–60 s), funktioniert auch mit Makro-Mods; optional zusätzlich in System) und ein **Live-Test** („✔ Landet im Tab …“)
  - *Kennung*: einfach z. B. `[G]` eintragen – Server-Präfixe wie `[Freebuild]` davor werden übersprungen
  - *Enthält Text*: alle Nachrichten mit diesem Text
  - *Regex*: regulärer Ausdruck für Profis
- **Aussehen**: Chat-Breite, -Höhe, Textgröße, Zeilenabstand und Deckkraft (Minecraft-Einstellungen direkt im Menü), **Hintergrundfarbe** (Vorgaben oder eigener Hex-Wert), **sanftes Ein- und Ausblenden** mit einstellbarer Anzeige- und Ausblendzeit
- **Allgemein**: Nach Updates suchen (Meldung im Chat, wenn auf GitHub eine neuere Version liegt), Sprache der Mod (Automatisch wie Minecraft / Deutsch / English), Tab-Leiste oben/unten, feste Chatgröße, Anzeige bei geschlossenem Chat, Ungelesen-Zähler, Verlaufsgröße, **Skin-Köpfe vor Spielernamen** (optional mit zweiter Hautschicht), **Uhrzeit vor Nachrichten** (an/aus, Format `[14:05]`, `[14:05:33]`, `14:05`, `14:05:33`)
- **Automatik**: **Standard-Tab** (bei jedem Spielstart ausgewählt), **automatisch zurück** in den Standard-Tab nach einstellbarer Zeit ohne eigene Nachricht, **System-Ausgabe kurz anzeigen** (verpasste Nachrichten werden nach dem Zurückspringen noch einmal eingeblendet; nach Befehlen oder Block-Klicks, z. B. LogBlock mit Holzspitzhacke/Bedrock, springt die Ansicht kurz in den System-Tab und danach zurück; Dauer, Reaktionszeit, Items per Auswahl-Menü mit allen Items, Ausnahme-Befehle einstellbar)
- **Kanäle & Verlauf**: Kanal beim Tab-Klick wechseln, Server-Bestätigung ausblenden, Nachrichten speichern, gespeicherten Verlauf löschen (gespeichert in `config/chatlanes/history/`)
- **Private Nachrichten**: Msg-Tabs an/aus, Blinken, Uhrzeit, Ton mit Lautstärke, beim Trennen schließen, Senden-Befehl, Erkennung mit Live-Test

Alles wird in `config/chatlanes.json` gespeichert.

## Download
- **Releases:** Nach jedem Push auf `main` erstellt GitHub automatisch ein Release mit der fertigen `.jar` – rechts auf der Repo-Seite unter *Releases*.
  - Neue Version in `gradle.properties` → Release `v<Version>` (z. B. `v1.6.0`)
  - Version unverändert → Release `v<Version>-build.<Nummer>`
- **Testversionen anderer Branches:** Reiter *Actions* → Lauf auswählen → unten bei *Artifacts* „chatlanes“.

## Selbst bauen
Benötigt JDK 25:
```
./gradlew build   # → build/libs/chatlanes-<version>.jar
```

## Lizenz
MIT – siehe [LICENSE](LICENSE). Du darfst ChatLanes frei nutzen, in Modpacks einbinden, verändern und weitergeben, solange der Copyright-Hinweis erhalten bleibt.
