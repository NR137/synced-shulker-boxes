package de.nr137.syncedshulkerboxes.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.nr137.syncedshulkerboxes.client.SettingsScreen;

/** Mod Menu's settings button (only loaded when Mod Menu is installed). */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
