package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.data.PlayerData;

/**
 * Curses: penalties chosen after a Dimensional Collapse or a death beyond the wound (and, when enabled, after a death in
 * the Overworld). They are permanent: every pick adds stacks, up to the curse's own maximum, and only atonement, Tether
 * Fragments or a Mending Cloth take a stack off again.
 *
 * <p>Extreme curses are the fourth set: now and then one of the cards is one of them (never multiplied), and spilled
 * stacks can land on them too, more rarely than on others. Their cards and the Curses screen only ever hint at what
 * they do, and lifting a stack takes twice the atonement and fragments.
 *
 * <p>Three world sets. Beyond curses only act beyond the wound (the Nether, the End and the Seam); Overworld curses only act
 * in the Overworld; Everywhere curses act in every world and can be offered after any death that costs a curse. Each curse has a maximum and a value per stack (a percentage, hearts, blocks...), both
 * read from the config's {@code penalties} section; the card text is formatted with that value.
 *
 * <p>All text lives in the language files under {@code penalty.extremedimensions.<id>.*}.
 */
public enum Penalty {
	// --- beyond the wound -------------------------------------------------------------------
	ACCELERATED_DRAIN("accelerated_drain", Group.BEYOND, 4, 25),
	DEBT("debt", Group.BEYOND, 4, 15),
	SCAR("scar", Group.BEYOND, 3, 2),
	CRACKED_ARMOR("cracked_armor", Group.BEYOND, 4, 2),
	HEAVY_LEGS("heavy_legs", Group.BEYOND, 3, 8),
	CLUMSY_HANDS("clumsy_hands", Group.BEYOND, 3, 15),
	FRAILTY("frailty", Group.BEYOND, 4, 1),
	FAMINE("famine", Group.BEYOND, 2, 1),
	SHADOWED("shadowed", Group.BEYOND, 2, 15),
	LEAKY_TANK("leaky_tank", Group.BEYOND, 3, 10),
	TOLL("toll", Group.BEYOND, 3, 15),
	BRITTLE_BONES("brittle_bones", Group.BEYOND, 3, 15),
	BURNING_BLOOD("burning_blood", Group.BEYOND, 3, 40),
	THIN_SKIN("thin_skin", Group.BEYOND, 3, 1),
	SLOW_HANDS("slow_hands", Group.BEYOND, 3, 10),
	SHORT_REACH("short_reach", Group.BEYOND, 2, 0.5),
	WEAK_KNEES("weak_knees", Group.BEYOND, 2, 10),
	HEAVY_SOUL("heavy_soul", Group.BEYOND, 2, 10),
	SHORT_CHAIN("short_chain", Group.BEYOND, 3, 15),
	DIM_ANCHORS("dim_anchors", Group.BEYOND, 4, 3),
	MARKED_BY_THE_TRIBE("marked_by_the_tribe", Group.BEYOND, 3, 50),
	EMPTY_POCKETS("empty_pockets", Group.BEYOND, 3, 1),
	WORN_EDGES("worn_edges", Group.BEYOND, 3, 1),
	FERRYMANS_FEE("ferrymans_fee", Group.BEYOND, 3, 2),
	VERTIGO("vertigo", Group.BEYOND, 2, 90),
	CURSED_COIN("cursed_coin", Group.BEYOND, 3, 1),

	// --- the Overworld ------------------------------------------------------------------------
	HOLLOW_HEART("hollow_heart", Group.OVERWORLD, 4, 1),
	LEADEN_STEPS("leaden_steps", Group.OVERWORLD, 3, 5),
	GNAWING("gnawing", Group.OVERWORLD, 2, 1),
	DULL_PICK("dull_pick", Group.OVERWORLD, 3, 10),
	DEEP_DREAD("deep_dread", Group.OVERWORLD, 1, 0),
	ILL_LUCK("ill_luck", Group.OVERWORLD, 3, 1),
	TITHE("tithe", Group.OVERWORLD, 3, 15),
	RESTLESS_SLEEP("restless_sleep", Group.OVERWORLD, 3, 25),
	BITTER_TONGUE("bitter_tongue", Group.OVERWORLD, 3, 15),
	RUSTED_TOOLS("rusted_tools", Group.OVERWORLD, 3, 1),
	ILL_REPUTE("ill_repute", Group.OVERWORLD, 3, 10),
	HUNTED_NIGHTS("hunted_nights", Group.OVERWORLD, 3, 240),
	WEAK_ARM("weak_arm", Group.OVERWORLD, 3, 1),
	SHORT_TETHER("short_tether", Group.OVERWORLD, 2, 1),
	WATCHED("watched", Group.OVERWORLD, 3, 50),
	OLD_BONES("old_bones", Group.OVERWORLD, 3, 15),
	DUSK_BLINDNESS("dusk_blindness", Group.OVERWORLD, 1, 0),

