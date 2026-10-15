package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamDepth;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The Seam's islands as they are built (blocks fly in before a watcher's eyes, the ground at their feet is simply
 * there) and the Fray beneath them: knots out past the rims, wells over them, strands between, a faster drain.
 */
public class FrayTests {
	@GameTest
	public void theFrayHangsBeneathTheIslands(GameTestHelper helper) {
		double wells = Config.SEAM_WELL_CHANCE;
		Config.SEAM_WELL_CHANCE = 1.0;
		try {
			long seed = 12345L;
			SeamArchitect.Shape island = new SeamArchitect.Shape(1050, 90, 1050, 14, 0.3, 1.1, 2.0);
			SeamArchitect.Shape east = new SeamArchitect.Shape(1150, 86, 1046, 12, 1.0, 0.2, 2.5);
			SeamDepth.Knot k = SeamDepth.knot(10, 10, island, seed);
			SeamDepth.Knot e = SeamDepth.knot(11, 10, east, seed);
			check(helper, k != null && k.well(), "with every island given a well, this one has none");
			check(helper, !island.inside(k.x(), k.z(), -2), "the knot hangs under its island (%s,%s)", k.x(), k.z());
			check(helper, k.y() < Config.SEAM_FRAY_TOP && k.y() > 8, "the knot hangs at %s, not in the Fray", k.y());
			SeamArchitect.Plan plan = SeamDepth.plan(10, 10, island, east, null, seed);
			check(helper, plan != null, "no Fray was planned");
			check(helper, plan.has(k.x(), k.y(), k.z()), "the knot has no landing under its well");
			check(helper, !plan.has(k.x(), k.top(), k.z()) && plan.has(k.x() + 2, k.top(), k.z()), "the well is no ring over a drop");
			check(helper, plan.marks.contains("w@" + k.x() + "," + k.top() + "," + k.z() + "," + k.y()), "the well was not remembered");
			check(helper, plan.marks.contains("k@" + k.x() + "," + k.y() + "," + k.z()), "the knot was not remembered");
			// The strand east: somewhere in the column halfway between the knots
			int mx = (k.x() + e.x()) / 2, mz = (k.z() + e.z()) / 2;
			boolean strand = false;
			for (int y = 2; y < Config.SEAM_FRAY_TOP && !strand; y++) {
				for (int dx = -1; dx <= 1 && !strand; dx++) {
					for (int dz = -1; dz <= 1 && !strand; dz++) {
						strand = plan.has(mx + dx, y, mz + dz);
					}
				}
			}
			check(helper, strand, "no strand runs from the knot to the next one east");
		} finally {
			Config.SEAM_WELL_CHANCE = wells;
		}
		helper.succeed();
	}

	@GameTest
	public void timeRunsOutFasterInTheFray(GameTestHelper helper) {
		check(helper, SeamDepth.drain(Config.SEAM_FRAY_TOP - 10) == Config.SEAM_FRAY_DRAIN, "the Fray drains at %s", SeamDepth.drain(Config.SEAM_FRAY_TOP - 10));
		check(helper, SeamDepth.drain(Config.SEAM_FRAY_TOP + 30) == 1.0, "the islands drain at %s", SeamDepth.drain(Config.SEAM_FRAY_TOP + 30));
		SeamDepth.Well w = new SeamDepth.Well(100, 90, 100, 30);
		check(helper, SeamDepth.inShaft(w, 100.5, 60, 100.5, 2) && !SeamDepth.inShaft(w, 104.5, 60, 100.5, 2), "the shaft is not where its well is");
		check(helper, !SeamDepth.inShaft(w, 100.5, 95, 100.5, 2) && !SeamDepth.inShaft(w, 100.5, 20, 100.5, 2), "the shaft reaches past its ends");
		helper.succeed();
	}

