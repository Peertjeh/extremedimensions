package net.battlehub.arch.arena;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.arch.Props;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.check.Report;
import net.battlehub.arch.check.Validator;
import net.battlehub.arch.check.Walk;
import net.battlehub.core.GridPath;

/**
 * Checks a generated arena the way a player would find it: the points the game uses are there and usable, the
 * zones where players build or stand are free, everything important can be walked to from the spawn, every lane
 * has a free place on the coast to start a bridge from, all teams got the same island, and nothing hangs in the
 * air. Also measures a few tactical numbers (cover round the generators, the high ground) for the log.
 */
public final class ArenaCheck {
	private ArenaCheck() {}

	/** How far a launch point may be from the straight line to its target, in degrees. */
	static final double LAUNCH_SPREAD = 28;

	public static Report bedwars(ArenaPlan plan, int teams) {
		Report r = new Report();
		r.addAll(Validator.names(plan.voxels));
		Voxels v = plan.voxels;
		GridPath.Terrain walk = Walk.of(v);
		List<ArenaPlan.Isle> isles = plan.isles("team");
		if (isles.size() != teams) r.error("teams", isles.size() + " team islands for " + teams + " teams");
		if (plan.spawns.size() != teams) r.error("spawns", plan.spawns.size() + " spawns for " + teams + " teams");
		for (String m : new String[] {"gen_diamond", "gen_emerald", "build_min", "build_max", "podium"}) {
			if (plan.marks(m).isEmpty()) r.error("mark", "mark " + m + " missing");
		}
		// the points of every team
		for (int i = 0; i < isles.size(); i++) {
			int[] spawn = plan.mark("spawn_" + i), bed = plan.mark("bed_" + i), gen = plan.mark("gen_" + i);
			int[] shop = plan.mark("shop_" + i), shopBlock = plan.mark("shopblock_" + i);
			if (spawn == null || bed == null || gen == null || shop == null || shopBlock == null) {
				r.error("mark", "team " + i + " lacks a spawn, bed, generator or shop mark");
				continue;
			}
			if (!walk.stand(spawn[0], spawn[1], spawn[2])) r.error("spawn", "team " + i + " cannot stand on its spawn");
			String foot = v.get(bed[0], bed[1], bed[2]);
			if (foot == null || !Props.base(foot).endsWith("_bed") || !"foot".equals(Props.get(foot, "part"))) {
				r.error("bed", "team " + i + " has no bed foot at its bed mark: " + foot);
			}
			String under = v.get(gen[0], gen[1] - 1, gen[2]);
			if (under == null || !(under.equals("gold_block") || under.equals("iron_block"))) r.error("generator", "team " + i + " generator stands on " + under);
			if (!walk.stand(shop[0], shop[1], shop[2])) r.error("shop", "team " + i + ": no room for the shopkeeper");
			String sb = v.get(shopBlock[0], shopBlock[1], shopBlock[2]);
			if (sb == null || !Props.base(sb).equals("barrel")) r.error("shop", "team " + i + ": no shop barrel");
			// walkable from the spawn: next to the bed, onto the generator, to the shopkeeper
			path(r, walk, "bed " + i, spawn, new int[] {bed[0], bed[1], bed[2]}, 1.5);
			path(r, walk, "generator " + i, spawn, gen, 1.0);
			path(r, walk, "shop " + i, spawn, shop, 1.5);
		}
		for (int[] g : plan.marks("gen_diamond")) {
			if (!"diamond_block".equals(v.get(g[0], g[1] - 1, g[2]))) r.error("generator", "diamond generator stands on " + v.get(g[0], g[1] - 1, g[2]));
			if (!walk.stand(g[0], g[1], g[2])) r.error("generator", "no room at the diamond generator " + g[0] + "," + g[2]);
		}
		for (int[] g : plan.marks("gen_emerald")) {
			if (!"emerald_block".equals(v.get(g[0], g[1] - 1, g[2]))) r.error("generator", "emerald generator stands on " + v.get(g[0], g[1] - 1, g[2]));
			if (!walk.stand(g[0], g[1], g[2])) r.error("generator", "no room at the emerald generator " + g[0] + "," + g[2]);
		}
		zones(plan, r);
		launches(plan, r, walk);
		fairness(plan, r, "team");
		fairness(plan, r, "diamond");
		int floating = 0;
		for (Building b : plan.buildings) floating += Validator.floating(b);
		r.metric("floating", floating);
		if (floating > 0) r.error("floating", floating + " blocks of the play area hang in the air");
		tactics(plan, r, walk);
		r.metric("blocks", v.size());
		return r;
	}

