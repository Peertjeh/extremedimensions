package net.battlehub.core;

/**
 * Geometry for bots: where to look, how to aim a bow and how hard to jump. Player and arrow physics as in vanilla
 * (gravity and drag per tick), so a bot's arrows and jumps follow the same rules as a player's.
 */
public final class BotMath {
	private BotMath() {}

	/** Gravity and drag of a player in the air. */
	public static final double PLAYER_GRAVITY = 0.08;
	public static final double PLAYER_DRAG_Y = 0.98;
	public static final double PLAYER_DRAG_XZ = 0.91;
	/** The vertical speed of a normal jump. */
	public static final double JUMP = 0.42;
	/** Arrow gravity and drag. */
	public static final double ARROW_GRAVITY = 0.05;
	public static final double ARROW_DRAG = 0.99;
	/** A thrown ender pearl: its speed and gravity (its drag is the arrow's, 1.49). */
	public static final double PEARL_SPEED = 1.5;
	public static final double PEARL_GRAVITY = 0.03;

	/** Minecraft yaw (0 = south, 90 = west) to look along dx, dz. */
	public static float yaw(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Minecraft pitch (negative = up) to look at something dy higher at horizontal distance h. */
	public static float pitch(double h, double dy) {
		return (float) -Math.toDegrees(Math.atan2(dy, Math.max(1e-6, h)));
	}

	/** Shortest turn from one angle to another, in -180..180. */
	public static float wrap(float degrees) {
		float d = degrees % 360f;
		if (d >= 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}

	/** Turns {@code from} towards {@code to} by at most {@code max} degrees. */
	public static float turn(float from, float to, float max) {
		float d = wrap(to - from);
		return from + Math.max(-max, Math.min(max, d));
	}

	/**
	 * The pitch to shoot an arrow with this speed so it comes down at horizontal distance {@code h}, {@code dy}
	 * higher; the flatter of the two solutions, or the 45 degree maximum when the target is out of range.
	 */
	public static float arrowPitch(double h, double dy, double speed) {
		return throwPitch(h, dy, speed, ARROW_GRAVITY);
	}

	/** The pitch to throw an ender pearl so it comes down at horizontal distance {@code h}, {@code dy} higher. */
	public static float pearlPitch(double h, double dy) {
		return throwPitch(h, dy, PEARL_SPEED, PEARL_GRAVITY);
	}

	/** {@link #arrowPitch} for anything thrown or shot with this speed and gravity (the drag of both is 0.99). */
	public static float throwPitch(double h, double dy, double speed, double gravity) {
		double lo = -60;
		double hi = 45;
		// Height of the arrow when it has flown h blocks, for a launch angle (positive = up)
		for (int i = 0; i < 40; i++) {
			double mid = (lo + hi) / 2;
			double y = heightAt(h, mid, speed, gravity);
			if (Double.isNaN(y) || y < dy) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (float) -((lo + hi) / 2);
	}

	/** Height of an arrow shot at {@code angle} degrees up when it has covered {@code h} blocks; NaN if it never gets there. */
	static double arrowHeightAt(double h, double angle, double speed) {
		return heightAt(h, angle, speed, ARROW_GRAVITY);
	}

	/** {@link #arrowHeightAt} with this gravity. */
	static double heightAt(double h, double angle, double speed, double gravity) {
		double a = Math.toRadians(angle);
		double vx = Math.cos(a) * speed;
		double vy = Math.sin(a) * speed;
		double x = 0;
		double y = 0;
		for (int t = 0; t < 200; t++) {
			double nx = x + vx;
			double ny = y + vy;
			if (nx >= h) {
				double f = (h - x) / (nx - x);
				return y + (ny - y) * f;
			}
			x = nx;
			y = ny;
			vx *= ARROW_DRAG;
			vy = vy * ARROW_DRAG - gravity;
			if (vx < 1e-3) {
				return Double.NaN;
			}
		}
		return Double.NaN;
	}

	/** Ticks an arrow needs to fly {@code h} blocks (for leading a moving target). */
	public static int arrowTicks(double h, double speed) {
		double x = 0;
		double v = speed;
		for (int t = 1; t < 200; t++) {
			x += v;
			if (x >= h) return t;
			v *= ARROW_DRAG;
		}
		return 200;
	}

	/**
	 * A jump: speed to set, the number of ticks until landing, and how much speed the first tick keeps (a player
	 * still touches the ground then, so the block's friction applies once).
	 */
	public record Jump(double vx, double vy, double vz, int ticks, double firstDrag) {}

	/** A jump from normal ground (friction 0.6). */
	public static Jump jump(double dx, double dy, double dz) {
		return jump(dx, dy, dz, 0.6 * PLAYER_DRAG_XZ);
	}

	/**
	 * The velocity to jump from standing to a spot {@code dx, dy, dz} away (dy: height of the landing surface
	 * relative to the take-off surface), with player physics and no steering in the air. A normal jump when
	 * that is high enough, otherwise a little more lift.
	 */
	public static Jump jump(double dx, double dy, double dz, double firstDrag) {
		double dist = Math.sqrt(dx * dx + dz * dz);
		double vy = JUMP;
		// Enough lift to clear the landing edge with some room
		while (apex(vy) < dy + 0.2 && vy < 1.2) {
			vy += 0.02;
		}
		double lo = 0;
		double hi = 3.0;
		for (int i = 0; i < 40; i++) {
			double mid = (lo + hi) / 2;
			if (land(mid, vy, dy, firstDrag).distance < dist) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		double v = (lo + hi) / 2;
		double fx = dist < 1e-6 ? 0 : dx / dist;
		double fz = dist < 1e-6 ? 0 : dz / dist;
		return new Jump(fx * v, vy, fz * v, land(v, vy, dy, firstDrag).ticks, firstDrag);
	}

	/** Highest point of a jump with this vertical speed. */
	public static double apex(double vy) {
		double y = 0;
		double best = 0;
		for (int t = 0; t < 60; t++) {
			y += vy;
			best = Math.max(best, y);
			vy = (vy - PLAYER_GRAVITY) * PLAYER_DRAG_Y;
			if (vy < 0 && y < best) break;
		}
		return best;
	}

	record Landing(double distance, int ticks) {}

	/** Where a jump with horizontal speed {@code v} comes down on a surface {@code dy} higher. */
	static Landing land(double v, double vy, double dy, double firstDrag) {
		double x = 0;
		double y = 0;
		for (int t = 1; t < 120; t++) {
			x += v;
			y += vy;
			v *= t == 1 ? firstDrag : PLAYER_DRAG_XZ;
			vy = (vy - PLAYER_GRAVITY) * PLAYER_DRAG_Y;
			if (vy < 0 && y <= dy) {
				return new Landing(x, t);
			}
		}
		return new Landing(x, 120);
	}

	/** Simulates a jump; the position after it has come down to {@code dy} (for tests). */
	public static double[] simulate(Jump j, double dy) {
		double x = 0;
		double y = 0;
		double z = 0;
		double vx = j.vx();
		double vy = j.vy();
		double vz = j.vz();
		for (int t = 1; t < 120; t++) {
			x += vx;
			y += vy;
			z += vz;
			double drag = t == 1 ? j.firstDrag() : PLAYER_DRAG_XZ;
			vx *= drag;
			vz *= drag;
			vy = (vy - PLAYER_GRAVITY) * PLAYER_DRAG_Y;
			if (vy < 0 && y <= dy) {
				return new double[] {x, y, z};
			}
		}
		return new double[] {x, y, z};
	}
}
