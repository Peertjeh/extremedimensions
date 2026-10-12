package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.hollow.Keeper;
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

	private static final ResourceKey<EntityType<?>> HOLLOWLING_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("hollowling"));
	private static final ResourceKey<EntityType<?>> KEEPER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("keeper"));

	/** The tribe's young: knee-high thieves around a hoard; spawned only by {@code HollowService}. */
	public static final EntityType<Hollowling> HOLLOWLING = Registry.register(BuiltInRegistries.ENTITY_TYPE, HOLLOWLING_KEY,
			EntityType.Builder.<Hollowling>of(Hollowling::new, MobCategory.CREATURE)
					.sized(0.6f, 1.1f)
					.eyeHeight(0.95f)
					.clientTrackingRange(8)
					.build(HOLLOWLING_KEY));

	/** A hoard's guardian, larger than a Lurker; spawned only by {@code HollowService}. */
	public static final EntityType<Keeper> KEEPER = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEEPER_KEY,
			EntityType.Builder.<Keeper>of(Keeper::new, MobCategory.MONSTER)
					.sized(1.6f, 4.2f)
					.eyeHeight(3.7f)
					.clientTrackingRange(10)
					.build(KEEPER_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(LURKER, Lurker.createAttributes());
		FabricDefaultAttributeRegistry.register(HOLLOWLING, Hollowling.createAttributes());
		FabricDefaultAttributeRegistry.register(KEEPER, Keeper.createAttributes());
	}
}
