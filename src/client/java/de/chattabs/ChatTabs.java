package de.chattabs;

import com.google.common.collect.MapMaker;
import de.chattabs.config.ChatTabsConfig;
import de.chattabs.config.ChatTabsConfig.TabConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Zentrale Verwaltung der Tabs: Zuordnung der Nachrichten, ungelesene Nachrichten, aktiver Tab. */
public final class ChatTabs {
	private static final Logger LOGGER = LoggerFactory.getLogger("chattabs");
	private static final ChatTabs INSTANCE = new ChatTabs();

	/** Ergebnis der Zuordnung einer Nachricht. channel == null und player == null bedeutet "System". */
	private record Classification(@Nullable ChatTab channel, @Nullable String player) {
		static final Classification SYSTEM = new Classification(null, null);
	}

	private ChatTabsConfig config = ChatTabsConfig.createDefault();
	private final List<ChatTab> configTabs = new ArrayList<>();
	private final Map<String, ChatTab> privateTabs = new LinkedHashMap<>();
	private @Nullable Pattern pmPattern;
	private List<String> pmGroups = List.of();
	private @Nullable ChatTab active;
	/** Schwache Identitäts-Map: Nachrichten, die aus dem Chat fliegen, werden automatisch vergessen. */
	private ConcurrentMap<GuiMessage, Classification> cache = new MapMaker().weakKeys().makeMap();

	private ChatTabs() {
	}

	public static ChatTabs get() {
		return INSTANCE;
	}

	public ChatTabsConfig config() {
		return this.config;
	}

	static @Nullable Pattern compile(@Nullable String regex) {
		if (regex == null || regex.isEmpty()) {
			return null;
		}
		try {
			return Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			LOGGER.warn("Ungültiger regulärer Ausdruck '{}': {}", regex, e.getDescription());
			return null;
		}
	}

	/** Übernimmt eine (neue) Konfiguration und baut die Tabs neu auf. */
	public void apply(ChatTabsConfig newConfig) {
		String activeName = this.active == null ? null : this.active.name();
		this.config = newConfig;
		this.configTabs.clear();
		for (TabConfig tab : newConfig.tabs) {
			this.configTabs.add(ChatTab.fromConfig(tab));
		}

		this.pmPattern = newConfig.pmTabs ? compile(newConfig.pmPattern) : null;
		List<String> groups = new ArrayList<>();
		if (this.pmPattern != null) {
			this.pmPattern.namedGroups().entrySet().stream()
				.filter(e -> e.getKey().startsWith("player"))
				.sorted(Map.Entry.comparingByValue())
				.forEach(e -> groups.add(e.getKey()));
		}
		this.pmGroups = groups;
		if (!newConfig.pmTabs) {
			this.privateTabs.clear();
		}

		this.cache = new MapMaker().weakKeys().makeMap();
		this.active = null;
		for (ChatTab tab : this.tabs()) {
			if (tab.name().equals(activeName)) {
				this.active = tab;
				break;
			}
		}
		if (this.active == null) {
			this.active = this.firstTab();
		}
		this.refreshChat();
	}

	public List<ChatTab> tabs() {
		if (this.privateTabs.isEmpty()) {
			return Collections.unmodifiableList(this.configTabs);
		}
		List<ChatTab> all = new ArrayList<>(this.configTabs);
		all.addAll(this.privateTabs.values());
		return all;
	}

	private @Nullable ChatTab firstTab() {
		List<ChatTab> tabs = this.tabs();
		return tabs.isEmpty() ? null : tabs.getFirst();
	}

	public @Nullable ChatTab active() {
		return this.active;
	}

	public boolean hasBlinkingUnread() {
		for (ChatTab tab : this.tabs()) {
			if (tab != this.active && tab.unread() > 0 && tab.blinks()) {
				return true;
			}
		}
		return false;
	}

	public void setActive(ChatTab tab) {
		tab.clearUnread();
		if (this.active != tab) {
			this.active = tab;
			this.refreshChat();
		}
	}

	public void closePrivateTab(ChatTab tab) {
		if (!tab.isPrivate()) {
			return;
		}
		this.privateTabs.remove(tab.player().toLowerCase(Locale.ROOT));
		if (this.active == tab) {
			this.active = this.firstTab();
			this.refreshChat();
		}
	}

	/** Lässt den Vanilla-Chat die sichtbaren Zeilen neu aufbauen. */
	private void refreshChat() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.gui != null && minecraft.gui.hud != null) {
			minecraft.gui.hud.getChat().rescaleChat();
		}
	}

	private Classification classify(GuiMessage message) {
		Classification cached = this.cache.get(message);
		if (cached != null) {
			return cached;
		}

		String text = ChatFormatting.stripFormatting(message.content().getString());
		if (text == null) {
			text = "";
		}

		Classification result = Classification.SYSTEM;
		for (ChatTab tab : this.configTabs) {
			Pattern pattern = tab.pattern();
			if (tab.kind() == ChatTab.Kind.CHANNEL && pattern != null && pattern.matcher(text).find()) {
				result = new Classification(tab, null);
				break;
			}
		}

		if (result == Classification.SYSTEM && this.pmPattern != null) {
			Matcher matcher = this.pmPattern.matcher(text);
			if (matcher.find()) {
				String player = null;
				for (String group : this.pmGroups) {
					player = matcher.group(group);
					if (player != null) {
						break;
					}
				}
				if (player != null && !player.isEmpty()) {
					String name = player;
					result = new Classification(null, name);
					this.privateTabs.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> ChatTab.privateTab(name));
				}
			}
		}

		this.cache.put(message, result);
		return result;
	}

	private static boolean belongsTo(Classification c, ChatTab tab) {
		return switch (tab.kind()) {
			case ALL -> true;
			case SYSTEM -> c.channel() == null && c.player() == null;
			case CHANNEL -> c.channel() == tab;
			case PRIVATE -> c.player() != null && c.player().equalsIgnoreCase(tab.player());
		};
	}

	/** Soll die Nachricht im aktuell gewählten Tab angezeigt werden? */
	public boolean isVisible(GuiMessage message) {
		Classification c = this.classify(message);
		if (this.active == null) {
			return true;
		}
		return belongsTo(c, this.active);
	}

	/** Wird für jede neu empfangene Nachricht aufgerufen und zählt ungelesene Nachrichten. */
	public void onNewMessage(GuiMessage message) {
		Classification c = this.classify(message);
		for (ChatTab tab : this.tabs()) {
			if (tab != this.active && belongsTo(c, tab)) {
				tab.addUnread();
			}
		}
	}

	/** Gelöschte Nachrichten (Moderation) bleiben im selben Tab. */
	public void copyClassification(GuiMessage from, GuiMessage to) {
		Classification c = this.cache.get(from);
		if (c != null) {
			this.cache.put(to, c);
		}
	}

	public void onClear(boolean disconnect) {
		for (ChatTab tab : this.tabs()) {
			tab.clearUnread();
		}
		if (disconnect && this.config.pmCloseOnDisconnect && !this.privateTabs.isEmpty()) {
			this.privateTabs.clear();
			if (this.active != null && this.active.isPrivate()) {
				this.active = this.firstTab();
			}
		}
	}

	/** Liefert den zu sendenden Text für den aktiven Tab oder null, wenn die Nachricht unverändert gesendet wird. */
	public @Nullable String transformOutgoing(String message) {
		if (this.active == null) {
			return null;
		}
		return this.active.transformOutgoing(message, this.config.pmSendCommand);
	}
}
