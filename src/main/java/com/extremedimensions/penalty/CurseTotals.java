package com.extremedimensions.penalty;

import com.extremedimensions.config.Config;

/**
 * What a curse comes to with all its stacks (3.55.0). A card and the Curses screen read the value of one stack ("25%
 * faster"); with three stacks a player wants the whole of it. Most curses add up stack by stack, but some compound (each
 * stack takes its share of what the stacks before it left: Debt, Leaky Tank, the slowing curses), some stop at a ceiling
 * (Toll, Fumbling, the health curses...) and Vertigo comes more often rather than harder. This works the total out the
 * way the code that applies each curse does, so what the screens say is what the player gets. Shared by the server and
 * the client; the client formats it.
 */
public final class CurseTotals {
	private CurseTotals() {}

	/** What the number on the card counts. */
	public enum Unit {
		PERCENT, HEARTS, BLOCKS, MINUTES, SECONDS, PLAIN;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** How the stacks come together. */
	public enum Rule {
		/** Stack on stack: three stacks of 10% are 30%. */
		ADDS,
		/** Each stack takes its share of what is left: three stacks of 10% are 27.1%. */
		COMPOUNDS,
		/** Vertigo: the time between two spells, shared among the stacks. */
		DIVIDES
	}

	/** One curse's unit, rule and ceiling (in the card's own unit; 0: none). */
	public record Spec(Unit unit, Rule rule, double cap) {}

	/**
	 * How a curse's stacks come together, or null for a curse whose card names no number (Famine, the Extreme and
	 * Abyssal curses...): there is nothing to add up.
	 */
	public static Spec spec(Penalty p) {
		if (p == null || p.extreme) {
			return null;
		}
		return switch (p) {
			// percentages that add up
			case ACCELERATED_DRAIN, BRITTLE_BONES, BURNING_BLOOD, HEAVY_SOUL, MARKED_BY_THE_TRIBE, EMPTY_POCKETS, ILL_REPUTE, WATCHED,
					OLD_BONES, KINDLING, FROSTBITTEN, LINGERING_ILLS, GLASS_JAW, VOLATILE, SHORT_BREATH -> new Spec(Unit.PERCENT, Rule.ADDS, 0);
			// ...and those held under a ceiling (see CurseEffects)
			case TOLL, TITHE, BITTER_TONGUE, FUMBLING -> new Spec(Unit.PERCENT, Rule.ADDS, 90);
			case RESTLESS_SLEEP -> new Spec(Unit.PERCENT, Rule.ADDS, 95);
			// percentages that compound
			case DEBT, LEAKY_TANK, HEAVY_LEGS, CLUMSY_HANDS, SLOW_HANDS, WEAK_KNEES, LEADEN_STEPS, DULL_PICK, FADING_BREWS, GROUNDED,
					LEAD_POCKETS -> new Spec(Unit.PERCENT, Rule.COMPOUNDS, 0);
			// maximum health: Scar and Hollow Heart take at most 8 hearts, Withering 4
			case SCAR, HOLLOW_HEART -> new Spec(Unit.HEARTS, Rule.ADDS, 8);
			case WITHERING -> new Spec(Unit.HEARTS, Rule.ADDS, 4);
			case SHORT_REACH, FRAGILE_FOOTING -> new Spec(Unit.BLOCKS, Rule.ADDS, 0);
			// the chain's step never falls below 25 blocks, nor the Tether's reach below 2
			case SHORT_CHAIN -> new Spec(Unit.BLOCKS, Rule.ADDS, Math.max(0, Config.TETHER_DISTANCE_STEP - 25));
			case SHORT_TETHER -> new Spec(Unit.BLOCKS, Rule.ADDS, Math.max(0, Config.TETHER_BREAK_RANGE - 2));
			case DIM_ANCHORS -> new Spec(Unit.MINUTES, Rule.ADDS, 0);
			case VERTIGO -> new Spec(Unit.SECONDS, Rule.DIVIDES, 0);
			// armour, toughness, attack, luck, wear, levels: plain numbers that add up
			case CRACKED_ARMOR, THIN_SKIN, FRAILTY, CURSED_COIN, ILL_LUCK, WEAK_ARM, WORN_EDGES, RUSTED_TOOLS, FERRYMANS_FEE,
					BRITTLE_GEAR -> new Spec(Unit.PLAIN, Rule.ADDS, 0);
			// at most 27 pockets sealed, and a stomach keeps at least one drumstick
			case SEALED_POCKETS -> new Spec(Unit.PLAIN, Rule.ADDS, 27);
			case SHRUNKEN_STOMACH -> new Spec(Unit.PLAIN, Rule.ADDS, 9);
			default -> null;
		};
	}

	/**
	 * The whole of a curse at so many stacks, in the card's unit ({@code value}: one stack's, as the server's config has
	 * it). 0 for no stacks, and for a curse without a number.
	 */
	public static double total(Penalty p, double value, int stacks) {
		Spec s = spec(p);
		if (s == null || stacks <= 0) {
			return 0;
		}
		double v = Math.max(0, value);
		double raw = switch (s.rule()) {
			// (Debt keeps at least a twentieth of every stack's charge, as PlayerData.debtFactor does)
			case COMPOUNDS -> 100 * (1 - Math.pow(Math.max(p == Penalty.DEBT ? 0.05 : 0, 1 - v / 100.0), stacks));
			// (the time between two spells never falls below ten seconds)
			case DIVIDES -> Math.max(10, v / stacks);
			default -> whole(p, v) * stacks;
		};
		return s.cap() > 0 ? Math.min(s.cap(), raw) : raw;
	}

	/** Whether the ceiling holds the total at so many stacks back (so a screen can say it). */
	public static boolean capped(Penalty p, double value, int stacks) {
		Spec s = spec(p);
		if (s == null || s.cap() <= 0 || stacks <= 0 || s.rule() != Rule.ADDS) {
			return false;
		}
		return Math.max(0, value) * stacks > s.cap() + 1e-9;
	}

	/** Curses whose value the code rounds to a whole number before it counts the stacks. */
	private static double whole(Penalty p, double v) {
		return switch (p) {
			case WORN_EDGES, RUSTED_TOOLS, FERRYMANS_FEE, BRITTLE_GEAR, SEALED_POCKETS, SHRUNKEN_STOMACH -> Math.round(v);
			default -> v;
		};
	}

	/** A number as the cards write one: whole, or with one decimal ("25", "38.6"). */
	public static String format(double v) {
		return Penalty.display(Math.round(v * 10) / 10.0);
	}
}
