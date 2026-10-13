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
 * <p>Two separate sets. Beyond curses only act beyond the wound (the Nether, the End and the Seam); Overworld curses
 * only act in the Overworld. Each curse has a maximum and a value per stack (a percentage, hearts, blocks...), both
 * read from the config's {@code penalties} section; the card text is formatted with that value.
 *
 * <p>All text lives in the language files under {@code penalty.extremedimensions.<id>.*}.
 */
public enum Penalty {
	// --- beyond the wound -------------------------------------------------------------------
	ACCELERATED_DRAIN("accelerated_drain", false, 4, 25),
	DEBT("debt", false, 4, 15),
	SCAR("scar", false, 3, 2),
	CRACKED_ARMOR("cracked_armor", false, 4, 2),
	HEAVY_LEGS("heavy_legs", false, 3, 8),
	CLUMSY_HANDS("clumsy_hands", false, 3, 15),
	FRAILTY("frailty", false, 4, 1),
	FAMINE("famine", false, 2, 1),
	SHADOWED("shadowed", false, 2, 15),
	LEAKY_TANK("leaky_tank", false, 3, 10),
	TOLL("toll", false, 3, 15),
	BRITTLE_BONES("brittle_bones", false, 3, 15),
	BURNING_BLOOD("burning_blood", false, 3, 40),
	THIN_SKIN("thin_skin", false, 3, 1),
	SLOW_HANDS("slow_hands", false, 3, 10),
	SHORT_REACH("short_reach", false, 2, 0.5),
	WEAK_KNEES("weak_knees", false, 2, 10),
	HEAVY_SOUL("heavy_soul", false, 2, 10),
	SHORT_CHAIN("short_chain", false, 3, 15),
	DIM_ANCHORS("dim_anchors", false, 4, 3),
	MARKED_BY_THE_TRIBE("marked_by_the_tribe", false, 3, 50),
	EMPTY_POCKETS("empty_pockets", false, 3, 1),
	WORN_EDGES("worn_edges", false, 3, 1),
	FERRYMANS_FEE("ferrymans_fee", false, 3, 2),
	VERTIGO("vertigo", false, 2, 90),
	CURSED_COIN("cursed_coin", false, 3, 1),

	// --- the Overworld ------------------------------------------------------------------------
	HOLLOW_HEART("hollow_heart", true, 4, 1),
	LEADEN_STEPS("leaden_steps", true, 3, 5),
	GNAWING("gnawing", true, 2, 1),
	DULL_PICK("dull_pick", true, 3, 10),
	DEEP_DREAD("deep_dread", true, 1, 0),
	ILL_LUCK("ill_luck", true, 3, 1),
	TITHE("tithe", true, 3, 15),
	RESTLESS_SLEEP("restless_sleep", true, 3, 25),
	BITTER_TONGUE("bitter_tongue", true, 3, 15),
	RUSTED_TOOLS("rusted_tools", true, 3, 1),
	ILL_REPUTE("ill_repute", true, 3, 10),
	HUNTED_NIGHTS("hunted_nights", true, 3, 240),
	WEAK_ARM("weak_arm", true, 3, 1),
	SHORT_TETHER("short_tether", true, 2, 1),
	WATCHED("watched", true, 3, 50),
	OLD_BONES("old_bones", true, 3, 15),
	DUSK_BLINDNESS("dusk_blindness", true, 1, 0);

	public final String id;
	/** Overworld curse: only active in the Overworld. Otherwise only beyond the wound. */
	public final boolean overworld;
	public final int defaultMax;
	public final double defaultValue;
	/** Current maximum stacks and value per stack (from the config). */
	public int max;
	public double value;

	Penalty(String id, boolean overworld, int max, double value) {
		this.id = id;
		this.overworld = overworld;
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
	public static List<Penalty> available(PlayerData d, boolean overworld) {
		List<Penalty> pool = new ArrayList<>();
		for (Penalty p : values()) {
			if (p.overworld == overworld && p.max > 0 && d.stacks(p) < p.max) {
				pool.add(p);
			}
		}
		return pool;
	}

	/** Up to {@code count} different curses from the set that this player can still receive. */
	public static List<String> rollOffer(PlayerData d, boolean overworld, int count) {
		List<Penalty> pool = available(d, overworld);
		Collections.shuffle(pool, new java.util.Random(ThreadLocalRandom.current().nextLong()));
		List<String> out = new ArrayList<>();
		for (int i = 0; i < count && i < pool.size(); i++) {
			out.add(pool.get(i).id);
		}
		return out;
	}
}
