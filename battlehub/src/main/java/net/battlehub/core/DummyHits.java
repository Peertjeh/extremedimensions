package net.battlehub.core;

/**
 * The training dummy in the lobby: what a hit would do with a diamond sword (the attack cooldown counts, as in
 * a real fight), critical hits, the combo, clicks per second and reach.
 */
public final class DummyHits {
	/** A diamond sword hits for 7. */
	public static final double SWORD = 7.0;
	/** Hits further apart than this (ticks) end the combo. */
	public static final int COMBO_GAP = 30;

	private final ClickRate cps = new ClickRate(20);
	private long lastHit = Long.MIN_VALUE / 2;
	public int hits;
	public int crits;
	public int combo;
	public int bestCombo;
	public double lastDamage;
	public double totalDamage;
	public double bestCps;
	public double lastReach;

	/** Damage of a hit with this base damage at this attack strength (0..1); a critical hit does 50% more. */
	public static double damage(double base, float strength, boolean crit) {
		double s = Math.max(0, Math.min(1, strength));
		double d = base * (0.2 + s * s * 0.8);
		return crit ? d * 1.5 : d;
	}

	/** Vanilla's rule for a critical hit: falling, almost fully charged, not on the ground, a ladder, in water, blind, riding or sprinting. */
	public static boolean crit(float strength, double fallDistance, boolean onGround, boolean climbing, boolean inWater,
			boolean blind, boolean riding, boolean sprinting) {
		return strength > 0.9f && fallDistance > 0 && !onGround && !climbing && !inWater && !blind && !riding && !sprinting;
	}

	/** One hit at {@code tick}; returns the damage. Clicks are counted from arm swings ({@link #click}). */
	public double hit(long tick, float strength, boolean crit, double reach) {
		combo = tick - lastHit <= COMBO_GAP ? combo + 1 : 1;
		bestCombo = Math.max(bestCombo, combo);
		lastHit = tick;
		hits++;
		if (crit) crits++;
		lastDamage = damage(SWORD, strength, crit);
		totalDamage += lastDamage;
		lastReach = reach;
		return lastDamage;
	}

	/** An arm swing (every click, hit or miss). */
	public void click(long tick) {
		cps.click(tick);
		bestCps = Math.max(bestCps, cps.perSecond(tick));
	}

	public double cps(long tick) {
		return cps.perSecond(tick);
	}

	/** True when the combo has run out at {@code tick}. */
	public boolean comboOver(long tick) {
		return tick - lastHit > COMBO_GAP;
	}

	/** Average damage per hit, 0 without hits. */
	public double average() {
		return hits == 0 ? 0 : totalDamage / hits;
	}
}
