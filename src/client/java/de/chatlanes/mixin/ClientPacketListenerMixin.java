package de.chatlanes.mixin;

import de.chatlanes.ChatLanes;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Befehle, die Mods/Makros direkt ausführen (auch reine Client-Befehle), als Aktion erkennen. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "sendCommand", at = @At("HEAD"))
	private void chatlanes$command(String command, CallbackInfo ci) {
		ChatLanes.get().onCommandIntent("/" + command);
	}

	@Inject(method = "sendUnattendedCommand", at = @At("HEAD"))
	private void chatlanes$unattendedCommand(String command, Screen screenAfterCommand, CallbackInfo ci) {
		ChatLanes.get().onCommandIntent("/" + command);
	}
}
