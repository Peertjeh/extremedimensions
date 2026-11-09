package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.LinkedHashMap;
import java.util.Map;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderUpheaval;
import com.extremedimensions.elder.PaleMason;
import com.extremedimensions.elder.PaleMasonService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground breaking in an Elder's ultimate (3.41): it raises cliffs and spikes and opens a chasm, never through the
 * floor's bottom; once its fight is over every block goes back exactly as it was; with griefing off it changes nothing.
 * Each test lays a flat floor of stone five blocks thick high over its own ground, clear of the Pale Mason who owns the
 * upheaval (he stands forming on the ground under it, without an arena), breaks a small round of it at once and drives
 * it on itself, all within the test's own tick; nothing the upheaval does reaches past the floor, nor hurts anyone.
 */
public class UpheavalTests {
	/** The floor: from LOW to HIGH, 0 to SIDE across both ways (relative to the test), broken within RADIUS of its middle. */
	private static final int LOW = 34, HIGH = 38, SIDE = 8;
	private static final double RADIUS = 4;

	private static ServerPlayer visitor(GameTestHelper helper, String name) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	private static PaleMason mason(GameTestHelper helper, ServerPlayer p) {
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		try {
			return PaleMasonService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
		} finally {
			Config.ELDER_ARENA = arena;
		}
	}

	/**
	 * Nothing here hurts anyone (the players of the tests round these are not his): the upheaval's and the Mason's own
	 * damage are held at 0. Run the returned task to put them back.
	 */
	private static Runnable harmless() {
		double upheaval = Config.ELDER_UPHEAVAL_DAMAGE, strike = Config.PALE_MASON_STRIKE_DAMAGE, block = Config.PALE_MASON_BLOCK_DAMAGE,
				gaze = Config.PALE_MASON_GAZE_DAMAGE, door = Config.PALE_MASON_DOOR_DAMAGE;
		Config.ELDER_UPHEAVAL_DAMAGE = 0;
		Config.PALE_MASON_STRIKE_DAMAGE = 0;
		Config.PALE_MASON_BLOCK_DAMAGE = 0;
		Config.PALE_MASON_GAZE_DAMAGE = 0;
		Config.PALE_MASON_DOOR_DAMAGE = 0;
		return () -> {
			Config.ELDER_UPHEAVAL_DAMAGE = upheaval;
			Config.PALE_MASON_STRIKE_DAMAGE = strike;
			Config.PALE_MASON_BLOCK_DAMAGE = block;
			Config.PALE_MASON_GAZE_DAMAGE = gaze;
			Config.PALE_MASON_DOOR_DAMAGE = door;
		};
	}

	/** Every block round the floor, from under it to well over it (as {@link #floor} leaves it, or as the world had it). */
	private static Map<BlockPos, BlockState> look(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (int x = -2; x <= SIDE + 2; x++) {
			for (int z = -2; z <= SIDE + 2; z++) {
				for (int y = LOW - 2; y <= HIGH + 20; y++) {
					BlockPos q = helper.absolutePos(new BlockPos(x, y, z));
					out.put(q, level.getBlockState(q));
				}
			}
		}
		return out;
	}

