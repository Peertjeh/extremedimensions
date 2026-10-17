package net.battlehub.arch.mod;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.LongIntMap;
import net.battlehub.arch.Plan;
import net.battlehub.arch.Slot;

/**
 * Roofs as a height field: every roof shape (gable, hip, cone, spire, dome) adds the height it wants over its
 * footprint, the field keeps the highest, and blocks are made from the field at the end. So where a transept roof
 * meets a nave roof, valleys and hips come out right by themselves, and stairs turn their corners (see
 * {@code Connect}). Coordinates are those of the frame the field is drawn in.
 */
public final class RoofField {
	private final LongIntMap top = new LongIntMap(1024);
	private final LongIntMap base = new LongIntMap(1024);

	private static long k(int x, int z) {
		return Plan.key(x, 0, z);
	}

	/** The roof height at a column (Integer.MIN_VALUE: no roof). */
	public int at(int x, int z) {
		return top.get(k(x, z), Integer.MIN_VALUE);
	}

	public int baseAt(int x, int z) {
		return base.get(k(x, z), Integer.MIN_VALUE);
	}

	public void raise(int x, int z, int h, int b) {
		long key = k(x, z);
		int old = top.get(key, Integer.MIN_VALUE);
		if (h > old) top.put(key, h);
		int ob = base.get(key, Integer.MAX_VALUE);
		if (b < ob) base.put(key, b);
	}

	/**
	 * A gable roof over the walls x0..x1, z0..z1 (outer faces) whose eaves sit at {@code eave} (the wall top),
	 * {@code pitch} blocks up per block in, the ridge along z when {@code alongZ}, hanging {@code over} blocks out.
	 */
	public void gable(int x0, int z0, int x1, int z1, boolean alongZ, int eave, int pitch, int over) {
		for (int x = x0 - over; x <= x1 + over; x++) {
			for (int z = z0 - over; z <= z1 + over; z++) {
				int d = alongZ ? Math.min(x - (x0 - over), (x1 + over) - x) : Math.min(z - (z0 - over), (z1 + over) - z);
				raise(x, z, eave + pitch * d, eave);
			}
		}
	}

	/**
	 * A lean-to (shed) roof: one slope rising away from {@code low} (the side where the eave is), as over a
	 * church aisle leaning on the nave.
	 */
	public void shed(int x0, int z0, int x1, int z1, net.battlehub.arch.Dir low, int eave, int pitch, int over) {
		for (int x = x0 - over; x <= x1 + over; x++) {
			for (int z = z0 - over; z <= z1 + over; z++) {
				int d = switch (low) {
					case NORTH -> z - (z0 - over);
					case SOUTH -> (z1 + over) - z;
					case WEST -> x - (x0 - over);
					case EAST -> (x1 + over) - x;
				};
				raise(x, z, eave + pitch * d, eave);
			}
		}
	}

	/** A hipped roof (a pyramid on a square): slopes on all four sides. */
	public void hip(int x0, int z0, int x1, int z1, int eave, int pitch, int over) {
		for (int x = x0 - over; x <= x1 + over; x++) {
			for (int z = z0 - over; z <= z1 + over; z++) {
				int d = Math.min(Math.min(x - (x0 - over), (x1 + over) - x), Math.min(z - (z0 - over), (z1 + over) - z));
				raise(x, z, eave + pitch * d, eave);
			}
		}
	}

	/** A cone (or a spire when steep) over a round or polygonal footprint. */
	public void cone(double cx, double cz, double r, int eave, double pitch, int over) {
		double rr = r + over;
		int ir = (int) Math.ceil(rr);
		for (int x = (int) Math.floor(cx) - ir; x <= (int) Math.ceil(cx) + ir; x++) {
			for (int z = (int) Math.floor(cz) - ir; z <= (int) Math.ceil(cz) + ir; z++) {
				double d = Math.hypot(x - cx, z - cz);
				if (d > rr + 0.35) continue;
				raise(x, z, eave + (int) Math.round(pitch * Math.max(0, rr - d)), eave);
			}
		}
	}

