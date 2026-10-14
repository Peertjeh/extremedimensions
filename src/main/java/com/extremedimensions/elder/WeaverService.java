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
import com.extremedimensions.lurker.Lurker;
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
 * may wake its Weaver. Each of its blows unpicks Seam time, it draws its prey in on a thread, and twice it calls its
 * kin. Slain, it gives its thread back: Seam time for everyone near, silk, fragments and a relic.
 */
public final class WeaverService {
	private WeaverService() {}

	private static final class Unweaving {
		UUID target;
		ServerBossEvent bar;
		ServerLevel level;
		int ticks;
		int nextTrick = 140;
		/** How many times it has called its kin (at 60% and 30% health). */
		int kin;
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
			if (w != null && w.level() == p.level() && w.distanceToSqr(p) < 40 * 40) {
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

	/** The Weaver wakes near a place and turns on this player (also the operator command). */
	public static boolean summon(ServerPlayer p, BlockPos near) {
		for (Unweaving u : WEAVERS.values()) {
			if (p.getUUID().equals(u.target)) {
				return false;
			}
		}
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockPos spot = ElderFx.ground(level, near.getX() + rnd.nextInt(-3, 4), near.getY() + 4, near.getZ() + rnd.nextInt(-3, 4), 10);
		if (spot == null) {
			spot = ElderFx.near(level, p, 8, 14);
		}
		Weaver w = ModEntities.WEAVER.spawn(level, spot, EntitySpawnReason.EVENT);
		if (w == null) {
			return false;
		}
		w.setPersistenceRequired();
		w.setTarget(p);
		Unweaving u = new Unweaving();
		u.target = p.getUUID();
		u.level = level;
		u.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.weaver"), BossEvent.BossBarColor.BLUE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		u.bar.addPlayer(p);
		WEAVERS.put(w.getUUID(), u);
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:lurker_shriek", "hostile", w.getX(), w.getY() + 3, w.getZ(), 3f, 1.3f);
		WorldFx.particleIn(ElderFx.dim(w), "extremedimensions:soul_mote", w.getX(), w.getY() + 2, w.getZ(), 1.0, 2.0, 1.0, 0.0, 60);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.weaver_wakes"));
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.LAST_WEAVER);
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
			if (p == null || !p.isAlive() || p.level() != w.level() || w.distanceToSqr(p) > 64 * 64) {
				later.add(() -> fade(w));
				continue;
			}
			if (u.ticks % 20 == 0) {
				w.setTarget(p);
				for (ServerPlayer q : u.level.players()) {
					if (q.distanceToSqr(w) < 40 * 40) {
						u.bar.addPlayer(q);
					}
				}
			}
			u.bar.setProgress(w.getHealth() / w.getMaxHealth());
			double d2 = w.distanceToSqr(p);
			w.setLurkerPose(d2 < 4.5 * 4.5 ? Lurker.POSE_TAKE : w.getDeltaMovement().horizontalDistanceSqr() > 0.002 ? Lurker.POSE_RUN : Lurker.POSE_WATCH);
			if (u.ticks % 5 == 0) {
				WorldFx.particleIn(ElderFx.dim(w), "extremedimensions:soul_mote", w.getX(), w.getY() + 2.5, w.getZ(), 0.6, 1.4, 0.6, 0.0, 1);
			}
			float f = w.getHealth() / w.getMaxHealth();
			if ((u.kin == 0 && f <= 0.6f) || (u.kin == 1 && f <= 0.3f)) {
				u.kin++;
				later.add(() -> kin(w));
			}
			if (--u.nextTrick <= 0) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				u.nextTrick = 140 + rnd.nextInt(80);
				if (d2 > 5 * 5) {
					// A thread: drawn in, and slowed while it holds
					ElderFx.pull(w, p, 5);
					WorldFx.effect(p, MobEffects.SLOWNESS, 3, 1);
					WorldFx.soundIn(ElderFx.dim(w), "minecraft:entity.leash_knot.place", "hostile", p.getX(), p.getY() + 1, p.getZ(), 2f, 0.6f);
				} else {
					WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:lurker_shriek", "hostile", w.getX(), w.getY() + 3, w.getZ(), 2.5f, 1.2f);
					WorldFx.effect(p, MobEffects.DARKNESS, 3, 0);
				}
			}
		}
		later.forEach(Runnable::run);
	}

	/** Its kin come: Thinned from the village (only in the Seam, where they belong). */
	private static void kin(Weaver w) {
		ServerLevel level = (ServerLevel) w.level();
		if (!Dims.SEAM.equals(Dims.of(level))) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < 2; i++) {
			BlockPos spot = ElderFx.ground(level, (int) Math.floor(w.getX()) + rnd.nextInt(-5, 6), (int) w.getY() + 3, (int) Math.floor(w.getZ()) + rnd.nextInt(-5, 6), 7);
			if (spot != null) {
				SeamFauna.summonThinned(level, spot);
			}
		}
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:lurker_whisper", "hostile", w.getX(), w.getY() + 3, w.getZ(), 3f, 1.4f);
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
		if (u == null) {
			return;
		}
		u.bar.removeAllPlayers();
		ServerLevel level = (ServerLevel) w.level();
		w.spawnAtLocation(level, new ItemStack(ModItems.SEAM_SILK, 8));
		w.spawnAtLocation(level, new ItemStack(ModItems.TETHER_FRAGMENT, 3));
		for (int i = 0; i < Math.max(0, Config.WEAVER_RELICS); i++) {
			w.spawnAtLocation(level, RelicService.random());
		}
		int gift = Math.max(0, Config.WEAVER_TIME_GIFT);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(w) >= 48 * 48) {
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
		WorldFx.particleIn(ElderFx.dim(w), "extremedimensions:soul_mote", w.getX(), w.getY() + 2, w.getZ(), 0.6, 1.4, 0.6, 0.0, 30);
		w.discard();
	}
}
