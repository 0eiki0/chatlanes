package de.chattabs.gui;

import de.chattabs.ChatTab;
import de.chattabs.config.ChatTabsConfig.TabConfig;
import de.chattabs.config.TabColor;
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
	private static String lastTestMessage = "[Freebuild] [G] Levlox: XD";

	private final ChatTabsConfigScreen main;
	private final TabConfig original;
	private final TabConfig edited;
	private final boolean isNew;
	private int resultY;

	TabEditScreen(ChatTabsConfigScreen main, TabConfig tab, boolean isNew) {
		super(Component.translatable(isNew ? "chattabs.config.edit.title_new" : "chattabs.config.edit.title"), main);
		this.main = main;
		this.original = tab;
		this.edited = tab.copy();
		this.isNew = isNew;
	}

	@Override
	protected void init() {
		this.beginLayout(360);
		int y = TOP;
		int half = this.halfWidth();
		boolean channel = this.edited.type == TabConfig.Type.CHANNEL;

		EditBox name = this.textRow(y, t("edit.name"), this.edited.name, null, null, v -> this.edited.name = v);
		name.setMaxLength(24);
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
			.withValues(TabConfig.Match.values())
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

		CycleButton<Boolean> blink = CycleButton.onOffBuilder(this.edited.blink)
			.create(this.left, y, half, 20, t("blink"), (b, v) -> this.edited.blink = v);
		blink.setTooltip(Tooltip.create(t("blink.tooltip")));
		this.addRenderableWidget(blink);
		this.addRenderableWidget(CycleButton.<TabColor>builder(c -> Component.translatable(c.translationKey()).withColor(c.argb()), this.edited.color)
			.withValues(TabColor.values())
			.create(this.rightHalfX(), y, half, 20, t("color"), (b, v) -> this.edited.color = v));
		y += ROW + 4;

		if (channel) {
			this.textRow(y, t("edit.test"), lastTestMessage, null, t("edit.test.tooltip"), v -> lastTestMessage = v);
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
			this.original.name = this.edited.name;
			this.original.type = this.edited.type;
			this.original.match = this.edited.match;
			this.original.pattern = this.edited.pattern;
			this.original.sendPrefix = this.edited.sendPrefix;
			this.original.switchCommand = this.edited.switchCommand;
			this.original.blink = this.edited.blink;
			this.original.color = this.edited.color;
		}
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.resultY < 0 || this.resultY > this.height - 38) {
			return;
		}

		Component result;
		int color;
		if (!this.isValueValid()) {
			result = t("edit.test.invalid");
			color = ERROR_COLOR;
		} else if (this.edited.pattern.isEmpty()) {
			result = t("edit.test.empty");
			color = LABEL_COLOR;
		} else {
			Pattern pattern = ChatTab.buildPattern(this.edited.match, this.edited.pattern);
			boolean matches = pattern != null && pattern.matcher(lastTestMessage).find();
			result = t(matches ? "edit.test.match" : "edit.test.no_match", this.edited.name);
			color = matches ? OK_COLOR : ERROR_COLOR;
		}
		graphics.text(this.font, result, this.left + this.labelWidth, this.resultY, color);
	}
}
