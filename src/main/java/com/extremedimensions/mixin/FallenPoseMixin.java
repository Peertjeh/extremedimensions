package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.coop.Crawl;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** The fallen lie on the ground and crawl, whatever room there is to stand (see {@link Crawl}). */
@Mixin(Player.class)
public abstract class FallenPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$crawl(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (Crawl.crawls(self)) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}
}
