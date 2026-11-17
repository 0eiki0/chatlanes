package de.chatlanes;

import de.chatlanes.config.TabSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Spielt Benachrichtigungstöne ab. */
public final class ChatSounds {
	private ChatSounds() {
	}

	/** @param volume 0–100 % */
	public static void play(TabSound sound, int volume) {
		SoundEvent event = sound == null ? null : sound.event();
		if (event == null || volume <= 0) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, Math.min(100, volume) / 100.0F));
	}
}
