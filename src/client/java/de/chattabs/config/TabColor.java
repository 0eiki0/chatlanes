package de.chattabs.config;

/** Auswählbare Textfarben für Tabs (Minecraft-Chatfarben). */
public enum TabColor {
	DEFAULT(0xFFAAAAAA),
	WHITE(0xFFFFFFFF),
	GOLD(0xFFFFAA00),
	YELLOW(0xFFFFFF55),
	GREEN(0xFF55FF55),
	DARK_GREEN(0xFF00AA00),
	AQUA(0xFF55FFFF),
	BLUE(0xFF5555FF),
	LIGHT_PURPLE(0xFFFF55FF),
	DARK_PURPLE(0xFFAA00AA),
	RED(0xFFFF5555),
	DARK_RED(0xFFAA0000);

	private final int argb;

	TabColor(int argb) {
		this.argb = argb;
	}

	public int argb() {
		return this.argb;
	}

	public String translationKey() {
		return "chattabs.color." + this.name().toLowerCase(java.util.Locale.ROOT);
	}
}
