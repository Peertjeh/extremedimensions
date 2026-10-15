package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.coop.FallenService;
import com.mojang.brigadier.ParseResults;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** A fallen player cannot use commands (no going home, no leaving the world, no way out but an ally or giving in). */
@Mixin(Commands.class)
public abstract class FallenCommandsMixin {
	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$fallenSilence(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
		if (FallenService.refuseCommand(parse.getContext().getSource())) {
			ci.cancel();
		}
	}
}
