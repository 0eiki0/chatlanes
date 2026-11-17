package de.chattabs.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jspecify.annotations.Nullable;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	/** Die aktuell sichtbaren Zeilen – damit die Tab-Leiste direkt über den Nachrichten sitzt. */
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> chattabs$getTrimmedMessages();

	@Accessor("chatScrollbarPos")
	int chattabs$getScrollPos();

	@Accessor("chatScrollbarPos")
	void chattabs$setScrollPos(int pos);

	/** Alle gespeicherten Nachrichten (neueste zuerst) – für die Suche. */
	@Accessor("allMessages")
	List<GuiMessage> chattabs$getAllMessages();

	@Invoker("addMessage")
	void chattabs$addMessage(Component contents, @Nullable MessageSignature signature, GuiMessageSource source, @Nullable GuiMessageTag tag);
}
