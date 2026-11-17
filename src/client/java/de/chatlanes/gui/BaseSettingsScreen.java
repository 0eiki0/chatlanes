package de.chatlanes.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Gemeinsame Hilfen für alle Einstellungsseiten: Formular-Layout mit Beschriftungen links und Feldern rechts. */
abstract class BaseSettingsScreen extends Screen {
	protected static final int LABEL_COLOR = 0xFFA0A0A0;
	protected static final int TITLE_COLOR = 0xFFFFFFFF;
	protected static final int OK_COLOR = 0xFF55FF55;
	protected static final int ERROR_COLOR = 0xFFFF5555;
	protected static final int ROW = 22;
	protected static final int TOP = 30;

	protected final @Nullable Screen parent;
	private final List<Label> labels = new ArrayList<>();
	protected int left;
	protected int contentWidth;
	protected int labelWidth;

	private record Label(Component text, int x, int y, int color) {
	}

	protected BaseSettingsScreen(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	static net.minecraft.network.chat.MutableComponent t(String key, Object... args) {
		return Component.translatable("chatlanes.config." + key, args);
	}

	/** An/Aus-Schalter mit Texten der Mod (folgen der Sprachwahl der Mod, nicht nur der von Minecraft). */
	static net.minecraft.client.gui.components.CycleButton.Builder<Boolean> onOff(boolean value) {
		return net.minecraft.client.gui.components.CycleButton.booleanBuilder(t("on"), t("off"), value);
	}

	/**
	 * Zeile "Ton | Lautstärke | ▶": Ton auswählen, Lautstärke einstellen, Probe hören.
	 */
	protected void soundRow(int y, de.chatlanes.config.TabSound sound, int volume,
			java.util.function.Consumer<de.chatlanes.config.TabSound> onSound, java.util.function.IntConsumer onVolume) {
		int[] currentVolume = {volume};
		de.chatlanes.config.TabSound[] currentSound = {sound};
		int playWidth = 20;
		int soundWidth = (this.contentWidth - playWidth - 8) / 2;
		var soundButton = net.minecraft.client.gui.components.CycleButton.<de.chatlanes.config.TabSound>builder(
				v -> Component.translatable(v.translationKey()), sound)
			.withValues(de.chatlanes.config.TabSound.values())
			.create(this.left, y, soundWidth, 20, t("sound"), (b, v) -> {
				currentSound[0] = v;
				onSound.accept(v);
				de.chatlanes.ChatSounds.play(v, currentVolume[0]);
			});
		soundButton.setTooltip(Tooltip.create(t("sound.tooltip")));
		this.addRenderableWidget(soundButton);
		this.addRenderableWidget(new VolumeSlider(this.left + soundWidth + 4, y, this.contentWidth - soundWidth - playWidth - 8, 20, volume, v -> {
			currentVolume[0] = v;
			onVolume.accept(v);
		}));
		var play = net.minecraft.client.gui.components.Button.builder(Component.literal("▶"),
				b -> de.chatlanes.ChatSounds.play(currentSound[0], currentVolume[0]))
			.bounds(this.left + this.contentWidth - playWidth, y, playWidth, 20).build();
		play.setTooltip(Tooltip.create(t("sound.play")));
		this.addRenderableWidget(play);
	}

	/** Auswahl "Uhrzeit: Standard / An / Aus" (Standard = wie unter Allgemein). */
	static net.minecraft.client.gui.components.CycleButton.Builder<de.chatlanes.config.ChatLanesConfig.TimestampMode> timestampMode(
			de.chatlanes.config.ChatLanesConfig.TimestampMode value) {
		return net.minecraft.client.gui.components.CycleButton.<de.chatlanes.config.ChatLanesConfig.TimestampMode>builder(
				m -> t("timestamp_mode." + m.name().toLowerCase(java.util.Locale.ROOT)), value)
			.withValues(de.chatlanes.config.ChatLanesConfig.TimestampMode.values());
	}

	/** 45 → "45 s", 120 → "2 min", 90 → "1:30 min" */
	static Component formatDuration(int seconds) {
		if (seconds < 60) {
			return Component.literal(seconds + " s");
		}
		int minutes = seconds / 60;
		int rest = seconds % 60;
		return Component.literal(rest == 0 ? minutes + " min" : minutes + ":" + String.format("%02d", rest) + " min");
	}

	/** 1.5 → "1,5 s" (Deutsch) bzw. "1.5 s" (Englisch) */
	static Component secondsLabel(double value) {
		java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.ROOT);
		symbols.setDecimalSeparator(net.minecraft.client.resources.language.I18n.get("chatlanes.config.decimal_separator").charAt(0));
		return Component.literal(new java.text.DecimalFormat("0.##", symbols).format(value) + " s");
	}

	/** Muss am Anfang jedes init() aufgerufen werden. */
	protected void beginLayout(int maxWidth) {
		this.labels.clear();
		this.contentWidth = Math.min(this.width - 24, maxWidth);
		this.left = (this.width - this.contentWidth) / 2;
		this.labelWidth = Math.min(140, this.contentWidth / 3);
	}

	protected void label(Component text, int x, int y) {
		this.labels.add(new Label(text, x, y, LABEL_COLOR));
	}

	/** Zeile mit Beschriftung links und Textfeld rechts. */
	protected EditBox textRow(int y, @Nullable Component label, String value, @Nullable Component hint, @Nullable Component tooltip, Consumer<String> onChange) {
		if (label != null) {
			this.label(label, this.left, y + 6);
		}
		EditBox box = new EditBox(this.font, this.left + this.labelWidth, y + 1, this.contentWidth - this.labelWidth, 18,
			label != null ? label : Component.empty());
		box.setMaxLength(1024);
		box.setValue(value);
		box.moveCursorToStart(false);
		if (hint != null) {
			box.setHint(hint);
		}
		if (tooltip != null) {
			box.setTooltip(Tooltip.create(tooltip));
		}
		box.setResponder(onChange);
		return this.addRenderableWidget(box);
	}

	protected int halfWidth() {
		return (this.contentWidth - 4) / 2;
	}

	protected int rightHalfX() {
		return this.left + this.halfWidth() + 4;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// Titel jedes Mal neu übersetzen, damit ein Sprachwechsel im Menü sofort sichtbar ist
		// (Minecraft speichert übersetzte Texte sonst zwischen).
		Component title = this.title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
			? Component.translatable(tc.getKey(), tc.getArgs())
			: this.title;
		graphics.centeredText(this.font, title, this.width / 2, 12, TITLE_COLOR);
		for (Label label : this.labels) {
			graphics.text(this.font, label.text(), label.x(), label.y(), label.color());
		}
	}
}
