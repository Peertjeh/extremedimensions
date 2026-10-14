package com.extremedimensions.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Where a menu sits on screen, so the sealed slots can be drawn over any menu (see {@code SealedSlots}). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
	@Accessor("leftPos")
	int extremedimensions$leftPos();

	@Accessor("topPos")
	int extremedimensions$topPos();
}
