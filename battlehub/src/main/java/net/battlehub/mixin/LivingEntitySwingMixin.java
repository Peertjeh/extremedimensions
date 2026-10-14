package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.battlehub.HubEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * Arm swings sent by the client, also at thin air (Prop Hunt hunters pay for missing). The server swings a
 * player's arm itself after some item uses, but then tells the player's own client too (updateSelf); a swing
 * from the client's packet does not, which is how the two are told apart.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), require = 0)
	private void battlehub$swing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
		if (!updateSelf && (Object) this instanceof ServerPlayer p) {
			HubEvents.swung(p);
		}
	}
}
