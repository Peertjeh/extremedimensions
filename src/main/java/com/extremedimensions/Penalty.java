package com.extremedimensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Penalty's die je kiest na een Dimensional Collapse of een dood in Nether/End.
 * Alles stackt: kies je dezelfde penalty nog een keer, dan telt hij dubbel.
 * "timed" penalty's: elke stack blijft Config.PENALTY_RUNS geslaagde expedities actief.
 * Niet-timed penalty's werken een keer.
 */
public enum Penalty {
	ACCELERATED_DRAIN("accelerated_drain", "Accelerated Drain", true, "Charge drains", "75% faster."),
	DEBT("debt", "Debt", true, "Altar fuel gives", "40% less charge."),
	SCAR("scar", "Scar", true, "Lose 3 hearts", "of max health."),
	CRACKED_ARMOR("cracked_armor", "Cracked Armor", true, "Lose 4 armor", "points."),
	HEAVY_LEGS("heavy_legs", "Heavy Legs", true, "You move", "15% slower."),
	CLUMSY_HANDS("clumsy_hands", "Clumsy Hands", true, "You mine", "35% slower."),
	FRAILTY("frailty", "Frailty", true, "Constant", "Weakness."),
	FAMINE("famine", "Famine", true, "Constant", "Hunger."),
	SHADOWED("shadowed", "Shadowed", true, "Darkness follows", "you beyond."),
	LEAKY_TANK("leaky_tank", "Leaky Tank", false, "Next expedition:", "altars lose 50%."),
	MOURNING("mourning", "Mourning", false, "Weakness, Hunger", "and Fatigue 10m."),
	TOLL("toll", "Toll", false, "Lose half of", "your XP levels.");

	public final String id;
	public final String title;
	public final boolean timed;
	public final String[] lines;

	Penalty(String id, String title, boolean timed, String l1, String l2) {
		this.id = id;
		this.title = title;
		this.timed = timed;
		this.lines = new String[] {l1, l2, timed ? Config.PENALTY_RUNS + " runs, stacks" : "Once, stacks"};
	}

	public static Penalty byId(String id) {
		for (Penalty p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** Trekt OFFER_COUNT unieke willekeurige penalty's. */
	public static List<String> rollOffer() {
		List<Penalty> all = new ArrayList<>(List.of(values()));
		Collections.shuffle(all);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < Config.OFFER_COUNT && i < all.size(); i++) {
			out.add(all.get(i).id);
		}
		return out;
	}
}
