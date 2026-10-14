package com.extremedimensions.seam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The life of the Seam. Veil Rays circle the islands around every visitor; the Thinned walk their villages. When a
 * visitor's thread runs thin (under a third of their time left, or while the Seam pulls them out) the Thinned near
 * them turn and follow, moving only while nobody looks at them.
 *
 * <p>Like the rest of the mod's creatures they are driven from here and never saved: whatever this service does not
 * drive dissolves, and comes back when someone returns.
 */
public final class SeamFauna {
	private SeamFauna() {}

	private static final int RAY = 0, THINNED = 1;
	private static final int RAYS_PER_PLAYER = 3;
	private static final int THINNED_PER_VILLAGE = 4;

	private static final class Driven {
		final int kind;
		UUID hunting;
		int cooldown;

		Driven(int kind) {
			this.kind = kind;
		}
	}

	private static final Map<UUID, Driven> DRIVEN = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return DRIVEN.containsKey(e.getUUID());
	}

	public static void clear() {
		DRIVEN.clear();
	}

	public static void fade(Mob m) {
		String dim = m.level().dimension().identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:void_mote", m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 0.6, m.getBbHeight() / 3, 0.6, 0.0, 20);
		DRIVEN.remove(m.getUUID());
		m.discard();
	}

	public static void tick(MinecraftServer s) {
		ServerLevel seam = s.getLevel(SeamService.SEAM);
		if (seam == null) {
			return;
		}
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : seam.players()) {
			if (!p.isSpectator()) {
				players.add(p);
			}
		}
		hunt(seam, players);
		if (s.getTickCount() % 20 != 0) {
			return;
		}
		// Forget the dead and the far away
		Iterator<Map.Entry<UUID, Driven>> it = DRIVEN.entrySet().iterator();
		List<Mob> far = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Driven> e = it.next();
			Entity ent = seam.getEntity(e.getKey());
			if (!(ent instanceof Mob m) || !m.isAlive()) {
				it.remove();
				continue;
			}
			ServerPlayer near = nearest(players, m.position(), 128);
			if (near == null) {
				far.add(m);
			} else if (m instanceof VeilRay ray && ray.farFromHome(near.getX(), near.getZ())) {
				// The rays drift along with the visitor, over whatever islands they walk to
				ray.anchor(near.getX(), cruise(near, ThreadLocalRandom.current()), near.getZ(), ray.roam());
			}
		}
		far.forEach(SeamFauna::fade);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : players) {
			// Veil Rays about every visitor
			int rays = 0;
			for (Map.Entry<UUID, Driven> e : DRIVEN.entrySet()) {
				Entity ent = seam.getEntity(e.getKey());
				if (e.getValue().kind == RAY && ent != null && ent.distanceToSqr(p) < 96 * 96) {
					rays++;
				}
			}
			if (rays < RAYS_PER_PLAYER && rnd.nextInt(3) == 0) {
				// They come out of the fog high overhead, in open sky with room for their wings
				double cruise = cruise(p, rnd);
				for (int tries = 0; tries < 4; tries++) {
					double a = rnd.nextDouble() * Math.PI * 2, d = 44 + rnd.nextDouble() * 30;
					Vec3 at = new Vec3(p.getX() + Math.cos(a) * d, Math.min(230, cruise + tries * 10), p.getZ() + Math.sin(a) * d);
					if (!VeilRay.open(seam, at, 7)) {
						continue;
					}
					VeilRay ray = ModEntities.VEIL_RAY.spawn(seam, BlockPos.containing(at), EntitySpawnReason.EVENT);
					if (ray != null) {
						ray.setYRot((float) Math.toDegrees(a) + 90 + rnd.nextFloat() * 60 - 30);
						ray.anchor(p.getX() + rnd.nextDouble(-16, 16), cruise, p.getZ() + rnd.nextDouble(-16, 16), 48 + rnd.nextDouble() * 26);
						DRIVEN.put(ray.getUUID(), new Driven(RAY));
					}
					break;
				}
			}
			// The Thinned in villages near this visitor
			for (String key : DataStore.seamCells()) {
				if (!key.startsWith("v@")) {
					continue;
				}
				String[] xyz = key.substring(2).split(",");
				int vx = Integer.parseInt(xyz[0]), vy = Integer.parseInt(xyz[1]), vz = Integer.parseInt(xyz[2]);
				double dx = vx - p.getX(), dz = vz - p.getZ();
				if (dx * dx + dz * dz > 64 * 64) {
					continue;
				}
				int count = 0;
				for (Map.Entry<UUID, Driven> e : DRIVEN.entrySet()) {
					Entity ent = seam.getEntity(e.getKey());
					if (e.getValue().kind == THINNED && ent != null && ent.distanceToSqr(vx, vy, vz) < 40 * 40) {
						count++;
					}
				}
				if (count < THINNED_PER_VILLAGE) {
					BlockPos at = new BlockPos(vx + rnd.nextInt(-4, 5), vy + 1, vz + rnd.nextInt(-4, 5));
					if (seam.isLoaded(at) && seam.getBlockState(at).isAir() && !seam.getBlockState(at.below()).isAir()) {
						Thinned t = ModEntities.THINNED.spawn(seam, at, EntitySpawnReason.EVENT);
						if (t != null) {
							DRIVEN.put(t.getUUID(), new Driven(THINNED));
						}
					}
				}
			}
		}
	}

	/** Every tick: the Thinned that hunt step closer while unwatched, and freeze when looked at. */
	private static void hunt(ServerLevel seam, List<ServerPlayer> players) {
		for (Map.Entry<UUID, Driven> e : DRIVEN.entrySet()) {
			Driven d = e.getValue();
			if (d.kind != THINNED) {
				continue;
			}
			if (!(seam.getEntity(e.getKey()) instanceof Thinned t) || !t.isAlive()) {
				continue;
			}
			if (d.cooldown > 0) {
				d.cooldown--;
			}
			ServerPlayer prey = null;
			if (seam.getGameTime() % 10 == 0 || d.hunting != null) {
				ServerPlayer near = nearest(players, t.position(), 40);
				prey = near != null && thin(near) ? near : null;
				d.hunting = prey == null ? null : prey.getUUID();
			}
			if (prey == null) {
				if (t.getLurkerPose() != Lurker.POSE_WATCH) {
					t.setLurkerPose(Lurker.POSE_WATCH);
				}
				continue;
			}
			Journal.unlock(prey, DataStore.get(prey.getUUID()), JournalEntry.THINNED);
			if (watched(players, t)) {
				t.getNavigation().stop();
				t.setDeltaMovement(0, t.getDeltaMovement().y, 0);
				t.setLurkerPose(Lurker.POSE_STALK);
				continue;
			}
			t.setLurkerPose(Lurker.POSE_STALK);
			t.getNavigation().moveTo(prey, 1.35);
			if (t.distanceToSqr(prey) < 2.4 * 2.4 && d.cooldown == 0) {
				d.cooldown = 24;
				prey.hurtServer(seam, t.damageSources().mobAttack(t), 5.0F);
				WorldFx.soundIn(SeamService.SEAM.identifier().toString(), "extremedimensions:lurker_whisper", "hostile", t.getX(), t.getY(), t.getZ(), 1f, 1.6f);
			}
		}
	}

	/** The height the rays soar at around a visitor: well over the island they stand on and most of what is built on it. */
	private static double cruise(ServerPlayer p, ThreadLocalRandom rnd) {
		return Math.min(212, Math.max(p.getY(), 88) + 24 + rnd.nextDouble() * 18);
	}

	/** A visitor's thread runs thin: under a third of their time, or already being pulled out. */
	private static boolean thin(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		return d.inSeam && !p.isCreative() && (d.seamPull > 0 || (d.seamMax > 0 && d.seamLeft < d.seamMax / 3)
				|| com.extremedimensions.trial.TrialService.hunted(d));
	}

	/** True when any player close by is looking at it. */
	private static boolean watched(List<ServerPlayer> players, LivingEntity e) {
		for (ServerPlayer p : players) {
			Vec3 eye = p.getEyePosition();
			Vec3 to = new Vec3(e.getX() - eye.x, e.getEyeY() - eye.y, e.getZ() - eye.z);
			double len = to.length();
			if (len < 48 && len > 0.1 && p.getViewVector(1.0F).dot(to.scale(1 / len)) > 0.82 && p.hasLineOfSight(e)) {
				return true;
			}
		}
		return false;
	}

	private static ServerPlayer nearest(List<ServerPlayer> players, Vec3 at, double max) {
		ServerPlayer best = null;
		double bd = max * max;
		for (ServerPlayer p : players) {
			double d = p.distanceToSqr(at);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** What the fauna leave behind. */
	public static void onDeath(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return;
		}
		DRIVEN.remove(e.getUUID());
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockPos at = e.blockPosition();
		if (e instanceof VeilRay) {
			Block.popResource(level, at, new ItemStack(ModItems.SEAM_SILK, 1 + rnd.nextInt(2)));
			Block.popResource(level, at, new ItemStack(Items.GLOW_INK_SAC, 1 + rnd.nextInt(2)));
			Block.popResource(level, at, new ItemStack(ModItems.VEIL_WING, rnd.nextDouble() < com.extremedimensions.config.Config.VEIL_WING_EXTRA_CHANCE ? 2 : 1));
		} else if (e instanceof Thinned) {
			if (rnd.nextInt(3) > 0) {
				Block.popResource(level, at, new ItemStack(ModItems.SEAM_SILK, 1 + rnd.nextInt(2)));
			}
			if (rnd.nextDouble() < 0.06) {
				Block.popResource(level, at, new ItemStack(ModItems.WANDERERS_PAGE));
			}
		}
	}
}
