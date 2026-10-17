package net.battlehub.arch;

/**
 * Places a local frame inside a parent frame: an optional mirror (local x flipped), a number of clockwise quarter
 * turns and an offset. Modules are drawn in their own frame, with local +z pointing outwards (towards whoever looks
 * at the front) and local +x to that viewer's right; a building is drawn in its own frame and placed in the lobby
 * the same way. Directions (stairs, doors, logs) turn along with the coordinates.
 */
public record Xform(int turns, boolean mirror, int ox, int oy, int oz) {
	public static final Xform ID = new Xform(0, false, 0, 0, 0);

	public Xform {
		turns = ((turns % 4) + 4) % 4;
	}

	/** A frame whose local +z (its front) points {@code outward}, with its local origin at (x, y, z). */
	public static Xform facing(Dir outward, int x, int y, int z) {
		// local +z is south; south turned t times clockwise must be outward
		int t = (outward.ordinal() - Dir.SOUTH.ordinal() + 4) & 3;
		return new Xform(t, false, x, y, z);
	}

	public static Xform at(int x, int y, int z) {
		return new Xform(0, false, x, y, z);
	}

	public Xform mirrored() {
		return new Xform(turns, !mirror, ox, oy, oz);
	}

	public int x(int lx, int lz) {
		int x = mirror ? -lx : lx;
		return ox + switch (turns) {
			case 0 -> x;
			case 1 -> -lz;
			case 2 -> -x;
			default -> lz;
		};
	}

	public int y(int ly) {
		return oy + ly;
	}

	public int z(int lx, int lz) {
		int x = mirror ? -lx : lx;
		return oz + switch (turns) {
			case 0 -> lz;
			case 1 -> x;
			case 2 -> -lz;
			default -> -x;
		};
	}

	public Dir dir(Dir local) {
		if (local == null) return null;
		Dir d = mirror && local.alongX() ? local.back() : local;
		return d.turn(turns);
	}

	/** True when local x and z swap places (for log axes). */
	public boolean swapsAxes() {
		return (turns & 1) == 1;
	}

	/** This frame placed inside {@code parent}: local points go through this one first, then the parent. */
	public Xform in(Xform parent) {
		// Work out where the local axes and origin end up in the parent's parent frame
		int x0 = parent.x(ox, oz);
		int z0 = parent.z(ox, oz);
		int exX = parent.x(x(1, 0), z(1, 0)) - parent.x(x(0, 0), z(0, 0));
		int exZ = parent.z(x(1, 0), z(1, 0)) - parent.z(x(0, 0), z(0, 0));
		int ezX = parent.x(x(0, 1), z(0, 1)) - parent.x(x(0, 0), z(0, 0));
		int ezZ = parent.z(x(0, 1), z(0, 1)) - parent.z(x(0, 0), z(0, 0));
		// Find the turns/mirror with the same axes
		for (int t = 0; t < 4; t++) {
			for (int m = 0; m < 2; m++) {
				Xform c = new Xform(t, m == 1, 0, 0, 0);
				if (c.x(1, 0) == exX && c.z(1, 0) == exZ && c.x(0, 1) == ezX && c.z(0, 1) == ezZ) {
					return new Xform(t, m == 1, x0, parent.y(oy), z0);
				}
			}
		}
		throw new IllegalStateException("not a rigid frame");
	}

	public Xform shifted(int dx, int dy, int dz) {
		return new Xform(turns, mirror, ox + dx, oy + dy, oz + dz);
	}
}
