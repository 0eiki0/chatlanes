package de.chatlanes.gui;

import de.chatlanes.ChatWindows;
import de.chatlanes.config.ChatLanesConfig.TabConfig;
import de.chatlanes.config.ChatLanesConfig.WindowLayout;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Aussehen eines eigenen Chatfensters: Textgröße, Hintergrund, Anzeige bei geschlossenem Chat. */
class WindowSettingsScreen extends BaseSettingsScreen {
	/** Platzhalter für "wie der Chat" (null in der Config). */
	private static final int DEFAULT = -100;
	private static final List<Integer> SCALES = List.of(DEFAULT, 50, 60, 70, 75, 80, 90, 100, 110, 125, 150);
	private static final List<Integer> OPACITIES = List.of(DEFAULT, 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
	private static final List<Integer> DISPLAY = List.of(DEFAULT, WindowLayout.ALWAYS, WindowLayout.FOCUSED_ONLY, 5, 10, 15, 20, 30, 60, 120, 300);

	private final TabConfig tab;
	private final WindowLayout layout;
	private int infoY;

	WindowSettingsScreen(Screen parent, TabConfig tab) {
		super(Component.translatable("chatlanes.config.window.title", tab.name), parent);
		this.tab = tab;
		this.layout = tab.windowLayoutOrDefault();
	}

	private static int orDefault(Integer value) {
		return value == null ? DEFAULT : value;
	}

	private static Integer toConfig(int value) {
		return value == DEFAULT ? null : value;
	}

	/** Liste, die den aktuellen (evtl. von Hand eingetragenen) Wert immer enthält. */
	private static List<Integer> withCurrent(List<Integer> values, int current) {
		if (values.contains(current)) {
			return values;
		}
		java.util.List<Integer> list = new java.util.ArrayList<>(values);
		list.add(current);
		list.sort(null);
		list.remove((Integer) DEFAULT);
		list.addFirst(DEFAULT);
		return list;
	}

	@Override
	protected void init() {
		this.beginLayout(360);
		int y = TOP;

		CycleButton<Integer> scale = CycleButton.<Integer>builder(
				v -> v == DEFAULT ? t("window.like_chat") : Component.literal(v + " %"), orDefault(this.layout.textScale))
			.withValues(withCurrent(SCALES, orDefault(this.layout.textScale)))
			.create(this.left, y, this.contentWidth, 20, t("window.text_scale"), (b, v) -> this.layout.textScale = toConfig(v));
		scale.setTooltip(Tooltip.create(t("window.text_scale.tooltip")));
		this.addRenderableWidget(scale);
		y += ROW;

		CycleButton<Integer> opacity = CycleButton.<Integer>builder(
				v -> v == DEFAULT ? t("window.like_chat") : Component.literal(v + " %"), orDefault(this.layout.opacity))
			.withValues(withCurrent(OPACITIES, orDefault(this.layout.opacity)))
			.create(this.left, y, this.contentWidth, 20, t("window.opacity"), (b, v) -> this.layout.opacity = toConfig(v));
		opacity.setTooltip(Tooltip.create(t("window.opacity.tooltip")));
		this.addRenderableWidget(opacity);
		y += ROW;

		CycleButton<Integer> display = CycleButton.<Integer>builder(v -> switch (v) {
				case DEFAULT -> t("window.like_chat");
				case WindowLayout.ALWAYS -> t("window.display.always");
				case WindowLayout.FOCUSED_ONLY -> t("window.display.focused");
				default -> t("window.display.seconds", formatDuration(v));
			}, orDefault(this.layout.displaySeconds))
			.withValues(withCurrent(DISPLAY, orDefault(this.layout.displaySeconds)))
			.create(this.left, y, this.contentWidth, 20, t("window.display"), (b, v) -> this.layout.displaySeconds = toConfig(v));
		display.setTooltip(Tooltip.create(t("window.display.tooltip")));
		this.addRenderableWidget(display);
		y += ROW;

		if (this.tab.type == TabConfig.Type.CHANNEL) {
			CycleButton<Boolean> alsoMain = onOff(this.layout.alsoInMain)
				.create(this.left, y, this.contentWidth, 20, t("filter.also_main"), (b, v) -> this.layout.alsoInMain = v);
			alsoMain.setTooltip(Tooltip.create(t("filter.also_main.tooltip")));
			this.addRenderableWidget(alsoMain);
			y += ROW;
		}

		Button reset = Button.builder(t("window.reset"), b -> {
			WindowLayout defaults = ChatWindows.defaultLayout(0);
			this.layout.x = defaults.x;
			this.layout.y = defaults.y;
			this.layout.width = defaults.width;
			this.layout.height = defaults.height;
		}).bounds(this.left, y, this.contentWidth, 20).build();
		reset.setTooltip(Tooltip.create(t("window.reset.tooltip")));
		this.addRenderableWidget(reset);
		y += ROW + 6;

		this.infoY = y;

		this.addRenderableWidget(Button.builder(t("done"), b -> this.onClose())
			.bounds(this.left, this.height - 26, this.contentWidth, 20).build());
	}

	@Override
	public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// Hinweis zum Verschieben (mehrzeilig, bricht automatisch um)
		int y = this.infoY;
		for (var line : this.font.split(t("window.move_hint"), this.contentWidth)) {
			if (y > this.height - 40) {
				break;
			}
			graphics.text(this.font, line, this.left, y, LABEL_COLOR, false);
			y += 10;
		}
	}
}
