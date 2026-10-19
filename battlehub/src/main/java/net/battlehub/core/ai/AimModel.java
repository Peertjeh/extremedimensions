package net.battlehub.core.ai;

import java.util.Random;

/**
 * A hand on a mouse: where the crosshair actually points, tick by tick, while it follows a target. Not a lock:
 * <ul>
 * <li>the hand reacts to where the target <i>was</i> a moment ago (a human sees, then moves), carried on by how fast
 * it was turning as far as the level can lead;</li>
 * <li>it accelerates and slows like a hand (a smoothed turning speed, capped), so a strafing target is followed
 * with a lag that grows with how fast it strafes, and every change of direction costs a few ticks off target;</li>
 * <li>a big turn (someone behind, a jump over the head) is a flick that overshoots and settles back;</li>
 * <li>a slow, smooth wobble never goes away (larger at lower levels), and a hit taken throws the aim off.</li>
 * </ul>
 * Whether a swing lands is then decided by the world: the crosshair has to be on the target's hitbox.
 */
public final class AimModel {
	/**
	 * How a level aims: reaction lag (ticks), how hard the hand pulls towards the target (share of the error per
	 * tick), how quickly the turning speed follows that pull, the fastest turn (degrees a tick), the wobble (degrees,
	 * the spread it settles to), the overshoot of a flick (share of its size), how far it leads a turning target
	 * (share of the lag) and how well the hand matches the target's turning speed (smooth pursuit, 0..1).
	 */
	public record Params(int lagTicks, double gain, double smoothing, double maxTurn, double wobble, double overshoot, double lead,
			double pursuit) {
		public static Params of(Difficulty d) {
			return switch (d) {
				//                      lag  gain  smooth turn wobble over  lead  pursuit
				case BEGINNER -> new Params(5, 0.50, 0.40, 10, 2.6, 0.30, 0.60, 0.68);
				case CASUAL -> new Params(4, 0.52, 0.50, 15, 2.4, 0.24, 0.60, 0.68);
				case INTERMEDIATE -> new Params(3, 0.55, 0.55, 25, 2.1, 0.18, 0.60, 0.70);
				case ADVANCED -> new Params(3, 0.62, 0.65, 35, 1.8, 0.14, 0.80, 0.72);
				case EXPERT -> new Params(2, 0.60, 0.75, 45, 1.5, 0.10, 0.85, 0.75);
				case MASTER -> new Params(2, 0.70, 0.85, 60, 0.6, 0.07, 0.92, 0.85);
			};
		}
	}

	private static final int HISTORY = 16;
	/** Where the target was (world positions), for aiming from a moving eye: see {@link #stepAt}. */
	private final double[] seenX = new double[HISTORY];
	private final double[] seenY = new double[HISTORY];
	private final double[] seenZ = new double[HISTORY];
	private int posCount;
	private int posHead;
	private float lastWantYaw = Float.NaN;
	private float lastWantPitch;

	private final Params p;
	private final Random rng;
	private final float[] seenYaw = new float[HISTORY];
	private final float[] seenPitch = new float[HISTORY];
	private int count;
	private int head;
	private float yaw;
	private float pitch;
	private double turnYaw;
	private double turnPitch;
	private double wobYaw;
	private double wobPitch;
	private double flick;
	private boolean synced;
	/** How predictable this target has proved (learned), or -1 while unknown (then the level's own lead). */
	private double predictability = -1;

	public AimModel(Params p, long seed) {
		this.p = p;
		this.rng = new Random(seed);
	}

	public Params params() {
		return p;
	}

	/**
	 * Where the head really points now. When something else turned it (walking a route, building), the hand starts
	 * again from there, at rest.
	 */
	public void sync(float realYaw, float realPitch) {
		float shownYaw = wrap((float) (yaw + wobYaw)), shownPitch = (float) (pitch + wobPitch);
		if (!synced || Math.abs(wrap(realYaw - shownYaw)) > 0.05 || Math.abs(realPitch - shownPitch) > 0.05) {
			yaw = (float) (realYaw - wobYaw);
			pitch = (float) (realPitch - wobPitch);
			turnYaw = 0;
			turnPitch = 0;
			synced = true;
		}
	}

	/**
	 * What it learned about this target's movement: predictable (long, steady strafes) is led further, erratic
	 * (quick changes of direction) less, since leading an erratic target means aiming where it will not be.
	 */
	public void adapt(double predictability) {
		this.predictability = predictability < 0 ? -1 : Math.min(1, predictability);
	}

	/** Another target (or one seen again): where the old one was says nothing about the new one. */
	public void retarget() {
		count = 0;
		posCount = 0;
		lastWantYaw = Float.NaN;
	}

	/** Knocked about (a hit taken): the aim is thrown off by about this many degrees. */
	public void disturb(double degrees) {
		wobYaw += rng.nextGaussian() * degrees;
		wobPitch += rng.nextGaussian() * degrees * 0.5;
	}

