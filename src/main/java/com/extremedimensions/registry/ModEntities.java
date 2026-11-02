package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.hollow.Keeper;
import com.extremedimensions.lurker.CaveLurker;
import com.extremedimensions.lurker.Gatebreaker;
import com.extremedimensions.lurker.Lockwarden;
import com.extremedimensions.lurker.Magpie;
import com.extremedimensions.lurker.Tidewalker;
import com.extremedimensions.lurker.Wick;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.SculkLurker;
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
					.notInPeaceful()
					.sized(1.3f, 3.4f)
					.eyeHeight(3.0f)
					.clientTrackingRange(10)
					.build(LURKER_KEY));

	private static final ResourceKey<EntityType<?>> CAVE_LURKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("cave_lurker"));

	/**
	 * The Lurker's kin of the deep, low enough for a miner's tunnel (narrower than a block, lower than two); spawned only
	 * by {@code CaveLurkerService}.
	 */
	public static final EntityType<CaveLurker> CAVE_LURKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, CAVE_LURKER_KEY,
			EntityType.Builder.<CaveLurker>of(CaveLurker::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(0.8f, 1.8f)
					.eyeHeight(1.45f)
					.clientTrackingRange(8)
					.build(CAVE_LURKER_KEY));

	private static final ResourceKey<EntityType<?>> MAGPIE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("magpie"));

	/** The tribe's lean thief, a little taller than a man, that robs chests; spawned only by {@code MagpieService}. */
	public static final EntityType<Magpie> MAGPIE = Registry.register(BuiltInRegistries.ENTITY_TYPE, MAGPIE_KEY,
			EntityType.Builder.<Magpie>of(Magpie::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(0.8f, 2.3f)
					.eyeHeight(2.0f)
					.clientTrackingRange(10)
					.build(MAGPIE_KEY));

	private static final ResourceKey<EntityType<?>> WICK_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("wick"));

	/** A knee-high thing of tallow and soot that eats the light at night; spawned only by {@code WickService}. */
	public static final EntityType<Wick> WICK = Registry.register(BuiltInRegistries.ENTITY_TYPE, WICK_KEY,
			EntityType.Builder.<Wick>of(Wick::new, MobCategory.MONSTER)
					.notInPeaceful()
					.fireImmune()
					.sized(0.45f, 0.6f)
					.eyeHeight(0.45f)
					.clientTrackingRange(8)
					.build(WICK_KEY));

	private static final ResourceKey<EntityType<?>> TIDEWALKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("tidewalker"));

	/** The tribe's kin of the deep water, that capsizes boats and drags swimmers down; spawned only by {@code TidewalkerService}. */
	public static final EntityType<Tidewalker> TIDEWALKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, TIDEWALKER_KEY,
			EntityType.Builder.<Tidewalker>of(Tidewalker::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(0.9f, 2.6f)
					.eyeHeight(2.3f)
					.clientTrackingRange(10)
					.build(TIDEWALKER_KEY));

	private static final ResourceKey<EntityType<?>> GATEBREAKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("gatebreaker"));

	/** The giant the Wound Storms wake, that breaks through walls; spawned only by {@code GatebreakerService}. */
	public static final EntityType<Gatebreaker> GATEBREAKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, GATEBREAKER_KEY,
			EntityType.Builder.<Gatebreaker>of(Gatebreaker::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(1.8f, 4.6f)
					.eyeHeight(4.0f)
					.clientTrackingRange(12)
					.build(GATEBREAKER_KEY));

	private static final ResourceKey<EntityType<?>> LOCKWARDEN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("lockwarden"));

	/** The tribe's keeper of the Dragon's lock, that roams the End after ender pearls; spawned only by {@code LockwardenService}. */
	public static final EntityType<Lockwarden> LOCKWARDEN = Registry.register(BuiltInRegistries.ENTITY_TYPE, LOCKWARDEN_KEY,
			EntityType.Builder.<Lockwarden>of(Lockwarden::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(1.0f, 3.6f)
					.eyeHeight(3.2f)
					.clientTrackingRange(10)
					.build(LOCKWARDEN_KEY));

	private static final ResourceKey<EntityType<?>> SCULK_LURKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("sculk_lurker"));

	/** The Warden's small servant in the Deep Dark, the size of a Hollowling; spawned only by {@code SculkLurkerService}. */
	public static final EntityType<SculkLurker> SCULK_LURKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, SCULK_LURKER_KEY,
			EntityType.Builder.<SculkLurker>of(SculkLurker::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(0.6f, 1.1f)
					.eyeHeight(0.9f)
					.clientTrackingRange(8)
					.build(SCULK_LURKER_KEY));

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
					.notInPeaceful()
					.sized(1.6f, 4.2f)
					.eyeHeight(3.7f)
					.clientTrackingRange(10)
					.build(KEEPER_KEY));

	private static final ResourceKey<EntityType<?>> STARVED_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("starved_one"));

	/** The last boss, at the Heart of the Seam; spawned only by {@code MendingService}. It stays on peaceful, as the Ender Dragon does. */
	public static final EntityType<StarvedOne> STARVED_ONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, STARVED_KEY,
			EntityType.Builder.<StarvedOne>of(StarvedOne::new, MobCategory.MONSTER)
					.sized(2.4f, 6.4f)
					.eyeHeight(5.4f)
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
					.notInPeaceful()
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
					.notInPeaceful()
					.sized(5.0f, 14.0f)
					.eyeHeight(12.5f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(MATRIARCH_KEY));

	/** The one the Nether pays; spawned only by {@code FerrymanService}. */
	public static final EntityType<com.extremedimensions.elder.Ferryman> FERRYMAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, FERRYMAN_KEY,
			EntityType.Builder.<com.extremedimensions.elder.Ferryman>of(com.extremedimensions.elder.Ferryman::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(5.0f, 12.0f)
					.eyeHeight(10.5f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(FERRYMAN_KEY));

	/** The Last Weaver, in the villages of the Thinned; spawned only by {@code WeaverService}. */
	public static final EntityType<com.extremedimensions.elder.Weaver> WEAVER = Registry.register(BuiltInRegistries.ENTITY_TYPE, WEAVER_KEY,
			EntityType.Builder.<com.extremedimensions.elder.Weaver>of(com.extremedimensions.elder.Weaver::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(4.0f, 15.0f)
					.eyeHeight(13.5f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(WEAVER_KEY));

	private static final ResourceKey<EntityType<?>> KNOT_MOTHER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("knot_mother"));

	/** The Knot Mother, tied into the First Knot deep in the Fray; woken only by {@code FirstKnot.wake}. */
	public static final EntityType<com.extremedimensions.elder.KnotMother> KNOT_MOTHER = Registry.register(BuiltInRegistries.ENTITY_TYPE, KNOT_MOTHER_KEY,
			EntityType.Builder.<com.extremedimensions.elder.KnotMother>of(com.extremedimensions.elder.KnotMother::new, MobCategory.MONSTER)
					.notInPeaceful()
					.sized(4.0f, 15.0f)
					.eyeHeight(13.5f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(KNOT_MOTHER_KEY));

	private static final ResourceKey<EntityType<?>> RIFT_PORTAL_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("rift_portal"));

	/** A rift as it is seen (a sight only, never saved); put where each rift stands by {@code SeamService}. */
	public static final EntityType<com.extremedimensions.seam.RiftPortal> RIFT_PORTAL = Registry.register(BuiltInRegistries.ENTITY_TYPE, RIFT_PORTAL_KEY,
			EntityType.Builder.<com.extremedimensions.seam.RiftPortal>of(com.extremedimensions.seam.RiftPortal::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(10)
					.build(RIFT_PORTAL_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(MATRIARCH, com.extremedimensions.elder.Matriarch.createAttributes());
		FabricDefaultAttributeRegistry.register(FERRYMAN, com.extremedimensions.elder.Ferryman.createAttributes());
		FabricDefaultAttributeRegistry.register(WEAVER, com.extremedimensions.elder.Weaver.createAttributes());
		FabricDefaultAttributeRegistry.register(KNOT_MOTHER, com.extremedimensions.elder.KnotMother.createAttributes());
		FabricDefaultAttributeRegistry.register(VEIL_RAY, VeilRay.createAttributes());
		FabricDefaultAttributeRegistry.register(THINNED, Thinned.createAttributes());
		FabricDefaultAttributeRegistry.register(LURKER, Lurker.createAttributes());
		FabricDefaultAttributeRegistry.register(CAVE_LURKER, CaveLurker.createAttributes());
		FabricDefaultAttributeRegistry.register(SCULK_LURKER, SculkLurker.createAttributes());
		FabricDefaultAttributeRegistry.register(MAGPIE, Magpie.createAttributes());
		FabricDefaultAttributeRegistry.register(WICK, Wick.createAttributes());
		FabricDefaultAttributeRegistry.register(TIDEWALKER, Tidewalker.createAttributes());
		FabricDefaultAttributeRegistry.register(GATEBREAKER, Gatebreaker.createAttributes());
		FabricDefaultAttributeRegistry.register(LOCKWARDEN, Lockwarden.createAttributes());
		FabricDefaultAttributeRegistry.register(HOLLOWLING, Hollowling.createAttributes());
		FabricDefaultAttributeRegistry.register(KEEPER, Keeper.createAttributes());
		FabricDefaultAttributeRegistry.register(STARVED_ONE, StarvedOne.createAttributes());
	}
}
