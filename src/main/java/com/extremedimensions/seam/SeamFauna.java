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
import com.extremedimensions.lore.Loot;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The life of the Seam. Veil Rays circle the islands around every visitor; the Thinned walk their villages. When a
 * visitor's thread runs thin (under a third of their time left, or while the Seam pulls them out) the Thinned near
 * them turn and follow, moving only while nobody looks at them. Down in the Fray the rays are the Frayed, and hunt.
 *
 * <p>Like the rest of the mod's creatures they are driven from here and never saved: whatever this service does not
 * drive dissolves, and comes back when someone returns.
 */
public final class SeamFauna {
	private SeamFauna() {}

	private static final int RAY = 0, THINNED = 1, FRAYED = 2, HELD = 3;
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
		return isDriven(e.getUUID());
	}

	public static boolean isDriven(UUID id) {
		return DRIVEN.containsKey(id);
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
		List<ServerPlayer> below = new ArrayList<>();
		for (ServerPlayer p : players) {
			if (SeamDepth.inFray(p.getY())) {
				below.add(p);
			}
		}
		while (it.hasNext()) {
			Map.Entry<UUID, Driven> e = it.next();
			Entity ent = seam.getEntity(e.getKey());
			if (!(ent instanceof Mob m) || !m.isAlive()) {
				it.remove();
				continue;
			}
			boolean frayed = e.getValue().kind == FRAYED;
			// The Frayed stay with whoever is down in the Fray, and sink back into the dark when nobody is
			ServerPlayer near = nearest(frayed ? below : players, m.position(), 128);
			if (near == null) {
				far.add(m);
			} else if (e.getValue().kind != HELD && m instanceof VeilRay ray && ray.farFromHome(near.getX(), near.getZ())) {
				// The rays drift along with the visitor, over whatever islands they walk to
				ThreadLocalRandom r = ThreadLocalRandom.current();
				ray.anchor(near.getX(), frayed ? frayCruise(near, r) : cruise(near, r), near.getZ(), ray.roam());
			}
		}
		far.forEach(SeamFauna::fade);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : players) {
			boolean deep = SeamDepth.inFray(p.getY());
			// Veil Rays about every visitor on the islands; the Frayed about every visitor in the Fray
			int rays = 0;
			for (Map.Entry<UUID, Driven> e : DRIVEN.entrySet()) {
				Entity ent = seam.getEntity(e.getKey());
				if (e.getValue().kind == (deep ? FRAYED : RAY) && ent != null && ent.distanceToSqr(p) < 96 * 96) {
					rays++;
				}
			}
			if (deep) {
				if (rays < Math.max(0, com.extremedimensions.config.Config.SEAM_FRAY_RAYS) && rnd.nextInt(3) == 0) {
					frayedRay(seam, p, rnd);
				}
			} else if (rays < RAYS_PER_PLAYER && rnd.nextInt(3) == 0) {
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
			// The Thinned on the knots of the Fray near a visitor down there
			if (SeamDepth.inFray(p.getY())) {
				frayed(seam, p, rnd);
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

	/** A Frayed Ray rises out of the dark below a visitor in the Fray, some way off, in air open enough for its wings. */
	private static void frayedRay(ServerLevel seam, ServerPlayer p, ThreadLocalRandom rnd) {
		double cruise = frayCruise(p, rnd);
		for (int tries = 0; tries < 4; tries++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = 30 + rnd.nextDouble() * 24;
			Vec3 at = new Vec3(p.getX() + Math.cos(a) * d, cruise - tries * 3, p.getZ() + Math.sin(a) * d);
			if (at.y < 12 || !VeilRay.open(seam, at, 6)) {
				continue;
			}
			VeilRay ray = ModEntities.VEIL_RAY.spawn(seam, BlockPos.containing(at), EntitySpawnReason.EVENT);
			if (ray != null) {
				ray.setFrayed(true);
				ray.setYRot((float) Math.toDegrees(a) + 90 + rnd.nextFloat() * 60 - 30);
				ray.anchor(p.getX() + rnd.nextDouble(-12, 12), cruise, p.getZ() + rnd.nextDouble(-12, 12), 34 + rnd.nextDouble() * 16);
				DRIVEN.put(ray.getUUID(), new Driven(FRAYED));
				WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:void_mote", at.x, at.y, at.z, 2.0, 0.6, 2.0, 0.0, 24);
			}
			return;
		}
	}

	/** The height the Frayed fly at around a visitor in the Fray: a little over them, never out of the dark. */
	private static double frayCruise(ServerPlayer p, ThreadLocalRandom rnd) {
		return Mth.clamp(p.getY() + 6 + rnd.nextDouble() * 8, 14, com.extremedimensions.config.Config.SEAM_FRAY_TOP - 4);
	}

	/** The dark closed in on a visitor in the Fray: every Frayed Ray near them is ready to dive at once. */
	public static void rouse(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		for (Map.Entry<UUID, Driven> e : DRIVEN.entrySet()) {
			if (e.getValue().kind == FRAYED && level.getEntity(e.getKey()) instanceof VeilRay ray && ray.distanceToSqr(p) < 64 * 64) {
				ray.rouse();
			}
		}
	}

	/** Holds a ray over a frayed island: it stays over the island, not with the visitor, until the fraying ends. */
	public static void hold(VeilRay ray) {
		DRIVEN.put(ray.getUUID(), new Driven(HELD));
	}

	/** Drives a ray the tests have made, as if the Seam had spawned it (one of the Frayed if it is). */
	public static void drive(VeilRay ray) {
		DRIVEN.put(ray.getUUID(), new Driven(ray.isFrayed() ? FRAYED : RAY));
	}

	/** Thinned walk the knots of the Fray, up to {@code seam_fray_thinned} on each knot near the visitor. */
	private static void frayed(ServerLevel seam, ServerPlayer p, ThreadLocalRandom rnd) {
		for (String key : DataStore.seamCells()) {
			if (!key.startsWith("k@")) {
				continue;
			}
			String[] xyz = key.substring(2).split(",");
			int kx = Integer.parseInt(xyz[0]), ky = Integer.parseInt(xyz[1]), kz = Integer.parseInt(xyz[2]);
			double dx = kx - p.getX(), dy = ky - p.getY(), dz = kz - p.getZ();
			if (dx * dx + dz * dz > 56 * 56 || Math.abs(dy) > 24) {
				continue;
			}
			int count = 0;
			for (Map.Entry<UUID, Driven> e : DRIVEN.entrySet()) {
				Entity ent = seam.getEntity(e.getKey());
				if (e.getValue().kind == THINNED && ent != null && ent.distanceToSqr(kx, ky, kz) < 14 * 14) {
					count++;
				}
			}
			if (count >= Math.max(0, com.extremedimensions.config.Config.SEAM_FRAY_THINNED) || rnd.nextInt(3) != 0) {
				continue;
			}
			BlockPos at = new BlockPos(kx + rnd.nextInt(-3, 4), ky + 1, kz + rnd.nextInt(-3, 4));
			if (seam.isLoaded(at) && seam.getBlockState(at).isAir() && seam.getBlockState(at.above()).isAir() && !seam.getBlockState(at.below()).isAir()
					&& p.distanceToSqr(at.getX(), at.getY(), at.getZ()) > 10 * 10) {
				Thinned t = ModEntities.THINNED.spawn(seam, at, EntitySpawnReason.EVENT);
				if (t != null) {
					DRIVEN.put(t.getUUID(), new Driven(THINNED));
					WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:void_mote", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0.4, 0.8, 0.4, 0.0, 14);
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
				ServerPlayer near = noticing(players, t.position(), 40);
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
		return d.inSeam && !p.isCreative() && (d.seamPull > 0 || (d.seamMax > 0 && d.seamLeft < d.seamMax / 3) || SeamDepth.inFray(p.getY())
				|| com.extremedimensions.trial.TrialService.hunted(d) || com.extremedimensions.elder.WeaverService.hunting(p)
				|| com.extremedimensions.events.FrayedSeam.on(p) != null);
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

	/** The nearest player a Thinned notices: within its range, twice as far for one who sees through the veil. */
	private static ServerPlayer noticing(List<ServerPlayer> players, Vec3 at, double range) {
		ServerPlayer best = null;
		double bd = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			double r = range * com.extremedimensions.binding.Veilsight.noticed(p);
			double d = p.distanceToSqr(at);
			if (d < r * r && d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
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

	/** A Thinned called to a fight (the Last Weaver's kin), driven like those of the villages. */
	public static void summonThinned(ServerLevel level, BlockPos at) {
		Thinned t = ModEntities.THINNED.spawn(level, at, EntitySpawnReason.EVENT);
		if (t != null) {
			DRIVEN.put(t.getUUID(), new Driven(THINNED));
		}
	}

	/** What the fauna leave behind. */
	public static void onDeath(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return;
		}
		DRIVEN.remove(e.getUUID());
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockPos at = e.blockPosition();
		if (e instanceof VeilRay ray) {
			// the Frayed carry more of the Seam in them
			drop(level, at, ray.isFrayed() ? Loot.FRAYED_RAY : Loot.VEIL_RAY);
			if (rnd.nextDouble() < com.extremedimensions.config.Config.VEIL_WING_EXTRA_CHANCE) {
				Block.popResource(level, at, new ItemStack(ModItems.VEIL_WING));
			}
		} else if (e instanceof Thinned) {
			drop(level, at, Loot.THINNED);
			if (SeamDepth.inFray(e.getY())) {
				// The Frayed carry more of what the Seam swallowed
				drop(level, at, Loot.THINNED_FRAY);
				if (rnd.nextDouble() < com.extremedimensions.config.Config.SEAM_FRAY_FRAGMENT_CHANCE) {
					Block.popResource(level, at, new ItemStack(ModItems.TETHER_FRAGMENT));
				}
			}
		}
	}

	private static void drop(ServerLevel level, BlockPos at, String table) {
		for (ItemStack s : Loot.roll(level, table, Vec3.atCenterOf(at))) {
			Block.popResource(level, at, s);
		}
	}
}