	/** One tick. In: the direction of the target from the eye now. Out: {yaw, pitch} of the crosshair. */
	public float[] step(float targetYaw, float targetPitch) {
		seenYaw[head] = targetYaw;
		seenPitch[head] = targetPitch;
		head = (head + 1) % HISTORY;
		count = Math.min(HISTORY, count + 1);
		int lag = Math.min(p.lagTicks(), count - 1);
		float lagYaw = back(seenYaw, lag), lagPitch = back(seenPitch, lag);
		// how fast the target was turning round the NPC then, carried on as far as the level leads
		int span = Math.min(2, count - 1 - lag);
		double spinYaw = span <= 0 ? 0 : wrap(lagYaw - back(seenYaw, lag + span)) / span;
		double spinPitch = span <= 0 ? 0 : (lagPitch - back(seenPitch, lag + span)) / span;
		// a steady target is led all the way (the lag fully made up), an erratic one half as far as the level would
		double lead = lead();
		double wantYaw = lagYaw + spinYaw * lag * lead;
		double wantPitch = lagPitch + spinPitch * lag * lead;
		return control(wantYaw, wantPitch, spinYaw, spinPitch);
	}

	/**
	 * One tick, from positions: the eye now and the target point now. The lag is the target's only: where the target
	 * was a moment ago (carried on by its own speed as far as the level leads) is looked at from where the eye is
	 * <i>now</i>, since a player always knows how they move themselves. Out: {yaw, pitch} of the crosshair.
	 */
	public float[] stepAt(double eyeX, double eyeY, double eyeZ, double tx, double ty, double tz) {
		seenX[posHead] = tx;
		seenY[posHead] = ty;
		seenZ[posHead] = tz;
		posHead = (posHead + 1) % HISTORY;
		posCount = Math.min(HISTORY, posCount + 1);
		int lag = Math.min(p.lagTicks(), posCount - 1);
		int span = Math.min(2, posCount - 1 - lag);
		double lx = backPos(seenX, lag), ly = backPos(seenY, lag), lz = backPos(seenZ, lag);
		double vx = 0, vy = 0, vz = 0;
		if (span > 0) {
			vx = (lx - backPos(seenX, lag + span)) / span;
			vy = (ly - backPos(seenY, lag + span)) / span;
			vz = (lz - backPos(seenZ, lag + span)) / span;
		}
		double ahead = lag * lead();
		double ax = lx + vx * ahead, ay = ly + vy * Math.min(ahead, 2), az = lz + vz * ahead;
		double dx = ax - eyeX, dz = az - eyeZ;
		float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float wantPitch = (float) -Math.toDegrees(Math.atan2(ay - eyeY, Math.max(1e-6, Math.hypot(dx, dz))));
		// how fast that direction turns (the target moving and the NPC moving itself): the hand follows it
		double spinYaw = Float.isNaN(lastWantYaw) ? 0 : wrap(wantYaw - lastWantYaw);
		double spinPitch = Float.isNaN(lastWantYaw) ? 0 : wantPitch - lastWantPitch;
		lastWantYaw = wantYaw;
		lastWantPitch = wantPitch;
		return control(wantYaw, wantPitch, spinYaw, spinPitch);
	}

	private double backPos(double[] ring, int ticks) {
		return ring[Math.floorMod(posHead - 1 - ticks, HISTORY)];
	}

	/** A steady target is led all the way (the lag fully made up), an erratic one half as far as the level would. */
	private double lead() {
		return predictability < 0 ? p.lead() : predictability + (1 - predictability) * p.lead() * 0.5;
	}

	/** The hand: pulls towards where it wants to be, turns along with it, overshoots big turns, wobbles. */
	private float[] control(double wantYaw, double wantPitch, double spinYaw, double spinPitch) {
		double errYaw = wrap((float) (wantYaw - yaw));
		if (Math.abs(errYaw) > 25 && Math.abs(turnYaw) < 3) {
			// a big turn from (nearly) still: a flick, which goes too far and comes back
			flick = Math.signum(errYaw) * Math.abs(errYaw) * p.overshoot() * (0.5 + rng.nextDouble());
		}
		// smooth pursuit: the hand turns along with the target, and corrects what it is off by
		double pullYaw = spinYaw * p.pursuit() + (errYaw + flick) * p.gain();
		double pullPitch = spinPitch * p.pursuit() + (wantPitch - pitch) * p.gain() * 0.8;
		turnYaw += (pullYaw - turnYaw) * p.smoothing();
		turnPitch += (pullPitch - turnPitch) * p.smoothing();
		turnYaw = Math.max(-p.maxTurn(), Math.min(p.maxTurn(), turnYaw));
		turnPitch = Math.max(-p.maxTurn(), Math.min(p.maxTurn(), turnPitch));
		flick *= 0.6;
		yaw = wrap((float) (yaw + turnYaw));
		pitch = (float) Math.max(-90, Math.min(90, pitch + turnPitch));

		// the wobble: a slow random drift pulled back to the middle (settles at about p.wobble degrees)
		wobYaw += -wobYaw * 0.2 + rng.nextGaussian() * p.wobble() * 0.6;
		wobPitch += -wobPitch * 0.2 + rng.nextGaussian() * p.wobble() * 0.36;
		return new float[] {wrap((float) (yaw + wobYaw)), (float) Math.max(-90, Math.min(90, pitch + wobPitch))};
	}

	private float back(float[] ring, int ticks) {
		return ring[Math.floorMod(head - 1 - ticks, HISTORY)];
	}

	static float wrap(float degrees) {
		float d = degrees % 360f;
		if (d >= 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}
}
