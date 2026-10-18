package net.battlehub.core.ai;

import java.util.Locale;

/** Ready-made fighting styles. They differ in the numbers the decisions use, not only in name. */
public enum Archetype {
	/** Straight at you, keeps swinging, takes risks. */
	RUSHER(new Personality(0.95, 0.80, 0.10, 2.2, 0.35, 0.25, 0.15, 0.40)),
	/** Holds the right distance and trades hits on its own terms. */
	DUELIST(new Personality(0.55, 0.45, 0.60, 3.3, 0.55, 0.20, 0.45, 0.50)),
	/** Waits just outside reach and punishes you when you come in. */
	COUNTER(new Personality(0.30, 0.40, 0.90, 3.6, 0.60, 0.15, 0.70, 0.45)),
	/** Hard to pin down: strafes, backs off, comes back when you are open. */
	EVASIVE(new Personality(0.35, 0.30, 0.65, 4.2, 0.50, 0.55, 0.80, 0.50)),
	/** Waits for a weak moment (low health, a missed swing) and then goes all in. */
	OPPORTUNIST(new Personality(0.50, 0.60, 0.70, 3.4, 0.75, 0.30, 0.40, 0.55)),
	/** A bit of everything; a good sparring partner. */
	BALANCED(new Personality(0.60, 0.50, 0.50, 3.0, 0.50, 0.25, 0.45, 0.60)),
	/** Changes its mind a lot: hard to read, sometimes wild. */
	IMPROVISER(new Personality(0.65, 0.65, 0.35, 3.0, 0.80, 0.90, 0.35, 0.45)),
	/** Likes distance and the bow; avoids close fights. */
	ARCHER(new Personality(0.30, 0.35, 0.70, 5.5, 0.50, 0.25, 0.70, 0.50));

	public final Personality base;

	Archetype(Personality base) {
		this.base = base;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Archetype parse(String s, Archetype fallback) {
		if (s == null) return fallback;
		for (Archetype a : values()) {
			if (a.id().equalsIgnoreCase(s.trim())) return a;
		}
		return fallback;
	}

	/** One archetype for a seed (a bot's name), the same every time. */
	public static Archetype forSeed(long seed, java.util.List<Archetype> pool) {
		if (pool == null || pool.isEmpty()) return BALANCED;
		return pool.get(Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 17, pool.size()));
	}
}
