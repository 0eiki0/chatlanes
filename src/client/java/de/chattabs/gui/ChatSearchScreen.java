package de.chattabs.gui;

import de.chattabs.ChatTab;
import de.chattabs.ChatTabs;
import de.chattabs.ChatTabs.SearchHit;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

/**
 * Durchsucht den Chatverlauf (alle gespeicherten Nachrichten, auch den wiederhergestellten Verlauf).
 * Klick auf einen Treffer öffnet den passenden Tab und scrollt zur Nachricht.
 * Öffnen: Strg+F im Chat oder "Suchen" in der Tab-Leiste.
 */
public class ChatSearchScreen extends BaseSettingsScreen {
	private static final int ROW_HEIGHT = 12;
	private static final int MAX_RESULTS = 500;
	private static final int MATCH_COLOR = 0xFFFFFF55;
	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int HOVER_BG = 0x40FFFFFF;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

	private static String lastQuery = "";
	private static boolean lastAllTabs = true;

	private List<SearchHit> hits = List.of();
	private int scroll;
	private int listTop;
	private int listBottom;

	public ChatSearchScreen(@Nullable Screen parent) {
		super(Component.translatable("chattabs.search.title"), parent);
	}

	@Override
	protected void init() {
		this.beginLayout(520);
		int scopeWidth = 130;

		EditBox query = new EditBox(this.font, this.left, TOP + 1, this.contentWidth - scopeWidth - 4, 18, Component.translatable("chattabs.search.title"));
		query.setMaxLength(256);
		query.setHint(Component.translatable("chattabs.search.hint"));
		query.setValue(lastQuery);
		query.setResponder(v -> {
			lastQuery = v;
			this.runSearch();
		});
		this.addRenderableWidget(query);
		this.setInitialFocus(query);

		CycleButton<Boolean> scope = CycleButton.booleanBuilder(Component.translatable("chattabs.search.all_tabs"),
				Component.translatable("chattabs.search.current_tab"), lastAllTabs)
			.displayOnlyValue()
			.create(this.left + this.contentWidth - scopeWidth, TOP, scopeWidth, 20, Component.translatable("chattabs.search.scope"), (b, v) -> {
				lastAllTabs = v;
				this.runSearch();
			});
		scope.setTooltip(Tooltip.create(Component.translatable("chattabs.search.scope.tooltip")));
		this.addRenderableWidget(scope);

		this.listTop = TOP + 26;
		this.listBottom = this.height - 30;
		this.addRenderableWidget(Button.builder(Component.translatable("chattabs.search.close"), b -> this.onClose())
			.bounds(this.left + this.contentWidth - 90, this.height - 26, 90, 20).build());

		this.runSearch();
	}

	@Override
	protected void setInitialFocus() {
		// Fokus setzt init() auf das Suchfeld
	}

	private void runSearch() {
		this.hits = ChatTabs.get().search(lastQuery, !lastAllTabs);
		if (this.hits.size() > MAX_RESULTS) {
			this.hits = this.hits.subList(0, MAX_RESULTS);
		}
		this.scroll = 0;
	}

