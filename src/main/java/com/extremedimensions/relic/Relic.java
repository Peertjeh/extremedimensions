package com.extremedimensions.relic;

import com.extremedimensions.config.Config;
import com.extremedimensions.penalty.Penalty;

/**
 * The relics: one at a time is bound to the Tether, and each brings a gift and a price. Their numbers live in the
 * config ({@code relic_*}); the texts in the language files under {@code item.extremedimensions.<id>} with
 * {@code .gift} and {@code .price} formatted with {@link #giftArgs()} and {@link #priceArgs()}.
 */
public enum Relic {
	/** Less drain in the Nether, more in the End. */
	EMBER_HEART("ember_heart"),
	/** Less drain in the End, more in the Nether. */
	PALE_SHARD("pale_shard"),
	/** Longer reach, a heart less. */
	HUNGERING_HAND("hungering_hand"),
	/** Long falls without harm, less armour. */
	FEATHERBONE("featherbone"),
	/** More fragments from wild kills, less atonement from kills. */
	KEEPERS_EYE("keepers_eye"),
	/** More experience, levels taken on every safe return. */
	TITHE_BELL("tithe_bell"),
	/** Lurkers cannot take items or armour, but come more often. */
	LURKERS_TOOTH("lurkers_tooth"),
	/** More time in the Seam, monsters notice you from farther at night. */
	SEAM_LANTERN("seam_lantern"),
	/** Fall again sooner after an ally lifts you, bleed out sooner while fallen. */
	KINDRED_KNOT("kindred_knot");

	public final String id;

	Relic(String id) {
		this.id = id;
	}

	public String nameKey() {
		return "item.extremedimensions." + id;
	}

	public String giftKey() {
		return nameKey() + ".gift";
	}

	public String priceKey() {
		return nameKey() + ".price";
	}

	private static String pct(double factor) {
		return Penalty.display(Math.round(Math.abs(factor - 1) * 100));
	}

	/** The numbers in the gift's text, from the config. */
	public Object[] giftArgs() {
		return switch (this) {
			case EMBER_HEART -> new Object[] {pct(Config.RELIC_EMBER_HEART_NETHER)};
			case PALE_SHARD -> new Object[] {pct(Config.RELIC_PALE_SHARD_END)};
			case HUNGERING_HAND -> new Object[] {Penalty.display(Config.RELIC_HUNGERING_HAND_REACH)};
			case FEATHERBONE -> new Object[] {Penalty.display(Config.RELIC_FEATHERBONE_FALL)};
			case KEEPERS_EYE -> new Object[] {Penalty.display(Config.RELIC_KEEPERS_EYE_FRAGMENTS)};
			case TITHE_BELL -> new Object[] {Penalty.display(Math.round(Config.RELIC_TITHE_BELL_XP * 100))};
			case LURKERS_TOOTH -> new Object[] {};
			case SEAM_LANTERN -> new Object[] {Config.RELIC_SEAM_LANTERN_SECONDS};
			case KINDRED_KNOT -> new Object[] {pct(Config.RELIC_KINDRED_KNOT_COOLDOWN)};
		};
	}

	/** The numbers in the price's text, from the config. */
	public Object[] priceArgs() {
		return switch (this) {
			case EMBER_HEART -> new Object[] {pct(Config.RELIC_EMBER_HEART_END)};
			case PALE_SHARD -> new Object[] {pct(Config.RELIC_PALE_SHARD_NETHER)};
			case HUNGERING_HAND -> new Object[] {Penalty.display(Config.RELIC_HUNGERING_HAND_HEALTH / 2)};
			case FEATHERBONE -> new Object[] {Penalty.display(Config.RELIC_FEATHERBONE_ARMOR)};
			case KEEPERS_EYE -> new Object[] {pct(Config.RELIC_KEEPERS_EYE_ATONEMENT)};
			case TITHE_BELL -> new Object[] {Config.RELIC_TITHE_BELL_LEVELS};
			case LURKERS_TOOTH -> new Object[] {Penalty.display(Config.RELIC_LURKERS_TOOTH_OFTEN)};
			case SEAM_LANTERN -> new Object[] {Penalty.display(Math.round(Config.RELIC_SEAM_LANTERN_NOTICE * 100))};
			case KINDRED_KNOT -> new Object[] {pct(Config.RELIC_KINDRED_KNOT_BLEED)};
		};
	}

	public static Relic byId(String id) {
		if (id == null) {
			return null;
		}
		for (Relic r : values()) {
			if (r.id.equals(id)) {
				return r;
			}
		}
		return null;
	}
}
