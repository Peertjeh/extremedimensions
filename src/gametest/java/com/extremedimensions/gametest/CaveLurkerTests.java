package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.lurker.CaveLurker;
import com.extremedimensions.lurker.CaveLurkerService;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Cave Lurker: it fits a miner's tunnel and runs it without turning on the spot, its grab always leaves a curse, it
 * hears a miner but not a sneak, and it comes only under the ground.
 */
public class CaveLurkerTests {
	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	private static int curses(PlayerData d) {
		int n = 0;
		for (Penalty pen : Penalty.values()) {
			n += d.owned(pen);
		}
		return n;
	}

	/** The tunnel's cells (x, z), from its start to its far end. */
	private static final int[][] TUNNEL = {
		{0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1},
		{6, 2},
		{6, 3}, {5, 3}, {4, 3}, {3, 3}, {2, 3}, {1, 3},
		{1, 4},
		{1, 5}, {2, 5}, {3, 5}, {4, 5}, {5, 5}, {6, 5}};

	/**
	 * A miner's tunnel, one block wide and two high, winding through solid stone over the test's floor: east, south,
	 * west, south and east again, four corners, and a step up a block near its end (the column before the step three
	 * high, as a dug stair is). The player stands at its far end.
	 */
	private static void tunnel(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				for (int y = 0; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		for (int[] c : TUNNEL) {
			boolean raised = c[1] == 5 && c[0] >= 4;
			boolean step = c[1] == 5 && c[0] == 3;
			int floor = raised ? 1 : 0;
			int top = step ? 2 : floor + 1;
			for (int y = floor; y <= top; y++) {
				helper.setBlock(new BlockPos(c[0], y, c[1]), Blocks.AIR);
			}
		}
	}

	/**
	 * Through a winding two-high tunnel with four corners and a step, a hunting Cave Lurker reaches its prey without ever
	 * turning on the spot: it turns no more than the corners ask (and its first look round), never more than a few tens of
	 * degrees in a tick, never round and round where it stands. Then it grabs, and the grab leaves a curse.
	 */
	@GameTest(environment = Alone.CAVE_TUNNEL, maxTicks = 460)
	public void aCaveLurkerRunsATunnelWithoutTurningRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		tunnel(helper);
		ServerPlayer p = TestPlayers.survivor(helper, "cave-tunnel");
		place(helper, p, 6.5, 1, 5.5);
		PlayerData d = DataStore.get(p.getUUID());
		CaveLurker[] in = {null};
		int[] before = {0};
		double extreme = Config.CAVE_LURKER_CURSE_EXTREME_CHANCE;
		// (once the player's grace on joining is over; one nobody drives dissolves, so it is taken in hand at once)
		helper.runAfterDelay(70, () -> {
			in[0] = ModEntities.CAVE_LURKER.spawn(level, helper.absolutePos(new BlockPos(0, 0, 1)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Cave Lurker came");
			Config.CAVE_LURKER_CURSE_EXTREME_CHANCE = 0;
			p.setHealth(20f);
			before[0] = curses(d);
			CaveLurkerService.adopt(level, in[0], p, "hunt");
		});
		// Watched every tick until its grab: where it is and which way it faces
		int last = 420;
		float[] yaw = new float[last + 1];
		Vec3[] at = new Vec3[last + 1];
		int[] grabbedAt = {-1};
		float[] lowest = {20f};
		for (int t = 71; t <= last; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				lowest[0] = Math.min(lowest[0], p.getHealth());
				if (in[0] == null || grabbedAt[0] >= 0) {
					return;
				}
				yaw[tick] = in[0].getYRot();
				at[tick] = in[0].position();
				if (CaveLurkerService.grabbed(in[0]) || curses(d) > before[0]) {
					grabbedAt[0] = tick;
				}
			});
		}
		helper.runAfterDelay(last + 1, () -> {
			try {
				int end = grabbedAt[0] >= 0 ? grabbedAt[0] : last;
				double total = 0, most = 0, worstSpot = 0;
				for (int t = 72; t <= end; t++) {
					if (at[t] == null || at[t - 1] == null) {
						continue;
					}
					double turn = Math.abs(Mth.wrapDegrees(yaw[t] - yaw[t - 1]));
					total += turn;
					most = Math.max(most, turn);
				}
				// on the spot: any second in which it moved less than half a block
				for (int t = 72; t + 20 <= end; t++) {
					if (at[t] == null || at[t + 20] == null || at[t].distanceTo(at[t + 20]) >= 0.5) {
						continue;
					}
					double turned = 0;
					for (int k = t + 1; k <= t + 20; k++) {
						turned += Math.abs(Mth.wrapDegrees(yaw[k] - yaw[k - 1]));
					}
					worstSpot = Math.max(worstSpot, turned);
				}
				String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + CaveLurkerService.mode(in[0]) + ")";
				check(helper, grabbedAt[0] >= 0, "the Cave Lurker never reached its prey through the tunnel (it is at %s)", where);
				check(helper, most <= 45, "the Cave Lurker turned %.0f degrees in one tick", most);
				check(helper, worstSpot <= 120, "the Cave Lurker turned %.0f degrees on the spot within a second", worstSpot);
				// its first look round (at most half a turn) and the four corners (a quarter turn each), and a little more
				check(helper, total <= 900, "the Cave Lurker turned %.0f degrees in all through four corners", total);
				check(helper, curses(d) == before[0] + 1, "its grab left %s curses, not one", curses(d) - before[0]);
				check(helper, lowest[0] < 20f, "its grab did not hurt");
			} finally {
				Config.CAVE_LURKER_CURSE_EXTREME_CHANCE = extreme;
				if (in[0] != null && in[0].isAlive()) {
					in[0].discard();
				}
			}
			helper.succeed();
		});
	}

