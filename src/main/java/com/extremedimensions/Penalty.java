package com.extremedimensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Penalty's die je kiest na een Dimensional Collapse of een dood in Nether/End.
 * Alles stackt: kies je dezelfde penalty nog een keer, dan telt hij dubbel.
 * "timed" penalty's: elke stack duurt Config.PENALTY_SECONDS actieve speeltijd in de Nether of End.
 * Niet-timed penalty's werken een keer.
 */
public enum Penalty {
	ACCELERATED_DRAIN("accelerated_drain", "Accelerated Drain", true, "Charge drains", "75% faster.",
			"They have learned your taste."),
	DEBT("debt", "Debt", true, "Anchor fuel gives", "40% less charge.",
			"The anchor remembers what you owe."),
	SCAR("scar", "Scar", true, "Lose 3 hearts", "of max health.",
			"Where they fed, nothing grows back."),
	CRACKED_ARMOR("cracked_armor", "Cracked Armor", true, "Lose 4 armor", "points.",
			"Iron forgets its shape near them."),
	HEAVY_LEGS("heavy_legs", "Heavy Legs", true, "You move", "15% slower.",
			"Something still holds your ankles."),
	CLUMSY_HANDS("clumsy_hands", "Clumsy Hands", true, "You mine", "35% slower.",
			"Your fingers are not only yours now."),
	FRAILTY("frailty", "Frailty", true, "Constant", "Weakness.",
			"Part of your strength stayed behind."),
	FAMINE("famine", "Famine", true, "Constant", "Hunger.",
			"You hunger with their hunger."),
	SHADOWED("shadowed", "Shadowed", true, "Darkness follows", "you beyond.",
			"One of them followed you home."),
	LEAKY_TANK("leaky_tank", "Leaky Tank", false, "Next expedition:", "anchors lose 50%.",
			"The wound weeps where you tore it."),
	MOURNING("mourning", "Mourning", false, "Weakness, Hunger", "and Fatigue 10m.",
			"You grieve for something you lost there."),
	TOLL("toll", "Toll", false, "Lose half of", "your XP levels.",
			"Memories are the oldest currency.");

	public final String id;
	public final String title;
	public final boolean timed;
	public final String[] lines;
	/** Een regel lore: wat de Starved van je hebben gehouden. */
	public final String lore;

	Penalty(String id, String title, boolean timed, String l1, String l2, String lore) {
		this.id = id;
		this.lore = lore;
		this.title = title;
		this.timed = timed;
		this.lines = new String[] {l1, l2, timed ? (Config.PENALTY_SECONDS / 60) + " min in Nether/End" : "One time"};
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
