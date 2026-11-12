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

	/** What the title screen and the loading screens bring: the art, the music, both, or Minecraft's own. */
	public enum MenuStyle {
		FULL, ART, MUSIC, PLAIN;

		public boolean art() {
			return this == FULL || this == ART;
		}

		public boolean music() {
			return this == FULL || this == MUSIC;
		}
	}

	/**
	 * How much of the mod's own spectacle this computer draws (3.36): the sparks, rings and beams of the Elders' blows and
	 * the air of the Seam, all of it or a share. (The mod's other particles follow Minecraft's own Particles option.)
	 */
	public enum Effects {
		FULL(1.0), REDUCED(0.5), MINIMAL(0.2);

		/** The share drawn. */
		public final double factor;

		Effects(double factor) {
			this.factor = factor;
		}
	}

	/**
	 * How the Kindled Seam Lantern shines under a shader pack (3.51.1): a shader pack gives light and glow to the blocks it
	 * knows, and knows none of the mod's; with Iris, the lantern is shown to it as one of these vanilla light sources, whose
	 * glow and light colour it then takes (and which the pack's own settings may change). Off leaves it unknown to it.
	 */
	public enum ShaderLook {
		CRYING_OBSIDIAN("crying_obsidian"), AMETHYST_CLUSTER("amethyst_cluster"), SOUL_LANTERN("soul_lantern"),
		LANTERN("lantern"), OFF("");

		/** The vanilla block it is shown as, by its id in the minecraft namespace ("" for none). */
		public final String block;

		ShaderLook(String block) {
			this.block = block;
		}
	}

	/** What is stored. Every field has a safe default so a damaged or older file still loads. */
	private static final class Values {
		boolean reduceFlashing = false;
		boolean colourBlind = false;
		Corner meters = Corner.TOP_LEFT;
		TimerPlace timer = TimerPlace.TOP;
		int hudScale = 100;
		boolean guidance = true;
		boolean hints = true;
		MenuStyle menu = MenuStyle.FULL;
		Effects effects = Effects.FULL;
		ShaderLook shaderLook = ShaderLook.CRYING_OBSIDIAN;
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
		if (values.menu == null) {
			values.menu = MenuStyle.FULL;
		}
		if (values.effects == null) {
			values.effects = Effects.FULL;
		}
		if (values.shaderLook == null) {
			values.shaderLook = ShaderLook.CRYING_OBSIDIAN;
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

	/** A hint as a toast the first time the player meets each part of the mod. */
	public static boolean hints() {
		return values.hints;
	}

	public static MenuStyle menu() {
		return values.menu;
	}

	public static Effects effects() {
		return values.effects;
	}

	public static void setEffects(Effects e) {
		values.effects = e;
		save();
	}

	public static ShaderLook shaderLook() {
		return values.shaderLook;
	}

	/** (Taken up the next time the shader pack is loaded.) */
	public static void setShaderLook(ShaderLook look) {
		values.shaderLook = look;
		save();
	}

	/**
	 * How many of something to draw at the effects level, from what full effects draw: the share, with the fraction
	 * left over drawn by chance (so a third of one is one every third time).
	 */
	public static int scaled(int full, net.minecraft.util.RandomSource random) {
		double want = full * values.effects.factor;
		int whole = (int) Math.floor(want);
		return whole + (random.nextDouble() < want - whole ? 1 : 0);
	}

	public static void setMenu(MenuStyle m) {
		values.menu = m;
		save();
	}

	public static void setHints(boolean on) {
		values.hints = on;
		save();
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