	private int visibleRows() {
		return Math.max(1, (this.listBottom - this.listTop) / ROW_HEIGHT);
	}

	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < this.left || mouseX >= this.left + this.contentWidth || mouseY < this.listTop || mouseY >= this.listTop + this.visibleRows() * ROW_HEIGHT) {
			return -1;
		}
		int index = this.scroll + (int) ((mouseY - this.listTop) / ROW_HEIGHT);
		return index < this.hits.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int row = this.rowAt(event.x(), event.y());
		if (row >= 0 && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			ChatTabs.get().jumpTo(this.hits.get(row));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int max = Math.max(0, this.hits.size() - this.visibleRows());
		this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY) * 3));
		return true;
	}

	private static String formatTime(long time) {
		if (time <= 0) {
			return "--:--";
		}
		ZonedDateTime when = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
		return when.toLocalDate().equals(LocalDate.now()) ? TIME.format(when) : DATE_TIME.format(when);
	}

	/** Zeile für einen Treffer: "[Zeit] Tab · Text" mit hervorgehobenen Suchwörtern. */
	private Component describe(SearchHit hit, int width) {
		ChatTab tab = hit.tab();
		MutableComponent line = Component.literal("[" + formatTime(hit.time()) + "] ").withStyle(ChatFormatting.DARK_GRAY);
		if (tab != null) {
			line.append(Component.literal(tab.name()).withColor(tab.color())).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		}

		String text = hit.text();
		String lower = text.toLowerCase(Locale.ROOT);
		String[] words = lastQuery.trim().toLowerCase(Locale.ROOT).split("\\s+");

		// Lange Nachricht: so kürzen, dass der erste Treffer sichtbar ist
		int first = words.length > 0 && !words[0].isEmpty() ? lower.indexOf(words[0]) : -1;
		int prefixWidth = this.font.width(line);
		boolean tooLong = prefixWidth + this.font.width(text) > width;
		int matchEnd = first < 0 ? 0 : Math.min(text.length(), first + words[0].length());
		// Nur kürzen, wenn die Zeile nicht passt UND der erste Treffer sonst hinten abgeschnitten würde
		if (first > 0 && tooLong && prefixWidth + this.font.width(text.substring(0, matchEnd)) > width - 20) {
			int start = Math.max(0, first - 20);
			text = "…" + text.substring(start);
			lower = text.toLowerCase(Locale.ROOT);
		}

		// Treffer markieren
		boolean[] mark = new boolean[text.length()];
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			int from = 0;
			int found;
			while ((found = lower.indexOf(word, from)) >= 0) {
				for (int i = found; i < found + word.length() && i < mark.length; i++) {
					mark[i] = true;
				}
				from = found + word.length();
			}
		}
		int i = 0;
		while (i < text.length()) {
			boolean m = mark[i];
			int j = i;
			while (j < text.length() && mark[j] == m) {
				j++;
			}
			line.append(Component.literal(text.substring(i, j)).withStyle(Style.EMPTY.withColor(m ? MATCH_COLOR : TEXT_COLOR).withUnderlined(m)));
			i = j;
		}
		return line;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.fill(this.left, this.listTop - 2, this.left + this.contentWidth, this.listBottom, 0x60000000);
		int rows = this.visibleRows();
		int hovered = this.rowAt(mouseX, mouseY);
		int width = this.contentWidth - 8;
		for (int r = 0; r < rows && this.scroll + r < this.hits.size(); r++) {
			int index = this.scroll + r;
			int y = this.listTop + r * ROW_HEIGHT;
			if (index == hovered) {
				graphics.fill(this.left, y, this.left + this.contentWidth, y + ROW_HEIGHT, HOVER_BG);
			}
			Component line = this.describe(this.hits.get(index), width);
			if (this.font.width(line) <= width) {
				graphics.text(this.font, line, this.left + 4, y + 2, TEXT_COLOR);
			} else {
				graphics.text(this.font, ComponentRenderUtils.clipText(line, this.font, width), this.left + 4, y + 2, TEXT_COLOR);
			}
		}

		Component info;
		if (lastQuery.isBlank()) {
			info = Component.translatable("chattabs.search.enter_text");
		} else if (this.hits.isEmpty()) {
			info = Component.translatable("chattabs.search.none");
		} else {
			info = Component.translatable(this.hits.size() >= MAX_RESULTS ? "chattabs.search.count_max" : "chattabs.search.count", this.hits.size());
		}
		int infoWidth = this.contentWidth - 96;
		if (this.font.width(info) <= infoWidth) {
			graphics.text(this.font, info, this.left, this.height - 20, LABEL_COLOR);
		} else {
			graphics.text(this.font, ComponentRenderUtils.clipText(info, this.font, infoWidth), this.left, this.height - 20, LABEL_COLOR);
		}

		// Scrollbalken
		int total = this.hits.size();
		if (total > rows) {
			int barHeight = Math.max(8, (this.listBottom - this.listTop) * rows / total);
			int barTop = this.listTop + (this.listBottom - this.listTop - barHeight) * this.scroll / Math.max(1, total - rows);
			graphics.fill(this.left + this.contentWidth - 2, barTop, this.left + this.contentWidth, barTop + barHeight, 0xFFAAAAAA);
		}
	}
}
