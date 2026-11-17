package de.chatlanes.gui;

import de.chatlanes.ChatLanes;
import de.chatlanes.config.ChatLanesConfig;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Einstellungen für Msg-Tabs (private Nachrichten), inklusive Live-Test der Erkennung. */
class PrivateMessagesScreen extends BaseSettingsScreen {
	private static String lastTestMessage;

	private final ChatLanesConfig working;
	private int resultY;

	PrivateMessagesScreen(ChatLanesConfigScreen main) {
		super(Component.translatable("chatlanes.config.pm.title"), main);
		this.working = main.working;
	}

	@Override
	protected void init() {
		if (lastTestMessage == null) {
			lastTestMessage = t("example.pm").getString();
		}
		this.beginLayout(360);
		int y = TOP;
		int half = this.halfWidth();

		this.addRenderableWidget(onOff(this.working.pmTabs)
			.create(this.left, y, half, 20, t("pm_tabs"), (b, v) -> this.working.pmTabs = v));
		this.addRenderableWidget(onOff(this.working.pmBlink)
			.create(this.rightHalfX(), y, half, 20, t("pm_blink"), (b, v) -> this.working.pmBlink = v));
		y += ROW;

		CycleButton<Boolean> close = onOff(this.working.pmCloseOnDisconnect)
			.create(this.left, y, half, 20, t("pm_close"), (b, v) -> this.working.pmCloseOnDisconnect = v);
		close.setTooltip(Tooltip.create(t("pm_close.tooltip")));
		this.addRenderableWidget(close);
		var timestamps = timestampMode(this.working.pmTimestamps)
			.create(this.rightHalfX(), y, half, 20, t("timestamps_short"), (b, v) -> this.working.pmTimestamps = v);
		timestamps.setTooltip(Tooltip.create(t("timestamp_mode.tooltip")));
		this.addRenderableWidget(timestamps);
		y += ROW;

		this.soundRow(y, this.working.pmSoundType, this.working.pmVolume, v -> this.working.pmSoundType = v, v -> this.working.pmVolume = v);
		y += ROW + 4;

		this.textRow(y, t("pm_command"), this.working.pmSendCommand, t("pm_command.hint"), t("pm_command.tooltip"),
			v -> this.working.pmSendCommand = v);
		y += ROW;

		EditBox pattern = this.textRow(y, t("pm_pattern"), this.working.pmPattern, null, t("pm_pattern.tooltip"), v -> this.working.pmPattern = v);
		pattern.setResponder(v -> {
			this.working.pmPattern = v;
			pattern.setTextColor(isValidRegex(v) ? 0xFFE0E0E0 : ERROR_COLOR);
		});
		pattern.setTextColor(isValidRegex(this.working.pmPattern) ? 0xFFE0E0E0 : ERROR_COLOR);
		y += ROW;

		this.addRenderableWidget(Button.builder(t("pm_pattern.reset"), b -> {
			this.working.pmPattern = new ChatLanesConfig().pmPattern;
			this.rebuildWidgets();
		}).bounds(this.left + this.labelWidth, y, this.contentWidth - this.labelWidth, 20).build());
		y += ROW + 6;

		this.textRow(y, t("edit.test"), lastTestMessage, null, t("pm_test.tooltip"), v -> lastTestMessage = v);
		this.resultY = y + ROW;

		this.addRenderableWidget(Button.builder(t("back"), b -> this.onClose())
			.bounds(this.left, this.height - 26, this.contentWidth, 20).build());
	}

	private static boolean isValidRegex(String regex) {
		try {
			Pattern.compile(regex);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.resultY > this.height - 38) {
			return;
		}
		Component result;
		int color;
		if (!isValidRegex(this.working.pmPattern)) {
			result = t("edit.test.invalid");
			color = ERROR_COLOR;
		} else {
			String player = ChatLanes.testPrivateMessage(this.working.pmPattern, lastTestMessage);
			result = player != null ? t("pm_test.match", player) : t("pm_test.no_match");
			color = player != null ? OK_COLOR : ERROR_COLOR;
		}
		graphics.text(this.font, result, this.left + this.labelWidth, this.resultY, color);
	}
}
