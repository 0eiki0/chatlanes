package de.chattabs.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Auswahl von Items aus allen Minecraft-Items: Raster mit Bildern, Suche nach Name oder ID,
 * Klick wählt an/ab. Ausgewählte Items stehen oben und sind grün umrandet.
 */
class ItemPickerScreen extends BaseSettingsScreen {
	private static final int CELL = 20;
	private static final int SELECTED_BG = 0x6055FF55;
	private static final int SELECTED_BORDER = 0xFF55FF55;
	private static final int HOVER_BG = 0x50FFFFFF;
	private static final int GRID_BG = 0x60000000;

	/** Alle Items (ohne Luft), einmal aufgebaut. */
	private static List<Entry> allItems;

	private record Entry(Item item, ItemStack stack, String id, String shortId, String name) {
	}

	private final Set<String> selected = new LinkedHashSet<>();
	private final Consumer<String> onDone;
	private String query = "";
	private List<Entry> shown = List.of();
	private int scroll;
	private int gridLeft;
	private int gridTop;
	private int columns;
	private int rows;

	/** @param current kommagetrennte Liste wie "oak_log, bedrock" */
	ItemPickerScreen(BaseSettingsScreen parent, String current, Consumer<String> onDone) {
		super(Component.translatable("chattabs.picker.title"), parent);
		this.onDone = onDone;
		for (String entry : current.split("[,;\\s]+")) {
			String id = entry.trim().toLowerCase(Locale.ROOT);
			if (!id.isEmpty()) {
				this.selected.add(id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id);
			}
		}
	}

	private static List<Entry> allItems() {
		if (allItems == null) {
			List<Entry> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}
				Identifier key = BuiltInRegistries.ITEM.getKey(item);
				ItemStack stack = item.getDefaultInstance();
				String id = key.toString();
				String shortId = key.getNamespace().equals("minecraft") ? key.getPath() : id;
				list.add(new Entry(item, stack, id, shortId, stack.getHoverName().getString()));
			}
			allItems = list;
		}
		return allItems;
	}

	@Override
	protected void init() {
		this.beginLayout(420);
		EditBox search = new EditBox(this.font, this.left, TOP + 1, this.contentWidth, 18, Component.translatable("chattabs.picker.search"));
		search.setMaxLength(64);
		search.setHint(Component.translatable("chattabs.picker.search"));
		search.setValue(this.query);
		search.setResponder(v -> {
			this.query = v;
			this.filter();
		});
		this.addRenderableWidget(search);
		this.setInitialFocus(search);

		this.gridTop = TOP + 26;
		int gridBottom = this.height - 46;
		this.columns = Math.max(1, this.contentWidth / CELL);
		this.rows = Math.max(1, (gridBottom - this.gridTop) / CELL);
		this.gridLeft = this.left + (this.contentWidth - this.columns * CELL) / 2;

		this.addRenderableWidget(Button.builder(Component.translatable("chattabs.picker.clear"), b -> {
			this.selected.clear();
			this.filter();
		}).bounds(this.left, this.height - 26, 90, 20).build());
		this.addRenderableWidget(Button.builder(t("done"), b -> {
			this.onDone.accept(String.join(", ", this.selected));
			this.onClose();
		}).bounds(this.left + this.contentWidth - 184, this.height - 26, 90, 20).build());
		this.addRenderableWidget(Button.builder(t("cancel"), b -> this.onClose())
			.bounds(this.left + this.contentWidth - 90, this.height - 26, 90, 20).build());

		this.filter();
	}

	@Override
	protected void setInitialFocus() {
		// Fokus setzt init() auf das Suchfeld
	}

	/** Suche anwenden: ausgewählte Items zuerst, dann alle passenden. */
	private void filter() {
		String q = this.query.trim().toLowerCase(Locale.ROOT);
		List<Entry> first = new ArrayList<>();
		List<Entry> rest = new ArrayList<>();
		for (Entry entry : allItems()) {
			boolean match = q.isEmpty() || entry.shortId().contains(q) || entry.name().toLowerCase(Locale.ROOT).contains(q);
			if (!match) {
				continue;
			}
			(this.selected.contains(entry.shortId()) ? first : rest).add(entry);
		}
		first.addAll(rest);
		this.shown = first;
		this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
	}

	private int maxScroll() {
		int totalRows = (this.shown.size() + this.columns - 1) / this.columns;
		return Math.max(0, totalRows - this.rows);
	}

	private int indexAt(double mouseX, double mouseY) {
		if (mouseX < this.gridLeft || mouseY < this.gridTop) {
			return -1;
		}
		int col = (int) ((mouseX - this.gridLeft) / CELL);
		int row = (int) ((mouseY - this.gridTop) / CELL);
		if (col >= this.columns || row >= this.rows) {
			return -1;
		}
		int index = (this.scroll + row) * this.columns + col;
		return index < this.shown.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = this.indexAt(event.x(), event.y());
		if (index >= 0 && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			String id = this.shown.get(index).shortId();
			if (!this.selected.remove(id)) {
				this.selected.add(id);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.fill(this.gridLeft - 2, this.gridTop - 2, this.gridLeft + this.columns * CELL + 2, this.gridTop + this.rows * CELL + 2, GRID_BG);
		int hovered = this.indexAt(mouseX, mouseY);
		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < this.columns; col++) {
				int index = (this.scroll + row) * this.columns + col;
				if (index >= this.shown.size()) {
					break;
				}
				Entry entry = this.shown.get(index);
				int x = this.gridLeft + col * CELL;
				int y = this.gridTop + row * CELL;
				if (this.selected.contains(entry.shortId())) {
					graphics.fill(x, y, x + CELL, y + CELL, SELECTED_BG);
					graphics.outline(x, y, CELL, CELL, SELECTED_BORDER);
				} else if (index == hovered) {
					graphics.fill(x, y, x + CELL, y + CELL, HOVER_BG);
				}
				graphics.fakeItem(entry.stack(), x + 2, y + 2);
			}
		}

		// Scrollbalken
		int max = this.maxScroll();
		if (max > 0) {
			int height = this.rows * CELL;
			int bar = Math.max(8, height * this.rows / (this.rows + max));
			int top = this.gridTop + (height - bar) * this.scroll / max;
			int x = this.gridLeft + this.columns * CELL + 3;
			graphics.fill(x, top, x + 2, top + bar, 0xFFAAAAAA);
		}

		// Ausgewählt: ...
		String list = this.selected.isEmpty() ? Component.translatable("chattabs.picker.none").getString() : String.join(", ", this.selected);
		Component line = Component.translatable("chattabs.picker.selected", list);
		if (this.font.width(line) <= this.contentWidth) {
			graphics.text(this.font, line, this.left, this.height - 40, LABEL_COLOR);
		} else {
			graphics.text(this.font, net.minecraft.client.gui.components.ComponentRenderUtils.clipText(line, this.font, this.contentWidth),
				this.left, this.height - 40, LABEL_COLOR);
		}

		if (hovered >= 0) {
			Entry entry = this.shown.get(hovered);
			graphics.setTooltipForNextFrame(this.font, List.of(
				Component.literal(entry.name()),
				Component.literal(entry.id()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
				Component.translatable(this.selected.contains(entry.shortId()) ? "chattabs.picker.click_remove" : "chattabs.picker.click_add")
					.withStyle(net.minecraft.ChatFormatting.GRAY)
			), java.util.Optional.empty(), mouseX, mouseY);
		}
	}
}
