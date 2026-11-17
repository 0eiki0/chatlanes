package de.chatlanes.config;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Auswählbare Benachrichtigungstöne (Minecraft-Sounds). */
public enum TabSound {
	NONE(null),
	PLING(() -> SoundEvents.NOTE_BLOCK_PLING.value()),
	BELL(() -> SoundEvents.NOTE_BLOCK_BELL.value()),
	CHIME(() -> SoundEvents.NOTE_BLOCK_CHIME.value()),
	XYLOPHONE(() -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value()),
	IRON_XYLOPHONE(() -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value()),
	BIT(() -> SoundEvents.NOTE_BLOCK_BIT.value()),
	HARP(() -> SoundEvents.NOTE_BLOCK_HARP.value()),
	FLUTE(() -> SoundEvents.NOTE_BLOCK_FLUTE.value()),
	GUITAR(() -> SoundEvents.NOTE_BLOCK_GUITAR.value()),
	BANJO(() -> SoundEvents.NOTE_BLOCK_BANJO.value()),
	COW_BELL(() -> SoundEvents.NOTE_BLOCK_COW_BELL.value()),
	BASS(() -> SoundEvents.NOTE_BLOCK_BASS.value()),
	DIDGERIDOO(() -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value()),
	EXPERIENCE(() -> SoundEvents.EXPERIENCE_ORB_PICKUP),
	AMETHYST(() -> SoundEvents.AMETHYST_BLOCK_CHIME),
	ITEM_PICKUP(() -> SoundEvents.ITEM_PICKUP),
	BELL_BLOCK(() -> SoundEvents.BELL_BLOCK),
	VILLAGER(() -> SoundEvents.VILLAGER_YES),
	ARROW_HIT(() -> SoundEvents.ARROW_HIT_PLAYER),
	ANVIL(() -> SoundEvents.ANVIL_LAND);

	private final @Nullable Supplier<SoundEvent> sound;

	TabSound(@Nullable Supplier<SoundEvent> sound) {
		this.sound = sound;
	}

	public @Nullable SoundEvent event() {
		return this.sound == null ? null : this.sound.get();
	}

	public String translationKey() {
		return "chatlanes.sound." + this.name().toLowerCase(Locale.ROOT);
	}
}
