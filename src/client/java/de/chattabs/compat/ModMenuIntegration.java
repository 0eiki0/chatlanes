package de.chattabs.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.chattabs.gui.ChatTabsConfigScreen;

/** "Konfigurieren"-Knopf in Mod Menu. Wird nur geladen, wenn Mod Menu installiert ist. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ChatTabsConfigScreen::new;
	}
}
