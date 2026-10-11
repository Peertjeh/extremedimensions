package com.extremedimensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Penalty's die je kunt kiezen na een Dimensional Collapse.
 * "timed" penalty's blijven actief tot je Config.PENALTY_RUNS expedities veilig afrondt.
 * Niet-timed penalty's werken meteen en zijn dan klaar.
 */
public enum Penalty {
	ACCELERATED_DRAIN("accelerated_drain", "Versnelde Drain", true,
			"Je charge loopt 1,5x sneller",
			"leeg in Nether en End.",
			"Duurt 2 geslaagde runs."),
	DEBT("debt", "Schuld", true,
			"Items in het altaar leveren",
			"25% minder charge op.",
			"Duurt 2 geslaagde runs."),
	SCAR("scar", "Litteken", true,
			"Je verliest 2 hartjes",
			"max health.",
			"Duurt 2 geslaagde runs."),
	LEAKY_TANK("leaky_tank", "Lekkende Tank", false,
			"Je verliest meteen 30%",
			"van je resterende charge",
			"(Nether en End)."),
	MOURNING("mourning", "Rouw", false,
			"Weakness en Hunger",
			"gedurende 5 minuten.",
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
