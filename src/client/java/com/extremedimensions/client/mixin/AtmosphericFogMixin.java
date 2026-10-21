package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector3fc;

import com.extremedimensions.client.sky.SeamFog;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;

/** In the Seam, the open-air fog rolls in and out in banks (see {@link SeamFog}). */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
	/** Over a frayed island the fog takes the Fray's green. */
	@Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
	private void extremedimensions$frayedGreen(ClientLevel level, Camera camera, int renderDistance, float partialTick, CallbackInfoReturnable<Vector3fc> cir) {
		Vector3fc tinted = SeamFog.tint(cir.getReturnValue());
		if (tinted != null) {
			cir.setReturnValue(tinted);
		}
	}

	@Inject(method = "setupFog", at = @At("TAIL"))
	private void extremedimensions$seamFog(FogData data, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
		SeamFog.apply(data);
	}
}
