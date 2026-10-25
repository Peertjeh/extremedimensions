package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.penalty.AbyssalWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Barren Hand: a crop a cursed player planted loses its share of growth, and of bone meal. */
@Mixin(CropBlock.class)
public abstract class BarrenHandMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$barrenGrowth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (AbyssalWorld.barren(level, pos) && AbyssalWorld.withers(random)) {
			ci.cancel();
		}
	}

	@Inject(method = "performBonemeal", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$barrenMeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source, CallbackInfo ci) {
		if (AbyssalWorld.barren(level, pos) && AbyssalWorld.withers(random)) {
			ci.cancel();
		}
	}
}
