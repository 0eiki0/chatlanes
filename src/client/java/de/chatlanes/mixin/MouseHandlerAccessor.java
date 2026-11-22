package de.chatlanes.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	/** Gerade gedrückte Maustaste (auch bei offenem Bildschirm, anders als isLeftPressed) – null = keine. */
	@Accessor("activeButton")
	@Nullable MouseButtonInfo chatlanes$getActiveButton();
}
