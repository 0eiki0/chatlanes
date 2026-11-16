package de.chattabs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chattabs.ChatTabs;
import de.chattabs.gui.TabBar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	/** Nur Nachrichten des aktiven Tabs in die sichtbaren Zeilen übernehmen. */
	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
	private void chattabs$filterDisplayed(GuiMessage message, CallbackInfo ci) {
		if (!ChatTabs.get().isVisible(message)) {
			ci.cancel();
		}
	}

	/** Neue Nachricht: ungelesene Zähler der anderen Tabs erhöhen. */
	@Inject(method = "addMessageToQueue", at = @At("HEAD"))
	private void chattabs$onNewMessage(GuiMessage message, CallbackInfo ci) {
		ChatTabs.get().onNewMessage(message);
	}

	@Shadow @Final private List<GuiMessage> allMessages;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;

	/**
	 * Größerer Verlauf (Vanilla: 100). Statt die Konstante 100 zu ändern (das kollidiert mit anderen
	 * Mods wie Cubeside, die dieselbe Konstante ändern), wird die Größenabfrage in der Kürzungsschleife
	 * angepasst: Solange die Liste unter unserem Limit liegt, meldet sie 0 Einträge, sonst die echte Größe.
	 * Damit gilt am Ende das größere der beiden Limits – egal was andere Mods einstellen.
	 */
	@WrapOperation(
		method = {"addMessageToQueue", "addMessageToDisplayQueue"},
		at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
		require = 0
	)
	private int chattabs$historySize(List<?> list, Operation<Integer> original) {
		int size = original.call(list);
		if (list != this.allMessages && list != this.trimmedMessages) {
			return size;
		}
		return size > ChatTabs.get().config().maxHistory ? size : 0;
	}

	@Inject(method = "createDeletedMarker", at = @At("RETURN"))
	private static void chattabs$keepTabOfDeleted(GuiMessage original, CallbackInfoReturnable<GuiMessage> cir) {
		ChatTabs.get().copyClassification(original, cir.getReturnValue());
	}

	@Inject(method = "clearMessages", at = @At("HEAD"))
	private void chattabs$onClear(boolean history, CallbackInfo ci) {
		ChatTabs.get().onClear(history);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At("TAIL")
	)
	private void chattabs$renderTabs(
		GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursor, CallbackInfo ci
	) {
		TabBar.render(graphics, font, displayMode.foreground, mouseX, mouseY);
	}
}
