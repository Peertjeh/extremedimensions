package com.extremedimensions.penalty;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What the Extreme curses do. Their cards never say it plainly; this is the truth behind the hints.
 *
 * <ul>
 * <li>Dwindling: every stack in the player's own inventory holds half as much per stack of the curse.</li>
 * <li>Withered Grasp: blocks can only be reached, placed and mined from one block closer per stack (an attribute).</li>
 * <li>Restless Gaze: Endermen turn on the player once they are within 15 degrees of the view per stack.</li>
 * <li>The Grudge: in the Nether, Zombified Piglins that see the player attack unprovoked.</li>
 * <li>Stormcalled: under open sky in a thunderstorm, lightning finds the player every 30 to 90 seconds.</li>
 * <li>Feral Scent: no animal can be tamed.</li>
 * <li>Broken Oath: a Totem of Undying fails a share of the time (25%).</li>
 * <li>Unhealing: no natural regeneration from a full stomach (a mixin on the food tick).</li>
 * <li>Echoing Steps: monsters notice the player from half again as far.</li>
 * </ul>
 */
public final class ExtremeCurses {
	private ExtremeCurses() {}

	private static final Identifier ENDERMAN = Identifier.fromNamespaceAndPath("minecraft", "enderman");
	private static final Identifier ZOMBIFIED_PIGLIN = Identifier.fromNamespaceAndPath("minecraft", "zombified_piglin");
	private static final Identifier LIGHTNING = Identifier.fromNamespaceAndPath("minecraft", "lightning_bolt");

	/** Every tick, for a player carrying any Extreme curse. */
	static void tick(ServerPlayer p, PlayerData d) {
		if (p.isCreative() || p.isSpectator()) {
			return;
		}
		if (d.tickCounter % 10 == 0) {
			Dwindling.enforce(p);
			if (d.stacks(Penalty.RESTLESS_GAZE) > 0) {
				gaze(p, d.stacks(Penalty.RESTLESS_GAZE));
			}
		}
		if (d.tickCounter % 20 == 0) {
			if (d.stacks(Penalty.THE_GRUDGE) > 0 && Dims.NETHER.equals(Dims.of(p))) {
				grudge(p);
			}
			if (d.stacks(Penalty.ECHOING_STEPS) > 0) {
				echo(p, Penalty.ECHOING_STEPS.frac() * d.stacks(Penalty.ECHOING_STEPS));
			}
			if (d.stacks(Penalty.STORMCALLED) > 0) {
				storm(p, d);
			}
		}
	}

	private static boolean is(Entity e, Identifier type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(type);
	}

