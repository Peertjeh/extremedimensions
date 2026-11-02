package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;

/** Which item an item display shows, set from code: for the loot a Magpie holds up as it runs. */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccess {
	@Invoker("setItemStack")
	void extremedimensions$setItemStack(ItemStack stack);

	@Invoker("getItemStack")
	ItemStack extremedimensions$getItemStack();
}
