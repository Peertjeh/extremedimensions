package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.battlehub.games.Visibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Disguised props are not sent to other players at all: no body, held item, name or hitbox gives them away. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true, require = 0)
	private void battlehub$hidden(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
		if (Visibility.hidden((Entity) (Object) this, viewer)) {
			cir.setReturnValue(false);
		}
	}
}
