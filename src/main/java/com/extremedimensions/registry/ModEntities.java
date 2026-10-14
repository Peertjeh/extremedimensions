package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.hollow.Keeper;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.mending.StarvedOne;
import com.extremedimensions.seam.Thinned;
import com.extremedimensions.seam.VeilRay;

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

	private static final ResourceKey<EntityType<?>> STARVED_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("starved_one"));

	/** The last boss, at the Heart of the Seam; spawned only by {@code MendingService}. */
	public static final EntityType<StarvedOne> STARVED_ONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, STARVED_KEY,
			EntityType.Builder.<StarvedOne>of(StarvedOne::new, MobCategory.MONSTER)
					.sized(2.0f, 5.6f)
					.eyeHeight(5.0f)
					.clientTrackingRange(12)
					.build(STARVED_KEY));

	private static final ResourceKey<EntityType<?>> VEIL_RAY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("veil_ray"));
	private static final ResourceKey<EntityType<?>> THINNED_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("thinned"));

	/** A great glowing manta circling the islands of the Seam; spawned only by {@code SeamFauna}. */
	public static final EntityType<VeilRay> VEIL_RAY = Registry.register(BuiltInRegistries.ENTITY_TYPE, VEIL_RAY_KEY,
			EntityType.Builder.<VeilRay>of(VeilRay::new, MobCategory.AMBIENT)
					.sized(3.2f, 0.9f)
					.eyeHeight(0.5f)
					.clientTrackingRange(16)
					.build(VEIL_RAY_KEY));

	/** The worn-thin people of the Seam's villages; spawned only by {@code SeamFauna}. */
	public static final EntityType<Thinned> THINNED = Registry.register(BuiltInRegistries.ENTITY_TYPE, THINNED_KEY,
			EntityType.Builder.<Thinned>of(Thinned::new, MobCategory.MONSTER)
					.sized(0.9f, 2.6f)
					.eyeHeight(2.3f)
					.clientTrackingRange(10)
					.build(THINNED_KEY));

	private static final ResourceKey<EntityType<?>> MATRIARCH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("matriarch"));
	private static final ResourceKey<EntityType<?>> FERRYMAN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("ferryman"));
	private static final ResourceKey<EntityType<?>> WEAVER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("weaver"));

	/** The oldest Keeper, for those who slew enough of them; spawned only by {@code HollowService}. */
	public static final EntityType<com.extremedimensions.elder.Matriarch> MATRIARCH = Registry.register(BuiltInRegistries.ENTITY_TYPE, MATRIARCH_KEY,
			EntityType.Builder.<com.extremedimensions.elder.Matriarch>of(com.extremedimensions.elder.Matriarch::new, MobCategory.MONSTER)
					.sized(5.0f, 14.0f)
					.eyeHeight(12.5f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(MATRIARCH_KEY));

	/** The one the Nether pays; spawned only by {@code FerrymanService}. */
	public static final EntityType<com.extremedimensions.elder.Ferryman> FERRYMAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, FERRYMAN_KEY,
			EntityType.Builder.<com.extremedimensions.elder.Ferryman>of(com.extremedimensions.elder.Ferryman::new, MobCategory.MONSTER)
					.sized(5.0f, 12.0f)
					.eyeHeight(10.5f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(FERRYMAN_KEY));

	/** The Last Weaver, in the villages of the Thinned; spawned only by {@code WeaverService}. */
	public static final EntityType<com.extremedimensions.elder.Weaver> WEAVER = Registry.register(BuiltInRegistries.ENTITY_TYPE, WEAVER_KEY,
			EntityType.Builder.<com.extremedimensions.elder.Weaver>of(com.extremedimensions.elder.Weaver::new, MobCategory.MONSTER)
					.sized(4.0f, 15.0f)
					.eyeHeight(13.5f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(WEAVER_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(MATRIARCH, com.extremedimensions.elder.Matriarch.createAttributes());
		FabricDefaultAttributeRegistry.register(FERRYMAN, com.extremedimensions.elder.Ferryman.createAttributes());
		FabricDefaultAttributeRegistry.register(WEAVER, com.extremedimensions.elder.Weaver.createAttributes());
		FabricDefaultAttributeRegistry.register(VEIL_RAY, VeilRay.createAttributes());
		FabricDefaultAttributeRegistry.register(THINNED, Thinned.createAttributes());
		FabricDefaultAttributeRegistry.register(LURKER, Lurker.createAttributes());
		FabricDefaultAttributeRegistry.register(HOLLOWLING, Hollowling.createAttributes());
		FabricDefaultAttributeRegistry.register(KEEPER, Keeper.createAttributes());
		FabricDefaultAttributeRegistry.register(STARVED_ONE, StarvedOne.createAttributes());
	}
}
