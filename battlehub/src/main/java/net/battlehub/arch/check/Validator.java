package net.battlehub.arch.check;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.LongIntMap;
import net.battlehub.arch.Plan;
import net.battlehub.arch.Props;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.bldg.Volume;
import net.battlehub.arch.mat.Catalog;
import net.battlehub.arch.mat.Material;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mat.Ramp;
import net.battlehub.arch.mod.Attachment;
import net.battlehub.core.GridPath;

/**
 * Checks a generated building (and the lobby) before it is accepted: geometry (size, budget, directions), block
 * names, palettes (ordered ramps without big jumps), structure (nothing hangs in the air unless meant to),
 * entrances (clear and reachable from outside), stairs (a walkable way from bottom to top), roofs (no gaps
 * over the walls), and composition metrics (height variation, silhouette, material mix).
 */
public final class Validator {
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

	private Validator() {}

	public static final int BUDGET = 400_000;

	public static Report building(Building b) {
		Report r = new Report();
		geometry(b, r);
		structure(b, r);
		entrances(b, r);
		stairs(b, r);
		roofs(b, r);
		palette(b.brief.palette, r);
		composition(b, r);
		return r;
	}

	static void geometry(Building b, Report r) {
		Plan p = b.plan;
		if (p.isEmpty()) {
			r.error("empty", b.brief.archetype + " drew nothing");
			return;
		}
		if (p.size() > BUDGET) r.error("budget", p.size() + " cells, over the budget of " + BUDGET);
		int[] bad = {0, 0};
		p.forEach((x, y, z, c) -> {
			if ((c.shape() == Shape.STAIRS || c.shape() == Shape.DOOR) && c.facing() == null) bad[0]++;
			if (c.slot() == Slot.LITERAL && Catalog.kind(Props.base(c.literal())) == null) bad[1]++;
		});
		if (bad[0] > 0) r.error("direction", bad[0] + " stairs or doors without a direction");
		if (bad[1] > 0) r.warn("literal", bad[1] + " decoration blocks the catalog does not know");
		r.metric("cells", p.size());
	}

	/** Solid cells that do not connect to the ground (through solid neighbours) and are not meant to float. */
	static void structure(Building b, Report r) {
		int floating = floating(b);
		r.metric("floating", floating);
		if (floating > 0) r.warn("floating", floating + " blocks hang in the air");
	}

	public static int floating(Building b) {
		Plan p = b.plan;
		LongIntMap seen = new LongIntMap(p.size());
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		for (long k : p.positions()) {
			int x = Plan.kx(k), y = Plan.ky(k), z = Plan.kz(k);
			Cell c = p.get(x, y, z);
			if (c == null || c.air()) continue;
			boolean grounded = y <= b.floorY - 1 || c.is(Cell.FLOAT);
			if (!grounded) {
				int g = b.groundAt(x, z);
				grounded = g != Integer.MIN_VALUE && y <= g + 1;
			}
			if (grounded) {
				seen.put(k, 1);
				queue.add(new int[] {x, y, z});
			}
		}
		int[][] dirs = NEIGHBOURS;
		while (!queue.isEmpty()) {
			int[] q = queue.poll();
			for (int[] d : dirs) {
				int x = q[0] + d[0], y = q[1] + d[1], z = q[2] + d[2];
				long k = Plan.key(x, y, z);
				if (seen.contains(k)) continue;
				Cell c = p.get(x, y, z);
				if (c == null || c.air()) continue;
				seen.put(k, 1);
				queue.add(new int[] {x, y, z});
			}
		}
		int n = 0;
		for (long k : p.positions()) {
			Cell c = p.get(Plan.kx(k), Plan.ky(k), Plan.kz(k));
			if (c != null && !c.air() && !seen.contains(k)) n++;
		}
		return n;
	}

	/** Every way in has clear space for a player and something to stand on in front of it. */
	static void entrances(Building b, Report r) {
		List<Attachment> list = b.attachments(Attachment.Kind.ENTRANCE);
		r.metric("entrances", list.size());
		Plan p = b.plan;
		for (Attachment a : list) {
			int ix = a.x() - a.facing().dx, iz = a.z() - a.facing().dz;
			// the opening itself: the block behind the attachment point, two high
			int kind0 = Walk.ofCell(p.get(ix, a.y(), iz)), kind1 = Walk.ofCell(p.get(ix, a.y() + 1, iz));
			if (kind0 != 0 || kind1 != 0) r.error("entrance", "the way in at " + ix + "," + a.y() + "," + iz + " is blocked");
		}
	}

