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
	/** 1 = the expedition timer is a boss bar at the top of the screen, as Minecraft shows such things; 0 = the mod's own panel. */
	public static int HUD_BOSS_BAR = 1;
	/**
	 * 1 = the mod acts only in its own worlds: the Overworld, the Nether, the End and the Seam. In another mod's world its
	 * curses sleep, an expedition waits, and nothing of it spawns or strikes. 0 = it acts in every world.
	 */
	public static int OWN_WORLDS_ONLY = 1;

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
	/** Seconds an offer of curse cards may stand unanswered before the Tether chooses one itself (0 = never). */
	public static int OFFER_TIMEOUT_SECONDS = 120;
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
	/** Active seconds beyond the wound an expedition needs before its safe return counts and earns atonement. */
	public static int ATONE_RETURN_MIN_SECONDS = 60;
	/** Points per Overworld day survived without dying, and per hostile mob slain at night in the Overworld. */
	public static int ATONE_PER_DAY = 10;
	public static int ATONE_PER_NIGHT_KILL = 1;
	/** Active seconds in a night a player needs for its dawn to earn atonement (idling or sleeping it away earns none). */
	public static int ATONE_NIGHT_ACTIVE_SECONDS = 120;
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
	/** Minutes (of the world's time) after a hoard is wrecked before a new one may nest within 256 blocks of it. */
	public static int HOARD_WRECK_MINUTES = 120;
	/** Hollowlings that play around a hoard someone is near. */
	public static int HOLLOWLINGS_PER_HOARD = 3;
	/** Seconds before a killed Hollowling is replaced by another (one at a time, each a full wait after the last). */
	public static int HOLLOWLING_RESPAWN_SECONDS = 120;
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
	/** Whether the Fray lies beneath the islands (knots and strands of frayed thread, wells down to them): 1 = on, 0 = off. */
	public static int SEAM_FRAY = 1;
	/** Height below which a visitor is in the Fray. */
	public static int SEAM_FRAY_TOP = 56;
	/** How much faster time runs out in the Fray. */
	public static double SEAM_FRAY_DRAIN = 2.0;
	/** Chance an island has a well down to the Fray at its edge. */
	public static double SEAM_WELL_CHANCE = 0.35;
	/** Chance a knot of the Fray holds a snarl (a barrel of what the Seam swallowed). */
	public static double SEAM_SNARL_CHANCE = 0.6;
	/** Thinned that walk each knot of the Fray near a visitor (they hunt there from the start). */
	public static int SEAM_FRAY_THINNED = 2;
	/** Chance a Thinned slain in the Fray leaves a Tether Fragment. */
	public static double SEAM_FRAY_FRAGMENT_CHANCE = 0.12;
	/** Frayed Rays about each visitor in the Fray: Veil Rays gone wrong down there, that dive at visitors (0 = none). */
	public static int SEAM_FRAY_RAYS = 2;
	/** How much faster a Frayed Ray flies than a Veil Ray over the islands. */
	public static double SEAM_FRAY_RAY_SPEED = 1.6;
	/** Damage a Frayed Ray's dive does when it strikes (2 = one heart; the world's difficulty scales it). */
	public static double SEAM_FRAY_RAY_DAMAGE = 6.0;
	/** Seconds of poison a Frayed Ray's strike leaves (0 = none). */
	public static int SEAM_FRAY_RAY_POISON = 6;
	/** Seconds a Frayed Ray circles between one dive and the next, at the least. */
	public static int SEAM_FRAY_RAY_COOLDOWN = 7;
	/** Seconds the dark of the Fray closes in on a visitor when it does (0 = never). */
	public static int SEAM_FRAY_GLOOM = 5;
	/** About how many seconds pass between the times the dark of the Fray closes in. */
	public static int SEAM_FRAY_GLOOM_EVERY = 70;
	/** Chance each second that the strand under a visitor's feet starts to give way (0 = never; it is woven back later). */
	public static double SEAM_FRAY_UNRAVEL = 0.12;
	/** On how many of a visitor's first visits the Seam says on arrival what to do there, and the Fray on the first descents (0 = never). */
	public static int SEAM_BRIEFING = 3;
	/**
	 * Whether a Return Gate stands where visitors arrive in the Seam, its rift between its pylons and a bridge from
	 * its island to the nearest one (1 = on, 0 = off: a bare island and the rift beside the landing).
	 */
	public static int SEAM_RETURN_GATE = 1;
	/** Whether the HUD points the way to the rift home beside its distance (1 = on, 0 = off). */
	public static int SEAM_RIFT_COMPASS = 1;
	/** Blocks within which the rift home can be heard humming (0 = silent). */
	public static int SEAM_RIFT_HUM = 40;
	/** Whether the rift home wanders only over its gate's threshold (1) or over any ground (0); a Restless Rift trial always lets it roam. */
	public static int SEAM_RIFT_LEASH = 1;
	/**
	 * Whether rifts are seen as tears in the world, a burning rim round a tunnel of light with the world they lead to
	 * hazy at its far end, that you walk through (1 = on, 0 = off: a spot of sparks you step into).
	 */
	public static int SEAM_PORTAL = 1;
	/** Radius, in tenths of a block, of the rift home's tear (at most 24 in a gate, whose opening is five blocks wide). */
	public static int SEAM_PORTAL_RADIUS = 20;
	/** Radius, in tenths of a block, of the tear a Seam Needle opens in the world. */
	public static int SEAM_PORTAL_ENTRY_RADIUS = 16;
	/** Whether a reset of the Seam keeps the old one aside in the world's folder (1) or wipes it (0). */
	public static int SEAM_RESET_BACKUP = 0;
	/**
	 * Of every hundred buildings in the Seam, how many are damaged or worse (a third of those ruins, crowns fallen and
	 * walls broken open); the rest are only weathered, or now and then intact. 0: none damaged.
	 */
	public static int SEAM_DAMAGE = 45;

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
	/** Kindred Knot: the wait before you can fall again, as a multiple (0.5 = halved); how long you last fallen, as a multiple (0.67 = a third shorter). */
	public static double RELIC_KINDRED_KNOT_COOLDOWN = 0.5;
	public static double RELIC_KINDRED_KNOT_BLEED = 0.67;
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

	// --- Rift Trials: the week's goal in the Seam, a world scoreboard and medals ---------------

	/** When Rift Trials open: 0 = from the start, 1 = after the Ender Dragon falls, 2 = after the Starved One falls. */
	public static int TRIAL_UNLOCK = 0;
	/** Days each trial lasts before the next one (7 = a new trial every Monday, UTC). */
	public static int TRIAL_ROTATION_DAYS = 7;
	/** Vein-cutter: Seam Veins broken for bronze, silver and gold. */
	public static int TRIAL_VEINS_BRONZE = 5;
	public static int TRIAL_VEINS_SILVER = 12;
	public static int TRIAL_VEINS_GOLD = 20;
	/** Hunter: Veil Rays and Thinned slain. */
	public static int TRIAL_HUNT_BRONZE = 2;
	public static int TRIAL_HUNT_SILVER = 5;
	public static int TRIAL_HUNT_GOLD = 9;
	/** Wanderer: islands (100-block cells) set foot on, the arrival one included. */
	public static int TRIAL_WANDER_BRONZE = 3;
	public static int TRIAL_WANDER_SILVER = 6;
	public static int TRIAL_WANDER_GOLD = 9;
	/** Far-walker: blocks from the arrival point at the farthest. */
	public static int TRIAL_FAR_BRONZE = 120;
	public static int TRIAL_FAR_SILVER = 240;
	public static int TRIAL_FAR_GOLD = 400;
	/** Plunderer: fresh Seam barrels opened (each barrel counts once, ever). */
	public static int TRIAL_PLUNDER_BRONZE = 1;
	public static int TRIAL_PLUNDER_SILVER = 2;
	public static int TRIAL_PLUNDER_GOLD = 4;
	/** Short Thread twist: the visit's time is multiplied by this. */
	public static double TRIAL_SHORT_FACTOR = 0.75;
	/** What each medal pays, the first time it is reached in a week. */
	public static int TRIAL_BRONZE_FRAGMENTS = 2;
	public static int TRIAL_BRONZE_ATONE = 30;
	public static int TRIAL_SILVER_FRAGMENTS = 3;
	public static int TRIAL_SILVER_ATONE = 60;
	public static int TRIAL_GOLD_FRAGMENTS = 4;
	public static int TRIAL_GOLD_ATONE = 90;
	public static int TRIAL_GOLD_RELICS = 1;

	// --- The Elders: the Matriarch, the Ferryman, the Last Weaver, and a hungrier Starved One ----

	/** Keepers a player must have slain before the next hoard they wake is guarded by the Matriarch (0 = never). */
	public static int MATRIARCH_AFTER_KEEPERS = 3;
	/** Hollowlings the Matriarch calls when her health falls below 70% and again below 35%. */
	public static int MATRIARCH_BROOD = 3;
	/** Relics the Matriarch always leaves. */
	public static int MATRIARCH_RELICS = 1;
	/** When the Ferryman comes: 0 = from the start, 1 = after the Ender Dragon falls, 2 = after the Starved One falls. */
	public static int FERRYMAN_UNLOCK = 1;
	/** Active seconds on a Nether expedition before the Ferryman may come (once per expedition). */
	public static int FERRYMAN_AFTER_SECONDS = 300;
	/** Chance he comes once that time has passed; each stack of Ferryman's Fee adds the same again. */
	public static double FERRYMAN_CHANCE = 0.2;
	/** While he lives, the Tether drains this many times faster (half again more once he is wounded below half). */
	public static double FERRYMAN_DRAIN = 2.0;
	/** Levels each of his blows takes. */
	public static int FERRYMAN_TOLL_LEVELS = 1;
	/** Levels he collects from someone who goes home while he still lives. */
	public static int FERRYMAN_ESCAPE_LEVELS = 5;
	/** Chance he leaves a relic. */
	public static double FERRYMAN_RELIC_CHANCE = 0.5;
	/** When the Last Weaver wakes: 0 = from the start, 1 = after the Ender Dragon falls, 2 = after the Starved One falls. */
	public static int WEAVER_UNLOCK = 2;
	/** Chance the Last Weaver wakes at a village of the Thinned on its first visit (each village rolls once). */
	public static double WEAVER_CHANCE = 0.4;
	/** Seconds of Seam time each of the Weaver's blows unpicks. */
	public static int WEAVER_TIME_THEFT = 10;
	/** Seconds of Seam time given back to everyone near when it falls. */
	public static int WEAVER_TIME_GIFT = 60;
	/** Relics the Last Weaver always leaves. */
	public static int WEAVER_RELICS = 1;
	/** How readily the Weaver fires its Seraphic Lance: its weight against its other blows (which weigh 1 to 4 each). */
	public static int WEAVER_LANCE_WEIGHT = 8;
	/** Chance the Lance sweeps back once more after its first pass (always, once the Weaver is enraged). */
	public static double WEAVER_LANCE_RETURN = 0.5;
	/** Damage of a Lance beam. */
	public static double WEAVER_LANCE_DAMAGE = 12.0;
	/** Rings of beams the Weaver's ultimate sweeps a full circle round it (0 to 4). */
	public static int WEAVER_ULTIMATE_RINGS = 2;
	/** Damage of a ring beam in the Weaver's ultimate. */
	public static double WEAVER_ULTIMATE_RING_DAMAGE = 10.0;
	/** How fast the Elders move, as a multiple of their own pace. */
	public static double ELDER_SPEED = 1.5;
	/** How often the Elders strike: the pause between their blows is divided by this. */
	public static double ELDER_ATTACK_SPEED = 1.5;
	/** How quickly a blow comes once an Elder starts it (its wind-up and the warning before it lands), as a multiple. */
	public static double ELDER_WINDUP_SPEED = 1.25;
	/** Whether the Elders' blows tear up the world (craters, torn-up ground, scorched and changed blocks): 1 = on, 0 = off. The mobGriefing rule must allow it too. */
	public static int ELDER_GRIEFING = 1;
	/** Share of its health at which an Elder unleashes its ultimate, once per fight (0 = never). */
	public static double ELDER_ULTIMATE_AT = 0.5;
	/** Seconds an Elder's ultimate lasts. */
	public static int ELDER_ULTIMATE_SECONDS = 10;
	/** Blocks from the Elder within which its ultimate's curse reaches everyone. */
	public static int ELDER_ULTIMATE_RADIUS = 150;
	/** Extreme curses an ultimate lays on everyone it reaches (random, among those with room). */
	public static int ELDER_ULTIMATE_CURSES = 2;
	/** Whether someone with no room for those curses has everything they carry flung around them instead: 1 = on, 0 = off. */
	public static int ELDER_ULTIMATE_SCATTER = 1;
	/** Share of the damage an Elder shrugs off while its ultimate builds. */
	public static double ELDER_ULTIMATE_GUARD = 0.6;
	/** Seconds an Elder takes to form out of matter and light before it fights (it cannot be hurt meanwhile). */
	public static double ELDER_SPAWN_SECONDS = 7.0;
	/** Whether the view of everyone near is held on an Elder while it forms: 1 = on, 0 = off. */
	public static int ELDER_SPAWN_CAMERA = 1;
	/** Whether an Elder raises its arena round it as it forms (blocks flying in to build it): 1 = on, 0 = off. */
	public static int ELDER_ARENA = 1;
	/** Size of the arenas, as a multiple (0.5 to 12). */
	public static double ELDER_ARENA_SIZE = 12.0;
	/** Whether an arena of size 3 or more gets a ring wall round its floor: 1 = on, 0 = off. */
	public static int ELDER_ARENA_WALL = 1;
	/** Whether an arena of size 3 or more gets its Elder's great piece (the Matriarch's root arches, the Ferryman's wreck, the Weaver's loom): 1 = on, 0 = off. */
	public static int ELDER_ARENA_LANDMARK = 1;
	/** Seconds an arena takes to build. */
	public static double ELDER_ARENA_BUILD_SECONDS = 40.0;
	/** Most blocks of an arena in flight at once; the rest simply appear in their places. */
	public static int ELDER_ARENA_FLY_MAX = 700;
	/** Blocks from the nearest player within which a block of an arena flies to its place; further off it simply appears. */
	public static int ELDER_ARENA_FLY_RANGE = 48;
	/** Nearest a block of an arena appears from its place, in blocks. */
	public static int ELDER_ARENA_FLIGHT_MIN = 10;
	/** Furthest a block of an arena appears from its place, in blocks. */
	public static int ELDER_ARENA_FLIGHT_MAX = 15;
	/** Ticks a block of an arena takes to fly to its place (20 ticks = 1 second). */
	public static int ELDER_ARENA_FLIGHT_TICKS = 24;
	/** Seconds an arena stands after its fight before it comes apart (-1 = it stays for good). */
	public static int ELDER_ARENA_LINGER_SECONDS = 30;
	/** Seconds an arena takes to come apart (the ground is put back as it was). */
	public static double ELDER_ARENA_DISSOLVE_SECONDS = 25.0;
	/** How much debris the Elders' blows throw, as a multiple (0.25 to 2); their sigils, rings and threads are always drawn. */
	public static double ELDER_ATTACK_FX_DETAIL = 1.0;
	/** An Elder's health, as a multiple of its own (before the players fighting it are counted). */
	public static double ELDER_HEALTH_SCALE = 1.5;
	/** An Elder's health grows by this share for every player fighting it beyond the first. */
	public static double ELDER_HEALTH_PER_PLAYER = 0.5;
	/** The damage of the Elders' blows, as a multiple. */
	public static double ELDER_DAMAGE_SCALE = 1.5;
	/** The damage of an Elder's blows grows by this share for every player fighting it beyond the first. */
	public static double ELDER_DAMAGE_PER_PLAYER = 0.1;
	/** The pause between an Elder's blows shrinks by this share for every player fighting it beyond the first. */
	public static double ELDER_REST_PER_PLAYER = 0.1;
	/** The pause between an Elder's blows never shrinks below this share of its own. */
	public static double ELDER_REST_MIN = 0.6;
	/** Most players an Elder's great blows fall on at once: its charges come again at the next, its Lance splits, its spears, webs and cages fall on each. */
	public static int ELDER_SPLIT_MAX = 3;
	/** Whether each Elder's great blow (the Thornbound Charge, the Crossing of the Dead, the Seraphic Lance) goes through armour: 1 = on, 0 = off. */
	public static int ELDER_PIERCE = 1;
	/** Blocks from an Elder within which a player counts as fighting it (for its health, damage and pace). */
	public static int ELDER_FIGHTERS_RANGE = 200;
	/** How far the Elders' ranged blows reach and how far they follow their prey, as a multiple (for the larger arenas). */
	public static double ELDER_REACH = 3.0;
	/** Extra brood an Elder calls (the Matriarch's Hollowlings, the Weaver's Thinned) for every player fighting it beyond the first. */
	public static int ELDER_BROOD_PER_PLAYER = 1;
	/** Blocks round an Elder its spell reaches (the Matriarch's hex, the Ferryman's weight) while it fights. */
	public static int ELDER_SPELL_RANGE = 250;
	/** Seconds of gliding on elytra near an Elder before it pulls the flyer down (0 = it never does). */
	public static double ELDER_SNARE_SECONDS = 1.0;
	/** Damage of an Elder pulling a flyer down. */
	public static double ELDER_SNARE_DAMAGE = 6.0;
	/** Seconds a flyer's wings stay bound after an Elder pulls them down: no gliding and no rockets. */
	public static int ELDER_BIND_SECONDS = 10;
	/** How much slower the Matriarch's hex makes everyone in her reach walk (0.25 = at 75% of their speed; 0 = no hex). */
	public static double MATRIARCH_HEX_SLOW = 0.25;
	/** Whether the Matriarch's hex keeps milk from being drunk: 1 = on, 0 = off. */
	public static int MATRIARCH_HEX_MILK = 1;
	/** How much lower the Ferryman's weight makes everyone in his reach jump (0.3 = at 70% of their jump; 0 = no weight). */
	public static double FERRYMAN_WEIGHT_JUMP = 0.3;
	/** Blows on one bar of the Ferryman's gaol that break it open. */
	public static int FERRYMAN_GAOL_HITS = 3;
	/** Seconds the Ferryman's gaol stands before it closes. */
	public static double FERRYMAN_GAOL_SECONDS = 1.5;
	/** Damage of the Ferryman's gaol closing on those still inside it. */
	public static double FERRYMAN_GAOL_DAMAGE = 14.0;
	/** How much sooner the Weaver's blows land once it starts them, as a multiple (the rhythm between its blows stays). */
	public static double WEAVER_WINDUP_SPEED = 1.6;
	/** How long the Seraphic Lance's beam burns, as a multiple. */
	public static double WEAVER_LANCE_LENGTH = 1.5;
	/** The Starved One's health grows by this share for every player at the Heart beyond the first. */
	public static double STARVED_HEALTH_PER_PLAYER = 0.5;
	/** Below this share of its health the Starved One turns ravenous: faster, quicker tricks, and famine. */
	public static double STARVED_ENRAGE_AT = 0.3;
	/** Its speed once ravenous, as a multiple. */
	public static double STARVED_ENRAGE_SPEED = 1.3;

	// --- Co-op -----------------------------------------------------------------------------------

	/** Whether a player struck down with an ally near falls instead of dying, to be lifted up again: 1 = on, 0 = off. */
	public static int COOP_REVIVE = 1;
	/** Blocks within which an ally must be for a player to fall instead of dying. */
	public static int COOP_REVIVE_RANGE = 32;
	/** Seconds a fallen player lasts before they bleed out. */
	public static int COOP_FALLEN_SECONDS = 30;
	/** Minutes after a fall before a player can fall again: struck down sooner, they die (0 = no wait). */
	public static int COOP_FALLEN_COOLDOWN_MINUTES = 30;
	/** How fast the fallen crawl, as a share of a walk (0 = not at all). */
	public static double COOP_FALLEN_CRAWL_SPEED = 0.2;
	/** Whether the fallen are kept from commands and from leaving their world: 1 = on, 0 = off. */
	public static int COOP_FALLEN_LOCKED = 1;
	/** Seconds an ally must crouch beside a fallen player to lift them up. */
	public static double COOP_LIFT_SECONDS = 4.0;
	/** Share of their health a lifted player gets back. */
	public static double COOP_LIFT_HEALTH = 0.3;
	/** Who counts as an ally: 0 = everyone not on another scoreboard team, 1 = only players on the same team. */
	public static int COOP_ALLIES = 0;
	/** Whether a player may carry one stack of an ally's curse for them, with their consent: 1 = on, 0 = off. */
	public static int COOP_BURDEN = 1;
	/** Extra stacks of the curse the one who carries it takes on as well (the toll of carrying it). */
	public static int COOP_BURDEN_TOLL = 0;
	/** Minutes before a player can carry another curse for the same ally. */
	public static int COOP_BURDEN_COOLDOWN_MINUTES = 5;
	/** Stacks one player may be relieved of per Overworld day, by all their allies together. */
	public static int COOP_BURDEN_RELIEF_PER_DAY = 3;
	/** Whether players may open their anchors to their allies: 1 = on, 0 = off. */
	public static int COOP_SHARED_ANCHORS = 1;

	// --- The tribe -------------------------------------------------------------------------------

	/** Whether the tribe remembers what each player does to it (standing, gifts, barter, raids): 1 = on, 0 = off. */
	public static int TRIBE_ENABLED = 1;
	/** Standing from which the tribe tolerates a player: its young stop stealing from them and will barter. */
	public static int TRIBE_TOLERATED_AT = 15;
	/** Standing from which the tribe trusts a player: better prices, their own hoards open without a fight. */
	public static int TRIBE_TRUSTED_AT = 40;
	/** Standing from which a player is kin to the tribe: the best prices, rare wares, Lurkers often spare them. */
	public static int TRIBE_KIN_AT = 75;
	/** Standing below which the tribe hates a player: its young steal from them twice as often. */
	public static int TRIBE_HATED_AT = -20;
	/** Standing below which the tribe hunts a player: a Keeper comes for them now and then. */
	public static int TRIBE_HUNTED_AT = -50;
	/** Standing gained for a gift to a Hollowling. */
	public static int TRIBE_REP_GIFT = 3;
	/** Gifts per day that win standing (a day in the Overworld). */
	public static int TRIBE_GIFTS_PER_DAY = 4;
	/** Standing gained for each barter. */
	public static int TRIBE_REP_BARTER = 1;
	/** Standing gained when a Hollowling bonds with the player. */
	public static int TRIBE_REP_BOND = 10;
	/** In-game days before bonding with one of the young again earns standing (a bond made again and again earns it once). */
	public static int TRIBE_BOND_REP_DAYS = 3;
	/** Standing lost for killing a Hollowling (negative). */
	public static int TRIBE_REP_HOLLOWLING = -12;
	/** Standing lost for killing a Keeper (negative). */
	public static int TRIBE_REP_KEEPER = -8;
	/** Standing lost for killing a Lurker (negative). */
	public static int TRIBE_REP_LURKER = -1;
	/** Standing lost for breaking up a hoard that is not the player's own (negative). */
	public static int TRIBE_REP_HOARD_BROKEN = -6;
	/** Standing lost for raiding a hoard (negative). */
	public static int TRIBE_REP_RAID = -15;
	/** Minutes of play in which a player the tribe holds a grudge against wins back one point of standing (0 = never). */
	public static int TRIBE_FORGET_MINUTES = 15;
	/** Minutes between the Keepers that hunt a player the tribe hunts (0 = never). */
	public static int TRIBE_HUNT_MINUTES = 25;
	/** Chance a Lurker still steals from someone who is kin to the tribe (1 = as from anyone). */
	public static double TRIBE_KIN_THEFT_CHANCE = 0.5;
	/** Wares a Hollowling offers in a day. */
	public static int TRIBE_BARTER_OFFERS = 4;
	/** Scales every price in barter. */
	public static double TRIBE_BARTER_PRICE = 1.0;
	/** Share taken off prices for players the tribe trusts. */
	public static double TRIBE_BARTER_TRUSTED_DISCOUNT = 0.25;
	/** Share taken off prices for kin of the tribe. */
	public static double TRIBE_BARTER_KIN_DISCOUNT = 0.4;
	/** Emeralds a Hollowling asks for a Tether Fragment (before discounts). */
	public static int TRIBE_BARTER_FRAGMENT_PRICE = 5;
	/** Emeralds a Hollowling asks kin for a relic (before discounts; 0 = never offered). */
	public static int TRIBE_BARTER_RELIC_PRICE = 20;
	/** Whether opening a hoard that is not the player's own starts a raid: 1 = on, 0 = off (the Keeper only). */
	public static int TRIBE_RAID = 1;
	/** Young that swarm out of a raided hoard. */
	public static int TRIBE_RAID_YOUNG = 5;
	/** Seconds the young of a raided hoard fight before the Keeper wakes. */
	public static int TRIBE_RAID_YOUNG_SECONDS = 45;
	/** Blocks from the hoard a raider may go before the raid fails. */
	public static int TRIBE_RAID_RANGE = 48;
	/** Chance the spoils of a raid hold a relic. */
	public static double TRIBE_RAID_RELIC_CHANCE = 0.15;

	// --- Technical -------------------------------------------------------------------------------

	/** Milliseconds per tick the Seam may spend building islands (blocks in chunks nobody has loaded wait). */
	public static int SEAM_BUILD_BUDGET_MS = 5;
	/** Blocks from a player within which the Seam builds itself before their eyes, blocks flying in out of the nothing (0 = never: they are simply there). */
	public static int SEAM_FLY_RANGE = 96;
	/** Most blocks in the air at once while the Seam builds itself. */
	public static int SEAM_FLY_MAX = 900;
	/** Ticks a block of the Seam takes to fly to its place. */
	public static int SEAM_FLY_TICKS = 18;
	/** About how many blocks off its place a block of the Seam appears. */
	public static int SEAM_FLY_FROM = 12;
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
			Map.entry("trial_unlock", "trials"), Map.entry("matriarch_after_keepers", "elders"),
			Map.entry("coop_revive", "coop"), Map.entry("tribe_enabled", "tribe"), Map.entry("seam_build_budget_ms", "technical"));

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

	/**
	 * Values that must stay within bounds or the game breaks (a divisor of zero, an overflowing timer, a negative
	 * drain): {key, lowest, highest}. Anything set outside them, from the file or the settings screen, is brought back
	 * inside.
	 */
	private static final Object[][] BOUNDS = {
			{"sync_interval_ticks", 1, 200}, {"join_delay_ticks", 0, 1200}, {"idle_ticks", 20, 72000},
			{"collapse_pull_ticks", 1, 400}, {"leech_seconds", 1, 3600}, {"rope_segment", 0.25, 8},
			{"lurker_ward_range", 0, 16}, {"scar_drain_reduction", 0, 0.2}, {"pact_full_seconds", 0, 86400},
			{"elder_speed", 0.1, 10}, {"elder_attack_speed", 0.1, 10}, {"elder_windup_speed", 0.2, 10},
			{"elder_ultimate_seconds", 2, 120}, {"elder_arena_linger_seconds", -1, 86400},
			{"seam_build_budget_ms", 1, 50}, {"seam_fly_range", 0, 128}, {"seam_fly_max", 1, 5000}, {"seam_fly_ticks", 10, 200},
			{"seam_fly_from", 2, 64}, {"seam_damage", 0, 100},
			{"seam_fray_rays", 0, 8}, {"seam_fray_ray_speed", 0.5, 4}, {"seam_fray_ray_damage", 0, 40}, {"seam_fray_ray_poison", 0, 60},
			{"seam_fray_ray_cooldown", 1, 120}, {"seam_fray_gloom", 0, 30}, {"seam_fray_gloom_every", 10, 3600}, {"seam_fray_unravel", 0, 1},
			{"seam_briefing", 0, 100}, {"hud_boss_bar", 0, 1},
			{"elder_health_scale", 0.1, 20}, {"elder_health_per_player", 0, 5}, {"elder_damage_scale", 0, 20}, {"elder_damage_per_player", 0, 2},
			{"elder_rest_per_player", 0, 0.5}, {"elder_rest_min", 0.1, 1}, {"elder_split_max", 1, 8}, {"elder_fighters_range", 8, 256},
			{"elder_reach", 1, 5}, {"elder_brood_per_player", 0, 10}, {"elder_spell_range", 16, 256}, {"elder_snare_seconds", 0, 30},
			{"elder_snare_damage", 0, 100}, {"elder_bind_seconds", 0, 120}, {"matriarch_hex_slow", 0, 0.9}, {"ferryman_weight_jump", 0, 0.9},
			{"ferryman_gaol_hits", 1, 20}, {"ferryman_gaol_seconds", 0.5, 6}, {"ferryman_gaol_damage", 0, 100}, {"weaver_windup_speed", 0.5, 4}, {"weaver_lance_length", 0.5, 4},
			{"elder_arena_size", 0.5, 12}, {"elder_arena_wall", 0, 1}, {"elder_arena_landmark", 0, 1}, {"elder_arena_fly_max", 50, 5000}, {"elder_arena_fly_range", 8, 256}, {"coop_fallen_seconds", 1, 3600}, {"coop_fallen_cooldown_minutes", 0, 1440},
			{"offer_timeout_seconds", 0, 3600}, {"atone_night_active_seconds", 0, 600}, {"coop_burden_relief_per_day", 0, 100}};

	/** Brings a setting back within its bounds, if it has any. */
	private static void bound(Setting st) {
		for (Object[] b : BOUNDS) {
			if (!b[0].equals(st.key())) {
				continue;
			}
			double lo = ((Number) b[1]).doubleValue(), hi = ((Number) b[2]).doubleValue();
			try {
				if (st.whole()) {
					st.field().setInt(null, (int) Math.max(lo, Math.min(hi, st.field().getInt(null))));
				} else {
					double v = st.field().getDouble(null);
					st.field().setDouble(null, Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo);
				}
			} catch (IllegalAccessException e) {
				// unreachable: the fields are public
			}
			return;
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
						double d = v.getAsDouble();
						if (!Double.isFinite(d)) {
							throw new NumberFormatException("not a number");
						}
						st.field().setDouble(null, d);
					}
				} catch (Exception e) {
					ExtremeDimensions.LOGGER.warn("Invalid config value for {}, keeping {}", st.key(), st.value());
				}
			}
			bound(st);
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
						double v = o.get("per_stack").getAsDouble();
						if (Double.isFinite(v) && v >= 0) {
							pen.value = v;
						}
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
						bound(st);
					} else {
						double v = Double.parseDouble(text);
						if (!Double.isFinite(v) || v < 0) {
							return false;
						}
						st.field().setDouble(null, v);
						bound(st);
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
