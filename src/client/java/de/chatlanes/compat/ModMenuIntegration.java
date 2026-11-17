package de.chatlanes.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.chatlanes.gui.ChatLanesConfigScreen;

/** "Konfigurieren"-Knopf in Mod Menu. Wird nur geladen, wenn Mod Menu installiert ist. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ChatLanesConfigScreen::new;
	}
}
