package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.registry.ModItems;

import net.minecraft.world.entity.player.Player;

/** Looking through a Wound Lens is scoping, as through a spyglass: the zoom, the scope overlay, the steady hand. */
@Mixin(Player.class)
public abstract class WoundLensMixin {
	@Inject(method = "isScoping", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$lens(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (self.isUsingItem() && self.getUseItem().is(ModItems.WOUND_LENS)) {
			cir.setReturnValue(true);
		}
	}
}
