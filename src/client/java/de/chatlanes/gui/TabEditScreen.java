package de.chatlanes.gui;

import de.chatlanes.ChatTab;
import de.chatlanes.config.ChatLanesConfig.TabConfig;
import de.chatlanes.config.TabColor;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Einen einzelnen Tab bearbeiten – mit Live-Test, ob eine Beispielnachricht in diesem Tab landen würde. */
class TabEditScreen extends BaseSettingsScreen {
	private static String lastTestMessage;

	private final ChatLanesConfigScreen main;
	private final TabConfig original;
	private final TabConfig edited;
	private final boolean isNew;
	private int resultY;
	private int testY;

	TabEditScreen(ChatLanesConfigScreen main, TabConfig tab, boolean isNew) {
		super(Component.translatable(isNew ? "chatlanes.config.edit.title_new" : "chatlanes.config.edit.title"), main);
		this.main = main;
		this.original = tab;
		this.edited = tab.copy();
		this.isNew = isNew;
	}

	@Override
	protected void init() {
		if (lastTestMessage == null) {
			// Beispiel passend zum Tab, z. B. "[Server] [T] Player: Hello" für die Kennung [T]
			String tag = this.edited.match == TabConfig.Match.TAG && !this.edited.pattern.isBlank() ? this.edited.pattern.trim() : "[G]";
			lastTestMessage = t("example.tab", tag).getString();
		}
		this.beginLayout(360);
		int y = TOP;
		int half = this.halfWidth();
		boolean channel = this.edited.type == TabConfig.Type.CHANNEL;

		EditBox name = this.textRow(y, t("edit.name"), this.edited.name, null, null, v -> this.edited.name = v);
		name.setMaxLength(24);
		// Name-Feld halbieren, rechts daneben die Uhrzeit-Einstellung des Tabs.
		int fieldWidth = this.contentWidth - this.labelWidth;
		name.setWidth(fieldWidth / 2 - 2);
		var timestamps = timestampMode(this.edited.timestampsOrDefault())
			.create(this.left + this.labelWidth + fieldWidth / 2 + 2, y, fieldWidth - fieldWidth / 2 - 2, 20, t("timestamps_short"),
				(b, v) -> this.edited.timestamps = v);
		timestamps.setTooltip(Tooltip.create(t("timestamp_mode.tooltip")));
		this.addRenderableWidget(timestamps);
		y += ROW;

		CycleButton<TabConfig.Type> type = CycleButton.<TabConfig.Type>builder(v -> t("type." + v.name().toLowerCase()), this.edited.type)
			.withValues(TabConfig.Type.values())
			.create(this.left, y, half, 20, t("type"), (b, v) -> {
				this.edited.type = v;
				this.rebuildWidgets();
			});
		type.setTooltip(Tooltip.create(t("type.tooltip")));
		this.addRenderableWidget(type);

		CycleButton<TabConfig.Match> match = CycleButton.<TabConfig.Match>builder(v -> t("match." + v.name().toLowerCase()), this.edited.match)
			.withValues(TabConfig.Match.MAIN)
			.create(this.rightHalfX(), y, half, 20, t("match"), (b, v) -> {
				this.edited.match = v;
				this.rebuildWidgets();
			});
		match.setTooltip(Tooltip.create(t("match.tooltip")));
		match.active = channel;
		this.addRenderableWidget(match);
		y += ROW;

		if (channel) {
			String key = "edit.value." + this.edited.match.name().toLowerCase();
			EditBox value = this.textRow(y, t(key), this.edited.pattern, t(key + ".hint"), t(key + ".tooltip"), v -> this.edited.pattern = v);
			value.setResponder(v -> {
				this.edited.pattern = v;
				value.setTextColor(this.isValueValid() ? 0xFFE0E0E0 : ERROR_COLOR);
			});
			value.setTextColor(this.isValueValid() ? 0xFFE0E0E0 : ERROR_COLOR);
		} else {
			this.label(t(this.edited.type == TabConfig.Type.SYSTEM ? "edit.info.system" : "edit.info.all"), this.left, y + 6);
		}
		y += ROW;

		EditBox switchCommand = this.textRow(y, t("edit.switch"), this.edited.switchCommand == null ? "" : this.edited.switchCommand,
			t("edit.switch.hint"), t("edit.switch.tooltip"), v -> this.edited.switchCommand = v);
		y += ROW;

		this.textRow(y, t("edit.prefix"), this.edited.sendPrefix, t("edit.prefix.hint"), t("prefix.tooltip"), v -> this.edited.sendPrefix = v);
		y += ROW;

		// Drei Schalter nebeneinander: Blinken | Zähler | Farbe
		int third = (this.contentWidth - 8) / 3;
		CycleButton<Boolean> blink = onOff(this.edited.blink)
			.create(this.left, y, third, 20, t("blink"), (b, v) -> this.edited.blink = v);
		blink.setTooltip(Tooltip.create(t("blink.tooltip")));
		this.addRenderableWidget(blink);
		CycleButton<Boolean> counter = onOff(this.edited.showsUnread())
			.create(this.left + third + 4, y, third, 20, t("counter"), (b, v) -> this.edited.showUnread = v);
		counter.setTooltip(Tooltip.create(t("counter.tooltip")));
		this.addRenderableWidget(counter);
		this.addRenderableWidget(CycleButton.<TabColor>builder(c -> Component.translatable(c.translationKey()).withColor(c.argb()), this.edited.color)
			.withValues(TabColor.values())
			.create(this.left + (third + 4) * 2, y, this.contentWidth - (third + 4) * 2, 20, t("color"), (b, v) -> this.edited.color = v));
		y += ROW;

		this.soundRow(y, this.edited.soundOrNone(), this.edited.volumeOrDefault(), v -> this.edited.sound = v, v -> this.edited.volume = v);
		y += ROW;

		if (channel) {
			// Weitere Nachrichten (die sonst im System-Tab landen würden) diesem Kanal zuordnen
			Button extra = Button.builder(t("extra.button", this.edited.extraRulesOrEmpty().size()),
					b -> this.minecraft.gui.setScreen(new ExtraRulesScreen(this, this.edited)))
				.bounds(this.left, y, this.contentWidth, 20).build();
			extra.setTooltip(Tooltip.create(t("extra.tooltip")));
			this.addRenderableWidget(extra);
			y += ROW;
		}
		y += 4;

		// Live-Test nur, wenn noch Platz über den unteren Knöpfen ist (sehr kleine Fenster: weglassen).
		if (channel && y + 20 <= this.height - 30) {
			// Beschriftung zeichnet extractRenderState selbst (bei wenig Platz direkt mit Ergebnis).
			this.textRow(y, null, lastTestMessage, null, t("edit.test.tooltip"), v -> lastTestMessage = v);
			this.testY = y;
			this.resultY = y + ROW;
		} else {
			this.resultY = -1;
		}

		int bottomY = this.height - 26;
		this.addRenderableWidget(Button.builder(t("done"), b -> this.confirm())
			.bounds(this.left, bottomY, half, 20).build());
		this.addRenderableWidget(Button.builder(t("cancel"), b -> this.onClose())
			.bounds(this.rightHalfX(), bottomY, half, 20).build());
	}

