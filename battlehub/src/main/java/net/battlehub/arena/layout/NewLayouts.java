package net.battlehub.arena.layout;

import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.battlehub.arena.gen.Shape;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Arenas for The Bridge, Boxing, Build UHC, Spleef, SkyWars and Murder Mystery. */
public final class NewLayouts {
	private NewLayouts() {}

	/** Distance from the middle to the centre of each Bridge island (along x). */
	public static final int BRIDGE_ISLAND = 21;

	/**
	 * The Bridge: a red island in the west and a blue one in the east, each with a goal (a 3x3 hole at the back)
	 * and a narrow path between them that players widen with their clay. Spawn 0 is red, spawn 1 blue. Marks:
	 * goal_red, goal_blue (the middle of each hole, at floor height), build_min, build_max.
	 */
	public static Blueprint bridge(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		BlockState red = Build.s("red_terracotta");
		BlockState blue = Build.s("blue_terracotta");
		for (int side = -1; side <= 1; side += 2) {
			int cx = side * BRIDGE_ISLAND;
			BlockState team = side < 0 ? red : blue;
			// Island 11 x 11 with two layers underneath
			for (int x = -5; x <= 5; x++) {
				for (int z = -5; z <= 5; z++) {
					boolean rim = Math.abs(x) == 5 || Math.abs(z) == 5;
					bp.set(cx + x, 0, z, rim ? team : t.floor(rnd));
					bp.set(cx + x, -1, z, t.under());
					if (Math.abs(x) < 4 && Math.abs(z) < 4) bp.set(cx + x, -2, z, t.under());
				}
			}
			// The goal: a hole at the back of the island, ringed in the team colour
			int gx = cx + side * 3;
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					bp.remove(gx + x, 0, z);
					bp.remove(gx + x, -1, z);
					bp.remove(gx + x, -2, z);
				}
			}
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2) bp.set(gx + x, 0, z, team);
				}
			}
			bp.mark(side < 0 ? "goal_red" : "goal_blue", gx, 0, 0);
			// Two small towers on the sides of the island
			Build.pillar(bp, cx - side * 2, 1, -4, 3, t.pillar());
			Build.pillar(bp, cx - side * 2, 1, 4, 3, t.pillar());
			bp.set(cx - side * 2, 4, -4, t.light());
			bp.set(cx - side * 2, 4, 4, t.light());
		}
		// Narrow path between the islands with a little platform in the middle
		for (int x = -BRIDGE_ISLAND + 6; x <= BRIDGE_ISLAND - 6; x++) {
			bp.set(x, 0, 0, t.trim());
		}
		Build.disc(bp, 0, 0, 0, 2.2, t::accent);
		// Red faces east (yaw -90), blue faces west (yaw 90)
		bp.spawn(-BRIDGE_ISLAND + 0.5, 1, 0.5, -90);
		bp.spawn(BRIDGE_ISLAND + 0.5, 1, 0.5, 90);
		// Players build between the islands, a little above and below the path, and on the islands
		bp.mark("build_min", -BRIDGE_ISLAND - 5, -6, -7);
		bp.mark("build_max", BRIDGE_ISLAND + 5, 8, 7);
		bp.include(-BRIDGE_ISLAND - 6, -7, -8);
		bp.include(BRIDGE_ISLAND + 6, 10, 8);
		Decor.ring(bp, t, rnd, BRIDGE_ISLAND + 12, BRIDGE_ISLAND + 18, -12);
		bp.spectator(0.5, 18, -22.5, 0, 35);
		bp.voidY(-8);
		bp.radius(BRIDGE_ISLAND + 22);
		return bp;
	}

	/** Team colours of the Bridge for parties: red, blue, lime, yellow. */
	public static final String[] BRIDGE_COLORS = {"red", "blue", "lime", "yellow"};

	/**
	 * The Bridge for 2 to 4 teams: an island per team on a ring round a small middle platform, each with a goal (a
	 * 3x3 hole at the back) and a narrow path to the middle. Team 0 is west, 1 east, 2 north, 3 south (for three
	 * teams a third apart). Spawns: {@code perTeam} per island, team by team. Marks: goal_0 .. goal_n (the middle of
	 * each hole, at floor height), build_min, build_max.
	 */
	public static Blueprint bridgeRing(Theme t, RandomSource rnd, int teams, int perTeam) {
		Blueprint bp = new Blueprint();
		int n = Math.max(2, Math.min(4, teams));
		int per = Math.max(1, Math.min(4, perTeam));
		int ring = n == 2 ? BRIDGE_ISLAND : 23;
		double[] angles = n == 3 ? new double[] {Math.PI, Math.PI / 3, -Math.PI / 3}
				: new double[] {Math.PI, 0, -Math.PI / 2, Math.PI / 2};
		net.battlehub.arena.gen.Style style = net.battlehub.arena.gen.Style.of(t, rnd, ring);
		// The middle and the paths first, so the islands sit on top
		Build.disc(bp, 0, 0, 0, 2.6, t::accent);
		for (int i = 0; i < n; i++) {
			double a = angles[i];
			for (int d = 3; d <= ring - 5; d++) {
				bp.set((int) Math.round(Math.cos(a) * d), 0, (int) Math.round(Math.sin(a) * d), t.trim());
			}
		}
		for (int i = 0; i < n; i++) {
			double a = angles[i];
			int cx = (int) Math.round(Math.cos(a) * ring);
			int cz = (int) Math.round(Math.sin(a) * ring);
			BlockState team = Build.s(BRIDGE_COLORS[i] + "_terracotta");
			for (int x = -6; x <= 6; x++) {
				for (int z = -6; z <= 6; z++) {
					double d = Math.sqrt(x * x + z * z);
					if (d > 6.2) continue;
					bp.set(cx + x, 0, cz + z, d > 5.3 ? team : style.floor(cx + x, cz + z, rnd));
					bp.set(cx + x, -1, cz + z, t.under());
					if (d < 4.5) bp.set(cx + x, -2, cz + z, t.under());
				}
			}
			// The goal at the back (away from the middle), ringed in the team colour
			int gx = cx + (int) Math.round(Math.cos(a) * 3);
			int gz = cz + (int) Math.round(Math.sin(a) * 3);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2) {
						bp.set(gx + x, 0, gz + z, team);
					} else {
						bp.remove(gx + x, 0, gz + z);
						bp.remove(gx + x, -1, gz + z);
						bp.remove(gx + x, -2, gz + z);
					}
				}
			}
			bp.mark("goal_" + i, gx, 0, gz);
			// Two lamp pillars on the sides
			double side = a + Math.PI / 2;
			for (int s2 = -1; s2 <= 1; s2 += 2) {
				int px = cx + (int) Math.round(Math.cos(side) * 4 * s2 - Math.cos(a) * 1);
				int pz = cz + (int) Math.round(Math.sin(side) * 4 * s2 - Math.sin(a) * 1);
				Build.pillar(bp, px, 1, pz, 3, t.pillar());
				bp.set(px, 4, pz, t.light());
			}
			// Spawns towards the front of the island, side by side, facing the middle
			float yaw = (float) Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a)));
			for (int k = 0; k < per; k++) {
				double off = (k - (per - 1) / 2.0) * 2;
				double sx = cx - Math.cos(a) * 1.5 + Math.cos(side) * off;
				double sz = cz - Math.sin(a) * 1.5 + Math.sin(side) * off;
				bp.spawn(Math.floor(sx) + 0.5, 1, Math.floor(sz) + 0.5, yaw);
			}
		}
		int reach = ring + 7;
		bp.mark("build_min", -reach, -6, -reach);
		bp.mark("build_max", reach, 8, reach);
		bp.playArea(net.battlehub.arena.Blueprint.circle(reach), 8);
		bp.include(-reach - 1, -7, -reach - 1);
		bp.include(reach + 1, 10, reach + 1);
		Decor.ring(bp, t, rnd, reach + 6, reach + 12, -12);
		bp.spectator(0.5, 22, -(reach + 4) + 0.5, 0, 40);
		bp.voidY(-8);
		bp.radius(reach + 18);
		return bp;
	}

	/** Boxing: a square ring with corner posts and a low rope of fences. */
	public static Blueprint boxing(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int h = 7;
		BlockState rope = Build.s("oak_fence");
		for (int x = -h - 2; x <= h + 2; x++) {
			for (int z = -h - 2; z <= h + 2; z++) {
				boolean inside = Math.abs(x) <= h && Math.abs(z) <= h;
				bp.set(x, 0, z, inside ? ((x + z & 1) == 0 ? t.floor(rnd) : t.trim()) : t.under());
				bp.set(x, -1, z, t.under());
				boolean edge = Math.max(Math.abs(x), Math.abs(z)) == h + 1;
				if (edge) {
					bp.set(x, 1, z, rope);
					bp.set(x, 2, z, Build.s("barrier"));
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				Build.pillar(bp, sx * (h + 1), 1, sz * (h + 1), 3, t.pillar());
				bp.set(sx * (h + 1), 4, sz * (h + 1), t.light());
			}
		}
		Decor.ring(bp, t, rnd, h + 6, h + 13, -1);
		bp.spawn(-3.5, 1, 0.5, -90);
		bp.spawn(4.5, 1, 0.5, 90);
		bp.spectator(0.5, 9, -(h + 7) + 0.5, 0, 30);
		bp.voidY(-6);
		bp.radius(h + 16);
		return bp;
	}

	/**
	 * Build UHC: a big duel field (made by the duel generator, larger) with room above to build. Marks build_min
	 * and build_max: the box players may build in.
	 */
	public static Blueprint buildUhc(Theme t, RandomSource rnd, int buildHeight) {
		Blueprint bp = net.battlehub.arena.gen.ArenaGen.buildArena(t, rnd, 1.5, buildHeight);
		// The box round the field; the field itself (inside the walls, up to the top) is the play area
		bp.mark("build_min", bp.minX() + 1, bp.minY(), bp.minZ() + 1);
		bp.mark("build_max", bp.maxX() - 1, bp.playTop(), bp.maxZ() - 1);
		bp.include(bp.minX(), bp.playTop() + 3, bp.minZ());
		return bp;
	}

	/** Spleef: {@code layers} snow floors 6 apart over the void, in a random shape. Top floor is y=0. Marks "floor". */
	public static Blueprint snowSpleef(Theme t, RandomSource rnd, int layers) {
		Blueprint bp = new Blueprint();
		Shape shape = Shape.roundish(rnd);
		int r = 11 + rnd.nextInt(5);
		BlockState snow = Build.s("snow_block");
		for (int l = 0; l < layers; l++) {
			int y = -6 * l;
			int rr = r - l;
			for (int x = -rr - 1; x <= rr + 1; x++) {
				for (int z = -rr - 1; z <= rr + 1; z++) {
					if (shape.inside(x, z, rr)) {
						bp.set(x, y, z, snow);
					}
				}
			}
			bp.mark("floor", 0, y, 0);
		}
		Decor.ring(bp, t, rnd, r + 6, r + 13, -3);
		bp.spawnRing(16, Math.max(4, Math.round(r * 0.55)), 1);
		bp.spectator(0.5, 14, -(r + 7) + 0.5, 0, 35);
		bp.voidY(-6 * (layers - 1) - 6);
		bp.radius(r + 18);
		return bp;
	}

	/** SkyWars at a random size (1.5 to 2.5 times as wide as the map of 1.29). */
	public static Blueprint skywars(Theme t, RandomSource rnd, int players) {
		return skywars(t, rnd, net.battlehub.core.SkyWarsPlan.of(players, net.battlehub.core.SkyWarsPlan.factor(rnd.nextDouble())));
	}

	/**
	 * SkyWars ({@link net.battlehub.core.SkyWarsPlan}): one island per player on the outside, a ring of smaller middle
	 * islands beside the ways in and a super big island in the middle with the best loot, all at the plan's size.
	 * Marks: chest (two on every spawn island), mid_chest (two on every middle island, better), center_chest (eight
	 * in the middle, twelve when it is very big; the best).
	 */
	public static Blueprint skywars(Theme t, RandomSource rnd, net.battlehub.core.SkyWarsPlan plan) {
		Blueprint bp = new Blueprint();
		int outer = (int) Math.ceil(plan.outer);
		double s = plan.scale;
		net.battlehub.arena.gen.Style style = net.battlehub.arena.gen.Style.of(t, rnd, outer);
		for (net.battlehub.core.SkyWarsPlan.Island i : plan.spawns) {
			int cx = (int) Math.round(i.x()), cz = (int) Math.round(i.z());
			double a = i.angle(), r = i.r();
			Build.island(bp, cx, 0, cz, r, depth(r), (x, z) -> style.floor(x, z, rnd), t.under(), t.rough(), rnd);
			// out (away from the middle) and to the side
			double ox = Math.cos(a), oz = Math.sin(a);
			double out = Math.max(3, r * 0.65), aside = Math.max(2.6, r * 0.55);
			int[] back = {(int) Math.round(ox * out), (int) Math.round(oz * out)};
			int[] left = {(int) Math.round(-oz * aside), (int) Math.round(ox * aside)};
			chest(bp, "chest", cx + back[0], 1, cz + back[1], cx, cz);
			chest(bp, "chest", cx + left[0], 1, cz + left[1], cx, cz);
			if (t.hasTrees() && rnd.nextBoolean()) {
				Build.tree(bp, cx - left[0], 1, cz - left[1], 4 + (int) Math.round(s), t.log(), t.leaves(), rnd);
			} else {
				Build.pillar(bp, cx - left[0], 1, cz - left[1], 2, t.pillar());
				bp.set(cx - left[0], 3, cz - left[1], t.light());
			}
			// facing the middle, a step in from the island's centre
			float yaw = (float) Math.toDegrees(Math.atan2(cx, -cz));
			bp.spawn(cx - Math.round(ox * 1.5) + 0.5, 1, cz - Math.round(oz * 1.5) + 0.5, yaw);
		}
		for (net.battlehub.core.SkyWarsPlan.Island i : plan.mids) {
			int cx = (int) Math.round(i.x()), cz = (int) Math.round(i.z());
			double a = i.angle(), r = i.r();
			Build.island(bp, cx, -1, cz, r, depth(r), (x, z) -> (x - cx) * (x - cx) + (z - cz) * (z - cz) <= 2 ? t.accent() : style.floor(x, z, rnd),
					t.under(), t.rough(), rnd);
			// a lamp post in the middle, a chest on either side of it (along the ring)
			Build.pillar(bp, cx, 0, cz, 2, t.pillar());
			bp.set(cx, 2, cz, t.light());
			double along = Math.max(2, r * 0.45);
			int sx = (int) Math.round(-Math.sin(a) * along), sz = (int) Math.round(Math.cos(a) * along);
			chest(bp, "mid_chest", cx + sx, 0, cz + sz, cx, cz);
			chest(bp, "mid_chest", cx - sx, 0, cz - sz, cx, cz);
		}
		int deepest = centre(bp, t, style, rnd, plan.center.r());
		bp.include(-outer - 10, -deepest - 6, -outer - 10);
		bp.include(outer + 10, 24, outer + 10);
		// Bridging between the islands is fine; far out into the void or high up in the sky is not
		bp.playArea(Blueprint.circle(outer + 10), 22);
		bp.spectator(0.5, 20 + Math.round(outer * 0.35), -(outer + 12) + 0.5, 0, 35);
		bp.voidY(-deepest - 6);
		bp.radius(outer + 24);
		return bp;
	}

	/** How deep the underside of an island of this radius hangs. */
	private static int depth(double r) {
		return (int) Math.min(20, Math.round(3 + r * 0.45));
	}

	/**
	 * The middle of SkyWars: a super big island rising in two terraces to a pavilion with four chests under its roof,
	 * four more chests round the foot of the terraces (and four on the outer ring when the island is very big), trees
	 * (or lamp posts) between them. Returns how deep its underside hangs.
	 */
	private static int centre(Blueprint bp, Theme t, net.battlehub.arena.gen.Style style, RandomSource rnd, double r) {
		int deep = depth(r);
		Build.island(bp, 0, 0, 0, r, deep, (x, z) -> style.floor(x, z, rnd), t.under(), t.rough(), rnd);
		double r1 = Math.round(r * 0.55), r2 = Math.max(4, Math.round(r * 0.28));
		Build.island(bp, 0, 1, 0, r1, 0, (x, z) -> style.floor(x, z, rnd), t.under(), t.rough(), rnd);
		Build.island(bp, 0, 2, 0, r2, 0, (x, z) -> style.floorAt(Math.min(1, Build.dist(x, z) / r2)), t.under(), t.rough(), rnd);
		// the pavilion: four posts, a roof with a light under it, the best chests between the posts
		int p = (int) Math.max(2, Math.round(r2 * 0.45));
		int h = 4 + p / 2;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				Build.pillar(bp, p * sx, 3, p * sz, h, t.pillar());
			}
		}
		Build.disc(bp, 0, 3 + h, 0, p + 1, () -> t.trim());
		BlockState lamp = t.light();
		if (lamp.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING)) {
			lamp = lamp.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, true);
		}
		bp.set(0, 2 + h, 0, lamp);
		bp.set(0, 4 + h, 0, t.light());
		int[][] inner = {{p, 0}, {-p, 0}, {0, p}, {0, -p}};
		for (int[] c : inner) chest(bp, "center_chest", c[0], 3, c[1], 0, 0);
		// round the foot of the terraces: four chests on the diagonals, trees or lamp posts straight out
		ring(bp, t, rnd, (r1 + r) / 2 + 0.5, Math.PI / 4, 0);
		if (r >= 28) ring(bp, t, rnd, r * 0.85, Math.PI / 8, 3 * Math.PI / 8);
		return deep;
	}

	/** Four chests at {@code chestAngle} + quarter turns on a ring round the middle, and four trees (or lamps) at {@code treeAngle}. */
	private static void ring(Blueprint bp, Theme t, RandomSource rnd, double ring, double chestAngle, double treeAngle) {
		for (int q = 0; q < 4; q++) {
			double a = chestAngle + q * Math.PI / 2;
			chest(bp, "center_chest", (int) Math.round(Math.cos(a) * ring), 1, (int) Math.round(Math.sin(a) * ring), 0, 0);
			double b = treeAngle + q * Math.PI / 2;
			int px = (int) Math.round(Math.cos(b) * ring), pz = (int) Math.round(Math.sin(b) * ring);
			if (t.hasTrees()) {
				Build.tree(bp, px, 1, pz, 5, t.log(), t.leaves(), rnd);
			} else {
				Build.pillar(bp, px, 1, pz, 3, t.pillar());
				bp.set(px, 4, pz, t.light());
			}
		}
	}

	/** A chest that faces the middle of its island, marked {@code mark}. */
	private static void chest(Blueprint bp, String mark, int x, int y, int z, int cx, int cz) {
		int dx = cx - x, dz = cz - z;
		net.minecraft.core.Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
				: (dz > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
		bp.set(x, y, z, Build.facing("chest", dir));
		bp.mark(mark, x, y, z);
	}

	/** Murder Mystery: the market square of Prop Hunt (full of hiding places), with spawns spread over it. */
	public static Blueprint murder(Theme t, RandomSource rnd) {
		return PropHuntLayout.design(t, rnd, List.of("minecraft:barrel", "minecraft:hay_block", "minecraft:bookshelf",
				"minecraft:pumpkin", "minecraft:oak_log", "minecraft:crafting_table"));
	}
}
