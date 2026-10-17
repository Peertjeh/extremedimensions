package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.Random;

import com.extremedimensions.seam.Colossus;
import com.extremedimensions.seam.MaterialContext;
import com.extremedimensions.seam.SeamArchitect;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How the Seam's buildings are made: the same seed builds the same building, stone for stone; a building's masonry is
 * planned in the world's coordinates, so it is the same stone whichever part of it lays a block and its gradient runs
 * unbroken from tower to hall; the gradient climbs from a dark foot to a pale crown, and neighbouring stones never
 * jump across it.
 */
public class ArchitectureTests {
	/** A ladder of seven distinct stones, darkest first, to see where blocks fall on it. */
	private static final BlockState[] LADDER = {Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
			Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
			Blocks.DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState()};

	private static int rung(BlockState b) {
		for (int i = 0; i < LADDER.length; i++) {
			if (LADDER[i] == b) {
				return i;
			}
		}
		return -1;
	}

	/** Every plan of building, built twice from one seed, is the same block for block; another seed builds another. */
	@GameTest(maxTicks = 200)
	public void aSeedAlwaysBuildsTheSameBuilding(GameTestHelper helper) {
		int top = 100;
		for (int plan = 0; plan < 5; plan++) {
			long seed = 51_329L + plan * 977L;
			SeamArchitect.Plan a = new SeamArchitect.Plan(), b = new SeamArchitect.Plan(), c = new SeamArchitect.Plan();
			Colossus.Summary sa = Colossus.build(a, new Random(seed), 400, top, -300, 40, plan);
			Colossus.Summary sb = Colossus.build(b, new Random(seed), 400, top, -300, 40, plan);
			Colossus.build(c, new Random(seed + 1), 400, top, -300, 40, plan);
			check(helper, sa.equals(sb) && a.size() == b.size() && a.fingerprint() == b.fingerprint(),
					"plan %s from seed %s built two different buildings (%s and %s blocks)", plan, seed, a.size(), b.size());
			check(helper, a.fingerprint() != c.fingerprint(), "plan %s built the same building from seeds %s and %s", plan, seed, seed + 1);
		}
		helper.succeed();
	}

	/**
	 * A building can be told its scheme of materials: it is built in it, and (but for a ruin, whose crowns are broken
	 * and battlements ragged) to the same shape its seed gives in any other.
	 */
	@GameTest(maxTicks = 200)
	public void aBuildingToldItsSchemeKeepsItsShape(GameTestHelper helper) {
		for (int plan = 0; plan < 5; plan++) {
			long seed = 8_191L + plan;
			Colossus.Summary twilight = Colossus.build(new SeamArchitect.Plan(), new Random(seed), 0, 100, 0, 40, plan, Colossus.TWILIGHT);
			Colossus.Summary bone = Colossus.build(new SeamArchitect.Plan(), new Random(seed), 0, 100, 0, 40, plan, Colossus.BONE);
			Colossus.Summary ruin = Colossus.build(new SeamArchitect.Plan(), new Random(seed), 0, 100, 0, 40, plan, Colossus.RUIN);
			check(helper, twilight.scheme() == Colossus.TWILIGHT && bone.scheme() == Colossus.BONE && ruin.scheme() == Colossus.RUIN,
					"plan %s was not built in the schemes it was told (%s, %s, %s)", plan, twilight.scheme(), bone.scheme(), ruin.scheme());
			check(helper, twilight.layout() == bone.layout() && twilight.crown() == bone.crown() && twilight.towers() == bone.towers()
					&& twilight.height() == bone.height(), "plan %s changed shape with its scheme: %s against %s", plan, twilight, bone);
		}
		helper.succeed();
	}

