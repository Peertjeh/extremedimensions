package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.math.Transformation;

import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;

/** The look of a display entity, set from code (vanilla only sets it from data): for the blocks of an Elder's arena. */
@Mixin(Display.class)
public interface DisplayAccess {
	@Invoker("setTransformation")
	void extremedimensions$setTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void extremedimensions$setInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void extremedimensions$setInterpolationDelay(int ticks);

	@Invoker("setBrightnessOverride")
	void extremedimensions$setBrightnessOverride(Brightness brightness);
}
