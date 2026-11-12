package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.extremedimensions.client.compat.ShaderLight;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;

/**
 * With Iris (3.51.1): a shader pack gives glow and coloured light only to the blocks its own list names, and names none
 * of the mod's. As Iris hands it the list, the mod's lanterns are added to it under a vanilla light's name (see
 * ShaderLight). Applied only where Iris is installed; nothing happens without it.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", remap = false)
public abstract class IrisBlockIdsMixin {
	@ModifyVariable(method = "setBlockStateIds", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
	private Object2IntMap<BlockState> extremedimensions$lanterns(Object2IntMap<BlockState> ids) {
		return ShaderLight.withLanterns(ids);
	}
}
