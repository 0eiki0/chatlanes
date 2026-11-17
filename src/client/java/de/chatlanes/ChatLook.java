package de.chatlanes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;

/** Aussehen des Chats: Hintergrundfarbe und sanftes Ein-/Ausblenden. */
public final class ChatLook {
	private static final double FADE_IN_SECONDS = 0.25;

	private ChatLook() {
	}

	/** Schwarzen Chat-Hintergrund (nur Alpha zählt) in die eingestellte Farbe umfärben. */
	public static int tint(int argb) {
		ChatLanes lanes = ChatLanes.get();
		if (!lanes.isEnabled()) {
			return argb;
		}
		return (argb & 0xFF000000) | (lanes.config().chatBackgroundColor & 0xFFFFFF);
	}

	/** Sichtbarkeit einer Zeile bei geschlossenem Chat – weich, statt hart nach 10 s zu verschwinden. */
	public static float alpha(GuiMessage.Line line, float vanilla) {
		ChatLanes lanes = ChatLanes.get();
		Minecraft minecraft = Minecraft.getInstance();
		if (!lanes.isEnabled() || !lanes.config().smoothFade || minecraft.gui.screen() instanceof ChatScreen) {
			return vanilla;
		}
		double ticks = minecraft.gui.hud.getGuiTicks() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		double age = (ticks - line.addedTime()) / 20.0;
		if (age < 0) {
			age = 0;
		}
		double in = smooth(age / FADE_IN_SECONDS);
		double display = lanes.config().fadeDisplaySeconds;
		double out = age <= display ? 1.0 : smooth(1.0 - (age - display) / lanes.config().fadeOutSeconds);
		return (float) (in * out);
	}

	private static double smooth(double t) {
		t = Mth.clamp(t, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
