package net.battlehub.arch.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Easing;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Brief;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.mat.Connect;
import net.battlehub.arch.mat.Gradient;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Library;

/**
 * Bed Wars maps from the architecture engine. One island per team round a middle island, diamond islands between
 * the teams, all floating in the theme's sky with landmarks in the distance. Every team island is drawn from the
 * same seed in its own frame (local +z towards the middle), so all teams get exactly the same island, turned or
 * mirrored; the diamond islands and the middle are the same on all four sides.
 *
 * <p>A team island: the spawn on a small square in the team colour, the generator under a forge canopy at the front
 * (cover from arrows, open on every side), the bed at the back on a wool plinth with a gate behind it (every side of
 * the bed stays free to build round), the shopkeeper under a striped market stall on one side, and a lookout
 * platform with a parapet and a stair on the other (high ground over the bridges). Lamps, trees and plants fill
 * the rest, but never the lanes towards the middle, the neighbours and the diamonds.
 */
public final class BedWars {
	private BedWars() {}

	/** Team colours in team order (the game's team order). */
	public static final String[] COLORS = {"red", "blue", "lime", "yellow", "light_blue", "white", "pink", "gray"};
	/** Players may build this far past the team islands. */
	public static final int BUILD_MARGIN = 12;

	// The team island, in its own frame
	static final double ISLE_R = 10.5;
	static final int GEN_Z = 6;
	static final int BED_Z = -5;
	static final int GATE_Z = -8;
	static final int SHOP_X = 5;
	static final int LOOKOUT_X0 = -9;

	/** The boxes of a team island (its own frame) that stay free: name, x0, y0, z0, x1, y1, z1. */
	static final Object[][] TEAM_ZONES = {
			{"spawn", -1, 1, -1, 1, 2, 1},
			{"bed", -3, 1, BED_Z - 3, 3, 4, BED_Z + 2},
			{"gen", -2, 1, GEN_Z - 2, 2, 3, GEN_Z + 2},
			{"shop", SHOP_X, 1, 0, SHOP_X, 2, 0},
			{"lookout", LOOKOUT_X0 + 1, 5, -1, LOOKOUT_X0 + 3, 6, 1}};

	/** What dangles from the underside of the islands in a theme (null: nothing). */
	static String hanging(Dress d) {
		return switch (d.id) {
			case "forest", "castle", "jungle", "cherry", "sky", "mushroom" -> "hanging_roots";
			case "nether" -> "weeping_vines";
			default -> null;
		};
	}

	public static final class Settings {
		public long seed = 1;
		public String theme = "castle";
		public int teams = 4;
		public int buildHeight = 24;
		public int buildDepth = 10;
		public int detail = 2;
		/** Islands and landmarks in the distance (out of play). */
		public boolean backdrop = true;
	}

	/** Distance from the middle to the team islands (more islands, a wider circle). */
	public static int teamRadius(int teams) {
		return teams <= 2 ? 36 : teams <= 4 ? 40 : teams <= 6 ? 48 : 56;
	}

	/** The angles of the team islands: eight teams sit on an octagon turned so every island faces alike. */
	static double[] teamAngles(int n) {
		double[] a = new double[n];
		for (int k = 0; k < n; k++) {
			if (n == 8) {
				a[k] = Math.PI / 8 + k * Math.PI / 4;
			} else {
				double start = n == 2 ? Math.PI : Math.PI / 2;
				a[k] = start + k * 2 * Math.PI / n;
			}
		}
		return a;
	}

	static double[] diamondAngles(int n) {
		int count = Math.min(n, 4);
		double start = n == 2 ? Math.PI : Math.PI / 2;
		double[] a = new double[count];
		for (int k = 0; k < count; k++) a[k] = start + (k + 0.5) * 2 * Math.PI / count;
		return a;
	}

	static double diamondRing(int n) {
		return n <= 4 ? teamRadius(n) * 0.62 : teamRadius(n) * 0.55;
	}

