package de.chatlanes;

import com.google.common.collect.MapMaker;
import de.chatlanes.config.ChatLanesConfig;
import de.chatlanes.config.ChatLanesConfig.TabConfig;
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
public final class ChatLanes {
	private static final Logger LOGGER = LoggerFactory.getLogger("chatlanes");
	private static final ChatLanes INSTANCE = new ChatLanes();

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
	private @Nullable String captureTab;
	private long captureUntil;
	/** Per Befehl-Ausgabe zugeordnete Nachrichten → Tab-Name (bleibt auch nach "Speichern" der Einstellungen). */
	private final ConcurrentMap<GuiMessage, String> captured = new MapMaker().weakKeys().makeMap();
	/** Beim Wiederherstellen: Tab-Name der gerade geladenen Nachricht (aus dem gespeicherten Verlauf). */
	private @Nullable String restoreCapturedTab;

	/** Zuletzt (automatisch oder von Hand) gesendeter Kanalwechsel, z. B. "/ch global". */
	private @Nullable String lastSwitchCommand;
	private long lastSwitchTime;

	private ChatLanesConfig config = ChatLanesConfig.createDefault();
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

	private ChatLanes() {
	}

	public static ChatLanes get() {
		return INSTANCE;
	}

	public ChatLanesConfig config() {
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

	/** Ist ChatLanes gerade aktiv? (aus = Chat wie in normalem Minecraft) */
	private boolean enabled = true;

	public boolean isEnabled() {
		return this.enabled;
	}

	/** An/Aus für den aktuellen Server – baut den sichtbaren Chat sofort neu auf. */
	public void setEnabled(boolean enabled) {
		if (this.enabled != enabled) {
			this.enabled = enabled;
			this.peekReturnTab = null;
			this.refreshChat();
		}
	}

	/** Profil wechseln (anderer Server): Tabs neu, Standard-Tab des Profils wählen. */
	public void applyProfile(ChatLanesConfig newConfig, boolean enabled) {
		this.enabled = enabled;
		this.active = null;
		this.peekReturnTab = null;
		this.apply(newConfig);
	}

	/** Übernimmt eine (neue) Konfiguration und baut die Tabs neu auf. */
	public void apply(ChatLanesConfig newConfig) {
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
			// Erster Aufruf (Spielstart) oder der aktive Tab existiert nicht mehr → Standard-Tab
			this.active = this.defaultTabOrFirst();
		}
		this.refreshChat();
	}

	/** Der eingestellte Standard-Tab, sonst der erste Tab. */
	public @Nullable ChatTab defaultTabOrFirst() {
		String name = this.config.defaultTab;
		if (name != null && !name.isBlank()) {
			for (ChatTab tab : this.configTabs) {
				if (tab.name().equals(name)) {
					return tab;
				}
			}
		}
		return this.firstTab();
	}

	// ---------------------------------------------------------------- System-Ausgabe kurz anzeigen

	/** true, solange die Mod selbst einen Kanalwechsel-Befehl sendet (löst kein "System anzeigen" aus). */
	private boolean sendingOwnSwitch;
	/** Bis zu diesem Zeitpunkt gilt eine System-Nachricht als Antwort auf deine Aktion. */
	private long actionUntil;
	/** Tab, in den nach dem kurzen Anzeigen zurückgesprungen wird (null = gerade nicht aktiv). */
	private @Nullable String peekReturnTab;
	private long peekUntil;
	private boolean pendingRefresh;
	/** Nachrichten, die im Tab ankamen, in den zurückgesprungen wird – während die Ansicht im System-Tab war. */
	private final List<GuiMessage> missedDuringPeek = new ArrayList<>();
	/** Verpasste Nachrichten → Spiel-Tick, ab dem sie bei geschlossenem Chat neu eingeblendet werden. */
	private final ConcurrentMap<GuiMessage, Integer> replayTick = new MapMaker().weakKeys().makeMap();
	/** Kopien mit neuer Einblendzeit → Original (für Suche/Hervorheben). */
	private final ConcurrentMap<GuiMessage, GuiMessage> replayOriginal = new MapMaker().weakKeys().makeMap();

	/**
	 * Wird beim Aufbau der sichtbaren Zeilen benutzt: verpasste Nachrichten bekommen eine Kopie mit
	 * frischer Einblendzeit, damit sie bei geschlossenem Chat noch einmal erscheinen.
	 */
	public GuiMessage displayParent(GuiMessage message) {
		Integer tick = this.replayTick.get(message);
		if (tick == null || tick <= message.addedTime()) {
			return message;
		}
		GuiMessage copy = new GuiMessage(tick, message.content(), message.signature(), message.source(), message.tag());
		this.replayOriginal.put(copy, message);
		return copy;
	}

