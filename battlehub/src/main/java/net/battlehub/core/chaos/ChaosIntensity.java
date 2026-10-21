package net.battlehub.core.chaos;

/**
 * How much worse an event is the second, third, ... time round. A Chaos match plans its events (twenty-five); once
 * it has had them all they come again twice as bad, after fifty four times as bad, and so on: level 0 is as written,
 * level 1 twice as bad, level 2 four times. What "twice as bad" means depends on the action:
 * <ul>
 * <li>an event lasts longer (twice, three times, never more than {@link #MAX_SECONDS});</li>
 * <li>an effect gets stronger (one amplifier per level, up to {@link #MAX_AMPLIFIER});</li>
 * <li>an attribute change is that many times as big, within what a body can take (see {@link #attribute});</li>
 * <li>items and potions come in that many (up to a stack);</li>
 * <li>what the match does itself (bombs, rain, crumbling, pushes) happens that much more, see the methods below.</li>
 * </ul>
 * Pure numbers, so the self-test and the unit tests can check them.
 */
public final class ChaosIntensity {
	private ChaosIntensity() {}

	public static final int MAX_SECONDS = 45;
	public static final int MAX_AMPLIFIER = 5;

	/** 1, 2, 4, 8, ... (no more than 64). */
	public static int factor(int level) {
		return 1 << Math.max(0, Math.min(6, level));
	}

	/** How long the event lasts at this level (instant events stay instant). */
	public static int seconds(int base, int level) {
		if (base <= 0) return base;
		return Math.min(Math.max(base, MAX_SECONDS), base * Math.min(3, 1 + Math.max(0, level)));
	}

	public static int amplifier(int base, int level) {
		return Math.min(Math.max(base, MAX_AMPLIFIER), base + Math.max(0, level));
	}

	/** How many items (or potions): that many times as many, never more than {@code max}. */
	public static int count(int base, int level, int max) {
		return (int) Math.min(Math.max(base, max), (long) base * factor(level));
	}

	/** An enchantment one level higher per level, up to five. */
	public static int enchantment(int base, int level) {
		return Math.min(Math.max(base, 5), base + Math.max(0, level));
	}

	/**
	 * An attribute change that many times as big, kept to what a body can take: a size no smaller than a tenth and
	 * no bigger than three times, gravity never upside down, health never gone, speed and jumps never negative.
	 *
	 * @param op "add", "base" or "total" (the catalogue's words)
	 */
	public static double attribute(String attribute, String op, double amount, int level) {
		double v = amount * factor(level);
		if (op.equals("add")) {
			return switch (attribute) {
				case "minecraft:entity_interaction_range" -> clamp(v, -2.5, 8);
				case "minecraft:block_interaction_range" -> clamp(v, -3.5, 12);
				case "minecraft:step_height" -> clamp(v, -0.6, 3);
				case "minecraft:max_health" -> clamp(v, -10, 60);
				case "minecraft:attack_knockback" -> clamp(v, -2, 10);
				case "minecraft:safe_fall_distance" -> clamp(v, -3, 80);
				default -> clamp(v, -40, 40);
			};
		}
		// a multiplier: -1 takes it all away, so stay above that where nothing must be gone
		return switch (attribute) {
			case "minecraft:scale" -> clamp(v, -0.9, 2);
			case "minecraft:gravity" -> clamp(v, -0.95, 3);
			case "minecraft:max_health" -> clamp(v, -0.8, 4);
			case "minecraft:movement_speed", "minecraft:jump_strength" -> clamp(v, -1, 2.5);
			default -> clamp(v, -1, 4);
		};
	}

	/** A number of things (bombs, cobwebs, chickens, projectiles per volley) that many times as many, up to {@code max}. */
	public static int times(int base, int level, int max) {
		return count(base, level, max);
	}

	/** A push (launch, magnet, shockwave) half as strong again per level, up to {@code max}. */
	public static double push(double base, int level, double max) {
		return Math.min(Math.max(base, max), base * (1 + 0.5 * Math.max(0, level)));
	}

	/** Every so many ticks: half as long between them per level, but never more often than every {@code min} ticks. */
	public static int every(int base, int level, int min) {
		return Math.max(Math.min(base, min), base / factor(level));
	}

	/** A chance per block (erosion): as if it were tried that many times. */
	public static double chance(double base, int level) {
		return 1 - Math.pow(1 - base, factor(level));
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