	/** Spiral stairs and wall-walk stairs lead from their bottom to their top. */
	static void stairs(Building b, Report r) {
		List<Attachment> landings = b.attachments(Attachment.Kind.LANDING);
		GridPath.Terrain t = Walk.of(b.plan);
		int checked = 0, broken = 0;
		for (int i = 0; i + 1 < landings.size(); i += 2) {
			Attachment lo = landings.get(i), hi = landings.get(i + 1);
			if (!lo.name().equals("stair_bottom") || !hi.name().equals("stair_top")) continue;
			checked++;
			int sy = Walk.standY(t, lo.x(), lo.y(), lo.z());
			int gy = Walk.standY(t, hi.x(), hi.y() + 1, hi.z());
			if (sy == Integer.MIN_VALUE || gy == Integer.MIN_VALUE
					|| GridPath.find(t, new GridPath.Node(lo.x(), sy, lo.z()), new GridPath.Node(hi.x(), gy, hi.z()), 0.9, 20000) == null) {
				broken++;
			}
		}
		r.metric("stairs", checked);
		if (broken > 0) r.error("stairs", broken + " of " + checked + " stairs do not connect their levels");
	}

	/** Over the walls of every roofed volume there is roof (no wall top left open to the sky). */
	static void roofs(Building b, Report r) {
		int gaps = 0, seats = 0;
		for (Volume v : b.volumes) {
			if (v.roof == Volume.Roof.NONE || v.roof == Volume.Roof.FLAT || v.roof == Volume.Roof.BATTLEMENT) continue;
			if (v.role == Volume.Role.TOWER || v.role == Volume.Role.TURRET || v.role == Volume.Role.FACADE_TOWER || v.role == Volume.Role.CROSSING_TOWER
					|| v.role == Volume.Role.KEEP || v.role == Volume.Role.GATEHOUSE || v.role == Volume.Role.APSE || v.role == Volume.Role.TEMPLE) continue;
			for (int x = v.fp.minX(); x <= v.fp.maxX(); x++) {
				for (int z = v.fp.minZ(); z <= v.fp.maxZ(); z++) {
					if (!v.fp.edge(x, z)) continue;
					if (!b.plan.filled(x, v.top, z)) continue;
					seats++;
					// covered: something above it (the roof, a gable, a higher roof that runs over it)
					boolean covered = false;
					for (int y = v.top + 1; y <= b.plan.maxY() && !covered; y++) covered = b.plan.filled(x, y, z);
					if (!covered) gaps++;
				}
			}
		}
		r.metric("roofSeats", seats);
		r.metric("roofGaps", gaps);
		if (seats > 0 && gaps > seats * 0.08) r.warn("roof", gaps + " of " + seats + " wall tops have no roof over them");
	}

	/** Ordered ramps should climb in brightness without big jumps (a gradient, not a stripe). */
	public static void palette(Palette p, Report r) {
		for (Palette at = p; at != null; at = at.parent()) {
			for (Map.Entry<Slot, Ramp> e : at.ownRamps().entrySet()) {
				if (e.getKey() == Slot.STAINED || e.getKey() == Slot.GROUND || e.getKey() == Slot.ROCK || e.getKey() == Slot.MAGIC || e.getKey() == Slot.RUBBLE
						|| e.getKey() == Slot.LEAVES || e.getKey() == Slot.ACCENT) continue;
				Ramp ramp = e.getValue();
				for (int i = 1; i < ramp.size(); i++) {
					double gap = Math.abs(ramp.get(i).value() - ramp.get(i - 1).value());
					if (gap > 0.32) r.warn("ramp", at.name() + " " + e.getKey() + ": " + ramp.get(i - 1).name() + " → " + ramp.get(i).name() + " jumps " + Math.round(gap * 100) + "%");
				}
			}
		}
	}