	/** Original einer (evtl. neu eingeblendeten) Nachricht. */
	public GuiMessage originalOf(GuiMessage message) {
		return this.replayOriginal.getOrDefault(message, message);
	}

	/** Eine eigene Aktion (Befehl oder Block-Klick) – Antworten darauf dürfen kurz den System-Tab zeigen. */
	public void onAction() {
		if (!this.enabled) {
			return;
		}
		if (this.config.peekSystem) {
			this.actionUntil = System.currentTimeMillis() + Math.round(this.config.peekReaction * 1000.0);
		}
	}

	/** Block-Klick: zählt als Aktion, wenn eingeschaltet und (falls eingestellt) das passende Item in der Hand ist. */
	public void onBlockClick(String heldItemId) {
		if (!this.config.peekSystem || !this.config.peekBlockClicks) {
			return;
		}
		String items = this.config.peekItems.trim();
		if (!items.isEmpty()) {
			boolean match = false;
			for (String entry : items.split("[,;\\s]+")) {
				String id = entry.trim().toLowerCase(Locale.ROOT);
				if (!id.isEmpty() && (heldItemId.equals(id) || heldItemId.equals("minecraft:" + id))) {
					match = true;
					break;
				}
			}
			if (!match) {
				return;
			}
		}
		this.onAction();
	}

	/** Befehle, die nie den System-Tab zeigen lassen: Wechsel-Befehle der Tabs und die eingestellten Ausnahmen. */
	private boolean isPeekIgnored(String message) {
		String command = ChatTab.commandName(message);
		for (String entry : this.config.peekIgnoredCommands.split("[,;\\s]+")) {
			if (!entry.isBlank() && ChatTab.commandName(entry).equals(command)) {
				return true;
			}
		}
		for (ChatTab tab : this.configTabs) {
			TabConfig tabConfig = tab.config();
			if (tabConfig != null && tabConfig.switchCommand != null && !tabConfig.switchCommand.isBlank()
					&& ChatTab.commandName(tabConfig.switchCommand).equals(command)) {
				return true;
			}
		}
		return false;
	}

	/** Neue System-Nachricht kurz nach einer Aktion → Ansicht in den System-Tab (ohne Kanalwechsel auf dem Server). */
	private void maybePeek(Classification c) {
		if (!this.config.peekSystem || c.channel() != null || c.player() != null) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean peeking = this.peekReturnTab != null;
		if (!peeking && now > this.actionUntil) {
			return;
		}
		ChatTab system = null;
		for (ChatTab tab : this.configTabs) {
			if (tab.kind() == ChatTab.Kind.SYSTEM) {
				system = tab;
				break;
			}
		}
		if (system == null) {
			return;
		}
		if (!peeking) {
			if (this.active == system || this.active == null) {
				return; // du bist schon im System-Tab
			}
			this.peekReturnTab = this.active.name();
			this.active = system;
			this.pendingRefresh = true; // im nächsten Tick neu aufbauen – dann ist die neue Nachricht schon gespeichert
		}
		if (this.active == system) {
			system.clearUnread();
			this.peekUntil = now + this.config.peekSeconds * 1000L;
		}
	}

	/** Nach Ablauf (und bei geschlossenem Chat) zurück in den vorherigen Tab. */
	private void tickPeek(Minecraft minecraft) {
		if (this.pendingRefresh) {
			this.pendingRefresh = false;
			this.refreshChat();
		}
		if (this.peekReturnTab == null || System.currentTimeMillis() < this.peekUntil
				|| minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
			return;
		}
		String name = this.peekReturnTab;
		this.peekReturnTab = null;
		if (this.config.peekReplay && !this.missedDuringPeek.isEmpty()) {
			int now = minecraft.gui.hud.getGuiTicks();
			for (GuiMessage missed : this.missedDuringPeek) {
				this.replayTick.put(missed, now);
			}
		}
		this.missedDuringPeek.clear();
		for (ChatTab tab : this.tabs()) {
			if (tab.name().equals(name)) {
				this.active = tab;
				tab.clearUnread();
				this.refreshChat();
				return;
			}
		}
	}

	// ---------------------------------------------------------------- Suche

	/** Ein Suchtreffer: Nachricht, Text ohne Farben, Empfangszeit und der Tab, in den sie gehört. */
	public record SearchHit(GuiMessage message, String text, long time, @Nullable ChatTab tab) {
	}

