package com.extremedimensions.config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
	/** 1 = a big title ("The Tether Tightens", "Expedition Ended") on respawn after a death that costs a curse; 0 = none. */
	public static int RESPAWN_TITLE = 0;

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
	/** Share of every curse's atonement a death takes away (0.5 = half, 0 = none, 1 = all). */
	public static double ATONE_DEATH_LOSS = 0.5;
	/** Points needed for a curse's last stack, and how many more for every stack above it. */
	public static int ATONE_BASE = 120;
	public static int ATONE_PER_STACK = 60;
	/** Chance that one card of an offer is an Extreme curse (never multiplied). */
	public static double EXTREME_CARD_CHANCE = 0.025;
	/** How likely a spilled stack lands on an Extreme curse compared to any other curse (1 = as likely, 0 = never). */
	public static double EXTREME_SPILL_WEIGHT = 0.2;
	/** Extreme curses take this many times the atonement points and Tether Fragments to lift. */
	public static double EXTREME_ATONE_FACTOR = 2.0;
	/** Stormcalled: seconds between strikes while a storm finds the player under open sky (random in between). */
	public static int STORM_STRIKE_MIN_SECONDS = 30;
	public static int STORM_STRIKE_MAX_SECONDS = 90;
	/**
	 * Atonement from kills only counts monsters that spawned naturally, that the player dealt at least this share of the
	 * damage to (a farm's fall does not count), and at most a few at one place: after {@code atone_kill_area_limit}
	 * kills within {@code atone_kill_area_radius} blocks in {@code atone_kill_area_minutes}, that place gives nothing.
	 */
	public static double ATONE_KILL_MIN_SHARE = 0.5;
	public static double ATONE_KILL_AREA_RADIUS = 24.0;
	public static int ATONE_KILL_AREA_LIMIT = 5;
	public static int ATONE_KILL_AREA_MINUTES = 10;
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

	// --- Relics: one bound to the Tether, each with a gift and a price -------------------------

	/** Ember Heart: drain multiplier in the Nether (the gift) and in the End (the price). */
	public static double RELIC_EMBER_HEART_NETHER = 0.8;
	public static double RELIC_EMBER_HEART_END = 1.2;
	/** Pale Shard: drain multiplier in the End (the gift) and in the Nether (the price). */
	public static double RELIC_PALE_SHARD_END = 0.8;
	public static double RELIC_PALE_SHARD_NETHER = 1.2;
	/** Hungering Hand: extra reach for blocks and creatures (blocks); maximum health it takes (2 = one heart). */
	public static double RELIC_HUNGERING_HAND_REACH = 2.0;
	public static double RELIC_HUNGERING_HAND_HEALTH = 2.0;
	/** Featherbone: extra fall before it hurts (blocks); armour it takes. */
	public static double RELIC_FEATHERBONE_FALL = 8.0;
	public static double RELIC_FEATHERBONE_ARMOR = 2.0;
	/** Keeper's Eye: fragment chance multiplier for wild kills; atonement multiplier for kills. */
	public static double RELIC_KEEPERS_EYE_FRAGMENTS = 2.0;
	public static double RELIC_KEEPERS_EYE_ATONEMENT = 0.5;
	/** Tithe Bell: extra share of the experience you gain (0.5 = half again); levels it takes on every safe return. */
	public static double RELIC_TITHE_BELL_XP = 0.5;
	public static int RELIC_TITHE_BELL_LEVELS = 1;
	/** Lurker's Tooth: Lurkers cannot take your items or armour; how many times as often they come for you. */
	public static double RELIC_LURKERS_TOOTH_OFTEN = 2.0;
	/** Seam Lantern: extra seconds in the Seam; how much farther monsters notice you at night in the Overworld (0.25 = a quarter). */
	public static int RELIC_SEAM_LANTERN_SECONDS = 60;
	public static double RELIC_SEAM_LANTERN_NOTICE = 0.25;
	/** Chance a slain Keeper leaves a relic; relics the Starved One leaves every player who fought it; chance a Seam chest holds one. */
	public static double RELIC_KEEPER_CHANCE = 0.35;
	public static int RELIC_STARVED_ONE = 1;
	public static double RELIC_SEAM_CHEST_CHANCE = 0.05;

	// --- Pacts: curses taken on willingly for an expedition, for what a safe return brings -------

	/** When pacts open: 0 = from the start, 1 = after the Ender Dragon falls, 2 = after the Starved One falls. */
	public static int PACT_UNLOCK = 2;
	/** Highest heat a pact may reach (a sworn stack is 1 heat, an Extreme one 2). */
	public static int PACT_MAX_HEAT = 30;
	/** On a safe return with a pact: atonement points per heat. */
	public static int PACT_ATONE_PER_HEAT = 6;
	/** Heat per Tether Fragment a safe return brings (a remainder is a chance at one more). */
	public static double PACT_HEAT_PER_FRAGMENT = 3.0;
	/** A relic is possible from this heat on, with this chance per heat, up to the cap. */
	public static int PACT_RELIC_MIN_HEAT = 4;
	public static double PACT_RELIC_CHANCE_PER_HEAT = 0.05;
	public static double PACT_RELIC_MAX_CHANCE = 0.8;
	/** Active seconds beyond the wound before a kept pact pays in full; a shorter expedition pays its share (0 = always in full). */
	public static int PACT_FULL_SECONDS = 600;

	// --- Technical -------------------------------------------------------------------------------

	/** Milliseconds per tick the Seam may spend building islands (blocks in chunks nobody has loaded wait). */
	public static int SEAM_BUILD_BUDGET_MS = 5;
	/** Ticks between state updates sent to each player. */
	public static int SYNC_INTERVAL_TICKS = 10;
	/** Ticks after joining before items, recipes and effects are handed out. */
	public static int JOIN_DELAY_TICKS = 40;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Layout version of the config file; files from before a version are brought up to date on loading. */
	private static final int VERSION = 2;

	/**
	 * Defaults that changed, by the version that changed them: {key, old default, new default}. A file still holding
	 * the old default gets the new one; a value someone chose stays.
	 */
	private static final String[][][] MIGRATIONS = {
		{},
		{},
		// 2.7.0: curses take longer to atone for (balance model, option C)
		{{"atone_base", "100", "120"}, {"atone_per_stack", "50", "60"}},
	};

	/**
	 * Where each section of the settings starts (its first setting), in the order above; the in-game settings screen
	 * groups the settings by these. A setting belongs to the last section that started before it.
	 */
	private static final Map<String, String> SECTION_STARTS = Map.ofEntries(
			Map.entry("max_charge", "anchors"), Map.entry("base_drain", "expeditions"), Map.entry("rope_segment", "tether"),
			Map.entry("offer_count", "curses"), Map.entry("fragment_chance", "lore"), Map.entry("lurker_enabled", "lurker"),
			Map.entry("hoard_wild_chance", "hollow"), Map.entry("soul_woven_drain", "weaving"), Map.entry("seam_seconds", "seam"),
			Map.entry("storm_chance", "dragon"), Map.entry("relic_ember_heart_nether", "relics"), Map.entry("pact_unlock", "pacts"),
			Map.entry("seam_build_budget_ms", "technical"));

	/** One setting: its key in the file, its section, the field behind it and its default (as text). */
	public record Setting(String key, String section, Field field, String fallback) {
		public boolean whole() {
			return field.getType() == int.class;
		}

		public String value() {
			try {
				return whole() ? String.valueOf(field.getInt(null)) : format(field.getDouble(null));
			} catch (IllegalAccessException e) {
				return "";
			}
		}
	}

	private static List<Setting> settings;

	/** A value as text without losing precision: 0.012 stays "0.012", 2.0 becomes "2" (cards round, settings never). */
	public static String format(double v) {
		if (!Double.isFinite(v)) {
			return "0";
		}
		return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v)
				: java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
	}

	/** Every setting, in the order of this class (curses' maximum and strength are separate, see {@link #snapshot()}). */
	public static List<Setting> settings() {
		if (settings == null) {
			List<Setting> out = new ArrayList<>();
			String section = "anchors";
			for (Field f : Config.class.getDeclaredFields()) {
				int mod = f.getModifiers();
				if (!Modifier.isStatic(mod) || Modifier.isFinal(mod) || !Modifier.isPublic(mod)
						|| (f.getType() != int.class && f.getType() != double.class)) {
					continue;
				}
				String key = f.getName().toLowerCase(Locale.ROOT);
				section = SECTION_STARTS.getOrDefault(key, section);
				// First built before the file is read (see load), so the field still holds its default here
				Setting st = new Setting(key, section, f, "");
				out.add(new Setting(key, section, f, st.value()));
			}
			settings = List.copyOf(out);
		}
		return settings;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExtremeDimensions.MOD_ID + ".json");
	}

	/** Loads {@code config/extremedimensions.json} over the defaults and writes back any missing keys. */
	public static void load() {
		Path file = file();
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
		for (Penalty pen : Penalty.values()) {
			pen.max = pen.defaultMax;
			pen.value = pen.defaultValue;
		}
		migrate(json);
		apply(json);
		save();
	}

	/** Brings an older file up to the current defaults where it still held the old ones (see MIGRATIONS). */
	public static void migrate(JsonObject json) {
		int from = json.has("config_version") ? json.get("config_version").getAsInt() : 1;
		if (json.size() == 0) {
			return;
		}
		for (int v = from + 1; v <= VERSION && v < MIGRATIONS.length; v++) {
			for (String[] m : MIGRATIONS[v]) {
				if (json.has(m[0]) && json.get(m[0]).getAsString().equals(m[1])) {
					json.addProperty(m[0], Integer.parseInt(m[2]));
					ExtremeDimensions.LOGGER.info("Config: {} follows its new default {} (was the old default {})", m[0], m[2], m[1]);
				}
			}
		}
	}

	/** Takes over every value the json holds (the file's layout); a value that does not fit keeps the current one. */
	public static void apply(JsonObject json) {
		for (Setting st : settings()) {
			if (json.has(st.key())) {
				try {
					JsonElement v = json.get(st.key());
					if (st.whole()) {
						st.field().setInt(null, v.getAsInt());
					} else {
						st.field().setDouble(null, v.getAsDouble());
					}
				} catch (Exception e) {
					ExtremeDimensions.LOGGER.warn("Invalid config value for {}, keeping {}", st.key(), st.value());
				}
			}
		}
		// Curses: { "scar": { "max": 3, "per_stack": 2.0 }, ... }
		JsonObject pens = json.has("penalties") && json.get("penalties").isJsonObject() ? json.getAsJsonObject("penalties") : new JsonObject();
		for (Penalty pen : Penalty.values()) {
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
		}
	}

	/** Every value as the file holds it. */
	public static JsonObject snapshot() {
		JsonObject out = new JsonObject();
		for (Setting st : settings()) {
			try {
				if (st.whole()) {
					out.addProperty(st.key(), st.field().getInt(null));
				} else {
					out.addProperty(st.key(), st.field().getDouble(null));
				}
			} catch (IllegalAccessException e) {
				// unreachable: the fields are public
			}
		}
		JsonObject penOut = new JsonObject();
		for (Penalty pen : Penalty.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("max", pen.max);
			o.addProperty("per_stack", pen.value);
			penOut.add(pen.id, o);
		}
		out.add("penalties", penOut);
		return out;
	}

	public static String toJson() {
		return GSON.toJson(snapshot());
	}

	/** Writes the current values to the file, crash-safe. */
	public static void save() {
		Path file = file();
		try {
			JsonObject out = snapshot();
			out.addProperty("config_version", VERSION);
			com.extremedimensions.data.DataStore.writeAtomically(file, GSON.toJson(out));
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.warn("Could not write {}", file, e);
		}
	}

	/**
	 * Changes one value from text: a setting by its key, or a curse as {@code penalty.<id>.max} /
	 * {@code penalty.<id>.per_stack}. Values may not be negative. Returns true if the value was taken.
	 */
	public static boolean set(String key, String raw) {
		String text = raw == null ? "" : raw.trim().replace(',', '.');
		try {
			if (key.startsWith("penalty.")) {
				String[] parts = key.split("\\.");
				Penalty pen = parts.length == 3 ? Penalty.byId(parts[1]) : null;
				if (pen == null) {
					return false;
				}
				if ("max".equals(parts[2])) {
					int v = Integer.parseInt(text);
					if (v < 0 || v > 99) {
						return false;
					}
					pen.max = v;
					return true;
				}
				if ("per_stack".equals(parts[2])) {
					double v = Double.parseDouble(text);
					if (!Double.isFinite(v) || v < 0) {
						return false;
					}
					pen.value = v;
					return true;
				}
				return false;
			}
			for (Setting st : settings()) {
				if (st.key().equals(key)) {
					if (st.whole()) {
						int v = Integer.parseInt(text);
						if (v < 0) {
							return false;
						}
						st.field().setInt(null, v);
					} else {
						double v = Double.parseDouble(text);
						if (!Double.isFinite(v) || v < 0) {
							return false;
						}
						st.field().setDouble(null, v);
					}
					return true;
				}
			}
		} catch (NumberFormatException | IllegalAccessException e) {
			return false;
		}
		return false;
	}
}
