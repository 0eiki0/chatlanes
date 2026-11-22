# Installation

🇬🇧 [English version](Installation-EN)

## Voraussetzungen
- **Minecraft 26.3** (Java Edition)
- **[Fabric Loader](https://fabricmc.net/use/) 0.20 oder neuer**
- **[Fabric API](https://modrinth.com/mod/fabric-api)**
- optional: **[Mod Menu](https://modrinth.com/mod/modmenu)**, um die Einstellungen auch aus der Mod-Liste zu öffnen

## Schritte
1. Fabric Loader für Minecraft 26.3 installieren.
2. Die neueste `chatlanes-<version>.jar` von der [Releases-Seite](https://github.com/0eiki0/chatlanes/releases) herunterladen.
3. Die Datei zusammen mit der Fabric API in den `mods`-Ordner legen.
4. Minecraft mit dem Fabric-Profil starten.

ChatLanes läuft nur auf dem **Client**. Auf dem Server muss nichts installiert werden, und die Mod funktioniert auch zusammen mit der CubesideMod.

## Umstieg von „Chat Tabs“
ChatLanes hieß früher **Chat Tabs**. Beim ersten Start übernimmt ChatLanes automatisch alle Einstellungen, Server-Profile und den gespeicherten Verlauf. Die alte `chattabs-*.jar` danach bitte aus dem `mods`-Ordner löschen.

## Updates
Unter *Einstellungen → Allgemein → Nach Updates suchen* prüft die Mod beim Betreten einer Welt oder eines Servers, ob es auf GitHub eine neuere Version gibt, und meldet sich dann im Chat mit einem Link **[Herunterladen]**.

## Einstellungen öffnen
- Taste **K** (änderbar unter *Optionen → Steuerung → ChatLanes*)
- Befehl **`/chatlanes`** (der alte Befehl `/chattabs` geht weiterhin)
- **„Optionen“** am Ende der Tab-Leiste im offenen Chat
- **Mod Menu** → ChatLanes → Einstellungs-Symbol

## Dateien
| Datei | Inhalt |
|---|---|
| `config/chatlanes.json` | Standard-Profil (Einzelspieler und Vorlage für neue Server) |
| `config/chatlanes/servers.json` | Liste der bekannten Server |
| `config/chatlanes/profiles/` | Ein Profil pro Server |
| `config/chatlanes/history/` | Gespeicherter Chatverlauf pro Server |