	/** Restless Gaze: an Enderman within {@code 15 * stacks} degrees of where the player looks takes it as a stare. */
	public static void gaze(ServerPlayer p, int stacks) {
		if (p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
			return;
		}
		double cone = Math.cos(Math.toRadians(Math.min(89, Penalty.RESTLESS_GAZE.value * stacks)));
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1.0f).normalize();
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), e -> is(e, ENDERMAN) && e.getTarget() == null)) {
			Vec3 to = new Vec3(m.getX() - eye.x, m.getEyeY() - eye.y, m.getZ() - eye.z);
			double dist = to.length();
			if (dist < 1 || dist > 64 || look.dot(to.scale(1 / dist)) < cone || !p.hasLineOfSight(m)) {
				continue;
			}
			m.setTarget(p);
		}
	}

	/** The Grudge: Zombified Piglins that can see the player turn on them, as if one had been struck. */
	public static void grudge(ServerPlayer p) {
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16, 8, 16), e -> is(e, ZOMBIFIED_PIGLIN))) {
			if (m.getTarget() == p || !m.hasLineOfSight(p)) {
				continue;
			}
			m.setLastHurtByMob(p);
			m.setTarget(p);
			if (m instanceof NeutralMob angry) {
				angry.startPersistentAngerTimer();
			}
		}
	}

	/** Echoing Steps: a monster notices the player from {@code more} (0.5 = half) again as far as it normally would. */
	public static void echo(ServerPlayer p, double more) {
		double reach = 48 * (1 + more);
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(Math.min(96, reach)),
				e -> e instanceof Enemy && !(e instanceof NeutralMob) && e.getTarget() == null)) {
			double range = m.getAttributeValue(Attributes.FOLLOW_RANGE);
			double dist = m.distanceTo(p);
			if (dist > range && dist <= range * (1 + more) && m.hasLineOfSight(p)) {
				m.setTarget(p);
			}
		}
	}

	/** Stormcalled: while a thunderstorm finds the player under open sky, lightning comes every 30 to 90 seconds. */
	public static void storm(ServerPlayer p, PlayerData d) {
		ServerLevel level = (ServerLevel) p.level();
		if (!level.isThundering() || !level.canSeeSky(p.blockPosition())) {
			return;
		}
		if (d.stormIn < 0) {
			d.stormIn = nextStrike();
		}
		if (--d.stormIn <= 0) {
			strike(p);
			d.stormIn = nextStrike();
		}
	}

	private static int nextStrike() {
		int lo = Math.max(1, Config.STORM_STRIKE_MIN_SECONDS), hi = Math.max(lo, Config.STORM_STRIKE_MAX_SECONDS);
		return lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
	}

	/** A bolt right on the player. */
	public static void strike(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Entity bolt = BuiltInRegistries.ENTITY_TYPE.getValue(LIGHTNING).create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(p.getX(), p.getY(), p.getZ());
			level.addFreshEntity(bolt);
		}
	}

	/** Feral Scent: an untamed animal will not be fed, tamed or ridden by the player. Called on every use of an entity. */
	public static InteractionResult refuseTaming(Player player, Entity entity) {
		if (!(player instanceof ServerPlayer p) || p.isCreative()) {
			return InteractionResult.PASS;
		}
		boolean wild = entity instanceof TamableAnimal t && !t.isTame() || entity instanceof AbstractHorse h && !h.isTamed();
		if (!wild || DataStore.get(p.getUUID()).stacks(Penalty.FERAL_SCENT) <= 0) {
			return InteractionResult.PASS;
		}
		WorldFx.particleIn(entity.level(), "minecraft:smoke", entity.getX(), entity.getY() + entity.getBbHeight() * 0.7, entity.getZ(),
				0.3, 0.2, 0.3, 0.01, 8);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.extreme.feral"), true);
		return InteractionResult.FAIL;
	}

	/**
	 * Broken Oath: just before a totem would save the player, it may crumble instead (the share is the curse's value).
	 * Fabric calls this before the totem is tried; returning true lets death (and any other totem) take its course.
	 */
	public static boolean oath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p) || p.isCreative()) {
			return true;
		}
		int stacks = DataStore.get(p.getUUID()).stacks(Penalty.BROKEN_OATH);
		if (stacks <= 0 || ThreadLocalRandom.current().nextDouble() >= Math.min(1, Penalty.BROKEN_OATH.frac() * stacks)) {
			return true;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = p.getItemInHand(hand);
			if (held.is(Items.TOTEM_OF_UNDYING)) {
				held.shrink(1);
				WorldFx.particles(p, "minecraft:smoke", 1.0, 0.4, 0.02, 30);
				WorldFx.sound(p, "minecraft:entity.item.break", 1.0f, 0.6f);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.extreme.oath"), true);
				break;
			}
		}
		return true;
	}

	/** Unhealing: the food tick may not heal this player. */
	public static boolean mayRegenerate(ServerPlayer p) {
		PlayerData d = DataStore.peek(p.getUUID());
		return d == null || d.stacks(Penalty.UNHEALING) <= 0 || p.isCreative();
	}

	/** True if any Extreme curse is carried (skips the per-tick work for everyone else). */
	static boolean any(PlayerData d) {
		for (Penalty pen : Penalty.values()) {
			if (pen.extreme && d.stacks(pen) > 0) {
				return true;
			}
		}
		return false;
	}
}
