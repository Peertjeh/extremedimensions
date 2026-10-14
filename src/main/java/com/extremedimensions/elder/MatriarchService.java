package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The Matriarch's fight. She rises out of the ground by the hoard she guards and drags herself after her prey, each
 * step shaking the ground. Close, she sweeps her talons across everything in front of her, wails, or drives both
 * hands into the ground to raise rings of thorns; far, she hurls herself at her prey through the ground in her way,
 * throws her lanterns as burning bombs, shakes lanterns down onto them or calls roots up beneath them. Twice she calls
 * her brood; below a third of her health she is faster. At half her health comes her ultimate, the Verdant Requiem:
 * ten seconds of roots bursting from the ground, her chest opening in light, and then the bloom that curses everyone
 * for a hundred and fifty blocks. Her prey wandering far from the hoard ends the fight; slain, she leaves relics.
 */
public final class MatriarchService {
	private MatriarchService() {}

	private static final int RISE_TICKS = 60;
	private static final String BOSS = "boss.extremedimensions.matriarch";
	private static final String ULT = "gui.extremedimensions.elder.ultimate.matriarch";

	private static final class Fight extends ElderFight {
		String hoard;
		double hx, hz;
		int brood;
		double sunk;
		double dirX, dirZ;
		final Set<UUID> struck = new HashSet<>();
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
		f.makeBar(BOSS, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_12, p);
		FIGHTS.put(m.getUUID(), f);
		// She starts below the ground and rises
		m.setPos(m.getX(), m.getY() - f.sunk, m.getZ());
		float yaw = ElderFx.yawTo(m, p.getX(), p.getZ());
		m.setYRot(yaw);
		m.setYBodyRot(yaw);
		m.setYHeadRot(yaw);
		f.start(m, Matriarch.RISE);
		ElderFx.roar(m, 5f, 0.45f);
		WorldFx.soundIn(ElderFx.dim(m), "minecraft:block.rooted_dirt.break", "hostile", m.getX(), m.getY() + f.sunk, m.getZ(), 4f, 0.4f);
		for (ServerPlayer q : level.players()) {
			if (q.distanceToSqr(m) < 48 * 48) {
				WorldFx.effect(q, MobEffects.DARKNESS, 4, 0);
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.matriarch_comes"));
			}
		}
		ElderFx.cinema(m, 96, ModPackets.BossFxPayload.INTRO, 0, 0, "");
		ElderFx.shake(m, m.getX(), m.getZ(), 48, 0.7f, RISE_TICKS);
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
		f.keepBar(m);
		if (f.ticks % 20 == 0) {
			m.setTarget(p);
		}
		if (f.ticks % 20 == 1) {
			ElderFx.music(m, ElderFx.MUSIC_MATRIARCH);
		}
		if (f.ticks % 3 == 0) {
			WorldFx.particleIn(m.level(), "extremedimensions:lurker_wisp", m.getX(), m.getY() + 11, m.getZ(), 7, 4, 4, 0, 2);
			ElderFx.ambience(m);
		}
		// Rising out of the ground
		if (f.action == Matriarch.RISE) {
			m.motion.heave(m, f.sunk / RISE_TICKS);
			double ground = m.getY() + f.sunk - f.ticks * f.sunk / RISE_TICKS;
			WorldFx.particleIn(m.level(), "minecraft:large_smoke", m.getX(), ground, m.getZ(), 3, 0.4, 3, 0.02, 6);
			if (f.ticks % 6 == 0) {
				ElderTerrain.furrow(f.level, m.getX(), ground + 1, m.getZ(), 2.5, 0, 2, 3);
			}
			if (f.ticks >= RISE_TICKS) {
				f.end(m, 40);
				ElderFx.roar(m, 5f, 0.5f);
				ElderTerrain.mark(f.level, m.getX(), m.getY(), m.getZ(), 7, 0.6, ElderTerrain.Mark.ROOT);
			}
			return;
		}
		float frac = m.getHealth() / m.getMaxHealth();
		if ((f.brood == 0 && frac <= 0.7f) || (f.brood == 1 && frac <= 0.35f)) {
			f.brood++;
			later.add(() -> {
				HollowService.brood((ServerLevel) m.level(), m.blockPosition(), f.hoard, Math.max(0, Config.MATRIARCH_BROOD));
				ElderFx.roar(m, 5f, 0.55f);
				ElderFx.shake(m, m.getX(), m.getZ(), 48, 0.9f, 25);
				ElderStrikes.wave(m, m.getX(), m.getY(), m.getZ(), 0.8, 18, 4f, "extremedimensions:lurker_wisp", null);
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
			if (ElderUltimate.due(m, f.ultimateSpent)) {
				f.beginUltimate(m, ULT);
				ElderFx.roar(m, 6f, 0.35f);
				WorldFx.soundIn(dim, "minecraft:entity.warden.emerge", "hostile", m.getX(), m.getY() + 4, m.getZ(), 5f, 0.5f);
				return;
			}
			m.motion.turn(m, yaw, 3.5f);
			if (dist > 9) {
				m.motion.toward(m, p.getX(), p.getZ(), ElderFight.speed(f.enraged ? 0.14 : 0.11), 0.012, 0);
			} else {
				m.motion.brake(m, 0.02, 0);
			}
			f.walk(m, 3.4, 2.5f);
			if (f.ready()) {
				int a = dist <= 13 ? f.pick(Matriarch.SWEEP, 3, Matriarch.CATHEDRAL, 3, Matriarch.ROOTS, 2, Matriarch.WAIL, 1)
						: dist <= 30 ? f.pick(Matriarch.RUSH, 3, Matriarch.BARRAGE, 3, Matriarch.ROOTS, 2, Matriarch.LANTERNS, 2)
						: f.pick(Matriarch.RUSH, 4, Matriarch.LANTERNS, 2, Matriarch.BARRAGE, 2);
				f.start(m, a);
			}
			return;
		}
		f.step();
		Beat b = f.beat;
		if (f.action != Matriarch.RUSH && f.action != ElderBoss.ULTIMATE) {
			m.motion.turn(m, yaw, 1.8f);
			m.motion.brake(m, 0.025, 0);
		}
		switch (f.action) {
			case Matriarch.SWEEP -> {
				if (b.at(18)) {
					sweep(m);
				}
				if (b.past(34)) {
					f.end(m, 55);
				}
			}
			case Matriarch.LANTERNS -> {
				if (b.at(10)) {
					lanterns(m, f, p);
				}
				if (b.past(32)) {
					f.end(m, 60);
				}
			}
			case Matriarch.ROOTS -> {
				if (b.at(20)) {
					for (ServerPlayer q : ElderFx.targets(m, 28)) {
						ElderStrikes.strike(m, q.getX(), q.getY(), q.getZ(), 2.8, 6f, f.delay(16), ElderStrikes.ROOTS);
					}
					WorldFx.soundIn(dim, "minecraft:block.rooted_dirt.break", "hostile", m.getX(), m.getY(), m.getZ(), 4f, 0.4f);
					ElderFx.shake(m, m.getX(), m.getZ(), 30, 0.4f, 12);
				}
				if (b.past(42)) {
					f.end(m, 60);
				}
			}
			case Matriarch.WAIL -> {
				if (b.at(12)) {
					wail(m);
				}
				if (b.past(40)) {
					f.end(m, 60);
				}
			}
			case Matriarch.RUSH -> rush(m, f, p, b);
			case Matriarch.CATHEDRAL -> cathedral(m, f, b);
			case Matriarch.BARRAGE -> barrage(m, f, b);
			case ElderBoss.ULTIMATE -> requiem(m, f, b);
			default -> f.end(m, 40);
		}
	}

	/** Her talons across everything in front of her, tearing up the ground they pass over. */
	private static void sweep(Matriarch m) {
		String dim = ElderFx.dim(m);
		ServerLevel level = (ServerLevel) m.level();
		double yaw = Math.toRadians(m.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		for (int i = 0; i < 9; i++) {
			double a = -1.1 + 2.2 * i / 8;
			double cx = fx * Math.cos(a) - fz * Math.sin(a), cz = fz * Math.cos(a) + fx * Math.sin(a);
			WorldFx.particleIn(level, "minecraft:sweep_attack", m.getX() + cx * 8, m.getY() + 3, m.getZ() + cz * 8, 0, 0, 0, 0, 1);
			ElderTerrain.furrow(level, m.getX() + cx * 9, m.getY(), m.getZ() + cz * 9, 1.4, 0, 2, 3);
		}
		WorldFx.soundIn(dim, "minecraft:entity.player.attack.sweep", "hostile", m.getX(), m.getY() + 4, m.getZ(), 4f, 0.4f);
		ElderFx.shake(m, m.getX(), m.getZ(), 24, 0.5f, 10);
		for (ServerPlayer q : ElderFx.targets(m, 14)) {
			double dx = q.getX() - m.getX(), dz = q.getZ() - m.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if ((d > 2 && (dx * fx + dz * fz) / d < 0.15) || q.getY() > m.getY() + 10) {
				continue;
			}
			q.hurtServer(level, m.level().damageSources().mobAttack(m), 15f);
			ElderFx.shove(m, q, 1.6, 0.5);
		}
	}

	/** Her lanterns shaken down onto those she hunts (and a few beside them). */
	private static void lanterns(Matriarch m, Fight f, ServerPlayer p) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		WorldFx.soundIn(ElderFx.dim(m), "minecraft:block.chain.break", "hostile", m.getX(), m.getY() + 10, m.getZ(), 4f, 0.5f);
		for (ServerPlayer q : ElderFx.targets(m, 40)) {
			ElderStrikes.strike(m, q.getX(), q.getY(), q.getZ(), 3.2, 9f, f.delay(30), ElderStrikes.LANTERN);
		}
		for (int i = 0; i < 4; i++) {
			double x = p.getX() + rnd.nextDouble(-8, 8), z = p.getZ() + rnd.nextDouble(-8, 8);
			double y = ElderFx.surface((ServerLevel) m.level(), x, p.getY() + 4, z, 4, 12);
			ElderStrikes.strike(m, x, Double.isNaN(y) ? p.getY() : y, z, 3.2, 9f, f.delay(34 + i * 4), ElderStrikes.LANTERN);
		}
	}

	/** Her wail: darkness, weakness and slowness for everyone close, and a blast of force that throws them back. */
	private static void wail(Matriarch m) {
		String dim = ElderFx.dim(m);
		WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", m.getX(), m.getY() + 10, m.getZ(), 5f, 0.4f);
		WorldFx.particleIn(m.level(), "extremedimensions:lurker_wisp", m.getX(), m.getY() + 10, m.getZ(), 5, 3, 5, 0.0, 90);
		WorldFx.particleIn(m.level(), "minecraft:sonic_boom", m.getX(), m.getY() + 11, m.getZ(), 0, 0, 0, 0, 1);
		ElderFx.shake(m, m.getX(), m.getZ(), 32, 0.8f, 20);
		for (ServerPlayer q : ElderFx.targets(m, 20)) {
			WorldFx.effect(q, MobEffects.DARKNESS, 5, 0);
			WorldFx.effect(q, MobEffects.WEAKNESS, 5, 0);
			WorldFx.effect(q, MobEffects.SLOWNESS, 3, 1);
			ElderFx.shove(m, q, 1.2, 0.3);
		}
	}

	/** Thornbound Charge: she rears back, then hurls herself through the ground at her prey. */
	private static void rush(Matriarch m, Fight f, ServerPlayer p, Beat b) {
		ServerLevel level = (ServerLevel) m.level();
		String dim = ElderFx.dim(m);
		if (b.at(1)) {
			ElderFx.call(m, "thornbound_charge");
			ElderFx.roar(m, 5f, 0.5f);
		}
		if (b.between(0, 18)) {
			m.motion.turn(m, ElderFx.yawTo(m, p.getX(), p.getZ()), 6f);
			m.motion.brake(m, 0.05, 0);
			// The way she will take, burning on the ground
			if (f.ticks % 3 == 0) {
				double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
				double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
				for (int i = 3; i < 30; i += 2) {
					WorldFx.particleIn(level, "minecraft:flame", m.getX() + dx / d * i, m.getY() + 0.3, m.getZ() + dz / d * i, 0.6, 0, 0.6, 0, 1);
				}
			}
			WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", m.getX(), m.getY() + 0.5, m.getZ(), 3, 0.2, 3, 0.02, 2);
		}
		if (b.at(18)) {
			double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
			double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			f.dirX = dx / d;
			f.dirZ = dz / d;
			f.struck.clear();
			ElderFx.shake(m, m.getX(), m.getZ(), 30, 0.6f, 10);
			WorldFx.soundIn(dim, "minecraft:entity.ravager.roar", "hostile", m.getX(), m.getY() + 5, m.getZ(), 5f, 0.4f);
		}
		if (b.between(18, 44)) {
			double sp = ElderFight.speed(0.62);
			m.motion.drive(m, f.dirX * sp, f.dirZ * sp, 0.22, 0);
			m.motion.turn(m, (float) (Math.atan2(f.dirZ, f.dirX) * 180 / Math.PI) - 90f, 8f);
			double fx = m.getX() + f.dirX * 3, fz = m.getZ() + f.dirZ * 3;
			ElderTerrain.furrow(level, fx, m.getY(), fz, 2.6, 1, 4, 3);
			WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", fx, m.getY() + 1, fz, 1.5, 0.5, 1.5, 0.03, 4);
			WorldFx.particleIn(level, "minecraft:crit", fx, m.getY() + 2, fz, 2, 1, 2, 0.3, 8);
			if (f.ticks % 3 == 0) {
				ElderFx.step(m, 3f);
			}
			for (ServerPlayer q : ElderFx.targets(m, 7)) {
				double dx = q.getX() - m.getX(), dz = q.getZ() - m.getZ();
				if (f.struck.contains(q.getUUID()) || dx * dx + dz * dz > 4.8 * 4.8 || q.getY() > m.getY() + 10 || q.getY() < m.getY() - 3) {
					continue;
				}
				f.struck.add(q.getUUID());
				q.hurtServer(level, level.damageSources().mobAttack(m), 16f);
				ElderFx.knock(q, m.getX() - f.dirX * 2, m.getZ() - f.dirZ * 2, 1.5, 0.85);
			}
		}
		if (b.at(44)) {
			double fx = m.getX() + f.dirX * 4, fz = m.getZ() + f.dirZ * 4;
			ElderTerrain.blast(m, fx, m.getY() + 0.5, fz, 2.4f, false);
			ElderStrikes.wave(m, fx, m.getY(), fz, 0.7, 11, 6f, "minecraft:campfire_cosy_smoke", ElderTerrain.Mark.ROOT);
			ElderFx.shake(m, fx, fz, 36, 0.9f, 16);
		}
		if (b.past(44)) {
			m.motion.brake(m, 0.06, 0);
		}
		if (b.past(58)) {
			f.end(m, 55);
		}
	}

	/** Cathedral of Thorns: both hands into the ground, and three rings of thorns burst out of it, one after another. */
	private static void cathedral(Matriarch m, Fight f, Beat b) {
		ServerLevel level = (ServerLevel) m.level();
		String dim = ElderFx.dim(m);
		if (b.at(1)) {
			ElderFx.call(m, "cathedral_of_thorns");
		}
		double[] radius = {7, 13, 19};
		for (int k = 0; k < 3; k++) {
			double mark = 20 + k * 10;
			if (b.between(mark - 10, mark) && f.ticks % 2 == 0) {
				// The ring cracks before it bursts
				ElderFx.ring(level, "minecraft:smoke", m.getX(), m.getY() + 0.2, m.getZ(), radius[k], (int) (radius[k] * 4), f.ticks * 0.05);
				ElderFx.ring(level, "extremedimensions:lurker_wisp", m.getX(), m.getY() + 0.3, m.getZ(), radius[k], (int) radius[k], 0);
			}
			if (b.at(mark)) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				int n = (int) (radius[k] * 1.4);
				for (int i = 0; i < n; i++) {
					double a = 2 * Math.PI * i / n + k * 0.3;
					double x = m.getX() + Math.cos(a) * radius[k], z = m.getZ() + Math.sin(a) * radius[k];
					ElderTerrain.column(level, x, m.getY(), z, 2 + rnd.nextInt(3), Blocks.MANGROVE_ROOTS, 110 + k * 10);
					WorldFx.particleIn(level, "minecraft:large_smoke", x, m.getY() + 1, z, 0.5, 1, 0.5, 0.02, 3);
					ElderTerrain.mark(level, x, m.getY(), z, 1.6, 0.6, ElderTerrain.Mark.ROOT);
				}
				WorldFx.soundIn(dim, "minecraft:block.rooted_dirt.break", "hostile", m.getX(), m.getY(), m.getZ(), 5f, 0.4f + k * 0.1f);
				WorldFx.soundIn(dim, "minecraft:block.pointed_dripstone.fall", "hostile", m.getX(), m.getY(), m.getZ(), 4f, 0.5f);
				ElderFx.shake(m, m.getX(), m.getZ(), 40, 0.7f, 12);
				for (ServerPlayer q : ElderFx.targets(m, radius[k] + 3)) {
					double d = Math.sqrt(q.distanceToSqr(m.getX(), q.getY(), m.getZ()));
					if (Math.abs(d - radius[k]) < 1.9 && q.getY() < m.getY() + 4) {
						q.hurtServer(level, level.damageSources().mobAttack(m), 12f);
						ElderFx.knock(q, m.getX(), m.getZ(), 0.4, 1.0);
					}
				}
			}
		}
		if (b.past(54)) {
			f.end(m, 60);
		}
	}

