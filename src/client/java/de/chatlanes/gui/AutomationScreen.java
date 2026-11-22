package de.chatlanes.gui;

import de.chatlanes.config.ChatLanesConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Automatisches Tab-Verhalten: Standard-Tab, automatisch zurück, System-Ausgabe kurz anzeigen. */
class AutomationScreen extends BaseSettingsScreen {
	private final ChatLanesConfig working;

	AutomationScreen(ChatLanesConfigScreen main) {
		super(Component.translatable("chatlanes.config.automation.title"), main);
		this.working = main.working;
	}

	@Override
	protected void init() {
		this.beginLayout(380);
		int y = TOP;
		int w = this.contentWidth;
		int half = this.halfWidth();

		// --- Standard-Tab ---
		this.label(t("automation.default_header"), this.left, y + 2);
		y += 12;
		List<String> names = new ArrayList<>();
		for (var tab : this.working.tabs) {
			if (!tab.isWindow() && !names.contains(tab.name)) {
				names.add(tab.name);
			}
		}
		if (!names.isEmpty()) {
			String current = names.contains(this.working.defaultTab) ? this.working.defaultTab : names.getFirst();
			CycleButton<String> defaultTab = CycleButton.<String>builder(Component::literal, current)
				.withValues(names)
				.create(this.left, y, half, 20, t("default_tab"), (b, v) -> this.working.defaultTab = v);
			defaultTab.setTooltip(Tooltip.create(t("default_tab.tooltip")));
			this.addRenderableWidget(defaultTab);
		}
		// Automatisch zurück in den Standard-Tab nach einer Zeit ohne eigene Nachricht
		int quarter = (half - 4) / 2;
		CycleButton<Boolean> autoReturn = onOff(this.working.autoReturn)
			.create(this.rightHalfX(), y, quarter, 20, t("auto_return"), (b, v) -> this.working.autoReturn = v);
		autoReturn.setTooltip(Tooltip.create(t("auto_return.tooltip")));
		this.addRenderableWidget(autoReturn);
		TreeSet<Integer> times = new TreeSet<>(List.of(10, 15, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800, 3600));
		times.add(this.working.autoReturnSeconds);
		CycleButton<Integer> returnTime = CycleButton.<Integer>builder(BaseSettingsScreen::formatDuration, this.working.autoReturnSeconds)
			.withValues(List.copyOf(times))
			.displayOnlyValue()
			.create(this.rightHalfX() + quarter + 4, y, half - quarter - 4, 20, t("auto_return.time"), (b, v) -> this.working.autoReturnSeconds = v);
		returnTime.setTooltip(Tooltip.create(t("auto_return.tooltip")));
		this.addRenderableWidget(returnTime);
		y += ROW + 6;

		// --- System-Ausgabe kurz anzeigen ---
		this.label(t("peek.header"), this.left, y + 2);
		y += 12;
		CycleButton<Boolean> peek = onOff(this.working.peekSystem)
			.create(this.left, y, w, 20, t("peek"), (b, v) -> this.working.peekSystem = v);
		peek.setTooltip(Tooltip.create(t("peek.tooltip")));
		this.addRenderableWidget(peek);
		y += ROW;

		TreeSet<Integer> stay = new TreeSet<>(List.of(3, 5, 8, 10, 15, 20, 30, 45, 60, 90, 120, 300));
		stay.add(this.working.peekSeconds);
		CycleButton<Integer> peekTime = CycleButton.<Integer>builder(BaseSettingsScreen::formatDuration, this.working.peekSeconds)
			.withValues(List.copyOf(stay))
			.create(this.left, y, half, 20, t("peek.time"), (b, v) -> this.working.peekSeconds = v);
		peekTime.setTooltip(Tooltip.create(t("peek.time.tooltip")));
		this.addRenderableWidget(peekTime);
		TreeSet<Double> reaction = new TreeSet<>(List.of(0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 8.0, 10.0));
		reaction.add(this.working.peekReaction);
		CycleButton<Double> reactionTime = CycleButton.<Double>builder(BaseSettingsScreen::secondsLabel, this.working.peekReaction)
			.withValues(List.copyOf(reaction))
			.create(this.rightHalfX(), y, half, 20, t("peek.reaction"), (b, v) -> this.working.peekReaction = v);
		reactionTime.setTooltip(Tooltip.create(t("peek.reaction.tooltip")));
		this.addRenderableWidget(reactionTime);
		y += ROW;

		CycleButton<Boolean> blocks = onOff(this.working.peekBlockClicks)
			.create(this.left, y, half, 20, t("peek.blocks"), (b, v) -> this.working.peekBlockClicks = v);
		blocks.setTooltip(Tooltip.create(t("peek.blocks.tooltip")));
		this.addRenderableWidget(blocks);
		int pickWidth = 60;
		EditBox items = new EditBox(this.font, this.rightHalfX(), y + 1, half - pickWidth - 4, 18, t("peek.items"));
		items.setMaxLength(512);
		items.setValue(this.working.peekItems);
		items.moveCursorToStart(false);
		items.setHint(t("peek.items.hint"));
		items.setTooltip(Tooltip.create(this.working.peekItems.isBlank() ? t("peek.items.tooltip")
			: t("peek.items.tooltip").append("\n\n").append(t("peek.items.current", this.working.peekItems))));
		items.setResponder(v -> this.working.peekItems = v);
		this.addRenderableWidget(items);
		// Auswahl-Menü mit allen Items
		Button pick = Button.builder(t("peek.items.pick"), b -> this.minecraft.gui.setScreen(
				new ItemPickerScreen(this, this.working.peekItems, v -> this.working.peekItems = v)))
			.bounds(this.rightHalfX() + half - pickWidth, y, pickWidth, 20).build();
		pick.setTooltip(Tooltip.create(t("peek.items.pick.tooltip")));
		this.addRenderableWidget(pick);
		y += ROW;

		CycleButton<Boolean> returnTo = CycleButton.booleanBuilder(t("peek.return.default"), t("peek.return.previous"), this.working.peekReturnToDefault)
			.create(this.left, y, half, 20, t("peek.return"), (b, v) -> this.working.peekReturnToDefault = v);
		returnTo.setTooltip(Tooltip.create(t("peek.return.tooltip")));
		this.addRenderableWidget(returnTo);
		CycleButton<Boolean> replay = onOff(this.working.peekReplay)
			.create(this.rightHalfX(), y, half, 20, t("peek.replay_short"), (b, v) -> this.working.peekReplay = v);
		replay.setTooltip(Tooltip.create(t("peek.replay.tooltip")));
		this.addRenderableWidget(replay);
		y += ROW;

		this.textRow(y, t("peek.ignored"), this.working.peekIgnoredCommands, t("peek.ignored.hint"), t("peek.ignored.tooltip"),
			v -> this.working.peekIgnoredCommands = v);

		this.addRenderableWidget(Button.builder(t("back"), b -> this.onClose())
			.bounds(this.left, this.height - 26, w, 20).build());
	}
}