	/** Der Tab, in dem eine Nachricht hauptsächlich steht (Kanal, Msg-Tab, sonst System, sonst "Alle"). */
	private @Nullable ChatTab primaryTab(Classification c) {
		if (c.channel() != null) {
			return c.channel();
		}
		if (c.player() != null) {
			ChatTab pm = this.privateTabs.get(c.player().toLowerCase(Locale.ROOT));
			if (pm != null) {
				return pm;
			}
		}
		ChatTab fallback = null;
		for (ChatTab tab : this.tabs()) {
			if (belongsTo(c, tab)) {
				if (tab.kind() == ChatTab.Kind.SYSTEM) {
					return tab;
				}
				if (fallback == null) {
					fallback = tab;
				}
			}
		}
		return fallback;
	}

	/**
	 * Durchsucht alle gespeicherten Nachrichten (neueste zuerst), ohne Groß-/Kleinschreibung.
	 * Mehrere Wörter = alle müssen vorkommen.
	 */
	public List<SearchHit> search(String query, boolean currentTabOnly) {
		List<SearchHit> hits = new ArrayList<>();
		String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
		if (query.isBlank()) {
			return hits;
		}
		List<GuiMessage> messages = ((de.chatlanes.mixin.ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).chatlanes$getAllMessages();
		for (GuiMessage message : List.copyOf(messages)) {
			Classification c = this.classify(message);
			if (c.everywhere() || (currentTabOnly && this.active != null && !belongsTo(c, this.active))) {
				continue;
			}
			String text = ChatFormatting.stripFormatting(message.content().getString());
			if (text == null) {
				continue;
			}
			String lower = text.toLowerCase(Locale.ROOT);
			boolean all = true;
			for (String word : words) {
				if (!lower.contains(word)) {
					all = false;
					break;
				}
			}
			if (all) {
				hits.add(new SearchHit(message, text, this.receivedAt.getOrDefault(message, 0L), this.primaryTab(c)));
			}
		}
		return hits;
	}

	/** Gerade per Suche angesprungene Nachricht – wird kurz hervorgehoben. */
	private @Nullable GuiMessage highlighted;
	private long highlightUntil;

	public @Nullable GuiMessage highlighted() {
		return System.currentTimeMillis() < this.highlightUntil ? this.highlighted : null;
	}

	/**
	 * Springt zu einem Suchtreffer: wechselt (ohne Kanalwechsel auf dem Server) in den passenden Tab,
	 * öffnet den Chat und scrollt so, dass die Nachricht in der Mitte steht.
	 */
	public void jumpTo(SearchHit hit) {
		Minecraft minecraft = Minecraft.getInstance();
		Classification c = this.classify(hit.message());
		if (this.active == null || !belongsTo(c, this.active)) {
			ChatTab tab = hit.tab();
			if (tab != null) {
				this.active = tab;
				tab.clearUnread();
				this.lastActivity = System.currentTimeMillis();
				this.refreshChat();
			}
		}
		minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));

