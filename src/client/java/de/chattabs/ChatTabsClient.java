package de.chattabs;

import de.chattabs.config.ChatTabsConfig;
import net.fabricmc.api.ClientModInitializer;

public class ChatTabsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ChatTabs.get().apply(ChatTabsConfig.load());
	}
}
