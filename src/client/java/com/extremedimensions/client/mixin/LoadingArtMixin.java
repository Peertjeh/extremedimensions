package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.client.title.TitleArt;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * The title art behind the world loading screen and the message screens while a world is joined (see {@link TitleArt}).
 * Both draw their own background, so the art goes on after it, before their progress and text.
 */
@Mixin({LevelLoadingScreen.class, GenericMessageScreen.class})
public abstract class LoadingArtMixin {
	@Inject(method = "extractBackground", at = @At("RETURN"))
	private void extremedimensions$art(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		TitleArt.drawLoading(g, self.width, self.height);
	}
}
