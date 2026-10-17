package com.extremedimensions.client.compat;

import com.extremedimensions.client.screen.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** With Mod Menu installed, its "Configure" button opens this mod's settings. */
public class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
