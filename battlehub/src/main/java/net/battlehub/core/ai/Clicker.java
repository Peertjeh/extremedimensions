package net.battlehub.core.ai;

import java.util.Random;

/**
 * When a human presses attack. Not on the exact tick the charge is full and the target steps into reach:
 * <ul>
 * <li>each click waits for its own charge: around the level's {@link Difficulty#readyToHit}, more or less
 * (beginners spam weak hits, experts wait for a full one, but nobody is exact);</li>
 * <li>the finger is a little late: once everything is right, it presses with a chance per tick;</li>
 * <li>a swing can start a little too far away (whiffing at the edge of reach), more often at lower levels.</li>
 * </ul>
 * Whether the click lands is not decided here: the crosshair must be on the target ({@link AimModel}).
 */
public final class Clicker {
	private final Difficulty d;
	private final Random rng;
	private double threshold;
	private int clicks;

	public Clicker(Difficulty d, Random rng) {
		this.d = d;
		this.rng = rng;
		next();
	}

	/** Would the player click now, with this charge, at this distance from the target (and its reach)? */
	public boolean wants(double charge, double dist, double reach, boolean visible) {
		if (!visible || charge < threshold || dist > reach + d.earlyReach) return false;
		return rng.nextDouble() < d.clickRate;
	}

	/** A click happened: the next one waits for its own moment. */
	public void clicked() {
		clicks++;
		next();
	}

	/** The charge the next click waits for. */
	public double threshold() {
		return threshold;
	}

	public int clicks() {
		return clicks;
	}

	private void next() {
		threshold = Math.max(0.3, Math.min(1.0, d.readyToHit + rng.nextGaussian() * d.readyJitter));
	}
}
