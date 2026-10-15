package com.extremedimensions.coop;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/**
 * Fallen, not gone: a player struck down while an ally is near falls instead of dying. Fallen, they cannot be hurt,
 * can only crawl, cannot fight, use anything, use commands or leave their world, and bleed out after
 * {@code coop_fallen_seconds}; an ally crouching beside them for {@code coop_lift_seconds} lifts them up again with a
 * share of their health. Holding sneak, the fallen give in. A player falls at most once every
 * {@code coop_fallen_cooldown_minutes} (struck down again sooner, they die). A totem still saves as it always did,
 * and what kills outright (the void, a collapse, /kill) still kills.
 */
public final class FallenService {
	private FallenService() {}

	/** Blocks from a fallen player within which an ally lifts them. */
	private static final double LIFT_REACH = 2.2;
	/** Ticks of holding sneak before the fallen give in. */
	private static final int GIVE_IN_TICKS = 60;

	private static final Identifier CRAWL = Identifier.fromNamespaceAndPath("extremedimensions", "fallen_crawl");
	private static final Identifier NO_JUMP = Identifier.fromNamespaceAndPath("extremedimensions", "fallen_no_jump");

	private static final class Fallen {
		int left;
		int lift;
		int giveIn;
		DamageSource source;
		boolean ending;
		UUID lifter;
		/** When (server ticks) they were last told they cannot leave, so a portal does not say it every tick. */
		int warned = -1000;
	}

	private static final Map<UUID, Fallen> FALLEN = new HashMap<>();
	/** Whether who is fallen changed this tick (every client is told, to draw them crawling). */
	private static boolean changed;

	public static boolean fallen(ServerPlayer p) {
		return FALLEN.containsKey(p.getUUID());
	}

	public static int count() {
		return FALLEN.size();
	}

	public static void clear() {
		FALLEN.clear();
		changed = false;
	}

	/** Ticks before this player can fall again (0 = they can); a bound Kindred Knot shortens the wait. */
	public static long cooldownLeft(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		if (d.fallenAt < 0) {
			return 0;
		}
		double wait = Math.max(0, Config.COOP_FALLEN_COOLDOWN_MINUTES) * 1200.0;
		if (com.extremedimensions.relic.RelicService.has(d, com.extremedimensions.relic.Relic.KINDRED_KNOT)) {
			wait *= Math.max(0, Config.RELIC_KINDRED_KNOT_COOLDOWN);
		}
		long now = p.level().getServer().overworld().getGameTime();
		return Math.max(0, d.fallenAt + (long) wait - now);
	}

	/** Whole minutes (rounded up) in a span of ticks. */
	private static long minutes(long ticks) {
		return (ticks + 1199) / 1200;
	}

