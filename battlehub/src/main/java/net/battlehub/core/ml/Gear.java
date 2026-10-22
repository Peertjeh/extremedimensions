package net.battlehub.core.ml;

import java.util.Map;

/**
 * Melee weapons as spec v2's observation counts them, by item id: attack damage (with the player's 1) and attack
 * speed, as in the game and in the simulation's kits (ml/pvp_sim2/kits.py). Anything else is the fist (1 damage, speed
 * 4). Strength adds 3 a level and weakness takes 4 off every hit; sharpness adds 0.5 a level plus 0.5.
 */
public final class Gear {
	private Gear() {}

	/** A melee weapon: damage of a full hit, attack speed (20 / speed ticks to charge), axe (disables shields), mace. */
	public record Melee(double damage, double speed, boolean axe, boolean mace) {
		public double cooldown() {
			return 20.0 / speed;
		}
	}

	public static final Melee FIST = new Melee(1, 4, false, false);

	private static final Map<String, Melee> WEAPONS = Map.ofEntries(
			Map.entry("minecraft:wooden_sword", new Melee(4, 1.6, false, false)),
			Map.entry("minecraft:golden_sword", new Melee(4, 1.6, false, false)),
			Map.entry("minecraft:stone_sword", new Melee(5, 1.6, false, false)),
			Map.entry("minecraft:copper_sword", new Melee(5, 1.6, false, false)),
			Map.entry("minecraft:iron_sword", new Melee(6, 1.6, false, false)),
			Map.entry("minecraft:diamond_sword", new Melee(7, 1.6, false, false)),
			Map.entry("minecraft:netherite_sword", new Melee(8, 1.6, false, false)),
			Map.entry("minecraft:wooden_axe", new Melee(7, 0.8, true, false)),
			Map.entry("minecraft:golden_axe", new Melee(7, 1.0, true, false)),
			Map.entry("minecraft:stone_axe", new Melee(9, 0.8, true, false)),
			Map.entry("minecraft:copper_axe", new Melee(9, 0.8, true, false)),
			Map.entry("minecraft:iron_axe", new Melee(9, 0.9, true, false)),
			Map.entry("minecraft:diamond_axe", new Melee(9, 1.0, true, false)),
			Map.entry("minecraft:netherite_axe", new Melee(10, 1.0, true, false)),
			Map.entry("minecraft:mace", new Melee(6, 0.6, false, true)),
			Map.entry("minecraft:trident", new Melee(9, 1.1, false, false)));

	/** The weapon of an item id, or the fist. */
	public static Melee melee(String itemId) {
		return WEAPONS.getOrDefault(itemId, FIST);
	}

	public static boolean isWeapon(String itemId) {
		return WEAPONS.containsKey(itemId);
	}

	/** Which weapon fights better: more damage over time (for the bot's "weapon" hand). */
	public static double dps(Melee m) {
		return m.damage() * m.speed();
	}

	/** Damage of a full hit with this weapon: its damage, strength and weakness levels, and the sharpness level. */
	public static double fullDamage(Melee m, int strength, int weakness) {
		return Math.max(0, m.damage() + 3.0 * strength - 4.0 * weakness);
	}

	public static double sharpnessBonus(int level) {
		return level > 0 ? 0.5 * level + 0.5 : 0;
	}

	/**
	 * Health back a second: natural regeneration as the game's food data gives it (fast with saturation and full food,
	 * slow from 18), plus regeneration II's one every 25 ticks (other levels: 50 ticks halved per level).
	 */
	public static double regenRate(double health, double maxHealth, int food, double saturation, int regenerationLevel) {
		double rate = 0;
		if (health < maxHealth) {
			if (saturation > 0 && food >= 20) rate = Math.min(saturation, 6) / 6.0 * 2.0;
			else if (food >= 18) rate = 0.25;
		}
		if (regenerationLevel > 0) rate += 20.0 / Math.max(1, 50 >> (regenerationLevel - 1));
		return rate;
	}
}
