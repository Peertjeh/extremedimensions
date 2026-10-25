package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.penalty.AbyssalWorld;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;

/** Unwelcome: near a cursed player, a door breaker breaks doors whatever the difficulty. */
@Mixin(BreakDoorGoal.class)
public abstract class UnwelcomeDoorMixin extends DoorInteractGoal {
	UnwelcomeDoorMixin(Mob mob) {
		super(mob);
	}

	@Inject(method = "isValidDifficulty", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$unwelcome(Difficulty difficulty, CallbackInfoReturnable<Boolean> cir) {
		if (AbyssalWorld.unwelcome(this.mob)) {
			cir.setReturnValue(true);
		}
	}
}
