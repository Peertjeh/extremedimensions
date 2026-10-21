package com.terraformersmc.modmenu.api;

/**
 * Compile-time stand-in for Mod Menu's API (only what this mod uses, with the same signatures). It is left out of the
 * mod's jar: with Mod Menu installed its own class is used, without it this entrypoint is simply never loaded.
 */
public interface ModMenuApi {
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> null;
	}
}
