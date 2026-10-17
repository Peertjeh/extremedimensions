package net.battlehub.arch.bldg;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mod.Ctx;

/**
 * Fortification parts shared by castles and city walls: curtain walls of any plan (thick shells with a wall walk,
 * merlons, a battered foot and arrow slits), straight stairs up to a wall walk, openings from towers onto the
 * walks, and a courtyard well.
 */
public final class Fort {
	private Fort() {}

	/** The side of a footprint cell that faces out (null when inside). */
	static Dir outward(Footprint fp, int x, int z) {
		for (Dir d : Dir.values()) {
			if (!fp.contains(x + d.dx, z + d.dz)) return d;
		}
		return null;
	}

	/**
	 * A curtain wall round {@code fp}: {@code t} thick, walls up to {@code top} (the wall walk), a parapet with
	 * merlons on the outside, a battered foot two rows high and arrow slits every few blocks.
	 */
	public static void curtain(Ctx c, Footprint fp, int y0, int top, int t) {
		Footprint wide = fp.inset(-1);
		for (int x = wide.minX(); x <= wide.maxX(); x++) {
			for (int z = wide.minZ(); z <= wide.maxZ(); z++) {
				if (wide.contains(x, z) && !fp.contains(x, z)) {
					// battered foot
					Dir in = outward(wide, x, z);
					c.set(x, y0, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
					c.set(x, y0 + 1, z, Cell.stairs(Slot.FOUNDATION, in == null ? Dir.NORTH : in.back(), false, Cell.DETAIL));
					continue;
				}
				if (!fp.shell(x, z, t)) continue;
				for (int y = y0; y < top; y++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
				c.set(x, top, z, Cell.of(Slot.FLOOR, Cell.BODY));
				if (fp.edge(x, z)) {
					c.set(x, top, z, Cell.of(Slot.WALL, Cell.BODY));
					c.set(x, top + 1, z, Cell.of(Slot.WALL, Cell.DETAIL));
					int per = Math.floorMod(x + z, 3);
					if (per != 1) c.set(x, top + 2, z, Cell.of(Slot.WALL, Cell.DETAIL));
					// corbelled string course under the parapet
					Dir out = outward(fp, x, z);
					if (out != null && c.detail(1)) c.decor(x + out.dx, top - 1, z + out.dz, Cell.stairs(Slot.TRIM, out.back(), true, Cell.DETAIL));
					// arrow slits
					if (out != null && Math.floorMod(x * 7 + z * 13, 6) == 0) {
						int sy = y0 + (top - y0) / 2;
						for (int k = 0; k < t; k++) {
							for (int y = sy; y < sy + 3; y++) c.set(x - out.dx * k, y, z - out.dz * k, Cell.air(Cell.OPEN));
						}
					}
				}
			}
		}
	}

	/**
	 * Straight stairs rising along {@code along} from (x, y, z), {@code width} wide towards {@code side}, solid
	 * underneath; the last step arrives at y + length.
	 */
	public static void stairRun(Ctx c, int x, int y, int z, Dir along, Dir side, int length, int width) {
		for (int i = 0; i < length; i++) {
			for (int w = 0; w < width; w++) {
				int px = x + along.dx * i + side.dx * w, pz = z + along.dz * i + side.dz * w;
				for (int yy = y; yy < y + i; yy++) c.set(px, yy, pz, Cell.of(Slot.FOUNDATION, Cell.BODY));
				c.set(px, y + i, pz, Cell.stairs(Slot.WALL, along, false, Cell.FEATURE).flag(Cell.KEEP));
				for (int hy = 1; hy <= 3; hy++) c.set(px, y + i + hy, pz, Cell.air(Cell.FEATURE - 1));
			}
		}
	}

	/** Openings from a tower onto the wall walks that meet it: where walk floor touches the tower's wall. */
	public static void walkDoors(Ctx c, Footprint tower, int wallT, int walkY) {
		Footprint inner = tower.inset(wallT);
		for (int x = tower.minX(); x <= tower.maxX(); x++) {
			for (int z = tower.minZ(); z <= tower.maxZ(); z++) {
				if (!tower.edge(x, z)) continue;
				for (Dir d : Dir.values()) {
					int ox = x + d.dx, oz = z + d.dz;
					if (tower.contains(ox, oz)) continue;
					Cell walk = c.get(ox, walkY, oz);
					Cell above = c.get(ox, walkY + 1, oz);
					if (walk == null || walk.slot() != Slot.FLOOR || above != null && !above.air()) continue;
					// cut through the tower wall towards its inside
					int px = x, pz = z;
					for (int k = 0; k < wallT + 1 && !inner.contains(px, pz); k++) {
						c.force(px, walkY + 1, pz, Cell.air(Cell.FEATURE));
						c.force(px, walkY + 2, pz, Cell.air(Cell.FEATURE));
						c.set(px, walkY, pz, Cell.of(Slot.FLOOR, Cell.FEATURE));
						px -= d.dx;
						pz -= d.dz;
					}
				}
			}
		}
	}

	/** A well: a ring of stone round water, two posts and a little roof. */
	public static void well(Ctx c, int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					c.set(x, y, z, Cell.of(Slot.WATER, Cell.FEATURE));
					c.set(x, y - 1, z, Cell.of(Slot.WATER, Cell.FEATURE));
				} else {
					c.set(x + dx, y, z + dz, Cell.of(Slot.WALL, Cell.FEATURE));
				}
			}
		}
		for (int side = -1; side <= 1; side += 2) {
			c.set(x + side, y + 1, z, Cell.shape(Slot.WOOD, Shape.FENCE, Cell.FEATURE));
			c.set(x + side, y + 2, z, Cell.shape(Slot.WOOD, Shape.FENCE, Cell.FEATURE));
		}
		for (int dx = -1; dx <= 1; dx++) {
			c.set(x + dx, y + 3, z - 1, Cell.stairs(Slot.ROOF, Dir.SOUTH, false, Cell.FEATURE));
			c.set(x + dx, y + 3, z + 1, Cell.stairs(Slot.ROOF, Dir.NORTH, false, Cell.FEATURE));
			c.set(x + dx, y + 3, z, Cell.slab(Slot.ROOF, true, Cell.FEATURE));
		}
		c.decor(x, y + 2, z, Cell.literal("iron_chain", Cell.DETAIL));
	}
}
