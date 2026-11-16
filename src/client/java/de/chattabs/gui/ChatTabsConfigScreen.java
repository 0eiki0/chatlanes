package de.chattabs.gui;

import de.chattabs.ChatTabs;
import de.chattabs.config.ChatTabsConfig;
import de.chattabs.config.ChatTabsConfig.TabConfig;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Einstellungen im Spiel: allgemeine Optionen und Liste der Tabs (hinzufügen, löschen, sortieren, bearbeiten). */
public class ChatTabsConfigScreen extends Screen {
	private static final int TEXT_OK = 0xFFE0E0E0;
	private static final int TEXT_ERROR = 0xFFFF5555;
	private static final int LABEL_COLOR = 0xFFA0A0A0;
	private static final int ROW_HEIGHT = 22;
	private static final int TABS_TOP = 164;

	private final @Nullable Screen parent;
	private final ChatTabsConfig working;
	private int page;
	private int rowsPerPage = 1;
	private int left;
	private int contentWidth;

	public ChatTabsConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("chattabs.config.title"));
		this.parent = parent;
		this.working = ChatTabs.get().config().copy();
	}

	private static Component t(String key) {
		return Component.translatable("chattabs.config." + key);
	}

	@Override
	protected void init() {
		this.contentWidth = Math.min(this.width - 20, 480);
		this.left = (this.width - this.contentWidth) / 2;
		int half = (this.contentWidth - 4) / 2;
		int right = this.left + half + 4;

		// --- Allgemein ---
		int y = 28;
		this.addRenderableWidget(CycleButton.<ChatTabsConfig.Position>builder(
				p -> t(p == ChatTabsConfig.Position.TOP ? "position.top" : "position.bottom"), this.working.position)
			.withValues(ChatTabsConfig.Position.values())
			.create(this.left, y, half, 20, t("position"), (b, v) -> this.working.position = v));
		CycleButton<ChatTabsConfig.ClosedChatMode> closed = CycleButton.<ChatTabsConfig.ClosedChatMode>builder(
				m -> t("closed." + m.name().toLowerCase()), this.working.showWhenClosed)
			.withValues(ChatTabsConfig.ClosedChatMode.values())
			.create(right, y, half, 20, t("closed"), (b, v) -> this.working.showWhenClosed = v);
		closed.setTooltip(Tooltip.create(t("closed.tooltip")));
		this.addRenderableWidget(closed);

		y += 22;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.working.pmTabs)
			.create(this.left, y, half, 20, t("pm_tabs"), (b, v) -> this.working.pmTabs = v));
		this.addRenderableWidget(CycleButton.onOffBuilder(this.working.pmBlink)
			.create(right, y, half, 20, t("pm_blink"), (b, v) -> this.working.pmBlink = v));

		y += 22;
		CycleButton<Boolean> closeOnDisconnect = CycleButton.onOffBuilder(this.working.pmCloseOnDisconnect)
			.create(this.left, y, half, 20, t("pm_close"), (b, v) -> this.working.pmCloseOnDisconnect = v);
		closeOnDisconnect.setTooltip(Tooltip.create(t("pm_close.tooltip")));
		this.addRenderableWidget(closeOnDisconnect);
		Integer history = this.working.maxHistory;
		List<Integer> historyValues = new java.util.ArrayList<>(List.of(100, 250, 500, 1000, 2000, 5000));
		if (!historyValues.contains(history)) {
			historyValues.add(history);
			historyValues.sort(Integer::compare);
		}
		CycleButton<Integer> historyButton = CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)), history)
			.withValues(historyValues)
			.create(right, y, half, 20, t("history"), (b, v) -> this.working.maxHistory = v);
		historyButton.setTooltip(Tooltip.create(t("history.tooltip")));
		this.addRenderableWidget(historyButton);

		int labelWidth = 110;
		y += 24;
		EditBox pmPattern = this.textField(this.left + labelWidth, y, this.contentWidth - labelWidth, this.working.pmPattern, true,
			v -> this.working.pmPattern = v);
		pmPattern.setTooltip(Tooltip.create(t("pm_pattern.tooltip")));
		y += 22;
		EditBox pmCommand = this.textField(this.left + labelWidth, y, this.contentWidth - labelWidth, this.working.pmSendCommand, false,
			v -> this.working.pmSendCommand = v);
		pmCommand.setTooltip(Tooltip.create(t("pm_command.tooltip")));

		// --- Tabs ---
		int bottomButtonsY = this.height - 26;
		this.rowsPerPage = Math.max(1, (bottomButtonsY - 4 - TABS_TOP) / ROW_HEIGHT);
		int pages = Math.max(1, (this.working.tabs.size() + this.rowsPerPage - 1) / this.rowsPerPage);
		this.page = Math.min(this.page, pages - 1);

		int start = this.page * this.rowsPerPage;
		for (int i = start; i < Math.min(this.working.tabs.size(), start + this.rowsPerPage); i++) {
			this.addTabRow(i, TABS_TOP + (i - start) * ROW_HEIGHT);
		}

		// --- Untere Leiste ---
		int x = this.left;
		this.addRenderableWidget(Button.builder(t("add_tab"), b -> {
			this.working.tabs.add(new TabConfig("Neu", TabConfig.Type.CHANNEL, "", "", false));
			this.page = (this.working.tabs.size() - 1) / this.rowsPerPage;
			this.rebuildWidgets();
		}).bounds(x, bottomButtonsY, 70, 20).build());
		x += 72;
		Button prev = Button.builder(Component.literal("<"), b -> {
			this.page--;
			this.rebuildWidgets();
		}).bounds(x, bottomButtonsY, 20, 20).build();
		prev.active = this.page > 0;
		this.addRenderableWidget(prev);
		x += 22;
		Button next = Button.builder(Component.literal(">"), b -> {
			this.page++;
			this.rebuildWidgets();
		}).bounds(x, bottomButtonsY, 20, 20).build();
		next.active = this.page < pages - 1;
		this.addRenderableWidget(next);
		x += 22;
		Button reset = Button.builder(t("reset"), b -> {
			ChatTabsConfig defaults = ChatTabsConfig.createDefault();
			this.working.tabs = defaults.tabs;
			this.working.pmPattern = defaults.pmPattern;
			this.working.pmSendCommand = defaults.pmSendCommand;
			this.page = 0;
			this.rebuildWidgets();
		}).bounds(x, bottomButtonsY, 70, 20).build();
		reset.setTooltip(Tooltip.create(t("reset.tooltip")));
		this.addRenderableWidget(reset);

		int buttonWidth = Math.min(80, (this.left + this.contentWidth - x - 70 - 8) / 2);
		this.addRenderableWidget(Button.builder(t("cancel"), b -> this.onClose())
			.bounds(this.left + this.contentWidth - buttonWidth, bottomButtonsY, buttonWidth, 20).build());
		this.addRenderableWidget(Button.builder(t("save"), b -> this.saveAndClose())
			.bounds(this.left + this.contentWidth - buttonWidth * 2 - 4, bottomButtonsY, buttonWidth, 20).build());
	}

	private void addTabRow(int index, int y) {
		TabConfig tab = this.working.tabs.get(index);
		int nameWidth = 70;
		int typeWidth = 64;
		int prefixWidth = 52;
		int blinkWidth = 40;
		int smallWidth = 16;
		int patternWidth = this.contentWidth - nameWidth - typeWidth - prefixWidth - blinkWidth - smallWidth * 3 - 2 * 7;
		int x = this.left;

		EditBox name = this.textField(x + 1, y + 1, nameWidth - 2, tab.name, false, v -> tab.name = v.isBlank() ? "Tab" : v);
		name.setMaxLength(24);
		x += nameWidth + 2;

		CycleButton<TabConfig.Type> type = CycleButton.<TabConfig.Type>builder(v -> t("type." + v.name().toLowerCase()), tab.type)
			.withValues(TabConfig.Type.values())
			.displayOnlyValue()
			.create(x, y, typeWidth, 20, t("type"), (b, v) -> {
				tab.type = v;
				this.rebuildWidgets();
			});
		type.setTooltip(Tooltip.create(t("type.tooltip")));
		this.addRenderableWidget(type);
		x += typeWidth + 2;

		EditBox pattern = this.textField(x + 1, y + 1, patternWidth - 2, tab.pattern, true, v -> tab.pattern = v);
		pattern.setTooltip(Tooltip.create(t("pattern.tooltip")));
		pattern.active = tab.type == TabConfig.Type.CHANNEL;
		pattern.setEditable(tab.type == TabConfig.Type.CHANNEL);
		x += patternWidth + 2;

		EditBox prefix = this.textField(x + 1, y + 1, prefixWidth - 2, tab.sendPrefix, false, v -> tab.sendPrefix = v);
		prefix.setTooltip(Tooltip.create(t("prefix.tooltip")));
		x += prefixWidth + 2;

		CycleButton<Boolean> blink = CycleButton.onOffBuilder(tab.blink)
			.displayOnlyValue()
			.create(x, y, blinkWidth, 20, t("blink"), (b, v) -> tab.blink = v);
		blink.setTooltip(Tooltip.create(t("blink.tooltip")));
		this.addRenderableWidget(blink);
		x += blinkWidth + 2;

		Button up = Button.builder(Component.literal("↑"), b -> this.move(index, -1)).bounds(x, y, smallWidth, 20).build();
		up.active = index > 0;
		this.addRenderableWidget(up);
		x += smallWidth + 2;
		Button down = Button.builder(Component.literal("↓"), b -> this.move(index, 1)).bounds(x, y, smallWidth, 20).build();
		down.active = index < this.working.tabs.size() - 1;
		this.addRenderableWidget(down);
		x += smallWidth + 2;
		Button delete = Button.builder(Component.literal("✕"), b -> {
			this.working.tabs.remove(index);
			this.rebuildWidgets();
		}).bounds(x, y, smallWidth, 20).build();
		delete.setTooltip(Tooltip.create(t("delete")));
		this.addRenderableWidget(delete);
	}

	private void move(int index, int dir) {
		int target = index + dir;
		if (target < 0 || target >= this.working.tabs.size()) {
			return;
		}
		TabConfig tab = this.working.tabs.remove(index);
		this.working.tabs.add(target, tab);
		this.page = target / this.rowsPerPage;
		this.rebuildWidgets();
	}

	private EditBox textField(int x, int y, int width, String value, boolean regex, Consumer<String> onChange) {
		EditBox box = new EditBox(this.font, x, y, width, 18, Component.empty());
		box.setMaxLength(1024);
		box.setValue(value);
		if (regex) {
			box.setTextColor(isValidRegex(value) ? TEXT_OK : TEXT_ERROR);
		}
		box.setResponder(v -> {
			onChange.accept(v);
			if (regex) {
				box.setTextColor(isValidRegex(v) ? TEXT_OK : TEXT_ERROR);
			}
		});
		return this.addRenderableWidget(box);
	}

	private static boolean isValidRegex(String regex) {
		try {
			Pattern.compile(regex);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	private void saveAndClose() {
		ChatTabs.get().apply(this.working.copy());
		ChatTabs.get().config().save();
		this.onClose();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

		int y = 28 + 22 * 3 + 2;
		graphics.text(this.font, t("pm_pattern"), this.left, y + 5, LABEL_COLOR);
		graphics.text(this.font, t("pm_command"), this.left, y + 22 + 5, LABEL_COLOR);

		int headerY = TABS_TOP - 11;
		int x = this.left;
		graphics.text(this.font, t("col.name"), x, headerY, LABEL_COLOR);
		x += 72;
		graphics.text(this.font, t("col.type"), x, headerY, LABEL_COLOR);
		x += 66;
		graphics.text(this.font, t("col.pattern"), x, headerY, LABEL_COLOR);
		int patternWidth = this.contentWidth - 70 - 64 - 52 - 40 - 16 * 3 - 14;
		x += patternWidth + 2;
		graphics.text(this.font, t("col.prefix"), x, headerY, LABEL_COLOR);
		x += 54;
		graphics.text(this.font, t("col.blink"), x, headerY, LABEL_COLOR);

		int pages = Math.max(1, (this.working.tabs.size() + this.rowsPerPage - 1) / this.rowsPerPage);
		if (pages > 1) {
			graphics.text(this.font, (this.page + 1) + "/" + pages, this.left + 72 + 44 + 4 + 72 + 4, this.height - 20, LABEL_COLOR);
		}
	}
}
