package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.penalty.AtonementGuard;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/** Remembers which monsters spawned in the wild, so only those count towards atonement (see {@link AtonementGuard}). */
@Mixin(Mob.class)
public abstract class NaturalSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void extremedimensions$wild(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		AtonementGuard.spawned((Mob) (Object) this, reason);
	}
}
