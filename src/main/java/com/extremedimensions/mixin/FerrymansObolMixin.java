package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.relic.RelicService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The Ferryman's Obol: dying beyond the wound, its bearer drops nothing they carry (the bag, the armour, the off hand), as
 * with the game's keepInventory, and has it all back on respawn (see RelicService). Their experience falls as ever.
 */
@Mixin(Player.class)
public abstract class FerrymansObolMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$ferrymansObol(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer p && RelicService.spareDrops(p)) {
			ci.cancel();
		}
	}
}
