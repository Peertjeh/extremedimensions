package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.lurker.Lockwarden;
import com.extremedimensions.lurker.LockwardenService;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Lockwarden of 3.21: it comes on in steps and snatches a player's ender pearls, leaves them on a lost islet of end
 * stone with one pearl at their feet, and drops all it took when it is slain.
 */
public class LockTests {
	private static int pearlsNear(ServerLevel level, Vec3 at, double r) {
		int n = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(r))) {
			if (e.getItem().is(Items.ENDER_PEARL)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	@GameTest(environment = Alone.LOCK_SNATCH, maxTicks = 300)
	public void aLockwardenSnatchesPearlsAndMaroonsItsPrey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		ServerPlayer p = TestPlayers.survivor(helper, "lock-traveller");
		Vec3 start = helper.absoluteVec(new Vec3(6.5, 1, 6.5));
		p.snapTo(start.x, start.y, start.z, 0f, 0f);
		p.getInventory().setItem(0, new ItemStack(Items.ENDER_PEARL, 16));
		double chance = Config.LOCKWARDEN_MAROON_CHANCE;
		int takes = Config.LOCKWARDEN_TAKES;
		Config.LOCKWARDEN_MAROON_CHANCE = 1;
		Config.LOCKWARDEN_TAKES = 16;
		Runnable restore = () -> {
			Config.LOCKWARDEN_MAROON_CHANCE = chance;
			Config.LOCKWARDEN_TAKES = takes;
		};
		Lockwarden[] in = {null};
		helper.runAfterDelay(10, () -> {
			in[0] = ModEntities.LOCKWARDEN.spawn(level, helper.absolutePos(new BlockPos(1, 1, 1)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Lockwarden came");
			LockwardenService.adopt(level, in[0], p, "close");
		});
		boolean[] done = {false};
		for (int t = 11; t <= 260; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0] || !in[0].isAlive() || LockwardenService.stolen(in[0]) <= 0) {
					return;
				}
				done[0] = true;
				try {
					check(helper, LockwardenService.pearls(p) == 0, "the player still has %s ender pearls", LockwardenService.pearls(p));
					check(helper, LockwardenService.stolen(in[0]) == 16, "the Lockwarden took %s pearls, not 16", LockwardenService.stolen(in[0]));
					check(helper, LockwardenService.marooned(in[0]), "the Lockwarden did not leave its prey on a lost islet");
					double moved = p.position().distanceTo(start);
					check(helper, moved > 15, "the player was moved only %.1f blocks", moved);
					check(helper, level.getBlockState(p.blockPosition().below()).is(Blocks.END_STONE), "the player does not stand on an islet of end stone");
					check(helper, pearlsNear(level, p.position(), 2) == 1, "there is not one pearl at the player's feet but %s", pearlsNear(level, p.position(), 2));
					Vec3 at = in[0].position();
					in[0].hurtServer(level, level.damageSources().playerAttack(p), 500.0f);
					check(helper, !in[0].isAlive(), "the Lockwarden did not die");
					check(helper, pearlsNear(level, at, 4) == 16, "the slain Lockwarden dropped %s pearls, not 16", pearlsNear(level, at, 4));
				} finally {
					restore.run();
					if (in[0].isAlive()) {
						in[0].discard();
					}
				}
				helper.succeed();
			});
		}
		helper.runAfterDelay(261, () -> {
			if (done[0]) {
				return;
			}
			restore.run();
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + LockwardenService.mode(in[0]) + ")";
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Lockwarden never snatched the pearls (it is at %s)", where);
		});
	}
}
