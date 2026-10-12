package com.extremedimensions.config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremedimensions.ExtremeDimensions;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Every balance value of the mod. Times are in seconds unless stated otherwise.
 *
 * <p>The values below are the defaults. On startup they are overridden by
 * {@code config/extremedimensions.json}; missing keys are written back so the file always lists every option.
 */
public final class Config {
	private Config() {}

	// --- Anchors ---------------------------------------------------------------------------------

	/** Maximum charge one player can store in a single anchor (30 minutes). */
	public static double MAX_CHARGE = 1800.0;
	/** An anchor only works within this many blocks of a portal of its type. */
	public static int PORTAL_RANGE = 5;
	/** Anchors within this many blocks of the portal you enter feed your expedition. */
	public static double LINK_RANGE = 8.0;
	/** The anchor meters appear in the HUD within this many blocks of a charged anchor. */
	public static double HUD_RANGE = 10.0;
	/** Players within this many blocks see an anchor's particles. */
	public static double PARTICLE_RANGE = 48.0;
	/** Charge lost per second by an anchor whose path to its portal is blocked. */
	public static double BLOCKED_LEAK_PER_SECOND = 1.0;
	/** Charge granted at once by an Ember Tether or Void Tether. */
	public static double TETHER_ITEM_CHARGE = 600.0;

	// --- Expeditions -----------------------------------------------------------------------------

	/** Charge drained per second beyond the wound, before penalties and distance. */
	public static double BASE_DRAIN = 1.0;
	/** Every this many blocks from your arrival portal adds +100% drain. */
	public static double TETHER_DISTANCE_STEP = 100.0;
	/** Below this much charge the Leeching starts: souls are visibly pulled out of you. */
	public static double LEECH_SECONDS = 60.0;
	/** Length of the Collapse pull (the player is dragged out of the dimension) before the death, in ticks. */
	public static int COLLAPSE_PULL_TICKS = 60;
	/** Ticks before a dormant portal is checked again after a bounce. */
	public static int BOUNCE_GRACE_TICKS = 40;

	// --- Physical tether -------------------------------------------------------------------------

	/** A new chain segment is laid every this many blocks walked. */
	public static double ROPE_SEGMENT = 1.0;
	/** Maximum chain segments per player; after that the oldest segment disappears. */
	public static int ROPE_MAX_SEGMENTS = 400;
	/** In the Overworld the tether snaps beyond this distance from its anchor. */
	public static double TETHER_BREAK_RANGE = 5.0;
	/** Standing this close to the anchor's portal never snaps the tether. */
	public static double TETHER_PORTAL_SLACK = 3.0;

	// --- Penalties -------------------------------------------------------------------------------

	/** Cards offered after a Collapse or a death beyond the wound. */
	public static int OFFER_COUNT = 3;
	/** Active play time one penalty stack lasts (10 minutes). */
	public static int PENALTY_SECONDS = 600;
	/** Ticks without looking around or without moving before a player counts as idle (15 s). */
	public static int IDLE_TICKS = 300;
	/** Horizontal blocks a player must move to count as active. */
	public static double ACTIVE_MOVE = 2.0;
	/** Ticks after respawning before the penalty screen opens. */
	public static int OFFER_DELAY_TICKS = 70;

	public static double ACCELERATED_DRAIN_PER_STACK = 0.75;
	public static double DEBT_FACTOR = 0.60;
	public static double LEAK_FRACTION = 0.50;
	public static double SCAR_HEALTH = 6.0;
	public static double CRACKED_ARMOR = 4.0;
	public static double HEAVY_LEGS = 0.15;
	public static double CLUMSY_HANDS = 0.35;
	public static int TOLL_LEVELS = 10;
	public static int MOURNING_SECONDS = 600;

	// Overworld penalties (only active, and only counting down, in the Overworld)
	public static double HOLLOW_HEART = 4.0;
	public static double LEADEN_STEPS = 0.10;
	public static double DULL_PICK = 0.25;
	public static int TITHE_LEVELS = 5;

	// --- Lore ------------------------------------------------------------------------------------

	/** Chance that a hostile mob killed by a player beyond the wound drops a Tether Fragment. */
	public static double FRAGMENT_CHANCE = 0.04;
	/** Fragment chance for Wither Skeletons, Endermen, Blazes, Ghasts and Shulkers. */
	public static double FRAGMENT_CHANCE_ELITE = 0.12;

	// --- Technical -------------------------------------------------------------------------------

	/** Ticks between state updates sent to each player. */
	public static int SYNC_INTERVAL_TICKS = 10;
	/** Ticks after joining before items, recipes and effects are handed out. */
	public static int JOIN_DELAY_TICKS = 40;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Loads {@code config/extremedimensions.json} over the defaults and writes back any missing keys. */
	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(ExtremeDimensions.MOD_ID + ".json");
		JsonObject json = new JsonObject();
		try {
			if (Files.exists(file)) {
				JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (parsed.isJsonObject()) {
					json = parsed.getAsJsonObject();
				}
			}
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.error("Could not read {}, using defaults", file, e);
		}

		JsonObject out = new JsonObject();
		for (Field f : Config.class.getDeclaredFields()) {
			int mod = f.getModifiers();
			if (!Modifier.isStatic(mod) || Modifier.isFinal(mod) || !Modifier.isPublic(mod)) {
				continue;
			}
			String key = f.getName().toLowerCase();
			try {
				if (json.has(key)) {
					JsonElement v = json.get(key);
					if (f.getType() == int.class) {
						f.setInt(null, v.getAsInt());
					} else if (f.getType() == double.class) {
						f.setDouble(null, v.getAsDouble());
					}
				}
				if (f.getType() == int.class) {
					out.addProperty(key, f.getInt(null));
				} else if (f.getType() == double.class) {
					out.addProperty(key, f.getDouble(null));
				}
			} catch (Exception e) {
				ExtremeDimensions.LOGGER.warn("Invalid config value for {}, keeping default", key);
			}
		}

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(out), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.warn("Could not write {}", file, e);
		}
	}
}
