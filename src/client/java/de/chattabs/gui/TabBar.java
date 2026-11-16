package de.chattabs.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.chattabs.ChatTab;
import de.chattabs.ChatTabs;
import de.chattabs.config.ChatTabsConfig;
import de.chattabs.config.TabColor;
import de.chattabs.mixin.ChatComponentAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Zeichnet die Tab-Leiste über bzw. unter dem Chat und verarbeitet Klicks darauf. */
public final class TabBar {
	private static final int HEIGHT = 12;
	private static final int PADDING = 4;
	private static final int GAP = 2;
	

	private static final int BG_ACTIVE = 0xE0000000;
	private static final int BG_INACTIVE = 0x90000000;
	private static final int BG_HOVER = 0xB0303030;
	private static final int BG_BLINK = 0xE0E08000;
	private static final int ACCENT = 0xFF55FF55;
	private static final int TEXT_ACTIVE = 0xFFFFFFFF;
	private static final int TEXT_INACTIVE = 0xFFAAAAAA;
	private static final int TEXT_UNREAD = 0xFFFFFF55;
	private static final int TEXT_BLINK = 0xFF000000;

	private record Entry(@Nullable ChatTab tab, String label, int x, int y, int width) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.width && my >= this.y && my < this.y + HEIGHT;
		}
	}

	private TabBar() {
	}

	private static int barY(Minecraft minecraft, int screenHeight, boolean focused) {
		ChatTabsConfig config = ChatTabs.get().config();
		if (config.position == ChatTabsConfig.Position.BOTTOM) {
			// Zwischen Chat und Eingabefeld (das Eingabefeld beginnt bei height - 14).
			return screenHeight - 14 - HEIGHT - 1;
		}

		double scale = minecraft.options.chatScale().get();
		int lineHeight = (int) (9.0 * (minecraft.options.chatLineSpacing().get() + 1.0));
		var chat = minecraft.gui.hud.getChat();
		// Direkt über der obersten sichtbaren Zeile – mindestens eine Zeile Platz, höchstens die volle Chathöhe.
		int visibleLines = Math.min(((ChatComponentAccessor) chat).chattabs$getTrimmedMessages().size(), chat.getLinesPerPage());
		visibleLines = Math.max(1, visibleLines);
		int chatBottom = (int) Math.floor((screenHeight - 40) / scale);
		int chatTop = (int) Math.floor((chatBottom - visibleLines * lineHeight) * scale);
		return Math.max(0, chatTop - HEIGHT - 1);
	}

	private static List<Entry> layout(Minecraft minecraft, Font font, int screenHeight, boolean focused) {
		List<Entry> entries = new ArrayList<>();
		ChatTabs tabs = ChatTabs.get();
		int y = barY(minecraft, screenHeight, focused);
		int x = 2;
		for (ChatTab tab : tabs.tabs()) {
			String label = tab.name();
			if (tabs.config().showUnreadCount && tab != tabs.active() && tab.unread() > 0) {
				label += " (" + (tab.unread() > 99 ? "99+" : tab.unread()) + ")";
			}
			int width = font.width(label) + PADDING * 2;
			entries.add(new Entry(tab, label, x, y, width));
			x += width + GAP;
		}
		if (focused) {
			String settings = net.minecraft.client.resources.language.I18n.get("chattabs.tabbar.settings");
			entries.add(new Entry(null, settings, x, y, font.width(settings) + PADDING * 2));
		}
		return entries;
	}

	private static boolean shouldRender(boolean focused) {
		if (focused) {
			return true;
		}
		ChatTabs tabs = ChatTabs.get();
		return switch (tabs.config().showWhenClosed) {
			case NEVER -> false;
			case ALWAYS -> true;
			case UNREAD -> tabs.hasBlinkingUnread();
		};
	}

	public static void render(GuiGraphicsExtractor graphics, Font font, boolean focused, int mouseX, int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN || !shouldRender(focused)) {
			return;
		}

		ChatTab active = ChatTabs.get().active();
		boolean blinkOn = (System.currentTimeMillis() / 500L) % 2L == 0L;
		for (Entry entry : layout(minecraft, font, graphics.guiHeight(), focused)) {
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
			graphics.text(font, entry.label(), x0 + PADDING, y0 + 2, textColor, !blinking);
		}
	}

	/** @return true, wenn der Klick von der Tab-Leiste verarbeitet wurde. */
	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
			return false;
		}

		ChatTabs tabs = ChatTabs.get();
		for (Entry entry : layout(minecraft, minecraft.font, screen.height, true)) {
			if (!entry.contains(mouseX, mouseY)) {
				continue;
			}
			ChatTab tab = entry.tab();
			if (tab == null) {
				if (button == InputConstants.MOUSE_BUTTON_LEFT) {
					minecraft.gui.setScreen(new ChatTabsConfigScreen(screen));
				}
				return true;
			}
			// Achtung: Seit 26.x nutzt Minecraft SDL-Nummern (links = 1, Mitte = 2, rechts = 3).
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				tabs.setActive(tab);
			} else if ((button == InputConstants.MOUSE_BUTTON_RIGHT || button == InputConstants.MOUSE_BUTTON_MIDDLE) && tab.isPrivate()) {
				tabs.closePrivateTab(tab);
			}
			return true;
		}
		return false;
	}
}
