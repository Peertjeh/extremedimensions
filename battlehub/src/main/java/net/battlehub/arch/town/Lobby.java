package net.battlehub.arch.town;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Easing;
import net.battlehub.arch.Plan;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Architect;
import net.battlehub.arch.bldg.Brief;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.bldg.Style;
import net.battlehub.arch.mat.Connect;
import net.battlehub.arch.mat.Gradient;
import net.battlehub.arch.mat.MaterialContext;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mat.Probe;
import net.battlehub.arch.mat.Resolver;
import net.battlehub.arch.mod.Attachment;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Library;
import net.battlehub.arch.mod.Spec;

/**
 * The lobby as a settlement on a floating island, planned top-down: the plaza at the heart (the leaderboards,
 * the lobby characters and the spawn are there), four avenues out to four districts, each anchored by a
 * landmark that faces the plaza (a cathedral to the north, seen from the spawn through the north gate; a castle on
 * a hill to the east; a temple ruin to the south; a mage tower to the west), town blocks with houses facing the
 * streets and gardens inside, and open country with trees towards the cliffs. Districts have their own palettes;
 * houses between two districts take after the nearer one, so the city changes gradually.
 */
public final class Lobby {
	private Lobby() {}

	/** What the lobby generator needs to know. */
	public static final class Settings {
		public long seed = 20261017L;
		public int radius = 150;
		public int detail = 2;
		/** 0..1: how full the town blocks are. */
		public double density = 0.85;
	}

	/** A landmark: its archetype, the avenue it ends, how far out and how high its terrace stands. */
	record Site(String archetype, int dir, int distance, int pad, double scale) {}

	public static final class Result {
		public final Voxels voxels = new Voxels(1 << 20);
		public final List<Building> buildings = new ArrayList<>();
		public final Map<String, Building> landmarks = new LinkedHashMap<>();
		public Terrain terrain;
		public final List<String> log = new ArrayList<>();
		public int houses;
	}

	static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	static final Dir[] FRONT = {Dir.SOUTH, Dir.WEST, Dir.NORTH, Dir.EAST};
	static final int AVENUE = 3;
	static final int[] STREETS = {40, 64, 88};

	public static Result generate(Settings s, Library lib) {
		Result out = new Result();
		Rng root = new Rng(s.seed);
		Palette dim = Regions.dimension();
		Palette[] districts = Regions.districts(dim);
		List<Site> sites = List.of(new Site("cathedral", 0, 62, 2, 1.0), new Site("castle", 1, 66, 7, 1.1), new Site("temple_ruin", 2, 58, 1, 1.0),
				new Site("mage_tower", 3, 56, 4, 1.0));
		Terrain t = Terrain.island(s.seed, s.radius, 44, (x, z) -> lift(sites, x, z));
		out.terrain = t;
		Occupancy occ = new Occupancy(s.radius + 2);
		occ.disc(0, 0, Plaza.RADIUS + 4);
		// The scenery and the roads live in plain "buildings" in world coordinates
		Building infra = container("infra", s, lib, districts[0], t, root.stream("infra").seed());
		Building scenery = container("scenery", s, lib, dim, t, root.stream("scenery").seed());
		Ctx road = new Ctx(infra, Xform.ID, root.stream("roads"), root.stream("decor:roads"));
		// Landmarks first (they decide where the avenues end), on levelled terraces
		for (Site site : sites) {
			Building b = landmark(site, s, lib, districts[site.dir()], t, occ, root, out);
			out.landmarks.put(site.archetype(), b);
		}
		// Avenues from the plaza gates to the landmarks
		int[][] avenueHeights = new int[4][];
		for (Site site : sites) avenueHeights[site.dir()] = avenue(road, t, occ, site);
		// Streets of the town grid and houses along streets and avenues
		Rng plots = root.stream("plots");
		for (int line : STREETS) {
			for (int sign = -1; sign <= 1; sign += 2) {
				street(road, t, occ, true, sign * line, s.radius);
				street(road, t, occ, false, sign * line, s.radius);
			}
		}
		for (Site site : sites) lineAvenue(site, s, lib, districts, t, occ, plots, out);
		for (int line : STREETS) {
			for (int sign = -1; sign <= 1; sign += 2) {
				lineStreet(true, sign * line, s, lib, districts, t, occ, plots, out);
				lineStreet(false, sign * line, s, lib, districts, t, occ, plots, out);
			}
		}
		// Watchtowers on the cliffs between the districts
		for (int q = 0; q < 4; q++) {
			double a = Math.PI / 4 + q * Math.PI / 2 + plots.range(-0.2, 0.2);
			tower(a, s, lib, districts[q], t, occ, plots, out);
		}
		scenery(scenery, t, occ, root.stream("trees"), s);
		skyIslands(scenery, root.stream("islands"), s.radius);
		// Emit: terrain, roads, the plaza, buildings, scenery; then let walls, panes and stairs join up
		terrain(out.voxels, t, dim, s.seed);
		infra.emit(out.voxels);
		Plan plaza = new Plan(1 << 14);
		Plaza.draw(plaza, s.seed);
		emitLiteral(plaza, out.voxels);
		for (Building b : out.buildings) b.emit(out.voxels);
		scenery.emit(out.voxels);
		Connect.apply(out.voxels);
		out.log.add("lobby: " + out.buildings.size() + " buildings (" + out.houses + " houses), " + out.voxels.size() + " blocks");
		return out;
	}

