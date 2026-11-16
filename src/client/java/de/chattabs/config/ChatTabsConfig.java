package de.chattabs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alle Einstellungen der Mod. Wird als config/chattabs.json gespeichert und kann
 * im Spiel über das Zahnrad in der Tab-Leiste bearbeitet werden.
 */
public class ChatTabsConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("chattabs");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum Position { TOP, BOTTOM }

	public enum ClosedChatMode { NEVER, UNREAD, ALWAYS }

	/** Tabs oberhalb oder unterhalb des Chats. */
	public Position position = Position.TOP;
	/** Ob die Tab-Leiste auch bei geschlossenem Chat angezeigt wird. */
	public ClosedChatMode showWhenClosed = ClosedChatMode.UNREAD;
	/** Wie viele Nachrichten insgesamt gespeichert werden (Vanilla: 100). */
	public int maxHistory = 1000;

	/** Automatisch einen Tab pro Spieler für private Nachrichten anlegen. */
	public boolean pmTabs = true;
	/**
	 * Erkennt private Nachrichten. Der Spielername muss in einer Gruppe namens
	 * "player" (oder "player2", "player3", ...) stehen.
	 */
	public String pmPattern = "^(?:\\[[^\\]]+\\] )?(?:From|To|Von|An) (?<player>[A-Za-z0-9_]{2,16}): "
		+ "|^\\[(?<player2>[A-Za-z0-9_]{2,16}) -> (?:me|mir|dir)\\]"
		+ "|^\\[(?:me|mir|Ich|ich) -> (?<player3>[A-Za-z0-9_]{2,16})\\]";
	/** Befehl, mit dem im Msg-Tab getippte Nachrichten gesendet werden. */
	public String pmSendCommand = "/msg {player} {message}";
	/** Msg-Tabs mit ungelesenen Nachrichten blinken. */
	public boolean pmBlink = true;
	/** Msg-Tabs nach einem Server-Wechsel automatisch schließen. */
	public boolean pmCloseOnDisconnect = true;

	public List<TabConfig> tabs = new ArrayList<>();

	public static class TabConfig {
		public enum Type { SYSTEM, CHANNEL, ALL }

		public String name = "Tab";
		public Type type = Type.CHANNEL;
		/** Regulärer Ausdruck, der auf den Text der Nachricht (ohne Farben) angewendet wird. */
		public String pattern = "";
		/** Wird beim Senden aus diesem Tab vor die Nachricht gesetzt, z. B. "/g " (leer = normal senden). */
		public String sendPrefix = "";
		/** Tab blinkt bei ungelesenen Nachrichten. */
		public boolean blink = false;

		public TabConfig() {
		}

		public TabConfig(String name, Type type, String pattern, String sendPrefix, boolean blink) {
			this.name = name;
			this.type = type;
			this.pattern = pattern;
			this.sendPrefix = sendPrefix;
			this.blink = blink;
		}

		public TabConfig copy() {
			return new TabConfig(this.name, this.type, this.pattern, this.sendPrefix, this.blink);
		}
	}

	public static ChatTabsConfig createDefault() {
		ChatTabsConfig config = new ChatTabsConfig();
		config.tabs.add(new TabConfig("System", TabConfig.Type.SYSTEM, "", "", false));
		config.tabs.add(new TabConfig("Global", TabConfig.Type.CHANNEL, "^(?:\\[[^\\]]+\\] )?\\[G\\] ", "", false));
		config.tabs.add(new TabConfig("Handel", TabConfig.Type.CHANNEL, "^(?:\\[[^\\]]+\\] )?\\[H\\] ", "", false));
		return config;
	}

	public ChatTabsConfig copy() {
		return GSON.fromJson(GSON.toJson(this), ChatTabsConfig.class).sanitize();
	}

	private ChatTabsConfig sanitize() {
		ChatTabsConfig defaults = new ChatTabsConfig();
		if (this.position == null) this.position = defaults.position;
		if (this.showWhenClosed == null) this.showWhenClosed = defaults.showWhenClosed;
		if (this.pmPattern == null) this.pmPattern = defaults.pmPattern;
		if (this.pmSendCommand == null) this.pmSendCommand = defaults.pmSendCommand;
		if (this.maxHistory < 100) this.maxHistory = 100;
		if (this.maxHistory > 20000) this.maxHistory = 20000;
		if (this.tabs == null) this.tabs = new ArrayList<>();
		this.tabs.removeIf(tab -> tab == null);
		for (TabConfig tab : this.tabs) {
			if (tab.name == null || tab.name.isBlank()) tab.name = "Tab";
			if (tab.type == null) tab.type = TabConfig.Type.CHANNEL;
			if (tab.pattern == null) tab.pattern = "";
			if (tab.sendPrefix == null) tab.sendPrefix = "";
		}
		return this;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("chattabs.json");
	}

	public static ChatTabsConfig load() {
		Path path = file();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				ChatTabsConfig config = GSON.fromJson(reader, ChatTabsConfig.class);
				if (config != null) {
					return config.sanitize();
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.error("Konnte {} nicht lesen, verwende Standardwerte", path, e);
				return createDefault();
			}
		}

		ChatTabsConfig config = createDefault();
		config.save();
		return config;
	}

	public void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Konnte {} nicht speichern", path, e);
		}
	}
}
