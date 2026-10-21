package com.extremedimensions.seam;

import static com.extremedimensions.seam.SeamArchitect.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The ground of a floating island, planned as a surface before a block of it is laid, so what is built on it can be
 * fitted to it and it to what is built on it.
 *
 * <p>An island's top rolls: low mounds and a gentle crown on the larger islands, and a shoulder at the coast where the
 * turf rounds over the edge instead of ending in a cut. Where a bridge comes in the ground runs level out to the rim
 * (its {@code corridors}), so streets and bridges meet it at the island's height. Its coast wanders in bays and
 * headlands ({@link Shape#edge}), and its underside is an upside-down mountain whose thickness grows smoothly from the
 * coast to a pointed summit under the middle, with lesser summits about it and ribs down its flanks; no column is a
 * block longer or shorter than its neighbours by chance alone.
 *
 * <p>A structure is set down on it with {@link #settle}: the ground under its footprint and a margin round it is
 * levelled to the height it stands at, the land round that blends into it, and wherever the footprint reaches past
 * the coast the island grows a ledge of rock under it, faced in masonry where it shows, so nothing built here stands
 * over the nothing.
 */
public final class Terrain {
	/** Corridors (bits as for streets: 0 east, 1 west, 2 south, 3 north) where the ground runs level to a bridge. */
	public static final int ALL_SIDES = 15;

	final Shape s;
	final long seed;
	final int span, size;
	final boolean mossy;
	/** Per column (index of {@link #at}): whether there is land, its surface, and its underside (lowest block). */
	final boolean[] land;
	final int[] surf, bottom;
	/** Whether a column belongs to something built on it (no growth, its surface kept level). */
	final boolean[] built;
	/** How deep the underside is under the middle, and near the coast (for ledges grown under structures). */
	final double deep;

	private Terrain(Shape s, long seed, int mask, boolean mossy) {
		this.s = s;
		this.seed = seed;
		this.mossy = mossy;
		this.span = s.r() + 14;
		this.size = span * 2 + 1;
		this.land = new boolean[size * size];
		this.surf = new int[size * size];
		this.bottom = new int[size * size];
		this.built = new boolean[size * size];
		int r = s.r();
		this.deep = r <= 16 ? r * 1.15 : 18.4 + (r - 16) * 0.65;
		plan(mask);
	}

	/** The ground of an island, planned (nothing is laid until {@link #emit}); one number is drawn from {@code rnd}. */
	public static Terrain of(Random rnd, Shape s, int mask) {
		return new Terrain(s, rnd.nextLong(), mask, true);
	}

	/** As {@link #of(Random, Shape, int)}, with or without turf on its top. */
	public static Terrain of(Random rnd, Shape s, int mask, boolean mossy) {
		return new Terrain(s, rnd.nextLong(), mask, mossy);
	}

	int at(int x, int z) {
		int dx = x - s.x() + span, dz = z - s.z() + span;
		return dx < 0 || dz < 0 || dx >= size || dz >= size ? -1 : dx * size + dz;
	}

	/** The surface under a column, or {@code fallback} where the island has no land. */
	public int surface(int x, int z, int fallback) {
		int i = at(x, z);
		return i < 0 || !land[i] ? fallback : surf[i];
	}

	/** The lowest block of the island's rock under a column, or {@code fallback} where it has no land. */
	public int bottomAt(int x, int z, int fallback) {
		int i = at(x, z);
		return i < 0 || !land[i] ? fallback : bottom[i];
	}

	private static double smooth(double e0, double e1, double v) {
		double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}

	/** Thickness of the island's underside at a column (below its surface height, the island's top). */
	private double underside(int dx, int dz, double f, double[][] lobes) {
		int x = s.x() + dx, z = s.z() + dz;
		double dist = Math.sqrt(dx * dx + dz * dz);
		// ribs and gullies down the flanks, broad and soft
		double rib = 1 - Math.abs(2 * noise(seed + 3, x / 11.0, z / 11.0) - 1);
		double dd = Math.pow(f, 0.8) * deep * (0.78 + 0.22 * rib);
		double mid = Math.max(0, 1 - dist / (0.42 * s.r()));
		dd += deep * 0.3 * Math.pow(mid, 1.5);
		for (double[] l : lobes) {
			double q = Math.max(0, 1 - Math.hypot(dx - l[0], dz - l[1]) / l[2]);
			dd += l[3] * Math.pow(q, 1.4) * Math.min(1, f * 3);
		}
		// a slow wander, so the underside is not a turned cone
		dd += (noise(seed + 5, x / 6.0, z / 6.0) - 0.5) * 2.4 * Math.min(1, f * 4);
		return 2.5 + dd;
	}

	private void plan(int mask) {
		int r = s.r(), top = s.top();
		Random under = new Random(seed ^ 0x51DE5L);
		double[][] lobes = new double[2 + under.nextInt(3)][];
		for (int i = 0; i < lobes.length; i++) {
			double a = under.nextDouble() * Math.PI * 2, out = (0.25 + under.nextDouble() * 0.4) * r;
			lobes[i] = new double[] {Math.cos(a) * out, Math.sin(a) * out, r * (0.22 + under.nextDouble() * 0.2), deep * (0.2 + under.nextDouble() * 0.3)};
		}
		// How high the mounds rise: hardly on the small islands, up to four blocks on the great ones
		double hills = r <= 12 ? 0.5 : Math.min(4.0, 0.5 + (r - 12) * 0.15);
		double shoulder = r >= 14 ? 2 : 1;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				double edge = s.edge(Math.atan2(dz, dx));
				if (dist > edge) {
					continue;
				}
				int i = (dx + span) * size + dz + span;
				int x = s.x() + dx, z = s.z() + dz;
				double f = 1 - dist / edge;
				// level towards the bridges: out along each side that has one, the ground keeps the island's height
				double corridor = 0;
				for (int k = 0; k < 4; k++) {
					if ((mask & (1 << k)) == 0) {
						continue;
					}
					double along = dx * dirs[k][0] + dz * dirs[k][1], across = Math.abs(dx * dirs[k][1] - dz * dirs[k][0]);
					if (along > 0) {
						corridor = Math.max(corridor, 1 - smooth(2.5, 6.5, across));
					}
				}
				double n = noise(seed + 21, x / (r * 0.45 + 6), z / (r * 0.45 + 6)) * 0.65 + noise(seed + 22, x / 7.0, z / 7.0) * 0.35;
				double mound = hills * smooth(0.42, 0.95, n) + hills * 0.3 * f;
				double drop = shoulder * (1 - smooth(0, 4.5, edge - dist));
				double relief = (mound - drop) * (1 - corridor);
				land[i] = true;
				surf[i] = top + (int) Math.round(relief);
				bottom[i] = top - (int) Math.round(underside(dx, dz, f, lobes));
			}
		}
	}

	/**
	 * Lays the island: turf of moss and pale moss with grass, flowers, firefly bushes and eyeblossoms (bare rock where
	 * the coast wears through), hush stone under it and the rock below darkening with depth in streaks crossed by thin
	 * strata, threaded with veins of silk; vines, roots, chains and soul lanterns hanging beneath, long fangs of stone
	 * tipped with amethyst, and light pouring off its edges. Records each column's surface on the plan.
	 *
	 * @return the depth of the underside at the centre, below the island's top
	 */
	public int emit(Plan p) {
		Random rnd = new Random(seed);
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				int i = (dx + span) * size + dz + span;
				if (land[i]) {
					lay(p, rnd, s.x() + dx, s.z() + dz, i, true);
				}
			}
		}
		int r = s.r(), top = s.top(), cx = s.x(), cz = s.z();
		// Fangs of stone hanging below, tapering to a point (thick at the root, a needle at the tip), darkening and
		// ending in amethyst
		BlockState[] rock = rockGrad();
		int fangs = 1 + r / 8;
		for (int k = 0; k < fangs; k++) {
			int fx = (int) ((rnd.nextDouble() - 0.5) * r * 0.8), fz = (int) ((rnd.nextDouble() - 0.5) * r * 0.8);
			int i = at(cx + fx, cz + fz);
			if (i < 0 || !land[i]) {
				continue;
			}
			// rooted a little up inside the rock, so it grows out of the underside rather than hanging off it
			int base = bottom[i] + 2;
			int len = 5 + rnd.nextInt(Math.max(2, Math.min(r / 2 + 4, 18)));
			double rad = 1.8 + rnd.nextDouble() * 1.6;
			for (int j = 0; j < len; j++) {
				double rr = rad * Math.pow(1 - (double) j / len, 1.6);
				int ir = (int) Math.ceil(rr);
				double t = (double) j / len;
				for (int ax = -ir; ax <= ir; ax++) {
					for (int az = -ir; az <= ir; az++) {
						if (ax * ax + az * az <= rr * rr + 0.3) {
							BlockState b = t > 0.82 ? (rnd.nextInt(3) == 0 ? crying() : amethyst()) : grad(rock, 0.55 + t * 0.45, rnd);
							p.set(cx + fx + ax, base - j, cz + fz + az, b);
						}
					}
				}
			}
		}
		// Soul lanterns on long chains beneath
		for (int k = 0; k < 2 + rnd.nextInt(3); k++) {
			int hx = (int) ((rnd.nextDouble() - 0.5) * r * 1.2), hz = (int) ((rnd.nextDouble() - 0.5) * r * 1.2);
			int i = at(cx + hx, cz + hz);
			if (i < 0 || !land[i]) {
				continue;
			}
			int len = 2 + rnd.nextInt(7);
			for (int j = 1; j <= len; j++) {
				p.soft(cx + hx, bottom[i] - j, cz + hz, chain());
			}
			p.soft(cx + hx, bottom[i] - len - 1, cz + hz, hanging(soulLantern()));
		}
		// Light pouring off the edges
		for (int k = 0; k < 1 + r / 14 + rnd.nextInt(2); k++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int ex = cx + (int) Math.round(Math.cos(a) * r * 0.8), ez = cz + (int) Math.round(Math.sin(a) * r * 0.8);
			p.falls.add(new int[] {ex, top, ez, 18 + rnd.nextInt(30)});
		}
		int mid = at(cx, cz);
		return mid >= 0 && land[mid] ? top - bottom[mid] : 0;
	}

	/** One column of the island, from its underside to its surface, and what grows on it and hangs from it. */
	private void lay(Plan p, Random rnd, int x, int z, int i, boolean growth) {
		int top = s.top(), S = surf[i], B = bottom[i];
		double dist = Math.hypot(x - s.x(), z - s.z());
		double edge = s.edge(Math.atan2(z - s.z(), x - s.x()));
		double f = Math.max(0, 1 - dist / edge);
		double patch = noise(seed, x / 7.0, z / 7.0) * 0.7 + noise(seed + 1, x / 3.0, z / 3.0) * 0.3;
		// the coast wears through to bare rock in places, not in a ring all round
		boolean bare = f < 0.12 && noise(seed + 31, x / 4.0, z / 4.0) > 0.62;
		BlockState[] rock = rockGrad();
		BlockState paleMoss = mc("pale_moss_block", moss());
		int depth = Math.max(1, top - B);
		for (int y = B; y <= S; y++) {
			BlockState b;
			if (y == S) {
				if (!mossy || built[i]) {
					b = stone();
				} else if (bare || patch < 0.2) {
					b = hash(x, y, z, 1) < 0.33 ? cobble() : stone();
				} else if (patch < 0.38) {
					b = paleMoss;
				} else {
					b = moss();
				}
			} else if (y > S - 3 || y > top - 3) {
				b = stone();
			} else {
				double k = (double) (top - y) / depth;
				b = hash(x, y, z, 2) < 0.015 + 0.05 * k ? vein() : rockAt(rock, seed, x, y, z, top, k, rnd);
			}
			p.set(x, y, z, b);
		}
		p.setGround(x, z, S);
		if (!growth) {
			return;
		}
		// Growth on the top
		if (mossy && !built[i] && !bare && f > 0.06 && patch >= 0.2) {
			int k = rnd.nextInt(1000);
			BlockState g = k < 150 ? mc("short_grass") : k < 190 ? mc("fern") : k < 210 ? mc("allium") : k < 220 ? mc("azure_bluet")
					: k < 232 ? mc("firefly_bush") : k < 240 ? mc("open_eyeblossom") : null;
			p.set(x, S + 1, z, g);
		}
		// Growth hanging from the underside near the rim
		if (f < 0.4 && rnd.nextInt(6) == 0) {
			BlockState vines = mc("cave_vines"), pale = mc("pale_hanging_moss"), roots = mc("hanging_roots");
			int len = 2 + rnd.nextInt(7);
			int kind = rnd.nextInt(3);
			for (int k = 1; k <= len; k++) {
				BlockState h = kind == 0 && vines != null
						? (rnd.nextInt(3) == 0 && vines.hasProperty(BlockStateProperties.BERRIES) ? vines.setValue(BlockStateProperties.BERRIES, true) : vines)
						: kind == 1 ? pale : roots;
				p.set(x, B - k, z, h);
				if (kind == 2) {
					break;
				}
			}
		}
	}

	/** A fixed fraction 0..1 for a block of this island (the same however often it is laid). */
	private double hash(int x, int y, int z, int salt) {
		long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	// ------------------------------------------------------------------ setting structures down

	/** The columns where a structure meets the ground: any of its blocks within two above the surface it stands on. */
	public static Set<Long> footprint(Plan structure, int base) {
		Set<Long> out = new HashSet<>();
		for (long k : structure.blocks.keySet()) {
			int y = net.minecraft.core.BlockPos.getY(k);
			if (y >= base && y <= base + 2) {
				out.add(Plan.column(net.minecraft.core.BlockPos.getX(k), net.minecraft.core.BlockPos.getZ(k)));
			}
		}
		// and the ground it asked to have paved (a forecourt, a weaving floor)
		for (Object[] pv : structure.paving) {
			out.add(Plan.column((int) pv[0], (int) pv[2]));
		}
		return out;
	}

	/**
	 * Sets a structure planned on its own down on this island, whose surface it stands on is {@code base} (it is laid
	 * from {@code base} up, usually the island's top): levels the ground under it and two blocks round it to that
	 * height, blends the land round that into it over five more, grows a ledge of rock under any of it that overhangs
	 * the coast (faced in masonry where it shows), and lays the changed columns again in {@code world}. The structure
	 * itself is not merged.
	 */
	public void settle(Plan world, Plan structure, int base) {
		settle(world, footprint(structure, base), base, 2, 5, new Terrain[0]);
	}

	/** Whether another island's land covers a column (where islands meet, each keeps to its own). */
	private static boolean landIn(Terrain[] others, int x, int z) {
		for (Terrain t : others) {
			int j = t.at(x, z);
			if (j >= 0 && t.land[j]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * As {@link #settle(Plan, Plan, int)}, for given footprint columns, a margin levelled round them and a blend beyond;
	 * columns that are land of one of the {@code others} are left to them.
	 */
	void settle(Plan world, Set<Long> foot, int base, int margin, int blend, Terrain[] others) {
		if (foot.isEmpty()) {
			return;
		}
		// Distance from the footprint, over the island's grid (a chamfer transform over the box round it)
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
		for (long c : foot) {
			int x = Plan.columnX(c), z = Plan.columnZ(c);
			x0 = Math.min(x0, x);
			x1 = Math.max(x1, x);
			z0 = Math.min(z0, z);
			z1 = Math.max(z1, z);
		}
		int reach = margin + blend + 1;
		x0 -= reach;
		x1 += reach;
		z0 -= reach;
		z1 += reach;
		int w = x1 - x0 + 1, h = z1 - z0 + 1;
		double[] d = new double[w * h];
		java.util.Arrays.fill(d, 1e9);
		for (long c : foot) {
			d[(Plan.columnX(c) - x0) * h + Plan.columnZ(c) - z0] = 0;
		}
		double dg = Math.sqrt(2);
		for (int pass = 0; pass < 2; pass++) {
			for (int a = 0; a < w; a++) {
				for (int b = 0; b < h; b++) {
					int ia = pass == 0 ? a : w - 1 - a, ib = pass == 0 ? b : h - 1 - b, sg = pass == 0 ? -1 : 1;
					int idx = ia * h + ib;
					int pa = ia + sg, pb = ib + sg;
					if (pa >= 0 && pa < w) {
						d[idx] = Math.min(d[idx], d[pa * h + ib] + 1);
						if (pb >= 0 && pb < h) {
							d[idx] = Math.min(d[idx], d[pa * h + pb] + dg);
						}
						int qb = ib - sg;
						if (qb >= 0 && qb < h) {
							d[idx] = Math.min(d[idx], d[pa * h + qb] + dg);
						}
					}
					if (pb >= 0 && pb < h) {
						d[idx] = Math.min(d[idx], d[ia * h + pb] + 1);
					}
				}
			}
		}
		// how thick the island is a little in from its coast: a ledge grown out under a structure starts as thick
		double rim = Math.max(3, 1.5 + Math.pow(0.12, 0.8) * deep * 0.9);
		Set<Integer> changed = new HashSet<>();
		int[] oldSurf = surf.clone(), oldBottom = bottom.clone();
		boolean[] oldLand = land.clone();
		for (int a = 0; a < w; a++) {
			for (int b = 0; b < h; b++) {
				double dist = d[a * h + b];
				if (dist > margin + blend) {
					continue;
				}
				int x = x0 + a, z = z0 + b;
				int i = at(x, z);
				if (i < 0) {
					continue;
				}
				if (!land[i] && landIn(others, x, z)) {
					continue;
				}
				if (dist <= margin) {
					if (!land[i]) {
						// past the coast: a ledge of rock grows under it, thinning away from the island
						double out = Math.hypot(x - s.x(), z - s.z()) - s.edge(Math.atan2(z - s.z(), x - s.x()));
						int thick = (int) Math.max(3, Math.round(rim * (1 - Math.max(0, out) / (margin + 8.0)) - dist));
						land[i] = true;
						bottom[i] = base - thick;
					}
					surf[i] = base;
					built[i] |= dist < 0.5;
				} else if (land[i]) {
					double k = smooth(0, 1, (dist - margin) / blend);
					surf[i] = (int) Math.round(base + (surf[i] - base) * k);
				}
				if (land[i] != oldLand[i] || surf[i] != oldSurf[i] || bottom[i] != oldBottom[i] || built[i]) {
					changed.add(i);
				}
			}
		}
		Random rnd = new Random(seed ^ base * 31L ^ foot.size());
		for (int i : changed) {
			int x = s.x() + i / size - span, z = s.z() + i % size - span;
			if (oldLand[i]) {
				for (int y = oldBottom[i]; y <= oldSurf[i]; y++) {
					world.clear(x, y, z);
				}
				BlockState on = world.get(x, oldSurf[i] + 1, z);
				if (on != null && growth(on)) {
					world.clear(x, oldSurf[i] + 1, z);
				}
			}
			lay(world, rnd, x, z, i, !built[i]);
		}
		// Where the levelled ground ends in a drop (the coast, or a ledge grown out over it), its face is masonry
		for (int i : changed) {
			if (surf[i] != base) {
				continue;
			}
			int x = s.x() + i / size - span, z = s.z() + i % size - span;
			int low = base;
			int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] n : nb) {
				int j = at(x + n[0], z + n[1]);
				boolean ground = j >= 0 && land[j] || landIn(others, x + n[0], z + n[1]);
				low = Math.min(low, !ground ? bottom[i] - 1 : j >= 0 && land[j] ? surf[j] : base);
			}
			if (low > base - 2) {
				continue;
			}
			for (int y = Math.max(low + 1, base - 4); y <= base; y++) {
				world.set(x, y, z, y == base ? bsPol() : ((base - y) % 2 == 0 ? bsBrick() : brick()));
			}
		}
	}

	/**
	 * As {@link #settle(Plan, Plan, int)} over several islands whose land meets (the arrival island and its gate's
	 * rock): each levels what lies on its own land, and a footprint over none of them grows a ledge from the first.
	 */
	public static void settle(Plan world, Plan structure, int base, Terrain... grounds) {
		Set<Long> foot = footprint(structure, base);
		for (int k = 0; k < grounds.length; k++) {
			Terrain t = grounds[k];
			Terrain[] others = new Terrain[grounds.length - 1];
			for (int j = 0, o = 0; j < grounds.length; j++) {
				if (j != k) {
					others[o++] = grounds[j];
				}
			}
			Set<Long> mine = new HashSet<>();
			for (long c : foot) {
				int x = Plan.columnX(c), z = Plan.columnZ(c);
				int i = t.at(x, z);
				boolean own = i >= 0 && t.land[i];
				boolean none = !own && !landIn(others, x, z) && k == 0;
				if (own || none && i >= 0) {
					mine.add(c);
				}
			}
			t.settle(world, mine, base, 2, 5, others);
		}
	}

	/** The columns of land within {@code reach} of a point, as a queue (for tests and dressing). */
	ArrayDeque<int[]> landNear(int x, int z, int reach) {
		ArrayDeque<int[]> out = new ArrayDeque<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int i = at(x + dx, z + dz);
				if (i >= 0 && land[i]) {
					out.add(new int[] {x + dx, surf[i], z + dz});
				}
			}
		}
		return out;
	}
}
