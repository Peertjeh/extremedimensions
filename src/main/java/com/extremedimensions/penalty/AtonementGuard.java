package com.extremedimensions.penalty;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Atonement from kills is earned in the wild, not in a farm. A kill counts only when
 * <ol>
 * <li>the monster spawned naturally (in the dark, in a cave, outside, in a structure), not from a spawner, an egg, a
 * raid, reinforcements or a command;</li>
 * <li>no monster spawner or trial spawner stands within {@code atone_spawner_radius} blocks of where it was born or
 * where it fell (a spawner's dark room is a farm, whatever else spawns in it);</li>
 * <li>the killer dealt at least {@code atone_kill_min_share} of its health themselves (a farm's fall does not count);</li>
 * <li>not already {@code atone_kill_area_limit} monsters were killed within {@code atone_kill_area_radius} blocks in the
 * last {@code atone_kill_area_minutes}, by anyone ({@code atone_kill_area_shared}, so friends cannot each take a
 * spot's share) or by the killer alone (a farm keeps its own spot saturated).</li>
 * </ol>
 * The marks live in memory: a monster that outlives a server restart no longer counts, which only ever errs on the
 * strict side.
 */
public final class AtonementGuard {
	private AtonementGuard() {}

	private static final Set<UUID> NATURAL = ConcurrentHashMap.newKeySet();
	/** Monster -> player -> damage dealt. */
	private static final Map<UUID, Map<UUID, Float>> DEALT = new HashMap<>();
	/** Monster -> its health just before the latest blow (the killing blow never reaches the after-damage event). */
	private static final Map<UUID, Float> BEFORE = new HashMap<>();

	/** Kills of wild monsters lately, by anyone: dimension, x, z, game time (for the shared per-place limit). */
	private record Kill(String dim, long x, long z, long at) {}

	private static final ArrayDeque<Kill> RECENT = new ArrayDeque<>();

	/** Called as a monster is prepared for the world (a mixin on its spawn): remembers the wild ones. */
	public static void spawned(Mob mob, EntitySpawnReason reason) {
		if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION
				|| reason == EntitySpawnReason.STRUCTURE || reason == EntitySpawnReason.PATROL)
				&& !nearSpawner(mob.level(), mob.getX(), mob.getY(), mob.getZ())) {
			NATURAL.add(mob.getUUID());
		}
	}

	/**
	 * Whether a monster spawner or a trial spawner stands within {@code atone_spawner_radius} blocks of this spot. Only
	 * chunks already loaded are looked in (from the server's thread; elsewhere, as while the world is being generated,
	 * the answer is no).
	 */
	public static boolean nearSpawner(Level level, double x, double y, double z) {
		double r = Config.ATONE_SPAWNER_RADIUS;
		if (r <= 0 || !(level instanceof ServerLevel server)) {
			return false;
		}
		int x0 = SectionPos.blockToSectionCoord(x - r), x1 = SectionPos.blockToSectionCoord(x + r);
		int z0 = SectionPos.blockToSectionCoord(z - r), z1 = SectionPos.blockToSectionCoord(z + r);
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				LevelChunk chunk = server.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if ((be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) && !be.isRemoved()) {
						BlockPos at = be.getBlockPos();
						if (at.distToCenterSqr(x, y, z) <= r * r) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * A creature this mod sends after the player (a wound storm's leak, the risen of a hunted night) is as wild as any:
	 * it was never anyone's farm.
	 */
	public static void markWild(net.minecraft.world.entity.Entity e) {
		if (e != null) {
			NATURAL.add(e.getUUID());
		}
	}

	/** Whether this creature is of the wild and not by a spawner (for the deeds that break a curse, as for atonement). */
	public static boolean natural(LivingEntity e) {
		return NATURAL.contains(e.getUUID()) && !nearSpawner(e.level(), e.getX(), e.getY(), e.getZ());
	}

	/** Every hit on a non-player: who dealt how much. */
	public static void damaged(LivingEntity e, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (e instanceof Player || taken <= 0 || !(source.getEntity() instanceof ServerPlayer p)) {
			return;
		}
		DEALT.computeIfAbsent(e.getUUID(), k -> new HashMap<>()).merge(p.getUUID(), taken, Float::sum);
	}

	/** Before every blow on a wild monster: its health, so the killing blow can be counted for the one who struck it. */
	public static boolean hurting(LivingEntity e, DamageSource source, float amount) {
		if (!(e instanceof Player) && NATURAL.contains(e.getUUID())) {
			BEFORE.put(e.getUUID(), e.getHealth());
		}
		return true;
	}

	/** Whether this kill (the killer struck the last blow) earns atonement. Also records it towards the limit here. */
	public static boolean counts(ServerPlayer killer, LivingEntity mob) {
		boolean natural = NATURAL.remove(mob.getUUID());
		Map<UUID, Float> dealt = DEALT.remove(mob.getUUID());
		Float last = BEFORE.remove(mob.getUUID());
		if (!natural || nearSpawner(mob.level(), mob.getX(), mob.getY(), mob.getZ())) {
			return false;
		}
		float mine = (dealt == null ? 0 : dealt.getOrDefault(killer.getUUID(), 0f)) + (last == null ? 0 : Math.max(0, last));
		if (mine < mob.getMaxHealth() * Config.ATONE_KILL_MIN_SHARE) {
			return false;
		}
		PlayerData d = DataStore.get(killer.getUUID());
		long now = killer.level().getGameTime();
		long window = Math.max(1, Config.ATONE_KILL_AREA_MINUTES) * 1200L;
		double r2 = Config.ATONE_KILL_AREA_RADIUS * Config.ATONE_KILL_AREA_RADIUS;
		d.recentKills.removeIf(k -> now - k[2] > window);
		int here = 0;
		for (long[] k : d.recentKills) {
			double dx = k[0] - mob.getX(), dz = k[1] - mob.getZ();
			if (dx * dx + dz * dz <= r2) {
				here++;
			}
		}
		d.recentKills.add(new long[] {(long) mob.getX(), (long) mob.getZ(), now});
		if (Config.ATONE_KILL_AREA_SHARED > 0) {
			// Everyone's kills at this place count towards its limit, so friends cannot each take its share (all levels
			// share one game clock)
			String dim = mob.level().dimension().identifier().toString();
			RECENT.removeIf(k -> now - k.at() > window || k.at() > now);
			int shared = 0;
			for (Kill k : RECENT) {
				double dx = k.x() - mob.getX(), dz = k.z() - mob.getZ();
				if (k.dim().equals(dim) && dx * dx + dz * dz <= r2) {
					shared++;
				}
			}
			RECENT.add(new Kill(dim, (long) mob.getX(), (long) mob.getZ(), now));
			return shared < Config.ATONE_KILL_AREA_LIMIT && here < Config.ATONE_KILL_AREA_LIMIT;
		}
		return here < Config.ATONE_KILL_AREA_LIMIT;
	}

	/** A monster died without a player's hand: nothing more to remember about it. */
	public static void forget(LivingEntity e) {
		NATURAL.remove(e.getUUID());
		DEALT.remove(e.getUUID());
		BEFORE.remove(e.getUUID());
	}

	/** Now and then: monsters that despawned or unloaded are forgotten. */
	public static void sweep(MinecraftServer s) {
		NATURAL.removeIf(id -> !alive(s, id));
		DEALT.keySet().removeIf(id -> !alive(s, id));
		BEFORE.keySet().removeIf(id -> !NATURAL.contains(id));
	}

	private static boolean alive(MinecraftServer s, UUID id) {
		for (ServerLevel level : s.getAllLevels()) {
			if (level.getEntity(id) != null) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		RECENT.clear();
		NATURAL.clear();
		DEALT.clear();
		BEFORE.clear();
	}
}
