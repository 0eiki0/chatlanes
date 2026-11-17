package de.chatlanes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chatlanes.ChatLanes;
import de.chatlanes.gui.TabBar;
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
	private void chatlanes$filterDisplayed(GuiMessage message, CallbackInfo ci) {
		ChatLanes.get().recordTime(message);
		if (!ChatLanes.get().isVisible(message)) {
			ci.cancel();
		}
	}

	/** Uhrzeit vor die Nachricht setzen – nur für die Anzeige, der gespeicherte Inhalt bleibt unverändert. */
	@WrapOperation(
		method = "addMessageToDisplayQueue",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;")
	)
	private List<net.minecraft.util.FormattedCharSequence> chatlanes$addTimestamp(GuiMessage message, Font font, int width,
			Operation<List<net.minecraft.util.FormattedCharSequence>> original) {
		net.minecraft.network.chat.Component prefix = ChatLanes.get().timestampFor(message);
		if (prefix == null) {
			return original.call(message, font, width);
		}
		GuiMessage withTime = new GuiMessage(message.addedTime(), prefix.copy().append(message.content()), message.signature(), message.source(), message.tag());
		return original.call(withTime, font, width);
	}

	/** Neue Nachricht: ungelesene Zähler der anderen Tabs erhöhen. */
	@Inject(method = "addMessageToQueue", at = @At("HEAD"))
	private void chatlanes$onNewMessage(GuiMessage message, CallbackInfo ci) {
		ChatLanes.get().onNewMessage(message);
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
	private int chatlanes$historySize(List<?> list, Operation<Integer> original) {
		int size = original.call(list);
		if (list != this.allMessages && list != this.trimmedMessages) {
			return size;
		}
		return size > ChatLanes.get().config().maxHistory ? size : 0;
	}

	/** Server-Bestätigung eines automatischen Kanalwechsels ausblenden (falls eingestellt). */
	@Inject(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void chatlanes$hideSwitchConfirmation(
		net.minecraft.network.chat.Component contents, net.minecraft.network.chat.MessageSignature signature,
		net.minecraft.client.multiplayer.chat.GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, CallbackInfo ci
	) {
		if (ChatLanes.get().shouldHide(contents)) {
			ci.cancel();
		}
	}

	@Inject(method = "createDeletedMarker", at = @At("RETURN"))
	private static void chatlanes$keepTabOfDeleted(GuiMessage original, CallbackInfoReturnable<GuiMessage> cir) {
		ChatLanes.get().copyClassification(original, cir.getReturnValue());
	}

	@Inject(method = "clearMessages", at = @At("HEAD"))
	private void chatlanes$onClear(boolean history, CallbackInfo ci) {
		ChatLanes.get().onClear(history);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At("TAIL")
	)
	private void chatlanes$renderTabs(
		GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursor, CallbackInfo ci
	) {
		TabBar.render(graphics, font, displayMode.foreground, mouseX, mouseY);
	}
}
