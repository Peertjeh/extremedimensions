package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.penalty.AbyssalWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/** Unmapped: a compass in the cursed player's hands spins, as it does where it has nothing to point at. */
@Mixin(CompassAngleState.class)
public abstract class UnmappedCompassMixin {
	@Shadow
	private float getRandomlySpinningRotation(int seed, long time) {
		throw new AssertionError();
	}

	@Inject(method = "calculate", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$unmapped(ItemStack stack, ClientLevel level, int seed, ItemOwner owner, CallbackInfoReturnable<Float> cir) {
		if (AbyssalWorld.clientUnmapped > 0 && level != null && owner != null && owner == Minecraft.getInstance().player) {
			cir.setReturnValue(this.getRandomlySpinningRotation(seed, level.getGameTime()));
		}
	}
}
