package de.chattabs.gui;

import de.chattabs.ChatTabs;
import de.chattabs.config.ChatTabsConfig;
import de.chattabs.config.ChatTabsConfig.TabConfig;
import de.chattabs.config.TabColor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Hauptmenü der Mod: Liste aller Tabs (bearbeiten, sortieren, löschen, neu) und Zugang zu den Unterseiten.
 * Erreichbar über die Taste K, den Befehl /chattabs, "Optionen" in der Tab-Leiste und Mod Menu.
 */
public class ChatTabsConfigScreen extends BaseSettingsScreen {
	private static final int LIST_TOP = TOP + ROW + 18;

	/** Arbeitskopie – wird erst bei "Speichern" übernommen. */
	final ChatTabsConfig working;
	private int page;
	private int rowsPerPage = 1;

	public ChatTabsConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("chattabs.config.title"), parent);
		this.working = ChatTabs.get().config().copy();
	}

	@Override
	protected void init() {
		this.beginLayout(380);
		int third = (this.contentWidth - 8) / 3;
		this.addRenderableWidget(Button.builder(t("general"), b -> this.minecraft.gui.setScreen(new GeneralSettingsScreen(this)))
			.bounds(this.left, TOP, third, 20).build());
		this.addRenderableWidget(Button.builder(t("channels"), b -> this.minecraft.gui.setScreen(new ChannelHistoryScreen(this)))
			.bounds(this.left + third + 4, TOP, third, 20).build());
		this.addRenderableWidget(Button.builder(t("pm"), b -> this.minecraft.gui.setScreen(new PrivateMessagesScreen(this)))
			.bounds(this.left + (third + 4) * 2, TOP, this.contentWidth - (third + 4) * 2, 20).build());

		this.label(t("tabs_header"), this.left, LIST_TOP - 11);

		int bottomY = this.height - 26;
		this.rowsPerPage = Math.max(1, (bottomY - 4 - LIST_TOP) / 22);
		int pages = this.pageCount();
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		int start = this.page * this.rowsPerPage;
		for (int i = start; i < Math.min(this.working.tabs.size(), start + this.rowsPerPage); i++) {
			this.addTabRow(i, LIST_TOP + (i - start) * 22);
		}

		// Untere Leiste: [+ Neuer Tab] [<] [>]  ...  [Speichern] [Abbrechen]
		int x = this.left;
		this.addRenderableWidget(Button.builder(t("add_tab"), b -> {
			TabConfig tab = new TabConfig(Component.translatable("chattabs.config.new_tab_name").getString(), TabConfig.Type.CHANNEL,
				TabConfig.Match.TAG, "", "", false, TabColor.DEFAULT);
			this.minecraft.gui.setScreen(new TabEditScreen(this, tab, true));
		}).bounds(x, bottomY, 80, 20).build());
		x += 82;
		if (pages > 1) {
			Button prev = Button.builder(Component.literal("<"), b -> {
				this.page--;
				this.rebuildWidgets();
			}).bounds(x, bottomY, 20, 20).build();
			prev.active = this.page > 0;
			this.addRenderableWidget(prev);
			Button next = Button.builder(Component.literal(">"), b -> {
				this.page++;
				this.rebuildWidgets();
			}).bounds(x + 22, bottomY, 20, 20).build();
			next.active = this.page < pages - 1;
			this.addRenderableWidget(next);
		}

		int buttonWidth = Math.min(80, (this.contentWidth - 82 - 50) / 2);
		this.addRenderableWidget(Button.builder(t("cancel"), b -> this.onClose())
			.bounds(this.left + this.contentWidth - buttonWidth, bottomY, buttonWidth, 20).build());
		this.addRenderableWidget(Button.builder(t("save"), b -> this.saveAndClose())
			.bounds(this.left + this.contentWidth - buttonWidth * 2 - 4, bottomY, buttonWidth, 20).build());
	}

	private int pageCount() {
		return Math.max(1, (this.working.tabs.size() + this.rowsPerPage - 1) / this.rowsPerPage);
	}

	private void addTabRow(int index, int y) {
		TabConfig tab = this.working.tabs.get(index);
		int small = 20;
		int editWidth = 70;
		int buttonsX = this.left + this.contentWidth - (editWidth + 2 + (small + 2) * 3) + 2;

		Button edit = Button.builder(t("edit"), b -> this.minecraft.gui.setScreen(new TabEditScreen(this, tab, false)))
			.bounds(buttonsX, y, editWidth, 20).build();
		this.addRenderableWidget(edit);
		int x = buttonsX + editWidth + 2;

		Button up = Button.builder(Component.literal("↑"), b -> this.move(index, -1)).bounds(x, y, small, 20).build();
		up.active = index > 0;
		up.setTooltip(Tooltip.create(t("move_up")));
		this.addRenderableWidget(up);
		x += small + 2;
		Button down = Button.builder(Component.literal("↓"), b -> this.move(index, 1)).bounds(x, y, small, 20).build();
		down.active = index < this.working.tabs.size() - 1;
		down.setTooltip(Tooltip.create(t("move_down")));
		this.addRenderableWidget(down);
		x += small + 2;
		Button delete = Button.builder(Component.literal("✕"), b -> {
			this.working.tabs.remove(index);
			this.rebuildWidgets();
		}).bounds(x, y, small, 20).build();
		delete.setTooltip(Tooltip.create(t("delete")));
		this.addRenderableWidget(delete);
	}

	private void move(int index, int dir) {
		int target = index + dir;
		if (target < 0 || target >= this.working.tabs.size()) {
			return;
		}
		this.working.tabs.add(target, this.working.tabs.remove(index));
		this.page = target / this.rowsPerPage;
		this.rebuildWidgets();
	}

	/** Wird vom Bearbeiten-Bildschirm aufgerufen, wenn ein neuer Tab bestätigt wurde. */
	void addTab(TabConfig tab) {
		this.working.tabs.add(tab);
		this.page = Integer.MAX_VALUE;
	}

	private void saveAndClose() {
		ChatTabs.get().apply(this.working.copy());
		ChatTabs.get().config().save();
		this.onClose();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int pages = this.pageCount();
		if (pages > 1 && scrollY != 0) {
			int newPage = Math.max(0, Math.min(pages - 1, this.page + (scrollY < 0 ? 1 : -1)));
			if (newPage != this.page) {
				this.page = newPage;
				this.rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		int start = this.page * this.rowsPerPage;
		int end = Math.min(this.working.tabs.size(), start + this.rowsPerPage);
		for (int i = start; i < end; i++) {
			TabConfig tab = this.working.tabs.get(i);
			int y = LIST_TOP + (i - start) * 22;
			graphics.fill(this.left, y, this.left + this.contentWidth, y + 20, 0x60000000);
			int color = tab.color == null || tab.color == TabColor.DEFAULT ? 0xFFFFFFFF : tab.color.argb();
			graphics.text(this.font, tab.name, this.left + 6, y + 6, color);
			graphics.text(this.font, describe(tab), this.left + 6 + Math.max(70, this.font.width(tab.name) + 10), y + 6, LABEL_COLOR);
		}

		if (this.working.tabs.isEmpty()) {
			graphics.centeredText(this.font, t("no_tabs"), this.width / 2, LIST_TOP + 6, LABEL_COLOR);
		}

		int pages = this.pageCount();
		if (pages > 1) {
			graphics.text(this.font, (this.page + 1) + "/" + pages, this.left + 82 + 48, this.height - 20, LABEL_COLOR);
		}
	}

	/** Kurzbeschreibung eines Tabs für die Liste, z. B. "Kanal · [G]". */
	private static Component describe(TabConfig tab) {
		return switch (tab.type) {
			case SYSTEM -> t("describe.system");
			case ALL -> t("describe.all");
			case CHANNEL -> tab.pattern.isEmpty()
				? t("describe.channel_empty")
				: t("describe.channel", tab.match == TabConfig.Match.REGEX ? t("describe.regex") : Component.literal(tab.pattern))
					.append(tab.switchCommand == null || tab.switchCommand.isBlank() ? Component.empty() : Component.literal(" → " + tab.switchCommand));
		};
	}
}
