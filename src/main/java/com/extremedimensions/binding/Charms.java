package com.extremedimensions.binding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.BlockScan;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the enchantments of 3.3 do that their data cannot: the ones that reach into the mod's own workings (the Tether,
 * the Seam, its curses, the Lurker, the Elders), and the ones that need a moment of their own. Unravelling, Wardweave
 * and Anchored's steadiness are wholly in their data files.
 *
 * <ul>
 * <li>Reaping (weapon): a monster slain on an expedition gives back seconds of charge to the Tether's anchor.</li>
 * <li>Tethershot (bow, crossbow): an arrow pulls what it hits toward the archer.</li>
 * <li>Veinseeker (pickaxe): stone broken in the Seam makes the Tetherite ore and Seam Veins near it ring out.</li>
 * <li>Anchored (chest, legs): the End's void throws its wearer less (Unmoored).</li>
 * <li>Sated (helmet) and the Curse of the Starved (armour): hunger, slower or faster.</li>
 * <li>Driftfall (boots): a long fall in the Seam is caught with a moment of slow falling.</li>
 * <li>Hushed Steps (boots): harder to notice.</li>
 * <li>Foresight (Tether): one card more when the Tether gives way.</li>
 * <li>Soulward (helmet): the Lurker may flinch before it takes anything.</li>
 * <li>Knitting (any gear): in the Seam, worn and held gear knits itself back together.</li>
 * <li>Steadfast (legs): the Elders' spells take less hold.</li>
 * <li>The Curse of Fraying (any gear): in the Seam, it wears faster.</li>
 * </ul>
 */
public final class Charms {
	private Charms() {}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final EquipmentSlot[] KNIT = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** When each player's Veinseeker and Driftfall may answer again (game time). */
	private static final Map<UUID, Long> SEEK = new HashMap<>(), DRIFT = new HashMap<>();

	/** The level of an enchantment on what a creature holds or wears in one slot. */
	public static int on(LivingEntity e, EquipmentSlot slot, ResourceKey<Enchantment> k) {
		return ModEnchantments.level(e.level().registryAccess(), e.getItemBySlot(slot), k);
	}

	// ------------------------------------------------------------------ Reaping

	/** A monster slain with Reaping on an expedition gives back charge to the first anchor the Tether is tied to. */
	public static double reap(ServerPlayer killer, LivingEntity victim) {
		if (!(victim instanceof Enemy)) {
			return 0;
		}
		int lvl = on(killer, EquipmentSlot.MAINHAND, ModEnchantments.REAPING);
		PlayerData d = DataStore.get(killer.getUUID());
		if (lvl <= 0 || !d.inRun || d.collapsing) {
			return 0;
		}
		List<Anchor> linked = AnchorService.linked(d);
		if (linked.isEmpty()) {
			return 0;
		}
		Anchor a = linked.get(0);
		String uid = killer.getStringUUID();
		double room = Math.max(0, AnchorService.cap(d) - a.own(uid));
		double back = Math.min(room, Math.max(0, Config.ENCH_REAPING_SECONDS) * lvl);
		if (back <= 0) {
			return 0;
		}
		a.add(uid, back);
		DataStore.markDirty();
		WorldFx.particleIn(victim.level(), "minecraft:soul", victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 0.25, 0.3, 0.25, 0.03, 8);
		WorldFx.sound(killer, "minecraft:particle.soul_escape", 0.7f, 1.4f);
		return back;
	}

	// ------------------------------------------------------------------ Tethershot

