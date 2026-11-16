package de.chattabs;

import de.chattabs.config.ChatTabsConfig.TabConfig;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Ein Tab zur Laufzeit: entweder aus der Config (System/Kanal/Alle) oder ein automatisch erzeugter Msg-Tab. */
public final class ChatTab {
	public enum Kind { SYSTEM, CHANNEL, ALL, PRIVATE }

	private final Kind kind;
	private final @Nullable TabConfig config;
	private final @Nullable String player;
	private final @Nullable Pattern pattern;
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
		Pattern pattern = kind == Kind.CHANNEL ? ChatTabs.compile(config.pattern) : null;
		return new ChatTab(kind, config, null, pattern);
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

	void clearUnread() {
		this.unread = 0;
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
