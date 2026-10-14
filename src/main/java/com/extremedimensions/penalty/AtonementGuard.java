package com.extremedimensions.penalty;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Atonement from kills is earned in the wild, not in a farm. A kill counts only when
 * <ol>
 * <li>the monster spawned naturally (in the dark, in a cave, outside, in a structure), not from a spawner, an egg, a
 * raid, reinforcements or a command;</li>
 * <li>the killer dealt at least {@code atone_kill_min_share} of its health themselves (a farm's fall does not count);</li>
 * <li>the killer has not already killed {@code atone_kill_area_limit} monsters within {@code atone_kill_area_radius}
 * blocks in the last {@code atone_kill_area_minutes} (a farm keeps its own spot saturated).</li>
 * </ol>
 * The marks live in memory: a monster that outlives a server restart no longer counts, which only ever errs on the
 * strict side.
 */
public final class AtonementGuard {
	private AtonementGuard() {}

	private static final Set<UUID> NATURAL = ConcurrentHashMap.newKeySet();
	/** Monster -> player -> damage dealt. */
	private static final Map<UUID, Map<UUID, Float>> DEALT = new HashMap<>();

	/** Called as a monster is prepared for the world (a mixin on its spawn): remembers the wild ones. */
	public static void spawned(Mob mob, EntitySpawnReason reason) {
		if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION
				|| reason == EntitySpawnReason.STRUCTURE || reason == EntitySpawnReason.PATROL) {
			NATURAL.add(mob.getUUID());
		}
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

	public static boolean natural(LivingEntity e) {
		return NATURAL.contains(e.getUUID());
	}

	/** Every hit on a non-player: who dealt how much. */
	public static void damaged(LivingEntity e, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (e instanceof Player || taken <= 0 || !(source.getEntity() instanceof ServerPlayer p)) {
			return;
		}
		DEALT.computeIfAbsent(e.getUUID(), k -> new HashMap<>()).merge(p.getUUID(), taken, Float::sum);
	}

	/** Whether this kill earns atonement. Also records it towards the limit at this place. */
	public static boolean counts(ServerPlayer killer, LivingEntity mob) {
		boolean natural = NATURAL.remove(mob.getUUID());
		Map<UUID, Float> dealt = DEALT.remove(mob.getUUID());
		if (!natural) {
			return false;
		}
		float mine = dealt == null ? 0 : dealt.getOrDefault(killer.getUUID(), 0f);
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
		return here < Config.ATONE_KILL_AREA_LIMIT;
	}

	/** A monster died without a player's hand: nothing more to remember about it. */
	public static void forget(LivingEntity e) {
		NATURAL.remove(e.getUUID());
		DEALT.remove(e.getUUID());
	}

	/** Now and then: monsters that despawned or unloaded are forgotten. */
	public static void sweep(MinecraftServer s) {
		NATURAL.removeIf(id -> !alive(s, id));
		DEALT.keySet().removeIf(id -> !alive(s, id));
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
		NATURAL.clear();
		DEALT.clear();
	}
}