	/**
	 * The frame of an island at (cx, cz): local +z along the axis nearest to the way to the middle, mirrored when
	 * the middle lies to its left, so the middle is always straight ahead or ahead to the right.
	 */
	static Xform frame(int cx, int cz) {
		double vx = -cx, vz = -cz;
		Dir face;
		if (Math.abs(vx) > Math.abs(vz) + 1e-9) {
			face = vx >= 0 ? Dir.EAST : Dir.WEST;
		} else if (Math.abs(vz) > Math.abs(vx) + 1e-9) {
			face = vz >= 0 ? Dir.SOUTH : Dir.NORTH;
		} else {
			face = vx >= 0 ? Dir.EAST : Dir.WEST;
		}
		Xform f = Xform.facing(face, cx, 0, cz);
		double[] l = local(f, vx, vz);
		return l[0] < -1e-6 ? f.mirrored() : f;
	}

	/** A world direction in a frame's local axes {x, z}. */
	static double[] local(Xform f, double wx, double wz) {
		int exX = f.x(1, 0) - f.x(0, 0), exZ = f.z(1, 0) - f.z(0, 0);
		int ezX = f.x(0, 1) - f.x(0, 0), ezZ = f.z(0, 1) - f.z(0, 0);
		return new double[] {wx * exX + wz * exZ, wx * ezX + wz * ezZ};
	}

