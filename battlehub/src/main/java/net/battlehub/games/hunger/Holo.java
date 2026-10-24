package net.battlehub.games.hunger;

import net.battlehub.mixin.DisplayAccessor;
import net.battlehub.mixin.TextDisplayAccessor;
import net.battlehub.util.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/** Floating texts and blocks of the Hunger Games (the lock over a vault, the fallen in the sky, a parachute). */
final class Holo {
	private Holo() {}

	/** A text that always faces the viewer. */
	static Display.TextDisplay text(ServerLevel level, Vec3 at, Component text, float scale) {
		Display.TextDisplay d = create(level, "minecraft:text_display", Display.TextDisplay.class);
		if (d == null) return null;
		d.getEntityData().set(TextDisplayAccessor.battlehub$text(), text);
		d.getEntityData().set(TextDisplayAccessor.battlehub$background(), 0x40000000);
		// centre: the text turns to whoever looks at it
		d.getEntityData().set(DisplayAccessor.battlehub$billboard(), (byte) 3);
		d.getEntityData().set(DisplayAccessor.battlehub$scale(), new org.joml.Vector3f(scale, scale, scale));
		d.snapTo(at.x, at.y, at.z);
		d.addTag("battlehub_hg");
		level.addFreshEntity(d);
		return d;
	}

	static void setText(Display.TextDisplay d, Component text) {
		if (d != null && !d.isRemoved()) d.getEntityData().set(TextDisplayAccessor.battlehub$text(), text);
	}

	/** A block shown at {@code at} (its middle), moving smoothly when moved. */
	static Display.BlockDisplay block(ServerLevel level, Vec3 at, net.minecraft.world.level.block.state.BlockState state, float scale) {
		Display.BlockDisplay d = create(level, "minecraft:block_display", Display.BlockDisplay.class);
		if (d == null) return null;
		((net.battlehub.mixin.BlockDisplayAccessor) d).battlehub$setBlockState(state);
		d.getEntityData().set(DisplayAccessor.battlehub$translation(), new org.joml.Vector3f(-0.5f * scale, 0f, -0.5f * scale));
		d.getEntityData().set(DisplayAccessor.battlehub$scale(), new org.joml.Vector3f(scale, scale, scale));
		d.getEntityData().set(DisplayAccessor.battlehub$posRotDuration(), 3);
		d.snapTo(at.x, at.y, at.z);
		d.addTag("battlehub_hg");
		level.addFreshEntity(d);
		return d;
	}

	static <T extends Entity> T create(ServerLevel level, String id, Class<T> type) {
		EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id(id));
		Entity e = t == null ? null : t.create(level, EntitySpawnReason.COMMAND);
		return type.isInstance(e) ? type.cast(e) : null;
	}

	static void remove(Entity e) {
		if (e != null && !e.isRemoved()) e.discard();
	}
}
