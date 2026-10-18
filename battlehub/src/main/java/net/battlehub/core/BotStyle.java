package net.battlehub.core;

/** How bots play a match; each match type picks one. */
public enum BotStyle {
	/** Close in and hit (duels, Free for All, Sumo, KOTH, CTF, Bed Wars, ...). */
	MELEE,
	/** Keep distance and shoot (Bow and Crossbow PvP). */
	RANGED,
	/** Keep running on the floor that is still there (TNT Run). */
	RUNNER,
	/** Jump from platform to platform (Parkour Race). */
	PARKOUR,
	/** Shoot the floor under the others and stay on your own (Bow Spleef). */
	BOW_SPLEEF,
	/** Hide as a block, or hunt for blocks (Prop Hunt). */
	PROP_HUNT,
	/** Dig the floor away under the others, keep your own (Spleef). */
	SPLEEF
}
