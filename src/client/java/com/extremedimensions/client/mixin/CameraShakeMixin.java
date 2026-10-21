package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.client.fx.CameraLock;
import com.extremedimensions.client.fx.CameraShake;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;

/**
 * An Elder's blows shake the camera (see {@link CameraShake}); the player's aim is untouched. While an Elder dies the
 * view is held on it (see {@link CameraLock}).
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "update", at = @At("TAIL"))
	private void extremedimensions$shake(DeltaTracker delta, CallbackInfo ci) {
		float[] held = CameraLock.apply(((Camera) (Object) this).position());
		if (held != null) {
			this.setRotation(held[0], held[1]);
		}
		float[] o = CameraShake.offset();
		if (o != null) {
			this.setRotation(this.yRot + o[0], this.xRot + o[1]);
		}
	}
}
