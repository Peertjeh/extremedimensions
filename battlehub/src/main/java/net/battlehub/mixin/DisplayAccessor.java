package net.battlehub.mixin;

import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;

/** Prop disguises: centre the block on its entity and move it smoothly. */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Accessor("DATA_TRANSLATION_ID")
	static EntityDataAccessor<Vector3fc> battlehub$translation() {
		throw new AssertionError();
	}

	@Accessor("DATA_POS_ROT_INTERPOLATION_DURATION_ID")
	static EntityDataAccessor<Integer> battlehub$posRotDuration() {
		throw new AssertionError();
	}
}
