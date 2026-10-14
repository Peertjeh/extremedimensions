package net.battlehub.arena.layout;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.battlehub.util.Ids;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Prop Hunt: a walled market square full of everyday objects (barrels, crates, hay, bookshelves, pumpkins) for
 * props to blend in with, a few stalls and a fountain, and a glass cage where the hunters wait while the props
 * hide. Marks: hunter_cage (inside the cage), hunter_release (in front of it); spawns spread over the square.
 */
public final class PropHuntLayout {
	private PropHuntLayout() {}

	public static final int HALF = 22;

	public static Blueprint design(Theme t, RandomSource rnd, List<String> disguises) {
		Blueprint bp = new Blueprint();
		int h = HALF;
		// Floor and foundation
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				bp.set(x, 0, z, t.floor(rnd));
				bp.set(x, -1, z, t.under());
			}
		}
		// Wall all round (5 high) with lights on top
		for (int i = -h; i <= h; i++) {
			for (int y = 1; y <= 5; y++) {
				bp.set(i, y, -h, t.wall());
				bp.set(i, y, h, t.wall());
				bp.set(-h, y, i, t.wall());
				bp.set(h, y, i, t.wall());
			}
			if (i % 5 == 0) {
				bp.set(i, 6, -h, t.light());
				bp.set(i, 6, h, t.light());
				bp.set(-h, 6, i, t.light());
				bp.set(h, 6, i, t.light());
			}
		}
		cage(bp, t, 0, -h + 3);
		fountain(bp, t, 0, 0);
		List<BlockState> objects = objects(disguises);
		// Market stalls around the fountain
		int[][] stalls = {{-11, -8}, {11, -8}, {-11, 9}, {11, 9}, {0, 13}};
		for (int[] s : stalls) {
			stall(bp, t, rnd, s[0], s[1], objects);
		}
		// Corners: a little library and a farm corner
		library(bp, t, -h + 2, h - 6);
		farm(bp, rnd, h - 7, h - 7);
		// Loose objects everywhere else, some stacked two high
		int placed = 0;
		for (int tries = 0; tries < 900 && placed < 110; tries++) {
			int x = rnd.nextInt(2 * h - 3) - h + 2;
			int z = rnd.nextInt(2 * h - 3) - h + 2;
			if (reserved(x, z) || bp.has(x, 1, z)) continue;
			bp.set(x, 1, z, objects.get(rnd.nextInt(objects.size())));
			if (rnd.nextInt(5) == 0) {
				bp.set(x, 2, z, objects.get(rnd.nextInt(objects.size())));
			}
			placed++;
		}
		// Props start spread over the square, hunters in the cage
		bp.spawnRing(16, 9, 1);
		bp.mark("hunter_cage", 0, 1, -h + 3);
		bp.mark("hunter_release", 0, 1, -h + 7);
		bp.spectator(0.5, 24, 0.5, 0, 75);
		bp.voidY(-8);
		bp.radius(h + 14);
		return bp;
	}

	/** Free paths: spawns, the cage and its door, the fountain. */
	private static boolean reserved(int x, int z) {
		double d = Build.dist(x, z);
		if (d < 4.5 || (d > 7.5 && d < 10.5)) return true;
		return Math.abs(x) <= 4 && z <= -HALF + 9;
	}

	/** Full blocks from the disguise list (what props can turn into is also what the square is full of). */
	static List<BlockState> objects(List<String> disguises) {
		List<BlockState> out = new ArrayList<>();
		for (String id : disguises) {
			BlockState st = Ids.state(id);
			if (!st.isAir()) out.add(st);
		}
		if (out.isEmpty()) {
			out.add(Blocks.BARREL.defaultBlockState());
			out.add(Blocks.HAY_BLOCK.defaultBlockState());
		}
		return out;
	}

	private static void cage(Blueprint bp, Theme t, int cx, int cz) {
		BlockState glass = Ids.state("minecraft:glass");
		for (int x = cx - 3; x <= cx + 3; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				bp.set(x, 0, z, t.trim());
				boolean edge = x == cx - 3 || x == cx + 3 || z == cz - 2 || z == cz + 2;
				for (int y = 1; y <= 3; y++) {
					if (edge) bp.set(x, y, z, glass);
					else bp.remove(x, y, z);
				}
				bp.set(x, 4, z, glass);
			}
		}
		bp.set(cx, 4, cz, t.light());
	}

	private static void fountain(Blueprint bp, Theme t, int cx, int cz) {
		Build.ring(bp, cx, 1, cz, 2.2, 3.2, t::trim);
		Build.disc(bp, cx, 0, cz, 2.2, () -> Ids.state("minecraft:water"));
		Build.pillar(bp, cx, 1, cz, 2, t.pillar());
		bp.set(cx, 3, cz, t.light());
	}

	private static void stall(Blueprint bp, Theme t, RandomSource rnd, int cx, int cz, List<BlockState> objects) {
		BlockState fence = Ids.state("minecraft:oak_fence");
		String[] wool = {"red", "white", "yellow", "lime", "light_blue", "orange"};
		BlockState roof = Ids.state("minecraft:" + wool[rnd.nextInt(wool.length)] + "_wool");
		for (int[] c : new int[][] {{-2, -1}, {2, -1}, {-2, 1}, {2, 1}}) {
			Build.pillar(bp, cx + c[0], 1, cz + c[1], 3, fence);
		}
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				bp.set(x, 4, z, roof);
			}
		}
		for (int x = cx - 1; x <= cx + 1; x++) {
			bp.set(x, 1, cz, objects.get(rnd.nextInt(objects.size())));
		}
	}

	private static void library(Blueprint bp, Theme t, int x0, int z0) {
		BlockState shelf = Ids.state("minecraft:bookshelf");
		for (int x = x0; x < x0 + 5; x++) {
			for (int y = 1; y <= 2; y++) {
				bp.set(x, y, z0 + 4, shelf);
				if (x % 2 == 0) bp.set(x, y, z0 + 1, shelf);
			}
		}
		bp.set(x0 + 2, 1, z0 + 2, Build.facing("minecraft:lectern", Direction.SOUTH));
	}

	private static void farm(Blueprint bp, RandomSource rnd, int x0, int z0) {
		for (int x = x0; x < x0 + 5; x++) {
			for (int z = z0; z < z0 + 5; z++) {
				int r = rnd.nextInt(6);
				if (r == 0) bp.set(x, 1, z, Ids.state("minecraft:pumpkin"));
				else if (r == 1) bp.set(x, 1, z, Ids.state("minecraft:hay_block"));
				else if (r == 2) bp.set(x, 1, z, Ids.state("minecraft:melon"));
			}
		}
		bp.set(x0 + 2, 1, z0 + 2, Ids.state("minecraft:composter"));
	}
}
