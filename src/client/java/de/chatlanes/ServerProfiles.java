package de.chatlanes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.chatlanes.config.ChatLanesConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ein eigenes Profil (Tabs, Kanäle, alle Einstellungen) pro Server.
 * - Standard-Profil: config/chatlanes.json – gilt im Einzelspieler und ist Vorlage für neue Server.
 * - Server-Profile: config/chatlanes/profiles/&lt;server&gt;.json
 * - Serverliste mit An/Aus pro Server: config/chatlanes/servers.json
 * Sprache und Update-Prüfung gelten für alle Profile gemeinsam (stehen im Standard-Profil).
 */
public final class ServerProfiles {
	private static final Logger LOGGER = LoggerFactory.getLogger("chatlanes");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Inhalt von servers.json */
	public static class ServerList {
		/** Ist ChatLanes auf einem Server aktiv, den es noch nicht kennt? */
		public boolean unknownEnabled = true;
		public Map<String, Entry> servers = new LinkedHashMap<>();
	}

	public static class Entry {
		public String name = "";
		public String address = "";
		public boolean enabled = true;
		public long lastJoin;
	}

	private static ServerList list;
	/** Schlüssel des aktuellen Servers (null = Standard-Profil, z. B. Einzelspieler oder Hauptmenü). */
	private static @Nullable String activeKey;

	private ServerProfiles() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("chatlanes");
	}

	private static Path listFile() {
		return dir().resolve("servers.json");
	}

	public static Path profileFile(@Nullable String key) {
		return key == null ? ChatLanesConfig.standardFile() : dir().resolve("profiles").resolve(key + ".json");
	}

	public static ServerList list() {
		if (list == null) {
			Path path = listFile();
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					list = GSON.fromJson(reader, ServerList.class);
				} catch (IOException | RuntimeException e) {
					LOGGER.error("Konnte {} nicht lesen", path, e);
				}
			}
			if (list == null) {
				list = new ServerList();
			}
			if (list.servers == null) {
				list.servers = new LinkedHashMap<>();
			}
			list.servers.values().removeIf(e -> e == null);
		}
		return list;
	}

	public static void saveList() {
		Path path = listFile();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(list(), writer);
			}
		} catch (IOException e) {
			LOGGER.error("Konnte {} nicht speichern", path, e);
		}
	}

	public static @Nullable String activeKey() {
		return activeKey;
	}

	/** Ist ChatLanes auf dem aktuellen Server eingeschaltet? (Standard-Profil: immer) */
	public static boolean isActiveEnabled() {
		if (activeKey == null) {
			return true;
		}
		Entry entry = list().servers.get(activeKey);
		return entry == null || entry.enabled;
	}

	/** Name für die Anzeige, z. B. "Mein Server" oder "Standard-Profil". */
	public static String displayName(@Nullable String key) {
		if (key == null) {
			return net.minecraft.client.resources.language.I18n.get("chatlanes.profiles.standard");
		}
		Entry entry = list().servers.get(key);
		if (entry == null) {
			return key;
		}
		return entry.name == null || entry.name.isBlank() ? entry.address : entry.name;
	}

	/** Lädt ein Profil (Server-Profile übernehmen Sprache/Update-Prüfung aus dem Standard-Profil). */
	public static ChatLanesConfig load(@Nullable String key) {
		ChatLanesConfig config;
		if (key != null && !Files.exists(profileFile(key))) {
			// Neues Server-Profil: Kopie des Standard-Profils
			config = ChatLanesConfig.load(profileFile(null)).copy();
			config.save(profileFile(key));
		} else {
			config = ChatLanesConfig.load(profileFile(key));
		}
		if (key != null) {
			ChatLanesConfig standard = ChatLanesConfig.load(profileFile(null));
			config.language = standard.language;
			config.updateCheck = standard.updateCheck;
		}
		return config;
	}

	/**
	 * Speichert das gerade aktive Profil so, wie es im Spiel gerade ist (z. B. nach dem Verschieben
	 * eines Chatfensters) – ohne die Tabs neu aufzubauen.
	 */
	public static void saveActiveQuietly() {
		ChatLanes.get().config().save(profileFile(activeKey));
	}

	/** Speichert ein Profil; ist es das aktive, wird es sofort übernommen. */
	public static void save(@Nullable String key, ChatLanesConfig config) {
		config.save(profileFile(key));
		if (key != null) {
			// Sprache/Update-Prüfung gelten für alle Profile → im Standard-Profil merken
			ChatLanesConfig standard = ChatLanesConfig.load(profileFile(null));
			if (standard.language != config.language || standard.updateCheck != config.updateCheck) {
				standard.language = config.language;
				standard.updateCheck = config.updateCheck;
				standard.save(profileFile(null));
			}
		}
		if (java.util.Objects.equals(key, activeKey)) {
			ChatLanes.get().apply(config.copy());
		} else if (key != null) {
			// Sprache sofort auch im aktiven Profil übernehmen
			ChatLanes.get().config().language = config.language;
			ChatLanes.get().config().updateCheck = config.updateCheck;
		}
	}

	/** Server betreten: Profil wählen (neue Server bekommen eins) und anwenden. */
	public static void onJoin(Minecraft minecraft) {
		ServerData server = minecraft.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank() && minecraft.getSingleplayerServer() == null) {
			onJoinServer(server.name, server.ip);
		} else {
			switchTo(null, true);
		}
	}

	/** Mehrspieler-Server betreten (Name aus der Serverliste, Adresse). */
	static void onJoinServer(String name, String ip) {
		String key = ("server_" + ip).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		{
			Entry entry = list().servers.get(key);
			if (entry == null) {
				entry = new Entry();
				entry.address = ip;
				entry.enabled = list().unknownEnabled;
				list().servers.put(key, entry);
			}
			entry.name = name;
			entry.lastJoin = System.currentTimeMillis();
			saveList();
		}
		switchTo(key, true);
	}

	/** Server verlassen: zurück zum Standard-Profil. */
	public static void onDisconnect() {
		switchTo(null, true);
	}

	private static void switchTo(@Nullable String key, boolean force) {
		if (!force && java.util.Objects.equals(key, activeKey)) {
			return;
		}
		activeKey = key;
		ChatLanes.get().applyProfile(load(key), isActiveEnabled());
	}

	/** An/Aus für einen Server ändern (wirkt sofort, wenn es der aktuelle ist). */
	public static void setEnabled(String key, boolean enabled) {
		Entry entry = list().servers.get(key);
		if (entry != null) {
			entry.enabled = enabled;
			saveList();
			if (key.equals(activeKey)) {
				ChatLanes.get().setEnabled(enabled);
			}
		}
	}

	/** Server aus der Liste entfernen und sein Profil löschen (nicht den aktuellen Server). */
	public static void remove(String key) {
		if (key.equals(activeKey)) {
			return;
		}
		list().servers.remove(key);
		saveList();
		try {
			Files.deleteIfExists(profileFile(key));
		} catch (IOException e) {
			LOGGER.warn("Konnte Profil {} nicht löschen", key, e);
		}
	}

	/** Profil eines Servers durch eine Kopie des Standard-Profils ersetzen. */
	public static void resetToStandard(String key) {
		save(key, ChatLanesConfig.load(profileFile(null)).copy());
	}
}
