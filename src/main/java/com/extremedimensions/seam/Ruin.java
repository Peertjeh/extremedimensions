package com.extremedimensions.seam;

import static com.extremedimensions.seam.SeamArchitect.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.extremedimensions.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What time has done to a structure: its condition, from intact to a ruin, and the damage that goes with it.
 *
 * <p>Damage follows the building, not a coin tossed for each block. It falls where weather and collapse strike a real
 * building: on its crowns, parapets and the upper reaches of its walls far more than low down, never on its foundation
 * or the way in. Each blow takes a ragged bite out of the masonry (its edge drawn from noise, so it breaks along an
 * uneven line, the way a wall gives), and on a ruin whole crowns come down, cut off along a jagged line. Whatever is
 * left hanging without support falls too, so nothing floats that was not built to float. The broken edges crack and
 * gather moss, vines hang from them, glass near a break is gone, and the fallen stone lies in heaps at the building's
 * foot.
 */
public final class Ruin {
	private Ruin() {}

	public static final int INTACT = 0, WEATHERED = 1, DAMAGED = 2, RUINED = 3;

	/**
	 * The condition of a building: of every hundred, {@code seam_damage} are damaged or worse (a third of those ruins),
	 * the rest weathered (a few small breaks at the top) or, now and then, intact. A building of the ruined scheme is
	 * always a ruin.
	 */
	public static int condition(Random rnd, boolean ruinScheme) {
		int roll = rnd.nextInt(100);
		if (ruinScheme) {
			return RUINED;
		}
		int share = Math.max(0, Math.min(100, Config.SEAM_DAMAGE));
		if (roll < share) {
			return rnd.nextInt(3) == 0 ? RUINED : DAMAGED;
		}
		return rnd.nextInt(5) == 0 ? INTACT : WEATHERED;
	}

	private static long key(int x, int y, int z) {
		return BlockPos.asLong(x, y, z);
	}

