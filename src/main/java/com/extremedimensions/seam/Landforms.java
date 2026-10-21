package com.extremedimensions.seam;

import static com.extremedimensions.seam.SeamArchitect.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shapes of the land itself, beyond the roll of an island's top: arches of rock the weather has worn through, and
 * terraces cut into a hillside. Each is a feature of the open ground (see {@link SeamArchitect#grounds}), its feet sunk
 * into the island's turf, so nothing of it hangs over the nothing.
 */
public final class Landforms {
	private Landforms() {}

	/** The open ground (half-width) and the clear air each needs. */
	static final int ARCH_ROOM = 4, ARCH_TALL = 14, TERRACE_ROOM = 8, TERRACE_TALL = 10;

	/**
	 * An arch of rock: two feet ten to fifteen blocks apart, joined by a worn bow of stone rising six to nine blocks,
	 * thick at its feet and thin at its crown, turf on its back and vines hanging from its underside.
	 */
	public static boolean rockArch(Plan p, Random rnd, int cx, int y, int cz) {
		long seed = rnd.nextLong();
		double ang = rnd.nextDouble() * Math.PI;
		double ux = Math.cos(ang), uz = Math.sin(ang);
		double half = 5 + rnd.nextDouble() * 2.5, rise = 6 + rnd.nextDouble() * 3;
		if (!fits(p, cx, y, cz, ux, uz, half, rise)) {
			return false;
		}
		BlockState[] rock = rockGrad();
		// The arch's own blocks, by column: its highest and lowest block there
		Map<Long, int[]> columns = new HashMap<>();
		for (double t = -1.0; t <= 1.0001; t += 0.04) {
			double bx = cx + ux * t * half, bz = cz + uz * t * half;
			double by = y - 1 + rise * Math.sqrt(Math.max(0, 1 - t * t));
			double r = 1.3 + 1.4 * t * t + 0.6 * (noise(seed, bx / 2.0, bz / 2.0) - 0.5);
			int ir = (int) Math.ceil(r) + 1;
			for (int ox = -ir; ox <= ir; ox++) {
				for (int oy = -ir; oy <= ir; oy++) {
					for (int oz = -ir; oz <= ir; oz++) {
						if (ox * ox + oy * oy * 1.3 + oz * oz > r * r) {
							continue;
						}
						int x = (int) Math.floor(bx) + ox, yy = (int) Math.floor(by) + oy, z = (int) Math.floor(bz) + oz;
						// its feet stand in the turf, a block down
						if (yy < y - 1) {
							continue;
						}
						p.set(x, yy, z, grad(rock, Math.min(1, (yy - y + 1) / (rise + 2.0)) * 0.6, rnd));
						long col = BlockPos.asLong(x, 0, z);
						int[] c = columns.computeIfAbsent(col, k -> new int[] {yy, yy});
						c[0] = Math.max(c[0], yy);
						c[1] = Math.min(c[1], yy);
					}
				}
			}
		}
		BlockState vines = mc("cave_vines");
		for (Map.Entry<Long, int[]> e : columns.entrySet()) {
			int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
			int high = e.getValue()[0], low = e.getValue()[1];
			// Turf grows on its back, where nothing lies on it
			if (!p.has(x, high + 1, z) && rnd.nextInt(10) < 7) {
				p.set(x, high, z, rnd.nextInt(3) == 0 ? mc("pale_moss_block", moss()) : moss());
				if (rnd.nextInt(3) == 0) {
					p.soft(x, high + 1, z, rnd.nextBoolean() ? mc("short_grass") : mc("moss_carpet"));
				}
			}
			// and vines hang from its underside, where it stands clear of the ground
			if (vines != null && low > y + 1 && !p.has(x, low - 1, z) && rnd.nextInt(3) == 0) {
				int len = 1 + rnd.nextInt(3);
				for (int k = 1; k <= len && low - k > y && !p.has(x, low - k, z); k++) {
					p.set(x, low - k, z, vines);
				}
			}
		}
		return true;
	}

	/**
	 * Whether an arch fits: both its feet on the island's turf, near the height it is set at, and nothing built where
	 * its bow will stand.
	 */
	private static boolean fits(Plan p, int cx, int y, int cz, double ux, double uz, double half, double rise) {
		for (int side = -1; side <= 1; side += 2) {
			int fx = (int) Math.round(cx + ux * half * side), fz = (int) Math.round(cz + uz * half * side);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int g = p.ground(fx + dx, fz + dz, Integer.MIN_VALUE);
					if (g == Integer.MIN_VALUE || Math.abs(g - (y - 1)) > 2 || !turf(p.get(fx + dx, g, fz + dz))) {
						return false;
					}
				}
			}
		}
		for (double t = -1; t <= 1.0001; t += 0.1) {
			int x = (int) Math.round(cx + ux * t * half), z = (int) Math.round(cz + uz * t * half);
			int g = p.ground(x, z, y - 1);
			for (int yy = g + 2; yy <= y + rise + 3; yy++) {
				if (p.has(x, yy, z)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Terraces cut into a hillside: a low hill rising in three flat steps two blocks high, each step's face a retaining
	 * wall of worn hush stone, its tread turf, and a flight of steps up one side.
	 */
	public static void terraces(Plan p, Random rnd, int cx, int y, int cz) {
		long seed = rnd.nextLong();
		int steps = 3, rise = 2;
		double outer = 6.5;
		Direction up = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(seed));
		BlockState[] face = {cobble(), cobble(), mossy(), stone(), mc("mossy_cobblestone", cobble())};
		int room = (int) Math.ceil(outer) + 1;
		// The hill and an apron round it stand on ground levelled to its foot, so its first step rises from level turf
		for (int dx = -room - 1; dx <= room + 1; dx++) {
			for (int dz = -room - 1; dz <= room + 1; dz++) {
				if (dx * dx + dz * dz > (outer + 2) * (outer + 2)) {
					continue;
				}
				int x = cx + dx, z = cz + dz, g = p.ground(x, z, y - 1);
				for (int yy = g + 1; yy < y - 1; yy++) {
					p.set(x, yy, z, stone());
				}
				for (int yy = y; yy <= g + 2; yy++) {
					p.clear(x, yy, z);
				}
				p.set(x, y - 1, z, moss());
				p.setGround(x, z, y - 1);
			}
		}
		// (the flight runs in from the side the stairs face away from)
		int sx = up.getOpposite().getStepX(), sz = up.getOpposite().getStepZ();
		for (int dx = -room; dx <= room; dx++) {
			for (int dz = -room; dz <= room; dz++) {
				boolean flight = sx != 0 ? dz == 0 && dx * sx >= 0 : dx == 0 && dz * sz >= 0;
				int k = level(seed, cx, cz, dx, dz, steps, outer, !flight);
				if (k <= 0) {
					continue;
				}
				int x = cx + dx, z = cz + dz, top = y - 1 + k * rise;
				for (int yy = y; yy < top; yy++) {
					p.set(x, yy, z, face[rnd.nextInt(face.length)]);
				}
				p.set(x, top, z, rnd.nextInt(4) == 0 ? mc("pale_moss_block", moss()) : moss());
				p.clear(x, top + 1, z);
				if (rnd.nextInt(4) == 0) {
					p.soft(x, top + 1, z, rnd.nextInt(3) == 0 ? mc("fern") : mc("short_grass"));
				}
			}
		}
		// The flight up: in from the foot of the hill along one side, a stair on each face it meets
		for (int i = 0; i <= room; i++) {
			int inner = level(seed, cx, cz, sx * i, sz * i, steps, outer, false), out = level(seed, cx, cz, sx * (i + 1), sz * (i + 1), steps, outer, false);
			if (inner <= out) {
				continue;
			}
			int low = y - 1 + out * rise;
			int ox = cx + sx * (i + 1), oz = cz + sz * (i + 1), ix = cx + sx * i, iz = cz + sz * i;
			for (int k = 0; k < (inner - out) * rise; k++) {
				// one stair a block: the first at the foot of the face, the next in the tread above it
				int x = k == 0 ? ox : ix, z = k == 0 ? oz : iz;
				p.set(x, low + 1 + k, z, stairs(cobbleStairs(), up, false));
				p.clear(x, low + 2 + k, z);
				p.clear(x, low + 3 + k, z);
			}
		}
	}

	/**
	 * How many steps up a column of the terraced hill stands (0 off the hill): rings a little worn out of round, but true
	 * along the flight, so each tread it crosses is two blocks deep.
	 */
	private static int level(long seed, int cx, int cz, int dx, int dz, int steps, double outer, boolean worn) {
		double d = Math.sqrt(dx * dx + dz * dz) * (worn ? 0.9 + 0.2 * noise(seed, (cx + dx) / 3.0, (cz + dz) / 3.0) : 1);
		if (d > outer) {
			return 0;
		}
		return Math.min(steps, (int) Math.floor((outer - d) / (outer / steps)) + 1);
	}
}
