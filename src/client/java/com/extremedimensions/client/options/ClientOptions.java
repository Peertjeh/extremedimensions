package com.extremedimensions.client.options;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.data.DataStore;

import net.fabricmc.loader.api.FabricLoader;

/**
 * This player's own display options, kept on their computer in {@code config/extremedimensions-client.json}: they
 * travel to every world and server. The world's rules are separate (see the settings screen's second tab).
 */
public final class ClientOptions {
	private ClientOptions() {}

	/** Where the anchor and tether meters sit. */
	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		public boolean bottom() {
			return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
		}
	}

	/** Where the expedition timer sits: at the top of the screen or just above the hotbar. */
	public enum TimerPlace {
		TOP, ABOVE_HOTBAR
	}

	/** What is stored. Every field has a safe default so a damaged or older file still loads. */
	private static final class Values {
		boolean reduceFlashing = false;
		boolean colourBlind = false;
		Corner meters = Corner.TOP_LEFT;
		TimerPlace timer = TimerPlace.TOP;
		int hudScale = 100;
		boolean guidance = true;
	}

	/** HUD sizes to cycle through, in percent. */
	public static final int[] HUD_SCALES = {75, 100, 125, 150};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Values values = new Values();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExtremeDimensions.MOD_ID + "-client.json");
	}

	public static void load() {
		Values v = DataStore.readWithFallback(file(), Values.class);
		values = v == null ? new Values() : v;
		if (values.meters == null) {
			values.meters = Corner.TOP_LEFT;
		}
		if (values.timer == null) {
			values.timer = TimerPlace.TOP;
		}
		boolean known = false;
		for (int s : HUD_SCALES) {
			known |= s == values.hudScale;
		}
		if (!known) {
			values.hudScale = 100;
		}
	}

	public static void save() {
		try {
			DataStore.writeAtomically(file(), GSON.toJson(values));
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.warn("Could not save the display options", e);
		}
	}

	/** No strobing, shaking or glitching: flashes become soft fades and pulses hold still. */
	public static boolean reduceFlashing() {
		return values.reduceFlashing;
	}

	/** Set colours that stay apart for every kind of colour blindness (they always also differ in shape). */
	public static boolean colourBlind() {
		return values.colourBlind;
	}

	public static Corner meters() {
		return values.meters;
	}

	public static TimerPlace timer() {
		return values.timer;
	}

	public static int hudScale() {
		return values.hudScale;
	}

	/** Hints for a first expedition (the guide) are shown. */
	public static boolean guidance() {
		return values.guidance;
	}

	public static void setReduceFlashing(boolean on) {
		values.reduceFlashing = on;
		save();
	}

	public static void setColourBlind(boolean on) {
		values.colourBlind = on;
		save();
	}

	public static void setMeters(Corner c) {
		values.meters = c;
		save();
	}

	public static void setTimer(TimerPlace t) {
		values.timer = t;
		save();
	}

	public static void setHudScale(int percent) {
		values.hudScale = percent;
		save();
	}

	public static void setGuidance(boolean on) {
		values.guidance = on;
		save();
	}

	/** For tests: back to the defaults (not saved). */
	public static void resetForTest() {
		values = new Values();
	}
}
