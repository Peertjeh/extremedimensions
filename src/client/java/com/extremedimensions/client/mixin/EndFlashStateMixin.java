package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.client.sky.SeamSky;

import net.minecraft.client.renderer.EndFlashState;

/**
 * No End flashes in the Seam (3.51.1). The Seam borrows the End's sky, and with it the End's flashes: drawn over by the
 * Seam's own sky they hardly show in vanilla, but a shader pack that draws them itself (a white-out with a beam) showed
 * them every few seconds. In the Seam the flash is held at nothing (its stored strength too, for whatever reads that
 * rather than asking), and none is ever begun (nor its sound).
 */
@Mixin(EndFlashState.class)
public abstract class EndFlashStateMixin {
	@Shadow
	private float intensity;
	@Shadow
	private float oldIntensity;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$stillInSeam(long time, CallbackInfo ci) {
		if (SeamSky.here()) {
			this.intensity = 0f;
			this.oldIntensity = 0f;
			ci.cancel();
		}
	}

	@Inject(method = "getIntensity", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$noFlashInSeam(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (SeamSky.here()) {
			cir.setReturnValue(0f);
		}
	}

	@Inject(method = "flashStartedThisTick", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$noFlashStartInSeam(CallbackInfoReturnable<Boolean> cir) {
		if (SeamSky.here()) {
			cir.setReturnValue(false);
		}
	}
}