	/** The floor: stone, five thick, with nothing round it or over it. */
	private static void floor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = -2; x <= SIDE + 2; x++) {
			for (int z = -2; z <= SIDE + 2; z++) {
				for (int y = LOW - 2; y <= HIGH + 20; y++) {
					boolean in = x >= 0 && x <= SIDE && z >= 0 && z <= SIDE && y >= LOW && y <= HIGH;
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), in ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
		}
	}

	/** The world put back as it was before the floor was laid. */
	private static void tidy(GameTestHelper helper, Map<BlockPos, BlockState> world) {
		for (Map.Entry<BlockPos, BlockState> e : world.entrySet()) {
			helper.getLevel().setBlock(e.getKey(), e.getValue(), 2 | 16);
		}
	}

	/** Where the round of the floor that breaks has its middle: the first air over the floor's middle. */
	private static BlockPos middle(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(SIDE / 2, HIGH + 1, SIDE / 2));
	}

	/** Positions that differ between two looks at the same blocks. */
	private static int differ(Map<BlockPos, BlockState> a, Map<BlockPos, BlockState> b) {
		int n = 0;
		for (Map.Entry<BlockPos, BlockState> e : a.entrySet()) {
			n += e.getValue() == b.get(e.getKey()) ? 0 : 1;
		}
		return n;
	}

	/**
	 * It breaks: blocks rise over the floor, and a chasm opens in it that never goes through its bottom (every block torn
	 * out has solid ground under it).
	 */
	@GameTest(maxTicks = 40)
	public void theGroundBreaksIntoCliffsAndAChasm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = visitor(helper, "upheaval-breaks");
		Map<BlockPos, BlockState> world = look(helper);
		Runnable restore = harmless();
		int grief = Config.ELDER_GRIEFING;
		PaleMason m = null;
		try {
			Config.ELDER_GRIEFING = 1;
			floor(helper);
			m = mason(helper, p);
			check(helper, m != null, "the Pale Mason could not come");
			ElderUpheaval.eruptAt(m, middle(helper), RADIUS);
			ElderUpheaval.advance(m, 120);
			int[] tally = ElderUpheaval.tally(m);
			int raised = 0, removed = 0, bottom = 0, under = 0;
			for (int x = -2; x <= SIDE + 2; x++) {
				for (int z = -2; z <= SIDE + 2; z++) {
					// (nothing leaks out under the floor either)
					for (int y = LOW - 2; y < LOW; y++) {
						under += level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir() ? 0 : 1;
					}
					if (x < 0 || x > SIDE || z < 0 || z > SIDE) {
						continue;
					}
					for (int y = LOW; y <= HIGH + 20; y++) {
						BlockState s = level.getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
						if (y > HIGH) {
							raised += s.isAir() ? 0 : 1;
						} else if (!holdsUp(s)) {
							// torn out (to air, or to fire burning on a chasm's floor)
							removed++;
							bottom += y == LOW ? 1 : 0;
						}
					}
				}
			}
			check(helper, raised > 0 && tally[0] > 0, "nothing rose over the floor (%s blocks over it, %s raised by its count)", raised, tally[0]);
			check(helper, tally[2] >= 1 && removed > 0, "no chasm opened (%s chasms, %s blocks torn out of the floor)", tally[2], removed);
			// Every column keeps its bottom block: whatever was torn out has solid ground under it, never a hole through
			check(helper, bottom == 0, "%s blocks of the floor's bottom were torn out: the chasm went through it", bottom);
			check(helper, under == 0, "%s blocks turned up under the floor", under);
		} finally {
			if (m != null) {
				ElderUpheaval.finish(m);
				PaleMasonService.fade(m);
			}
			Config.ELDER_GRIEFING = grief;
			restore.run();
			tidy(helper, world);
		}
		helper.succeed();
	}

	/** Whether a block is solid ground (the floor's stone, or the Mason's stone laid in a chasm's floor). */
	private static boolean holdsUp(BlockState s) {
		return !s.isAir() && !s.canBeReplaced() && !s.liquid();
	}

	/** Its fight over, the broken ground sinks back: every block is as it was, exactly. */
	@GameTest(maxTicks = 40)
	public void theBrokenGroundGoesBackExactly(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "upheaval-sinks");
		Map<BlockPos, BlockState> world = look(helper);
		Runnable restore = harmless();
		int grief = Config.ELDER_GRIEFING;
		PaleMason m = null;
		try {
			Config.ELDER_GRIEFING = 1;
			floor(helper);
			Map<BlockPos, BlockState> before = look(helper);
			m = mason(helper, p);
			check(helper, m != null, "the Pale Mason could not come");
			ElderUpheaval.eruptAt(m, middle(helper), RADIUS);
			ElderUpheaval.advance(m, 120);
			int broken = differ(before, look(helper));
			check(helper, broken > 0, "the ground did not break");
			ElderUpheaval.release(m);
			ElderUpheaval.advance(m, 400);
			int left = ElderUpheaval.tally(m)[4];
			int still = differ(before, look(helper));
			boolean over = ElderUpheaval.tally(m)[4] == 0;
			check(helper, still == 0, "%s of the %s blocks it changed are not as they were (%s still held)", still, broken, left);
			check(helper, over, "the upheaval still holds %s blocks once it has sunk back", left);
			BlockPos any = middle(helper).below();
			check(helper, !ElderUpheaval.holds(helper.getLevel(), any) && !com.extremedimensions.elder.ElderTerrain.temporary(helper.getLevel(), any),
					"a block put back is still known as the upheaval's");
		} finally {
			if (m != null) {
				ElderUpheaval.finish(m);
				PaleMasonService.fade(m);
			}
			Config.ELDER_GRIEFING = grief;
			restore.run();
			tidy(helper, world);
		}
		helper.succeed();
	}

	/** With elder_griefing off the ground shakes, but not a block of it changes. */
	@GameTest(maxTicks = 40)
	public void withoutGriefingNoBlockChanges(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "upheaval-spares");
		Map<BlockPos, BlockState> world = look(helper);
		Runnable restore = harmless();
		int grief = Config.ELDER_GRIEFING;
		PaleMason m = null;
		try {
			floor(helper);
			Map<BlockPos, BlockState> before = look(helper);
			m = mason(helper, p);
			check(helper, m != null, "the Pale Mason could not come");
			Config.ELDER_GRIEFING = 0;
			ElderUpheaval.eruptAt(m, middle(helper), RADIUS);
			ElderUpheaval.advance(m, 120);
			int changed = differ(before, look(helper));
			int held = ElderUpheaval.tally(m)[4];
			check(helper, changed == 0 && held == 0, "with griefing off %s blocks changed (%s held)", changed, held);
		} finally {
			if (m != null) {
				ElderUpheaval.finish(m);
				PaleMasonService.fade(m);
			}
			Config.ELDER_GRIEFING = grief;
			restore.run();
			tidy(helper, world);
		}
		helper.succeed();
	}
}
