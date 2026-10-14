package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/**
 * The Matriarch's fight. She rises out of the ground by the hoard she guards and drags herself after her prey,
 * rooted and slow. Close, she sweeps her talons across everything in front of her or wails; far, she shakes her
 * lanterns down onto those she hunts, and twice she calls her brood. Below a third of her health she is faster and
 * her blows come sooner. Her prey wandering far from the hoard ends the fight; slain, she leaves relics.
 */
public final class MatriarchService {
	private MatriarchService() {}

	private static final int RISE_TICKS = 60;

	private static final class Fight {
		UUID target;
		String hoard;
		double hx, hz;
		ServerBossEvent bar;
		ServerLevel level;
		int ticks, nextMove = 90, action, actionAt, brood;
		double sunk;
		boolean enraged;
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return FIGHTS.containsKey(e.getUUID());
	}

	public static void clear() {
		for (Fight f : FIGHTS.values()) {
			f.bar.removeAllPlayers();
		}
		FIGHTS.clear();
	}

	public static boolean hunting(ServerPlayer p) {
		for (Fight f : FIGHTS.values()) {
			if (p.getUUID().equals(f.target)) {
				return true;
			}
		}
		return false;
	}

	/** She rises near the hoard and turns on the player who woke it. */
	public static boolean summon(ServerPlayer p, String hoard, BlockPos home) {
		if (hunting(p)) {
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		BlockPos spot = ElderFx.near(level, p, 16, 22);
		Matriarch m = ModEntities.MATRIARCH.spawn(level, spot, EntitySpawnReason.EVENT);
		if (m == null) {
			return false;
		}
		m.setPersistenceRequired();
		m.setTarget(p);
		Fight f = new Fight();
		f.target = p.getUUID();
		f.hoard = hoard;
		f.hx = home.getX() + 0.5;
		f.hz = home.getZ() + 0.5;
		f.level = level;
		f.sunk = 15;
		f.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.matriarch"), BossEvent.BossBarColor.RED,
				BossEvent.BossBarOverlay.NOTCHED_12);
		f.bar.addPlayer(p);
		FIGHTS.put(m.getUUID(), f);
		// She starts below the ground and rises
		m.setPos(m.getX(), m.getY() - f.sunk, m.getZ());
		m.setYRot(ElderFx.yawTo(m, p.getX(), p.getZ()));
		m.setAction(Matriarch.RISE);
		ElderFx.roar(m, 5f, 0.45f);
		WorldFx.soundIn(ElderFx.dim(m), "minecraft:block.rooted_dirt.break", "hostile", m.getX(), m.getY() + f.sunk, m.getZ(), 4f, 0.4f);
		for (ServerPlayer q : level.players()) {
			if (q.distanceToSqr(m) < 48 * 48) {
				WorldFx.effect(q, MobEffects.DARKNESS, 4, 0);
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.matriarch_comes"));
			}
		}
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.MATRIARCH);
		ElderFx.music(m, ElderFx.MUSIC_MATRIARCH);
		return true;
	}

	public static void tick(MinecraftServer s) {
		if (FIGHTS.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		Iterator<Map.Entry<UUID, Fight>> it = FIGHTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Fight> e = it.next();
			Fight f = e.getValue();
			Entity ent = f.level.getEntity(e.getKey());
			if (!(ent instanceof Matriarch m) || !m.isAlive()) {
				f.bar.removeAllPlayers();
				it.remove();
				continue;
			}
			f.ticks++;
			ServerPlayer p = s.getPlayerList().getPlayer(f.target);
			double hx = f.hx - (p == null ? 0 : p.getX()), hz = f.hz - (p == null ? 0 : p.getZ());
			if (p == null || !p.isAlive() || p.level() != m.level() || hx * hx + hz * hz > 80 * 80) {
				later.add(() -> fade(m));
				continue;
			}
			drive(m, f, p, later);
		}
		later.forEach(Runnable::run);
	}

	private static void drive(Matriarch m, Fight f, ServerPlayer p, List<Runnable> later) {
		String dim = ElderFx.dim(m);
		f.bar.setProgress(m.getHealth() / m.getMaxHealth());
		if (f.ticks % 20 == 0) {
			m.setTarget(p);
			for (ServerPlayer q : f.level.players()) {
				if (q.distanceToSqr(m) < 56 * 56) {
					f.bar.addPlayer(q);
				}
			}
		}
		if (f.ticks % 40 == 1) {
			ElderFx.music(m, ElderFx.MUSIC_MATRIARCH);
		}
		if (f.ticks % 4 == 0) {
			WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", m.getX(), m.getY() + 11, m.getZ(), 7, 4, 4, 0, 2);
		}
		// Rising out of the ground
		if (f.ticks <= RISE_TICKS) {
			double up = f.sunk / RISE_TICKS;
			m.setPos(m.getX(), m.getY() + up, m.getZ());
			WorldFx.particleIn(dim, "minecraft:large_smoke", m.getX(), m.getY() + f.sunk - f.ticks * up, m.getZ(), 3, 0.4, 3, 0.02, 6);
			if (f.ticks == RISE_TICKS) {
				m.setAction(ElderBoss.IDLE);
				ElderFx.roar(m, 5f, 0.5f);
			}
			return;
		}
		float frac = m.getHealth() / m.getMaxHealth();
		if ((f.brood == 0 && frac <= 0.7f) || (f.brood == 1 && frac <= 0.35f)) {
			f.brood++;
			later.add(() -> {
				HollowService.brood((ServerLevel) m.level(), m.blockPosition(), f.hoard, Math.max(0, Config.MATRIARCH_BROOD));
				ElderFx.roar(m, 4f, 0.6f);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.matriarch_brood"), true);
			});
		}
		if (!f.enraged && frac <= 0.35f) {
			f.enraged = true;
			ElderFx.roar(m, 5f, 0.4f);
		}
		double dist = Math.sqrt(m.distanceToSqr(p.getX(), m.getY(), p.getZ()));
		float yaw = ElderFx.yawTo(m, p.getX(), p.getZ());
		if (f.action == ElderBoss.IDLE) {
			ElderFx.turn(m, yaw, 4f);
			if (dist > 9) {
				ElderFx.glide(m, p.getX(), p.getZ(), f.enraged ? 0.16 : 0.11, 0);
			} else {
				ElderFx.glide(m, m.getX(), m.getZ(), 0, 0);
			}
			if (--f.nextMove <= 0) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				int roll = rnd.nextInt(6);
				f.action = dist <= 12 ? (roll < 3 ? Matriarch.SWEEP : roll < 5 ? Matriarch.ROOTS : Matriarch.WAIL)
						: (roll < 4 ? Matriarch.LANTERNS : Matriarch.ROOTS);
				f.actionAt = f.ticks;
				m.setAction(f.action);
			}
			return;
		}
		ElderFx.turn(m, yaw, 1.5f);
		ElderFx.glide(m, m.getX(), m.getZ(), 0, 0);
		int t = f.ticks - f.actionAt;
		switch (f.action) {
			case Matriarch.SWEEP -> {
				if (t == 18) {
					sweep(m);
				}
				if (t >= 34) {
					end(m, f);
				}
			}
			case Matriarch.LANTERNS -> {
				if (t == 10) {
					lanterns(m, p);
				}
				if (t >= 32) {
					end(m, f);
				}
			}
			case Matriarch.ROOTS -> {
				if (t == 20) {
					for (ServerPlayer q : f.level.players()) {
						if (!q.isSpectator() && q.distanceToSqr(m) < 26 * 26) {
							ElderStrikes.strike(m, q.getX(), q.getY(), q.getZ(), 2.6, 6f, 16, ElderStrikes.ROOTS);
						}
					}
					WorldFx.soundIn(dim, "minecraft:block.rooted_dirt.break", "hostile", m.getX(), m.getY(), m.getZ(), 4f, 0.4f);
				}
				if (t >= 42) {
					end(m, f);
				}
			}
			default -> {
				if (t == 12) {
					wail(m);
				}
				if (t >= 40) {
					end(m, f);
				}
			}
		}
	}

	private static void end(Matriarch m, Fight f) {
		f.action = ElderBoss.IDLE;
		m.setAction(ElderBoss.IDLE);
		f.nextMove = (f.enraged ? 40 : 70) + ThreadLocalRandom.current().nextInt(40);
	}

	/** Her talons across everything in front of her. */
	private static void sweep(Matriarch m) {
		String dim = ElderFx.dim(m);
		double yaw = Math.toRadians(m.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		for (int i = 0; i < 9; i++) {
			double a = -1.1 + 2.2 * i / 8;
			double cx = fx * Math.cos(a) - fz * Math.sin(a), cz = fz * Math.cos(a) + fx * Math.sin(a);
			WorldFx.particleIn(dim, "minecraft:sweep_attack", m.getX() + cx * 8, m.getY() + 3, m.getZ() + cz * 8, 0, 0, 0, 0, 1);
		}
		WorldFx.soundIn(dim, "minecraft:entity.player.attack.sweep", "hostile", m.getX(), m.getY() + 4, m.getZ(), 4f, 0.4f);
		for (ServerPlayer q : ((ServerLevel) m.level()).players()) {
			double dx = q.getX() - m.getX(), dz = q.getZ() - m.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (q.isSpectator() || q.isCreative() || d > 13 || (d > 2 && (dx * fx + dz * fz) / d < 0.15) || q.getY() > m.getY() + 10) {
				continue;
			}
			q.hurtServer((ServerLevel) m.level(), m.level().damageSources().mobAttack(m), 15f);
			ElderFx.shove(m, q, 1.6, 0.5);
		}
	}

	/** Her lanterns shaken down onto those she hunts (and a few beside them). */
	private static void lanterns(Matriarch m, ServerPlayer p) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		WorldFx.soundIn(ElderFx.dim(m), "minecraft:block.chain.break", "hostile", m.getX(), m.getY() + 10, m.getZ(), 4f, 0.5f);
		for (ServerPlayer q : ((ServerLevel) m.level()).players()) {
			if (!q.isSpectator() && q.distanceToSqr(m) < 40 * 40) {
				ElderStrikes.strike(m, q.getX(), q.getY(), q.getZ(), 3.2, 9f, 30, ElderStrikes.LANTERN);
			}
		}
		for (int i = 0; i < 3; i++) {
			double x = p.getX() + rnd.nextDouble(-7, 7), z = p.getZ() + rnd.nextDouble(-7, 7);
			double y = ElderFx.surface((ServerLevel) m.level(), x, p.getY() + 4, z, 4, 12);
			ElderStrikes.strike(m, x, Double.isNaN(y) ? p.getY() : y, z, 3.2, 9f, 34 + i * 4, ElderStrikes.LANTERN);
		}
	}

	/** Her wail: darkness, weakness and slowness for everyone close. */
	private static void wail(Matriarch m) {
		String dim = ElderFx.dim(m);
		WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", m.getX(), m.getY() + 10, m.getZ(), 5f, 0.4f);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", m.getX(), m.getY() + 10, m.getZ(), 5, 3, 5, 0.0, 90);
		for (ServerPlayer q : ((ServerLevel) m.level()).players()) {
			if (q.distanceToSqr(m) < 20 * 20) {
				WorldFx.effect(q, MobEffects.DARKNESS, 5, 0);
				WorldFx.effect(q, MobEffects.WEAKNESS, 5, 0);
				WorldFx.effect(q, MobEffects.SLOWNESS, 3, 1);
			}
		}
	}

	/** Slain: relics, fragments and pages, and the hoard she guarded is free. */
	public static void onDefeat(Matriarch m, Entity killer) {
		Fight f = FIGHTS.remove(m.getUUID());
		ElderStrikes.forget(m);
		ElderFx.silence(m);
		if (f == null) {
			return;
		}
		f.bar.removeAllPlayers();
		HollowService.markSlain(f.hoard);
		ServerLevel level = (ServerLevel) m.level();
		ElderFx.burst(m, "extremedimensions:lurker_wisp", 200);
		ElderFx.burst(m, "minecraft:soul_fire_flame", 120);
		m.spawnAtLocation(level, new ItemStack(ModItems.TETHER_FRAGMENT, 4));
		m.spawnAtLocation(level, new ItemStack(ModItems.WANDERERS_PAGE, 2));
		for (int i = 0; i < Math.max(0, Config.MATRIARCH_RELICS); i++) {
			m.spawnAtLocation(level, RelicService.random());
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(m) < 64 * 64) {
				PlayerData d = DataStore.get(p.getUUID());
				d.matriarchSlain = true;
				Advancements.grant(p, Advancements.MATRIARCH_SLAIN);
				Journal.unlock(p, d, JournalEntry.MATRIARCH);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.matriarch_falls"));
			}
		}
		DataStore.markDirty();
	}

	/** Gone: she sinks back into the ground, whole. */
	public static void fade(Matriarch m) {
		Fight f = FIGHTS.remove(m.getUUID());
		if (f != null) {
			f.bar.removeAllPlayers();
		}
		ElderStrikes.forget(m);
		ElderFx.silence(m);
		ElderFx.burst(m, "minecraft:large_smoke", 120);
		m.discard();
	}
}