	/** A path of at most this many nodes is searched for. */
	static final int BUDGET = 60_000;

	static void path(Report r, GridPath.Terrain walk, String what, int[] from, int[] to, double reach) {
		int sy = Walk.standY(walk, from[0], from[1], from[2]);
		int gy = Walk.standY(walk, to[0], to[1], to[2]);
		if (gy == Integer.MIN_VALUE) {
			// the goal itself may be occupied (a bed): any neighbour will do
			for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				gy = Walk.standY(walk, to[0] + dd[0], to[1], to[2] + dd[1]);
				if (gy != Integer.MIN_VALUE) {
					to = new int[] {to[0] + dd[0], to[1], to[2] + dd[1]};
					break;
				}
			}
		}
		List<GridPath.Node> p = sy == Integer.MIN_VALUE || gy == Integer.MIN_VALUE ? null
				: GridPath.find(walk, new GridPath.Node(from[0], sy, from[2]), new GridPath.Node(to[0], gy, to[2]), reach, BUDGET);
		r.metric("path:" + what, p == null ? -1 : p.size());
		if (p == null) r.error("path", "no way from the spawn to the " + what);
	}

	/** Zones must hold nothing a player would bump into (the bed itself and plants are fine). */
	static void zones(ArenaPlan plan, Report r) {
		int bad = 0;
		for (ArenaPlan.Zone z : plan.zones) {
			for (int x = z.x0(); x <= z.x1(); x++) {
				for (int y = z.y0(); y <= z.y1(); y++) {
					for (int zz = z.z0(); zz <= z.z1(); zz++) {
						String id = plan.voxels.get(x, y, zz);
						if (id == null || id.equals("air") || Props.base(id).endsWith("_bed")) continue;
						if (Walk.kind(id) != 0) {
							bad++;
							if (bad <= 5) r.error("zone", z.name() + " is not free at " + x + "," + y + "," + zz + ": " + id);
						}
					}
				}
			}
		}
		r.metric("zoneBlocks", bad);
	}

	/**
	 * For every team island and every target it must be able to bridge to (the middle, both neighbours, the two
	 * nearest diamonds): a coast block in that direction that can be walked to from the spawn, with the void in
	 * front of it.
	 */
	static void launches(ArenaPlan plan, Report r, GridPath.Terrain walk) {
		List<ArenaPlan.Isle> teams = plan.isles("team");
		List<ArenaPlan.Isle> diamonds = plan.isles("diamond");
		int n = teams.size();
		int missing = 0, total = 0;
		for (int i = 0; i < n; i++) {
			ArenaPlan.Isle me = teams.get(i);
			int[] spawn = plan.mark("spawn_" + i);
			if (spawn == null) continue;
			List<int[]> targets = new ArrayList<>();
			targets.add(new int[] {0, 0});
			targets.add(new int[] {teams.get((i + 1) % n).cx(), teams.get((i + 1) % n).cz()});
			if (n > 2) targets.add(new int[] {teams.get((i + n - 1) % n).cx(), teams.get((i + n - 1) % n).cz()});
			List<ArenaPlan.Isle> near = new ArrayList<>(diamonds);
			near.sort((a, b) -> Double.compare(Math.hypot(a.cx() - me.cx(), a.cz() - me.cz()), Math.hypot(b.cx() - me.cx(), b.cz() - me.cz())));
			for (int k = 0; k < Math.min(2, near.size()); k++) targets.add(new int[] {near.get(k).cx(), near.get(k).cz()});
			for (int[] t : targets) {
				total++;
				double want = Math.atan2(t[1] - me.cz(), t[0] - me.cx());
				if (!launch(plan.voxels, walk, me, spawn, want)) {
					missing++;
					r.error("lane", "team " + i + " has no free coast to bridge from towards " + t[0] + "," + t[1]);
				}
			}
		}
		r.metric("lanes", total);
		r.metric("lanesBlocked", missing);
	}

	static boolean launch(Voxels v, GridPath.Terrain walk, ArenaPlan.Isle me, int[] spawn, double want) {
		int reach = (int) Math.ceil(me.radius() * 1.2) + 1;
		List<int[]> coast = new ArrayList<>();
		for (int x = me.cx() - reach; x <= me.cx() + reach; x++) {
			for (int z = me.cz() - reach; z <= me.cz() + reach; z++) {
				if (!v.filled(x, 0, z) || !walk.stand(x, 1, z)) continue;
				double a = Math.atan2(z - me.cz(), x - me.cx());
				double diff = Math.abs(Math.toDegrees(Math.atan2(Math.sin(a - want), Math.cos(a - want))));
				if (diff > LAUNCH_SPREAD) continue;
				// the void straight ahead in the target's direction
				int ox = x + (int) Math.round(Math.cos(want)), oz = z + (int) Math.round(Math.sin(want));
				if (v.filled(ox, 0, oz) || v.filled(ox, 1, oz) || v.filled(ox, 2, oz)) continue;
				coast.add(new int[] {x, z});
			}
		}
		coast.sort((a, b) -> Double.compare(Math.hypot(a[0] - spawn[0], a[1] - spawn[2]), Math.hypot(b[0] - spawn[0], b[1] - spawn[2])));
		for (int k = 0; k < Math.min(6, coast.size()); k++) {
			int[] c = coast.get(k);
			if (GridPath.find(walk, new GridPath.Node(spawn[0], spawn[1], spawn[2]), new GridPath.Node(c[0], 1, c[1]), 0.5, BUDGET) != null) return true;
		}
		return false;
	}

	/**
	 * All islands of one kind must be the same for players: for every point in their own frame, a block one can
	 * stand on, a block in the way or open space, exactly as on the first island.
	 */
	static void fairness(ArenaPlan plan, Report r, String kind) {
		List<ArenaPlan.Isle> isles = plan.isles(kind);
		if (isles.size() < 2) return;
		ArenaPlan.Isle first = isles.get(0);
		// only the island itself and what stands on it (its neighbours need not be alike: with five teams the four
		// diamond islands cannot sit the same way round every island)
		double own = first.radius() * 1.15 + 1;
		int reach = (int) Math.ceil(own);
		int differ = 0;
		for (int i = 1; i < isles.size(); i++) {
			Xform a = first.frame(), b = isles.get(i).frame();
			for (int x = -reach; x <= reach; x++) {
				for (int z = -reach; z <= reach; z++) {
					if (Math.hypot(x, z) > own) continue;
					for (int y = -1; y <= 12; y++) {
						int ka = Walk.kind(plan.voxels.get(a.x(x, z), y, a.z(x, z)));
						int kb = Walk.kind(plan.voxels.get(b.x(x, z), y, b.z(x, z)));
						if (ka != kb) differ++;
					}
				}
			}
		}
		r.metric("unfair:" + kind, differ);
		if (differ > 0) r.error("fairness", differ + " points differ between the " + kind + " islands");
	}

	/** Cover round each team's generator, the highest place to stand on a team island, solid blocks on it. */
	static void tactics(ArenaPlan plan, Report r, GridPath.Terrain walk) {
		List<ArenaPlan.Isle> teams = plan.isles("team");
		if (teams.isEmpty()) return;
		ArenaPlan.Isle me = teams.get(0);
		int[] gen = plan.mark("gen_0");
		int cover = 0;
		for (int x = gen[0] - 5; x <= gen[0] + 5; x++) {
			for (int z = gen[2] - 5; z <= gen[2] + 5; z++) {
				for (int y = 1; y <= 7; y++) {
					if (Walk.kind(plan.voxels.get(x, y, z)) != 0) cover++;
				}
			}
		}
		int high = 0;
		int reach = (int) Math.ceil(me.radius()) + 1;
		for (int x = me.cx() - reach; x <= me.cx() + reach; x++) {
			for (int z = me.cz() - reach; z <= me.cz() + reach; z++) {
				for (int y = 12; y >= 1; y--) {
					if (walk.stand(x, y, z)) {
						high = Math.max(high, y - 1);
						break;
					}
				}
			}
		}
		r.metric("coverAtGenerator", cover);
		r.metric("highGround", high);
	}
}
