package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.client.title.Menus;
import com.extremedimensions.client.title.TitleArt;

import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;

/** The title art carries its own title: Minecraft's logo and the splash text stand aside on the title screen while it shows. */
@Mixin({LogoRenderer.class, SplashRenderer.class})
public abstract class TitleLogoMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$standAside(CallbackInfo ci) {
		if (TitleArt.enabled() && Menus.current() instanceof TitleScreen) {
			ci.cancel();
		}
	}
}
