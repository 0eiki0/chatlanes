package de.chattabs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chattabs.ChatTabs;
import de.chattabs.gui.TabBar;
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
	private void chattabs$clickTab(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (TabBar.mouseClicked((ChatScreen) (Object) this, event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
		}
	}

	/** Strg+F im Chat öffnet die Suche. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void chattabs$searchShortcut(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (de.chattabs.ChatTabs.get().isEnabled() && event.shortcutKey() == 'f' && event.hasControlDownWithQuirk() && !event.hasShiftDown() && !event.hasAltDown()) {
			net.minecraft.client.Minecraft.getInstance().gui.setScreen(new de.chattabs.gui.ChatSearchScreen((ChatScreen) (Object) this));
			cir.setReturnValue(true);
		}
	}

	/** Im Msg-Tab wird automatisch "/msg <Spieler> ..." gesendet, in Kanal-Tabs ggf. ein Präfix vorangestellt. */
	@WrapOperation(
		method = "handleChatInput",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V")
	)
	private void chattabs$sendToTab(ClientPacketListener connection, String message, Operation<Void> original) {
		// Erst in den Kanal des aktiven Tabs wechseln, falls der Server (noch) woanders ist
		ChatTabs.get().ensureServerChannel();
		String transformed = ChatTabs.get().transformOutgoing(message);
		if (transformed == null) {
			original.call(connection, message);
		} else if (transformed.startsWith("/")) {
			connection.sendCommand(transformed.substring(1));
		} else {
			original.call(connection, StringUtil.trimChatMessage(transformed));
		}
	}
}
