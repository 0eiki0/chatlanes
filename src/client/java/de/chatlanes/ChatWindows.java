package de.chatlanes;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import de.chatlanes.config.ChatLanesConfig.WindowLayout;
import de.chatlanes.config.TabColor;
import de.chatlanes.mixin.ChatComponentAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Eigene Chatfenster: Tabs mit "Als Fenster anzeigen" werden nicht in der Tab-Leiste angezeigt,
 * sondern ständig in einem eigenen Kasten. Bei offenem Chat lassen sich die Fenster an der
 * Titelleiste verschieben, an der Ecke unten rechts in der Größe ändern und mit dem Mausrad scrollen.
 */
public final class ChatWindows {
	private static final ChatWindows INSTANCE = new ChatWindows();

	/** Höhe der Titelleiste (nur bei offenem Chat sichtbar). */
	public static final int TITLE_HEIGHT = 11;
	/** Größe des Griffs zum Ändern der Größe (unten rechts). */
	private static final int GRIP = 7;
	/** Innenabstand links/rechts. */
	private static final int PAD = 3;
	private static final int MIN_WIDTH = 60;
	private static final int MIN_HEIGHT = TITLE_HEIGHT + 20;
	/** So viele Zeilen merkt sich ein Fenster höchstens. */
	private static final int MAX_LINES = 5000;

	private static final int TITLE_BG = 0xD0101010;
	private static final int TITLE_BG_HOVER = 0xE0303030;
	private static final int OUTLINE = 0x60FFFFFF;
	private static final int OUTLINE_ACTIVE = 0xFF55FF55;
	private static final int GRIP_COLOR = 0xA0FFFFFF;
	private static final int HIGHLIGHT = 0x50FFFF55;

	/** Ein Fenster zur Laufzeit. */
	public static final class Window {
		final ChatTab tab;
		/** Sichtbare Zeilen, neueste zuerst (wie im Vanilla-Chat). */
		final List<GuiMessage.Line> lines = new ArrayList<>();
		int scroll;
		boolean dirty = true;
		/** Breite (in skalierten Einheiten), für die die Zeilen umgebrochen wurden. */
		int builtWidth = -1;

		Window(ChatTab tab) {
			this.tab = tab;
		}

		public ChatTab tab() {
			return this.tab;
		}

		WindowLayout layout() {
			return this.tab.config().windowLayoutOrDefault();
		}
	}

