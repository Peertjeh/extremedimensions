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
import com.extremedimensions.penalty.Penalty;

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

	// --- Curses (penalties) ----------------------------------------------------------------------
	// Each curse's maximum stacks and value per stack live in the "penalties" section of the file (max 0 = never offered).

	/** Cards offered after a Collapse or a death beyond the wound. */
	public static int OFFER_COUNT = 3;
	/** Ticks after respawning before the curse screen opens. */
	public static int OFFER_DELAY_TICKS = 70;
	/** Chance that an offer is multiplied: then every card carries its own multiplier between the two values below. */
	public static double MULTIPLIER_CHANCE = 0.15;
	public static int MULTIPLIER_MIN = 2;
	public static int MULTIPLIER_MAX = 4;
	/** Ticks without looking around or without moving before a player counts as idle (no atonement) (15 s). */
	public static int IDLE_TICKS = 300;
	/** Horizontal blocks a player must move to count as active. */
	public static double ACTIVE_MOVE = 2.0;

	// Atonement: points towards taking the next stack of a curse off
	/** Points per minute of active play on an expedition, per elite slain beyond, per safe return. */
	public static int ATONE_PER_MINUTE = 1;
	public static int ATONE_PER_ELITE = 3;
	public static int ATONE_PER_RETURN = 5;
	/** Points per Overworld day survived without dying, and per hostile mob slain at night in the Overworld. */
	public static int ATONE_PER_DAY = 10;
	public static int ATONE_PER_NIGHT_KILL = 1;
	/** Points needed for a curse's last stack, and how many more for every stack above it. */
	public static int ATONE_BASE = 100;
	public static int ATONE_PER_STACK = 50;
	/** Tether Fragments a mending at an anchor costs per stack the curse has (3 stacks = 3 fragments). */
	public static int FRAGMENT_COST_PER_STACK = 1;
	/** Chance that a Veil Ray drops a second Veil Wing. */
	public static double VEIL_WING_EXTRA_CHANCE = 0.35;

	// --- Lore ------------------------------------------------------------------------------------

	/** Chance that a hostile mob killed by a player beyond the wound drops a Tether Fragment. */
	public static double FRAGMENT_CHANCE = 0.04;
	/** Fragment chance for Wither Skeletons, Endermen, Blazes, Ghasts and Shulkers. */
	public static double FRAGMENT_CHANCE_ELITE = 0.12;

	// --- The Lurker ------------------------------------------------------------------------------

	/** 1 = Lurkers appear, 0 = never. */
	public static int LURKER_ENABLED = 1;
	/** Chance per player every 20 seconds that a Lurker appears (Overworld and Nether), outside the cooldown. */
	public static double LURKER_SPAWN_CHANCE = 0.012;
	/** Minimum seconds between two encounters for the same player. */
	public static int LURKER_COOLDOWN_SECONDS = 900;
	/** Chance that an appearance in the Overworld is a Lurker draining a nearby charged anchor. */
	public static double LURKER_DRAIN_CHANCE = 0.15;
	/** Chance that a watching Lurker comes closer to take something instead of leaving. */
	public static double LURKER_STALK_CHANCE = 0.15;
	public static int LURKER_WATCH_MIN = 30;
	public static int LURKER_WATCH_MAX = 90;
	/** Charge drained per second while a Lurker drinks an anchor, and for how long at most. */
	public static double LURKER_DRAIN_PER_SECOND = 20.0;
	public static int LURKER_DRAIN_SECONDS = 20;
	/** Share of the player's levels a Lurker takes. */
	public static double LURKER_XP_FRACTION = 0.3;
	/**
	 * From how many blocks away a stalking Lurker can do each thing to you. It strikes as soon as you are within
	 * the largest of these, and only picks what reaches you at that moment. 0 = it never does that.
	 */
	public static double LURKER_ITEM_RANGE = 3.2;
	public static double LURKER_XP_RANGE = 3.2;
	public static double LURKER_ARMOR_RANGE = 3.2;
	public static double LURKER_CURSE_RANGE = 3.2;
	/** How likely each is, relative to the others (only those in range and possible count). */
	public static double LURKER_ITEM_WEIGHT = 35;
	public static double LURKER_XP_WEIGHT = 20;
	public static double LURKER_ARMOR_WEIGHT = 15;
	public static double LURKER_CURSE_WEIGHT = 30;
	/** Soul light (lanterns, torches, campfires, fire) within this many blocks wards an anchor against draining. 0 = off. */
	public static double LURKER_WARD_RANGE = 4.0;
	/** Chance per watching Lurker every 5 seconds that wolves and cats near its target growl or hiss towards it. */
	public static double LURKER_ANIMAL_WARNING = 0.6;
	/** Seconds a Lurker runs off with its loot before it vanishes; kill it in time to get the loot back. */
	public static int LURKER_RUN_SECONDS = 9;
	/** Closest and furthest distance at which a watching Lurker shows itself. */
	public static int LURKER_WATCH_DISTANCE_MIN = 50;
	public static int LURKER_WATCH_DISTANCE_MAX = 72;
	/** Speed multiplier while stalking: it starts slow and speeds up as it closes in. */
	public static double LURKER_STALK_SPEED_MIN = 0.7;
	public static double LURKER_STALK_SPEED_MAX = 1.45;
	/** Speed multiplier at full run, reached after a short burst of acceleration. */
	public static double LURKER_RUN_SPEED = 1.4;

	// --- Hoards, Hollowlings and the Keeper ------------------------------------------------------

	/** Chance every 30 seconds, in a dark forest, pale garden or soul sand valley, that an old hoard lies nearby. */
	public static double HOARD_WILD_CHANCE = 0.08;
	/** Hollowlings that play around a hoard someone is near. */
	public static int HOLLOWLINGS_PER_HOARD = 3;
	/** Chance that a Tether Fragment, offered in soul light, earns a Hollowling's trust. */
	public static double HOLLOWLING_BOND_CHANCE = 0.4;
	/** Minutes of play between the things a companion Hollowling brings you. */
	public static int COMPANION_GIFT_MINUTES = 20;
	/** Chance that a companion Hollowling chases off a Lurker about to take something from you. */
	public static double COMPANION_GUARD_CHANCE = 0.6;
	/** 1 = hoards are guarded by a Keeper, 0 = never. */
	public static int KEEPER_ENABLED = 1;

	// --- Weaver's craft ---------------------------------------------------------------------------

	/** Drain multiplier with a Soul-Woven and a Seam-Woven Tether. */
	public static double SOUL_WOVEN_DRAIN = 0.85;
	public static double SEAM_WOVEN_DRAIN = 0.75;
	/** Extra Overworld reach (blocks) of a woven Tether before it snaps. */
	public static double WOVEN_REACH = 3.0;
	/** Extra Seam seconds per woven tier. */
	public static int WOVEN_SEAM_SECONDS = 60;
	/** Drain reduction per scar (up to five scars; a scar is earned for every curse stack taken off). */
	public static double SCAR_DRAIN_REDUCTION = 0.03;

	// --- The Seam --------------------------------------------------------------------------------

	/** Seconds a Seam Needle gives you in the Seam before it spits you out. */
	public static int SEAM_SECONDS = 300;

	// --- After the Dragon and the Mending --------------------------------------------------------

	/** Chance per Overworld player every minute (after the seal broke) that a Wound Storm breaks out nearby. */
	public static double STORM_CHANCE = 0.04;
	/** Minimum minutes between two storms for the same player. */
	public static int STORM_COOLDOWN_MINUTES = 30;
	public static int STORM_SECONDS = 60;
	/** End drain multiplier while the seal is broken: the Pale Void starves. */
	public static double STARVING_END_DRAIN = 1.25;
	/** Chance that a watching Lurker leaves an offering when it goes, once the seal is broken. */
	public static double LURKER_OFFERING_CHANCE = 0.3;
	/** Drain multiplier for everyone in a mended world. */
	public static double MENDED_DRAIN = 0.8;

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

		// Curses: { "scar": { "max": 3, "per_stack": 2.0 }, ... }
		JsonObject pens = json.has("penalties") && json.get("penalties").isJsonObject() ? json.getAsJsonObject("penalties") : new JsonObject();
		JsonObject penOut = new JsonObject();
		for (Penalty pen : Penalty.values()) {
			pen.max = pen.defaultMax;
			pen.value = pen.defaultValue;
			try {
				if (pens.has(pen.id) && pens.get(pen.id).isJsonObject()) {
					JsonObject o = pens.getAsJsonObject(pen.id);
					if (o.has("max")) {
						pen.max = Math.max(0, o.get("max").getAsInt());
					}
					if (o.has("per_stack")) {
						pen.value = o.get("per_stack").getAsDouble();
					}
				}
			} catch (Exception e) {
				ExtremeDimensions.LOGGER.warn("Invalid config value for penalty {}, keeping default", pen.id);
			}
			JsonObject o = new JsonObject();
			o.addProperty("max", pen.max);
			o.addProperty("per_stack", pen.value);
			penOut.add(pen.id, o);
		}
		out.add("penalties", penOut);

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(out), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.warn("Could not write {}", file, e);
		}
	}
}
