package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.penalty.AbyssalWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Cold Forge: a furnace a cursed player works burns its fuel faster and cooks slower. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ColdForgeMixin {
	@Inject(method = "serverTick", at = @At("HEAD"))
	private static void extremedimensions$coldFuel(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
		if (AbyssalWorld.cold(level, pos)) {
			FurnaceAccess f = (FurnaceAccess) furnace;
			if (f.extremedimensions$litTime() > 1) {
				f.extremedimensions$setLitTime(Math.max(1, f.extremedimensions$litTime() - AbyssalWorld.extraFuel(level.getGameTime())));
			}
		}
	}

	@Inject(method = "serverTick", at = @At("TAIL"))
	private static void extremedimensions$coldCooking(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
		if (AbyssalWorld.cold(level, pos) && AbyssalWorld.slowTick(level, pos)) {
			FurnaceAccess f = (FurnaceAccess) furnace;
			if (f.extremedimensions$cookingTimer() > 0) {
				f.extremedimensions$setCookingTimer(f.extremedimensions$cookingTimer() - 1);
			}
		}
	}
}
