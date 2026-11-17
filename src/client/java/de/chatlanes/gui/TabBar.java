package de.chatlanes.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.chatlanes.ChatTab;
import de.chatlanes.ChatLanes;
import de.chatlanes.config.ChatLanesConfig;
import de.chatlanes.config.TabColor;
import de.chatlanes.mixin.ChatComponentAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/** Zeichnet die Tab-Leiste über bzw. unter dem Chat und verarbeitet Klicks darauf. */
public final class TabBar {
	private static final int HEIGHT = 12;
	private static final int PADDING = 4;
	private static final int GAP = 2;
	private static final int CLOSE_WIDTH = 9;
	private static final int SEARCH_WIDTH = 12;
	private static final String CLOSE_LABEL = "×";
	private static final int CLOSE_COLOR = 0xFF888888;
	private static final int CLOSE_HOVER = 0xFFFF5555;

	private static final int BG_ACTIVE = 0xE0000000;
	private static final int BG_INACTIVE = 0x90000000;
	private static final int BG_HOVER = 0xB0303030;
	private static final int BG_BLINK = 0xE0E08000;
	private static final int ACCENT = 0xFF55FF55;
	private static final int TEXT_ACTIVE = 0xFFFFFFFF;
	private static final int TEXT_INACTIVE = 0xFFAAAAAA;
	private static final int TEXT_UNREAD = 0xFFFFFF55;
	private static final int TEXT_BLINK = 0xFF000000;

	/** Was ein Eintrag ohne Tab tut. */
	private enum Action { TAB, SEARCH, SETTINGS }

	private record Entry(@Nullable ChatTab tab, String label, int x, int y, int width, boolean closable, Action action, boolean bottom) {
		Entry(@Nullable ChatTab tab, String label, int x, int y, int width, boolean closable, Action action) {
			this(tab, label, x, y, width, closable, action, false);
		}

		Entry(@Nullable ChatTab tab, String label, int x, int y, int width, boolean closable) {
			this(tab, label, x, y, width, closable, Action.TAB);
		}

		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.width && my >= this.y && my < this.y + HEIGHT;
		}