		var chat = minecraft.gui.hud.getChat();
		var accessor = (de.chatlanes.mixin.ChatComponentAccessor) chat;
		List<GuiMessage.Line> lines = accessor.chatlanes$getTrimmedMessages();
		int index = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (this.originalOf(lines.get(i).parent()) == hit.message()) {
				index = i;
				break;
			}
		}
		if (index >= 0) {
			int perPage = chat.getLinesPerPage();
			int max = Math.max(0, lines.size() - perPage);
			accessor.chatlanes$setScrollPos(Math.max(0, Math.min(max, index - perPage / 2)));
		}
		this.highlighted = hit.message();
		this.highlightUntil = System.currentTimeMillis() + 4000L;
	}

	/** Zeitpunkt, zu dem du zuletzt einen Tab gewählt oder etwas geschrieben hast. */
	private long lastActivity = System.currentTimeMillis();

	/**
	 * Wird jeden Tick aufgerufen: nach der eingestellten Zeit ohne eigene Nachricht zurück in den Standard-Tab.
	 * Passiert nur bei geschlossenem Chat, damit dir beim Lesen nichts wegspringt.
	 */
	public void tick(Minecraft minecraft) {
		if (!this.enabled) {
			return;
		}
		this.tickPeek(minecraft);
		if (!this.config.autoReturn || minecraft.player == null || this.active == null || this.peekReturnTab != null) {
			return;
		}
		ChatTab target = this.defaultTabOrFirst();
		if (target == null || target == this.active || minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
			return;
		}
		if (System.currentTimeMillis() - this.lastActivity >= this.config.autoReturnSeconds * 1000L) {
			this.setActive(target);
		}
	}

	/**
	 * Vor einer normalen Chatnachricht: sicherstellen, dass der Server im Kanal des aktiven Tabs ist
	 * (z. B. direkt nach dem Login im Standard-Tab).
	 */
	public void ensureServerChannel() {
		if (this.enabled && this.active != null) {
			this.switchServerChannel(this.active);
		}
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
		this.peekReturnTab = null;
		this.missedDuringPeek.clear();
		this.lastActivity = System.currentTimeMillis();
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
		this.sendingOwnSwitch = true;
		try {
			if (command.startsWith("/")) {
				minecraft.getConnection().sendCommand(command.substring(1));
			} else {
				minecraft.getConnection().sendChat(command);
			}
		} finally {
			this.sendingOwnSwitch = false;
		}
		this.lastSwitchCommand = command;
		this.lastSwitchTime = System.currentTimeMillis();
	}

	/** Merkt sich von Hand eingegebene Kanalwechsel, damit der nächste Tab-Klick wieder korrekt wechselt. */
	public void onCommandSent(String message) {
		if (!this.enabled) {
			return;
		}
		this.lastOwnInput = System.currentTimeMillis();
		this.lastActivity = this.lastOwnInput;
		if (message.startsWith("/") && !this.sendingOwnSwitch && !this.isPeekIgnored(message)) {
			this.onAction();
		}
		if (message.startsWith("/")) {
			String command = ChatTab.commandName(message);
			for (ChatTab tab : this.configTabs) {
				double seconds = tab.kind() == ChatTab.Kind.CHANNEL ? tab.captureSeconds(command) : 0;
				if (seconds > 0) {
					this.captureTab = tab.name();
					this.captureUntil = System.currentTimeMillis() + Math.round(seconds * 1000.0);
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
		if (!this.enabled) {
			return false;
		}
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
		if (!this.enabled) {
			return true;
		}
		Classification c = this.classify(message);
		if (this.active == null) {
			return true;
		}
		return belongsTo(c, this.active);
	}

	/** Wird für jede neu empfangene Nachricht aufgerufen und zählt ungelesene Nachrichten. */
	public void onNewMessage(GuiMessage message) {
		if (!this.enabled) {
			return;
		}
		Classification c = this.classify(message);
		this.reopenIfNewer(c, ChatHistory.get().isRestoring() ? ChatHistory.get().restoreTime() : System.currentTimeMillis());
		if (ChatHistory.get().isRestoring()) {
			return;
		}
		if (c.everywhere()) {
			// Hinweise der Mod (Update-Meldung, Trennlinie): in allen Tabs sichtbar, aber nicht ungelesen/gespeichert/Ton.
			return;
		}
		ChatHistory.get().onMessage(message, this.captured.get(message));
		this.maybePeek(c);
		if (this.peekReturnTab != null) {
			for (ChatTab tab : this.tabs()) {
				if (tab.name().equals(this.peekReturnTab) && tab != this.active && belongsTo(c, tab)) {
					this.missedDuringPeek.add(message);
					break;
				}
			}
		}
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
				de.chatlanes.config.TabSound sound = tabConfig == null ? this.config.pmSoundType : tabConfig.soundOrNone();
				int volume = tabConfig == null ? this.config.pmVolume : tabConfig.volumeOrDefault();
				if (sound != null && sound != de.chatlanes.config.TabSound.NONE) {
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
		if (!this.enabled) {
			return null;
		}
		Classification c = this.classify(message);
		if (c.everywhere()) {
			return null;
		}
		ChatLanesConfig.TimestampMode mode;
		if (c.player() != null) {
			mode = this.config.pmTimestamps;
		} else if (c.channel() != null && c.channel().config() != null) {
			mode = c.channel().config().timestampsOrDefault();
		} else {
			mode = ChatLanesConfig.TimestampMode.DEFAULT;
			for (ChatTab tab : this.configTabs) {
				if (tab.kind() == ChatTab.Kind.SYSTEM && tab.config() != null) {
					mode = tab.config().timestampsOrDefault();
					break;
				}
			}
		}
		boolean show = switch (mode == null ? ChatLanesConfig.TimestampMode.DEFAULT : mode) {
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
		if (!this.enabled) {
			return null;
		}
		if (this.active == null) {
			return null;
		}
		return this.active.transformOutgoing(message, this.config.pmSendCommand);
	}
}
