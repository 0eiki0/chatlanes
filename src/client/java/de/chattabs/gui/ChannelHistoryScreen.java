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
		CycleButton<Boolean> switchOnTab = CycleButton.onOffBuilder(this.working.switchChannelOnTab)
			.create(this.left, y, w, 20, t("switch_on_tab"), (b, v) -> this.working.switchChannelOnTab = v);
		switchOnTab.setTooltip(Tooltip.create(t("switch_on_tab.tooltip")));
		this.addRenderableWidget(switchOnTab);
		y += ROW;

		EditBox hide = this.textRow(y, t("hide_confirm"), this.working.hideSwitchConfirmation, t("hide_confirm.hint"), t("hide_confirm.tooltip"),
			v -> this.working.hideSwitchConfirmation = v);
		hide.setMaxLength(512);
		y += ROW + 8;

		this.label(t("channels.history_header"), this.left, y + 2);
		y += 12;
		CycleButton<Boolean> save = CycleButton.onOffBuilder(this.working.saveHistory)
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