	/**
	 * Two parts of a building that lay the same block lay the same stone: its plan is one (two contexts made alike
	 * agree on every block, worn or not), and nothing about a part enters into it.
	 */
	@GameTest
	public void aBlockIsTheSameStoneWhicheverPartLaysIt(GameTestHelper helper) {
		Random r = new Random(5);
		MaterialContext tower = MaterialContext.of(77L, 100, 228, 0.3), hall = MaterialContext.of(77L, 100, 228, 0.3);
		for (int i = 0; i < 4000; i++) {
			int x = r.nextInt(600) - 300, y = 90 + r.nextInt(150), z = r.nextInt(600) - 300;
			check(helper, tower.masonry(LADDER, x, y, z, 0) == hall.masonry(LADDER, x, y, z, 0) && tower.pick(LADDER, x, y, z, 0.2) == hall.pick(LADDER, x, y, z, 0.2),
					"two parts of one building lay different stone at %s,%s,%s", x, y, z);
		}
		helper.succeed();
	}

	/**
	 * The gradient climbs: from the foot to the crown the stone gets paler on average, height by height (allowing a
	 * hair for its patches); the foot is all but the darkest stone and the crown all but the palest; a course asked to
	 * be paler is.
	 */
	@GameTest
	public void theGradientClimbsFromFootToCrown(GameTestHelper helper) {
		MaterialContext ctx = MaterialContext.of(123_456_789L, 100, 228, 0);
		double last = -1;
		double[] means = new double[33];
		for (int k = 0; k <= 32; k++) {
			int y = 100 + k * 4;
			double sum = 0;
			int n = 0;
			for (int x = -40; x < 40; x += 3) {
				for (int z = -40; z < 40; z += 3) {
					sum += rung(ctx.pick(LADDER, x, y, z, 0));
					n++;
				}
			}
			means[k] = sum / n;
			check(helper, means[k] >= last - 0.15, "the stone at %s is darker (%s) than four blocks below it (%s)", y, means[k], last);
			last = means[k];
		}
		check(helper, means[0] < 0.5, "the foot is not dark: its mean rung is %s", means[0]);
		check(helper, means[32] > LADDER.length - 1.5, "the crown is not pale: its mean rung is %s", means[32]);
		double plain = 0, paler = 0;
		for (int x = 0; x < 60; x++) {
			plain += rung(ctx.pick(LADDER, x, 140, 0, 0));
			paler += rung(ctx.pick(LADDER, x, 140, 0, 0.35));
		}
		check(helper, paler > plain + 60, "a course asked to be paler is not (%s against %s)", paler / 60, plain / 60);
		helper.succeed();
	}

	/**
	 * Neighbouring stones never jump across the gradient: side by side or one on another, they are at most one rung
	 * apart (the streaks and patches move it smoothly, and the dither between bands is a quarter of a rung).
	 */
	@GameTest
	public void neighbouringStonesShadeSmoothly(GameTestHelper helper) {
		MaterialContext ctx = MaterialContext.of(987_654_321L, 100, 228, 0);
		Random r = new Random(9);
		int jumps = 0, pairs = 0;
		for (int i = 0; i < 4000; i++) {
			int x = r.nextInt(400) - 200, y = 100 + r.nextInt(128), z = r.nextInt(400) - 200;
			double here = ctx.place(x, y, z, 0);
			check(helper, Math.abs(ctx.place(x + 1, y, z, 0) - here) < 0.2 && Math.abs(ctx.place(x, y, z + 1, 0) - here) < 0.2
					&& Math.abs(ctx.place(x, y + 1, z, 0) - here) < 0.12, "the gradient breaks at %s,%s,%s", x, y, z);
			int a = rung(ctx.pick(LADDER, x, y, z, 0));
			for (int[] d : new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}) {
				pairs++;
				if (Math.abs(rung(ctx.pick(LADDER, x + d[0], y + d[1], z + d[2], 0)) - a) > 1) {
					jumps++;
				}
			}
		}
		check(helper, jumps * 200 < pairs, "%s of %s neighbouring stones jump more than a rung", jumps, pairs);
		helper.succeed();
	}
}
