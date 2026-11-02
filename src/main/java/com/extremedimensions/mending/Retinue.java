package com.extremedimensions.mending;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.seam.SeamFauna;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.seam.VeilRay;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the Starved One brings to the fight: the tribe's young, red-eyed and biting, called up out of the Heart in
 * waves ({@code starved_minions} every {@code starved_minion_seconds}, at most {@code starved_minions_max} at once),
 * and the Veil Rays, which turn on the living while it lives: every calm ray near the Heart turns Frayed and hunts, and
 * {@code starved_rays} Frayed Rays (one more for every player beyond the first) rise out of the dark to circle it.
 * When the fight ends the young fade, the risen rays sink back and the turned ones calm again.
 */
final class Retinue {
	private Retinue() {}

	private static final List<UUID> YOUNG = new ArrayList<>();
	private static final List<UUID> RISEN = new ArrayList<>();
	private static final List<UUID> TURNED = new ArrayList<>();
	private static int nextCall;

	static void forget() {
		YOUNG.clear();
		RISEN.clear();
		TURNED.clear();
		nextCall = 0;
	}

	/** It wakes: the first wave comes a little later, and the Frayed rise. */
	static void wake(ServerLevel seam, int players) {
		forget();
		nextCall = 160;
		int rays = Math.max(0, Config.STARVED_RAYS) + Math.max(0, players - 1);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < rays; i++) {
			for (int tries = 0; tries < 6; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, d = 26 + rnd.nextDouble() * 20;
				Vec3 at = new Vec3(MendingService.HX + Math.cos(a) * d, MendingService.HY + 14 + rnd.nextDouble() * 10, MendingService.HZ + Math.sin(a) * d);
				if (!VeilRay.open(seam, at, 5)) {
					continue;
				}
				VeilRay ray = ModEntities.VEIL_RAY.spawn(seam, BlockPos.containing(at), EntitySpawnReason.EVENT);
				if (ray != null) {
					ray.setFrayed(true);
					ray.rise();
					ray.anchor(MendingService.HX, MendingService.HY + 16, MendingService.HZ, 40);
					SeamFauna.hold(ray);
					RISEN.add(ray.getUUID());
					WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:void_mote", at.x, at.y, at.z, 2.0, 0.6, 2.0, 0.0, 24);
				}
				break;
			}
		}
		turn(seam);
	}

	/** Every calm ray near the Heart turns on the living. */
	private static void turn(ServerLevel seam) {
		AABB near = new AABB(MendingService.HX - 96, MendingService.HY - 60, MendingService.HZ - 96, MendingService.HX + 96, MendingService.HY + 80, MendingService.HZ + 96);
		for (VeilRay ray : seam.getEntitiesOfClass(VeilRay.class, near, r -> r.isAlive() && !r.isFrayed())) {
			ray.setFrayed(true);
			ray.rise();
			TURNED.add(ray.getUUID());
		}
	}

	/**
	 * Every tick of the fight (not while it is bound): rays that drift in turn, and the next wave of young is called.
	 * True on the tick it calls (it raises its arms for that).
	 */
	static boolean tick(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena, long tick, boolean ravenous) {
		if (tick % 100 == 0) {
			turn(seam);
			YOUNG.removeIf(id -> !(seam.getEntity(id) instanceof Mob m) || !m.isAlive());
		}
		if (--nextCall > 0 || arena.isEmpty()) {
			return false;
		}
		double every = Math.max(5, Config.STARVED_MINION_SECONDS) * 20 * (ravenous ? 0.7 : 1.0);
		nextCall = (int) every;
		int room = Math.max(0, Config.STARVED_MINIONS_MAX) - YOUNG.size();
		int wave = Math.min(room, Math.max(0, Config.STARVED_MINIONS));
		if (wave <= 0) {
			return false;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		// at one or two of those at the Heart, from all sides
		List<ServerPlayer> prey = new ArrayList<>(arena);
		java.util.Collections.shuffle(prey, rnd);
		int targets = Math.min(prey.size(), arena.size() > 1 ? 2 : 1);
		int called = 0;
		for (int t = 0; t < targets; t++) {
			int n = t == targets - 1 ? wave - called : wave / targets;
			if (n <= 0) {
				continue;
			}
			List<UUID> ids = HollowService.swarm(seam, prey.get(t), n, (int) Math.max(10, Config.STARVED_MINION_SECONDS + 10));
			YOUNG.addAll(ids);
			called += ids.size();
		}
		if (called == 0) {
			return false;
		}
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.soundIn(dim, "extremedimensions:keeper_roar", "hostile", so.getX(), so.getY() + 4, so.getZ(), 3f, 0.7f);
		WorldFx.soundIn(dim, "extremedimensions:hollowling_giggle", "hostile", so.getX(), so.getY() + 2, so.getZ(), 3f, 0.5f);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 4, so.getZ(), 1.5, 2, 1.5, 0, 30);
		return true;
	}

	/** How many of its young are about it now (for the tests). */
	static int young() {
		return YOUNG.size();
	}

	/** The fight is over: the young fade, the risen rays sink back into the dark, the turned ones calm. */
	static void end(ServerLevel seam) {
		for (UUID id : YOUNG) {
			if (seam.getEntity(id) instanceof Mob m && m.isAlive()) {
				HollowService.fade(m);
			}
		}
		for (UUID id : RISEN) {
			if (seam.getEntity(id) instanceof VeilRay ray && ray.isAlive()) {
				SeamFauna.fade(ray);
			}
		}
		for (UUID id : TURNED) {
			Entity e = seam.getEntity(id);
			if (e instanceof VeilRay ray && ray.isAlive()) {
				ray.setFrayed(false);
			}
		}
		forget();
	}
}
