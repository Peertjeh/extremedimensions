package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.lurker.Magpie;
import com.extremedimensions.lurker.MagpieService;
import com.extremedimensions.lurker.Wick;
import com.extremedimensions.lurker.WickService;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The tribe's two thieves of 3.19: the Magpie robs a chest of its brightest things and holds its loot up as it runs,
 * a blow shakes one thing loose and death drops the rest; the Wick eats the torches round it but not one guarded by
 * soul fire, and gives back what it ate when it is slain.
 */
public class ThiefTests {
	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	/** How many of this item lie on the ground within a few blocks of a spot. */
	private static int lying(ServerLevel level, Vec3 at, Item item) {
		int n = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static boolean holds(List<ItemStack> stacks, Item item) {
		for (ItemStack s : stacks) {
			if (s.is(item)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A Magpie creeps to a chest and takes its three brightest things (the gold, the diamonds, then the fullest stack),
	 * leaving the stick; it holds the last thing up over its head. A blow shakes that loose; slain, it drops the rest.
	 */
	@GameTest(environment = Alone.MAGPIE_RAID, maxTicks = 400)
	public void aMagpieRobsTheBrightestThingsAndDropsThemWhenStruck(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "magpie-victim");
		// (facing away from where it comes, far enough from the chest not to startle it)
		place(helper, p, 7.5, 0, 7.5);
		BlockPos chestAt = helper.absolutePos(new BlockPos(2, 0, 2));
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.CHEST);
		check(helper, level.getBlockEntity(chestAt) instanceof Container, "no chest to rob");
		Container chest = (Container) level.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 2));
		chest.setItem(1, new ItemStack(Items.DIRT, 64));
		chest.setItem(2, new ItemStack(Items.GOLD_INGOT, 5));
		chest.setItem(3, new ItemStack(Items.STICK, 1));
		int takes = Config.MAGPIE_TAKES;
		Config.MAGPIE_TAKES = 3;
		Magpie[] in = {null};
		helper.runAfterDelay(10, () -> {
			in[0] = ModEntities.MAGPIE.spawn(level, helper.absolutePos(new BlockPos(6, 0, 0)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Magpie came");
			MagpieService.adopt(level, in[0], p, chestAt, "sneak");
		});
		// Watched every tick: the moment it has taken its three things, it is looked at, struck and slain
		boolean[] done = {false};
		for (int t = 11; t <= 300; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0] || !in[0].isAlive() || MagpieService.carried(in[0]).size() < 3) {
					return;
				}
				done[0] = true;
				try {
					List<ItemStack> carried = MagpieService.carried(in[0]);
					check(helper, holds(carried, Items.GOLD_INGOT) && holds(carried, Items.DIAMOND) && holds(carried, Items.DIRT),
							"the Magpie did not take the brightest things: %s", carried);
					int left = 0;
					for (int i = 0; i < chest.getContainerSize(); i++) {
						if (!chest.getItem(i).isEmpty()) {
							left++;
							check(helper, chest.getItem(i).is(Items.STICK), "the Magpie left %s and took something duller", chest.getItem(i));
						}
					}
					check(helper, left == 1, "the chest holds %s stacks after the raid, not just the stick", left);
					check(helper, !MagpieService.shown(in[0]).isEmpty(), "the Magpie holds nothing up over its head");
					// A blow shakes the last thing loose, death drops the rest
					Vec3 at = in[0].position();
					in[0].hurtServer(level, level.damageSources().playerAttack(p), 1.0f);
					int still = MagpieService.carried(in[0]).size();
					check(helper, still == 2, "a blow shook %s things loose, not one", 3 - still);
					in[0].hurtServer(level, level.damageSources().playerAttack(p), 200.0f);
					check(helper, !in[0].isAlive(), "the Magpie did not die");
					int gold = lying(level, at, Items.GOLD_INGOT), gems = lying(level, at, Items.DIAMOND), dirt = lying(level, at, Items.DIRT);
					check(helper, gold == 5 && gems == 2 && dirt == 64, "the Magpie's loot did not all fall (gold %s, diamonds %s, dirt %s)", gold, gems, dirt);
				} finally {
					Config.MAGPIE_TAKES = takes;
					if (in[0].isAlive()) {
						in[0].discard();
					}
				}
				helper.succeed();
			});
		}
		helper.runAfterDelay(301, () -> {
			if (done[0]) {
				return;
			}
			Config.MAGPIE_TAKES = takes;
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + MagpieService.mode(in[0]) + ", carrying "
					+ MagpieService.carried(in[0]).size() + ")";
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Magpie never took three things from the chest (it is at %s)", where);
		});
	}

	/**
	 * A Wick eats the three torches round it, but not the one beside a soul torch; slain, it gives the three back. (On a
	 * floor of stone, so every torch stands.)
	 */
	@GameTest(environment = Alone.WICK_FEAST, maxTicks = 500)
	public void aWickEatsTheTorchesButFearsSoulFire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		GameRules rules = level.getGameRules();
		boolean griefing = rules.get(GameRules.MOB_GRIEFING);
		rules.set(GameRules.MOB_GRIEFING, true, server);
		int fear = Config.WICK_FEAR_RANGE, meals = Config.WICK_MEALS, eats = Config.WICK_EATS, burst = Config.WICK_BURST_SECONDS;
		Config.WICK_FEAR_RANGE = 3;
		Config.WICK_MEALS = 6;
		Config.WICK_EATS = 1;
		Config.WICK_BURST_SECONDS = 0;
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		BlockPos[] food = {new BlockPos(1, 1, 1), new BlockPos(6, 1, 1), new BlockPos(6, 1, 4)};
		for (BlockPos b : food) {
			helper.setBlock(b, Blocks.TORCH);
		}
		BlockPos guarded = new BlockPos(2, 1, 6), soul = new BlockPos(1, 1, 7);
		helper.setBlock(guarded, Blocks.TORCH);
		helper.setBlock(soul, Blocks.SOUL_TORCH);
		ServerPlayer p = TestPlayers.survivor(helper, "wick-host");
		place(helper, p, 7.5, 1, 7.5);
		Wick[] in = {null};
		helper.runAfterDelay(10, () -> {
			in[0] = ModEntities.WICK.spawn(level, helper.absolutePos(new BlockPos(4, 1, 1)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Wick came");
			WickService.adopt(level, in[0], p);
		});
		// The moment it has eaten its third torch, the torches are looked at and it is slain
		boolean[] done = {false};
		Runnable restore = () -> {
			Config.WICK_FEAR_RANGE = fear;
			Config.WICK_MEALS = meals;
			Config.WICK_EATS = eats;
			Config.WICK_BURST_SECONDS = burst;
			rules.set(GameRules.MOB_GRIEFING, griefing, server);
		};
		for (int t = 11; t <= 440; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0] || !in[0].isAlive() || WickService.meals(in[0]) < 3) {
					return;
				}
				done[0] = true;
				try {
					for (BlockPos b : food) {
						check(helper, level.getBlockState(helper.absolutePos(b)).isAir(), "the torch at %s is still there", b);
					}
					check(helper, level.getBlockState(helper.absolutePos(guarded)).is(Blocks.TORCH), "the Wick ate the torch beside the soul torch");
					check(helper, level.getBlockState(helper.absolutePos(soul)).is(Blocks.SOUL_TORCH), "the soul torch is gone");
					Vec3 at = in[0].position();
					in[0].hurtServer(level, level.damageSources().playerAttack(p), 50.0f);
					check(helper, !in[0].isAlive(), "the Wick did not die");
					int back = lying(level, at, Items.TORCH);
					check(helper, back == 3, "the slain Wick gave back %s torches, not three", back);
				} finally {
					restore.run();
					if (in[0].isAlive()) {
						in[0].discard();
					}
				}
				helper.succeed();
			});
		}
		helper.runAfterDelay(441, () -> {
			if (done[0]) {
				return;
			}
			restore.run();
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + WickService.mode(in[0]) + ", " + WickService.meals(in[0]) + " eaten)";
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Wick did not eat the three torches (it is at %s)", where);
		});
	}
}
