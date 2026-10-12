package com.extremedimensions.client.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.extremedimensions.client.sky.SeamSky;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;

/** In the Seam, the End sky's texture and geometry are swapped for the Seam's own cubemap. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Shadow
	@Final
	private AbstractTexture endSkyTexture;

	@Shadow
	@Final
	private GpuBuffer endSkyBuffer;

	@Redirect(method = "renderEndSky", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/SkyRenderer;endSkyTexture:Lnet/minecraft/client/renderer/texture/AbstractTexture;"))
	private AbstractTexture extremedimensions$seamTexture(SkyRenderer self) {
		return SeamSky.active() ? SeamSky.texture() : this.endSkyTexture;
	}

	@Redirect(method = "renderEndSky", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/SkyRenderer;endSkyBuffer:Lcom/mojang/renderpearl/api/buffers/GpuBuffer;"))
	private GpuBuffer extremedimensions$seamBuffer(SkyRenderer self) {
		return SeamSky.active() ? SeamSky.buffer(this.endSkyBuffer) : this.endSkyBuffer;
	}
}