	/**
	 * The Seam builds itself before a watcher's eyes: a wall a few blocks off flies in (it is not there at once, and
	 * stands whole a moment later), the ground right under them is simply there, and on stopping what is in the air
	 * lands and an unfinished plan is handed back to be built again. Built where the test's own space is, in the
	 * Overworld (the test server has no Seam), driving the Seam's builder by hand.
	 */
	@GameTest(maxTicks = 120)
	public void theSeamBuildsItselfBeforeAWatchersEyes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int base = Math.max(o.getY() + 3, 8);
		ServerPlayer p = TestPlayers.survivor(helper, "test-watcher");
		p.teleportTo(o.getX() + 1.5, base, o.getZ() + 1.5);
		int range = Config.SEAM_FLY_RANGE;
		Config.SEAM_FLY_RANGE = 48;
		SeamArchitect.clear();
		SeamArchitect.Plan wall = new SeamArchitect.Plan();
		for (int y = 1; y <= 3; y++) {
			for (int z = 4; z <= 6; z++) {
				wall.set(o.getX() + 7, base + y, o.getZ() + z, Blocks.STONE.defaultBlockState());
			}
		}
		// Right under the watcher's feet: the ground is simply there
		wall.set(o.getX() + 1, base - 1, o.getZ() + 1, Blocks.STONE.defaultBlockState());
		wall.queue();
		BlockPos corner = new BlockPos(o.getX() + 7, base + 2, o.getZ() + 5);
		BlockPos feet = new BlockPos(o.getX() + 1, base - 1, o.getZ() + 1);
		Runnable restore = () -> {
			Config.SEAM_FLY_RANGE = range;
			SeamArchitect.clear();
		};
		for (int t = 1; t <= 50; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				SeamArchitect.tick(level, 5_000_000L);
				if (tick == 3) {
					boolean ok = SeamArchitect.flying() > 0 && level.getBlockState(corner).isAir() && level.getBlockState(feet).is(Blocks.STONE);
					if (!ok) {
						restore.run();
					}
					check(helper, ok, "the wall did not fly in (flying %s, corner %s, ground under the watcher %s)", SeamArchitect.flying(), level.getBlockState(corner),
							level.getBlockState(feet));
				}
			});
		}
		helper.runAfterDelay(52, () -> {
			int stood = 0;
			for (int y = 1; y <= 3; y++) {
				for (int z = 4; z <= 6; z++) {
					stood += level.getBlockState(new BlockPos(o.getX() + 7, base + y, o.getZ() + z)).is(Blocks.STONE) ? 1 : 0;
				}
			}
			boolean ok = stood == 9 && SeamArchitect.flying() == 0 && SeamArchitect.pending() == 0;
			if (!ok) {
				restore.run();
			}
			check(helper, ok, "the wall stands with %s of 9 blocks (%s flying, %s pending)", stood, SeamArchitect.flying(), SeamArchitect.pending());
			// Stopping with a plan in the air: what flies lands at once, and the plan is handed back unfinished
			SeamArchitect.Plan more = new SeamArchitect.Plan();
			more.cell = "test@cell";
			for (int z = 4; z <= 6; z++) {
				more.set(o.getX() + 6, base + 4, o.getZ() + z, Blocks.STONE.defaultBlockState());
			}
			more.then(o.getX() + 6, base + 4, o.getZ() + 4, l -> {});
			more.queue();
			SeamArchitect.tick(level, 5_000_000L);
			SeamArchitect.tick(level, 5_000_000L);
			int inAir = SeamArchitect.flying();
			java.util.List<SeamArchitect.Meta> left = SeamArchitect.stop();
			boolean landed = level.getBlockState(new BlockPos(o.getX() + 6, base + 4, o.getZ() + 5)).is(Blocks.STONE);
			boolean handed = left.stream().anyMatch(m -> "test@cell".equals(m.cell()));
			restore.run();
			check(helper, inAir > 0 && landed, "on stopping the blocks in the air did not land (%s in the air)", inAir);
			check(helper, handed, "the unfinished plan was not handed back on stopping");
			helper.succeed();
		});
	}
}