	/** Raised ground round each landmark (the castle's hill, the other terraces). */
	static double lift(List<Site> sites, int x, int z) {
		double best = 0;
		for (Site s : sites) {
			double cx = DIRS[s.dir()][0] * (s.distance() + 22), cz = DIRS[s.dir()][1] * (s.distance() + 22);
			double d = Math.hypot(x - cx, z - cz);
			double r = s.archetype().equals("castle") ? 34 : 28;
			double f = 1 - Easing.SMOOTH.apply((d - r) / 26.0);
			best = Math.max(best, s.pad() * f + (s.archetype().equals("castle") ? 2 * f : 0));
		}
		return best;
	}

	static Building container(String name, Settings s, Library lib, Palette palette, Terrain t, long seed) {
		Brief br = new Brief("scenery", Style.TOWN, seed);
		br.palette = palette;
		br.detail = s.detail;
		Building b = new Building(br, lib, Xform.ID, t::height);
		b.floorY = 0;
		return b;
	}

	/** Builds a landmark: once to learn its size, then on a levelled terrace with the foundations to the ground. */
	static Building landmark(Site site, Settings s, Library lib, Palette district, Terrain t, Occupancy occ, Rng root, Result out) {
		long seed = root.stream("landmark:" + site.archetype()).seed();
		Brief br = Architect.brief(site.archetype(), seed, district, site.scale());
		br.detail = s.detail;
		int fx = DIRS[site.dir()][0] * site.distance(), fz = DIRS[site.dir()][1] * site.distance();
		Xform frame = Xform.facing(FRONT[site.dir()], fx, site.pad(), fz);
		Building probe = Architect.build(br, lib, frame, null);
		int[] box = probe.worldBox();
		t.level(box[0] - 1, box[2] - 1, box[3] + 1, box[5] + 1, site.pad() - 1, 6);
		occ.rect(box[0] - 3, box[2] - 3, box[3] + 3, box[5] + 3);
		Brief again = Architect.brief(site.archetype(), seed, district, site.scale());
		again.detail = s.detail;
		Building b = Architect.build(again, lib, frame, t::height);
		out.buildings.add(b);
		out.log.add(site.archetype() + ": " + again + " at " + fx + "," + fz + " palette " + again.palette.name());
		return b;
	}

