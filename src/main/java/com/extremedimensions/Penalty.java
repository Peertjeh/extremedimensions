package com.extremedimensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Penalty's die je kiest na een Dimensional Collapse of een dood in Nether/End.
 * Alles stackt: kies je dezelfde penalty nog een keer, dan telt hij dubbel.
 * "timed" penalty's: elke stack duurt Config.PENALTY_SECONDS actieve speeltijd in de Nether of End.
 * Niet-timed penalty's werken een keer.
 *
 * Overworld-penalty's (als die aan staan voor de wereld) krijg je bij een dood in de Overworld.
 * Ze werken alleen in de Overworld en tellen ook alleen daar af.
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
			"Memories are the oldest currency."),

	// --- Overworld ---
	HOLLOW_HEART("hollow_heart", "Hollow Heart", true, "Lose 2 hearts", "in the Overworld.",
			"The cord pulls tight around your chest.", true),
	LEADEN_STEPS("leaden_steps", "Leaden Steps", true, "Move 10% slower", "in the Overworld.",
			"The ground remembers your weight.", true),
	GNAWING("gnawing", "Gnawing", true, "Constant Hunger", "in the Overworld.",
			"Something chews at you from below.", true),
	DULL_PICK("dull_pick", "Dull Pick", true, "Mine 25% slower", "in the Overworld.",
			"Stone resists those it has buried.", true),
	NIGHT_TERROR("deep_dread", "Deep Dread", true, "Darkness below", "sea level.",
			"The dark below knows your name.", true),
	ILL_LUCK("ill_luck", "Ill Luck", true, "Worse loot and", "fishing (Unluck).",
			"Fortune turns its face away.", true),
	TITHE("tithe", "Tithe", false, "Lose a third of", "your XP levels.",
			"The Tether takes its share.", true);

	public final String id;
	public final String title;
	public final boolean timed;
	public final String[] lines;
	/** Een regel lore: wat de Starved van je hebben gehouden. */
	public final String lore;
	/** true = Overworld-penalty: werkt en telt alleen af in de Overworld. */
	public final boolean overworld;

	Penalty(String id, String title, boolean timed, String l1, String l2, String lore) {
		this(id, title, timed, l1, l2, lore, false);
	}

	Penalty(String id, String title, boolean timed, String l1, String l2, String lore, boolean overworld) {
		this.id = id;
		this.lore = lore;
		this.title = title;
		this.timed = timed;
		this.overworld = overworld;
		String where = overworld ? " min in Overworld" : " min in Nether/End";
		this.lines = new String[] {l1, l2, timed ? (Config.PENALTY_SECONDS / 60) + where : "One time"};
	}

	public static Penalty byId(String id) {
		for (Penalty p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** Trekt OFFER_COUNT unieke willekeurige penalty's uit de Nether/End-set of de Overworld-set. */
	public static List<String> rollOffer(boolean overworld) {
		List<Penalty> all = new ArrayList<>();
		for (Penalty p : values()) {
			if (p.overworld == overworld) {
				all.add(p);
			}
		}
		Collections.shuffle(all);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < Config.OFFER_COUNT && i < all.size(); i++) {
			out.add(all.get(i).id);
		}
		return out;
	}
}
