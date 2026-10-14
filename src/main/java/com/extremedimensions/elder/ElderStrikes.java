package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Elders' blows that are not a hand on a body: strikes that are foretold by a ring of light and land a moment
 * later where the ring was (step out of it), soul wisps that drift after their prey, and threads that hold a player
 * to the spot they stood on.
 */
public final class ElderStrikes {
	private ElderStrikes() {}

	public static final int LANTERN = 1;
	public static final int ROOTS = 2;
	public static final int SLAM = 3;
	public static final int NEEDLE = 4;

	private static final class Strike {
		ServerLevel level;
		UUID source;
		double x, y, z, radius;
		float damage;
		int delay, age, kind;
	}

	private static final class Wisp {
		ServerLevel level;
		UUID source, target;
		double x, y, z, vx, vy, vz;
		float damage;
		int age;
	}

	private static final class Tether {
		ServerLevel level;
		UUID source, player;
		double ax, ay, az;
		int side, age, duration;
	}

	private static final List<Strike> STRIKES = new ArrayList<>();
	private static final List<Wisp> WISPS = new ArrayList<>();
	private static final List<Tether> TETHERS = new ArrayList<>();

	public static void clear() {
		STRIKES.clear();
		WISPS.clear();
		TETHERS.clear();
	}

	/** Everything an Elder set going ends with it. */
	public static void forget(Entity boss) {
		UUID id = boss.getUUID();
		STRIKES.removeIf(s -> id.equals(s.source));
		WISPS.removeIf(w -> id.equals(w.source));
		TETHERS.removeIf(t -> id.equals(t.source));
	}

	static void strike(ElderBoss source, double x, double y, double z, double radius, float damage, int delay, int kind) {
		Strike s = new Strike();
		s.level = (ServerLevel) source.level();
		s.source = source.getUUID();
		s.x = x;
		s.y = y;
		s.z = z;
		s.radius = radius;
		s.damage = damage;
		s.delay = delay;
		s.kind = kind;
		STRIKES.add(s);
	}

	static void wisp(ElderBoss source, double x, double y, double z, ServerPlayer target, float damage) {
		Wisp w = new Wisp();
		w.level = (ServerLevel) source.level();
		w.source = source.getUUID();
		w.target = target.getUUID();
		w.x = x;
		w.y = y;
		w.z = z;
		w.vy = 0.25;
		w.damage = damage;
		WISPS.add(w);
	}

	static void tether(ElderBoss source, ServerPlayer p, int side, int duration) {
		TETHERS.removeIf(t -> p.getUUID().equals(t.player));
		Tether t = new Tether();
		t.level = (ServerLevel) source.level();
		t.source = source.getUUID();
		t.player = p.getUUID();
		t.ax = p.getX();
		t.ay = p.getY();
		t.az = p.getZ();
		t.side = side;
		t.duration = duration;
		TETHERS.add(t);
	}

	/** Whether a thread holds this player. */
	public static boolean tethered(ServerPlayer p) {
		for (Tether t : TETHERS) {
			if (p.getUUID().equals(t.player)) {
				return true;
			}
		}
		return false;
	}

	public static void tick(MinecraftServer s) {
		if (!STRIKES.isEmpty()) {
			strikes();
		}
		if (!WISPS.isEmpty()) {
			wisps(s);
		}
		if (!TETHERS.isEmpty()) {
			tethers(s);
		}
	}

	private static DamageSource from(ServerLevel level, UUID source) {
		Entity e = level.getEntity(source);
		return e instanceof LivingEntity le ? level.damageSources().mobAttack(le) : level.damageSources().magic();
	}

