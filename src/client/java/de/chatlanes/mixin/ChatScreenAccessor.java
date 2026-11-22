package de.chatlanes.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
	/** Klick auf Text mit Link/Befehl – wie im normalen Chat (auch für die eigenen Chatfenster). */
	@Invoker("handleComponentClicked")
	boolean chatlanes$handleComponentClicked(Style clicked, boolean allowInsertions);
}
