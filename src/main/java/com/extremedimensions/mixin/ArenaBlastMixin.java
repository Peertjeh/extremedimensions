package com.extremedimensions.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderArena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * An arena's blocks stand up to an explosion no better than {@code elder_arena_blast_resistance} (stone, by default),
 * whatever they are made of: an Elder's obsidian and blackstone break like the rest of the world.
 */
@Mixin(ExplosionDamageCalculator.class)
public abstract class ArenaBlastMixin {
	@Inject(method = "getBlockExplosionResistance", at = @At("RETURN"), cancellable = true)
	private void extremedimensions$arenaGivesWay(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState state, FluidState fluid,
			CallbackInfoReturnable<Optional<Float>> cir) {
		if (Config.ELDER_ARENA_BREAKABLE > 0 && getter instanceof Level level && ElderArena.holds(level, pos)) {
			float cap = (float) Config.ELDER_ARENA_BLAST_RESISTANCE;
			cir.setReturnValue(cir.getReturnValue().map(r -> Math.min(r, cap)));
		}
	}
}
