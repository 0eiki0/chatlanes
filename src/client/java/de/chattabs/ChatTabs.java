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
	/** alsoSystem = per Zusatzregel einem Kanal zugeordnet, soll aber auch im System-Tab erscheinen. */
	private record Classification(@Nullable ChatTab channel, @Nullable String player, boolean everywhere, boolean alsoSystem) {
		static final Classification SYSTEM = new Classification(null, null, false, false);
		static final Classification EVERYWHERE = new Classification(null, null, true, false);

		Classification(@Nullable ChatTab channel, @Nullable String player) {
			this(channel, player, false, false);
		}
	}

	/** Ausgabe eines Befehls einfangen: bis zu diesem Zeitpunkt landet "System"-Ausgabe im Tab captureTab. */
	private static final long COMMAND_OUTPUT_WINDOW = 3000L;
	private @Nullable String captureTab;
	private long captureUntil;
	/** Per Befehl-Ausgabe zugeordnete Nachrichten → Tab-Name (bleibt auch nach "Speichern" der Einstellungen). */
	private final ConcurrentMap<GuiMessage, String> captured = new MapMaker().weakKeys().makeMap();
	/** Beim Wiederherstellen: Tab-Name der gerade geladenen Nachricht (aus dem gespeicherten Verlauf). */
	private @Nullable String restoreCapturedTab;

	/** Zuletzt (automatisch oder von Hand) gesendeter Kanalwechsel, z. B. "/ch global". */
	private @Nullable String lastSwitchCommand;
	private long lastSwitchTime;

	private ChatTabsConfig config = ChatTabsConfig.createDefault();
	private final List<ChatTab> configTabs = new ArrayList<>();
	private final Map<String, ChatTab> privateTabs = new LinkedHashMap<>();
	/**
	 * Msg-Tabs, die der Spieler mit × geschlossen hat (Spielername klein → Zeitpunkt des Schließens).
	 * Ältere Nachrichten (z. B. aus dem gespeicherten Verlauf) öffnen sie nicht wieder,
	 * erst eine neue private Nachricht nach dem Schließen.
	 */
	private final Map<String, Long> closedPrivateTabs = new java.util.HashMap<>();
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
		Map<String, Integer> unread = new java.util.HashMap<>();
		for (ChatTab tab : this.configTabs) {
			unread.merge(tab.name(), tab.unread(), Integer::sum);
		}
		this.config = newConfig;
		this.configTabs.clear();
		for (TabConfig tab : newConfig.tabs) {
			ChatTab created = ChatTab.fromConfig(tab);
			// Ungelesen-Zähler bleiben nach dem Speichern der Einstellungen erhalten.
			created.setUnread(unread.getOrDefault(created.name(), 0));
			this.configTabs.add(created);
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
		this.switchServerChannel(tab);
	}

	/** Schickt den Wechsel-Befehl des Tabs (z. B. "/ch global") an den Server, falls nicht schon in diesem Kanal. */
	private void switchServerChannel(ChatTab tab) {
		TabConfig tabConfig = tab.config();
		if (!this.config.switchChannelOnTab || tabConfig == null || tabConfig.switchCommand == null || tabConfig.switchCommand.isBlank()) {
			return;
		}
		String command = tabConfig.switchCommand.trim();
		if (command.equalsIgnoreCase(this.lastSwitchCommand)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null) {
			return;
		}
		if (command.startsWith("/")) {
			minecraft.getConnection().sendCommand(command.substring(1));
		} else {
			minecraft.getConnection().sendChat(command);
		}
		this.lastSwitchCommand = command;
		this.lastSwitchTime = System.currentTimeMillis();
	}

	/** Merkt sich von Hand eingegebene Kanalwechsel, damit der nächste Tab-Klick wieder korrekt wechselt. */
	public void onCommandSent(String message) {
		this.lastOwnInput = System.currentTimeMillis();
		if (message.startsWith("/")) {
			String command = ChatTab.commandName(message);
			for (ChatTab tab : this.configTabs) {
				if (tab.kind() == ChatTab.Kind.CHANNEL && tab.capturesCommand(command)) {
					this.captureTab = tab.name();
					this.captureUntil = System.currentTimeMillis() + COMMAND_OUTPUT_WINDOW;
					break;
				}
			}
		}
		if (message.startsWith("/")) {
			for (ChatTab tab : this.configTabs) {
				TabConfig tabConfig = tab.config();
				String prefix = tabConfig == null || tabConfig.switchCommand == null ? "" : tabConfig.switchCommand.trim();
				int space = prefix.indexOf(' ');
				// Gleicher Befehl (z. B. "/ch ...") → der Server ist jetzt in einem Kanal, den wir nicht sicher kennen.
				if (space > 0 && message.regionMatches(true, 0, prefix, 0, space + 1)) {
					this.lastSwitchCommand = message.trim();
					return;
				}
			}
		}
	}

	/** Nach Verbindungsaufbau/-abbau ist unbekannt, in welchem Kanal der Server uns führt. */
	public void resetServerChannel() {
		this.lastSwitchCommand = null;
	}

	/** Server-Bestätigung eines automatischen Kanalwechsels ausblenden? */
	public boolean shouldHide(net.minecraft.network.chat.Component message) {
		String regex = this.config.hideSwitchConfirmation;
		if (regex == null || regex.isBlank() || System.currentTimeMillis() - this.lastSwitchTime > 3000L) {
			return false;
		}
		Pattern pattern = compile(regex);
		String text = ChatFormatting.stripFormatting(message.getString());
		return pattern != null && text != null && pattern.matcher(text).find();
	}

	/** Vom gespeicherten Verlauf gesetzt: zu welchem Tab die gerade geladene Befehl-Ausgabe gehört. */
	void setRestoreCapturedTab(@Nullable String tabName) {
		this.restoreCapturedTab = tabName;
	}

	/** Inhalte (z. B. die Trennlinie des wiederhergestellten Verlaufs), die in allen Tabs erscheinen. */
	private final java.util.Set<net.minecraft.network.chat.Component> everywhere =
		java.util.Collections.newSetFromMap(new MapMaker().weakKeys().<net.minecraft.network.chat.Component, Boolean>makeMap());

	public void markEverywhere(net.minecraft.network.chat.Component content) {
		this.everywhere.add(content);
	}

	public void closePrivateTab(ChatTab tab) {
		if (!tab.isPrivate()) {
			return;
		}
		String key = tab.player().toLowerCase(Locale.ROOT);
		this.privateTabs.remove(key);
		this.closedPrivateTabs.put(key, System.currentTimeMillis());
		ChatHistory.get().saveClosedTabs(this.closedPrivateTabs);
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

		if (this.everywhere.contains(message.content())) {
			this.cache.put(message, Classification.EVERYWHERE);
			return Classification.EVERYWHERE;
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
					// Geschlossene Tabs werden hier nicht wieder geöffnet – das entscheidet onNewMessage anhand der Uhrzeit.
					if (!this.closedPrivateTabs.containsKey(name.toLowerCase(Locale.ROOT))) {
						this.privateTabs.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> ChatTab.privateTab(name));
					}
				}
			}
		}

		// Zusätzliche Regeln der Kanäle – nur für Nachrichten, die sonst im System-Tab landen würden.
		if (result == Classification.SYSTEM) {
			for (ChatTab tab : this.configTabs) {
				if (tab.kind() == ChatTab.Kind.CHANNEL && tab.matchesExtra(text)) {
					result = new Classification(tab, null, false, tab.config().extraAlsoInSystem);
					break;
				}
			}
		}

		// Befehl-Ausgabe (z. B. /where): erst gemerkte Zuordnung, sonst ein gerade offenes Zeitfenster.
		if (result == Classification.SYSTEM) {
			String tabName = this.captured.get(message);
			if (tabName == null && ChatHistory.get().isRestoring()) {
				tabName = this.restoreCapturedTab;
			} else if (tabName == null && !ChatHistory.get().isRestoring()
					&& this.captureTab != null && System.currentTimeMillis() <= this.captureUntil) {
				tabName = this.captureTab;
			}
			if (tabName != null) {
				for (ChatTab tab : this.configTabs) {
					if (tab.kind() == ChatTab.Kind.CHANNEL && tab.name().equals(tabName)) {
						this.captured.put(message, tabName);
						result = new Classification(tab, null, false, tab.config().extraAlsoInSystem);
						break;
					}
				}
			}
		}

		this.cache.put(message, result);
		return result;
	}

	private static boolean belongsTo(Classification c, ChatTab tab) {
		if (c.everywhere()) {
			return true;
		}
		return switch (tab.kind()) {
			case ALL -> true;
			case SYSTEM -> (c.channel() == null && c.player() == null) || c.alsoSystem();
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
		this.reopenIfNewer(c, ChatHistory.get().isRestoring() ? ChatHistory.get().restoreTime() : System.currentTimeMillis());
		if (ChatHistory.get().isRestoring()) {
			return;
		}
		ChatHistory.get().onMessage(message, this.captured.get(message));
		for (ChatTab tab : this.tabs()) {
			if (tab != this.active && belongsTo(c, tab)) {
				tab.addUnread();
			}
		}
		this.playSoundFor(c);
	}

	/** Zeitpunkt der letzten eigenen Eingabe – das Server-Echo der eigenen Nachricht soll keinen Ton auslösen. */
	private long lastOwnInput;

	/**
	 * Spielt den Ton des passenden Tabs: Msg-Tab vor Kanal vor System vor Alle.
	 * Kein Ton, wenn du den Tab gerade bei offenem Chat ansiehst, oder direkt nach deiner eigenen Nachricht.
	 */
	private void playSoundFor(Classification c) {
		if (System.currentTimeMillis() - this.lastOwnInput < 1500L) {
			return;
		}
		boolean chatOpen = Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
		for (ChatTab.Kind kind : new ChatTab.Kind[] {ChatTab.Kind.PRIVATE, ChatTab.Kind.CHANNEL, ChatTab.Kind.SYSTEM, ChatTab.Kind.ALL}) {
			for (ChatTab tab : this.tabs()) {
				if (tab.kind() != kind || !belongsTo(c, tab) || (tab == this.active && chatOpen)) {
					continue;
				}
				TabConfig tabConfig = tab.config();
				de.chattabs.config.TabSound sound = tabConfig == null ? this.config.pmSoundType : tabConfig.soundOrNone();
				int volume = tabConfig == null ? this.config.pmVolume : tabConfig.volumeOrDefault();
				if (sound != null && sound != de.chattabs.config.TabSound.NONE) {
					ChatSounds.play(sound, volume);
					return;
				}
			}
		}
	}

	/** Ein mit × geschlossener Msg-Tab öffnet sich erst wieder, wenn eine Nachricht NACH dem Schließen kommt. */
	private void reopenIfNewer(Classification c, long messageTime) {
		if (c.player() == null || !this.config.pmTabs) {
			return;
		}
		String key = c.player().toLowerCase(Locale.ROOT);
		Long closedAt = this.closedPrivateTabs.get(key);
		if (closedAt != null) {
			if (messageTime <= closedAt) {
				return;
			}
			this.closedPrivateTabs.remove(key);
			ChatHistory.get().saveClosedTabs(this.closedPrivateTabs);
		}
		String player = c.player();
		this.privateTabs.computeIfAbsent(key, k -> ChatTab.privateTab(player));
	}

	/** Beim Betreten eines Servers: dort geschlossene Msg-Tabs laden. */
	void setClosedPrivateTabs(Map<String, Long> closed) {
		this.closedPrivateTabs.clear();
		this.closedPrivateTabs.putAll(closed);
		for (String key : closed.keySet()) {
			this.privateTabs.remove(key);
		}
	}

	/** Erkennt den Spielernamen einer privaten Nachricht – für den Live-Test im Menü. */
	public static @Nullable String testPrivateMessage(String regex, String text) {
		Pattern pattern = compile(regex);
		if (pattern == null) {
			return null;
		}
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		return pattern.namedGroups().entrySet().stream()
			.filter(e -> e.getKey().startsWith("player"))
			.sorted(Map.Entry.comparingByValue())
			.map(e -> matcher.group(e.getKey()))
			.filter(name -> name != null && !name.isEmpty())
			.findFirst()
			.orElse(null);
	}

	/** Gelöschte Nachrichten (Moderation) bleiben im selben Tab. */
	public void copyClassification(GuiMessage from, GuiMessage to) {
		Classification c = this.cache.get(from);
		if (c != null) {
			this.cache.put(to, c);
		}
		String capturedTab = this.captured.get(from);
		if (capturedTab != null) {
			this.captured.put(to, capturedTab);
		}
		Long time = this.receivedAt.get(from);
		if (time != null) {
			this.receivedAt.put(to, time);
		}
	}

	/**
	 * Empfangszeit jeder Nachricht (Wanduhr). Wird nie gelöscht, solange die Nachricht existiert –
	 * auch nicht beim Speichern der Einstellungen. Wiederhergestellte Nachrichten bekommen ihre echte Zeit.
	 */
	private final ConcurrentMap<GuiMessage, Long> receivedAt = new MapMaker().weakKeys().makeMap();

	public void recordTime(GuiMessage message) {
		if (!this.receivedAt.containsKey(message)) {
			this.receivedAt.put(message, ChatHistory.get().isRestoring() ? ChatHistory.get().restoreTime() : System.currentTimeMillis());
		}
	}

	/** Uhrzeit-Präfix für diese Nachricht oder null, wenn für ihren Tab keine Uhrzeit angezeigt wird. */
	public net.minecraft.network.chat.@Nullable Component timestampFor(GuiMessage message) {
		Classification c = this.classify(message);
		if (c.everywhere()) {
			return null;
		}
		ChatTabsConfig.TimestampMode mode;
		if (c.player() != null) {
			mode = this.config.pmTimestamps;
		} else if (c.channel() != null && c.channel().config() != null) {
			mode = c.channel().config().timestampsOrDefault();
		} else {
			mode = ChatTabsConfig.TimestampMode.DEFAULT;
			for (ChatTab tab : this.configTabs) {
				if (tab.kind() == ChatTab.Kind.SYSTEM && tab.config() != null) {
					mode = tab.config().timestampsOrDefault();
					break;
				}
			}
		}
		boolean show = switch (mode == null ? ChatTabsConfig.TimestampMode.DEFAULT : mode) {
			case ON -> true;
			case OFF -> false;
			case DEFAULT -> this.config.timestamps;
		};
		if (!show) {
			return null;
		}
		long time = this.receivedAt.getOrDefault(message, System.currentTimeMillis());
		String text = java.time.format.DateTimeFormatter.ofPattern(this.config.timestampFormat.pattern)
			.format(java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()));
		return net.minecraft.network.chat.Component.literal(text + " ").withStyle(ChatFormatting.GRAY);
	}

	public void onClear(boolean disconnect) {
		if (disconnect) {
			ChatHistory.get().onChatCleared();
			this.resetServerChannel();
		}
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