	private static void strikes() {
		Iterator<Strike> it = STRIKES.iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Strike s = it.next();
			s.age++;
			String dim = s.level.dimension().identifier().toString();
			String warn = switch (s.kind) {
				case LANTERN -> "minecraft:soul_fire_flame";
				case ROOTS -> "minecraft:smoke";
				case SLAM -> "minecraft:flame";
				default -> "minecraft:witch";
			};
			if (s.age < s.delay && s.age % 3 == 0) {
				// The warning: a ring on the ground that tightens as the blow comes
				double r = s.radius * (1.0 - 0.35 * s.age / (double) s.delay);
				for (int i = 0; i < 14; i++) {
					double a = 2 * Math.PI * i / 14 + s.age * 0.1;
					WorldFx.particleIn(dim, warn, s.x + Math.cos(a) * r, s.y + 0.15, s.z + Math.sin(a) * r, 0, 0.02, 0, 0, 1);
				}
				if (s.kind == LANTERN) {
					WorldFx.particleIn(dim, "minecraft:soul_fire_flame", s.x, s.y + 12 - 12.0 * s.age / s.delay, s.z, 0.1, 0.1, 0.1, 0, 2);
				} else if (s.kind == NEEDLE) {
					WorldFx.particleIn(dim, "minecraft:end_rod", s.x, s.y + 9 - 9.0 * s.age / s.delay, s.z, 0.05, 0.2, 0.05, 0, 1);
				}
			}
			if (s.age >= s.delay) {
				it.remove();
				later.add(() -> land(s, dim));
			}
		}
		later.forEach(Runnable::run);
	}

	private static void land(Strike s, String dim) {
		switch (s.kind) {
			case LANTERN -> {
				WorldFx.particleIn(dim, "minecraft:soul_fire_flame", s.x, s.y + 0.5, s.z, s.radius * 0.5, 0.4, s.radius * 0.5, 0.06, 60);
				WorldFx.soundIn(dim, "minecraft:block.glass.break", "hostile", s.x, s.y, s.z, 2f, 0.6f);
				WorldFx.soundIn(dim, "minecraft:item.firecharge.use", "hostile", s.x, s.y, s.z, 2f, 0.7f);
			}
			case ROOTS -> {
				WorldFx.particleIn(dim, "minecraft:large_smoke", s.x, s.y + 1, s.z, s.radius * 0.4, 0.8, s.radius * 0.4, 0.02, 30);
				WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", s.x, s.y + 1.5, s.z, s.radius * 0.4, 1.2, s.radius * 0.4, 0, 20);
				WorldFx.soundIn(dim, "minecraft:block.rooted_dirt.break", "hostile", s.x, s.y, s.z, 2f, 0.5f);
			}
			case SLAM -> {
				WorldFx.particleIn(dim, "minecraft:lava", s.x, s.y + 0.3, s.z, s.radius * 0.4, 0.2, s.radius * 0.4, 0, 12);
				WorldFx.particleIn(dim, "minecraft:explosion", s.x, s.y + 0.5, s.z, 0.3, 0.1, 0.3, 0, 1);
				WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", s.x, s.y, s.z, 1.5f, 0.6f);
			}
			default -> {
				WorldFx.particleIn(dim, "minecraft:end_rod", s.x, s.y + 0.5, s.z, s.radius * 0.3, 0.6, s.radius * 0.3, 0.05, 30);
				WorldFx.soundIn(dim, "minecraft:entity.arrow.hit", "hostile", s.x, s.y, s.z, 2f, 0.5f);
			}
		}
		for (ServerPlayer p : s.level.players()) {
			double dx = p.getX() - s.x, dz = p.getZ() - s.z, dy = p.getY() - s.y;
			if (p.isSpectator() || p.isCreative() || dx * dx + dz * dz > s.radius * s.radius || dy < -2 || dy > 4) {
				continue;
			}
			p.hurtServer(s.level, from(s.level, s.source), s.damage);
			switch (s.kind) {
				case LANTERN -> p.setRemainingFireTicks(80);
				case ROOTS -> WorldFx.effect(p, MobEffects.SLOWNESS, 3, 3);
				case SLAM -> {
					p.setDeltaMovement(p.getDeltaMovement().x, 0.9, p.getDeltaMovement().z);
					p.hurtMarked = true;
				}
				default -> {
				}
			}
		}
	}

	private static void wisps(MinecraftServer s) {
		Iterator<Wisp> it = WISPS.iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Wisp w = it.next();
			w.age++;
			ServerPlayer p = s.getPlayerList().getPlayer(w.target);
			String dim = w.level.dimension().identifier().toString();
			if (p == null || !p.isAlive() || p.level() != w.level || w.age > 200) {
				WorldFx.particleIn(dim, "minecraft:soul", w.x, w.y, w.z, 0.2, 0.2, 0.2, 0.02, 6);
				it.remove();
				continue;
			}
			// It drifts after its prey, slower than a sprint, quicker than a walk
			double dx = p.getX() - w.x, dy = p.getY() + 1.2 - w.y, dz = p.getZ() - w.z;
			double d = Math.max(0.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
			w.vx = w.vx * 0.9 + dx / d * 0.045;
			w.vy = w.vy * 0.9 + dy / d * 0.045;
			w.vz = w.vz * 0.9 + dz / d * 0.045;
			w.x += w.vx;
			w.y += w.vy;
			w.z += w.vz;
			WorldFx.particleIn(dim, "minecraft:soul_fire_flame", w.x, w.y, w.z, 0.12, 0.12, 0.12, 0.01, 3);
			if (w.age % 2 == 0) {
				WorldFx.particleIn(dim, "extremedimensions:soul_mote", w.x, w.y, w.z, 0.2, 0.2, 0.2, 0, 1);
			}
			if (d < 1.3) {
				it.remove();
				later.add(() -> {
					WorldFx.particleIn(dim, "minecraft:soul_fire_flame", w.x, w.y, w.z, 0.4, 0.4, 0.4, 0.05, 25);
					WorldFx.soundIn(dim, "minecraft:entity.vex.hurt", "hostile", w.x, w.y, w.z, 1.5f, 0.5f);
					if (!p.isCreative() && !p.isSpectator()) {
						p.hurtServer(w.level, from(w.level, w.source), w.damage);
					}
				});
			}
		}
		later.forEach(Runnable::run);
	}

	private static void tethers(MinecraftServer s) {
		Iterator<Tether> it = TETHERS.iterator();
		while (it.hasNext()) {
			Tether t = it.next();
			t.age++;
			ServerPlayer p = s.getPlayerList().getPlayer(t.player);
			Entity boss = t.level.getEntity(t.source);
			if (p == null || !p.isAlive() || boss == null || !boss.isAlive() || p.level() != t.level || t.age > t.duration) {
				it.remove();
				continue;
			}
			String dim = t.level.dimension().identifier().toString();
			// The thread runs from the Weaver's hand to the player
			double yaw = Math.toRadians(boss.getYRot());
			double hx = boss.getX() - Math.cos(yaw) * 9.5 * t.side, hz = boss.getZ() - Math.sin(yaw) * 9.5 * t.side;
			if (t.age % 2 == 0) {
				ElderFx.line(dim, "minecraft:witch", hx, boss.getY() + 13.5, hz, p.getX(), p.getY() + 1.2, p.getZ(), 18);
			}
			// It holds them near the spot it caught them
			double dx = t.ax - p.getX(), dz = t.az - p.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 4.5) {
				p.setDeltaMovement(dx / d * 0.45, Math.max(p.getDeltaMovement().y, 0.05), dz / d * 0.45);
				p.hurtMarked = true;
			}
			// And the Seam takes its time faster while it holds
			PlayerData data = DataStore.get(p.getUUID());
			if (data.inSeam && !p.isCreative()) {
				data.seamLeft = Math.max(1, data.seamLeft - 1.0 / 20.0);
			}
		}
	}
}
