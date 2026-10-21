package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.seam.Colossus;
import com.extremedimensions.seam.Ruin;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.seam.Terrain;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground of the Seam and what stands on it: an island's top rolls but never jumps, its coast is no circle and its
 * underside no stack of columns; whatever is built on an island stands on ground, overhang or not; and damage takes
 * what age takes, leaving nothing hanging in the air and the foot of a building whole.
 */
public class GroundTests {
	/** A great island rolls (its surface rises and falls a few blocks) and almost nowhere steps more than a block. */
	@GameTest
	public void theGroundRollsButNeverJumps(GameTestHelper helper) {
		SeamArchitect.Shape s = SeamArchitect.Shape.of(new Random(4), 0, 100, 0, 32);
		SeamArchitect.Plan p = new SeamArchitect.Plan();
		Terrain t = Terrain.of(new Random(5), s, 0);
		t.emit(p);
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, pairs = 0, steps = 0, under = 0, cliffs = 0;
		for (int x = -45; x <= 45; x++) {
			for (int z = -45; z <= 45; z++) {
				int y = t.surface(x, z, Integer.MIN_VALUE);
				if (y == Integer.MIN_VALUE) {
					continue;
				}
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
				for (int[] n : new int[][] {{1, 0}, {0, 1}}) {
					int ny = t.surface(x + n[0], z + n[1], Integer.MIN_VALUE);
					if (ny == Integer.MIN_VALUE) {
						continue;
					}
					pairs++;
					steps += Math.abs(ny - y) > 1 ? 1 : 0;
					under++;
					cliffs += Math.abs(t.bottomAt(x + n[0], z + n[1], 0) - t.bottomAt(x, z, 0)) > 4 ? 1 : 0;
				}
			}
		}
		check(helper, hi - lo >= 3, "the island's top is flat (%s to %s)", lo, hi);
		check(helper, steps * 50 < pairs, "the island's top steps two or more blocks between %s of %s neighbours", steps, pairs);
		check(helper, cliffs * 25 < under, "the island's underside jumps more than four blocks between %s of %s neighbours", cliffs, under);
		// the coast wanders: its distance from the middle differs by a tenth of the radius or more
		double near = s.nearest(), far = 0;
		for (int i = 0; i < 360; i += 3) {
			far = Math.max(far, s.edge(Math.toRadians(i)));
		}
		check(helper, far - near >= s.r() / 10.0, "the coast is all but a circle (%s to %s)", near, far);
		helper.succeed();
	}