	/** A Hundred Lanterns: her branches thrown wide, her lanterns hurled one after another as burning bombs. */
	private static void barrage(Matriarch m, Fight f, Beat b) {
		if (b.at(1)) {
			ElderFx.call(m, "hundred_lanterns");
			WorldFx.soundIn(ElderFx.dim(m), "minecraft:block.chain.break", "hostile", m.getX(), m.getY() + 10, m.getZ(), 5f, 0.4f);
		}
		if (b.every(4, 18) && b.t < 48) {
			List<ServerPlayer> targets = ElderFx.targets(m, 44);
			if (!targets.isEmpty()) {
				int shot = (int) (b.t / 4);
				ServerPlayer q = targets.get(shot % targets.size());
				double yaw = Math.toRadians(m.getYRot());
				double side = (shot % 2 == 0 ? 1 : -1) * (5 + ThreadLocalRandom.current().nextDouble(3));
				double x = m.getX() - Math.cos(yaw) * side, z = m.getZ() - Math.sin(yaw) * side;
				ElderStrikes.bomb(m, x, m.getY() + 10, z, q, 6f, 1.7f);
				WorldFx.soundIn(ElderFx.dim(m), "minecraft:entity.blaze.shoot", "hostile", x, m.getY() + 10, z, 3f, 0.5f);
			}
		}
		if (b.past(58)) {
			f.end(m, 65);
		}
	}