	/** An octagonal spire: like a cone but with flat faces. */
	public void spire(double cx, double cz, double r, int eave, double pitch, int over) {
		double rr = r + over;
		int ir = (int) Math.ceil(rr);
		for (int x = (int) Math.floor(cx) - ir; x <= (int) Math.ceil(cx) + ir; x++) {
			for (int z = (int) Math.floor(cz) - ir; z <= (int) Math.ceil(cz) + ir; z++) {
				double ax = Math.abs(x - cx), az = Math.abs(z - cz);
				double d = Math.max(Math.max(ax, az), (ax + az) / 1.414);
				if (d > rr + 0.3) continue;
				raise(x, z, eave + (int) Math.round(pitch * Math.max(0, rr - d)), eave);
			}
		}
	}

	/** A dome: a half sphere (taller than half when {@code stretch} > 1). */
	public void dome(double cx, double cz, double r, int eave, double stretch) {
		int ir = (int) Math.ceil(r);
		for (int x = (int) Math.floor(cx) - ir; x <= (int) Math.ceil(cx) + ir; x++) {
			for (int z = (int) Math.floor(cz) - ir; z <= (int) Math.ceil(cz) + ir; z++) {
				double d = Math.hypot(x - cx, z - cz);
				if (d > r + 0.3) continue;
				double h = Math.sqrt(Math.max(0, r * r - d * d)) * stretch;
				raise(x, z, eave + (int) Math.round(h), eave);
			}
		}
	}

	/**
	 * Draws the roof: each column gets its top block (stairs facing uphill where the roof slopes, a ridge cap where
	 * it peaks) and enough blocks below it to close the steps to its neighbours; the edges of each sloping field
	 * (verges and eaves) in the edge role.
	 */
	public void draw(Ctx c, Slot roof, Slot edge, int prio) {
		top.forEach((key, h) -> {
			int x = Plan.kx(key), z = Plan.kz(key);
			int lowest = h;
			int lowestReal = h;
			Dir down = null;
			int downCount = 0;
			Dir up = null;
			int upBest = h;
			for (Dir d : Dir.values()) {
				int n = at(x + d.dx, z + d.dz);
				if (n != Integer.MIN_VALUE) lowestReal = Math.min(lowestReal, n);
				if (n == Integer.MIN_VALUE) n = baseAt(x, z) - 1;
				lowest = Math.min(lowest, n);
				if (n < h) {
					downCount++;
					down = d;
				}
				if (n > upBest) {
					upBest = n;
					up = d;
				}
			}
			int b = baseAt(x, z);
			// down to the lowest roof neighbour, so each step rests on the one below (no edge-only joins); the
			// outer edges (verges and eaves) stay one block thick instead of hanging down to the eaves
			int from = Math.max(b, Math.min(h, lowestReal));
			for (int y = from; y < h; y++) c.set(x, y, z, Cell.of(roof, prio));
			boolean edgeCell = false;
			for (Dir d : Dir.values()) {
				if (at(x + d.dx, z + d.dz) == Integer.MIN_VALUE) edgeCell = true;
			}
			Slot slot = edgeCell ? edge : roof;
			if (up != null) {
				c.set(x, h, z, Cell.stairs(slot, up, false, prio));
			} else if (downCount >= 3 || downCount == 2 && opposite(x, z, h)) {
				// a peak or a ridge: a full block with a cap
				c.set(x, h, z, Cell.of(roof, prio));
				c.set(x, h + 1, z, Cell.slab(edge, false, prio));
			} else if (down != null) {
				c.set(x, h, z, Cell.stairs(slot, down.back(), false, prio));
			} else {
				c.set(x, h, z, Cell.of(roof, prio));
			}
		});
	}

	/** True when the two lower neighbours are on opposite sides (a ridge line). */
	private boolean opposite(int x, int z, int h) {
		boolean ns = at(x, z - 1) < h && at(x, z + 1) < h;
		boolean ew = at(x - 1, z) < h && at(x + 1, z) < h;
		return ns || ew;
	}

	public boolean isEmpty() {
		return top.size() == 0;
	}
}
