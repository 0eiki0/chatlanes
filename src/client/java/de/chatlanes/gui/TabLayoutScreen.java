package de.chatlanes.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.chatlanes.config.ChatLanesConfig;
import de.chatlanes.config.ChatLanesConfig.Position;
import de.chatlanes.config.ChatLanesConfig.TabConfig;
import de.chatlanes.config.TabColor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Tabs per Drag &amp; Drop anordnen: zwei Leisten (oben über dem Chat, unten über dem Eingabefeld),
 * Reihenfolge und Leiste per Maus ziehen. Die Msg-Tabs werden durch einen Platzhalter dargestellt.
 */
class TabLayoutScreen extends BaseSettingsScreen {
	private static final int CHIP_H = 16;
	private static final int GAP = 4;
	private static final int ZONE_PAD = 6;
	private static final int ZONE_BG = 0x70000000;
	private static final int ZONE_HOVER = 0x90304030;
	private static final int CHIP_BG = 0xE0202020;
	private static final int MARKER = 0xFF55FF55;
	private static final Object PM = new Object();

	private final ChatLanesConfig working;
	/** Was gezogen wird: ein TabConfig oder PM, sonst null. */
	private @Nullable Object dragging;
	private double dragX;
	private double dragY;
	private double grabDx;
	private double grabDy;
	private final int[] zoneTop = new int[2];
	private final int[] zoneBottom = new int[2];