	public static ArenaPlan generate(Settings s, Library lib) {
		ArenaPlan out = new ArenaPlan();
		Rng root = new Rng(s.seed);
		Dress d = Dress.of(s.theme, s.seed);
		out.dress = d;
		int n = Math.max(2, Math.min(COLORS.length, s.teams));
		int R = teamRadius(n);
		double[] ta = teamAngles(n);
		int[][] teamAt = new int[n][];
		for (int i = 0; i < n; i++) teamAt[i] = new int[] {(int) Math.round(Math.cos(ta[i]) * R), (int) Math.round(Math.sin(ta[i]) * R)};
		double[] da = diamondAngles(n);
		double ring = diamondRing(n);
		int[][] diaAt = new int[da.length][];
		for (int k = 0; k < da.length; k++) diaAt[k] = new int[] {(int) Math.round(Math.cos(da[k]) * ring), (int) Math.round(Math.sin(da[k]) * ring)};

		// The lanes every team island keeps free: towards the middle, its neighbours and its two nearest diamonds,
		// seen from its own frame; the union over all islands, so every island is drawn the same
		List<Double> lanes = new ArrayList<>();
		Set<Long> seen = new TreeSet<>();
		for (int i = 0; i < n; i++) {
			Xform f = frame(teamAt[i][0], teamAt[i][1]);
			List<int[]> targets = new ArrayList<>();
			targets.add(new int[] {0, 0});
			targets.add(teamAt[(i + 1) % n]);
			targets.add(teamAt[(i + n - 1) % n]);
			int[][] byDist = diaAt.clone();
			final int ii = i;
			java.util.Arrays.sort(byDist, (a, b) -> Double.compare(Math.hypot(a[0] - teamAt[ii][0], a[1] - teamAt[ii][1]),
					Math.hypot(b[0] - teamAt[ii][0], b[1] - teamAt[ii][1])));
			for (int k = 0; k < Math.min(2, byDist.length); k++) targets.add(byDist[k]);
			for (int[] t : targets) {
				double[] l = local(f, t[0] - teamAt[i][0], t[1] - teamAt[i][1]);
				double a = Math.atan2(l[0], l[1]);
				if (seen.add(Math.round(Math.toDegrees(a)))) lanes.add(a);
			}
		}

		long isleSeed = root.stream("team-isle").seed();
		for (int i = 0; i < n; i++) {
			Xform f = frame(teamAt[i][0], teamAt[i][1]);
			Building b = teamIsle(d, lib, f, isleSeed, s.detail, COLORS[i], lanes);
			out.buildings.add(b);
			out.isles.add(new ArenaPlan.Isle("team", i, teamAt[i][0], teamAt[i][1], f, ISLE_R));
			int[] sp = world(f, 0, 1, 0);
			double yaw = Math.toDegrees(Math.atan2(-(0 - teamAt[i][0]), 0 - teamAt[i][1]));
			out.spawns.add(new ArenaPlan.Spawn(sp[0] + 0.5, 1, sp[2] + 0.5, (float) yaw));
			out.mark("spawn_" + i, sp[0], 1, sp[2]);
			int[] bed = world(f, 0, 1, BED_Z);
			out.mark("bed_" + i, bed[0], bed[1], bed[2]);
			int[] gen = world(f, 0, 1, GEN_Z);
			out.mark("gen_" + i, gen[0], gen[1], gen[2]);
			int[] shop = world(f, SHOP_X, 1, 0);
			out.mark("shop_" + i, shop[0], shop[1], shop[2]);
			int[] shopBlock = world(f, SHOP_X + 1, 1, 0);
			out.mark("shopblock_" + i, shopBlock[0], shopBlock[1], shopBlock[2]);
			for (Object[] z : TEAM_ZONES) {
				zone(out, z[0] + "_" + i, f, (int) z[1], (int) z[2], (int) z[3], (int) z[4], (int) z[5], (int) z[6]);
			}
		}
		long diaSeed = root.stream("diamond-isle").seed();
		for (int k = 0; k < diaAt.length; k++) {
			Xform f = frame(diaAt[k][0], diaAt[k][1]);
			Building b = diamondIsle(d, lib, f, diaSeed, s.detail);
			out.buildings.add(b);
			out.isles.add(new ArenaPlan.Isle("diamond", k, diaAt[k][0], diaAt[k][1], f, 5.5));
			int[] g = world(f, 0, 1, 0);
			out.mark("gen_diamond", g[0], g[1], g[2]);
			zone(out, "diamond_" + k, f, -1, 1, -1, 1, 3, 1);
		}
		List<Double> middleLanes = new ArrayList<>();
		for (int[] t : teamAt) middleLanes.add(Math.atan2(t[0], t[1]));
		for (int[] t : diaAt) middleLanes.add(Math.atan2(t[0], t[1]));
		Building mid = middle(d, lib, root.stream("middle").seed(), s.detail, middleLanes, teamAt);
		out.buildings.add(mid);
		out.isles.add(new ArenaPlan.Isle("middle", 0, 0, 0, Xform.ID, 12.5));
		for (int sx = -2; sx <= 2; sx += 4) out.mark("gen_emerald", sx, 1, 0);
		zone(out, "emeralds", Xform.ID, -3, 1, -1, 3, 3, 1);

		int edge = R + BUILD_MARGIN;
		out.buildMin = new int[] {-edge, -s.buildDepth, -edge};
		out.buildMax = new int[] {edge, s.buildHeight, edge};
		out.mark("build_min", -edge, -s.buildDepth, -edge);
		out.mark("build_max", edge, s.buildHeight, edge);
		// The winners' podium is built above the middle when the game is over
		out.mark("podium", 0, Math.min(s.buildHeight, 14), 0);
		out.spectator = new double[] {0.5, Math.max(30, s.buildHeight + 8), 0.5, 0, 89};
		out.voidY = -s.buildDepth - 3;
		out.radius = R + 26;

		for (Building b : out.buildings) b.emit(out.voxels);
		if (s.backdrop) {
			Backdrop.bedwars(out, d, lib, root.stream("backdrop").seed(), R, s.buildDepth, s.detail);
		}
		Connect.apply(out.voxels);
		out.log.add("bedwars " + n + " teams, " + d + ", " + out.voxels.size() + " blocks, lanes " + lanes.size());
		return out;
	}

	static int[] world(Xform f, int x, int y, int z) {
		return new int[] {f.x(x, z), f.y(y), f.z(x, z)};
	}

