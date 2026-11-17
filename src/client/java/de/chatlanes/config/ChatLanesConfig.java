package de.chatlanes.config;

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
 * Alle Einstellungen der Mod. Wird als config/chatlanes.json gespeichert und kann
 * im Spiel über das Zahnrad in der Tab-Leiste bearbeitet werden.
 */
public class ChatLanesConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("chatlanes");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Erkennt die Standard-Regex aus Version 1.0.x, z. B. ^(?:\[[^\]]+\] )?\[G\] */
	private static final java.util.regex.Pattern LEGACY_DEFAULT =
		java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("^(?:\\[[^\\]]+\\] )?\\[") + "([A-Za-z0-9]+)" + java.util.regex.Pattern.quote("\\] "));

	public enum Position { TOP, BOTTOM }

	/** Sprache der Mod: wie Minecraft, oder fest Deutsch/Englisch. */
	public enum Language { AUTO, GERMAN, ENGLISH }

	public Language language = Language.AUTO;

	/** Beim Betreten eines Servers prüfen, ob auf GitHub eine neuere Version liegt. */
	public boolean updateCheck = true;

	/** Pro Tab: Uhrzeit wie unter Allgemein eingestellt, immer an oder immer aus. */
	public enum TimestampMode { DEFAULT, ON, OFF }

	/** Format der Uhrzeit vor den Nachrichten. */
	public enum TimestampFormat {
		BRACKET_HM("'['HH:mm']'"), BRACKET_HMS("'['HH:mm:ss']'"), HM("HH:mm"), HMS("HH:mm:ss");

		public final String pattern;

		TimestampFormat(String pattern) {
			this.pattern = pattern;
		}
	}

	/** Uhrzeit vor jeder Nachricht anzeigen (Standard für alle Tabs, pro Tab übersteuerbar). */
	public boolean timestamps = false;
	public TimestampFormat timestampFormat = TimestampFormat.BRACKET_HM;
	/** Uhrzeit bei privaten Nachrichten. */
	public TimestampMode pmTimestamps = TimestampMode.DEFAULT;

	public enum ClosedChatMode { NEVER, UNREAD, ALWAYS }

	/** Tabs oberhalb oder unterhalb des Chats. */
	public Position position = Position.TOP;
	/** Chatfeld hat bei offenem Chat immer die volle Höhe, damit die Tabs immer an derselben Stelle sitzen. */
	public boolean fixedChatSize = true;
	/** Ob die Tab-Leiste auch bei geschlossenem Chat angezeigt wird. */
	public ClosedChatMode showWhenClosed = ClosedChatMode.UNREAD;
	/** Wie viele Nachrichten insgesamt gespeichert werden (Vanilla: 100). */
	public int maxHistory = 1000;
	/** Anzahl ungelesener Nachrichten hinter dem Tab-Namen anzeigen, z. B. "Global (3)". */
	public boolean showUnreadCount = true;

	/** Name des Tabs, der bei jedem Spielstart ausgewählt ist (leer/unbekannt = erster Tab). */
	public String defaultTab = "";
	/** Automatisch in den Standard-Tab zurückwechseln, wenn man eine Weile im aktuellen Tab nichts geschrieben hat. */
	public boolean autoReturn = false;
	/** Nach so vielen Sekunden ohne eigene Nachricht zurückwechseln. */
	public int autoReturnSeconds = 60;

	/**
	 * System-Ausgabe kurz anzeigen: Kommt kurz nach einer eigenen Aktion (Befehl, Block-Klick) etwas in den
	 * System-Tab, springt die Ansicht dorthin und nach {@link #peekSeconds} wieder zurück.
	 */
	public boolean peekSystem = true;
	/** So lange bleibt die Ansicht im System-Tab (verlängert sich bei weiteren System-Nachrichten). */
	public int peekSeconds = 15;
	/** So lange nach der Aktion zählt eine System-Nachricht als "Antwort" darauf. */
	public double peekReaction = 1.5;
	/** Auch Block-Klicks (z. B. LogBlock-Abfragen) gelten als Aktion. */
	public boolean peekBlockClicks = true;
	/** Nur mit diesen Items in der Hand zählen Block-Klicks (leer = mit jedem Item). */
	public String peekItems = "wooden_pickaxe, bedrock";
	/** Befehle, die nie in den System-Tab springen lassen (zusätzlich zu den Wechsel-Befehlen der Tabs). */
	public String peekIgnoredCommands = "/ch";

	/** Beim Auswählen eines Kanal-Tabs dessen Wechsel-Befehl (z. B. "/ch global") an den Server senden. */
	public boolean switchChannelOnTab = true;
	/**
	 * Optional: Server-Antworten auf den Kanalwechsel ausblenden (regulärer Ausdruck, leer = nichts ausblenden).
	 * Gilt nur für Nachrichten, die bis zu 3 Sekunden nach einem automatischen Wechsel ankommen.
	 */
	public String hideSwitchConfirmation = "";
	/** Nachrichten aller Tabs pro Server speichern und beim nächsten Betreten wiederherstellen. */
	public boolean saveHistory = true;

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
	/** Veraltet (bis 1.4.0): Ton an/aus. Wird beim Laden in {@link #pmSoundType} übernommen. */
	public @org.jspecify.annotations.Nullable Boolean pmSound;
	/** Ton bei einer neuen privaten Nachricht. */
	public TabSound pmSoundType;
	/** Lautstärke für Msg-Töne in Prozent (0–100). */
	public int pmVolume = 100;
	/** Msg-Tabs nach einem Server-Wechsel automatisch schließen. */
	public boolean pmCloseOnDisconnect = true;

	public List<TabConfig> tabs = new ArrayList<>();

	public static class TabConfig {
		public enum Type { SYSTEM, CHANNEL, ALL }

		/**
		 * Wie {@link #pattern} gelesen wird:
		 * TAG = Kennung wie "[G]" am Anfang (davor stehende Server-Präfixe wie "[Freebuild]" werden übersprungen),
		 * CONTAINS = Nachricht enthält den Text (Groß-/Kleinschreibung egal),
		 * REGEX = regulärer Ausdruck für Profis.
		 */
		/** COMMAND gibt es nur bei zusätzlichen Regeln: Ausgabe eines Befehls (z. B. /where) diesem Kanal zuordnen. */
		public enum Match {
			TAG, CONTAINS, REGEX, COMMAND;

			/** Erkennungsarten für die Haupt-Erkennung eines Kanals (ohne COMMAND). */
			public static final java.util.List<Match> MAIN = java.util.List.of(TAG, CONTAINS, REGEX);
		}

		public String name = "Tab";
		public Type type = Type.CHANNEL;
		// Absichtlich ohne Startwert: fehlt das Feld in einer alten Config, wird es zu REGEX (siehe sanitize).
		public Match match;
		/** Kennung, Text oder regulärer Ausdruck – je nach {@link #match}. */
		public String pattern = "";
		/** Textfarbe des Tabs (siehe {@link TabColor}). */
		public TabColor color = TabColor.DEFAULT;
		/**
		 * Zusätzliche Regeln: Nachrichten, die sonst im System-Tab landen würden,
		 * kommen in diesen Kanal, wenn eine dieser Regeln passt.
		 */
		public List<Rule> extraRules;
		/** Per Zusatzregel zugeordnete Nachrichten trotzdem auch im System-Tab zeigen. */
		public boolean extraAlsoInSystem = false;

		public List<Rule> extraRulesOrEmpty() {
			if (this.extraRules == null) {
				this.extraRules = new ArrayList<>();
			}
			return this.extraRules;
		}

		/** Uhrzeit vor Nachrichten dieses Tabs (fehlt = wie unter Allgemein). */
		public TimestampMode timestamps;

		public TimestampMode timestampsOrDefault() {
			return this.timestamps == null ? TimestampMode.DEFAULT : this.timestamps;
		}

		/** Ton bei neuen Nachrichten in diesem Tab (fehlt = kein Ton). */
		public TabSound sound;
		/** Lautstärke des Tons in Prozent (0–100, fehlt = 100). */
		public Integer volume;

		public TabSound soundOrNone() {
			return this.sound == null ? TabSound.NONE : this.sound;
		}

		public int volumeOrDefault() {
			return this.volume == null ? 100 : Math.max(0, Math.min(100, this.volume));
		}
		/** Wird beim Auswählen des Tabs an den Server gesendet, z. B. "/ch global" (leer = nichts senden). */
		public String switchCommand;
		/** Wird beim Senden aus diesem Tab vor die Nachricht gesetzt, z. B. "/g " (leer = normal senden). */
		public String sendPrefix = "";
		/** Tab blinkt bei ungelesenen Nachrichten. */
		public boolean blink = false;
		/** Anzahl ungelesener Nachrichten in Klammern hinter dem Namen anzeigen, z. B. "Global (3)". Fehlt = an. */
		public Boolean showUnread;

		public boolean showsUnread() {
			return this.showUnread == null || this.showUnread;
		}

		public TabConfig() {
		}

		public TabConfig(String name, Type type, Match match, String pattern, String sendPrefix, boolean blink, TabColor color) {
			this(name, type, match, pattern, "", sendPrefix, blink, color);
		}

		public TabConfig(String name, Type type, Match match, String pattern, String switchCommand, String sendPrefix, boolean blink, TabColor color) {
			this.switchCommand = switchCommand;
			this.name = name;
			this.type = type;
			this.match = match;
			this.pattern = pattern;
			this.sendPrefix = sendPrefix;
			this.blink = blink;
			this.color = color;
		}

		public TabConfig copy() {
			TabConfig copy = new TabConfig(this.name, this.type, this.match, this.pattern, this.switchCommand, this.sendPrefix, this.blink, this.color);
			copy.showUnread = this.showUnread;
			copy.sound = this.sound;
			copy.timestamps = this.timestamps;
			copy.extraAlsoInSystem = this.extraAlsoInSystem;
			copy.extraRules = new ArrayList<>();
			for (Rule rule : this.extraRulesOrEmpty()) {
				copy.extraRules.add(rule.copy());
			}
			copy.volume = this.volume;
			return copy;
		}
	}

	/** Eine zusätzliche Erkennungsregel eines Kanals. */
	public static class Rule {
		public TabConfig.Match match = TabConfig.Match.CONTAINS;
		public String pattern = "";
		/** Nur bei Befehl-Ausgabe: wie viele Sekunden nach dem Befehl eingefangen wird (fehlt = 3, auch 0,25 usw.). */
		public Double seconds;

		public double secondsOrDefault() {
			return this.seconds == null ? 3.0 : Math.max(0.1, Math.min(60.0, this.seconds));
		}

		public Rule() {
		}

		public Rule(TabConfig.Match match, String pattern) {
			this.match = match;
			this.pattern = pattern;
		}

		public Rule copy() {
			Rule copy = new Rule(this.match, this.pattern);
			copy.seconds = this.seconds;
			return copy;
		}
	}

	public static ChatLanesConfig createDefault() {
		ChatLanesConfig config = new ChatLanesConfig();
		config.pmSoundType = TabSound.NONE;
		config.tabs.add(new TabConfig("System", TabConfig.Type.SYSTEM, TabConfig.Match.TAG, "", "", false, TabColor.DEFAULT));
		config.tabs.add(new TabConfig("Global", TabConfig.Type.CHANNEL, TabConfig.Match.TAG, "[G]", "/ch global", "", false, TabColor.GREEN));
		config.tabs.add(new TabConfig("Handel", TabConfig.Type.CHANNEL, TabConfig.Match.TAG, "[H]", "/ch handel", "", false, TabColor.GOLD));
		return config;
	}

	public ChatLanesConfig copy() {
		return GSON.fromJson(GSON.toJson(this), ChatLanesConfig.class).sanitize();
	}

	private ChatLanesConfig sanitize() {
		ChatLanesConfig defaults = new ChatLanesConfig();
		if (this.position == null) this.position = defaults.position;
		if (this.language == null) this.language = Language.AUTO;
		if (this.timestampFormat == null) this.timestampFormat = TimestampFormat.BRACKET_HM;
		if (this.pmTimestamps == null) this.pmTimestamps = TimestampMode.DEFAULT;
		if (this.pmSoundType == null) {
			this.pmSoundType = Boolean.TRUE.equals(this.pmSound) ? TabSound.PLING : TabSound.NONE;
		}
		this.pmSound = null;
		this.pmVolume = Math.max(0, Math.min(100, this.pmVolume));
		if (this.showWhenClosed == null) this.showWhenClosed = defaults.showWhenClosed;
		if (this.pmPattern == null) this.pmPattern = defaults.pmPattern;
		if (this.pmSendCommand == null) this.pmSendCommand = defaults.pmSendCommand;
		if (this.hideSwitchConfirmation == null) this.hideSwitchConfirmation = "";
		if (this.defaultTab == null) this.defaultTab = "";
		if (this.peekItems == null) this.peekItems = "";
		if (this.peekIgnoredCommands == null) this.peekIgnoredCommands = "";
		this.peekSeconds = Math.max(2, Math.min(600, this.peekSeconds));
		this.peekReaction = Math.max(0.2, Math.min(10.0, this.peekReaction));
		this.autoReturnSeconds = Math.max(5, Math.min(3600, this.autoReturnSeconds));
		if (this.maxHistory < 100) this.maxHistory = 100;
		if (this.maxHistory > 20000) this.maxHistory = 20000;
		if (this.tabs == null) this.tabs = new ArrayList<>();
		this.tabs.removeIf(tab -> tab == null);
		for (TabConfig tab : this.tabs) {
			if (tab.name == null || tab.name.isBlank()) tab.name = "Tab";
			if (tab.type == null) tab.type = TabConfig.Type.CHANNEL;
			// Ältere Configs (Version 1.0.x) kannten nur reguläre Ausdrücke.
			// Die Standard-Regex von 1.0.x ("^(?:\\[[^\\]]+\\] )?\\[G\\] ") wird zur einfachen Kennung "[G]".
			java.util.regex.Matcher legacy = LEGACY_DEFAULT.matcher(tab.pattern);
			if ((tab.match == null || tab.match == TabConfig.Match.REGEX) && legacy.matches()) {
				tab.match = TabConfig.Match.TAG;
				tab.pattern = "[" + legacy.group(1) + "]";
			} else if (tab.match == null) {
				tab.match = TabConfig.Match.REGEX;
			}
			if (tab.color == null) tab.color = TabColor.DEFAULT;
			tab.extraRulesOrEmpty().removeIf(rule -> rule == null);
			for (Rule rule : tab.extraRules) {
				if (rule.match == null) rule.match = TabConfig.Match.CONTAINS;
				if (rule.pattern == null) rule.pattern = "";
			}
			if (tab.switchCommand == null) {
				// Configs vor 1.3.0: für die Standard-Kanäle den passenden Cubeside-Befehl eintragen.
				String tag = tab.match == TabConfig.Match.TAG ? tab.pattern.trim() : "";
				tab.switchCommand = switch (tag) {
					case "[G]" -> "/ch global";
					case "[H]" -> "/ch handel";
					default -> "";
				};
			}
			if (tab.pattern == null) tab.pattern = "";
			if (tab.sendPrefix == null) tab.sendPrefix = "";
		}
		return this;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("chatlanes.json");
	}

	public static ChatLanesConfig load() {
		return load(file());
	}

	/** Lädt ein Profil aus einer Datei; fehlt sie, wird ein Standardprofil angelegt. */
	public static ChatLanesConfig load(Path path) {
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				ChatLanesConfig config = GSON.fromJson(reader, ChatLanesConfig.class);
				if (config != null) {
					return config.sanitize();
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.error("Konnte {} nicht lesen, verwende Standardwerte", path, e);
				return createDefault();
			}
		}

		ChatLanesConfig config = createDefault();
		config.save(path);
		return config;
	}

	public static Path standardFile() {
		return file();
	}

	public void save() {
		this.save(file());
	}

	public void save(Path path) {
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
