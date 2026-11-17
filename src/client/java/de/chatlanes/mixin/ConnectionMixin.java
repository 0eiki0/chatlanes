package de.chatlanes.mixin;

import de.chatlanes.ChatLanes;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bemerkt JEDEN gesendeten Befehl und jede Chatnachricht – egal ob getippt, per Makro-Mod
 * oder von einer anderen Mod geschickt. Wichtig für "Befehl-Ausgabe" und Kanalwechsel.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
	private void chatlanes$onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (packet instanceof ServerboundChatCommandPacket command) {
			ChatLanes.get().onCommandSent("/" + command.command());
		} else if (packet instanceof ServerboundChatCommandSignedPacket command) {
			ChatLanes.get().onCommandSent("/" + command.command());
		} else if (packet instanceof ServerboundChatPacket chat) {
			ChatLanes.get().onCommandSent(chat.message());
		} else if (packet instanceof net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
				|| (packet instanceof net.minecraft.network.protocol.game.ServerboundPlayerActionPacket action
					&& action.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)) {
			// Block-Klick (rechts oder links) – z. B. LogBlock-Abfrage mit Holzspitzhacke oder Bedrock
			var player = net.minecraft.client.Minecraft.getInstance().player;
			if (player != null) {
				String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
				ChatLanes.get().onBlockClick(item);
			}
		}
	}
}
