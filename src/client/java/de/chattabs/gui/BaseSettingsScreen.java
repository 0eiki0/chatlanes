package de.chattabs.gui;

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
		return Component.translatable("chattabs.config." + key, args);
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
	protected EditBox textRow(int y, Component label, String value, @Nullable Component hint, @Nullable Component tooltip, Consumer<String> onChange) {
		this.label(label, this.left, y + 6);
		EditBox box = new EditBox(this.font, this.left + this.labelWidth, y + 1, this.contentWidth - this.labelWidth, 18, label);
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
		graphics.centeredText(this.font, this.title, this.width / 2, 12, TITLE_COLOR);
		for (Label label : this.labels) {
			graphics.text(this.font, label.text(), label.x(), label.y(), label.color());
		}
	}
}