	/**
	 * An avenue from the plaza gate to a landmark: seven wide, paved, rising with stairs where the ground rises,
	 * curbs and lamp posts along it. Returns the road height at every step out from the plaza.
	 */
	static int[] avenue(Ctx c, Terrain t, Occupancy occ, Site site) {
		int start = Plaza.RADIUS + 2, end = site.distance();
		int[] h = new int[end + 2];
		for (int s = 0; s <= end + 1; s++) {
			double f = Easing.SMOOTH.apply((s - (start + 4)) / (double) Math.max(1, end - 6 - (start + 4)));
			h[s] = (int) Math.round(site.pad() * f);
		}
		int[] d = DIRS[site.dir()];
		Dir out = Dir.of(d[0], d[1]);
		for (int s = start; s <= end; s++) {
			for (int l = -AVENUE - 2; l <= AVENUE + 2; l++) {
				int x = d[0] * s - d[1] * l, z = d[1] * s + d[0] * l;
				occ.mark(x, z);
				if (!t.land(x, z)) continue;
				t.set(x, z, h[s]);
				t.markBuilt(x, z);
				int al = Math.abs(l);
				if (al <= AVENUE) {
					boolean stepUp = s < end && h[s + 1] > h[s];
					c.set(x, h[s], z, Cell.of(al <= 1 ? Slot.PATH : Slot.PAVING, Cell.DETAIL).flag(Cell.KEEP));
					if (stepUp) c.set(x, h[s] + 1, z, Cell.stairs(Slot.PATH, out, false, Cell.DETAIL).flag(Cell.KEEP));
				} else if (al == AVENUE + 1) {
					c.set(x, h[s], z, Cell.of(Slot.FOUNDATION, Cell.DETAIL));
					c.set(x, h[s] + 1, z, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
					if (s % 12 == 6) c.place("orn.lamppost", Xform.at(x, h[s] + 1, z), Spec.of(1, 3, 1).with("height", 3));
				} else {
					c.set(x, h[s], z, Cell.of(Slot.PAVING, Cell.DETAIL));
				}
				for (int y = h[s] + 1; y <= h[s] + 4; y++) {
					if (al <= AVENUE) c.set(x, y + (al <= AVENUE && s < end && h[s + 1] > h[s] ? 1 : 0), z, Cell.air(Cell.FILL));
				}
			}
		}
		return h;
	}

	/** A town street five wide along x = at (alongZ) or z = at, at the ground's height, with stairs where it climbs. */
	static void street(Ctx c, Terrain t, Occupancy occ, boolean alongZ, int at, int radius) {
		int lo = -radius, hi = radius;
		int prev = Integer.MIN_VALUE;
		for (int s = lo; s <= hi; s++) {
			int cx = alongZ ? at : s, cz = alongZ ? s : at;
			if (Math.hypot(cx, cz) < Plaza.RADIUS + 6 || Math.hypot(cx, cz) > radius * 0.68 || !t.land(cx, cz)) {
				prev = Integer.MIN_VALUE;
				continue;
			}
			if (occ.used(cx, cz) && !occ.road(cx, cz)) {
				prev = Integer.MIN_VALUE;
				continue;
			}
			int h = t.height(cx, cz);
			if (occ.road(cx, cz)) h = t.height(cx, cz);
			if (prev != Integer.MIN_VALUE && Math.abs(h - prev) > 1) h = prev + (h > prev ? 1 : -1);
			Dir along = alongZ ? Dir.SOUTH : Dir.EAST;
			for (int l = -2; l <= 2; l++) {
				int x = alongZ ? at + l : s, z = alongZ ? s : at + l;
				if (!t.land(x, z) || occ.road(x, z) && !occ.street(x, z)) continue;
				t.set(x, z, h);
				t.markBuilt(x, z);
				occ.street(x, z, true);
				c.set(x, h, z, Cell.of(Math.abs(l) == 2 ? Slot.PAVING : Slot.PATH, Cell.DETAIL).flag(Cell.KEEP));
				for (int y = h + 1; y <= h + 4; y++) c.set(x, y, z, Cell.air(Cell.FILL));
				if (prev != Integer.MIN_VALUE && h > prev) {
					// the street climbs: stairs on the step before
					int px = alongZ ? x : s - 1, pz = alongZ ? s - 1 : z;
					c.set(px, prev + 1, pz, Cell.stairs(Slot.PATH, along, false, Cell.DETAIL).flag(Cell.KEEP));
				} else if (prev != Integer.MIN_VALUE && h < prev) {
					c.set(x, h + 1, z, Cell.stairs(Slot.PATH, along.back(), false, Cell.DETAIL).flag(Cell.KEEP));
				}
			}
			prev = h;
		}
	}

	/** Houses lining both sides of an avenue, facing it. */
	static void lineAvenue(Site site, Settings s, Library lib, Palette[] districts, Terrain t, Occupancy occ, Rng rng, Result out) {
		int[] d = DIRS[site.dir()];
		for (int side = -1; side <= 1; side += 2) {
			int along = Plaza.RADIUS + 8;
			while (along < site.distance() - 10) {
				int w = rng.range(6, 9);
				int lat = side * (AVENUE + 3);
				int fx = d[0] * (along + w / 2) - d[1] * lat, fz = d[1] * (along + w / 2) + d[0] * lat;
				Dir front = Dir.of(-d[1] * -side, -d[0] * side);
				// front faces the avenue: towards -lateral
				front = lateralFacing(d, -side);
				if (rng.next() < s.density && house(fx, fz, front, s, lib, districts, t, occ, rng, out, w)) {
					// mostly in rows, now and then an alley
					along += w + (rng.chance(0.25) ? rng.range(2, 3) : 0);
				} else {
					along += 2;
				}
			}
		}
	}

	/** The direction of the lateral axis of an avenue in direction d ({@code sign} 1 = to its right). */
	static Dir lateralFacing(int[] d, int sign) {
		return Dir.of(-d[1] * sign, d[0] * sign);
	}

	/** Houses along both sides of a grid street. */
	static void lineStreet(boolean alongZ, int at, Settings s, Library lib, Palette[] districts, Terrain t, Occupancy occ, Rng rng, Result out) {
		int r = (int) (s.radius * 0.68);
		for (int side = -1; side <= 1; side += 2) {
			int pos = -r;
			while (pos < r) {
				int w = rng.range(6, 9);
				int fx = alongZ ? at + side * 4 : pos + w / 2, fz = alongZ ? pos + w / 2 : at + side * 4;
				Dir front = alongZ ? (side > 0 ? Dir.WEST : Dir.EAST) : (side > 0 ? Dir.NORTH : Dir.SOUTH);
				if (rng.next() < s.density && house(fx, fz, front, s, lib, districts, t, occ, rng, out, w)) {
					pos += w + (rng.chance(0.25) ? rng.range(2, 3) : 0);
				} else {
					pos += 2;
				}
			}
		}
	}

	/**
	 * A house (now and then a market hall, chapel or watchtower) whose front middle is at fx, fz facing {@code front},
	 * if its plot is free, on land and not too steep. The district palette comes from the nearer of the two
	 * districts on either side, by angle.
	 */
	static boolean house(int fx, int fz, Dir front, Settings s, Library lib, Palette[] districts, Terrain t, Occupancy occ, Rng rng, Result out, int w) {
		double rr = Math.hypot(fx, fz);
		if (rr < Plaza.RADIUS + 8 || rr > s.radius * 0.7) return false;
		String arch = "townhouse";
		double roll = rng.next();
		if (roll < 0.04) arch = "market_hall";
		else if (roll < 0.08) arch = "chapel";
		long seed = rng.stream("house", fx * 31 + fz).seed();
		Palette district = districtFor(districts, fx, fz, rng);
		Brief br = Architect.brief(arch, seed, district, 1.0);
		if (arch.equals("townhouse")) br.size(w - 1, br.depth, br.height);
		br.detail = s.detail;
		// The plot in world coordinates
		int width = br.width + 2, depth = br.depth + (arch.equals("chapel") ? 6 : 2);
		int[] rect = plot(fx, fz, front, width, depth);
		if (!occ.free(rect[0], rect[1], rect[2], rect[3])) return false;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int x = rect[0]; x <= rect[2]; x++) {
			for (int z = rect[1]; z <= rect[3]; z++) {
				int h = t.height(x, z);
				if (h == Terrain.NONE) return false;
				lo = Math.min(lo, h);
				hi = Math.max(hi, h);
			}
		}
		if (hi - lo > 5) return false;
		int floor = streetHeight(t, fx, fz, front);
		t.level(rect[0], rect[1], rect[2], rect[3], floor - 1, 2);
		occ.rect(rect[0], rect[1], rect[2], rect[3]);
		Xform frame = Xform.facing(front, fx, floor, fz);
		Building b = Architect.build(br, lib, frame, t::height);
		out.buildings.add(b);
		out.houses++;
		return true;
	}