	/**
	 * The Verdant Requiem: she drives her roots into the ground; roots burst from it in a spiral round her while light
	 * gathers over everyone she marks; her chest opens and draws the light in; and she blooms: the ground bursts, a
	 * wave of light runs out over it, and her curse falls on everyone within reach.
	 */
	private static void requiem(Matriarch m, Fight f, Beat b) {
		ServerLevel level = (ServerLevel) m.level();
		String dim = ElderFx.dim(m);
		int length = ElderUltimate.length();
		double k = b.t / length;
		if (b.every(2, 0)) {
			ElderFx.shake(m, m.getX(), m.getZ(), 64, (float) (0.15 + 0.5 * k), 4);
		}
		if (b.between(0, length * 0.15)) {
			WorldFx.particleIn(level, "minecraft:large_smoke", m.getX(), m.getY() + 0.5, m.getZ(), 4, 0.4, 4, 0.03, 6);
			ElderFx.ring(level, "extremedimensions:lurker_wisp", m.getX(), m.getY() + 0.3, m.getZ(), 2 + b.t * 0.6, 24, b.t * 0.1);
		}
		if (b.between(length * 0.15, length * 0.75)) {
			if (b.every(6, 0)) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				double a = b.t * 0.55, r = 6 + (b.t * 1.7) % 22;
				double x = m.getX() + Math.cos(a) * r, z = m.getZ() + Math.sin(a) * r;
				ElderTerrain.column(level, x, m.getY(), z, 4 + rnd.nextInt(4), Blocks.MANGROVE_ROOTS, 220);
				WorldFx.particleIn(level, "minecraft:explosion", x, m.getY() + 1, z, 0.5, 0.5, 0.5, 0, 1);
				WorldFx.soundIn(dim, "minecraft:block.rooted_dirt.place", "hostile", x, m.getY(), z, 3f, 0.4f);
				ElderTerrain.mark(level, x, m.getY(), z, 2.5, 0.6, ElderTerrain.Mark.ROOT);
				for (ServerPlayer q : ElderFx.targets(m, 40)) {
					if (q.distanceToSqr(x, q.getY(), z) < 2.4 * 2.4) {
						q.hurtServer(level, level.damageSources().mobAttack(m), 8f);
						ElderFx.knock(q, x, z, 0.5, 1.0);
					}
				}
			}
			if (b.every(20, 0)) {
				ElderFight.markPlayers(m, ElderFx.targets(m, 64), "minecraft:end_rod");
			}
		}
		if (b.between(length * 0.75, length - 10)) {
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			for (int i = 0; i < 6; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				WorldFx.particleIn(level, "minecraft:reverse_portal", m.getX() + Math.cos(a) * 10, m.getY() + 8 + rnd.nextDouble(6), m.getZ() + Math.sin(a) * 10,
						0.2, 0.2, 0.2, 0.4, 2);
			}
			WorldFx.particleIn(level, "minecraft:soul_fire_flame", m.getX(), m.getY() + 9, m.getZ(), 2, 3, 2, 0.05, 6);
			if (b.every(10, 0)) {
				WorldFx.soundIn(dim, "minecraft:block.beacon.ambient", "hostile", m.getX(), m.getY() + 9, m.getZ(), 5f, 0.5f + (float) k * 0.5f);
			}
		}
		if (b.at(length - 10)) {
			// The bloom
			WorldFx.soundIn(dim, "minecraft:entity.wither.spawn", "hostile", m.getX(), m.getY() + 8, m.getZ(), 6f, 0.6f);
			ElderFx.roar(m, 6f, 0.4f);
			WorldFx.particleIn(level, "minecraft:explosion_emitter", m.getX(), m.getY() + 2, m.getZ(), 3, 1, 3, 0, 4);
			ElderTerrain.blast(m, m.getX(), m.getY() + 0.5, m.getZ(), 3.5f, false);
			ElderStrikes.wave(m, m.getX(), m.getY(), m.getZ(), 1.1, 36, 10f, "minecraft:soul_fire_flame", ElderTerrain.Mark.ROOT);
			ElderStrikes.wave(m, m.getX(), m.getY(), m.getZ(), 0.8, 26, 0f, "extremedimensions:lurker_wisp", null);
			ElderTerrain.mark(level, m.getX(), m.getY(), m.getZ(), 18, 0.5, ElderTerrain.Mark.ROOT);
			f.breakUltimate(m, BOSS);
		}
		if (b.past(length)) {
			f.closeUltimate(m);
		}
	}

	/** Slain: relics, fragments and pages, and the hoard she guarded is free. */
	public static void onDefeat(Matriarch m, Entity killer) {
		Fight f = FIGHTS.remove(m.getUUID());
		ElderStrikes.forget(m);
		ElderFx.silence(m, ElderFx.MUSIC_MATRIARCH);
		if (f == null) {
			return;
		}
		f.bar.removeAllPlayers();
		HollowService.markSlain(f.hoard);
		ServerLevel level = (ServerLevel) m.level();
		ElderFx.burst(m, "extremedimensions:lurker_wisp", 200);
		ElderFx.burst(m, "minecraft:soul_fire_flame", 120);
		ElderFx.cinema(m, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
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
			if (f.action == ElderBoss.ULTIMATE) {
				ElderFx.cinema(m, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
			}
		}
		ElderStrikes.forget(m);
		ElderFx.silence(m, ElderFx.MUSIC_MATRIARCH);
		ElderFx.burst(m, "minecraft:large_smoke", 120);
		m.discard();
	}
}