	// --- everywhere -----------------------------------------------------------------------------
	KINDLING("kindling", Group.EVERYWHERE, 3, 20),
	FROSTBITTEN("frostbitten", Group.EVERYWHERE, 3, 25),
	SEALED_POCKETS("sealed_pockets", Group.EVERYWHERE, 3, 3),
	SHRUNKEN_STOMACH("shrunken_stomach", Group.EVERYWHERE, 3, 1),
	FADING_BREWS("fading_brews", Group.EVERYWHERE, 3, 15),
	LINGERING_ILLS("lingering_ills", Group.EVERYWHERE, 3, 20),
	GROUNDED("grounded", Group.EVERYWHERE, 2, 8),
	GLASS_JAW("glass_jaw", Group.EVERYWHERE, 3, 8),
	VOLATILE("volatile", Group.EVERYWHERE, 3, 15),
	SHORT_BREATH("short_breath", Group.EVERYWHERE, 3, 20),
	FRAGILE_FOOTING("fragile_footing", Group.EVERYWHERE, 2, 1),
	FUMBLING("fumbling", Group.EVERYWHERE, 3, 5),
	BRITTLE_GEAR("brittle_gear", Group.EVERYWHERE, 3, 1),
	HUNGRY_WOUNDS("hungry_wounds", Group.EVERYWHERE, 3, 2),
	WITHERING("withering", Group.EVERYWHERE, 2, 1),
	LEAD_POCKETS("lead_pockets", Group.EVERYWHERE, 3, 5),

	// --- extreme: rare, never multiplied, cryptic, twice as hard to lift --------------------------
	DWINDLING("dwindling", Group.EXTREME, 3, 2),
	WITHERED_GRASP("withered_grasp", Group.EXTREME, 3, 1),
	RESTLESS_GAZE("restless_gaze", Group.EXTREME, 3, 15),
	THE_GRUDGE("the_grudge", Group.EXTREME, 1, 0),
	STORMCALLED("stormcalled", Group.EXTREME, 1, 0),
	FERAL_SCENT("feral_scent", Group.EXTREME, 1, 0),
	BROKEN_OATH("broken_oath", Group.EXTREME, 1, 25),
	UNHEALING("unhealing", Group.EXTREME, 1, 0),
	ECHOING_STEPS("echoing_steps", Group.EXTREME, 1, 50);

	/** Where a curse acts: beyond the wound, in the Overworld, in every world; Extreme curses act in every world too. */
	public enum Group {
		BEYOND, OVERWORLD, EVERYWHERE, EXTREME;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Group byId(String id) {
			for (Group g : values()) {
				if (g.id().equals(id)) {
					return g;
				}
			}
			return BEYOND;
		}
	}

	public final String id;
	public final Group group;
	/** Overworld curse: only active in the Overworld. */
	public final boolean overworld;
	/** Acts in every world (the Everywhere and the Extreme set). */
	public final boolean everywhere;
	/** Extreme curse: rare, never multiplied, its effect never spelled out, and twice as hard to lift. */
	public final boolean extreme;
	public final int defaultMax;
	public final double defaultValue;
	/** Current maximum stacks and value per stack (from the config). */
	public int max;
	public double value;

	Penalty(String id, Group group, int max, double value) {
		this.id = id;
		this.group = group;
		this.overworld = group == Group.OVERWORLD;
		this.everywhere = group == Group.EVERYWHERE || group == Group.EXTREME;
		this.extreme = group == Group.EXTREME;
		this.defaultMax = max;
		this.defaultValue = value;
		this.max = max;
		this.value = value;
	}

	public String nameKey() {
		return "penalty.extremedimensions." + id;
	}

	/** First or second effect line on the card (index 1 or 2); formatted with {@link #display()}. */
	public String lineKey(int line) {
		return nameKey() + ".line" + line;
	}

	public String loreKey() {
		return nameKey() + ".lore";
	}

	/** The value per stack as shown on cards: "25", "0.5". */
	public String display() {
		return display(value);
	}

	public static String display(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
	}

	/** The value as a fraction (25 -> 0.25) for percentage curses. */
	public double frac() {
		return value / 100.0;
	}

	public static Penalty byId(String id) {
		for (Penalty p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** Curses of one set that this player can still receive (not at their maximum, and not switched off with max 0). */
	public static List<Penalty> available(PlayerData d, Group group) {
		List<Penalty> pool = new ArrayList<>();
		for (Penalty p : values()) {
			if (p.group == group && p.max > 0 && d.owned(p) < p.max) {
				pool.add(p);
			}
		}
		return pool;
	}

	/** What a death costs from: the set of the world (beyond or the Overworld) and the Everywhere set. */
	public static List<Penalty> pool(PlayerData d, boolean overworld) {
		List<Penalty> pool = available(d, overworld ? Group.OVERWORLD : Group.BEYOND);
		pool.addAll(available(d, Group.EVERYWHERE));
		return pool;
	}

	/** Every curse with room left, in any set (Overworld curses only while they are switched on): where spilled stacks land. */
	public static List<Penalty> withRoom(PlayerData d, boolean overworldOn) {
		List<Penalty> pool = new ArrayList<>();
		for (Penalty p : values()) {
			if ((overworldOn || !p.overworld) && p.max > 0 && d.owned(p) < p.max) {
				pool.add(p);
			}
		}
		return pool;
	}

	/**
	 * Up to {@code count} different curses this player can still receive, from the world's set and the Everywhere set.
	 * With {@code extremeChance}, one of the cards is an Extreme curse instead.
	 */
	public static List<String> rollOffer(PlayerData d, boolean overworld, int count, double extremeChance) {
		List<Penalty> pool = pool(d, overworld);
		java.util.Random rnd = new java.util.Random(ThreadLocalRandom.current().nextLong());
		Collections.shuffle(pool, rnd);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < count && i < pool.size(); i++) {
			out.add(pool.get(i).id);
		}
		List<Penalty> extreme = available(d, Group.EXTREME);
		if (count > 0 && !extreme.isEmpty() && rnd.nextDouble() < extremeChance) {
			String pick = extreme.get(rnd.nextInt(extreme.size())).id;
			if (out.size() >= count) {
				out.set(rnd.nextInt(out.size()), pick);
			} else {
				out.add(pick);
			}
		}
		return out;
	}
}
