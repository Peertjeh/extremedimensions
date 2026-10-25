package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.penalty.AbyssalWorld;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;

/** Cold Forge's deed: what a player takes out of a furnace counts. */
@Mixin(FurnaceResultSlot.class)
public abstract class ColdForgeTakeMixin {
	@Shadow
	@Final
	private Player player;

	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void extremedimensions$smelted(ItemStack stack, CallbackInfo ci) {
		AbyssalWorld.smelted(this.player, this.removeCount);
	}
}
