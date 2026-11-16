package de.chattabs;

import de.chattabs.config.ChatTabsConfig.TabConfig;
import de.chattabs.config.TabColor;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Ein Tab zur Laufzeit: entweder aus der Config (System/Kanal/Alle) oder ein automatisch erzeugter Msg-Tab. */
public final class ChatTab {
	public enum Kind { SYSTEM, CHANNEL, ALL, PRIVATE }

	private final Kind kind;
	private final @Nullable TabConfig config;
	private final @Nullable String player;
	private final @Nullable Pattern pattern;
	/** Zusätzliche Regeln (nur für Nachrichten, die sonst im System-Tab landen würden). */
	private final java.util.List<Pattern> extraPatterns = new java.util.ArrayList<>();
	/** Befehle (klein, ohne "/"), deren Ausgabe in diesen Kanal kommt, z. B. "where". */
	private final java.util.Map<String, Integer> extraCommands = new java.util.HashMap<>();
	private int unread;

	private ChatTab(Kind kind, @Nullable TabConfig config, @Nullable String player, @Nullable Pattern pattern) {
		this.kind = kind;
		this.config = config;
		this.player = player;
		this.pattern = pattern;
	}

	static ChatTab fromConfig(TabConfig config) {
		Kind kind = switch (config.type) {
			case SYSTEM -> Kind.SYSTEM;
			case ALL -> Kind.ALL;
			case CHANNEL -> Kind.CHANNEL;
		};
		Pattern pattern = kind == Kind.CHANNEL ? buildPattern(config.match, config.pattern) : null;
		ChatTab tab = new ChatTab(kind, config, null, pattern);
		if (kind == Kind.CHANNEL) {
			for (de.chattabs.config.ChatTabsConfig.Rule rule : config.extraRulesOrEmpty()) {
				if (rule.match == TabConfig.Match.COMMAND) {
					String command = commandName(rule.pattern);
					if (!command.isEmpty()) {
						tab.extraCommands.merge(command, rule.secondsOrDefault(), Math::max);
					}
					continue;
				}
				Pattern extra = buildPattern(rule.match, rule.pattern);
				if (extra != null) {
					tab.extraPatterns.add(extra);
				}
			}
		}
		return tab;
	}

	/** Baut aus Erkennungsart und Wert den regulären Ausdruck. Wird auch vom Live-Test im Menü benutzt. */
	public static @Nullable Pattern buildPattern(TabConfig.Match match, String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		return switch (match == null ? TabConfig.Match.REGEX : match) {
			// Beliebig viele "[Irgendwas] "-Präfixe (z. B. "[Freebuild] ") überspringen, dann muss die Kennung kommen.
			case TAG -> Pattern.compile("^(?:\\[[^\\]]*\\]\\s*)*?" + Pattern.quote(value.trim()));
			case CONTAINS -> Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			case REGEX -> ChatTabs.compile(value);
			case COMMAND -> null; // wird über den gesendeten Befehl erkannt, nicht über den Text
		};
	}

	public int color() {
		if (this.isPrivate()) {
			return 0xFFFF55FF;
		}
		return this.config.color == null ? TabColor.DEFAULT.argb() : this.config.color.argb();
	}

	static ChatTab privateTab(String player) {
		return new ChatTab(Kind.PRIVATE, null, player, null);
	}

	public Kind kind() {
		return this.kind;
	}

	public boolean isPrivate() {
		return this.kind == Kind.PRIVATE;
	}

	public @Nullable TabConfig config() {
		return this.config;
	}

	public @Nullable String player() {
		return this.player;
	}

	/** "/Where Spieler" → "where" */
	public static String commandName(String input) {
		String command = input == null ? "" : input.trim();
		if (command.startsWith("/")) {
			command = command.substring(1);
		}
		int space = command.indexOf(' ');
		return (space >= 0 ? command.substring(0, space) : command).toLowerCase(java.util.Locale.ROOT);
	}

	/** Sekunden, die die Ausgabe dieses Befehls eingefangen wird, oder 0, wenn der Tab den Befehl nicht einfängt. */
	int captureSeconds(String command) {
		return this.extraCommands.getOrDefault(command, 0);
	}

	/** Passt eine der zusätzlichen Regeln? */
	boolean matchesExtra(String text) {
		for (Pattern extra : this.extraPatterns) {
			if (extra.matcher(text).find()) {
				return true;
			}
		}
		return false;
	}

	@Nullable Pattern pattern() {
		return this.pattern;
	}

	public String name() {
		return this.isPrivate() ? "@" + this.player : this.config.name;
	}

	public int unread() {
		return this.unread;
	}

	void addUnread() {
		this.unread++;
	}

	void setUnread(int unread) {
		this.unread = unread;
	}

	void clearUnread() {
		this.unread = 0;
	}

	/** Soll hinter dem Namen "(Anzahl)" stehen? Msg-Tabs folgen der globalen Einstellung. */
	public boolean showsUnreadCount() {
		return this.isPrivate() || this.config.showsUnread();
	}

	public boolean blinks() {
		if (this.isPrivate()) {
			return ChatTabs.get().config().pmBlink;
		}
		return this.config.blink;
	}

	/** Text, der beim Senden aus diesem Tab verwendet wird, oder null wenn normal gesendet werden soll. */
	@Nullable String transformOutgoing(String message, String pmCommand) {
		if (this.isPrivate()) {
			if (pmCommand.isBlank()) {
				return null;
			}
			return pmCommand.replace("{player}", this.player).replace("{message}", message);
		}
		if (this.config.sendPrefix.isEmpty()) {
			return null;
		}
		return this.config.sendPrefix + message;
	}
}
