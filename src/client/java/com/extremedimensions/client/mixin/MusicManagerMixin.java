package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.client.audio.MenuMusic;

import net.minecraft.client.sounds.MusicManager;

/** Minecraft's own music waits while the menu music holds the menus and the join (see {@link MenuMusic}). */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$wait(CallbackInfo ci) {
		if (MenuMusic.holdsVanilla()) {
			ci.cancel();
		}
	}
}
