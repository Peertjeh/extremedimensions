package com.extremedimensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Penalty's die je kunt kiezen na een Dimensional Collapse.
 * "timed" penalty's blijven actief tot je Config.PENALTY_RUNS expedities veilig afrondt.
 * Niet-timed penalty's werken een keer en zijn dan klaar.
 */
public enum Penalty {
	ACCELERATED_DRAIN("accelerated_drain", "Accelerated Drain", true,
			"Your charge drains 1.5x",
			"faster in Nether and End.",
			"Lasts 2 successful runs."),
	DEBT("debt", "Debt", true,
			"Items put in an altar",
			"give 25% less charge.",
			"Lasts 2 successful runs."),
	SCAR("scar", "Scar", true,
			"You lose 2 hearts",
			"of max health.",
			"Lasts 2 successful runs."),
	LEAKY_TANK("leaky_tank", "Leaky Tank", false,
			"Your next expedition",
			"loses 30% of the charge",
			"in its altars."),
	MOURNING("mourning", "Mourning", false,
			"Weakness and Hunger",
			"for 5 minutes.",
			"");

	public final String id;
	public final String title;
	public final boolean timed;
	public final String[] lines;

	Penalty(String id, String title, boolean timed, String l1, String l2, String l3) {
		this.id = id;
		this.title = title;
		this.timed = timed;
		this.lines = new String[] {l1, l2, l3};
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
