package de.chattabs.gui;

import de.chattabs.config.ChatTabsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Allgemeine Einstellungen: Position der Tab-Leiste, Verhalten bei geschlossenem Chat, Verlauf. */
class GeneralSettingsScreen extends BaseSettingsScreen {
	private final ChatTabsConfig working;

	GeneralSettingsScreen(ChatTabsConfigScreen main) {
		super(Component.translatable("chattabs.config.general.title"), main);
		this.working = main.working;
	}

	@Override
	protected void init() {
		this.beginLayout(310);
		int y = TOP;
		int w = this.contentWidth;

		this.addRenderableWidget(CycleButton.<ChatTabsConfig.Position>builder(
				p -> t(p == ChatTabsConfig.Position.TOP ? "position.top" : "position.bottom"), this.working.position)
			.withValues(ChatTabsConfig.Position.values())
			.create(this.left, y, w, 20, t("position"), (b, v) -> this.working.position = v));
		y += ROW;

		CycleButton<ChatTabsConfig.ClosedChatMode> closed = CycleButton.<ChatTabsConfig.ClosedChatMode>builder(
				m -> t("closed." + m.name().toLowerCase()), this.working.showWhenClosed)
			.withValues(ChatTabsConfig.ClosedChatMode.values())
			.create(this.left, y, w, 20, t("closed"), (b, v) -> this.working.showWhenClosed = v);
		closed.setTooltip(Tooltip.create(t("closed.tooltip")));
		this.addRenderableWidget(closed);
		y += ROW;

		CycleButton<Boolean> unread = CycleButton.onOffBuilder(this.working.showUnreadCount)
			.create(this.left, y, w, 20, t("unread_count"), (b, v) -> this.working.showUnreadCount = v);
		unread.setTooltip(Tooltip.create(t("unread_count.tooltip")));
		this.addRenderableWidget(unread);
		y += ROW;

		List<Integer> values = new ArrayList<>(List.of(100, 250, 500, 1000, 2000, 5000));
		if (!values.contains(this.working.maxHistory)) {
			values.add(this.working.maxHistory);
			values.sort(Integer::compare);
		}
		CycleButton<Integer> history = CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)), this.working.maxHistory)
			.withValues(values)
			.create(this.left, y, w, 20, t("history"), (b, v) -> this.working.maxHistory = v);
		history.setTooltip(Tooltip.create(t("history.tooltip")));
		this.addRenderableWidget(history);

		this.addRenderableWidget(Button.builder(t("back"), b -> this.onClose())
			.bounds(this.left, this.height - 26, w, 20).build());
	}
}