		/** Bereich des "×" am rechten Rand eines Msg-Tabs. */
		boolean closeContains(double mx, double my) {
			return this.closable && this.contains(mx, my) && mx >= this.x + this.width - CLOSE_WIDTH - 1;
		}
	}

	private TabBar() {
	}

	/** Feste Geometrie des Chatfelds in Bildschirmkoordinaten. */
	private record ChatBox(int top, int bottom, int right, double scale, int lineHeight, int lines, int scaledBottom) {
	}

	private static ChatBox chatBox(Minecraft minecraft, int screenHeight) {
		double scale = minecraft.options.chatScale().get();
		int lineHeight = (int) (9.0 * (minecraft.options.chatLineSpacing().get() + 1.0));
		var chat = minecraft.gui.hud.getChat();
		int lines;
		if (ChatLanes.get().config().fixedChatSize) {
			// Immer die fokussierte (volle) Chathöhe – unabhängig davon, wie viele Nachrichten gerade da sind.
			lines = ChatComponent.getHeight(minecraft.options.chatHeightFocused().get()) / lineHeight;
		} else {
			// Direkt über der obersten sichtbaren Zeile.
			lines = Math.max(1, Math.min(((ChatComponentAccessor) chat).chatlanes$getTrimmedMessages().size(), chat.getLinesPerPage()));
		}
		int scaledBottom = (int) Math.floor((screenHeight - 40) / scale);
		int bottom = (int) Math.floor(scaledBottom * scale);
		int top = (int) Math.floor((scaledBottom - lines * lineHeight) * scale);
		int maxWidth = (int) Math.ceil(ChatComponent.getWidth(minecraft.options.chatWidth().get()) / scale);
		int right = (int) Math.floor((maxWidth + 12) * scale);
		return new ChatBox(top, bottom, right, scale, lineHeight, lines, scaledBottom);
	}

	/** Hebt die per Suche angesprungene Nachricht ein paar Sekunden lang gelb hervor. */
	private static void renderSearchHighlight(Minecraft minecraft, GuiGraphicsExtractor graphics) {
		net.minecraft.client.multiplayer.chat.GuiMessage target = ChatLanes.get().highlighted();
		if (target == null) {
			return;
		}
		ChatBox box = chatBox(minecraft, graphics.guiHeight());
		var chat = minecraft.gui.hud.getChat();
		var accessor = (ChatComponentAccessor) chat;
		java.util.List<net.minecraft.client.multiplayer.chat.GuiMessage.Line> lines = accessor.chatlanes$getTrimmedMessages();
		int scroll = accessor.chatlanes$getScrollPos();
		int perPage = chat.getLinesPerPage();
		for (int i = 0; i < perPage && scroll + i < lines.size(); i++) {
			if (ChatLanes.get().originalOf(lines.get(scroll + i).parent()) == target) {
				int bottom = (int) Math.floor((box.scaledBottom() - i * box.lineHeight()) * box.scale());
				int top = (int) Math.floor((box.scaledBottom() - (i + 1) * box.lineHeight()) * box.scale());
				graphics.fill(0, top, box.right(), bottom, 0x50FFFF55);
			}
		}
	}

	private static int rowY(Minecraft minecraft, int screenHeight, ChatLanesConfig.Position position) {
		if (position == ChatLanesConfig.Position.BOTTOM) {
			// Zwischen Chat und Eingabefeld (das Eingabefeld beginnt bei height - 14).
			return screenHeight - 14 - HEIGHT - 1;
		}
		return Math.max(0, chatBox(minecraft, screenHeight).top() - HEIGHT);
	}

	/**
	 * Füllt bei geöffnetem Chat den leeren Bereich über den Nachrichten mit dem Chat-Hintergrund,
	 * damit das Chatfeld immer gleich groß aussieht.
	 */
	private static void renderFixedBackground(Minecraft minecraft, GuiGraphicsExtractor graphics) {
		ChatBox box = chatBox(minecraft, graphics.guiHeight());
		var accessor = (ChatComponentAccessor) minecraft.gui.hud.getChat();
		int drawnLines = Math.max(0, Math.min(accessor.chatlanes$getTrimmedMessages().size() - accessor.chatlanes$getScrollPos(),
			minecraft.gui.hud.getChat().getLinesPerPage()));
		int emptyBottomScaled = box.scaledBottom() - drawnLines * box.lineHeight();
		int emptyBottom = (int) Math.floor(emptyBottomScaled * box.scale());
		if (emptyBottom > box.top()) {
			float opacity = minecraft.options.textBackgroundOpacity().get().floatValue();
			graphics.fill(0, box.top(), box.right(), emptyBottom, de.chatlanes.ChatLook.tint(ARGB.black(opacity)));
		}
	}

	private static List<Entry> layout(Minecraft minecraft, Font font, int screenHeight, boolean focused) {
		List<Entry> entries = new ArrayList<>();
		ChatLanes tabs = ChatLanes.get();
		ChatLanesConfig config = tabs.config();

		// Tabs auf die beiden Leisten verteilen (Reihenfolge wie in der Tab-Liste)
		List<ChatTab> top = new ArrayList<>();
		List<ChatTab> bottom = new ArrayList<>();
		for (ChatTab tab : tabs.tabs()) {
			ChatLanesConfig.Position row = tab.isPrivate() ? config.pmRowOrDefault() : config.rowOf(tab.config());
			(row == ChatLanesConfig.Position.BOTTOM ? bottom : top).add(tab);
		}
		// Lupe und "Optionen" gehören in die obere Leiste – außer sie ist leer
		boolean controlsTop = !top.isEmpty() || bottom.isEmpty();
		addRow(entries, font, top, rowY(minecraft, screenHeight, ChatLanesConfig.Position.TOP), focused, controlsTop);
		int topCount = entries.size();
		addRow(entries, font, bottom, rowY(minecraft, screenHeight, ChatLanesConfig.Position.BOTTOM), focused, !controlsTop);
		// Einträge der unteren Leiste markieren (für "bei geschlossenem Chat" pro Leiste)
		for (int i = topCount; i < entries.size(); i++) {
			Entry e = entries.get(i);
			entries.set(i, new Entry(e.tab(), e.label(), e.x(), e.y(), e.width(), e.closable(), e.action(), true));
		}
		return entries;
	}

	private static void addRow(List<Entry> entries, Font font, List<ChatTab> rowTabs, int y, boolean focused, boolean controls) {
		if (rowTabs.isEmpty() && !controls) {
			return;
		}
		ChatLanes tabs = ChatLanes.get();
		int x = 2;
		if (controls) {
			// Lupe ganz vorne – der Platz bleibt auch bei geschlossenem Chat frei, damit die Tabs nicht verrutschen.
			if (focused) {
				entries.add(new Entry(null, "", x, y, SEARCH_WIDTH, false, Action.SEARCH));
			}
			x += SEARCH_WIDTH + GAP;
		}
		for (ChatTab tab : rowTabs) {
			String label = tab.name();
			if (tabs.config().showUnreadCount && tab.showsUnreadCount() && tab != tabs.active() && tab.unread() > 0) {
				label += " (" + (tab.unread() > 99 ? "99+" : tab.unread()) + ")";
			}
			boolean closable = tab.isPrivate();
			int width = font.width(label) + PADDING * 2 + (closable ? CLOSE_WIDTH : 0);
			entries.add(new Entry(tab, label, x, y, width, closable));
			x += width + GAP;
		}
		if (controls && focused) {
			String settings = net.minecraft.client.resources.language.I18n.get("chatlanes.tabbar.settings");
			entries.add(new Entry(null, settings, x, y, font.width(settings) + PADDING * 2, false, Action.SETTINGS));
		}
	}

	/** Soll eine Leiste bei geschlossenem Chat sichtbar sein? (Einstellung pro Leiste, sonst die allgemeine) */
	private static boolean shouldRenderRow(boolean focused, boolean bottom, List<Entry> entries) {
		if (focused) {
			return true;
		}
		ChatLanesConfig config = ChatLanes.get().config();
		ChatLanesConfig.ClosedChatMode mode = config.closedModeFor(bottom ? ChatLanesConfig.Position.BOTTOM : ChatLanesConfig.Position.TOP);
		return switch (mode) {
			case NEVER -> false;
			case ALWAYS -> true;
			case UNREAD -> {
				ChatTab active = ChatLanes.get().active();
				for (Entry e : entries) {
					if (e.bottom() == bottom && e.tab() != null && e.tab() != active && e.tab().unread() > 0 && e.tab().blinks()) {
						yield true;
					}
				}
				yield false;
			}
		};
	}

	public static void render(GuiGraphicsExtractor graphics, Font font, boolean focused, int mouseX, int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ChatLanes.get().isEnabled()) {
			return;
		}
		if (minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
			return;
		}
		List<Entry> all = layout(minecraft, font, graphics.guiHeight(), focused);
		boolean showTop = shouldRenderRow(focused, false, all);
		boolean showBottom = shouldRenderRow(focused, true, all);
		if (!showTop && !showBottom) {
			return;
		}

		if (focused && ChatLanes.get().config().fixedChatSize) {
			renderFixedBackground(minecraft, graphics);
		}
		if (focused) {
			renderSearchHighlight(minecraft, graphics);
		}

		ChatTab active = ChatLanes.get().active();
		boolean blinkOn = (System.currentTimeMillis() / 500L) % 2L == 0L;
		for (Entry entry : all) {
			if (entry.bottom() ? !showBottom : !showTop) {
				continue;
			}
			ChatTab tab = entry.tab();
			boolean isActive = tab != null && tab == active;
			boolean hovered = focused && entry.contains(mouseX, mouseY);
			boolean blinking = tab != null && !isActive && tab.unread() > 0 && tab.blinks() && blinkOn;

			int background = isActive ? BG_ACTIVE : blinking ? BG_BLINK : hovered ? BG_HOVER : BG_INACTIVE;
			int textColor;
			if (blinking) {
				textColor = TEXT_BLINK;
			} else if (isActive) {
				textColor = tab.color() == TabColor.DEFAULT.argb() ? TEXT_ACTIVE : tab.color();
			} else if (hovered) {
				textColor = TEXT_ACTIVE;
			} else if (tab != null && tab.unread() > 0) {
				textColor = TEXT_UNREAD;
			} else if (tab != null) {
				textColor = tab.color();
			} else {
				textColor = TEXT_INACTIVE;
			}

			int x0 = entry.x();
			int y0 = entry.y();
			graphics.fill(x0, y0, x0 + entry.width(), y0 + HEIGHT, background);
			if (isActive) {
				graphics.fill(x0, y0 + HEIGHT - 1, x0 + entry.width(), y0 + HEIGHT, ACCENT);
			}
			if (entry.action() == Action.SEARCH) {
				drawMagnifier(graphics, x0 + 2, y0 + 2, hovered ? TEXT_ACTIVE : TEXT_INACTIVE);
				if (hovered) {
					graphics.setTooltipForNextFrame(net.minecraft.network.chat.Component.translatable("chatlanes.tabbar.search_tooltip"), mouseX, mouseY);
				}
			} else {
				graphics.text(font, entry.label(), x0 + PADDING, y0 + 2, textColor, !blinking);
			}
			if (entry.closable()) {
				boolean closeHovered = focused && entry.closeContains(mouseX, mouseY);
				int closeColor = closeHovered ? CLOSE_HOVER : blinking ? TEXT_BLINK : CLOSE_COLOR;
				graphics.text(font, CLOSE_LABEL, x0 + entry.width() - CLOSE_WIDTH - 1 + (CLOSE_WIDTH - font.width(CLOSE_LABEL)) / 2, y0 + 2, closeColor, !blinking);
			}
		}
	}

	/** Kleine Lupe (8 × 8 Pixel): Ring oben links, Griff nach unten rechts. */
	private static void drawMagnifier(GuiGraphicsExtractor graphics, int x, int y, int color) {
		// Ring (Kreis aus Pixeln, Durchmesser 6)
		graphics.fill(x + 1, y, x + 5, y + 1, color);
		graphics.fill(x + 1, y + 5, x + 5, y + 6, color);
		graphics.fill(x, y + 1, x + 1, y + 5, color);
		graphics.fill(x + 5, y + 1, x + 6, y + 5, color);
		// Griff (diagonal)
		graphics.fill(x + 5, y + 5, x + 6, y + 6, color);
		graphics.fill(x + 6, y + 6, x + 7, y + 7, color);
		graphics.fill(x + 7, y + 7, x + 8, y + 8, color);
		graphics.fill(x + 6, y + 7, x + 7, y + 8, color);
		graphics.fill(x + 7, y + 6, x + 8, y + 7, color);
	}

	/** @return true, wenn der Klick von der Tab-Leiste verarbeitet wurde. */
	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ChatLanes.get().isEnabled()) {
			return false;
		}
		if (minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
			return false;
		}

		ChatLanes tabs = ChatLanes.get();
		for (Entry entry : layout(minecraft, minecraft.font, screen.height, true)) {
			if (!entry.contains(mouseX, mouseY)) {
				continue;
			}
			ChatTab tab = entry.tab();
			if (tab == null) {
				if (button == InputConstants.MOUSE_BUTTON_LEFT) {
					minecraft.gui.setScreen(entry.action() == Action.SEARCH ? new ChatSearchScreen(screen) : new ChatLanesConfigScreen(screen));
				}
				return true;
			}
			// Achtung: Seit 26.x nutzt Minecraft SDL-Nummern (links = 1, Mitte = 2, rechts = 3).
			if (button == InputConstants.MOUSE_BUTTON_LEFT && entry.closeContains(mouseX, mouseY)) {
				tabs.closePrivateTab(tab);
			} else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				tabs.setActive(tab);
			} else if ((button == InputConstants.MOUSE_BUTTON_RIGHT || button == InputConstants.MOUSE_BUTTON_MIDDLE) && tab.isPrivate()) {
				tabs.closePrivateTab(tab);
			}
			return true;
		}
		return false;
	}
}
