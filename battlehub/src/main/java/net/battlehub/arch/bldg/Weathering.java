package net.battlehub.arch.bldg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.LongIntMap;
import net.battlehub.arch.Noise3;
import net.battlehub.arch.Plan;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;

/**
 * Age and condition, applied after assembly and in an architectural order: roofs and ornaments go first, crowns
 * crumble from the top, breaches open in walls (decoration before structure), floors fall in, and then everything
 * that no longer connects to the ground falls (except what is meant to float). Fallen stone lies at the foot of
 * the walls; vines, moss and bushes take the ruin back. Weathering of the stone itself (moss low, cracks higher)
 * is done by the material resolver.
 */
public final class Weathering {
	/** Faces and edges: blocks that touch along an edge hold each other in Minecraft (stairs, roof steps). */
	static final int[][] NEIGHBOURS = neighbours();

	private static int[][] neighbours() {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					int n = Math.abs(x) + Math.abs(y) + Math.abs(z);
					if (n == 1 || n == 2) out.add(new int[] {x, y, z});
				}
			}
		}
		return out.toArray(new int[0][]);
	}

	private Weathering() {}

	public static void apply(Building b, Rng rng) {
		Condition c = b.brief.condition;
		if (c.damage > 0) damage(b, rng.stream("damage"), c.damage);
		if (c.vegetation > 0 && b.detail >= 1) vegetation(b, rng.stream("plants"), c.vegetation);
	}

	static void damage(Building b, Rng rng, double amount) {
		Plan p = b.plan;
		Noise3 n = new Noise3(rng.stream("noise").seed());
		int minY = b.floorY, maxY = p.maxY();
		int h = Math.max(1, maxY - minY);
		List<int[]> fallen = new ArrayList<>();
		breakColumns(b, rng.stream("columns"), amount, fallen);
		for (long k : p.positions()) {
			int x = Plan.kx(k), y = Plan.ky(k), z = Plan.kz(k);
			Cell c = p.get(x, y, z);
			if (c == null || c.air() || c.is(Cell.KEEP) || c.is(Cell.FLOAT)) continue;
			double rel = (double) (y - minY) / h;
			double noise = n.even(x, y * 0.7, z, 7, 2);
			boolean remove = false;
			// roofs open up in patches
			if ((c.slot() == Slot.ROOF || c.slot() == Slot.ROOF_EDGE) && noise < amount * 0.95) remove = true;
			// crowns crumble: the higher, the likelier
			double collapse = maxY - h * amount * 0.45 * (0.55 + noise);
			if (y > collapse) remove = true;
			// ornament and glass break first
			if (c.is(Cell.DECOR) && Rng.hash(rng.seed(), x, y, z) < amount * 0.5) remove = true;
			if ((c.slot() == Slot.GLASS || c.slot() == Slot.STAINED) && Rng.hash(rng.seed() + 1, x, y, z) < amount * 1.2) remove = true;
			// breaches in walls, high up more than low down, structure less than infill
			if (c.slot().weathers() && !c.is(Cell.INSIDE)) {
				double breach = n.even(x * 1.3 + 100, y * 1.1, z * 1.3, 5, 2);
				double threshold = amount * (0.25 + 0.55 * rel) * (c.slot().structural() ? 0.75 : 1);
				if (breach < threshold) remove = true;
			}
			// upper floors fall in
			if ((c.slot() == Slot.FLOOR || c.slot() == Slot.FLOOR_ALT) && y > minY + 1 && noise < amount * 0.6) remove = true;
			if (remove) {
				p.force(x, y, z, Cell.air(Cell.FILL));
				if (c.solid() && c.slot().weathers()) fallen.add(new int[] {x, y, z});
			}
		}
		fall(b);
		rubble(b, rng, fallen, amount);
	}

	/**
	 * Columns break at a random height (the shaft above falls) and some of the pieces lie on the ground beside
	 * them, along x or z.
	 */
	static void breakColumns(Building b, Rng rng, double amount, List<int[]> fallen) {
		Plan p = b.plan;
		java.util.Set<Long> done = new java.util.HashSet<>();
		for (long k : p.positions()) {
			int x = Plan.kx(k), z = Plan.kz(k);
			Cell c = p.get(x, Plan.ky(k), z);
			if (c == null || c.slot() != Slot.PILLAR || c.shape() != Shape.LOG || c.is(Cell.INSIDE)) continue;
			long col = Plan.key(x, 0, z);
			if (!done.add(col)) continue;
			if (Rng.hash(rng.seed(), x, 0, z) > amount * 0.8) continue;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int y = p.minY(); y <= p.maxY(); y++) {
				Cell cc = p.get(x, y, z);
				if (cc != null && cc.slot() == Slot.PILLAR && cc.shape() == Shape.LOG) {
					lo = Math.min(lo, y);
					hi = Math.max(hi, y);
				}
			}
			int cut = lo + 1 + (int) (Rng.hash(rng.seed() + 1, x, 0, z) * Math.max(1, hi - lo - 1));
			int removed = 0;
			for (int y = cut; y <= hi + 1; y++) {
				Cell cc = p.get(x, y, z);
				if (cc == null || cc.air()) break;
				if (cc.slot() != Slot.PILLAR && cc.slot() != Slot.TRIM) break;
				p.force(x, y, z, Cell.air(Cell.FILL));
				removed++;
			}
			// the drum lying beside its column
			if (removed >= 2 && rng.chance(0.6)) {
				Dir d = Dir.values()[rng.nextInt(4)];
				int gy = lo;
				for (int i = 1; i <= Math.min(removed, 4); i++) {
					int fx = x + d.dx * (i + 1), fz = z + d.dz * (i + 1);
					if (p.filled(fx, gy, fz)) break;
					p.put(fx, gy, fz, Cell.log(Slot.PILLAR, d.alongX() ? 'x' : 'z', Cell.DETAIL).flag(Cell.DECOR));
				}
			}
			fallen.add(new int[] {x, cut, z});
		}
	}

	/** Removes everything not connected to the ground (through solid neighbours), unless it is meant to float. */
	static void fall(Building b) {
		Plan p = b.plan;
		LongIntMap seen = new LongIntMap(p.size());
		ArrayDeque<long[]> queue = new ArrayDeque<>();
		int base = b.floorY;
		for (long k : p.positions()) {
			int x = Plan.kx(k), y = Plan.ky(k), z = Plan.kz(k);
			Cell c = p.get(x, y, z);
			if (c == null || c.air()) continue;
			boolean grounded = y <= base - 1 || c.is(Cell.FLOAT);
			if (!grounded) {
				int g = b.groundAt(x, z);
				grounded = g != Integer.MIN_VALUE && y <= g + 1;
			}
			if (grounded) {
				seen.put(k, 1);
				queue.add(new long[] {x, y, z});
			}
		}
		int[][] dirs = NEIGHBOURS;
		while (!queue.isEmpty()) {
			long[] q = queue.poll();
			for (int[] d : dirs) {
				int x = (int) q[0] + d[0], y = (int) q[1] + d[1], z = (int) q[2] + d[2];
				long k = Plan.key(x, y, z);
				if (seen.contains(k)) continue;
				Cell c = p.get(x, y, z);
				if (c == null || c.air()) continue;
				seen.put(k, 1);
				queue.add(new long[] {x, y, z});
			}
		}
		for (long k : p.positions()) {
			if (seen.contains(k)) continue;
			int x = Plan.kx(k), y = Plan.ky(k), z = Plan.kz(k);
			Cell c = p.get(x, y, z);
			if (c != null && !c.air() && !c.is(Cell.KEEP)) p.force(x, y, z, Cell.air(Cell.FILL));
		}
	}

	/** Heaps of fallen stone at the foot of the walls below where stone fell. */
	static void rubble(Building b, Rng rng, List<int[]> fallen, double amount) {
		Plan p = b.plan;
		int piles = Math.min(fallen.size() / 6, 400);
		for (int i = 0; i < piles; i++) {
			int[] f = fallen.get(rng.nextInt(fallen.size()));
			int x = f[0] + rng.range(-2, 2), z = f[2] + rng.range(-2, 2);
			int y = b.floorY + 1;
			// settle on whatever is below
			while (y > b.floorY - 3 && !p.solid(x, y - 1, z)) y--;
			Cell here = p.get(x, y, z);
			if (here != null && !here.air()) continue;
			Cell under = p.get(x, y - 1, z);
			if (under != null && under.is(Cell.KEEP)) continue;
			p.put(x, y, z, (rng.chance(0.5) ? Cell.slab(Slot.RUBBLE, false, Cell.DETAIL) : Cell.of(Slot.RUBBLE, Cell.DETAIL)).flag(Cell.DECOR));
		}
	}

	/** True above a path or anything else that must stay clear (within three blocks down). */
	static boolean keptBelow(Plan p, int x, int y, int z) {
		for (int d = 1; d <= 3; d++) {
			Cell c = p.get(x, y - d, z);
			if (c != null && c.is(Cell.KEEP)) return true;
			if (c != null && !c.air() && !c.is(Cell.DECOR) && c.slot() != Slot.LITERAL) return false;
		}
		return false;
	}

	static void vegetation(Building b, Rng rng, double amount) {
		Plan p = b.plan;
		Noise3 patches = new Noise3(rng.stream("patches").seed());
		for (long k : p.positions()) {
			int x = Plan.kx(k), y = Plan.ky(k), z = Plan.kz(k);
			Cell c = p.get(x, y, z);
			if (c == null || !c.solid() || c.is(Cell.INSIDE)) continue;
			double r = Rng.hash(rng.seed(), x, y, z);
			// vines down outer wall faces
			if (c.slot().weathers() && r < amount * 0.05) {
				for (Dir d : Dir.values()) {
					int vx = x + d.dx, vz = z + d.dz;
					if (p.filled(vx, y, vz)) continue;
					int len = 1 + (int) (Rng.hash(rng.seed() + 3, x, y, z) * 6 * amount);
					for (int i = 0; i < len; i++) {
						if (p.filled(vx, y - i, vz) || y - i < b.floorY) break;
						p.put(vx, y - i, vz, Cell.literal("vine[" + d.back().id() + "=true]", Cell.DETAIL).flag(Cell.DECOR));
					}
					break;
				}
			}
			// moss and grass on exposed tops
			if (!p.filled(x, y + 1, z) && c.shape() == Shape.CUBE && patches.even(x, y, z, 5, 2) < amount * 0.55 && r < 0.85) {
				String top = Rng.hash(rng.seed() + 5, x, y, z) < 0.6 ? "moss_carpet" : "short_grass";
				p.put(x, y + 1, z, Cell.literal(top, Cell.DETAIL).flag(Cell.DECOR));
			}
		}
		// bushes at the foot of the walls
		int bushes = (int) (amount * 30);
		for (int i = 0; i < bushes; i++) {
			int x = rng.range(p.minX(), p.maxX()), z = rng.range(p.minZ(), p.maxZ());
			int y = b.floorY + 1;
			if (p.filled(x, y, z) || !p.solid(x, y - 1, z) && b.groundAt(x, z) != y - 1) continue;
			Cell under = p.get(x, y - 1, z);
			if (under != null && under.is(Cell.KEEP)) continue;
			int size = rng.range(1, 2);
			for (int dx = -size; dx <= size; dx++) {
				for (int dz = -size; dz <= size; dz++) {
					for (int dy = 0; dy <= size - 1; dy++) {
						if (Math.abs(dx) + Math.abs(dz) + dy > size + 1) continue;
						if (keptBelow(p, x + dx, y + dy, z + dz)) continue;
						p.putIfEmpty(x + dx, y + dy, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL).flag(Cell.DECOR));
					}
				}
			}
		}
	}
}
