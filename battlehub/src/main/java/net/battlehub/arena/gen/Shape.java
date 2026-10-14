package net.battlehub.arena.gen;

import net.minecraft.util.RandomSource;

/** Floor outline of a generated arena. All shapes are symmetric around both axes. */
public enum Shape {
	CIRCLE, SQUARE, OCTAGON, HEXAGON, ELLIPSE, CROSS;

	/** Shapes that contain a circle of 0.6 × radius (for spawn rings). */
	public static final Shape[] ROUNDISH = {CIRCLE, SQUARE, OCTAGON, HEXAGON};

	public static Shape random(RandomSource rnd) {
		Shape[] all = values();
		return all[rnd.nextInt(all.length)];
	}

	public static Shape roundish(RandomSource rnd) {
		return ROUNDISH[rnd.nextInt(ROUNDISH.length)];
	}

	/** Is block (x, z) inside this shape with radius r (centred on 0,0)? */
	public boolean inside(int x, int z, int r) {
		double ax = Math.abs(x);
		double az = Math.abs(z);
		return switch (this) {
			case CIRCLE -> x * x + z * z <= r * r + r * 0.6;
			case SQUARE -> ax <= r - 1 && az <= r - 1;
			case OCTAGON -> ax <= r && az <= r && ax + az <= r * 1.42;
			case HEXAGON -> az <= r * 0.87 && ax + az * 0.577 <= r;
			case ELLIPSE -> (ax * ax) / ((double) r * r) + (az * az) / (r * 0.72 * r * 0.72) <= 1.0 + 0.8 / r;
			case CROSS -> (ax <= r && az <= r * 0.42) || (az <= r && ax <= r * 0.42) || x * x + z * z <= r * 0.66 * r * 0.66;
		};
	}
}
