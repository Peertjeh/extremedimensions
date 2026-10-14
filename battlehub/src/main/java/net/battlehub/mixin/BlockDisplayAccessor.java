package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/** Which block a disguise shows. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {
	@Invoker("setBlockState")
	void battlehub$setBlockState(BlockState state);
}
