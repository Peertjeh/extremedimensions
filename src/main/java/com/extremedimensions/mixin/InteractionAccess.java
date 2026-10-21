package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Interaction;

/** The size of an interaction entity, set from code (vanilla only sets it from data): the bars of the Ferryman's gaol. */
@Mixin(Interaction.class)
public interface InteractionAccess {
	@Invoker("setWidth")
	void extremedimensions$setWidth(float width);

	@Invoker("setHeight")
	void extremedimensions$setHeight(float height);
}
