package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** A furnace's fuel and cooking time, for Cold Forge. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccess {
	@Accessor("litTimeRemaining")
	int extremedimensions$litTime();

	@Accessor("litTimeRemaining")
	void extremedimensions$setLitTime(int ticks);

	@Accessor("cookingTimer")
	int extremedimensions$cookingTimer();

	@Accessor("cookingTimer")
	void extremedimensions$setCookingTimer(int ticks);
}