	private record Chip(Object item, String label, int color, int x, int y, int w) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + CHIP_H;
		}
	}

	TabLayoutScreen(ChatLanesConfigScreen main) {
		super(Component.translatable("chatlanes.config.arrange.title"), main);
		this.working = main.working;
	}

	@Override
	protected void init() {
		this.beginLayout(420);
		int zoneH = Math.max(40, (this.height - 30 - 60 - TOP - 24) / 2);
		this.zoneTop[0] = TOP + 22;
		this.zoneBottom[0] = this.zoneTop[0] + zoneH;
		this.zoneTop[1] = this.zoneBottom[0] + 24;
		this.zoneBottom[1] = this.zoneTop[1] + zoneH;
		this.addRenderableWidget(Button.builder(t("done"), b -> this.onClose())
			.bounds(this.left, this.height - 26, this.contentWidth, 20).build());
	}

	private Position rowOf(Object item) {
		return item == PM ? this.working.pmRowOrDefault() : this.working.rowOf((TabConfig) item);
	}

	private List<Object> itemsIn(Position row) {
		List<Object> items = new ArrayList<>();
		for (TabConfig tab : this.working.tabs) {
			if (this.working.rowOf(tab) == row) {
				items.add(tab);
			}
		}
		if (this.working.pmTabs && this.working.pmRowOrDefault() == row) {
			items.add(PM); // Msg-Tabs stehen immer am Ende ihrer Leiste
		}
		return items;
	}

	/** Chips einer Leiste mit Zeilenumbruch auslegen (der gezogene Chip wird ausgelassen). */
	private List<Chip> layout(int zone) {
		Position row = zone == 0 ? Position.TOP : Position.BOTTOM;
		List<Chip> chips = new ArrayList<>();
		int x = this.left + ZONE_PAD;
		int y = this.zoneTop[zone] + ZONE_PAD;
		for (Object item : this.itemsIn(row)) {
			if (item == this.dragging) {
				continue;
			}
			String label = item == PM ? t("arrange.pm").getString() : ((TabConfig) item).name;
			int color = item == PM ? 0xFFFF55FF
				: ((TabConfig) item).color == null || ((TabConfig) item).color == TabColor.DEFAULT ? 0xFFFFFFFF : ((TabConfig) item).color.argb();
			int w = this.font.width(label) + 12;
			if (x + w > this.left + this.contentWidth - ZONE_PAD && x > this.left + ZONE_PAD) {
				x = this.left + ZONE_PAD;
				y += CHIP_H + GAP;
			}
			chips.add(new Chip(item, label, color, x, y, w));
			x += w + GAP;
		}
		return chips;
	}

	private int zoneAt(double my) {
		for (int zone = 0; zone < 2; zone++) {
			if (my >= this.zoneTop[zone] - 10 && my <= this.zoneBottom[zone] + 10) {
				return zone;
			}
		}
		return -1;
	}

	/** Einfügeposition in einer Leiste: Anzahl der Chips "vor" der Maus. */
	private int insertIndex(List<Chip> chips, double mx, double my) {
		int index = 0;
		for (Chip chip : chips) {
			if (chip.item() == PM) {
				break; // Msg-Tabs bleiben am Ende
			}
			boolean before = my >= chip.y() + CHIP_H + GAP / 2.0 || (my >= chip.y() - GAP / 2.0 && mx > chip.x() + chip.w() / 2.0);
			if (before) {
				index++;
			}
		}
		return index;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int zone = 0; zone < 2; zone++) {
				for (Chip chip : this.layout(zone)) {
					if (chip.contains(event.x(), event.y())) {
						this.dragging = chip.item();
						this.grabDx = event.x() - chip.x();
						this.grabDy = event.y() - chip.y();
						this.dragX = event.x();
						this.dragY = event.y();
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging != null) {
			this.dragX = event.x();
			this.dragY = event.y();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging == null) {
			return super.mouseReleased(event);
		}
		Object item = this.dragging;
		int zone = this.zoneAt(event.y());
		if (zone >= 0) {
			Position row = zone == 0 ? Position.TOP : Position.BOTTOM;
			if (item == PM) {
				this.working.pmRow = row;
			} else {
				TabConfig tab = (TabConfig) item;
				List<Chip> chips = this.layout(zone);
				int index = this.insertIndex(chips, event.x(), event.y());
				// In der Gesamtliste vor den index-ten Tab dieser Leiste einsortieren
				this.working.tabs.remove(tab);
				tab.row = row;
				List<TabConfig> rowTabs = new ArrayList<>();
				for (TabConfig other : this.working.tabs) {
					if (this.working.rowOf(other) == row) {
						rowTabs.add(other);
					}
				}
				if (index < rowTabs.size()) {
					this.working.tabs.add(this.working.tabs.indexOf(rowTabs.get(index)), tab);
				} else if (!rowTabs.isEmpty()) {
					this.working.tabs.add(this.working.tabs.indexOf(rowTabs.getLast()) + 1, tab);
				} else {
					this.working.tabs.add(tab);
				}
			}
		}
		this.dragging = null;
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int hoverZone = this.dragging != null ? this.zoneAt(this.dragY) : -1;
		for (int zone = 0; zone < 2; zone++) {
			graphics.text(this.font, t(zone == 0 ? "arrange.top" : "arrange.bottom"), this.left, this.zoneTop[zone] - 11, LABEL_COLOR);
			graphics.fill(this.left, this.zoneTop[zone], this.left + this.contentWidth, this.zoneBottom[zone], zone == hoverZone ? ZONE_HOVER : ZONE_BG);
			List<Chip> chips = this.layout(zone);
			for (Chip chip : chips) {
				this.drawChip(graphics, chip.label(), chip.color(), chip.x(), chip.y(), chip.w(), chip.contains(mouseX, mouseY) && this.dragging == null);
			}
			if (chips.isEmpty()) {
				graphics.text(this.font, t("arrange.empty"), this.left + ZONE_PAD, this.zoneTop[zone] + ZONE_PAD + 4, 0xFF777777);
			}
			// Einfügemarker
			if (zone == hoverZone && this.dragging != PM) {
				int index = this.insertIndex(chips, this.dragX, this.dragY);
				int mx;
				int my;
				if (index < chips.size()) {
					mx = chips.get(index).x() - GAP / 2 - 1;
					my = chips.get(index).y();
				} else if (!chips.isEmpty()) {
					Chip last = chips.get(chips.size() - 1);
					if (last.item() == PM && chips.size() > 1) {
						last = chips.get(chips.size() - 1);
						mx = last.x() - GAP / 2 - 1;
					} else {
						mx = last.x() + last.w() + GAP / 2 - 1;
					}
					my = last.y();
				} else {
					mx = this.left + ZONE_PAD - 2;
					my = this.zoneTop[zone] + ZONE_PAD;
				}
				graphics.fill(mx, my - 1, mx + 2, my + CHIP_H + 1, MARKER);
			}
		}
		// Gezogener Chip folgt der Maus
		if (this.dragging != null) {
			Object item = this.dragging;
			String label = item == PM ? t("arrange.pm").getString() : ((TabConfig) item).name;
			int color = item == PM ? 0xFFFF55FF
				: ((TabConfig) item).color == null || ((TabConfig) item).color == TabColor.DEFAULT ? 0xFFFFFFFF : ((TabConfig) item).color.argb();
			this.drawChip(graphics, label, color, (int) (this.dragX - this.grabDx), (int) (this.dragY - this.grabDy), this.font.width(label) + 12, true);
		}
		graphics.text(this.font, t("arrange.hint"), this.left, this.height - 40, LABEL_COLOR);
	}

	private void drawChip(GuiGraphicsExtractor graphics, String label, int color, int x, int y, int w, boolean highlight) {
		graphics.fill(x, y, x + w, y + CHIP_H, CHIP_BG);
		graphics.outline(x, y, w, CHIP_H, highlight ? 0xFFFFFFFF : 0xFF555555);
		graphics.text(this.font, label, x + 6, y + 4, color);
	}
}
