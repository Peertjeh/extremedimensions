package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.lurker.Gatebreaker;
import com.extremedimensions.lurker.GatebreakerService;
import com.extremedimensions.lurker.Tidewalker;
import com.extremedimensions.lurker.TidewalkerService;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * The tribe's two of 3.20: the Tidewalker seizes a swimmer and tears the breath out of them, lets go when struck, and a
 * lantern held up drives it off; the Gatebreaker breaks through a stone wall to the player behind it, but not through
 * obsidian.
 */
public class DeepTests {
	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					helper.setBlock(new BlockPos(x, y, z), b);
				}
			}
		}
	}

	/**
	 * In a pool five blocks deep, a Tidewalker rises at a swimmer and seizes them: it tears the breath out of them far
	 * faster than the water does. A blow makes it let go; a lantern held up then sends it away.
	 */
	@GameTest(environment = Alone.TIDE_DRAG, maxTicks = 300)
	public void aTidewalkerDragsASwimmerAndALanternDrivesItOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		fill(helper, 0, 0, 0, 7, 5, 7, Blocks.STONE);
		fill(helper, 1, 1, 1, 6, 5, 6, Blocks.WATER);
		ServerPlayer p = TestPlayers.survivor(helper, "tide-swimmer");
		place(helper, p, 3.5, 2, 3.5);
		int drain = Config.TIDEWALKER_AIR_DRAIN, hold = Config.TIDEWALKER_DRAG_SECONDS, strikes = Config.TIDEWALKER_STRIKES;
		Config.TIDEWALKER_AIR_DRAIN = 6;
		Config.TIDEWALKER_DRAG_SECONDS = 4;
		Config.TIDEWALKER_STRIKES = 5;
		Runnable restore = () -> {
			Config.TIDEWALKER_AIR_DRAIN = drain;
			Config.TIDEWALKER_DRAG_SECONDS = hold;
			Config.TIDEWALKER_STRIKES = strikes;
		};
		Tidewalker[] in = {null};
		helper.runAfterDelay(10, () -> {
			in[0] = ModEntities.TIDEWALKER.spawn(level, helper.absolutePos(new BlockPos(5, 1, 5)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Tidewalker came");
			p.setAirSupply(p.getMaxAirSupply());
			TidewalkerService.adopt(level, in[0], p, "strike");
		});
		int[] seizedAt = {-1};
		int[] air = {0};
		boolean[] done = {false};
		for (int t = 11; t <= 260; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0] || !in[0].isAlive()) {
					return;
				}
				// (it holds the swimmer under: place them back where they were, as a real swimmer would be pulled)
				place(helper, p, 3.5, 2, 3.5);
				if (seizedAt[0] < 0) {
					if (TidewalkerService.seized(in[0])) {
						seizedAt[0] = tick;
						air[0] = p.getAirSupply();
					}
					return;
				}
				if (tick < seizedAt[0] + 6) {
					return;
				}
				done[0] = true;
				try {
					int lost = air[0] - p.getAirSupply();
					check(helper, lost >= 25, "held under for six ticks the swimmer lost only %s breath", lost);
					check(helper, "drag".equals(TidewalkerService.mode(in[0])), "the Tidewalker is %s, not holding the swimmer", TidewalkerService.mode(in[0]));
					in[0].hurtServer(level, level.damageSources().playerAttack(p), 1.0f);
					check(helper, !"drag".equals(TidewalkerService.mode(in[0])), "a blow did not make it let go");
					p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LANTERN));
					check(helper, TidewalkerService.warded(p), "a lantern held up does not ward the swimmer");
				} catch (RuntimeException e) {
					restore.run();
					in[0].discard();
					throw e;
				}
				helper.runAfterDelay(3, () -> {
					try {
						check(helper, "retreat".equals(TidewalkerService.mode(in[0])), "with a lantern held up it is %s, not going",
								TidewalkerService.mode(in[0]));
					} finally {
						restore.run();
						p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
						if (in[0].isAlive()) {
							in[0].discard();
						}
					}
					helper.succeed();
				});
			});
		}
		helper.runAfterDelay(261, () -> {
			if (done[0]) {
				return;
			}
			restore.run();
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + TidewalkerService.mode(in[0]) + ")";
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Tidewalker never seized the swimmer (it is at %s)", where);
		});
	}

	/**
	 * A player shut in a stone room: a Gatebreaker outside brings its fists down on the wall and breaks it open, all
	 * but the column of obsidian in it.
	 */
	@GameTest(environment = Alone.GATE_SMASH, maxTicks = 260)
	public void aGatebreakerBreaksThroughStoneButNotObsidian(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		GameRules rules = level.getGameRules();
		boolean griefing = rules.get(GameRules.MOB_GRIEFING);
		rules.set(GameRules.MOB_GRIEFING, true, server);
		int breaks = Config.GATEBREAKER_BREAKS;
		double hardness = Config.GATEBREAKER_HARDNESS;
		Config.GATEBREAKER_BREAKS = 1;
		Config.GATEBREAKER_HARDNESS = 10;
		Runnable restore = () -> {
			Config.GATEBREAKER_BREAKS = breaks;
			Config.GATEBREAKER_HARDNESS = hardness;
			rules.set(GameRules.MOB_GRIEFING, griefing, server);
		};
		fill(helper, 0, 0, 0, 7, 0, 7, Blocks.STONE);
		// the room: walls five high round x 4-7, z 1-5, and a roof
		fill(helper, 4, 1, 1, 7, 5, 5, Blocks.STONE);
		fill(helper, 5, 1, 2, 6, 5, 4, Blocks.AIR);
		fill(helper, 4, 6, 1, 7, 6, 5, Blocks.STONE);
		fill(helper, 4, 1, 4, 4, 5, 4, Blocks.OBSIDIAN);
		ServerPlayer p = TestPlayers.survivor(helper, "gate-keeper");
		place(helper, p, 5.5, 1, 3.0);
		Gatebreaker[] in = {null};
		helper.runAfterDelay(10, () -> {
			in[0] = ModEntities.GATEBREAKER.spawn(level, helper.absolutePos(new BlockPos(1, 1, 3)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Gatebreaker came");
			GatebreakerService.adopt(level, in[0], p);
		});
		boolean[] done = {false};
		for (int t = 11; t <= 220; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0] || !in[0].isAlive() || GatebreakerService.smashes(in[0]) < 1) {
					return;
				}
				done[0] = true;
				try {
					for (int y = 1; y <= 4; y++) {
						check(helper, level.getBlockState(helper.absolutePos(new BlockPos(4, y, 3))).isAir(), "the wall at height %s still stands", y);
						check(helper, level.getBlockState(helper.absolutePos(new BlockPos(4, y, 4))).is(Blocks.OBSIDIAN), "it broke the obsidian at height %s", y);
					}
				} finally {
					restore.run();
					if (in[0].isAlive()) {
						in[0].discard();
					}
				}
				helper.succeed();
			});
		}
		helper.runAfterDelay(221, () -> {
			if (done[0]) {
				return;
			}
			restore.run();
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + GatebreakerService.mode(in[0]) + ")";
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Gatebreaker never brought its fists down on the wall (it is at %s)", where);
		});
	}
}
