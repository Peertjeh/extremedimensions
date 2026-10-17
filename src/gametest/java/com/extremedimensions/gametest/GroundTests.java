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
