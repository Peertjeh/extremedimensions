package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Display;

/** A display that always turns to face whoever looks at it: for the loot a Magpie holds up as it runs. */
@Mixin(Display.class)
public interface BillboardAccess {
	@Invoker("setBillboardConstraints")
	void extremedimensions$setBillboard(Display.BillboardConstraints constraints);
}
