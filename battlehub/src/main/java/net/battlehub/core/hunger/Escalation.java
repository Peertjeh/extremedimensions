package net.battlehub.core.hunger;

/**
 * How hard the Gamemakers' events hit at each level of danger (1.47; {@link HgSchedule#tier}): level 1 in the first
 * eight minutes, 2 up to sixteen, 3 after that. Everything is bigger, faster and hurts more at a higher level.
 */
public final class Escalation {
	private Escalation() {}

	private static int t(int tier) {
		return Math.max(1, Math.min(3, tier));
	}

	private static double pick(int tier, double one, double two, double three) {
		return switch (t(tier)) {
			case 1 -> one;
			case 2 -> two;
			default -> three;
		};
	}

	/** Wildfire: how long the front takes to cross the city (ticks), how thick it is, the damage every half second, how long you burn. */
	public static int fireTicks(int tier) {
		return (int) pick(tier, 40 * 20, 32 * 20, 26 * 20);
	}

	public static double fireWidth(int tier) {
		return pick(tier, 2.5, 3.5, 4.5);
	}

	public static float fireDamage(int tier) {
		return (float) pick(tier, 2, 2.5, 3);
	}

	public static float burnSeconds(int tier) {
		return (float) pick(tier, 5, 7, 9);
	}

	/** Two walls of fire from two sides, closing in on the middle. */
	public static boolean twoFronts(int tier) {
		return t(tier) >= 3;
	}

	/** Poison fog: its radius, how fast it drifts after the nearest tribute (blocks a tick), the poison's level (0 = I). */
	public static double fogRadius(int tier) {
		return pick(tier, 18, 22, 26);
	}

	public static double fogDrift(int tier) {
		return pick(tier, 0.05, 0.075, 0.1);
	}

	public static int fogPoison(int tier) {
		return t(tier) >= 2 ? 1 : 0;
	}

	public static boolean fogWither(int tier) {
		return t(tier) >= 2;
	}

	/** Mutts per tribute, and how long they hunt (ticks). */
	public static int muttsEach(int tier) {
		return t(tier);
	}

	public static int muttTicks(int tier) {
		return (int) pick(tier, 45 * 20, 55 * 20, 60 * 20);
	}

	/** Tracker jackers: swarms, the damage of a sting, how fast a swarm flies (blocks a tick). */
	public static int swarms(int tier) {
		return t(tier);
	}

	public static float stingDamage(int tier) {
		return (float) pick(tier, 2.5, 3.5, 4.5);
	}

	public static double swarmSpeed(int tier) {
		return pick(tier, 0.24, 0.28, 0.32);
	}

	/** Lightning storm: ticks between strikes, the radius it rages in, the share of strikes aimed at a tribute, the damage. */
	public static int strikeEvery(int tier) {
		return (int) pick(tier, 12, 9, 6);
	}

	public static double stormRadius(int tier) {
		return pick(tier, 18, 24, 30);
	}

	public static double aimedShare(int tier) {
		return pick(tier, 0.2, 0.3, 0.4);
	}

	public static float strikeDamage(int tier) {
		return (float) pick(tier, 6, 7, 9);
	}

	/** Acid rain: ticks between burns out in the open, and the damage. */
	public static int acidEvery(int tier) {
		return (int) pick(tier, 30, 25, 20);
	}

	public static float acidDamage(int tier) {
		return (float) pick(tier, 1, 1.5, 2);
	}

	/** Meteors: ticks between impacts, the force of an impact (explosion power), the warning before it (ticks). */
	public static int meteorEvery(int tier) {
		return (int) pick(tier, 22, 15, 10);
	}

	public static float meteorPower(int tier) {
		return (float) pick(tier, 2.5f, 3.0f, 3.5f);
	}

	public static final int METEOR_WARNING = 40;

	/** Earthquake: the radius it shakes, ticks between shocks, the damage of a shock, falling rubble per shock. */
	public static double quakeRadius(int tier) {
		return pick(tier, 35, 45, 60);
	}

	public static int shockEvery(int tier) {
		return (int) pick(tier, 40, 30, 22);
	}

	public static float shockDamage(int tier) {
		return (float) pick(tier, 1.5, 2.5, 3.5);
	}

	public static int rubble(int tier) {
		return t(tier) + 1;
	}
}
