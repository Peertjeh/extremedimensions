package net.battlehub.core;

/** Camera paths for the match intro and the victory shot: a slow circle around a point, always looking at it. */
public final class Orbit {
	private Orbit() {}

	public record Pose(double x, double y, double z, float yaw, float pitch) {}

	/** Smooth start and stop (0..1 in, 0..1 out). */
	public static double ease(double t) {
		double c = Math.max(0, Math.min(1, t));
		return c * c * (3 - 2 * c);
	}

	/**
	 * The camera at progress {@code t} (0..1): on a circle of {@code radius} around the centre, {@code height}
	 * above it, starting at {@code startDeg} and turning {@code sweepDeg}, looking at the centre (raised by {@code lookUp}).
	 */
	public static Pose at(double cx, double cy, double cz, double radius, double height, double startDeg, double sweepDeg,
			double lookUp, double t) {
		double a = Math.toRadians(startDeg + sweepDeg * ease(t));
		double x = cx + Math.cos(a) * radius;
		double z = cz + Math.sin(a) * radius;
		double y = cy + height;
		float yaw = BotMath.yaw(cx - x, cz - z);
		float pitch = BotMath.pitch(Math.hypot(cx - x, cz - z), cy + lookUp - y);
		return new Pose(x, y, z, yaw, pitch);
	}

	/** The intro: from high and far to closer and lower while turning, so the arena is seen from above first. */
	public static Pose intro(double cx, double cy, double cz, double size, double t) {
		double e = ease(t);
		double radius = size * (1.1 - 0.45 * e);
		double height = size * (0.9 - 0.55 * e);
		return at(cx, cy, cz, radius, height, 200, 140, 1.0, t);
	}
}
