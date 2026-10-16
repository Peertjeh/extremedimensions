package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.battlehub.arena.FluidGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Buckets in an arena are emptied only where the game allows building (also when aimed at the air or a fluid). */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
	@Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
	private void battlehub$onlyOnTheField(LivingEntity who, Level level, BlockPos pos, BlockHitResult hit, CallbackInfoReturnable<Boolean> cir) {
		if (!FluidGuard.mayEmpty(who, level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
