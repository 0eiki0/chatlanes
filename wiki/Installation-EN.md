# Installation

🇩🇪 [Deutsche Version](Installation)

## Requirements
- **Minecraft 26.3** (Java Edition)
- **[Fabric Loader](https://fabricmc.net/use/) 0.20 or newer**
- **[Fabric API](https://modrinth.com/mod/fabric-api)**
- optional: **[Mod Menu](https://modrinth.com/mod/modmenu)** to open the settings from the mod list too

## Steps
1. Install Fabric Loader for Minecraft 26.3.
2. Download the latest `chatlanes-<version>.jar` from the [releases page](https://github.com/0eiki0/chatlanes/releases).
3. Put it into your `mods` folder together with Fabric API.
4. Start Minecraft with the Fabric profile.

ChatLanes is **client-side only**. Nothing needs to be installed on the server, and it works together with the CubesideMod.

## Switching from “Chat Tabs”
ChatLanes used to be called **Chat Tabs**. On first start it automatically takes over all settings, server profiles and the saved history. Afterwards please delete the old `chattabs-*.jar` from your `mods` folder.

## Updates
With *Settings → General → Check for updates* the mod checks, when you join a world or server, whether a newer version is available on GitHub, and then posts a message in the chat with a **[Download]** link.

## Opening the settings
- Key **K** (can be changed under *Options → Controls → ChatLanes*)
- Command **`/chatlanes`** (the old `/chattabs` still works)
- **“Settings”** at the end of the tab bar in the open chat
- **Mod Menu** → ChatLanes → settings icon

## Files
| File | Content |
|---|---|
| `config/chatlanes.json` | default profile (singleplayer and template for new servers) |
| `config/chatlanes/servers.json` | list of known servers |
| `config/chatlanes/profiles/` | one profile per server |
| `config/chatlanes/history/` | saved chat history per server |
