package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import com.extremedimensions.binding.Charms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** The Curse of Fraying: in the Seam, an item takes more wear than its enchantments left it (see Charms). */
@Mixin(EnchantmentHelper.class)
public abstract class FrayingWearMixin {
	@ModifyReturnValue(method = "processDurabilityChange", at = @At("RETURN"))
	private static int extremedimensions$fraying(int wear, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) ItemStack stack) {
		return Charms.fraying(level, stack, wear);
	}
}