	/** After a hit: an arrow from a Tethershot bow pulls what it struck toward the archer (Elders do not move). */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (blocked || taken <= 0 || !source.is(DamageTypeTags.IS_PROJECTILE) || !(source.getEntity() instanceof ServerPlayer shooter)) {
			return;
		}
		// the weapon an arrow was loosed from
		ItemStack bow = source.getWeaponItem();
		if (bow == null || ModEnchantments.level(shooter.level().registryAccess(), bow, ModEnchantments.TETHERSHOT) <= 0
				|| entity instanceof com.extremedimensions.elder.ElderBoss || entity == shooter) {
			return;
		}
		pull(entity, shooter.position());
	}

	/** Pulls a creature toward a point, a little up, as if hauled on a line. */
	public static void pull(LivingEntity entity, Vec3 to) {
		Vec3 dir = to.subtract(entity.position());
		double len = dir.length();
		if (len < 1.5) {
			return;
		}
		Vec3 v = dir.scale(Math.max(0, Config.ENCH_TETHERSHOT_PULL) / len);
		entity.setDeltaMovement(v.x, Math.max(0.25, v.y + 0.2), v.z);
		entity.needsSync = true;
		WorldFx.particleIn(entity.level(), "minecraft:end_rod", entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 0.2, 0.2, 0.2, 0.02, 6);
	}

	// ------------------------------------------------------------------ Veinseeker

	/** Stone broken in the Seam with a Veinseeker pick: the ore and veins near it ring out, for the miner's eyes. */
	public static int seek(ServerPlayer p, BlockPos pos, BlockState state) {
		if (!SeamService.inSeam(p) || on(p, EquipmentSlot.MAINHAND, ModEnchantments.VEINSEEKER) <= 0) {
			return -1;
		}
		boolean stone = state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(ModBlocks.HUSH_STONE) || state.is(ModBlocks.COBBLED_HUSH_STONE);
		long now = p.level().getGameTime();
		if (!stone || now < SEEK.getOrDefault(p.getUUID(), Long.MIN_VALUE)) {
			return -1;
		}
		SEEK.put(p.getUUID(), now + Math.max(0, Config.ENCH_VEINSEEKER_COOLDOWN) * 20L);
		return ring((ServerLevel) p.level(), p, pos);
	}

	/** Makes the Tetherite ore and Seam Veins round a spot ring out for one player; returns ore * 1000 + veins found. */
	public static int ring(ServerLevel level, ServerPlayer p, BlockPos pos) {
		int r = Math.max(1, Config.ENCH_VEINSEEKER_RANGE);
		int[] found = {0, 0};
		BlockScan.each(level, pos.getX() - r, pos.getY() - r, pos.getZ() - r, pos.getX() + r, pos.getY() + r, pos.getZ() + r,
				st -> st.is(ModBlocks.TETHERITE_ORE) || st.is(ModBlocks.SEAM_VEIN), q -> {
					if (q.distSqr(pos) > (double) r * r) {
						return;
					}
					boolean ore = level.getBlockState(q).is(ModBlocks.TETHERITE_ORE);
					level.sendParticles(p, ore ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, true, false,
							q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, ore ? 10 : 3, 0.35, 0.35, 0.35, 0.0);
					found[ore ? 0 : 1]++;
				});
		if (found[0] > 0) {
			WorldFx.sound(p, "minecraft:block.amethyst_block.resonate", 1.0f, 0.6f);
		} else if (found[1] > 0) {
			WorldFx.sound(p, "minecraft:block.amethyst_cluster.hit", 0.6f, 1.4f);
		}
		return found[0] * 1000 + found[1];
	}

	// ------------------------------------------------------------------ Anchored, Steadfast, Foresight, Soulward

	/** What Anchored leaves of the void's throw (Unmoored): 1 without it. */
	public static double unmoored(Player p) {
		int lvl = Math.max(on(p, EquipmentSlot.CHEST, ModEnchantments.ANCHORED), on(p, EquipmentSlot.LEGS, ModEnchantments.ANCHORED));
		return Math.max(0, 1 - Config.ENCH_ANCHORED_UNMOORED * lvl);
	}

	/** What Steadfast leaves of an Elder's spell: 1 without it. */
	public static double steadfast(Player p) {
		return Math.max(0, 1 - Config.ENCH_STEADFAST * on(p, EquipmentSlot.LEGS, ModEnchantments.STEADFAST));
	}

	/** The cards Foresight adds to a Collapse's offer. */
	public static int foresight(PlayerData d) {
		return d.charm(ModEnchantments.FORESIGHT) > 0 ? Math.max(0, Config.ENCH_FORESIGHT_CARDS) : 0;
	}

	/** True when the Lurker flinches from a Soulward helmet this time, instead of taking. */
	public static boolean soulward(ServerPlayer p) {
		return on(p, EquipmentSlot.HEAD, ModEnchantments.SOULWARD) > 0 && ThreadLocalRandom.current().nextDouble() < Config.ENCH_SOULWARD_CHANCE;
	}

	// ------------------------------------------------------------------ Sated, the Curse of the Starved

	/** How much of the exhaustion a player is caused they take: less with Sated, more for each piece of the Starved. */
	public static float exhaustion(Player p) {
		if (p.isCreative() || p.isSpectator()) {
			return 1f;
		}
		double k = 1 - Config.ENCH_SATED * on(p, EquipmentSlot.HEAD, ModEnchantments.SATED);
		for (EquipmentSlot slot : ARMOR) {
			if (on(p, slot, ModEnchantments.CURSE_OF_THE_STARVED) > 0) {
				k += Config.ENCH_STARVED;
			}
		}
		return (float) Math.max(0, k);
	}

	// ------------------------------------------------------------------ The Curse of Fraying

	/** The wear an item takes after its enchantments had their say: more in the Seam with the Curse of Fraying. */
	public static int fraying(ServerLevel level, ItemStack stack, int wear) {
		if (wear <= 0 || !Dims.SEAM.equals(Dims.of(level))
				|| ModEnchantments.level(level.registryAccess(), stack, ModEnchantments.CURSE_OF_FRAYING) <= 0) {
			return wear;
		}
		double more = wear * Math.max(0, Config.ENCH_FRAYING_WEAR);
		int whole = (int) more;
		return wear + whole + (ThreadLocalRandom.current().nextDouble() < more - whole ? 1 : 0);
	}

	// ------------------------------------------------------------------ Hushed Steps

	/** How much of their visibility a creature has to others: less in Hushed Steps boots. */
	public static double visibility(LivingEntity e) {
		if (!(e instanceof Player p)) {
			return 1;
		}
		return Math.max(0.05, 1 - Config.ENCH_HUSHED_STEPS * on(p, EquipmentSlot.FEET, ModEnchantments.HUSHED_STEPS));
	}

	// ------------------------------------------------------------------ Driftfall and Knitting, ticked

	public static void tick(MinecraftServer s) {
		int knit = Math.max(1, Config.ENCH_KNITTING_SECONDS) * 20;
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (p.isSpectator() || !SeamService.inSeam(p)) {
				continue;
			}
			if ((s.getTickCount() + p.getId()) % 5 == 0) {
				drift(p);
			}
			if ((s.getTickCount() + p.getId()) % knit == 0) {
				knit(p);
			}
		}
	}

	/** Driftfall: a long fall in the Seam is caught with a few seconds of slow falling, now and then. */
	public static boolean drift(ServerPlayer p) {
		if (p.onGround() || p.fallDistance < Config.ENCH_DRIFTFALL_FALL || p.hasEffect(MobEffects.SLOW_FALLING)
				|| on(p, EquipmentSlot.FEET, ModEnchantments.DRIFTFALL) <= 0) {
			return false;
		}
		long now = p.level().getGameTime();
		if (now < DRIFT.getOrDefault(p.getUUID(), Long.MIN_VALUE)) {
			return false;
		}
		DRIFT.put(p.getUUID(), now + Math.max(0, Config.ENCH_DRIFTFALL_COOLDOWN) * 20L);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(1, Config.ENCH_DRIFTFALL_SECONDS) * 20, 0, false, true, true));
		WorldFx.particles(p, "minecraft:end_rod", 0.5, 0.6, 0.03, 14);
		WorldFx.sound(p, "minecraft:item.elytra.flying", 0.4f, 1.6f);
		return true;
	}

	/** Knitting: in the Seam, every piece of worn and held gear with it mends a point. */
	public static int knit(ServerPlayer p) {
		int mended = 0;
		for (EquipmentSlot slot : KNIT) {
			ItemStack stack = p.getItemBySlot(slot);
			if (stack.isDamaged() && ModEnchantments.level(p.level().registryAccess(), stack, ModEnchantments.KNITTING) > 0) {
				stack.setDamageValue(stack.getDamageValue() - 1);
				mended++;
			}
		}
		return mended;
	}

	/** Forgets a player's moments (on leaving). */
	public static void forget(UUID id) {
		SEEK.remove(id);
		DRIFT.remove(id);
	}
}
