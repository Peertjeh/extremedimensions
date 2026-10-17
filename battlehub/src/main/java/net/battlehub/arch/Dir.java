package net.battlehub.arch;

/**
 * The four horizontal directions: x grows to the east, z to the south (Minecraft's axes). Turning "right" is
 * clockwise seen from above: north, east, south, west.
 */
public enum Dir {
	NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

	public final int dx;
	public final int dz;

	Dir(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	public Dir right() {
		return values()[(ordinal() + 1) & 3];
	}

	public Dir left() {
		return values()[(ordinal() + 3) & 3];
	}

	public Dir back() {
		return values()[(ordinal() + 2) & 3];
	}

	/** Turned {@code quarters} times clockwise. */
	public Dir turn(int quarters) {
		return values()[(ordinal() + (quarters % 4 + 4)) & 3];
	}

	public boolean alongX() {
		return dx != 0;
	}

	/** The block-state name: "north", "east", ... */
	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static Dir of(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? EAST : WEST;
		}
		return dz >= 0 ? SOUTH : NORTH;
	}

	public static Dir byId(String id) {
		for (Dir d : values()) {
			if (d.id().equals(id)) return d;
		}
		return null;
	}
}
