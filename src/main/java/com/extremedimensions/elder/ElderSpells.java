package com.extremedimensions.elder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModEffects;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What an Elder lays on those who come against it, beyond its blows. The Matriarch's hex slows everyone in her reach
 * and sours milk; the Ferryman's weight keeps everyone in his reach from jumping as high. Each shows as an effect for
 * as long as the player stays in reach of a living Elder. And no Elder lets anyone glide near it for long: it pulls
 * the flyer out of the air (the Matriarch with roots, the Ferryman with a chain, the Weaver with a thread) and binds
 * their wings for a while, rockets and all.
 */
public final class ElderSpells {
	private ElderSpells() {}

	private static final Identifier SLOW = ExtremeDimensions.id("matriarch_hex");
	private static final Identifier HEAVY = ExtremeDimensions.id("styx_weight");
	/** How long one laying of a spell lasts; it is laid again while the player stays in reach. */
	private static final int SPELL_TICKS = 40;
	/** Ticks each player has glided near an Elder without a break. */
	private static final Map<UUID, Integer> GLIDING = new HashMap<>();

	/** Every tick of a fight (not while it forms): its spell on everyone in its reach, and flyers pulled down. */
	static void tick(ElderBoss b, ElderFight f) {
		if (f.ticks % 10 == 0) {
			Holder<MobEffect> spell = b instanceof Matriarch && Config.MATRIARCH_HEX_SLOW > 0 ? ModEffects.HEX
					: b instanceof Ferryman && Config.FERRYMAN_WEIGHT_JUMP > 0 ? ModEffects.WEIGHT : null;
			if (spell != null) {
				for (ServerPlayer q : ElderFx.targets(b, Math.max(16, Config.ELDER_SPELL_RANGE))) {
					q.addEffect(new MobEffectInstance(spell, SPELL_TICKS, 0, true, false, true));
				}
			}
		}
		if (Config.ELDER_SNARE_SECONDS <= 0) {
			return;
		}
		int needed = Math.max(1, (int) Math.round(Config.ELDER_SNARE_SECONDS * 20));
		for (ServerPlayer q : ElderFx.targets(b, ElderPower.reach(64))) {
			if (!q.isFallFlying()) {
				GLIDING.remove(q.getUUID());
				continue;
			}
			int t = GLIDING.merge(q.getUUID(), 1, Integer::sum);
			if (t >= needed) {
				GLIDING.remove(q.getUUID());
				snare(b, q);
			}
		}
	}

	/** It pulls a flyer out of the air: a hard drag to the ground, a blow, and their wings bound (public for tests). */
	public static void snare(ElderBoss b, ServerPlayer q) {
		ServerLevel level = (ServerLevel) b.level();
		int who = ElderFx.index(b);
		float yaw = ElderFx.yawTo(b, q.getX(), q.getZ());
		b.setYRot(yaw);
		b.setYHeadRot(yaw);
		double fromY = b.getY() + b.getBbHeight() * 0.6;
		int kind = who == 0 ? AttackFx.LINE_PATH : who == 1 ? AttackFx.LINE_CHAIN : AttackFx.LINE_THREAD_TAUT;
		AttackFx.line(level, who, kind, b.getX(), fromY, b.getZ(), q.getX(), q.getY() + 1, q.getZ(), 14);
		ElderTells.snare(b, q);
		q.stopFallFlying();
		double dx = b.getX() - q.getX(), dz = b.getZ() - q.getZ();
		double d = Math.max(1, Math.sqrt(dx * dx + dz * dz));
		q.setDeltaMovement(dx / d * 0.4, -2.4, dz / d * 0.4);
		q.needsSync = true;
		if (Config.ELDER_SNARE_DAMAGE > 0) {
			q.hurtServer(level, level.damageSources().mobAttack(b), (float) Config.ELDER_SNARE_DAMAGE);
		}
		if (Config.ELDER_BIND_SECONDS > 0) {
			q.addEffect(new MobEffectInstance(ModEffects.BOUND_WINGS, Math.max(1, (int) Math.round(Config.ELDER_BIND_SECONDS * 20 * com.extremedimensions.binding.Charms.steadfast(q))), 0, false, false, true));
		}
		WorldFx.particleIn(level, who == 0 ? "minecraft:spore_blossom_air" : who == 1 ? "minecraft:soul" : "minecraft:end_rod",
				q.getX(), q.getY() + 1, q.getZ(), 0.4, 0.6, 0.4, 0.05, 16);
	}

	/**
	 * Every tick: the strength of the spells follows the settings, held as modifiers while the effect lasts and taken
	 * off when it goes; bound wings fold.
	 */
	public static void sweep(MinecraftServer s) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			// Steadfast leggings take some of the hold off
			double steady = com.extremedimensions.binding.Charms.steadfast(p);
			hold(p, Attributes.MOVEMENT_SPEED, SLOW, p.hasEffect(ModEffects.HEX) ? -Math.max(0, Math.min(0.9, Config.MATRIARCH_HEX_SLOW)) * steady : 0);
			hold(p, Attributes.JUMP_STRENGTH, HEAVY, p.hasEffect(ModEffects.WEIGHT) ? -Math.max(0, Math.min(0.9, Config.FERRYMAN_WEIGHT_JUMP)) * steady : 0);
			if (p.isFallFlying() && p.hasEffect(ModEffects.BOUND_WINGS)) {
				p.stopFallFlying();
			}
		}
	}

	private static void hold(ServerPlayer p, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance a = p.getAttribute(attribute);
		if (a == null) {
			return;
		}
		AttributeModifier now = a.getModifier(id);
		if (amount == 0) {
			if (now != null) {
				a.removeModifier(id);
			}
			return;
		}
		if (now == null || Math.abs(now.amount() - amount) > 1e-6) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Whether bound wings keep this one from gliding (both sides: the client does not even try). */
	public static boolean grounded(LivingEntity e) {
		return e.hasEffect(ModEffects.BOUND_WINGS);
	}

	/**
	 * What the spells forbid when an item is used (on both sides, so the client does not start what the server
	 * refuses): milk under the Matriarch's hex, rockets with bound wings. PASS when nothing is forbidden.
	 */
	public static InteractionResult refuse(Player player, ItemStack held) {
		boolean milk = held.is(Items.MILK_BUCKET) && Config.MATRIARCH_HEX_MILK > 0 && player.hasEffect(ModEffects.HEX);
		boolean rocket = held.is(Items.FIREWORK_ROCKET) && player.hasEffect(ModEffects.BOUND_WINGS);
		if (!milk && !rocket) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			sp.sendSystemMessage(Component.translatable(milk ? "gui.extremedimensions.elder.milk_soured" : "gui.extremedimensions.elder.wings_bound"), true);
		}
		return InteractionResult.FAIL;
	}

	public static void forget(UUID id) {
		GLIDING.remove(id);
	}

	public static void clear() {
		GLIDING.clear();
	}
}
