package net.battlehub.core.hunger;

import net.battlehub.core.BotMath;
import net.battlehub.core.Orbit;

/**
 * The night's tribute to the fallen (1.47), without Minecraft: when what happens, and where the camera is. For every
 * viewer the stage hangs over their own spot: the Capitol's emblem high in the sky, under it the fallen one after the
 * other (a giant head, the name and district), each for a few seconds on the beat of the anthem.
 *
 * <pre>
 *   0          the sky goes dark, the anthem starts, the camera rises from the eyes
 *   EMBLEM     the emblem appears
 *   INTRO      the first of the fallen
 *   + each     the next one ...
 *   end-OUTRO  the emblem fades, the camera comes down again
 * </pre>
 */
public final class NightPlan {
	/** When the emblem appears, the first of the fallen, how long the end takes (ticks). */
	public static final int EMBLEM = 20, INTRO = 100, OUTRO = 80;
	/** How long one of the fallen is shown, at most and at least (ticks). */
	public static final int LONGEST = 80, SHORTEST = 40;
	/** The length of the anthem (44 beats at 60 a minute). */
	public static final int ANTHEM = 44 * 20;
	/** With nobody fallen the night is short: the emblem and a word. */
	public static final int EMPTY = 200;
	/** The stage, from the viewer's feet: the middle of the faces, and the camera's spot. */
	public static final double SKY_UP = 24, SKY_AHEAD = 22, CAMERA_UP = 5, CAMERA_BACK = 6;

	public final int fallen;
	/** Ticks each of the fallen is shown. */
	public final int each;
	/** The whole night, in ticks. */
	public final int length;

	public NightPlan(int fallen) {
		this.fallen = Math.max(0, fallen);
		if (this.fallen == 0) {
			each = 0;
			length = EMPTY;
		} else {
			int room = ANTHEM - INTRO - OUTRO;
			each = Math.max(SHORTEST, Math.min(LONGEST, room / this.fallen));
			length = INTRO + this.fallen * each + OUTRO;
		}
	}

	/** When the {@code i}-th of the fallen appears. */
	public int faceAt(int i) {
		return INTRO + i * each;
	}

	/** Which of the fallen is shown at this tick of the night, or -1. */
	public int faceShown(int tick) {
		if (fallen == 0 || tick < INTRO) return -1;
		int i = (tick - INTRO) / each;
		return i < fallen ? i : -1;
	}

	/** When the end begins (the emblem fades, the camera comes down). */
	public int outroAt() {
		return length - OUTRO;
	}

	/**
	 * The camera at tick {@code tick}, for a viewer whose eyes are at (x, y, z) facing {@code yawDeg}: from the eyes up
	 * to its spot below and behind the stage in the first second and a half, a slow sideways drift while the fallen are
	 * shown, always looking at the stage, and back down to the eyes in the last second and a half.
	 */
	public Orbit.Pose camera(double x, double y, double z, float yawDeg, int tick) {
		double[] sky = sky(x, y, z, yawDeg);
		double yaw = Math.toRadians(yawDeg);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double sx = Math.cos(yaw), sz = Math.sin(yaw);
		double drift = (Orbit.ease(tick / (double) length) - 0.5) * 8;
		double cx = x - fx * CAMERA_BACK + sx * drift, cy = y + CAMERA_UP + 2 * Orbit.ease(tick / (double) length), cz = z - fz * CAMERA_BACK + sz * drift;
		double in = Orbit.ease(tick / 30.0), out = Orbit.ease((length - tick) / 30.0);
		double k = Math.min(in, out);
		double px = x + (cx - x) * k, py = y + (cy - y) * k, pz = z + (cz - z) * k;
		float lookYaw = BotMath.yaw(sky[0] - px, sky[2] - pz);
		float lookPitch = BotMath.pitch(Math.hypot(sky[0] - px, sky[2] - pz), sky[1] - py);
		// at the very start and end the camera looks where the viewer looks
		float yawNow = (float) (yawDeg + wrap(lookYaw - yawDeg) * k);
		float pitchNow = (float) (lookPitch * k);
		return new Orbit.Pose(px, py, pz, yawNow, pitchNow);
	}

	/** The middle of the stage (the faces) for a viewer at (x, y, z) facing {@code yawDeg}: high up, ahead of them. */
	public static double[] sky(double x, double y, double z, float yawDeg) {
		double yaw = Math.toRadians(yawDeg);
		return new double[] {x - Math.sin(yaw) * SKY_AHEAD, y + SKY_UP, z + Math.cos(yaw) * SKY_AHEAD};
	}

	private static double wrap(double deg) {
		double d = deg % 360;
		if (d > 180) d -= 360;
		if (d < -180) d += 360;
		return d;
	}
}
