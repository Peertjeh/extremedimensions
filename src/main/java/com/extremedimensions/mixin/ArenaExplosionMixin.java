package com.extremedimensions.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.elder.ArenaRuin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;

/** Whatever an explosion takes out of an arena is looked at afterwards: what is left without support comes down. */
@Mixin(ServerExplosion.class)
public abstract class ArenaExplosionMixin {
	@Inject(method = "interactWithBlocks(Ljava/util/List;)V", at = @At("HEAD"))
	private void extremedimensions$arenaBlasted(List<BlockPos> blocks, CallbackInfo ci) {
		ArenaRuin.blasted(((ServerExplosion) (Object) this).level(), blocks);
	}
}
