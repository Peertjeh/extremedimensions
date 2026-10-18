package net.battlehub.core.ai;

/** A small immutable vector for the AI (positions, velocities, directions), free of Minecraft classes. */
public record V3(double x, double y, double z) {
	public static final V3 ZERO = new V3(0, 0, 0);

	public V3 add(V3 o) {
		return new V3(x + o.x, y + o.y, z + o.z);
	}

	public V3 sub(V3 o) {
		return new V3(x - o.x, y - o.y, z - o.z);
	}

	public V3 scale(double f) {
		return new V3(x * f, y * f, z * f);
	}

	public V3 flat() {
		return new V3(x, 0, z);
	}

	public double length() {
		return Math.sqrt(x * x + y * y + z * z);
	}

	public double flatLength() {
		return Math.sqrt(x * x + z * z);
	}

	public double dot(V3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	/** Unit vector (or zero when too short to have a direction). */
	public V3 unit() {
		double l = length();
		return l < 1e-9 ? ZERO : scale(1 / l);
	}

	public double distance(V3 o) {
		return sub(o).length();
	}

	public double flatDistance(V3 o) {
		return sub(o).flatLength();
	}

	/** The horizontal direction rotated a quarter turn to the left (for strafing round something). */
	public V3 left() {
		return new V3(z, 0, -x);
	}
}