	/**
	 * A Cave Lurker hunts by ear: a sneaking player moving about is not heard at all, footsteps are heard a little, and
	 * blocks broken near it soon bring it on.
	 */
	@GameTest(environment = Alone.CAVE_HEARING, maxTicks = 260)
	public void aCaveLurkerHearsAMinerButNotASneak(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "cave-miner");
		place(helper, p, 7.5, 0, 7.5);
		CaveLurker[] in = {null};
		helper.runAfterDelay(70, () -> {
			in[0] = ModEntities.CAVE_LURKER.spawn(level, helper.absolutePos(new BlockPos(0, 0, 0)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Cave Lurker came");
			CaveLurkerService.adopt(level, in[0], p, "listen");
			p.setShiftKeyDown(true);
		});
		// Sneaking to and fro (out of its notice, more than eight blocks off): it hears nothing
		for (int t = 72; t <= 110; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> place(helper, p, 7.5 - (tick % 2) * 0.4, 0, 7.5));
		}
		double[] sneaking = {0};
		String[] modeThen = {""};
		helper.runAfterDelay(111, () -> {
			sneaking[0] = CaveLurkerService.heard(in[0]);
			modeThen[0] = CaveLurkerService.mode(in[0]);
			p.setShiftKeyDown(false);
		});
		// Walking to and fro: it hears the steps, not yet enough
		for (int t = 112; t <= 140; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> place(helper, p, 7.5 - (tick % 2) * 0.4, 0, 7.5));
		}
		double[] walking = {0};
		helper.runAfterDelay(141, () -> walking[0] = CaveLurkerService.heard(in[0]));
		// Breaking stone beside them: it comes
		for (int t = 142; t <= 165; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				BlockPos b = helper.absolutePos(new BlockPos(6, 0, 7 - (tick % 3)));
				level.setBlockAndUpdate(b, Blocks.STONE.defaultBlockState());
				p.gameMode.destroyBlock(b);
			});
		}
		String[] mined = {""};
		for (int t = 142; t <= 200; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] != null && mined[0].isEmpty() && "hunt".equals(CaveLurkerService.mode(in[0]))) {
					mined[0] = "hunt";
				}
			});
		}
		helper.runAfterDelay(201, () -> {
			try {
				check(helper, sneaking[0] == 0 && "listen".equals(modeThen[0]), "a sneaking player was heard (%.1f, %s)", sneaking[0], modeThen[0]);
				check(helper, walking[0] > 0, "footsteps were not heard at all");
				check(helper, "hunt".equals(mined[0]), "stone broken near it did not bring it on (it heard %.1f, it is %s)",
						in[0] == null ? 0 : CaveLurkerService.heard(in[0]), in[0] == null ? "gone" : CaveLurkerService.mode(in[0]));
			} finally {
				p.setShiftKeyDown(false);
				if (in[0] != null && in[0].isAlive()) {
					in[0].discard();
				}
			}
			helper.succeed();
		});
	}

	/** It comes only to a player under the ground: under a roof, below its height; never under the open sky. */
	@GameTest
	public void aCaveLurkerComesOnlyUnderTheGround(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "cave-sky");
		place(helper, p, 3.5, 0, 3.5);
		int maxY = Config.CAVE_LURKER_MAX_Y;
		try {
			Config.CAVE_LURKER_MAX_Y = 10000;
			check(helper, !CaveLurkerService.fits(p), "a Cave Lurker may come under the open sky");
			for (int x = 1; x <= 5; x++) {
				for (int z = 1; z <= 5; z++) {
					helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
				}
			}
			check(helper, CaveLurkerService.fits(p), "a Cave Lurker may not come under a roof of stone");
			Config.CAVE_LURKER_MAX_Y = (int) Math.floor(p.getY()) - 1;
			check(helper, !CaveLurkerService.fits(p), "a Cave Lurker may come above its height");
			check(helper, ModEntities.CAVE_LURKER.getWidth() < 1 && ModEntities.CAVE_LURKER.getHeight() < 2,
					"a Cave Lurker (%s by %s) does not fit a tunnel one wide and two high", ModEntities.CAVE_LURKER.getWidth(), ModEntities.CAVE_LURKER.getHeight());
		} finally {
			Config.CAVE_LURKER_MAX_Y = maxY;
		}
		helper.succeed();
	}

	/**
	 * A Cave Lurker set down on open ground, as the world and the operator's command set one down, can walk to the
	 * player and stays to listen. One that has only now come into the world has not touched the ground yet: its route
	 * must still be worked out, or none ever comes.
	 */
	@GameTest(environment = Alone.CAVE_COMES)
	public void aCaveLurkerSetDownCanComeToThePlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "cave-comes");
		place(helper, p, 6.5, 0, 6.5);
		CaveLurker cl = CaveLurkerService.setDown(level, helper.absolutePos(new BlockPos(1, 0, 1)), p);
		try {
			check(helper, cl != null, "no Cave Lurker could be set down on open ground seven blocks from the player");
			check(helper, "listen".equals(CaveLurkerService.mode(cl)), "the Cave Lurker set down is %s, not listening", CaveLurkerService.mode(cl));
		} finally {
			if (cl != null) {
				cl.discard();
			}
		}
		helper.succeed();
	}
}
