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
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.seam.SeamFauna;
import com.extremedimensions.server.Dims;
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
 * The Last Weaver: once the hunger at the Heart is ended, the weave stirs. The first visit to a village of the Thinned
 * may wake its Weaver. It floats above the ground and keeps its distance; it casts threads that hold players to the
 * spot they stood on (and drain the Seam faster while they hold), rains needles of light, flings back anyone who
 * comes too close and twice calls its kin. Every hurt it does unpicks Seam time. Slain, it gives its thread back.
 */
public final class WeaverService {
	private WeaverService() {}

	private static final double HOVER = 2.5;

	private static final class Unweaving {
		UUID target;
		ServerBossEvent bar;
		ServerLevel level;
		int ticks, nextMove = 70, action, actionAt;
		/** How many times it has called its kin (at 60% and 30% health). */
		int kin;
		int drift = 1;
	}

	private static final Map<UUID, Unweaving> WEAVERS = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return WEAVERS.containsKey(e.getUUID());
	}

	public static void clear() {
		for (Unweaving u : WEAVERS.values()) {
			u.bar.removeAllPlayers();
		}
		WEAVERS.clear();
	}

	public static boolean unlocked() {
		return switch (Config.WEAVER_UNLOCK) {
			case 0 -> true;
			case 1 -> DataStore.sealBroken();
			default -> !DataStore.ending().isEmpty();
		};
	}

	/** Whether a Weaver is after this player, or near enough to set its kin on them. */
	public static boolean hunting(ServerPlayer p) {
		for (Map.Entry<UUID, Unweaving> e : WEAVERS.entrySet()) {
			if (p.getUUID().equals(e.getValue().target)) {
				return true;
			}
			Entity w = e.getValue().level.getEntity(e.getKey());
			if (w != null && w.level() == p.level() && w.distanceToSqr(p) < 48 * 48) {
				return true;
			}
		}
		return false;
	}

	/** A visitor stands in a village of the Thinned: on its first visit, the village's Weaver may wake. */
	public static void nearVillage(ServerPlayer p, int vx, int vy, int vz) {
		if (!unlocked() || p.isCreative() || p.isSpectator()) {
			return;
		}
		if (!DataStore.seamCells().add("w@" + vx + "," + vz)) {
			return;
		}
		DataStore.markDirty();
		if (ThreadLocalRandom.current().nextDouble() < Config.WEAVER_CHANCE) {
			summon(p, new BlockPos(vx, vy + 1, vz));
		}
	}

	/** The Weaver rises near a place and turns on this player (also the operator command). */
	public static boolean summon(ServerPlayer p, BlockPos near) {
		for (Unweaving u : WEAVERS.values()) {
			if (p.getUUID().equals(u.target)) {
				return false;
			}
		}
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockPos spot = ElderFx.ground(level, near.getX() + rnd.nextInt(-3, 4), near.getY() + 6, near.getZ() + rnd.nextInt(-3, 4), 14);
		if (spot == null || spot.distSqr(p.blockPosition()) < 10 * 10) {
			spot = ElderFx.near(level, p, 14, 20);
		}
		Weaver w = ModEntities.WEAVER.spawn(level, spot.above(2), EntitySpawnReason.EVENT);
		if (w == null) {
			return false;
		}
		w.setPersistenceRequired();
		w.setTarget(p);
		w.setYRot(ElderFx.yawTo(w, p.getX(), p.getZ()));
		Unweaving u = new Unweaving();
		u.target = p.getUUID();
		u.level = level;
		u.drift = rnd.nextBoolean() ? 1 : -1;
		u.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.weaver"), BossEvent.BossBarColor.BLUE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		u.bar.addPlayer(p);
		WEAVERS.put(w.getUUID(), u);
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:lurker_shriek", "hostile", w.getX(), w.getY() + 12, w.getZ(), 5f, 1.2f);
		ElderFx.burst(w, "extremedimensions:soul_mote", 120);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.weaver_wakes"));
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.LAST_WEAVER);
		ElderFx.music(w, ElderFx.MUSIC_WEAVER);
		return true;
	}

	public static void tick(MinecraftServer s) {
		if (WEAVERS.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		Iterator<Map.Entry<UUID, Unweaving>> it = WEAVERS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Unweaving> e = it.next();
			Unweaving u = e.getValue();
			Entity ent = u.level.getEntity(e.getKey());
			if (!(ent instanceof Weaver w) || !w.isAlive()) {
				u.bar.removeAllPlayers();
				it.remove();
				continue;
			}
			u.ticks++;
			ServerPlayer p = s.getPlayerList().getPlayer(u.target);
			if (p == null || !p.isAlive() || p.level() != w.level() || w.distanceToSqr(p) > 80 * 80) {
				later.add(() -> fade(w));
				continue;
			}
			drive(w, u, p, later);
		}
		later.forEach(Runnable::run);
	}

	private static void drive(Weaver w, Unweaving u, ServerPlayer p, List<Runnable> later) {
		String dim = ElderFx.dim(w);
		if (u.ticks % 20 == 0) {
			w.setTarget(p);
			for (ServerPlayer q : u.level.players()) {
				if (q.distanceToSqr(w) < 56 * 56) {
					u.bar.addPlayer(q);
				}
			}
		}
		if (u.ticks % 40 == 1) {
			ElderFx.music(w, ElderFx.MUSIC_WEAVER);
		}
		u.bar.setProgress(w.getHealth() / w.getMaxHealth());
		if (u.ticks % 3 == 0) {
			WorldFx.particleIn(dim, "extremedimensions:soul_mote", w.getX(), w.getY() + 13, w.getZ(), 9, 3, 2, 0.0, 2);
		}
		float frac = w.getHealth() / w.getMaxHealth();
		if ((u.kin == 0 && frac <= 0.6f) || (u.kin == 1 && frac <= 0.3f)) {
			u.kin++;
			later.add(() -> kin(w));
		}
		double dx = p.getX() - w.getX(), dz = p.getZ() - w.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		ElderFx.turn(w, ElderFx.yawTo(w, p.getX(), p.getZ()), 3f);
		if (u.action == ElderBoss.IDLE) {
			// It keeps its distance: closer when far, back when near, drifting round in between
			if (dist > 17) {
				ElderFx.glide(w, p.getX(), p.getZ(), 0.13, HOVER);
			} else if (dist < 9) {
				ElderFx.glide(w, w.getX() - dx, w.getZ() - dz, 0.11, HOVER);
			} else {
				double a = Math.atan2(w.getZ() - p.getZ(), w.getX() - p.getX()) + u.drift * 0.25;
				ElderFx.glide(w, p.getX() + Math.cos(a) * dist, p.getZ() + Math.sin(a) * dist, 0.06, HOVER);
			}
			if (--u.nextMove <= 0) {
				int roll = ThreadLocalRandom.current().nextInt(5);
				u.action = dist < 8 ? Weaver.UNRAVEL : roll < 2 ? Weaver.THREAD : Weaver.NEEDLES;
				u.actionAt = u.ticks;
				w.setAction(u.action);
			}
			return;
		}
		ElderFx.glide(w, w.getX(), w.getZ(), 0, HOVER);
		int t = u.ticks - u.actionAt;
		switch (u.action) {
			case Weaver.THREAD -> {
				if (t == 15) {
					int side = 1;
					int n = 0;
					for (ServerPlayer q : u.level.players()) {
						if (!q.isSpectator() && !q.isCreative() && q.distanceToSqr(w) < 36 * 36 && n < 2) {
							ElderStrikes.tether(w, q, side, 90);
							side = -side;
							n++;
						}
					}
					WorldFx.soundIn(dim, "minecraft:entity.leash_knot.place", "hostile", p.getX(), p.getY() + 1, p.getZ(), 3f, 0.5f);
				}
				if (t >= 30) {
					end(w, u);
				}
			}
			case Weaver.NEEDLES -> {
				if (t == 20) {
					ThreadLocalRandom rnd = ThreadLocalRandom.current();
					for (ServerPlayer q : u.level.players()) {
						if (!q.isSpectator() && q.distanceToSqr(w) < 40 * 40) {
							ElderStrikes.strike(w, q.getX(), q.getY(), q.getZ(), 2.2, 7f, 28, ElderStrikes.NEEDLE);
						}
					}
					for (int i = 0; i < 5; i++) {
						double x = p.getX() + rnd.nextDouble(-8, 8), z = p.getZ() + rnd.nextDouble(-8, 8);
						double y = ElderFx.surface(u.level, x, p.getY() + 4, z, 4, 12);
						ElderStrikes.strike(w, x, Double.isNaN(y) ? p.getY() : y, z, 2.2, 7f, 30 + i * 3, ElderStrikes.NEEDLE);
					}
					WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "hostile", w.getX(), w.getY() + 14, w.getZ(), 4f, 0.5f);
				}
				if (t >= 40) {
					end(w, u);
				}
			}
			default -> {
				if (t == 10) {
					WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", w.getX(), w.getY() + 12, w.getZ(), 4f, 1.3f);
					for (ServerPlayer q : u.level.players()) {
						if (q.distanceToSqr(w) < 11 * 11) {
							ElderFx.shove(w, q, 1.8, 0.6);
							WorldFx.effect(q, MobEffects.DARKNESS, 3, 0);
						}
					}
				}
				if (t >= 26) {
					end(w, u);
				}
			}
		}
	}

	private static void end(Weaver w, Unweaving u) {
		u.action = ElderBoss.IDLE;
		w.setAction(ElderBoss.IDLE);
		u.nextMove = 60 + ThreadLocalRandom.current().nextInt(45);
		if (ThreadLocalRandom.current().nextInt(3) == 0) {
			u.drift = -u.drift;
		}
	}

	/** Its kin come: Thinned from the village (only in the Seam, where they belong). */
	private static void kin(Weaver w) {
		ServerLevel level = (ServerLevel) w.level();
		if (!Dims.SEAM.equals(Dims.of(level))) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < 3; i++) {
			BlockPos spot = ElderFx.ground(level, (int) Math.floor(w.getX()) + rnd.nextInt(-7, 8), (int) w.getY() + 2, (int) Math.floor(w.getZ()) + rnd.nextInt(-7, 8), 10);
			if (spot != null) {
				SeamFauna.summonThinned(level, spot);
			}
		}
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:lurker_whisper", "hostile", w.getX(), w.getY() + 6, w.getZ(), 4f, 1.4f);
	}

	/** One of its blows landed: it unpicks some of the thread that holds the player in the Seam. */
	public static void unweave(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		if (!d.inSeam || d.seamPull > 0 || p.isCreative()) {
			return;
		}
		int theft = Math.max(0, Config.WEAVER_TIME_THEFT);
		if (theft <= 0) {
			return;
		}
		d.seamLeft = Math.max(1, d.seamLeft - theft);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.5, 0.0, 10);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.weaver_unpicks", theft), true);
	}

	/** Slain: its thread is given back to everyone near, with silk, fragments and a relic. */
	public static void onDefeat(Weaver w, Entity killer) {
		Unweaving u = WEAVERS.remove(w.getUUID());
		ElderStrikes.forget(w);
		ElderFx.silence(w, ElderFx.MUSIC_WEAVER);
		if (u == null) {
			return;
		}
		u.bar.removeAllPlayers();
		ServerLevel level = (ServerLevel) w.level();
		ElderFx.burst(w, "extremedimensions:soul_mote", 240);
		ElderFx.burst(w, "minecraft:end_rod", 120);
		w.spawnAtLocation(level, new ItemStack(ModItems.SEAM_SILK, 8));
		w.spawnAtLocation(level, new ItemStack(ModItems.TETHER_FRAGMENT, 3));
		for (int i = 0; i < Math.max(0, Config.WEAVER_RELICS); i++) {
			w.spawnAtLocation(level, RelicService.random());
		}
		int gift = Math.max(0, Config.WEAVER_TIME_GIFT);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(w) >= 64 * 64) {
				continue;
			}
			PlayerData d = DataStore.get(p.getUUID());
			Advancements.grant(p, Advancements.WEAVER_SLAIN);
			Journal.unlock(p, d, JournalEntry.LAST_WEAVER);
			if (d.inSeam && gift > 0) {
				d.seamLeft += gift;
				d.seamMax = Math.max(d.seamMax, d.seamLeft);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.weaver_gift", gift));
			}
		}
		DataStore.markDirty();
	}

	/** Gone: it unravels into the dark, whole. */
	public static void fade(Weaver w) {
		Unweaving u = WEAVERS.remove(w.getUUID());
		if (u != null) {
			u.bar.removeAllPlayers();
		}
		ElderStrikes.forget(w);
		ElderFx.silence(w, ElderFx.MUSIC_WEAVER);
		ElderFx.burst(w, "extremedimensions:soul_mote", 120);
		w.discard();
	}
}