	/** Lage eines Fensters auf dem Bildschirm (GUI-Pixel). */
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
		}

		boolean inTitle(double mx, double my) {
			return this.contains(mx, my) && my < this.y + TITLE_HEIGHT;
		}

		boolean inGrip(double mx, double my) {
			return this.contains(mx, my) && mx >= this.x + this.w - GRIP && my >= this.y + this.h - GRIP;
		}
	}

	/** Maße der Textzeilen eines Fensters. */
	private record Metrics(float scale, int lineHeight, int textOffset, int innerWidth, int contentTop, int contentBottom, int maxLines) {
	}

	private enum DragMode { MOVE, RESIZE }

	private final Map<String, Window> windows = new LinkedHashMap<>();
	/** true, solange ChatLanes selbst den Chat neu aufbaut (Tab-Wechsel) – dann bleiben die Fenster, wie sie sind. */
	private boolean ownRefresh;

	private @Nullable Window dragging;
	private DragMode dragMode = DragMode.MOVE;
	private double grabX;
	private double grabY;
	private Rect grabRect = new Rect(0, 0, 0, 0);

	private ChatWindows() {
	}

	public static ChatWindows get() {
		return INSTANCE;
	}

	/** Neue Konfiguration: Fenster neu anlegen (die Zeilen werden beim nächsten Zeichnen aufgebaut). */
	void setTabs(List<ChatTab> tabs) {
		Map<String, Integer> scrolls = new java.util.HashMap<>();
		for (Window window : this.windows.values()) {
			scrolls.put(window.tab.name(), window.scroll);
		}
		this.windows.clear();
		this.dragging = null;
		for (ChatTab tab : tabs) {
			Window window = new Window(tab);
			window.scroll = scrolls.getOrDefault(tab.name(), 0);
			this.windows.putIfAbsent(tab.name(), window);
		}
	}

	public boolean isEmpty() {
		return this.windows.isEmpty();
	}

	void setOwnRefresh(boolean ownRefresh) {
		this.ownRefresh = ownRefresh;
	}

	/** Der Vanilla-Chat baut seine Zeilen neu auf (Sprache, Schrift, gelöschte Nachricht …) → Fenster auch. */
	public void onChatRefresh() {
		if (!this.ownRefresh) {
			this.markDirty();
		}
	}

	public void markDirty() {
		for (Window window : this.windows.values()) {
			window.dirty = true;
		}
	}

	/** Neue Nachricht: in alle passenden Fenster eintragen. */
	void onNewMessage(GuiMessage message) {
		if (this.windows.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		for (Window window : this.windows.values()) {
			if (window.dirty || window.builtWidth <= 0 || !ChatLanes.get().belongsToWindow(message, window.tab)) {
				continue;
			}
			int added = addLines(window, message, font, window.builtWidth);
			// Beim Zurückscrollen bleibt die Ansicht an derselben Stelle stehen
			if (window.scroll > 0) {
				window.scroll += added;
			}
			trim(window);
		}
	}

	private static int addLines(Window window, GuiMessage message, Font font, int width) {
		net.minecraft.network.chat.Component display = ChatLanes.get().displayContent(message);
		GuiMessage decorated = display == message.content()
			? message
			: new GuiMessage(message.addedTime(), display, message.signature(), message.source(), message.tag());
		List<FormattedCharSequence> split = decorated.splitLines(font, width);
		for (int i = 0; i < split.size(); i++) {
			// Elternnachricht bleibt das Original – für Suche, Hervorheben und "Filter erstellen"
			window.lines.addFirst(new GuiMessage.Line(message, split.get(i), i == split.size() - 1));
		}
		return split.size();
	}

	private static void trim(Window window) {
		while (window.lines.size() > MAX_LINES) {
			window.lines.removeLast();
		}
	}

	private static void rebuild(Window window, Font font, int width) {
		window.lines.clear();
		window.builtWidth = width;
		window.dirty = false;
		List<GuiMessage> all = ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).chatlanes$getAllMessages();
		for (GuiMessage message : Lists.reverse(List.copyOf(all))) {
			if (ChatLanes.get().belongsToWindow(message, window.tab)) {
				addLines(window, message, font, width);
			}
		}
		trim(window);
		window.scroll = Mth.clamp(window.scroll, 0, Math.max(0, window.lines.size() - 1));
	}

	// ---------------------------------------------------------------- Geometrie

	private static Rect rect(Window window, int screenWidth, int screenHeight) {
		WindowLayout layout = window.layout();
		int w = Mth.clamp((int) Math.round(layout.width * screenWidth), Math.min(MIN_WIDTH, screenWidth), screenWidth);
		int h = Mth.clamp((int) Math.round(layout.height * screenHeight), Math.min(MIN_HEIGHT, screenHeight), screenHeight);
		int x = Mth.clamp((int) Math.round(layout.x * screenWidth), 0, screenWidth - w);
		int y = Mth.clamp((int) Math.round(layout.y * screenHeight), 0, screenHeight - h);
		return new Rect(x, y, w, h);
	}

	private static Metrics metrics(Window window, Rect rect) {
		Minecraft minecraft = Minecraft.getInstance();
		Integer percent = window.layout().textScale;
		float scale = percent == null ? minecraft.options.chatScale().get().floatValue() : percent / 100.0F;
		scale = Math.max(0.25F, scale);
		double spacing = minecraft.options.chatLineSpacing().get();
		int lineHeight = (int) (9.0 * (spacing + 1.0));
		int textOffset = (int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing);
		int innerWidth = Math.max(10, Mth.floor((rect.w() - PAD * 2) / scale));
		// Die Titelleiste ist nur bei offenem Chat zu sehen – die Zeilen bleiben trotzdem an derselben Stelle,
		// deshalb wird ihr Platz immer freigehalten.
		int contentTop = rect.y() + TITLE_HEIGHT;
		int contentBottom = rect.y() + rect.h() - 1;
		int maxLines = Math.max(1, (int) Math.floor((contentBottom - contentTop) / (lineHeight * scale)));
		return new Metrics(scale, lineHeight, textOffset, innerWidth, contentTop, contentBottom, maxLines);
	}

	/** Sichtbarkeit einer Zeile bei geschlossenem Chat (oder 0 = Fenster ganz ausblenden). */
	private static float lineAlpha(Window window, GuiMessage.Line line) {
		Integer seconds = window.layout().displaySeconds;
		if (seconds == null) {
			return ChatLook.alpha(line, vanillaAlpha(line));
		}
		if (seconds == WindowLayout.ALWAYS) {
			return 1.0F;
		}
		if (seconds == WindowLayout.FOCUSED_ONLY) {
			return 0.0F;
		}
		return ChatLook.alpha(line, seconds, ChatLanes.get().config().smoothFade ? ChatLanes.get().config().fadeOutSeconds : 0.0);
	}

	/** Wie der Vanilla-Chat: 10 s sichtbar, dann kurz ausblenden. */
	private static float vanillaAlpha(GuiMessage.Line line) {
		int age = Minecraft.getInstance().gui.hud.getGuiTicks() - line.addedTime();
		double t = Mth.clamp((1.0 - age / 200.0) * 10.0, 0.0, 1.0);
		return (float) (t * t);
	}

	// ---------------------------------------------------------------- Zeichnen

	public void render(GuiGraphicsExtractor graphics, Font font, boolean focused, int mouseX, int mouseY) {
		if (this.windows.isEmpty() || !ChatLanes.get().isEnabled()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
			return;
		}
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		if (!focused) {
			this.dragging = null;
		} else if (this.dragging != null) {
			this.updateDrag(mouseX, mouseY, screenWidth, screenHeight);
		}

		// Das Fenster unter der Maus bzw. das gezogene Fenster wird zuletzt (also oben) gezeichnet.
		@Nullable Window top = focused ? this.windowAt(mouseX, mouseY, screenWidth, screenHeight) : null;
		if (this.dragging != null) {
			top = this.dragging;
		}
		for (Window window : this.windows.values()) {
			if (window != top) {
				this.renderWindow(graphics, font, window, focused, false, mouseX, mouseY, screenWidth, screenHeight);
			}
		}
		if (top != null) {
			this.renderWindow(graphics, font, top, focused, true, mouseX, mouseY, screenWidth, screenHeight);
		}
	}

	private void renderWindow(GuiGraphicsExtractor graphics, Font font, Window window, boolean focused, boolean hovered,
			int mouseX, int mouseY, int screenWidth, int screenHeight) {
		Minecraft minecraft = Minecraft.getInstance();
		WindowLayout layout = window.layout();
		if (!focused && layout.displaySeconds != null && layout.displaySeconds == WindowLayout.FOCUSED_ONLY) {
			return;
		}
		Rect rect = rect(window, screenWidth, screenHeight);
		Metrics m = metrics(window, rect);
		if (window.dirty || window.builtWidth != m.innerWidth()) {
			rebuild(window, font, m.innerWidth());
		}
		int maxScroll = Math.max(0, window.lines.size() - m.maxLines());
		window.scroll = Mth.clamp(window.scroll, 0, maxScroll);
		if (!focused) {
			window.scroll = 0;
		}

		float backgroundOpacity = layout.opacity != null
			? layout.opacity / 100.0F
			: minecraft.options.textBackgroundOpacity().get().floatValue();
		float textOpacity = minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
		int tabColor = window.tab.color() == TabColor.DEFAULT.argb() ? 0xFFFFFFFF : window.tab.color();

		if (focused) {
			// Ganzer Kasten mit Hintergrund, Titelleiste und Rahmen
			graphics.fill(rect.x(), m.contentTop(), rect.x() + rect.w(), rect.y() + rect.h(), ChatLook.tint(ARGB.black(Math.max(backgroundOpacity, 0.25F))));
			boolean titleHovered = this.dragging == null && rect.inTitle(mouseX, mouseY);
			graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + TITLE_HEIGHT, titleHovered || this.dragging == window ? TITLE_BG_HOVER : TITLE_BG);
			String title = font.plainSubstrByWidth(window.tab.name(), rect.w() - 8);
			graphics.text(font, title, rect.x() + 4, rect.y() + 2, tabColor, true);
			graphics.outline(rect.x(), rect.y(), rect.w(), rect.h(), this.dragging == window ? OUTLINE_ACTIVE : hovered ? OUTLINE : 0x30FFFFFF);
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(rect.x() + PAD, m.contentBottom());
		graphics.pose().scale(m.scale(), m.scale());
		ActiveTextCollector text = graphics.textRenderer(
			focused ? GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR : GuiGraphicsExtractor.HoveredTextEffects.NONE, null);
		ActiveTextCollector.Parameters parameters = text.defaultParameters();
		int padScaled = Mth.ceil(PAD / m.scale());
		GuiMessage highlighted = ChatLanes.get().highlighted();

		for (int i = 0; i < m.maxLines() && window.scroll + i < window.lines.size(); i++) {
			GuiMessage.Line line = window.lines.get(window.scroll + i);
			float alpha = focused ? 1.0F : lineAlpha(window, line);
			if (alpha <= 1.0E-5F) {
				continue;
			}
			int entryBottom = -i * m.lineHeight();
			int entryTop = entryBottom - m.lineHeight();
			if (!focused) {
				graphics.fill(-padScaled, entryTop, m.innerWidth() + padScaled, entryBottom, ChatLook.tint(ARGB.black(alpha * backgroundOpacity)));
			} else if (highlighted != null && line.parent() == highlighted) {
				graphics.fill(-padScaled, entryTop, m.innerWidth() + padScaled, entryBottom, HIGHLIGHT);
			}
			GuiMessageTag tag = line.tag();
			if (tag != null) {
				graphics.fill(-padScaled, entryTop, -padScaled + 2, entryBottom, ARGB.color(alpha * textOpacity, tag.indicatorColor()));
			}
			text.accept(TextAlignment.LEFT, 0, entryBottom - m.textOffset(), parameters.withOpacity(alpha * textOpacity), line.content());
		}

		// Scrollleiste
		if (focused && window.lines.size() > m.maxLines()) {
			int visibleHeight = m.maxLines() * m.lineHeight();
			int total = window.lines.size() * m.lineHeight();
			int barHeight = Math.max(4, visibleHeight * visibleHeight / total);
			int barBottom = -(window.scroll * visibleHeight / window.lines.size());
			int x = m.innerWidth() + padScaled - 2;
			int alpha = window.scroll > 0 ? 170 : 96;
			graphics.fill(x, barBottom - barHeight, x + 2, barBottom, ARGB.color(alpha, 0x3333AA));
		}
		graphics.pose().popMatrix();

		if (focused) {
			// Griff unten rechts: drei kleine Treppenstufen
			int gx = rect.x() + rect.w() - 2;
			int gy = rect.y() + rect.h() - 2;
			for (int i = 0; i < 3; i++) {
				graphics.fill(gx - 2 - i * 2, gy - 1, gx - 1 - i * 2, gy, GRIP_COLOR);
				graphics.fill(gx - 1, gy - 2 - i * 2, gx, gy - 1 - i * 2, GRIP_COLOR);
			}
			if (this.dragging == null) {
				if (rect.inGrip(mouseX, mouseY)) {
					graphics.requestCursor(CursorTypes.RESIZE_ALL);
				} else if (rect.inTitle(mouseX, mouseY)) {
					graphics.requestCursor(CursorTypes.RESIZE_ALL);
					graphics.setTooltipForNextFrame(font,
						font.split(net.minecraft.network.chat.Component.translatable("chatlanes.window.title_tooltip"), 170), mouseX, mouseY);
				}
			}
		}
	}

	// ---------------------------------------------------------------- Maus

	private @Nullable Window windowAt(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		Window found = null;
		for (Window window : this.windows.values()) {
			// Das zuletzt gezeichnete (oberste) Fenster gewinnt
			if (rect(window, screenWidth, screenHeight).contains(mouseX, mouseY)) {
				found = window;
			}
		}
		return found;
	}

	/** Fenster unter der Maus (für Rechtsklick, Scrollen …), sonst null. */
	private @Nullable Window windowAt(double mouseX, double mouseY) {
		var window = Minecraft.getInstance().getWindow();
		return this.windowAt(mouseX, mouseY, window.getGuiScaledWidth(), window.getGuiScaledHeight());
	}

	/** Nachricht unter der Maus in einem Fenster (für "Filter erstellen"), sonst null. */
	public @Nullable GuiMessage messageAt(double mouseX, double mouseY) {
		if (!ChatLanes.get().isEnabled()) {
			return null;
		}
		var mcWindow = Minecraft.getInstance().getWindow();
		Window window = this.windowAt(mouseX, mouseY);
		if (window == null) {
			return null;
		}
		Rect rect = rect(window, mcWindow.getGuiScaledWidth(), mcWindow.getGuiScaledHeight());
		Metrics m = metrics(window, rect);
		double local = (m.contentBottom() - mouseY) / m.scale();
		if (local < 0) {
			return null;
		}
		int index = (int) (local / m.lineHeight());
		if (index >= m.maxLines() || window.scroll + index >= window.lines.size()) {
			return null;
		}
		return window.lines.get(window.scroll + index).parent();
	}

	/** Liegt die Maus über einem Fenster? */
	public boolean isOverWindow(double mouseX, double mouseY) {
		return ChatLanes.get().isEnabled() && this.windowAt(mouseX, mouseY) != null;
	}

	/**
	 * Klick bei offenem Chat. Linksklick auf die Titelleiste verschiebt, auf den Griff ändert die Größe,
	 * auf Text mit Link/Befehl führt ihn aus.
	 * @return true, wenn der Klick in einem Fenster lag (dann bekommt ihn der normale Chat nicht).
	 */
	public boolean mouseClicked(net.minecraft.client.gui.screens.ChatScreen screen, double mouseX, double mouseY, int button, boolean shift) {
		if (!ChatLanes.get().isEnabled() || this.windows.isEmpty()) {
			return false;
		}
		var mcWindow = Minecraft.getInstance().getWindow();
		int screenWidth = mcWindow.getGuiScaledWidth();
		int screenHeight = mcWindow.getGuiScaledHeight();
		Window window = this.windowAt(mouseX, mouseY, screenWidth, screenHeight);
		if (window == null) {
			return false;
		}
		if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}
		Rect rect = rect(window, screenWidth, screenHeight);
		if (rect.inGrip(mouseX, mouseY) || rect.inTitle(mouseX, mouseY)) {
			this.dragging = window;
			this.dragMode = rect.inGrip(mouseX, mouseY) ? DragMode.RESIZE : DragMode.MOVE;
			this.grabX = mouseX;
			this.grabY = mouseY;
			this.grabRect = rect;
			// Gezogenes Fenster nach oben holen
			this.windows.remove(window.tab.name());
			this.windows.put(window.tab.name(), window);
			return true;
		}
		Style style = this.styleAt(window, rect, mouseX, mouseY, shift);
		if (style != null) {
			((de.chatlanes.mixin.ChatScreenAccessor) screen).chatlanes$handleComponentClicked(style, shift);
		}
		return true;
	}

	/** Anklickbarer Text (Link, Befehl, Einfügen mit Umschalt) an der Mausposition. */
	private @Nullable Style styleAt(Window window, Rect rect, double mouseX, double mouseY, boolean insertions) {
		Metrics m = metrics(window, rect);
		ActiveTextCollector.ClickableStyleFinder finder =
			new ActiveTextCollector.ClickableStyleFinder(Minecraft.getInstance().font, (int) mouseX, (int) mouseY).includeInsertions(insertions);
		Matrix3x2f pose = new Matrix3x2f().translate(rect.x() + PAD, m.contentBottom()).scale(m.scale(), m.scale());
		ActiveTextCollector.Parameters parameters = new ActiveTextCollector.Parameters(pose);
		for (int i = 0; i < m.maxLines() && window.scroll + i < window.lines.size(); i++) {
			GuiMessage.Line line = window.lines.get(window.scroll + i);
			finder.accept(TextAlignment.LEFT, 0, -i * m.lineHeight() - m.textOffset(), parameters, line.content());
		}
		return finder.result();
	}

	/** Mausrad über einem Fenster: dieses Fenster scrollen. */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!ChatLanes.get().isEnabled()) {
			return false;
		}
		Window window = this.windowAt(mouseX, mouseY);
		if (window == null) {
			return false;
		}
		int amount = (int) Math.signum(scrollY) * (Minecraft.getInstance().hasShiftDown() ? 1 : 7);
		window.scroll = Math.max(0, window.scroll + amount);
		return true;
	}

	private void updateDrag(int mouseX, int mouseY, int screenWidth, int screenHeight) {
		Window window = this.dragging;
		if (window == null) {
			return;
		}
		WindowLayout layout = window.layout();
		int dx = (int) Math.round(mouseX - this.grabX);
		int dy = (int) Math.round(mouseY - this.grabY);
		Rect start = this.grabRect;
		if (this.dragMode == DragMode.MOVE) {
			int x = Mth.clamp(start.x() + dx, 0, Math.max(0, screenWidth - start.w()));
			int y = Mth.clamp(start.y() + dy, 0, Math.max(0, screenHeight - start.h()));
			layout.x = x / (double) screenWidth;
			layout.y = y / (double) screenHeight;
		} else {
			int w = Mth.clamp(start.w() + dx, MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - start.x()));
			int h = Mth.clamp(start.h() + dy, MIN_HEIGHT, Math.max(MIN_HEIGHT, screenHeight - start.y()));
			layout.width = w / (double) screenWidth;
			layout.height = h / (double) screenHeight;
			layout.x = start.x() / (double) screenWidth;
			layout.y = start.y() / (double) screenHeight;
		}
		var pressed = ((de.chatlanes.mixin.MouseHandlerAccessor) Minecraft.getInstance().mouseHandler).chatlanes$getActiveButton();
		if (pressed == null || pressed.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			// Losgelassen → Position im Profil speichern
			this.dragging = null;
			layout.clamp();
			ServerProfiles.saveActiveQuietly();
		}
	}

	/** Zur Nachricht scrollen (Suche), damit sie in der Mitte des Fensters steht. */
	void scrollTo(ChatTab tab, GuiMessage message) {
		Window window = this.windows.get(tab.name());
		if (window == null) {
			return;
		}
		var mcWindow = Minecraft.getInstance().getWindow();
		Rect rect = rect(window, mcWindow.getGuiScaledWidth(), mcWindow.getGuiScaledHeight());
		Metrics m = metrics(window, rect);
		if (window.dirty || window.builtWidth != m.innerWidth()) {
			rebuild(window, Minecraft.getInstance().font, m.innerWidth());
		}
		for (int i = 0; i < window.lines.size(); i++) {
			if (window.lines.get(i).parent() == message) {
				window.scroll = Math.max(0, i - m.maxLines() / 2);
				return;
			}
		}
	}

	/** Standard-Lage für das n-te neue Fenster: rechts oben, untereinander. */
	public static WindowLayout defaultLayout(int index) {
		WindowLayout layout = new WindowLayout();
		int column = index / 3;
		layout.x = Math.max(0.0, 0.62 - column * 0.37);
		layout.y = 0.04 + (index % 3) * 0.26;
		return layout;
	}
}
