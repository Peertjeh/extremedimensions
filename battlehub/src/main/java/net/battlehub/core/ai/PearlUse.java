package net.battlehub.core.ai;

/**
 * When a bot throws an ender pearl (1.49), as people with pearls in their kit do: to close in on someone it sees far
 * off (a pearl a few blocks short of them, then the fight), and to get away from someone on top of it when it is low
 * and could eat. A beginner never throws one; the higher the level the sooner it thinks of it.
 */
public final class PearlUse {
	private PearlUse() {}

	/** How far off someone has to be to pearl after them (closer it runs), and how far a pearl is worth it. */
	public static final double CHASE_MIN = 13, CHASE_MAX = 40;
	/** How far short of the target a chasing pearl comes down (not on their head: into their swing). */
	public static final double SHORT = 3;
	/** How far a pearl to get away goes. */
	public static final double AWAY = 14;
	/** Ticks between two pearls of the same bot (the game's cooldown is 20). */
	public static final int SPACING = 60;

	/** The chance a bot of this level thinks of a pearl at a moment it would help (checked once a second). */
	public static double chance(Difficulty d) {
		return d == Difficulty.BEGINNER ? 0 : Math.max(0, Math.min(0.9, 0.9 - 2.5 * d.mistakeRate));
	}

	/** Pearl after the target: seen, far but not too far, on the ground, not low itself. */
	public static boolean chase(double dist, boolean seen, boolean onGround, double healthShare, Difficulty d, double roll) {
		return seen && onGround && dist >= CHASE_MIN && dist <= CHASE_MAX && healthShare > 0.35 && roll < chance(d);
	}

	/** Pearl away to eat: low, with something to eat, the target right on it, on the ground. */
	public static boolean escape(double dist, double healthShare, boolean canHeal, boolean onGround, Difficulty d, double roll) {
		return onGround && canHeal && healthShare < 0.3 && dist < 4 && roll < chance(d);
	}

	/** Where a chasing pearl should come down: {@link #SHORT} blocks short of the target, on the line from the bot. */
	public static V3 chaseTo(V3 me, V3 target) {
		V3 d = target.sub(me).flat();
		double len = d.flatLength();
		if (len < 1e-6) return target;
		return target.sub(d.scale(Math.min(SHORT, len) / len));
	}

	/**
	 * Where a pearl to get away should come down: {@link #AWAY} blocks from the bot, away from the target, or as near to
	 * away as {@code ground} allows (it says whether a spot has ground to land on). Null when no direction has.
	 */
	public static V3 escapeTo(V3 me, V3 target, java.util.function.Predicate<V3> ground) {
		V3 away = me.sub(target).flat().unit();
		if (away.flatLength() < 1e-6) away = new V3(1, 0, 0);
		// straight away first, then ever further round to either side
		for (int step = 0; step <= 4; step++) {
			for (int side : step == 0 ? new int[] {1} : new int[] {1, -1}) {
				double a = Math.toRadians(side * step * 30);
				V3 dir = new V3(away.x() * Math.cos(a) - away.z() * Math.sin(a), 0, away.x() * Math.sin(a) + away.z() * Math.cos(a));
				V3 at = me.add(dir.scale(AWAY));
				if (ground.test(at)) return at;
			}
		}
		return null;
	}
}
