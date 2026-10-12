package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.lurker.Lurker;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class ModEntities {
	private ModEntities() {}

	private static final ResourceKey<EntityType<?>> LURKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("lurker"));

	/** A hunched giant, about three and a half blocks tall; spawned only by {@code LurkerService}. */
	public static final EntityType<Lurker> LURKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, LURKER_KEY,
			EntityType.Builder.<Lurker>of(Lurker::new, MobCategory.MONSTER)
					.sized(1.3f, 3.4f)
					.eyeHeight(3.0f)
					.clientTrackingRange(10)
					.build(LURKER_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(LURKER, Lurker.createAttributes());
	}
}