	/** The death check: a player with an ally near falls instead; returns false when they do. */
	public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p)) {
			return true;
		}
		Fallen f = FALLEN.get(p.getUUID());
		PlayerData d = DataStore.get(p.getUUID());
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || d.collapsing) {
			release(p.getUUID(), p);
			return true;
		}
		if (f != null) {
			return f.ending;
		}
		if (Config.COOP_REVIVE <= 0 || p.isCreative() || p.isSpectator() || !Dims.ours(p) || holdsTotem(p) || allyNear(p, Config.COOP_REVIVE_RANGE) == null) {
			return true;
		}
		long wait = cooldownLeft(p);
		if (wait > 0) {
			// They fell not long ago: this time the blow kills
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.too_soon", minutes(wait)));
			return true;
		}
		fall(p, source);
		return false;
	}

	/** Strikes a player down at once, as if a deadly blow had landed beside an ally (for operators and tests). */
	public static void knockDown(ServerPlayer p) {
		if (!FALLEN.containsKey(p.getUUID())) {
			fall(p, ((ServerLevel) p.level()).damageSources().generic());
		}
	}

	/** Whether this command source is a fallen player, who may not use commands (they are told so). */
	public static boolean refuseCommand(net.minecraft.commands.CommandSourceStack src) {
		if (Config.COOP_FALLEN_LOCKED <= 0 || !(src.getEntity() instanceof ServerPlayer p) || !FALLEN.containsKey(p.getUUID())) {
			return false;
		}
		src.sendFailure(Component.translatable("gui.extremedimensions.coop.no_commands"));
		return true;
	}

	/** Whether a fallen player is kept from moving to another world (they are told so, now and then). */
	public static boolean refuseTravel(ServerPlayer p, ServerLevel to) {
		Fallen f = FALLEN.get(p.getUUID());
		if (Config.COOP_FALLEN_LOCKED <= 0 || f == null || f.ending || to == null || to == p.level()) {
			return false;
		}
		int now = p.level().getServer().getTickCount();
		if (now - f.warned > 60) {
			f.warned = now;
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.no_travel"), true);
		}
		return true;
	}

	/** The players fallen now (every client draws them crawling). */
	public static java.util.List<UUID> fallenIds() {
		return new java.util.ArrayList<>(FALLEN.keySet());
	}

	/** Tells one player (joining) or everyone who is fallen. */
	public static void sync(MinecraftServer s, ServerPlayer only) {
		ModPackets.FallenPayload payload = new ModPackets.FallenPayload(fallenIds());
		for (ServerPlayer q : only != null ? java.util.List.of(only) : s.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(q, ModPackets.FallenPayload.TYPE)) {
				ServerPlayNetworking.send(q, payload);
			}
		}
	}

	/** Fallen players cannot be hurt (except by what kills outright, or as they bleed out). */
	public static boolean mayHurt(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer p)) {
			return true;
		}
		Fallen f = FALLEN.get(p.getUUID());
		return f == null || f.ending || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	private static boolean holdsTotem(ServerPlayer p) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (p.getItemInHand(hand).is(Items.TOTEM_OF_UNDYING)) {
				return true;
			}
		}
		return false;
	}

	/** The nearest ally within {@code range} blocks who can help (alive, not fallen, not watching); null if none. */
	static ServerPlayer allyNear(ServerPlayer p, double range) {
		ServerPlayer best = null;
		double bestD = range * range;
		for (ServerPlayer q : ((ServerLevel) p.level()).players()) {
			if (q == p || !q.isAlive() || q.isSpectator() || FALLEN.containsKey(q.getUUID()) || !Allies.allied(p, q)) {
				continue;
			}
			double dd = q.distanceToSqr(p);
			if (dd <= bestD) {
				bestD = dd;
				best = q;
			}
		}
		return best;
	}

	private static void fall(ServerPlayer p, DamageSource source) {
		Fallen f = new Fallen();
		PlayerData d = DataStore.get(p.getUUID());
		double last = Math.max(1, Config.COOP_FALLEN_SECONDS) * 20.0;
		if (com.extremedimensions.relic.RelicService.has(d, com.extremedimensions.relic.Relic.KINDRED_KNOT)) {
			last *= Math.max(0.05, Config.RELIC_KINDRED_KNOT_BLEED);
		}
		f.left = (int) Math.max(20, Math.round(last));
		f.source = source;
		FALLEN.put(p.getUUID(), f);
		changed = true;
		d.fallenAt = p.level().getServer().overworld().getGameTime();
		DataStore.markDirty();
		p.setHealth(1.0F);
		p.stopRiding();
		p.setSprinting(false);
		if (p.isFallFlying()) {
			p.stopFallFlying();
		}
		crawl(p, true);
		weaken(p);
		WorldFx.sound(p, "minecraft:entity.player.hurt", 1.0f, 0.5f);
		WorldFx.sound(p, "minecraft:block.respawn_anchor.deplete", 1.0f, 0.6f);
		WorldFx.particles(p, "minecraft:soul", 1.0, 0.4, 0.05, 20);
		Component msg = Component.translatable("gui.extremedimensions.coop.fell", p.getDisplayName());
		for (ServerPlayer q : ((ServerLevel) p.level()).players()) {
			if (q != p && Allies.allied(p, q) && q.distanceToSqr(p) <= 96 * 96) {
				q.sendSystemMessage(msg);
			}
		}
	}

	/**
	 * Down on the ground: they crawl (every client draws them so, see {@link Crawl}), at {@code coop_fallen_crawl_speed}
	 * of a walk, and cannot jump.
	 */
	private static void crawl(ServerPlayer p, boolean on) {
		AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
		if (speed != null) {
			speed.removeModifier(CRAWL);
		}
		if (jump != null) {
			jump.removeModifier(NO_JUMP);
		}
		if (!on) {
			return;
		}
		if (speed != null) {
			// Crawling already slows a walk to the sneaking speed; this brings it down to the share set
			double sneak = Math.max(0.05, p.getAttributeValue(Attributes.SNEAKING_SPEED));
			double factor = Math.max(0, Math.min(1, Config.COOP_FALLEN_CRAWL_SPEED)) / sneak;
			speed.addTransientModifier(new AttributeModifier(CRAWL, Math.max(-1, Math.min(0, factor - 1)), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (jump != null) {
			jump.addTransientModifier(new AttributeModifier(NO_JUMP, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Ends a fall without a word (lifted, dead, gone or moved on): they stand and walk again. */
	private static void release(UUID id, ServerPlayer p) {
		if (FALLEN.remove(id) != null) {
			changed = true;
		}
		if (p != null) {
			crawl(p, false);
		}
	}

	private static void weaken(ServerPlayer p) {
		WorldFx.effect(p, MobEffects.WEAKNESS, 3, 9);
		WorldFx.effect(p, MobEffects.MINING_FATIGUE, 3, 4);
		WorldFx.effect(p, MobEffects.GLOWING, 3, 0);
	}

	/** Each tick: the fallen bleed out, allies crouching beside them lift them up, holding sneak they give in. */
	public static void tick(MinecraftServer s) {
		if (changed) {
			changed = false;
			sync(s, null);
		}
		if (FALLEN.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Fallen>> it = FALLEN.entrySet().iterator();
		java.util.List<Runnable> later = new java.util.ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Fallen> e = it.next();
			Fallen f = e.getValue();
			ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive() || f.ending) {
				it.remove();
				changed = true;
				if (p != null) {
					crawl(p, false);
				}
				continue;
			}
			f.left--;
			if (p.getHealth() > 1.0F) {
				p.setHealth(1.0F);
			}
			// No running, no gliding: down is down
			p.setSprinting(false);
			if (p.isFallFlying()) {
				p.stopFallFlying();
			}
			if (p.tickCount % 20 == 0) {
				weaken(p);
				WorldFx.particles(p, "extremedimensions:soul_mote", 0.6, 0.6, 0.0, 4);
			}
			// An ally crouching right beside them lifts them up (letting go, the lift slips back)
			ServerPlayer lifter = null;
			for (ServerPlayer q : ((ServerLevel) p.level()).players()) {
				if (q != p && q.isAlive() && !q.isSpectator() && q.isShiftKeyDown() && !FALLEN.containsKey(q.getUUID())
						&& q.distanceToSqr(p) <= LIFT_REACH * LIFT_REACH && Allies.allied(p, q)) {
					lifter = q;
					break;
				}
			}
			int need = (int) Math.max(1, Math.round(Config.COOP_LIFT_SECONDS * 20));
			if (lifter != null) {
				f.lift++;
				f.lifter = lifter.getUUID();
				if (f.lift % 10 == 1) {
					WorldFx.sound(lifter, "minecraft:block.amethyst_block.chime", 0.8f, 0.6f + f.lift / (float) need);
				}
			} else {
				f.lift = Math.max(0, f.lift - 2);
			}
			f.giveIn = p.isShiftKeyDown() ? f.giveIn + 1 : 0;
			if (f.lift >= need && lifter != null) {
				ServerPlayer by = lifter;
				later.add(() -> lift(p, by));
				continue;
			}
			if (f.left <= 0 || f.giveIn >= GIVE_IN_TICKS) {
				later.add(() -> bleedOut(p));
				continue;
			}
			if (p.tickCount % 5 == 0) {
				hud(p, f, lifter, need);
			}
		}
		later.forEach(Runnable::run);
	}

	private static String bar(int done, int need) {
		int n = Math.max(0, Math.min(10, done * 10 / Math.max(1, need)));
		return "▮".repeat(n) + "▯".repeat(10 - n);
	}

	/** The fallen see how long they have (or who is lifting them); allies near see who has fallen and how to help. */
	private static void hud(ServerPlayer p, Fallen f, ServerPlayer lifter, int need) {
		int secs = (f.left + 19) / 20;
		if (lifter != null) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.being_lifted", lifter.getDisplayName(), bar(f.lift, need)), true);
			lifter.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.lifting", p.getDisplayName(), bar(f.lift, need)), true);
		} else {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.fallen", secs), true);
		}
		for (ServerPlayer q : ((ServerLevel) p.level()).players()) {
			if (q != p && q != lifter && !FALLEN.containsKey(q.getUUID()) && Allies.allied(p, q) && q.distanceToSqr(p) <= 48 * 48) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.ally_fallen", p.getDisplayName(), secs), true);
			}
		}
	}

	/** An ally lifts a fallen player up: back on their feet with a share of their health, a moment's shelter. */
	public static void lift(ServerPlayer p, ServerPlayer by) {
		release(p.getUUID(), p);
		p.setHealth((float) Math.max(1.0, p.getMaxHealth() * Math.max(0.05, Math.min(1.0, Config.COOP_LIFT_HEALTH))));
		p.removeEffect(MobEffects.WEAKNESS);
		p.removeEffect(MobEffects.MINING_FATIGUE);
		p.removeEffect(MobEffects.GLOWING);
		WorldFx.effect(p, MobEffects.RESISTANCE, 3, 1);
		WorldFx.sound(p, "minecraft:block.beacon.power_select", 1.0f, 1.3f);
		WorldFx.sound(p, "minecraft:entity.player.levelup", 0.6f, 0.7f);
		WorldFx.particles(p, "minecraft:totem_of_undying", 1.0, 0.8, 0.3, 30);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 1.0, 0.0, 16);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.lifted", by.getDisplayName()));
		long wait = cooldownLeft(p);
		if (wait > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.cooldown", minutes(wait)));
		}
		by.sendSystemMessage(Component.translatable("gui.extremedimensions.coop.lifted_other", p.getDisplayName()));
		DataStore.get(by.getUUID()).liftsGiven++;
		DataStore.get(p.getUUID()).liftsTaken++;
		com.extremedimensions.progression.Advancements.grant(by, com.extremedimensions.progression.Advancements.LIFTED);
		DataStore.markDirty();
	}

	/** Out of time (or given in): they die of what struck them down. */
	public static void bleedOut(ServerPlayer p) {
		Fallen f = FALLEN.get(p.getUUID());
		if (f == null || f.ending) {
			return;
		}
		f.ending = true;
		ServerLevel level = (ServerLevel) p.level();
		DamageSource source = f.source != null ? f.source : level.damageSources().generic();
		boolean totem = holdsTotem(p);
		crawl(p, false);
		p.hurtServer(level, source, Float.MAX_VALUE);
		if (p.isAlive() && !totem) {
			// The blow could not land (a moment's shelter after joining, say): they die of it all the same
			p.setHealth(0.0F);
			p.die(source);
		}
		release(p.getUUID(), p);
	}

	/** A fallen player leaving the game bleeds out at once. */
	public static void onLeave(ServerPlayer p) {
		if (FALLEN.containsKey(p.getUUID())) {
			bleedOut(p);
		}
	}

	/** Forgets a player's fall (respawned, or moved to another world): they stand again, nothing more. */
	public static void forget(ServerPlayer p) {
		release(p.getUUID(), p);
	}
}
