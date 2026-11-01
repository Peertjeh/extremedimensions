package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.lurker.SculkLurker;
import com.extremedimensions.lurker.SculkLurkerService;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.phys.Vec3;

/**
 * The Sculk Lurker: the Warden spares it, it rattles a shrieker awake without ever striking the player, and once a
 * Warden is up it leads it to the player and sets it on them.
 */
public class SculkLurkerTests {
	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	/** The Warden never takes it for a target (and so never hears it or grows angry at it), and its blows do it no harm. */
	@GameTest(environment = Alone.SCULK_SPARED)
	public void theWardenSparesTheSculkLurker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "sculk-spared");
		place(helper, p, 7.5, 0, 7.5);
		Warden w = EntityType.WARDEN.spawn(level, helper.absolutePos(new BlockPos(1, 0, 1)), EntitySpawnReason.COMMAND);
		SculkLurker sl = ModEntities.SCULK_LURKER.spawn(level, helper.absolutePos(new BlockPos(3, 0, 3)), EntitySpawnReason.COMMAND);
		try {
			check(helper, w != null && sl != null, "no Warden or no Sculk Lurker came");
			w.setNoAi(true);
			check(helper, !w.canTargetEntity(sl), "the Warden may take its Sculk Lurker for a target");
			check(helper, w.canTargetEntity(p), "the Warden may not take a player for a target any more");
			float health = sl.getHealth();
			check(helper, !sl.hurtServer(level, w.damageSources().mobAttack(w), 10f) && sl.getHealth() == health, "the Warden's blow hurt its Sculk Lurker");
			check(helper, sl.dampensVibrations(), "the Sculk Lurker's steps make vibrations");
		} finally {
			if (w != null) {
				w.discard();
			}
			if (sl != null) {
				sl.discard();
			}
		}
		helper.succeed();
	}

	/** It scurries to a shrieker that can call the Warden and rattles it: the shrieker shrieks. The player it never strikes. */
	@GameTest(environment = Alone.SCULK_BECKON, maxTicks = 320)
	public void aSculkLurkerRattlesAShriekerAwake(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos shrieker = helper.absolutePos(new BlockPos(6, 0, 6));
		level.setBlockAndUpdate(shrieker, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true));
		ServerPlayer p = TestPlayers.survivor(helper, "sculk-beckoned");
		place(helper, p, 3.5, 0, 6.5);
		SculkLurker[] in = {null};
		helper.runAfterDelay(70, () -> {
			in[0] = ModEntities.SCULK_LURKER.spawn(level, helper.absolutePos(new BlockPos(1, 0, 1)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Sculk Lurker came");
			p.setHealth(20f);
			SculkLurkerService.adopt(level, in[0], p, "beckon");
		});
		boolean[] shrieked = {false};
		float[] lowest = {20f};
		for (int t = 71; t <= 290; t++) {
			helper.runAfterDelay(t, () -> {
				var s = level.getBlockState(shrieker);
				shrieked[0] |= s.hasProperty(SculkShriekerBlock.SHRIEKING) && s.getValue(SculkShriekerBlock.SHRIEKING);
				lowest[0] = Math.min(lowest[0], p.getHealth());
			});
		}
		helper.runAfterDelay(291, () -> {
			try {
				check(helper, shrieked[0], "the Sculk Lurker never rattled the shrieker awake (it rattled %s, it is %s at %s)",
						in[0] == null ? 0 : SculkLurkerService.rattles(in[0]), in[0] == null ? "gone" : SculkLurkerService.mode(in[0]),
						in[0] == null ? "-" : in[0].blockPosition());
				check(helper, lowest[0] >= 20f, "the Sculk Lurker hurt the player (%s)", lowest[0]);
			} finally {
				if (in[0] != null && in[0].isAlive()) {
					in[0].discard();
				}
				level.setBlockAndUpdate(shrieker, Blocks.AIR.defaultBlockState());
			}
			helper.succeed();
		});
	}

	/**
	 * With a Warden up, it goes to it and leads it towards the player, the Warden's ear set on where it goes; near the
	 * player, it sets the Warden on them.
	 */
	@GameTest(environment = Alone.SCULK_LURE, maxTicks = 300)
	public void aSculkLurkerLeadsTheWardenToItsPrey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "sculk-lured");
		place(helper, p, 7.5, 0, 7.5);
		Warden[] w = {null};
		SculkLurker[] in = {null};
		helper.runAfterDelay(70, () -> {
			// (the Warden's blows can do the player no harm here: the test is about where its anger goes)
			p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 4));
			w[0] = EntityType.WARDEN.spawn(level, helper.absolutePos(new BlockPos(0, 0, 0)), EntitySpawnReason.COMMAND);
			in[0] = ModEntities.SCULK_LURKER.spawn(level, helper.absolutePos(new BlockPos(1, 0, 2)), EntitySpawnReason.COMMAND);
			check(helper, w[0] != null && in[0] != null, "no Warden or no Sculk Lurker came");
			SculkLurkerService.adopt(level, in[0], p, "lure");
		});
		boolean[] heard = {false};
		boolean[] angry = {false};
		boolean[] delivered = {false};
		for (int t = 72; t <= 270; t++) {
			helper.runAfterDelay(t, () -> {
				if (w[0] == null || in[0] == null) {
					return;
				}
				// the Warden's ear on the lurker as it leads
				heard[0] |= w[0].getBrain().getMemory(MemoryModuleType.DISTURBANCE_LOCATION)
						.filter(pos -> pos.distSqr(in[0].blockPosition()) <= 9).isPresent();
				angry[0] |= w[0].getEntityAngryAt().filter(e -> e == p).isPresent();
				delivered[0] |= SculkLurkerService.delivered(in[0]);
			});
		}
		helper.runAfterDelay(271, () -> {
			try {
				check(helper, heard[0] || delivered[0], "the Warden's ear was never set on the Sculk Lurker leading it");
				check(helper, delivered[0], "the Sculk Lurker never set the Warden on its prey (it is %s at %s)",
						in[0] == null ? "gone" : SculkLurkerService.mode(in[0]), in[0] == null ? "-" : in[0].blockPosition());
				check(helper, angry[0], "the Warden never grew angry at the player");
			} finally {
				if (w[0] != null) {
					w[0].discard();
				}
				if (in[0] != null && in[0].isAlive()) {
					in[0].discard();
				}
				p.removeAllEffects();
			}
			helper.succeed();
		});
	}
}
