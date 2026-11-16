package de.chattabs.gui;

import de.chattabs.ModLanguage;
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

		// Sprache – Beschriftung absichtlich zweisprachig, damit man sie in jeder Sprache findet.
		CycleButton<ChatTabsConfig.Language> language = CycleButton.<ChatTabsConfig.Language>builder(
				l -> t("language." + l.name().toLowerCase()), this.working.language)
			.withValues(ChatTabsConfig.Language.values())
			.create(this.left, y, w, 20, Component.literal("Sprache / Language"), (b, v) -> {
				this.working.language = v;
				// Sofort anzeigen; wirksam gespeichert wird erst mit "Speichern".
				ModLanguage.setPreview(v);
				this.rebuildWidgets();
			});
		language.setTooltip(Tooltip.create(t("language.tooltip")));
		this.addRenderableWidget(language);
		y += ROW;

		this.addRenderableWidget(CycleButton.<ChatTabsConfig.Position>builder(
				p -> t(p == ChatTabsConfig.Position.TOP ? "position.top" : "position.bottom"), this.working.position)
			.withValues(ChatTabsConfig.Position.values())
			.create(this.left, y, w, 20, t("position"), (b, v) -> this.working.position = v));
		y += ROW;

		CycleButton<Boolean> fixed = onOff(this.working.fixedChatSize)
			.create(this.left, y, w, 20, t("fixed_size"), (b, v) -> this.working.fixedChatSize = v);
		fixed.setTooltip(Tooltip.create(t("fixed_size.tooltip")));
		this.addRenderableWidget(fixed);
		y += ROW;

		CycleButton<ChatTabsConfig.ClosedChatMode> closed = CycleButton.<ChatTabsConfig.ClosedChatMode>builder(
				m -> t("closed." + m.name().toLowerCase()), this.working.showWhenClosed)
			.withValues(ChatTabsConfig.ClosedChatMode.values())
			.create(this.left, y, w, 20, t("closed"), (b, v) -> this.working.showWhenClosed = v);
		closed.setTooltip(Tooltip.create(t("closed.tooltip")));
		this.addRenderableWidget(closed);
		y += ROW;

		CycleButton<Boolean> unread = onOff(this.working.showUnreadCount)
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
		y += ROW;

		// Uhrzeit vor Nachrichten: an/aus + Format
		CycleButton<Boolean> timestamps = onOff(this.working.timestamps)
			.create(this.left, y, this.halfWidth(), 20, t("timestamps"), (b, v) -> this.working.timestamps = v);
		timestamps.setTooltip(Tooltip.create(t("timestamps.tooltip")));
		this.addRenderableWidget(timestamps);
		this.addRenderableWidget(CycleButton.<ChatTabsConfig.TimestampFormat>builder(
				f -> Component.literal(java.time.format.DateTimeFormatter.ofPattern(f.pattern).format(java.time.LocalTime.now())), this.working.timestampFormat)
			.withValues(ChatTabsConfig.TimestampFormat.values())
			.create(this.rightHalfX(), y, this.halfWidth(), 20, t("timestamp_format"), (b, v) -> this.working.timestampFormat = v));

		this.addRenderableWidget(Button.builder(t("back"), b -> this.onClose())
			.bounds(this.left, this.height - 26, w, 20).build());
	}
}
