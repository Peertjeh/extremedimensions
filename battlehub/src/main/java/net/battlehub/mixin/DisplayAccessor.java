package net.battlehub.mixin;

import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;

/** Prop disguises (centre the block on its entity, move it smoothly); size and facing of the lobby texts. */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Accessor("DATA_TRANSLATION_ID")
	static EntityDataAccessor<Vector3fc> battlehub$translation() {
		throw new AssertionError();
	}

	@Accessor("DATA_SCALE_ID")
	static EntityDataAccessor<Vector3fc> battlehub$scale() {
		throw new AssertionError();
	}

	@Accessor("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID")
	static EntityDataAccessor<Byte> battlehub$billboard() {
		throw new AssertionError();
	}

	@Accessor("DATA_POS_ROT_INTERPOLATION_DURATION_ID")
	static EntityDataAccessor<Integer> battlehub$posRotDuration() {
		throw new AssertionError();
	}
}