	/** Every block of a plan joined to its foot (within a block of {@code base}) through the plan's own blocks. */
	private static Set<Long> grounded(Plan p, int base) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<Long> todo = new ArrayDeque<>();
		for (long k : p.blocks.keySet()) {
			if (BlockPos.getY(k) <= base + 1) {
				seen.add(k);
				todo.add(k);
			}
		}
		int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!todo.isEmpty()) {
			long k = todo.poll();
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			for (int[] n : nb) {
				long m = key(x + n[0], y + n[1], z + n[2]);
				if (!seen.contains(m) && p.blocks.containsKey(m)) {
					seen.add(m);
					todo.add(m);
				}
			}
		}
		return seen;
	}

	/**
	 * Damages a structure (planned on its own, standing on the ground at {@code base}, its lowest floor at
	 * {@code base + 1}) as its condition says. The blocks kept whole are its foundation (up to {@code base + 3}) and
	 * whatever its actions (barrels) stand on.
	 */
	public static void apply(Plan p, Random rnd, int condition, int base) {
		if (condition <= INTACT || p.blocks.isEmpty()) {
			return;
		}
		int top = base;
		for (long k : p.blocks.keySet()) {
			top = Math.max(top, BlockPos.getY(k));
		}
		int height = Math.max(8, top - base);
		// What its actions (a reliquary) stand on is kept whole, the whole column under them and round them, so they
		// never end up hanging over a fall
		Set<Long> keep = new HashSet<>(), support = new HashSet<>();
		for (Object[] a : p.after) {
			BlockPos at = (BlockPos) a[0];
			support.add(at.asLong());
			support.add(at.below().asLong());
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int y = base; y <= at.getY() + 1; y++) {
						keep.add(key(at.getX() + dx, y, at.getZ() + dz));
					}
				}
			}
		}
		Set<Long> before = grounded(p, base);
		// The outer skin: blocks with open air beside them, where weather and collapse strike
		List<long[]> skin = new ArrayList<>();
		for (long k : p.blocks.keySet()) {
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			if (y <= base + 3) {
				continue;
			}
			if (!p.has(x + 1, y, z) || !p.has(x - 1, y, z) || !p.has(x, y, z + 1) || !p.has(x, y, z - 1) || !p.has(x, y + 1, z)) {
				skin.add(new long[] {x, y, z});
			}
		}
		if (skin.isEmpty()) {
			return;
		}
		int sites = switch (condition) {
			case WEATHERED -> 1 + rnd.nextInt(2);
			case DAMAGED -> 4 + rnd.nextInt(4);
			default -> 8 + rnd.nextInt(6);
		};
		double size = condition == WEATHERED ? 1.7 : condition == DAMAGED ? 3.1 : 4.0;
		// the lowest a blow lands: on what is barely touched, only the top third; on a ruin, down to the second floor
		double lowest = condition == WEATHERED ? 0.66 : condition == DAMAGED ? 0.35 : 0.12;
		long noiseSeed = rnd.nextLong();
		Set<Long> gone = new HashSet<>();
		for (int s = 0, tries = 0; s < sites && tries < sites * 40; tries++) {
			long[] c = skin.get(rnd.nextInt(skin.size()));
			double up = (c[1] - base) / (double) height;
			// far more often high up than low down
			if (up < lowest || rnd.nextDouble() > Math.pow(up, 1.3) + 0.08) {
				continue;
			}
			double rad = size * (0.7 + rnd.nextDouble() * 0.7);
			bite(p, gone, keep, base, (int) c[0], (int) c[1], (int) c[2], rad, noiseSeed + s);
			s++;
		}
		// On a ruin, and sometimes on a damaged building, the highest crowns come down along a jagged line
		int crowns = condition == RUINED ? 2 + rnd.nextInt(2) : condition == DAMAGED && rnd.nextInt(3) == 0 ? 1 : 0;
		for (int k = 0; k < crowns; k++) {
			long[] peak = null;
			for (int tries = 0; tries < 60; tries++) {
				long[] c = skin.get(rnd.nextInt(skin.size()));
				if (!gone.contains(key((int) c[0], (int) c[1], (int) c[2])) && (peak == null || c[1] > peak[1])) {
					peak = c;
				}
			}
			if (peak == null) {
				break;
			}
			int cut = (int) peak[1] - 4 - rnd.nextInt(Math.max(1, (int) ((peak[1] - base) * 0.25)));
			fell(p, gone, keep, base, (int) peak[0], cut, (int) peak[2], 5 + rnd.nextInt(4), noiseSeed + 100 + k);
		}
		// The broken edges wear (and glass beside a break shatters) before what is left hanging is found
		weather(p, rnd, gone, base, keep);
		// What was left hanging falls too (not what was built to float, which never stood on the ground)
		Set<Long> after = grounded(p, base);
		List<Long> loose = new ArrayList<>();
		for (long k : before) {
			if (p.blocks.containsKey(k) && !after.contains(k) && !support.contains(k)) {
				loose.add(k);
			}
		}
		for (long k : loose) {
			p.blocks.remove(k);
			gone.add(k);
		}
		vines(p, rnd, gone, base);
		rubble(p, rnd, gone, base, condition);
	}

	/** A ragged bite out of the masonry round a point: an uneven ball, a little taller than wide. */
	private static void bite(Plan p, Set<Long> gone, Set<Long> keep, int base, int cx, int cy, int cz, double rad, long seed) {
		int r = (int) Math.ceil(rad * 1.6) + 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					int x = cx + dx, y = cy + dy, z = cz + dz;
					long k = key(x, y, z);
					if (y <= base + 3 || keep.contains(k) || !p.blocks.containsKey(k)) {
						continue;
					}
					double d = Math.sqrt(dx * dx + dy * dy / 1.6 + dz * dz);
					double edge = rad * (0.7 + 0.6 * noise(seed, (x + y * 0.7) / 2.3, (z - y * 0.5) / 2.3));
					if (d < edge) {
						p.blocks.remove(k);
						gone.add(k);
					}
				}
			}
		}
	}

	/** A crown come down: everything above a jagged line, within a radius of a point. */
	private static void fell(Plan p, Set<Long> gone, Set<Long> keep, int base, int cx, int cut, int cz, int rad, long seed) {
		List<Long> down = new ArrayList<>();
		for (long k : p.blocks.keySet()) {
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			int dx = x - cx, dz = z - cz;
			if (dx * dx + dz * dz > rad * rad || y <= base + 3 || keep.contains(k)) {
				continue;
			}
			double line = cut + (noise(seed, x / 2.0, z / 2.0) - 0.5) * 6;
			if (y > line) {
				down.add(k);
			}
		}
		for (long k : down) {
			p.blocks.remove(k);
			gone.add(k);
		}
	}

	/** Masonry worn at a break: cracked, mossy or rough. */
	private static BlockState worn(BlockState s, Random rnd) {
		if (s == brick() || s == tile()) {
			return rnd.nextInt(3) == 0 ? mossy() : cracked();
		}
		if (s == stone() || s == smooth()) {
			return cobble();
		}
		if (s == bsBrick()) {
			return mc("cracked_polished_blackstone_bricks", s);
		}
		return s;
	}

	/** The broken edges: cracked and mossy, a slab left where a course broke off, glass near a break gone. */
	private static void weather(Plan p, Random rnd, Set<Long> gone, int base, Set<Long> keep) {
		Map<BlockState, BlockState> slabs = new HashMap<>();
		slabs.put(tile(), tileSlab());
		slabs.put(pale(), paleSlab());
		slabs.put(smooth(), smoothSlab());
		slabs.put(palePol(), palePolSlab());
		slabs.put(brick(), tileSlab());
		int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		Set<Long> edge = new HashSet<>();
		for (long k : gone) {
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			for (int[] n : nb) {
				long m = key(x + n[0], y + n[1], z + n[2]);
				if (p.blocks.containsKey(m)) {
					edge.add(m);
				}
			}
		}
		for (long k : edge) {
			if (keep.contains(k)) {
				continue;
			}
			BlockState s = p.blocks.get(k);
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("glass")) {
				// glass beside a break shatters
				if (rnd.nextInt(3) > 0) {
					p.blocks.remove(k);
				}
				continue;
			}
			boolean open = !p.has(x, y + 1, z) && gone.contains(key(x, y + 1, z));
			if (open && rnd.nextInt(4) == 0 && slabs.containsKey(s)) {
				p.blocks.put(k, slab(slabs.get(s), false));
			} else if (rnd.nextInt(2) == 0) {
				p.blocks.put(k, worn(s, rnd));
			}
		}
	}

	/** Vines hanging from the undersides of broken courses (from what still stands, once the loose has fallen). */
	private static void vines(Plan p, Random rnd, Set<Long> gone, int base) {
		BlockState vines = mc("cave_vines");
		if (vines == null) {
			return;
		}
		List<long[]> hang = new ArrayList<>();
		for (long k : gone) {
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			if (!p.has(x, y, z) && p.has(x, y + 1, z) && rnd.nextInt(6) == 0) {
				hang.add(new long[] {x, y, z, 1 + rnd.nextInt(4)});
			}
		}
		for (long[] h : hang) {
			for (int j = 0; j < h[3] && !p.has((int) h[0], (int) h[1] - j, (int) h[2]) && h[1] - j > base + 1; j++) {
				p.blocks.put(key((int) h[0], (int) (h[1] - j), (int) h[2]), vines);
			}
		}
	}

	/**
	 * The fallen stone, in heaps at the building's foot: some of what came down lands below where it fell, on the first
	 * thing under it (a floor, a terrace, or the ground at {@code base + 1}), two high at most.
	 */
	private static void rubble(Plan p, Random rnd, Set<Long> gone, int base, int condition) {
		double share = condition == WEATHERED ? 0.1 : condition == DAMAGED ? 0.18 : 0.24;
		BlockState[] heap = {cobble(), cobble(), mc("mossy_cobblestone", cobble()), cracked(), mc("tuff", stone())};
		BlockState stairs = cobbleStairs();
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		for (long k : gone) {
			if (rnd.nextDouble() > share) {
				continue;
			}
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			// it lands a little out from where it fell
			x += rnd.nextInt(3) - 1;
			z += rnd.nextInt(3) - 1;
			int land = y;
			while (land > base + 1 && !p.has(x, land - 1, z)) {
				land--;
			}
			if (p.has(x, land, z)) {
				continue;
			}
			// a heap is two high at most
			if (p.has(x, land - 1, z) && p.has(x, land - 2, z) && land - 2 > base && isRubble(p.get(x, land - 1, z)) && isRubble(p.get(x, land - 2, z))) {
				continue;
			}
			BlockState b = rnd.nextInt(4) == 0 ? stairs(stairs, dirs[rnd.nextInt(4)], false) : heap[rnd.nextInt(heap.length)];
			p.blocks.put(key(x, land, z), b);
		}
	}

	private static boolean isRubble(BlockState s) {
		return s != null && (s == cobble() || s == mc("mossy_cobblestone", cobble()) || s == cracked() || s == mc("tuff", stone()) || s.getBlock() == cobbleStairs().getBlock());
	}
}
