package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.lurker.Lurker;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enderman;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class ModEntities {
	private ModEntities() {}

	private static final ResourceKey<EntityType<?>> LURKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("lurker"));

	/** Slightly taller than an Enderman; spawned only by {@code LurkerService}. */
	public static final EntityType<Lurker> LURKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, LURKER_KEY,
			EntityType.Builder.<Lurker>of(Lurker::new, MobCategory.MONSTER)
					.sized(0.7f, 3.3f)
					.eyeHeight(2.9f)
					.clientTrackingRange(10)
					.build(LURKER_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(LURKER, Enderman.createAttributes());
	}
}
