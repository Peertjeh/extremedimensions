package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Interaction;

/** Lobby characters: a click box the size of a player. */
@Mixin(Interaction.class)
public interface InteractionAccessor {
	@Invoker("setWidth")
	void battlehub$setWidth(float width);

	@Invoker("setHeight")
	void battlehub$setHeight(float height);
}
