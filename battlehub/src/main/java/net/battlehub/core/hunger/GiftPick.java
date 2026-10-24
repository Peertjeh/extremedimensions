package net.battlehub.core.hunger;

/**
 * What a silver parachute brings (without Minecraft): what the tribute needs most. Little health: healing. Hungry:
 * food. No weapon: a weapon. Otherwise a power-up.
 */
public final class GiftPick {
	private GiftPick() {}

	public enum Kind { HEAL, FOOD, WEAPON, POWER }

	/**
	 * @param health    share of health left, 0..1
	 * @param food      food level, 0..20
	 * @param hasWeapon whether it carries a sword, axe, bow, trident or mace
	 */
	public static Kind pick(double health, int food, boolean hasWeapon) {
		if (health < 0.45) return Kind.HEAL;
		if (food <= 10) return Kind.FOOD;
		if (!hasWeapon) return Kind.WEAPON;
		return Kind.POWER;
	}
}
