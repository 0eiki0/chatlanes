package de.chatlanes;

import com.mojang.blaze3d.platform.InputConstants;
import de.chatlanes.config.ChatLanesConfig;
import de.chatlanes.gui.ChatLanesConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class ChatLanesClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chatlanes", "main"));

	/** Wird im nächsten Tick geöffnet (nötig, weil der Chat nach einem Befehl erst noch geschlossen wird). */
	private static boolean openSettingsNextTick;

	@Override
	public void onInitializeClient() {
		migrateFromChatTabs();
		ChatLanes.get().apply(ChatLanesConfig.load());

		// Taste zum Öffnen des Einstellungsmenüs (Standard: K, änderbar unter Optionen → Steuerung)
		KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.chatlanes.settings", InputConstants.KEY_K, CATEGORY)
		);

		// Befehl /chatlanes öffnet ebenfalls das Menü
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			dispatcher.register(ClientCommands.literal("chatlanes").executes(ctx -> {
				openSettingsNextTick = true;
				return 1;
			}));
			// alter Befehl aus "Chat Tabs"-Zeiten funktioniert weiter
			dispatcher.register(ClientCommands.literal("chattabs").executes(ctx -> {
				openSettingsNextTick = true;
				return 1;
			}));
		});

		// Gespeicherten Verlauf beim Betreten eines Servers wiederherstellen
		ClientPlayConnectionEvents.JOIN.register((connection, sender, minecraft) -> {
			// Erst das Profil des Servers laden (neue Server bekommen eins), dann alles Weitere
			ServerProfiles.onJoin(minecraft);
			PlayerHeads.clearCache();
			ChatLanes.get().resetServerChannel();
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
			ChatLanes.get().tick(minecraft);
			if (openSettingsNextTick && minecraft.gui.screen() == null) {
				openSettingsNextTick = false;
				openSettings(minecraft);
			}
		});
	}

	/**
	 * Die Mod hieß früher "Chat Tabs" (Mod-ID chattabs). Beim ersten Start von ChatLanes werden
	 * Einstellungen, Server-Profile und gespeicherter Verlauf übernommen (die alten Dateien bleiben liegen).
	 */
	private static void migrateFromChatTabs() {
		java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
		try {
			java.nio.file.Path oldFile = configDir.resolve("chattabs.json");
			java.nio.file.Path newFile = configDir.resolve("chatlanes.json");
			if (java.nio.file.Files.exists(oldFile) && !java.nio.file.Files.exists(newFile)) {
				java.nio.file.Files.copy(oldFile, newFile);
			}
			java.nio.file.Path oldDir = configDir.resolve("chattabs");
			java.nio.file.Path newDir = configDir.resolve("chatlanes");
			if (java.nio.file.Files.isDirectory(oldDir) && !java.nio.file.Files.exists(newDir)) {
				try (var paths = java.nio.file.Files.walk(oldDir)) {
					for (java.nio.file.Path source : paths.toList()) {
						java.nio.file.Path target = newDir.resolve(oldDir.relativize(source).toString());
						if (java.nio.file.Files.isDirectory(source)) {
							java.nio.file.Files.createDirectories(target);
						} else {
							java.nio.file.Files.copy(source, target);
						}
					}
				}
			}
		} catch (java.io.IOException e) {
			org.slf4j.LoggerFactory.getLogger("chatlanes").warn("Konnte alte Chat-Tabs-Einstellungen nicht übernehmen", e);
		}
	}

	public static void openSettings(Minecraft minecraft) {
		minecraft.gui.setScreen(new ChatLanesConfigScreen(minecraft.gui.screen()));
	}
}
