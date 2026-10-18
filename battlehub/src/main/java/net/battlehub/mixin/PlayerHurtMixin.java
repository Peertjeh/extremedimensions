package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.battlehub.match.Thrown;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Snowballs and eggs hit players for 0 damage, which vanilla ignores entirely; in matches they hurt or push. */
@Mixin(Player.class)
public abstract class PlayerHurtMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void battlehub$thrown(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer self) {
			Boolean r = Thrown.hit(self, level, source, amount);
			if (r != null) cir.setReturnValue(r);
		}
	}
}