	/** A box given in a frame's local coordinates, kept free in the world. */
	static void zone(ArenaPlan out, String name, Xform f, int x0, int y0, int z0, int x1, int y1, int z1) {
		int ax = f.x(x0, z0), az = f.z(x0, z0), bx = f.x(x1, z1), bz = f.z(x1, z1);
		out.zones.add(new ArenaPlan.Zone(name, Math.min(ax, bx), f.y(y0), Math.min(az, bz), Math.max(ax, bx), f.y(y1), Math.max(az, bz)));
	}

	static Building container(String name, Dress d, Library lib, Xform f, long seed, int detail) {
		Brief br = new Brief("arena:" + name, d.style, seed);
		br.palette = d.palette;
		br.detail = detail;
		br.condition = d.condition;
		Building b = new Building(br, lib, f, null);
		b.floorY = 1;
		return b;
	}

	/** Gradients for the land: rock darker towards the bottom, ground and paving in soft patches. */
	static void earth(Building b, long seed) {
		int minY = Math.min(-1, b.plan.minY());
		b.field(Slot.ROCK, new Gradient.Stack().add(Gradient.vertical(minY, 0, Easing.LINEAR), 1).noise(seed + 3, 0.12, 4));
		b.field(Slot.SOIL, new Gradient.Stack().add(Gradient.noise(seed + 5, 4, 2), 1));
		b.field(Slot.GROUND, new Gradient.Stack().add(Gradient.noise(seed + 4, 5, 2), 1));
		b.field(Slot.PAVING, new Gradient.Stack().add(Gradient.noise(seed + 6, 4, 2), 1));
		b.field(Slot.PATH, new Gradient.Stack().add(Gradient.noise(seed + 7, 3, 2), 1));
	}

	/** True when a local column lies in one of the lanes (five wide, from the centre outwards). */
	static boolean inLane(List<Double> lanes, double x, double z, double from, double half) {
		for (double a : lanes) {
			double ux = Math.sin(a), uz = Math.cos(a);
			double along = x * ux + z * uz;
			double lateral = Math.abs(x * uz - z * ux);
			if (along >= from && lateral <= half) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ the team island

	static Building teamIsle(Dress d, Library lib, Xform f, long seed, int detail, String colour, List<Double> lanes) {
		Building b = container("team", d, lib, f, seed, detail);
		Rng rng = new Rng(seed);
		Ctx c = new Ctx(b, Xform.ID, rng.stream("isle"), rng.stream("decor"));
		Isle land = new Isle(seed, ISLE_R, 0.06, 0.55, Isle.Symmetry.NONE);
		land.draw(c, hanging(d));
		Cell paving = Cell.of(Slot.PAVING, Cell.DETAIL).flag(Cell.KEEP);
		Cell path = Cell.of(Slot.PATH, Cell.DETAIL).flag(Cell.KEEP);
		// the spawn square in the team colour
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				double r = Math.hypot(x, z);
				if (r <= 2.6) c.set(x, 0, z, paving);
				else if (r <= 3.5) c.set(x, 0, z, Kit.team(colour, "concrete", Cell.DETAIL));
			}
		}
		c.set(0, 0, 0, Kit.team(colour, "concrete", Cell.FEATURE));
		// paths to the generator, the bed, the stall and the lookout's stair
		for (int x = -1; x <= 1; x++) {
			c.set(x, 0, 4, path);
			c.set(x, 0, BED_Z + 2, path);
		}
		for (int z = -1; z <= 1; z++) c.set(3, 0, z, path);
		c.set(-3, 0, 3, path);
		c.set(-4, 0, 3, path);
		// the generator: iron round a gold heart, paved round
		for (int x = -2; x <= 2; x++) {
			for (int z = GEN_Z - 2; z <= GEN_Z + 2; z++) {
				boolean pad = Math.abs(x) <= 1 && Math.abs(z - GEN_Z) <= 1;
				c.set(x, 0, z, pad ? Cell.literal(x == 0 && z == GEN_Z ? "gold_block" : "iron_block", Cell.FEATURE) : paving);
			}
		}
		Kit.forge(c, d, GEN_Z);
		// the bed on its wool plinth, foot towards the spawn, head towards the void; the gate behind
		for (int x = -1; x <= 1; x++) {
			for (int z = BED_Z - 2; z <= BED_Z + 1; z++) c.set(x, 0, z, Kit.team(colour, "wool", Cell.FEATURE));
		}
		c.set(0, 1, BED_Z, Kit.team(colour, "bed[facing=north,part=foot]", Cell.FEATURE));
		c.set(0, 1, BED_Z - 1, Kit.team(colour, "bed[facing=north,part=head]", Cell.FEATURE));
		Kit.bedGate(c, d, colour, GATE_Z);
		// the shopkeeper's stall (the barrel is the shop block) and the lookout
		Kit.stall(c, d, colour, SHOP_X);
		c.set(SHOP_X + 1, 1, 0, Cell.literal("barrel[facing=west]", Cell.FEATURE));
		Kit.lookout(c, d, colour, LOOKOUT_X0);
		// lamps at the back corners of the square
		Kit.post(c, d, -3, 1, -2, 3);
		Kit.post(c, d, 3, 1, -2, 3);
		// trees (or the theme's scenery) in the back corners, more where the lanes leave room
		Rng decor = rng.stream("trees");
		for (int sx = -6; sx <= 6; sx += 12) {
			if (land.land(sx, -7) && !inLane(lanes, sx, -7, 1.5, 2.5)) Kit.tree(c, d, decor, sx, 1, -7, 4 + decor.nextInt(2));
		}
		for (int[] p : new int[][] {{7, 7}, {-7, 7}, {8, -4}, {-8, -5}}) {
			if (land.land(p[0], p[1]) && Math.hypot(p[0], p[1]) < land.edge(p[0], p[1]) - 1.5 && !inLane(lanes, p[0], p[1], 1.5, 2.6)
					&& free(c, p[0], p[1], 1)) {
				Kit.feature(c, d, decor, p[0], 1, p[1], 1);
			}
		}
		plants(c, d, land, rng.stream("plants"), lanes);
		for (Object[] z : TEAM_ZONES) carve(c, (int) z[1], (int) z[2], (int) z[3], (int) z[4], (int) z[5], (int) z[6]);
		earth(b, seed);
		return b;
	}

