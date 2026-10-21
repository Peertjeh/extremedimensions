package com.extremedimensions.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Where the player is between starting the game and standing in a world: on the menus, joining a world, or in it.
 * The title art and the menu music follow this. The screen on show is tracked here from the screen events (the
 * game keeps it out of reach).
 */
public final class Menus {
	private Menus() {}

	public enum Phase {
		/** No world: the title screen and every menu behind it (and the saving screen after leaving a world). */
		MENU,
		/** From the first loading screen until the player stands in the world. */
		JOINING,
		/** In a world (loading screens here are portal trips, not joins). */
		WORLD
	}

	private static Phase phase = Phase.MENU;
	private static Screen current = null;
	/** A menu has shown since the last world: a loading screen now is a join, not the save on the way out. */
	private static boolean sawMenu = true;

	public static Phase phase() {
		return phase;
	}

	public static Screen current() {
		return current;
	}

	public static void shown(Screen screen) {
		current = screen;
	}

	public static void gone(Screen screen) {
		if (current == screen) {
			current = null;
		}
	}

	/** The screens that show while a world is set up or a server is reached. */
	public static boolean loading(Screen screen) {
		return screen instanceof LevelLoadingScreen || screen instanceof GenericMessageScreen || screen instanceof ConnectScreen
				|| screen instanceof ProgressScreen;
	}

	/** The art goes behind a loading screen while no world is being played (not on a portal trip). */
	public static boolean artBehindLoading() {
		return phase != Phase.WORLD;
	}

	public static void tick(Minecraft mc) {
		boolean loadingScreen = loading(current);
		boolean inWorld = mc.level != null && mc.player != null;
		switch (phase) {
			case MENU -> {
				if (current != null && !loadingScreen) {
					sawMenu = true;
				}
				if ((loadingScreen && sawMenu) || mc.level != null) {
					phase = Phase.JOINING;
				}
			}
			case JOINING -> {
				if (inWorld && !loadingScreen) {
					phase = Phase.WORLD;
				} else if (mc.level == null && current != null && !loadingScreen) {
					// the join was given up or the server turned us away: back on the menus
					phase = Phase.MENU;
				}
			}
			case WORLD -> {
				if (mc.level == null) {
					phase = Phase.MENU;
					sawMenu = false;
				}
			}
		}
	}
}
