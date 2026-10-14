package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.battlehub.HubEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Arm swings of players, also at thin air (Prop Hunt hunters pay for missing). Matched by name only: in
 * Minecraft 26.3 the method also takes the swing animation and returns a value. The game decides what a
 * swing means (a hit or a block in reach is not a miss).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
	@Inject(method = "swing", at = @At("HEAD"), require = 0)
	private void battlehub$swing(CallbackInfoReturnable<?> cir) {
		if ((Object) this instanceof ServerPlayer p) {
			HubEvents.swung(p);
		}
	}
}
