package net.battlehub.core.hunger;

/**
 * The night's tribute to the fallen (1.47, high over the plaza since 1.49), without Minecraft: when what happens, and
 * where the stage hangs. The game goes on meanwhile; the stage hangs high in the middle of the city, where everyone can
 * see it: the Capitol's emblem, under it the fallen one after the other (a giant head, a pillar of light down to the
 * plaza, the name and district), each for a few seconds on the beat of the anthem.
 *
 * <pre>
 *   0          the sky goes dark, the anthem starts
 *   EMBLEM     the emblem appears
 *   INTRO      the first of the fallen
 *   + each     the next one ...
 *   end-OUTRO  the emblem fades
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
	/**
	 * The stage over the plaza: how high the faces hang, how big a head grows (about half a block per unit), how big a
	 * block of the emblem is and how high over the faces the emblem's middle hangs; the name and district under a face.
	 */
	public static final double FACE_UP = 40, EMBLEM_BLOCK = 2.0, EMBLEM_UP = 26, NAME_DOWN = 9, DISTRICT_DOWN = 12.5;
	public static final float FACE = 24f, NAME = 10f, DISTRICT = 6f;
	/** How far round the stage (degrees) a viewer walks before their emblem turns to face them again. */
	public static final double TURN_AFTER = 20;

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

	/** When the end begins (the emblem fades). */
	public int outroAt() {
		return length - OUTRO;
	}

	/**
	 * The direction along the emblem's face for a viewer at (vx, vz) looking at the stage at (cx, cz): flat, at right
	 * angles to the line between them, so the emblem faces them. A viewer right under it: east.
	 */
	public static double[] across(double vx, double vz, double cx, double cz) {
		double dx = vx - cx, dz = vz - cz;
		double len = Math.hypot(dx, dz);
		if (len < 1e-6) return new double[] {1, 0};
		return new double[] {dz / len, -dx / len};
	}

	/** The bearing (degrees) of a viewer at (vx, vz) round the stage at (cx, cz). */
	public static double bearing(double vx, double vz, double cx, double cz) {
		return Math.toDegrees(Math.atan2(vz - cz, vx - cx));
	}

	/** Whether a viewer went far enough round the stage (from {@code was} to {@code now}, degrees) for the emblem to turn. */
	public static boolean turned(double was, double now) {
		double d = Math.abs(((now - was) % 360 + 540) % 360 - 180);
		return d > TURN_AFTER;
	}
}
