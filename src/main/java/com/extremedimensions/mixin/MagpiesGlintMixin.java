package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.extremedimensions.relic.RelicService;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Magpie's Glint: a wild creature its bearer slew now and then drops what its loot table gives a second time (its
 * gear and anything else it drops come once, as ever; see RelicService).
 */
@Mixin(LivingEntity.class)
public abstract class MagpiesGlintMixin {
	@WrapOperation(method = "dropAllDeathLoot", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V"))
	private void extremedimensions$magpiesGlint(LivingEntity self, ServerLevel level, DamageSource source, boolean byPlayer, Operation<Void> drop) {
		drop.call(self, level, source, byPlayer);
		if (RelicService.lootTwice(self, source)) {
			drop.call(self, level, source, byPlayer);
		}
	}
}
