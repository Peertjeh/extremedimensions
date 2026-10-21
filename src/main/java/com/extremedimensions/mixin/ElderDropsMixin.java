package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.elder.ElderArena;
import com.extremedimensions.elder.ElderTerrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * What an Elder built or raised drops nothing, however it comes down: mined, blown up (a creeper, TNT, the Elders' own
 * blasts) or knocked off its support. Every block drop passes through here, so arenas and thorns are no quarry.
 */
@Mixin(Block.class)
public abstract class ElderDropsMixin {
	@Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"),
			cancellable = true)
	private static void extremedimensions$noElderLoot(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		if (ElderArena.holds(level, pos) || ElderTerrain.temporary(level, pos)) {
			ci.cancel();
		}
	}
}
