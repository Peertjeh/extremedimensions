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

	/**
	 * SkyWars: one island per player on a ring (at least four), smaller islands between them and a big middle
	 * island. Marks: chest (island chests), center_chest (the better ones in the middle).
	 */
	public static Blueprint skywars(Theme t, RandomSource rnd, int players) {
		Blueprint bp = new Blueprint();
		int n = Math.max(4, players);
		double ring = Math.max(20, n * 3.2);
		BlockState chest = Build.s("chest");
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			int cx = (int) Math.round(Math.cos(a) * ring);
			int cz = (int) Math.round(Math.sin(a) * ring);
			Build.island(bp, cx, 0, cz, 3.6, 4, () -> t.floor(rnd), t.under(), t.rough(), rnd);
			// A chest at the outer edge, a little tree or pillar on the side
			int ox = (int) Math.round(Math.cos(a) * 2);
			int oz = (int) Math.round(Math.sin(a) * 2);
			bp.set(cx + ox, 1, cz + oz, chest);
			bp.mark("chest", cx + ox, 1, cz + oz);
			if (t.hasTrees() && rnd.nextBoolean()) {
				Build.tree(bp, cx - oz, 1, cz + ox, 4, t.log(), t.leaves(), rnd);
			} else {
				Build.pillar(bp, cx - oz, 1, cz + ox, 2, t.pillar());
			}
			float yaw = (float) Math.toDegrees(Math.atan2(cx, -cz));
			bp.spawn(cx - ox * 0.5 + 0.5, 1, cz - oz * 0.5 + 0.5, yaw);
			// A stepping-stone island halfway to the next island
			double b = (i + 0.5) * Math.PI * 2 / n;
			int mx = (int) Math.round(Math.cos(b) * ring * 0.62);
			int mz = (int) Math.round(Math.sin(b) * ring * 0.62);
			Build.island(bp, mx, -1, mz, 2.2, 3, t::accent, t.under(), t.rough(), rnd);
			if (i % 2 == 0) {
				bp.set(mx, 0, mz, chest);
				bp.mark("chest", mx, 0, mz);
			}
		}
		// The middle: a big island with four chests round a pillar
		Build.island(bp, 0, 0, 0, 7.5, 6, () -> t.floor(rnd), t.under(), t.rough(), rnd);
		Build.pillar(bp, 0, 1, 0, 4, t.pillar());
		bp.set(0, 5, 0, t.light());
		int[][] cc = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
		for (int[] c : cc) {
			bp.set(c[0], 1, c[1], chest);
			bp.mark("center_chest", c[0], 1, c[1]);
		}
		bp.include((int) -ring - 8, -12, (int) -ring - 8);
		bp.include((int) ring + 8, 20, (int) ring + 8);
		// Bridging between the islands is fine; far out into the void or high up in the sky is not
		bp.playArea(Blueprint.circle((int) ring + 8), 18);
		bp.spectator(0.5, 30, -(ring + 10) + 0.5, 0, 40);
		bp.voidY(-14);
		bp.radius((int) ring + 24);
		return bp;
	}

	/** Murder Mystery: the market square of Prop Hunt (full of hiding places), with spawns spread over it. */
	public static Blueprint murder(Theme t, RandomSource rnd) {
		return PropHuntLayout.design(t, rnd, List.of("minecraft:barrel", "minecraft:hay_block", "minecraft:bookshelf",
				"minecraft:pumpkin", "minecraft:oak_log", "minecraft:crafting_table"));
	}
}
