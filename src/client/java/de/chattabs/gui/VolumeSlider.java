package de.chattabs.gui;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Schieberegler für die Lautstärke in Prozent (0–100). */
class VolumeSlider extends AbstractSliderButton {
	private final IntConsumer onChange;

	VolumeSlider(int x, int y, int width, int height, int volume, IntConsumer onChange) {
		super(x, y, width, height, Component.empty(), volume / 100.0);
		this.onChange = onChange;
		this.updateMessage();
	}

	private int percent() {
		return (int) Math.round(this.value * 100.0);
	}

	@Override
	protected void updateMessage() {
		this.setMessage(BaseSettingsScreen.t("volume", this.percent()));
	}

	@Override
	protected void applyValue() {
		this.onChange.accept(this.percent());
	}
}
