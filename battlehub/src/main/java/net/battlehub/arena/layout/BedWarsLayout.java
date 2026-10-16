package net.battlehub.arena.layout;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.battlehub.util.Ids;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Bed Wars: one floating island per team (2 to 8) around a centre island, diamond islands between the teams and
 * emerald generators in the middle. Marks per team i: spawn_i, bed_i (the foot), gen_i (iron and gold), shop_i;
 * plus gen_diamond and gen_emerald. Island floors are at y = 0; bridges have to be built. The arena box reaches
 * {@code buildHeight} above and {@code buildDepth} below the floors and past the islands, so everything players
 * build is inside it (and cleared afterwards).
 */
public final class BedWarsLayout {
	private BedWarsLayout() {}

	/** Team colours in team order. */
	public static final String[] COLORS = {"red", "blue", "lime", "yellow", "light_blue", "white", "pink", "gray"};

	public static BlockState bed(String color, Direction facing, BedPart part) {
		BlockState st = Ids.state("minecraft:" + color + "_bed");
		if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		}
		if (st.hasProperty(BlockStateProperties.BED_PART)) {
			st = st.setValue(BlockStateProperties.BED_PART, part);
		}
		return st;
	}

	/** Distance from the centre to the team islands (more islands, a wider circle). */
	public static int teamRadius(int teams) {
		return teams <= 2 ? 36 : teams <= 4 ? 40 : teams <= 6 ? 48 : 56;
	}

	/** Players may build this far past the team islands. */
	public static final int BUILD_MARGIN = 12;

	public static Blueprint design(Theme t, RandomSource rnd, int teams) {
		return design(t, rnd, teams, 24, 10);
	}

	public static Blueprint design(Theme t, RandomSource rnd, int teams, int buildHeight, int buildDepth) {
		Blueprint bp = new Blueprint();
		int n = Math.max(2, Math.min(COLORS.length, teams));
		int r = teamRadius(n);
		double start = n == 2 ? Math.PI : Math.PI / 2;
		net.battlehub.arena.gen.Style style = net.battlehub.arena.gen.Style.of(t, rnd, r);
		for (int i = 0; i < n; i++) {
			teamIsland(bp, t, style, rnd, i, start + i * 2 * Math.PI / n, r);
		}
		// A diamond island between every two teams; with more than four teams, four of them
		int diamonds = Math.min(n, 4);
		double ring = n <= 4 ? r * 0.62 : r * 0.55;
		for (int k = 0; k < diamonds; k++) {
			double a = start + (k + 0.5) * 2 * Math.PI / diamonds;
			diamondIsland(bp, t, style, rnd, (int) Math.round(Math.cos(a) * ring), (int) Math.round(Math.sin(a) * ring));
		}
		centre(bp, t, style, rnd);
		Decor.ring(bp, t, rnd, r + 14, r + 24, Math.min(-18, -buildDepth - 8));
		int edge = r + BUILD_MARGIN;
		bp.include(-edge, -buildDepth, -edge);
		bp.include(edge, buildHeight, edge);
		bp.mark("build_min", -edge, -buildDepth, -edge);
		bp.mark("build_max", edge, buildHeight, edge);
		// The winners' podium is built above the centre when the game is over
		bp.mark("podium", 0, Math.min(buildHeight, 14), 0);
		bp.spectator(0.5, Math.max(30, buildHeight + 8), 0.5, 0, 89);
		// Below the lowest place one can build: the decoration far below is out of play
		bp.voidY(-buildDepth - 3);
		bp.radius(r + 26);
		return bp;
	}

	private static Direction nearest(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static void teamIsland(Blueprint bp, Theme t, net.battlehub.arena.gen.Style style, RandomSource rnd, int team, double angle, int r) {
		String color = COLORS[team];
		double ox = Math.cos(angle);
		double oz = Math.sin(angle);
		int cx = (int) Math.round(ox * r);
		int cz = (int) Math.round(oz * r);
		// Outward along the island's axis (away from the centre) and sideways
		Direction out = nearest(ox, oz);
		Direction side = out.getClockWise();
		Build.island(bp, cx, 0, cz, 8.5, 6, (x, z) -> style.floor(x, z, rnd), t.under(), t.rough(), rnd);
		BlockState teamBlock = Ids.state("minecraft:" + color + "_concrete");
		BlockState teamWool = Ids.state("minecraft:" + color + "_wool");
		// A coloured border so everyone knows whose island it is
		Build.ring(bp, cx, 0, cz, 7.6, 8.6, () -> teamBlock);
		// Spawn in the middle, facing the centre
		float yaw = (float) Math.toDegrees(Math.atan2(-ox, oz)) + 180f;
		bp.spawn(cx + 0.5, 1, cz + 0.5, yaw);
		bp.mark("spawn_" + team, cx, 1, cz);
		// Bed at the back: foot nearer the middle, head further out, on a wool plinth
		int bx = cx + out.getStepX() * 5;
		int bz = cz + out.getStepZ() * 5;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				int px = bx + side.getStepX() * dx + out.getStepX() * dz;
				int pz = bz + side.getStepZ() * dx + out.getStepZ() * dz;
				bp.set(px, 0, pz, teamWool);
			}
		}
		bp.set(bx, 1, bz, bed(color, out, BedPart.FOOT));
		bp.set(bx + out.getStepX(), 1, bz + out.getStepZ(), bed(color, out, BedPart.HEAD));
		bp.mark("bed_" + team, bx, 1, bz);
		// Generator at the front, towards the centre
		int gx = cx - out.getStepX() * 5;
		int gz = cz - out.getStepZ() * 5;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				bp.set(gx + dx, 0, gz + dz, Ids.state("minecraft:iron_block"));
			}
		}
		bp.set(gx, 0, gz, Ids.state("minecraft:gold_block"));
		bp.mark("gen_" + team, gx, 1, gz);
		// Shop on the side: a barrel to click, and the shopkeeper next to it
		int sx = cx + side.getStepX() * 5;
		int sz = cz + side.getStepZ() * 5;
		bp.set(sx, 0, sz, t.trim());
		bp.set(sx + side.getStepX(), 1, sz + side.getStepZ(), Build.facing("minecraft:barrel", side.getOpposite()));
		bp.set(sx + side.getStepX(), 2, sz + side.getStepZ(), t.light());
		bp.mark("shop_" + team, sx, 1, sz);
		bp.mark("shopblock_" + team, sx + side.getStepX(), 1, sz + side.getStepZ());
	}

	private static void diamondIsland(Blueprint bp, Theme t, net.battlehub.arena.gen.Style style, RandomSource rnd, int x, int z) {
		Build.island(bp, x, 0, z, 3.6, 4, (px, pz) -> style.floor(px, pz, rnd), t.under(), t.rough(), rnd);
		bp.set(x, 0, z, Ids.state("minecraft:diamond_block"));
		bp.mark("gen_diamond", x, 1, z);
	}

	private static void centre(Blueprint bp, Theme t, net.battlehub.arena.gen.Style style, RandomSource rnd) {
		Build.island(bp, 0, 0, 0, 7.5, 7, (x, z) -> style.floor(x, z, rnd), t.under(), t.rough(), rnd);
		for (int sx : new int[] {-2, 2}) {
			bp.set(sx, 0, 0, Ids.state("minecraft:emerald_block"));
			bp.mark("gen_emerald", sx, 1, 0);
		}
		for (int[] c : new int[][] {{-4, -4}, {4, 4}, {-4, 4}, {4, -4}}) {
			Build.pillar(bp, c[0], 1, c[1], 3, t.pillar());
			bp.set(c[0], 4, c[1], t.light());
		}
	}
}
