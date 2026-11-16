package de.chattabs.gui;

import de.chattabs.ChatHistory;
import de.chattabs.config.ChatTabsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Kanalwechsel beim Tab-Klick und gespeicherter Chatverlauf. */
class ChannelHistoryScreen extends BaseSettingsScreen {
	private final ChatTabsConfig working;
	private boolean confirmDelete;
	private Component status = Component.empty();
	private int statusY;

	ChannelHistoryScreen(ChatTabsConfigScreen main) {
		super(Component.translatable("chattabs.config.channels.title"), main);
		this.working = main.working;
	}

	@Override
	protected void init() {
		this.beginLayout(360);
		int y = TOP;
		int w = this.contentWidth;

		this.label(t("channels.switch_header"), this.left, y + 2);
		y += 12;
		CycleButton<Boolean> switchOnTab = onOff(this.working.switchChannelOnTab)
			.create(this.left, y, w, 20, t("switch_on_tab"), (b, v) -> this.working.switchChannelOnTab = v);
		switchOnTab.setTooltip(Tooltip.create(t("switch_on_tab.tooltip")));
		this.addRenderableWidget(switchOnTab);
		y += ROW;

		EditBox hide = this.textRow(y, t("hide_confirm"), this.working.hideSwitchConfirmation, t("hide_confirm.hint"), t("hide_confirm.tooltip"),
			v -> this.working.hideSwitchConfirmation = v);
		hide.setMaxLength(512);
		y += ROW;

		// Standard-Tab (bei jedem Spielstart ausgewählt)
		java.util.List<String> names = new java.util.ArrayList<>();
		for (var tab : this.working.tabs) {
			if (!names.contains(tab.name)) {
				names.add(tab.name);
			}
		}
		if (!names.isEmpty()) {
			String current = names.contains(this.working.defaultTab) ? this.working.defaultTab : names.getFirst();
			CycleButton<String> defaultTab = CycleButton.<String>builder(Component::literal, current)
				.withValues(names)
				.create(this.left, y, w, 20, t("default_tab"), (b, v) -> this.working.defaultTab = v);
			defaultTab.setTooltip(Tooltip.create(t("default_tab.tooltip")));
			this.addRenderableWidget(defaultTab);
			y += ROW;
		}

		// Automatisch zurück in den Standard-Tab nach einer Zeit ohne eigene Nachricht
		int half = this.halfWidth();
		CycleButton<Boolean> autoReturn = onOff(this.working.autoReturn)
			.create(this.left, y, half, 20, t("auto_return"), (b, v) -> this.working.autoReturn = v);
		autoReturn.setTooltip(Tooltip.create(t("auto_return.tooltip")));
		this.addRenderableWidget(autoReturn);
		java.util.TreeSet<Integer> times = new java.util.TreeSet<>(java.util.List.of(10, 15, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800, 3600));
		times.add(this.working.autoReturnSeconds);
		CycleButton<Integer> returnTime = CycleButton.<Integer>builder(ChannelHistoryScreen::formatDuration, this.working.autoReturnSeconds)
			.withValues(java.util.List.copyOf(times))
			.create(this.rightHalfX(), y, half, 20, t("auto_return.time"), (b, v) -> this.working.autoReturnSeconds = v);
		returnTime.setTooltip(Tooltip.create(t("auto_return.tooltip")));
		this.addRenderableWidget(returnTime);
		y += ROW + 8;

		this.label(t("channels.history_header"), this.left, y + 2);
		y += 12;
		CycleButton<Boolean> save = onOff(this.working.saveHistory)
			.create(this.left, y, w, 20, t("save_history"), (b, v) -> this.working.saveHistory = v);
		save.setTooltip(Tooltip.create(t("save_history.tooltip")));
		this.addRenderableWidget(save);
		y += ROW;

		Button delete = Button.builder(this.confirmDelete ? t("delete_history.confirm") : t("delete_history"), b -> {
			if (!this.confirmDelete) {
				this.confirmDelete = true;
			} else {
				int deleted = ChatHistory.get().deleteAll();
				this.confirmDelete = false;
				this.status = t("delete_history.done", deleted);
			}
			this.rebuildWidgets();
		}).bounds(this.left, y, w, 20).build();
		delete.setTooltip(Tooltip.create(t("delete_history.tooltip")));
		this.addRenderableWidget(delete);
		y += ROW;
		this.statusY = y + 2;

		this.addRenderableWidget(Button.builder(t("back"), b -> this.onClose())
			.bounds(this.left, this.height - 26, w, 20).build());
	}

	/** 45 → "45 s", 120 → "2 min", 90 → "1:30 min" */
	private static Component formatDuration(int seconds) {
		if (seconds < 60) {
			return Component.literal(seconds + " s");
		}
		int minutes = seconds / 60;
		int rest = seconds % 60;
		return Component.literal(rest == 0 ? minutes + " min" : minutes + ":" + String.format("%02d", rest) + " min");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.statusY > this.height - 38) {
			return;
		}
		Component info = this.status.getString().isEmpty()
			? t("history_info", ChatHistory.savedFileCount(), this.working.maxHistory)
			: this.status;
		graphics.text(this.font, info, this.left, this.statusY, LABEL_COLOR);
	}
}
