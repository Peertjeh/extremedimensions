package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/** Which block a block display shows, set from code: for the blocks of an Elder's arena. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccess {
	@Invoker("setBlockState")
	void extremedimensions$setBlockState(BlockState state);
}
