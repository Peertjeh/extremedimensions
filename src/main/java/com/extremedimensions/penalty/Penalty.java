package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.extremedimensions.config.Config;

/**
 * Penalties chosen after a Dimensional Collapse or a death beyond the wound (and, when enabled, in the Overworld).
 * Everything stacks: picking the same penalty again doubles it.
 *
 * <p>Timed penalties last {@link Config#PENALTY_SECONDS} of active play per stack: Nether/End penalties count down
 * on expeditions, Overworld penalties in the Overworld. Untimed penalties apply once.
 *
 * <p>All text lives in the language files under {@code penalty.extremedimensions.<id>.*}.
 */
public enum Penalty {
	ACCELERATED_DRAIN("accelerated_drain", true, false),
	DEBT("debt", true, false),
	SCAR("scar", true, false),
	CRACKED_ARMOR("cracked_armor", true, false),
	HEAVY_LEGS("heavy_legs", true, false),
	CLUMSY_HANDS("clumsy_hands", true, false),
	FRAILTY("frailty", true, false),
	FAMINE("famine", true, false),
	SHADOWED("shadowed", true, false),
	LEAKY_TANK("leaky_tank", false, false),
	MOURNING("mourning", false, false),
	TOLL("toll", false, false),

	HOLLOW_HEART("hollow_heart", true, true),
	LEADEN_STEPS("leaden_steps", true, true),
	GNAWING("gnawing", true, true),
	DULL_PICK("dull_pick", true, true),
	DEEP_DREAD("deep_dread", true, true),
	ILL_LUCK("ill_luck", true, true),
	TITHE("tithe", false, true);

	public final String id;
	public final boolean timed;
	/** Overworld penalty: only active, and only counting down, in the Overworld. */
	public final boolean overworld;

	Penalty(String id, boolean timed, boolean overworld) {
		this.id = id;
		this.timed = timed;
		this.overworld = overworld;
	}

	public String nameKey() {
		return "penalty.extremedimensions." + id;
	}

	/** First or second effect line on the card (index 1 or 2). */
	public String lineKey(int line) {
		return nameKey() + ".line" + line;
	}

	public String loreKey() {
		return nameKey() + ".lore";
	}

	/** Duration line; formatted with the minutes per stack for timed penalties. */
	public String durationKey() {
		if (!timed) {
			return "penalty.extremedimensions.duration.once";
		}
		return overworld ? "penalty.extremedimensions.duration.overworld" : "penalty.extremedimensions.duration.beyond";
	}

	public static int minutesPerStack() {
		return Config.PENALTY_SECONDS / 60;
	}

	public static Penalty byId(String id) {
		for (Penalty p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** Draws {@link Config#OFFER_COUNT} unique random penalties from the beyond or the Overworld set. */
	public static List<String> rollOffer(boolean overworld) {
		List<Penalty> pool = new ArrayList<>();
		for (Penalty p : values()) {
			if (p.overworld == overworld) {
				pool.add(p);
			}
		}
		Collections.shuffle(pool);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < Config.OFFER_COUNT && i < pool.size(); i++) {
			out.add(pool.get(i).id);
		}
		return out;
	}
}