	/**
	 * Whatever is built on an island stands on ground: under every block of a village, a loom or a colossal building at
	 * its floor there is the island (levelled, or a ledge grown under it); and a cathedral has its plinth.
	 */
	@GameTest(maxTicks = 300)
	public void whatIsBuiltStandsOnGround(GameTestHelper helper) {
		int top = 100;
		String[] names = {"village", "loom", "spire"};
		int[] kinds = {2, 1, 3};
		for (int k = 0; k < kinds.length; k++) {
			for (int seed = 0; seed < 3; seed++) {
				SeamArchitect.Plan p = SeamService.showpiece(500L + seed * 17 + k, 0, top, 0, kinds[k]);
				int bare = 0, stood = 0;
				for (long key : p.positions()) {
					if (BlockPos.getY(key) != top + 1) {
						continue;
					}
					int x = BlockPos.getX(key), z = BlockPos.getZ(key);
					BlockState b = p.at(x, top + 1, z);
					if (b.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
						continue;
					}
					stood++;
					bare += p.has(x, top, z) ? 0 : 1;
				}
				check(helper, stood > 20, "the %s (seed %s) has hardly anything on the ground (%s blocks)", names[k], seed, stood);
				check(helper, bare == 0, "%s blocks of the %s (seed %s) stand over the nothing", bare, names[k], seed);
			}
		}
		// a cathedral's walls stand on a plinth all round (stepped plans stand on their own steps)
		for (int seed = 0; seed < 3; seed++) {
			SeamArchitect.Plan p = new SeamArchitect.Plan();
			Colossus.build(p, new Random(70 + seed), 0, top, 0, 36, Colossus.CATHEDRAL, Colossus.TWILIGHT);
			int plinth = 0;
			for (long key : p.positions()) {
				BlockState b = p.at(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
				plinth += BlockPos.getY(key) == top + 1 && b.getBlock() instanceof net.minecraft.world.level.block.StairBlock ? 1 : 0;
			}
			check(helper, plinth > 40, "the cathedral (seed %s) has no plinth round its foot (%s stairs)", seed, plinth);
		}
		helper.succeed();
	}

	/** A structure reaching out past a small island's coast stands on a ledge grown for it, faced in masonry. */
	@GameTest
	public void anOverhangStandsOnALedge(GameTestHelper helper) {
		int top = 100;
		SeamArchitect.Shape s = new SeamArchitect.Shape(0, top, 0, 10, 0.4, 1.7, 2.9);
		SeamArchitect.Plan world = new SeamArchitect.Plan();
		Terrain t = Terrain.of(new Random(8), s, 0);
		t.emit(world);
		SeamArchitect.Plan wall = new SeamArchitect.Plan();
		for (int x = -4; x <= 16; x++) {
			for (int y = top + 1; y <= top + 6; y++) {
				wall.set(x, y, 0, Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		t.settle(world, wall, top);
		world.merge(wall);
		for (int x = -4; x <= 16; x++) {
			check(helper, world.has(x, top, 0), "the wall stands over the nothing at x %s", x);
		}
		check(helper, world.count(Blocks.POLISHED_BLACKSTONE) > 0 || world.count(Blocks.POLISHED_BLACKSTONE_BRICKS) > 0, "the ledge shows no masonry");
		helper.succeed();
	}

	/** Every block of a plan joined to the ground (within a block of {@code base}) through the plan's own blocks. */
	private static Set<Long> grounded(SeamArchitect.Plan p, int base) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<Long> todo = new ArrayDeque<>();
		for (long k : p.positions()) {
			if (BlockPos.getY(k) <= base + 1) {
				seen.add(k);
				todo.add(k);
			}
		}
		while (!todo.isEmpty()) {
			long k = todo.poll();
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			for (int[] n : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
				long m = BlockPos.asLong(x + n[0], y + n[1], z + n[2]);
				if (!seen.contains(m) && p.has(x + n[0], y + n[1], z + n[2])) {
					seen.add(m);
					todo.add(m);
				}
			}
		}
		return seen;
	}

	/**
	 * A ruin takes much of a building, but leaves nothing that stood on the ground hanging in the air, and leaves its
	 * foot whole; the same ruin from the same seed falls the same way.
	 */
	@GameTest(maxTicks = 300)
	public void aRuinLeavesNothingHanging(GameTestHelper helper) {
		int top = 100;
		for (int plan = 0; plan < 5; plan++) {
			SeamArchitect.Plan p = new SeamArchitect.Plan(), q = new SeamArchitect.Plan();
			Colossus.build(p, new Random(300 + plan), 0, top, 0, 40, plan, Colossus.TWILIGHT);
			Colossus.build(q, new Random(300 + plan), 0, top, 0, 40, plan, Colossus.TWILIGHT);
			Set<Long> foot = new HashSet<>();
			for (long k : p.positions()) {
				if (BlockPos.getY(k) <= top + 1) {
					foot.add(k);
				}
			}
			Set<Long> stood = grounded(p, top);
			int before = p.size();
			Ruin.apply(p, new Random(9), Ruin.RUINED, top);
			Ruin.apply(q, new Random(9), Ruin.RUINED, top);
			check(helper, p.fingerprint() == q.fingerprint(), "plan %s fell two different ways from one seed", plan);
			check(helper, p.size() < before * 0.985, "plan %s as a ruin lost only %s of %s blocks", plan, before - p.size(), before);
			Set<Long> still = grounded(p, top);
			int hanging = 0;
			for (long k : stood) {
				if (p.positions().contains(k) && !still.contains(k)) {
					hanging++;
				}
			}
			check(helper, hanging == 0, "plan %s as a ruin leaves %s blocks hanging in the air", plan, hanging);
			for (long k : foot) {
				check(helper, p.positions().contains(k), "plan %s as a ruin lost a block of its foot at %s", plan, BlockPos.of(k));
			}
		}
		helper.succeed();
	}

	/** A hall: walls round a square, roofed, a parapet of whole blocks and walls in turn round the roof. */
	private static SeamArchitect.Plan hall(int top, int w, int h, BlockState body, BlockState whole, BlockState ornament) {
		SeamArchitect.Plan p = new SeamArchitect.Plan();
		for (int x = 0; x <= w; x++) {
			for (int z = 0; z <= w; z++) {
				boolean rim = x == 0 || z == 0 || x == w || z == w;
				p.set(x, top, z, body);
				p.set(x, top + h + 1, z, body);
				for (int y = top + 1; rim && y <= top + h; y++) {
					p.set(x, y, z, body);
				}
				if (rim) {
					p.set(x, top + h + 2, z, (x + z) % 2 == 0 ? whole : ornament);
				}
			}
		}
		return p;
	}

	/**
	 * Damage follows what each part does: the ornaments of a parapet go before its whole blocks, and the corners that
	 * carry a hall stand longer than the middle of its walls.
	 */
	@GameTest
	public void damageFollowsTheRole(GameTestHelper helper) {
		int top = 100, w = 10, h = 12;
		BlockState body = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), whole = Blocks.DEEPSLATE_TILES.defaultBlockState(),
				ornament = Blocks.DEEPSLATE_TILE_WALL.defaultBlockState();
		int piers = 0, piersLost = 0, walls = 0, wallsLost = 0, wholes = 0, wholesLost = 0, ornaments = 0, ornamentsLost = 0;
		for (int seed = 0; seed < 60; seed++) {
			// The walls are read on a ruin; the parapet on a damaged hall (a ruin's crowns come down whole, ornaments and all)
			SeamArchitect.Plan p = hall(top, w, h, body, whole, ornament), q = hall(top, w, h, body, whole, ornament);
			Ruin.apply(p, new Random(seed), Ruin.RUINED, top);
			Ruin.apply(q, new Random(seed), Ruin.DAMAGED, top);
			for (int x = 0; x <= w; x++) {
				for (int z = 0; z <= w; z++) {
					boolean corner = (x == 0 || x == w) && (z == 0 || z == w), rim = x == 0 || z == 0 || x == w || z == w;
					if (!rim) {
						continue;
					}
					// the lower half of the walls, below where whole crowns come down
					for (int y = top + 4; y <= top + 8; y++) {
						boolean kept = p.at(x, y, z) != null && p.at(x, y, z).is(Blocks.POLISHED_DEEPSLATE);
						if (corner) {
							piers++;
							piersLost += kept ? 0 : 1;
						} else {
							walls++;
							wallsLost += kept ? 0 : 1;
						}
					}
					BlockState was = (x + z) % 2 == 0 ? whole : ornament, now = q.at(x, top + h + 2, z);
					boolean kept = now != null && now.getBlock() == was.getBlock();
					if (was == whole) {
						wholes++;
						wholesLost += kept ? 0 : 1;
					} else {
						ornaments++;
						ornamentsLost += kept ? 0 : 1;
					}
				}
			}
		}
		double pier = piersLost / (double) piers, wall = wallsLost / (double) walls, kept = wholesLost / (double) wholes,
				orn = ornamentsLost / (double) ornaments;
		check(helper, wall > 0.02, "the ruined halls hardly lost their walls (%s)", wall);
		check(helper, pier < wall * 0.7, "the corners of the halls went as readily as their walls (%s against %s)", pier, wall);
		check(helper, orn > kept + 0.1, "the parapets' ornaments went no sooner than their whole blocks (%s against %s)", orn, kept);
		helper.succeed();
	}

	/**
	 * An arch of rock and a terraced hill stand on their island: nothing of either hangs in the air, an arch only stands
	 * with both feet on turf, and the terraces can be climbed by their stairs, a block at a time.
	 */
	@GameTest(maxTicks = 200)
	public void theLandsShapesStand(GameTestHelper helper) {
		int arches = 0;
		for (int seed = 0; seed < 6; seed++) {
			SeamArchitect.Shape s = SeamArchitect.Shape.of(new Random(40 + seed), 0, 100, 0, 30);
			SeamArchitect.Plan p = new SeamArchitect.Plan();
			Terrain.of(new Random(50 + seed), s, 0).emit(p);
			int before = p.size(), ty = p.ground(12, 0, 100) + 1;
			Set<Long> island = new HashSet<>(p.positions());
			for (int tries = 0; tries < 8; tries++) {
				if (com.extremedimensions.seam.Landforms.rockArch(p, new Random(seed * 10 + tries), -10, p.ground(-10, 0, 100) + 1, 0)) {
					arches++;
					break;
				}
			}
			com.extremedimensions.seam.Landforms.terraces(p, new Random(seed), 12, ty, 0);
			check(helper, p.size() > before + 60, "the arch and the terraces of island %s laid only %s blocks", seed, p.size() - before);
			Set<Long> held = grounded(p, 100);
			int hanging = 0;
			for (long k : p.positions()) {
				hanging += island.contains(k) || held.contains(k) ? 0 : 1;
			}
			check(helper, hanging == 0, "the land's shapes on island %s leave %s blocks hanging", seed, hanging);
			// Up the terraces from their foot to their summit, never more than a block at a time
			int[][] h = new int[19][19];
			for (int dx = -9; dx <= 9; dx++) {
				for (int dz = -9; dz <= 9; dz++) {
					h[dx + 9][dz + 9] = Integer.MIN_VALUE;
					for (int y = ty + 12; y >= ty - 4; y--) {
						BlockState b = p.at(12 + dx, y, dz);
						if (b != null && (b.getBlock() instanceof net.minecraft.world.level.block.StairBlock
								|| b.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
							h[dx + 9][dz + 9] = y;
							break;
						}
					}
				}
			}
			boolean[][] reached = new boolean[19][19];
			ArrayDeque<int[]> todo = new ArrayDeque<>();
			// from the level apron round the foot of the hill
			for (int a = 0; a < 19; a++) {
				for (int b = 0; b < 19; b++) {
					double d = Math.hypot(a - 9, b - 9);
					if (d >= 7.8 && d <= 8.4 && h[a][b] == ty - 1) {
						reached[a][b] = true;
						todo.add(new int[] {a, b});
					}
				}
			}
			check(helper, !todo.isEmpty(), "the terraces of island %s have no level apron round their foot", seed);
			while (!todo.isEmpty()) {
				int[] c = todo.poll();
				for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int a = c[0] + n[0], b = c[1] + n[1];
					if (a >= 0 && b >= 0 && a < 19 && b < 19 && !reached[a][b] && h[a][b] != Integer.MIN_VALUE && h[a][b] - h[c[0]][c[1]] <= 1) {
						reached[a][b] = true;
						todo.add(new int[] {a, b});
					}
				}
			}
			check(helper, h[9][9] >= ty + 4, "the terraces of island %s rise only to %s from %s", seed, h[9][9], ty);
			check(helper, reached[9][9], "the summit of the terraces on island %s cannot be climbed to", seed);
		}
		check(helper, arches >= 3, "an arch found its feet on only %s of six islands", arches);
		helper.succeed();
	}

	/**
	 * The Seam's briefs are data: the file the mod ships says what the Seam always raised, a datapack's file changes the
	 * mix and the sizes, and what is out of bounds keeps its default.
	 */
	@GameTest
	public void theSeamsBriefsAreData(GameTestHelper helper) {
		com.extremedimensions.seam.Briefs.Brief d = com.extremedimensions.seam.Briefs.DEFAULT;
		var res = helper.getLevel().getServer().getResourceManager().getResource(com.extremedimensions.seam.Briefs.FILE);
		check(helper, res.isPresent(), "the Seam's briefs are not in the mod's data");
		com.extremedimensions.seam.Briefs.Brief shipped;
		try (java.io.Reader in = res.get().openAsReader()) {
			shipped = com.extremedimensions.seam.Briefs.parse(com.google.gson.JsonParser.parseReader(in).getAsJsonObject());
		} catch (java.io.IOException e) {
			throw new AssertionError("the Seam's briefs could not be read", e);
		}
		check(helper, java.util.Arrays.equals(shipped.weights(), d.weights()) && java.util.Arrays.deepEquals(shipped.radii(), d.radii())
				&& shipped.arches() == d.arches() && shipped.terraces() == d.terraces(), "the shipped briefs differ from the defaults");
		// The mix the Seam always had: of twenty draws, 7 plain, 3 loom, 3 village, 3 spire, 2 hanging, 2 halo
		int[] counts = new int[com.extremedimensions.seam.Briefs.KINDS.length];
		for (long t = 0; t < 20; t++) {
			counts[d.kind(t)]++;
		}
		check(helper, java.util.Arrays.equals(counts, new int[] {7, 3, 3, 3, 2, 2}), "the default mix is %s", java.util.Arrays.toString(counts));
		// A pack that wants villages only, and bigger
		com.extremedimensions.seam.Briefs.Brief villages = com.extremedimensions.seam.Briefs.parse(com.google.gson.JsonParser.parseString(
				"{\"kinds\":{\"plain\":0,\"loom\":0,\"village\":5,\"spire\":0,\"hanging\":0,\"halo\":0},\"radius\":{\"village\":[30,32]}}").getAsJsonObject());
		for (long t = 0; t < 50; t++) {
			int r = villages.radius(villages.kind(t), t * 7919);
			check(helper, villages.kind(t) == 2 && r >= 30 && r <= 32, "a villages-only brief gave kind %s, radius %s", villages.kind(t), r);
		}
		// Nonsense keeps to the bounds: no kind at all, radii past them, a chance above one
		com.extremedimensions.seam.Briefs.Brief odd = com.extremedimensions.seam.Briefs.parse(com.google.gson.JsonParser.parseString(
				"{\"kinds\":{\"plain\":0,\"loom\":0,\"village\":0,\"spire\":0,\"hanging\":0,\"halo\":0},\"radius\":{\"spire\":[200,3]},"
						+ "\"landforms\":{\"rock_arch\":7}}").getAsJsonObject());
		check(helper, java.util.Arrays.equals(odd.weights(), d.weights()), "a brief with no kind at all left %s", java.util.Arrays.toString(odd.weights()));
		check(helper, odd.radius(3, 5) >= 8 && odd.radius(3, 5) <= 48 && odd.arches() == 1.0, "an odd brief went past its bounds");
		helper.succeed();
	}

	/**
	 * A village on rolling ground steps with it: each house is planned at the ground where it stands (never more than
	 * two blocks off the plaza's), its floor on that ground.
	 */
	@GameTest
	public void aVillageStepsWithTheSlope(GameTestHelper helper) {
		int top = 100;
		java.util.Map<Integer, Integer> floors = new java.util.HashMap<>();
		java.util.List<int[]> seen = new java.util.ArrayList<>();
		SeamArchitect.Plan p = new SeamArchitect.Plan();
		// ground that falls away to the west and rises to the east
		SeamArchitect.village(p, new Random(12), 0, top, 0, 28, (x, z) -> top + Integer.signum(x) * (Math.abs(x) > 6 ? 3 : 1), (house, hy) -> {
			int low = Integer.MAX_VALUE;
			for (long k : house.positions()) {
				low = Math.min(low, BlockPos.getY(k));
			}
			seen.add(new int[] {hy, low});
			floors.merge(hy, 1, Integer::sum);
		});
		check(helper, seen.size() >= 6, "the village planned only %s houses apart", seen.size());
		check(helper, floors.size() >= 2, "every house of a village on a slope stands at one height (%s)", floors.keySet());
		for (int[] h : seen) {
			check(helper, Math.abs(h[0] - top) <= 2, "a house stands %s blocks off the plaza", h[0] - top);
			check(helper, h[1] == h[0], "a house planned at %s has its floor at %s", h[0], h[1]);
		}
		helper.succeed();
	}

	/** How many buildings are damaged follows the setting: none at 0, every one at 100. */
	@GameTest
	public void damageFollowsTheSetting(GameTestHelper helper) {
		int was = Config.SEAM_DAMAGE;
		try {
			Random r = new Random(3);
			Config.SEAM_DAMAGE = 0;
			for (int i = 0; i < 300; i++) {
				check(helper, Ruin.condition(r, false) <= Ruin.WEATHERED, "a building was damaged with damage set to 0");
			}
			Config.SEAM_DAMAGE = 100;
			for (int i = 0; i < 300; i++) {
				check(helper, Ruin.condition(r, false) >= Ruin.DAMAGED, "a building was left whole with damage set to 100");
			}
			check(helper, Ruin.condition(r, true) == Ruin.RUINED, "a building of the ruined scheme was not a ruin");
		} finally {
			Config.SEAM_DAMAGE = was;
		}
		helper.succeed();
	}
}