	/** The height of the street in front of a plot. */
	static int streetHeight(Terrain t, int fx, int fz, Dir front) {
		int h = t.height(fx + front.dx * 2, fz + front.dz * 2);
		return (h == Terrain.NONE ? t.height(fx, fz) : h) + 1;
	}

	/** The world rectangle {x0, z0, x1, z1} of a plot whose front middle is (fx, fz). */
	static int[] plot(int fx, int fz, Dir front, int width, int depth) {
		int hw = width / 2;
		// local: x across -hw..hw, z from 0 (front) back to -(depth-1)
		Xform f = Xform.facing(front, fx, 0, fz);
		int ax = f.x(-hw, 0), az = f.z(-hw, 0), bx = f.x(hw, -(depth - 1)), bz = f.z(hw, -(depth - 1));
		return new int[] {Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
	}

	/** The district whose palette a building at (x, z) takes: the nearer by angle, more likely the closer. */
	static Palette districtFor(Palette[] districts, int x, int z, Rng rng) {
		double a = Math.atan2(x, -z);
		if (a < 0) a += 2 * Math.PI;
		double q = a / (Math.PI / 2);
		int i0 = (int) Math.floor(q) % 4;
		int i1 = (i0 + 1) % 4;
		double f = q - Math.floor(q);
		return rng.next() < Easing.SMOOTH.apply(f) ? districts[i1] : districts[i0];
	}

	static void tower(double a, Settings s, Library lib, Palette district, Terrain t, Occupancy occ, Rng rng, Result out) {
		for (double r = s.radius * 0.8; r > s.radius * 0.6; r -= 3) {
			int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
			if (!t.land(x, z) || !occ.free(x - 6, z - 6, x + 6, z + 6)) continue;
			Brief br = Architect.brief("watchtower", rng.stream("tower", x * 7 + z).seed(), district, 1.2);
			br.detail = s.detail;
			int h = t.height(x, z);
			t.level(x - 5, z - 10, x + 5, z + 1, h, 3);
			occ.rect(x - 6, z - 11, x + 6, z + 2);
			Building b = Architect.build(br, lib, Xform.facing(Dir.of((int) -Math.signum(Math.round(Math.cos(a))), 0), x, h + 1, z), t::height);
			out.buildings.add(b);
			return;
		}
	}

	/** Trees, bushes and flowers on free land; denser towards the coast, gardens inside the town blocks. */
	static void scenery(Building sc, Terrain t, Occupancy occ, Rng rng, Settings s) {
		Ctx c = new Ctx(sc, Xform.ID, rng, rng.stream("decor"));
		net.battlehub.arch.Noise3 forest = new net.battlehub.arch.Noise3(rng.seed());
		String[] flowers = {"short_grass", "short_grass", "short_grass", "fern", "poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy"};
		for (int x = -s.radius; x <= s.radius; x++) {
			for (int z = -s.radius; z <= s.radius; z++) {
				if (!t.land(x, z) || t.built(x, z) || occ.used(x, z)) continue;
				int h = t.height(x, z);
				double d = Math.hypot(x, z);
				double wood = forest.even(x, 0, z, 24, 2) * Easing.norm(d, 50, s.radius * 0.8);
				double roll = Rng.hash(rng.seed(), x, 0, z);
				if (roll < 0.004 + wood * 0.035 && occ.free(x - 2, z - 2, x + 2, z + 2)) {
					c.place("orn.tree", Xform.at(x, h + 1, z), Spec.of(1, 5, 1).with("height", 4 + (int) (roll * 1000) % 3).with("crown", 2.2 + roll * 30 % 1));
					occ.rect(x - 1, z - 1, x + 1, z + 1);
				} else if (roll > 0.82) {
					c.set(x, h + 1, z, Cell.literal(flowers[(int) (roll * 1000) % flowers.length], Cell.DETAIL));
				}
			}
		}
	}

	/** Small islands floating round the main one, each with a tree (meant to float). */
	static void skyIslands(Building sc, Rng rng, int radius) {
		Ctx c = new Ctx(sc, Xform.ID, rng, rng.stream("decor"));
		for (int i = 0; i < 7; i++) {
			double a = i * 2 * Math.PI / 7 + rng.range(-0.3, 0.3);
			double d = radius + rng.range(14, 30);
			int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
			int y = rng.range(-8, 18);
			double r = rng.range(3, 6);
			for (int dx = -7; dx <= 7; dx++) {
				for (int dz = -7; dz <= 7; dz++) {
					double dd = Math.hypot(dx, dz);
					if (dd > r) continue;
					int depth = (int) Math.round((r - dd) * 1.4) + 1;
					for (int k = 0; k < depth; k++) {
						Slot slot = k == 0 ? Slot.GROUND : k < 2 ? Slot.SOIL : Slot.ROCK;
						c.set(x + dx, y - k, z + dz, Cell.of(slot, Cell.BODY).flag(Cell.FLOAT));
					}
				}
			}
			c.place("orn.tree", Xform.at(x, y + 1, z), Spec.of(1, 5, 1).with("height", rng.range(3, 5)).with("crown", 2.0));
		}
	}

	/** The island's land as a shell: grass on top, soil under it, rock where the sides and underside show. */
	static void terrain(Voxels v, Terrain t, Palette dim, long seed) {
		MaterialContext mc = new MaterialContext(seed, dim);
		int minB = Integer.MAX_VALUE;
		for (int b : t.bottom) minB = Math.min(minB, b);
		mc.field(Slot.ROCK, new Gradient.Stack().add(Gradient.vertical(minB, 2, Easing.LINEAR), 1).noise(seed + 3, 0.12, 6));
		mc.field(Slot.GROUND, new Gradient.Stack().add(Gradient.noise(seed + 4, 14, 2), 1));
		mc.field(Slot.SOIL, new Gradient.Stack().add(Gradient.noise(seed + 5, 6, 2), 1));
		Probe p = new Probe();
		Cell ground = Cell.of(Slot.GROUND), soil = Cell.of(Slot.SOIL), rock = Cell.of(Slot.ROCK), wall = Cell.of(Slot.FOUNDATION);
		for (int x = t.minX; x < t.minX + t.size; x++) {
			for (int z = t.minZ; z < t.minZ + t.size; z++) {
				int h = t.height(x, z);
				if (h == Terrain.NONE) continue;
				int b = t.bottom(x, z);
				for (int y = h; y >= b; y--) {
					boolean show = y >= h - 3 || y == b;
					if (!show) {
						for (Dir d : Dir.values()) {
							int nh = t.height(x + d.dx, z + d.dz);
							if (nh == Terrain.NONE || y > nh || y < t.bottom(x + d.dx, z + d.dz)) {
								show = true;
								break;
							}
						}
					}
					if (!show) continue;
					Cell c = y == h ? ground : y >= h - 3 ? soil : rock;
					// where the land stands over a road or a building plot, its face is a masonry retaining wall
					if (y < h && retaining(t, x, y, z)) c = wall;
					p.set(x, y, z, x, y, z, c);
					v.set(x, y, z, Resolver.resolve(c, p, mc));
				}
			}
		}
	}

	/** A face of land that looks out over a built column lower than it (a road, a plot, the plaza). */
	static boolean retaining(Terrain t, int x, int y, int z) {
		for (Dir d : Dir.values()) {
			int nx = x + d.dx, nz = z + d.dz;
			if (!t.built(nx, nz) && Math.hypot(nx, nz) > Plaza.RADIUS + 1) continue;
			int nh = t.height(nx, nz);
			if (nh != Terrain.NONE && y > nh) return true;
		}
		return false;
	}

	static void emitLiteral(Plan plan, Voxels v) {
		plan.forEach((x, y, z, c) -> v.set(x, y, z, c.air() ? "air" : c.literal()));
	}

	/** Where the entrances of the landmarks are (world coordinates), for checks. */
	public static Map<String, Attachment> entrances(Result r) {
		Map<String, Attachment> out = new LinkedHashMap<>();
		for (Map.Entry<String, Building> e : r.landmarks.entrySet()) {
			for (Attachment a : e.getValue().attachments(Attachment.Kind.ENTRANCE)) {
				out.putIfAbsent(e.getKey(), a.in(e.getValue().frame));
			}
		}
		return out;
	}
}
