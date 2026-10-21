package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.elder.ElderSpells;

import net.minecraft.world.entity.player.Player;

/** Players decide for themselves whether they can glide: bound wings say no there too (see BoundWingsMixin). */
@Mixin(Player.class)
public abstract class BoundWingsPlayerMixin {
	@Inject(method = "canGlide", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$boundWings(CallbackInfoReturnable<Boolean> cir) {
		if (ElderSpells.grounded((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