	private boolean isValueValid() {
		if (this.edited.match != TabConfig.Match.REGEX) {
			return true;
		}
		try {
			Pattern.compile(this.edited.pattern);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	private void confirm() {
		if (this.edited.name == null || this.edited.name.isBlank()) {
			this.edited.name = "Tab";
		}
		if (this.isNew) {
			this.main.addTab(this.edited);
		} else {
			if (this.original.name.equals(this.main.working.defaultTab)) {
				this.main.working.defaultTab = this.edited.name;
			}
			this.original.name = this.edited.name;
			this.original.type = this.edited.type;
			this.original.match = this.edited.match;
			this.original.pattern = this.edited.pattern;
			this.original.sendPrefix = this.edited.sendPrefix;
			this.original.switchCommand = this.edited.switchCommand;
			this.original.blink = this.edited.blink;
			this.original.showUnread = this.edited.showUnread;
			this.original.sound = this.edited.sound;
			this.original.timestamps = this.edited.timestamps;
			this.original.volume = this.edited.volume;
			this.original.color = this.edited.color;
			this.original.extraRules = this.edited.extraRules;
			this.original.extraAlsoInSystem = this.edited.extraAlsoInSystem;
		}
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.resultY < 0) {
			return;
		}

		Component result;
		int color;
		boolean matches = false;
		if (!this.isValueValid()) {
			result = t("edit.test.invalid");
			color = ERROR_COLOR;
		} else if (this.edited.pattern.isEmpty() && this.edited.extraRulesOrEmpty().isEmpty()) {
			result = t("edit.test.empty");
			color = LABEL_COLOR;
		} else {
			Pattern pattern = ChatTab.buildPattern(this.edited.match, this.edited.pattern);
			matches = (pattern != null && pattern.matcher(lastTestMessage).find()) || ExtraRulesScreen.matchesAny(this.edited, lastTestMessage);
			result = t(matches ? "edit.test.match" : "edit.test.no_match", this.edited.name);
			color = matches ? OK_COLOR : ERROR_COLOR;
		}
		if (this.resultY <= this.height - 38) {
			// Genug Platz: Beschriftung links, ausführliches Ergebnis darunter.
			graphics.text(this.font, t("edit.test"), this.left, this.testY + 6, LABEL_COLOR);
			graphics.text(this.font, result, this.left + this.labelWidth, this.resultY, color);
		} else {
			// Wenig Platz (kleines Fenster/große GUI): Ergebnis kurz in der Beschriftung, z. B. "Test: ✔".
			graphics.text(this.font, t(matches ? "edit.test.short_ok" : "edit.test.short_fail"), this.left, this.testY + 6, color);
		}
	}
}