	/** Takes leaves, plants and decoration out of a box that must stay free (a crown that grew into it). */
	static void carve(Ctx c, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					Cell k = c.get(x, y, z);
					if (k == null || k.air()) continue;
					if (k.slot() == Slot.LEAVES || k.is(Cell.DECOR) || k.slot() == Slot.LITERAL && k.prio() <= Cell.DETAIL) c.remove(x, y, z);
				}
			}
		}
	}

	/** Nothing planned above the ground in a square round (x, z). */
	static boolean free(Ctx c, int x, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int y = 1; y <= 4; y++) {
					if (c.filled(x + dx, y, z + dz)) return false;
				}
			}
		}
		return true;
	}

	/** Ground cover on open grass (never on paving, never where something stands). */
	static void plants(Ctx c, Dress d, Isle land, Rng r, List<Double> lanes) {
		if (d.plants.isEmpty() && d.flowers.isEmpty()) return;
		int reach = land.reach();
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (!land.land(x, z)) continue;
				Cell g = c.get(x, 0, z);
				if (g == null || g.slot() != Slot.GROUND || c.filled(x, 1, z) || c.get(x, 1, z) != null) continue;
				double roll = r.next();
				if (roll < 0.03 && !d.flowers.isEmpty() && !inLane(lanes, x, z, 3, 1.5)) {
					c.set(x, 1, z, Cell.literal(r.pick(d.flowers), Cell.DETAIL));
				} else if (roll < 0.11 && !d.plants.isEmpty()) {
					c.set(x, 1, z, Cell.literal(r.pick(d.plants), Cell.DETAIL));
				}
			}
		}
	}

	// ------------------------------------------------------------------ diamond islands and the middle

	static Building diamondIsle(Dress d, Library lib, Xform f, long seed, int detail) {
		Building b = container("diamond", d, lib, f, seed, detail);
		Rng rng = new Rng(seed);
		Ctx c = new Ctx(b, Xform.ID, rng.stream("isle"), rng.stream("decor"));
		Isle land = new Isle(seed, 5.6, 0.05, 0.7, Isle.Symmetry.FOUR);
		land.draw(c, hanging(d));
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				if (Math.hypot(x, z) <= 3.3) c.set(x, 0, z, Cell.of(Slot.PAVING, Cell.DETAIL).flag(Cell.KEEP));
			}
		}
		c.set(0, 0, 0, Cell.literal("diamond_block", Cell.FEATURE));
		Kit.pavilion(c, d);
		for (int[] p : new int[][] {{4, 1}, {4, -1}, {-4, 1}, {-4, -1}, {1, 4}, {-1, 4}, {1, -4}, {-1, -4}}) {
			if (land.land(p[0], p[1])) c.set(p[0], 1, p[1], Cell.slab(Slot.RUBBLE, false, Cell.DETAIL));
		}
		earth(b, seed);
		return b;
	}

	static Building middle(Dress d, Library lib, long seed, int detail, List<Double> lanes, int[][] teamAt) {
		Building b = container("middle", d, lib, Xform.ID, seed, detail);
		Rng rng = new Rng(seed);
		Ctx c = new Ctx(b, Xform.ID, rng.stream("isle"), rng.stream("decor"));
		Isle land = new Isle(seed, 12.5, 0.05, 0.6, Isle.Symmetry.FOUR);
		land.draw(c, hanging(d));
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				double r = Math.hypot(x, z);
				boolean axis = (x == 0 || z == 0) && r >= 4 && r <= 8.6;
				if (r <= 8.6) c.set(x, 0, z, Cell.of(axis || r >= 3.0 && r <= 3.8 ? Slot.FLOOR_ALT : Slot.PAVING, Cell.DETAIL).flag(Cell.KEEP));
				else if (r <= 9.6) c.set(x, 0, z, Cell.of(Slot.PATH, Cell.DETAIL).flag(Cell.KEEP));
			}
		}
		for (int sx = -2; sx <= 2; sx += 4) c.set(sx, 0, 0, Cell.literal("emerald_block", Cell.FEATURE));
		Kit.tholos(c, d);
		// a banner of each team on the ring, facing its island
		for (int i = 0; i < teamAt.length; i++) {
			double len = Math.hypot(teamAt[i][0], teamAt[i][1]);
			double ux = teamAt[i][0] / len, uz = teamAt[i][1] / len;
			Dir face = Dir.of((int) Math.round(ux * 10), (int) Math.round(uz * 10));
			for (double r = 8.0; r <= 9.5; r += 0.5) {
				int x = (int) Math.round(ux * r), z = (int) Math.round(uz * r);
				Cell behind = c.get(x - face.dx, 7, z - face.dz);
				if (c.get(x, 7, z) == null && behind != null && behind.slot() == Slot.BAND) {
					c.set(x, 7, z, Kit.team(COLORS[i], "wall_banner[facing=" + face.id() + "]", Cell.DETAIL));
					break;
				}
			}
		}
		// scenery on the rim, out of every lane
		Rng decor = rng.stream("rim");
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8 + Math.PI / 16;
			int x = (int) Math.round(Math.sin(a) * 10.4), z = (int) Math.round(Math.cos(a) * 10.4);
			if (!land.land(x, z) || Math.hypot(x, z) > land.edge(x, z) - 1.0 || inLane(lanes, x, z, 2, 3.2) || !free(c, x, z, 1)) continue;
			if (k % 2 == 0) Kit.tree(c, d, decor, x, 1, z, 4 + decor.nextInt(2));
			else Kit.post(c, d, x, 1, z, 3);
		}
		plants(c, d, land, rng.stream("plants"), lanes);
		earth(b, seed);
		return b;
	}
}
