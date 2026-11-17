package de.chatlanes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chatlanes.ChatLanes;
import de.chatlanes.gui.TabBar;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void chatlanes$clickTab(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (TabBar.mouseClicked((ChatScreen) (Object) this, event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
		}
	}

	/** Im Chatfeld getippt (auch Client-Befehle anderer Mods, die nie an den Server gehen). */
	@Inject(method = "handleChatInput", at = @At("HEAD"))
	private void chatlanes$userInput(String message, boolean addToRecent, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		ChatLanes.get().onUserInput(message.trim());
	}

	/** Strg+F im Chat öffnet die Suche. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void chatlanes$searchShortcut(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (de.chatlanes.ChatLanes.get().isEnabled() && event.shortcutKey() == 'f' && event.hasControlDownWithQuirk() && !event.hasShiftDown() && !event.hasAltDown()) {
			net.minecraft.client.Minecraft.getInstance().gui.setScreen(new de.chatlanes.gui.ChatSearchScreen((ChatScreen) (Object) this));
			cir.setReturnValue(true);
		}
	}

	/** Im Msg-Tab wird automatisch "/msg <Spieler> ..." gesendet, in Kanal-Tabs ggf. ein Präfix vorangestellt. */
	@WrapOperation(
		method = "handleChatInput",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V")
	)
	private void chatlanes$sendToTab(ClientPacketListener connection, String message, Operation<Void> original) {
		// Erst in den Kanal des aktiven Tabs wechseln, falls der Server (noch) woanders ist
		ChatLanes.get().ensureServerChannel();
		String transformed = ChatLanes.get().transformOutgoing(message);
		if (transformed == null) {
			original.call(connection, message);
		} else if (transformed.startsWith("/")) {
			connection.sendCommand(transformed.substring(1));
		} else {
			original.call(connection, StringUtil.trimChatMessage(transformed));
		}
	}
}
