package net.battlehub.core.ai;

/**
 * The weapon a fight is about (from its kit): a bow fight is fought with the bow, a mace fight with the mace and its
 * smash. ANY for mixed kits (Free for All, CTF, The Bridge, ...), where the situation decides.
 */
public enum Weapon {
	ANY, SWORD, AXE, MACE, BOW, CROSSBOW;

	public boolean ranged() {
		return this == BOW || this == CROSSBOW;
	}

	/** From a kit name: "sword", "axe", "mace", "bow" and "crossbow" are fights about that weapon. */
	public static Weapon ofKit(String kit) {
		if (kit == null) return ANY;
		return switch (kit.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "sword" -> SWORD;
			case "axe" -> AXE;
			case "mace" -> MACE;
			case "bow" -> BOW;
			case "crossbow" -> CROSSBOW;
			default -> ANY;
		};
	}
}
