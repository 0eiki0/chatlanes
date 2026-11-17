package de.chattabs;

import com.mojang.blaze3d.platform.InputConstants;
import de.chattabs.config.ChatTabsConfig;
import de.chattabs.gui.ChatTabsConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class ChatTabsClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chattabs", "main"));

	/** Wird im nächsten Tick geöffnet (nötig, weil der Chat nach einem Befehl erst noch geschlossen wird). */
	private static boolean openSettingsNextTick;

	@Override
	public void onInitializeClient() {
		ChatTabs.get().apply(ChatTabsConfig.load());

		// Taste zum Öffnen des Einstellungsmenüs (Standard: K, änderbar unter Optionen → Steuerung)
		KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.chattabs.settings", InputConstants.KEY_K, CATEGORY)
		);

		// Befehl /chattabs öffnet ebenfalls das Menü
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("chattabs").executes(ctx -> {
				openSettingsNextTick = true;
				return 1;
			})
		));

		// Gespeicherten Verlauf beim Betreten eines Servers wiederherstellen
		ClientPlayConnectionEvents.JOIN.register((connection, sender, minecraft) -> {
			// Erst das Profil des Servers laden (neue Server bekommen eins), dann alles Weitere
			ServerProfiles.onJoin(minecraft);
			ChatTabs.get().resetServerChannel();
			ChatHistory.get().onJoin(connection, minecraft);
			UpdateChecker.onJoin(minecraft);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((connection, minecraft) -> {
			ChatHistory.get().onDisconnect();
			minecraft.execute(ServerProfiles::onDisconnect);
		});

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (openSettings.consumeClick()) {
				openSettingsNextTick = true;
			}
			ChatTabs.get().tick(minecraft);
			if (openSettingsNextTick && minecraft.gui.screen() == null) {
				openSettingsNextTick = false;
				openSettings(minecraft);
			}
		});
	}

	public static void openSettings(Minecraft minecraft) {
		minecraft.gui.setScreen(new ChatTabsConfigScreen(minecraft.gui.screen()));
	}
}
