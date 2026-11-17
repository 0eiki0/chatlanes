package de.chatlanes.gui;

import de.chatlanes.config.ChatLanesConfig;
import java.util.List;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Aussehen des Chats: Größe und Deckkraft (Minecraft-Einstellungen, gelten sofort und für alle Profile),
 * Hintergrundfarbe und sanftes Ein-/Ausblenden (pro Profil, mit "Speichern").
 */
class AppearanceScreen extends BaseSettingsScreen {
	/** Name-Schlüssel und RGB der Farb-Vorgaben. */
	private static final List<int[]> PRESETS = List.of(
		new int[] {0x000000}, new int[] {0x202020}, new int[] {0x0B1A33}, new int[] {0x0D2B1A},
		new int[] {0x2A0F3A}, new int[] {0x3A0D0D}, new int[] {0x3A2A0A}, new int[] {0x0A2E33});
	private static final String[] PRESET_KEYS = {"black", "gray", "blue", "green", "purple", "red", "brown", "teal"};

	private final ChatLanesConfig working;
	private int previewY;

	AppearanceScreen(ChatLanesConfigScreen main) {
		super(Component.translatable("chatlanes.config.appearance.title"), main);
		this.working = main.working;
	}

	@Override
	protected void init() {
		this.beginLayout(400);
		Options options = this.minecraft.options;
		int y = TOP;
		int half = this.halfWidth();

		// Größe & Deckkraft – direkt die Minecraft-Einstellungen
		this.label(t("appearance.size_header"), this.left, y + 2);
		y += 12;
		this.addRenderableWidget(options.chatWidth().createButton(options, this.left, y, half));
		this.addRenderableWidget(options.chatScale().createButton(options, this.rightHalfX(), y, half));
		y += ROW;
		this.addRenderableWidget(options.chatHeightFocused().createButton(options, this.left, y, half));
		this.addRenderableWidget(options.chatHeightUnfocused().createButton(options, this.rightHalfX(), y, half));
		y += ROW;
		this.addRenderableWidget(options.chatLineSpacing().createButton(options, this.left, y, half));
		this.addRenderableWidget(options.chatOpacity().createButton(options, this.rightHalfX(), y, half));
		y += ROW;
		this.addRenderableWidget(options.textBackgroundOpacity().createButton(options, this.left, y, half));

		// Hintergrundfarbe: Vorgabe + eigener Hex-Wert
		int presetIndex = 0;
		for (int i = 0; i < PRESETS.size(); i++) {
			if (PRESETS.get(i)[0] == this.working.chatBackgroundColor) {
				presetIndex = i;
			}
		}
		int quarter = (half - 4) / 2;
		EditBox hex = new EditBox(this.font, this.rightHalfX() + quarter + 4, y + 1, half - quarter - 4, 18, t("appearance.color"));
		CycleButton<Integer> preset = CycleButton.<Integer>builder(i -> t("appearance.color." + PRESET_KEYS[i]), presetIndex)
			.withValues(List.of(0, 1, 2, 3, 4, 5, 6, 7))
			.displayOnlyValue()
			.create(this.rightHalfX(), y, quarter, 20, t("appearance.color"), (b, v) -> {
				this.working.chatBackgroundColor = PRESETS.get(v)[0];
				hex.setValue(String.format("#%06X", this.working.chatBackgroundColor));
			});
		preset.setTooltip(Tooltip.create(t("appearance.color.tooltip")));
		this.addRenderableWidget(preset);
		hex.setMaxLength(7);
		hex.setValue(String.format("#%06X", this.working.chatBackgroundColor));
		hex.setTooltip(Tooltip.create(t("appearance.color.hex")));
		hex.setResponder(v -> {
			String clean = v.startsWith("#") ? v.substring(1) : v;
			if (clean.matches("[0-9A-Fa-f]{6}")) {
				this.working.chatBackgroundColor = Integer.parseInt(clean, 16);
				hex.setTextColor(0xFFE0E0E0);
			} else {
				hex.setTextColor(ERROR_COLOR);
			}
		});
		this.addRenderableWidget(hex);
		y += ROW + 8;

		// Sanftes Ein-/Ausblenden
		this.label(t("appearance.fade_header"), this.left, y + 2);
		y += 12;
		CycleButton<Boolean> smooth = onOff(this.working.smoothFade)
			.create(this.left, y, this.contentWidth, 20, t("appearance.smooth"), (b, v) -> this.working.smoothFade = v);
		smooth.setTooltip(Tooltip.create(t("appearance.smooth.tooltip")));
		this.addRenderableWidget(smooth);
		y += ROW;
		java.util.TreeSet<Double> shows = new java.util.TreeSet<>(List.of(3.0, 5.0, 8.0, 10.0, 15.0, 20.0, 30.0, 60.0));
		shows.add(this.working.fadeDisplaySeconds);
		this.addRenderableWidget(CycleButton.<Double>builder(BaseSettingsScreen::secondsLabel, this.working.fadeDisplaySeconds)
			.withValues(List.copyOf(shows))
			.create(this.left, y, half, 20, t("appearance.display"), (b, v) -> this.working.fadeDisplaySeconds = v));
		java.util.TreeSet<Double> fades = new java.util.TreeSet<>(List.of(0.5, 1.0, 1.5, 2.0, 3.0, 5.0));
		fades.add(this.working.fadeOutSeconds);
		this.addRenderableWidget(CycleButton.<Double>builder(BaseSettingsScreen::secondsLabel, this.working.fadeOutSeconds)
			.withValues(List.copyOf(fades))
			.create(this.rightHalfX(), y, half, 20, t("appearance.fadeout"), (b, v) -> this.working.fadeOutSeconds = v));
		y += ROW + 4;
		this.previewY = y;

		this.addRenderableWidget(Button.builder(t("back"), b -> this.onClose())
			.bounds(this.left, this.height - 26, this.contentWidth, 20).build());
	}

	@Override
	public void onClose() {
		this.minecraft.options.save();
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// Vorschau des Hintergrunds
		if (this.previewY + 14 < this.height - 30) {
			float opacity = this.minecraft.options.textBackgroundOpacity().get().floatValue();
			int bg = ((int) (Math.max(opacity, 0.15f) * 255) << 24) | (this.working.chatBackgroundColor & 0xFFFFFF);
			graphics.fill(this.left, this.previewY, this.left + this.contentWidth, this.previewY + 14, bg);
			graphics.text(this.font, t("appearance.preview"), this.left + 4, this.previewY + 3, 0xFFFFFFFF);
		}
	}
}
