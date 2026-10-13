package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** Bow Spleef needs to know when an arrow hit a block. */
@Mixin(AbstractArrow.class)
public interface ArrowAccessor {
	@Invoker("isInGround")
	boolean battlehub$isInGround();
}
