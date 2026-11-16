package de.chattabs.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	/** Die aktuell sichtbaren Zeilen – damit die Tab-Leiste direkt über den Nachrichten sitzt. */
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> chattabs$getTrimmedMessages();

	@Accessor("chatScrollbarPos")
	int chattabs$getScrollPos();
}