	/** Composition metrics: height variation, skyline complexity, and how the materials mix. */
	static void composition(Building b, Report r) {
		Plan p = b.plan;
		int minX = p.minX(), maxX = p.maxX(), minZ = p.minZ(), maxZ = p.maxZ();
		double sum = 0, sum2 = 0;
		int n = 0;
		int[] skylineX = new int[maxX - minX + 1];
		int[] skylineZ = new int[maxZ - minZ + 1];
		java.util.Arrays.fill(skylineX, Integer.MIN_VALUE);
		java.util.Arrays.fill(skylineZ, Integer.MIN_VALUE);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				int top = p.top(x, z);
				if (top == Integer.MIN_VALUE || top < b.floorY) continue;
				sum += top;
				sum2 += (double) top * top;
				n++;
				skylineX[x - minX] = Math.max(skylineX[x - minX], top);
				skylineZ[z - minZ] = Math.max(skylineZ[z - minZ], top);
			}
		}
		if (n > 0) {
			double mean = sum / n;
			r.metric("heightStd", Math.sqrt(Math.max(0, sum2 / n - mean * mean)));
		}
		r.metric("skylineSteps", steps(skylineX) + steps(skylineZ));
		r.metric("height", p.maxY() - b.floorY);
	}

	private static int steps(int[] skyline) {
		int s = 0;
		for (int i = 1; i < skyline.length; i++) {
			if (skyline[i] != Integer.MIN_VALUE && skyline[i - 1] != Integer.MIN_VALUE && skyline[i] != skyline[i - 1]) s++;
		}
		return s;
	}

	/** Every block name in the finished blocks is one the catalog knows (so the game will know it too). */
	public static Report names(Voxels v) {
		Report r = new Report();
		Set<String> unknown = new HashSet<>();
		for (String id : v.names()) {
			String b = Props.base(id);
			if (Catalog.kind(b) == null && Catalog.ofBlock(b) == null) unknown.add(b);
		}
		if (!unknown.isEmpty()) r.error("names", "unknown blocks: " + unknown);
		r.metric("distinctBlocks", v.names().size());
		return r;
	}

	/** How much of a material role is a secondary material (0 = one material, high = noise). */
	public static double mix(Voxels v, String family) {
		Map<String, Integer> counts = v.counts();
		int total = 0, main = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			Material m = Catalog.ofBlock(Props.base(e.getKey()));
			if (m == null || !m.is(family)) continue;
			total += e.getValue();
			main = Math.max(main, e.getValue());
		}
		return total == 0 ? 0 : 1 - (double) main / total;
	}

	/**
	 * The lobby as a whole: every building checked, every block name known, no two buildings overlapping, and a
	 * walkable way from the spawn to the entrance of each landmark.
	 */
	public static Report lobby(net.battlehub.arch.town.Lobby.Result res, int[] spawn) {
		Report r = new Report();
		r.addAll(names(res.voxels));
		int errors = 0, warnings = 0;
		for (Building b : res.buildings) {
			Report br = building(b);
			for (Report.Issue i : br.issues) {
				if (i.level() == Report.Level.ERROR) {
					errors++;
					r.issues.add(new Report.Issue(i.level(), i.code(), b.brief.archetype + ": " + i.message()));
				} else {
					warnings++;
				}
			}
		}
		r.metric("buildings", res.buildings.size());
		r.metric("buildingErrors", errors);
		r.metric("buildingWarnings", warnings);
		// overlaps between building boxes (shrunk by one, roofs may touch)
		int overlaps = 0;
		for (int i = 0; i < res.buildings.size(); i++) {
			int[] a = res.buildings.get(i).worldBox();
			for (int j = i + 1; j < res.buildings.size(); j++) {
				int[] c = res.buildings.get(j).worldBox();
				if (a[0] + 1 <= c[3] - 1 && c[0] + 1 <= a[3] - 1 && a[2] + 1 <= c[5] - 1 && c[2] + 1 <= a[5] - 1) overlaps++;
			}
		}
		r.metric("overlaps", overlaps);
		if (overlaps > 0) r.error("overlap", overlaps + " buildings overlap");
		// walkable from the spawn to each landmark
		GridPath.Terrain t = Walk.of(res.voxels);
		int sy = Walk.standY(t, spawn[0], spawn[1], spawn[2]);
		for (Map.Entry<String, Attachment> e : net.battlehub.arch.town.Lobby.entrances(res).entrySet()) {
			Attachment a = e.getValue();
			int gy = Walk.standY(t, a.x(), a.y(), a.z());
			List<GridPath.Node> path = sy == Integer.MIN_VALUE || gy == Integer.MIN_VALUE ? null
					: GridPath.find(t, new GridPath.Node(spawn[0], sy, spawn[2]), new GridPath.Node(a.x(), gy, a.z()), 2.5, 400000);
			r.metric("path:" + e.getKey(), path == null ? -1 : path.size());
			if (path == null) r.error("path", "no way from the spawn to the " + e.getKey() + " (" + a.x() + "," + a.y() + "," + a.z() + ")");
		}
		return r;
	}
}
