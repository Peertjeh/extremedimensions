package com.extremedimensions.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.network.Fx;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The world after the Dragon. The Ender Dragon was no enemy but a seal on the first wound; when it dies the seal
 * breaks:
 *
 * <ul>
 *   <li>everyone sees the world crack, and the journal tells what was done;</li>
 *   <li>the tribe turns from thieves into frightened supplicants: Lurkers no longer steal, they leave offerings;</li>
 *   <li>the Pale Void starves (the End drains faster);</li>
 *   <li><b>Wound Storms</b> break out in the Overworld: the sky tears, darkness pulses and things from the Nether
 *       and the End leak through around you for a minute.</li>
 * </ul>
 *
 * The Mending (slaying the Starved One in the Seam and choosing) settles it, one way or the other.
 */
public final class WorldService {
	private WorldService() {}

	private static final Map<UUID, Integer> STORMS = new HashMap<>();
	private static final Map<UUID, Long> LAST_STORM = new HashMap<>();

	public static void clear() {
		STORMS.clear();
		LAST_STORM.clear();
	}

	/** Lurkers still steal: before the seal breaks, and again in a severed world. */
	public static boolean tribeSteals() {
		return !DataStore.sealBroken() || "severed".equals(DataStore.ending());
	}

	/** Extra drain on top of a player's own, from the state of the world. */
	public static double drainFactor(String dim) {
		String ending = DataStore.ending();
		if ("severed".equals(ending)) {
			return 0.0;
		}
		if ("mended".equals(ending)) {
			return Config.MENDED_DRAIN;
		}
		return DataStore.sealBroken() && Dims.END.equals(dim) ? Config.STARVING_END_DRAIN : 1.0;
	}

	public static void onDragonDeath(MinecraftServer s) {
		boolean first = !DataStore.sealBroken();
		DataStore.setSealBroken(true);
		if ("mended".equals(DataStore.ending())) {
			// A mended world slays its dragon again: the seam stays closed
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			WorldFx.fx(p, Fx.SEAL, 0);
			WorldFx.soundSelf(p, "extremedimensions:keeper_roar", 1.0f, 0.5f);
			WorldFx.soundSelf(p, "minecraft:block.end_portal.spawn", 0.8f, 0.6f);
			Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.SEAL);
			Advancements.grant(p, Advancements.SEAL_BROKEN);
		}
		if (first) {
			DataStore.markDirty();
		}
	}

	/** A Lurker that goes quietly, after the seal broke, may leave something behind. */
	public static void offering(ServerLevel level, double x, double y, double z) {
		if (tribeSteals() || ThreadLocalRandom.current().nextDouble() >= Config.LURKER_OFFERING_CHANCE) {
			return;
		}
		for (ItemStack s : Loot.roll(level, Loot.LURKER_OFFERING, new Vec3(x, y + 0.5, z))) {
			net.minecraft.world.level.block.Block.popResource(level, BlockPos.containing(x, y + 0.5, z), s);
		}
		WorldFx.particleIn(level.dimension().identifier().toString(), "extremedimensions:soul_mote", x, y + 0.8, z, 0.3, 0.3, 0.3, 0.0, 8);
	}

	// ------------------------------------------------------------------ wound storms

	public static void tick(MinecraftServer s) {
		boolean storms = DataStore.sealBroken() && !"mended".equals(DataStore.ending());
		int now = s.getTickCount();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			Integer left = STORMS.get(p.getUUID());
			if (left != null) {
				if (left <= 0 || !Dims.OVERWORLD.equals(Dims.of(p))) {
					STORMS.remove(p.getUUID());
				} else {
					STORMS.put(p.getUUID(), left - 1);
					storm(p, left);
				}
				continue;
			}
			if (!storms || now % 1200 != 0 || p.isSpectator() || !Dims.OVERWORLD.equals(Dims.of(p))) {
				continue;
			}
			Long last = LAST_STORM.get(p.getUUID());
			if (last != null && now - last < Config.STORM_COOLDOWN_MINUTES * 1200L) {
				continue;
			}
			// Storms keep to the world events' rules: not in an Elder fight, not on the heels of another event
			if (ThreadLocalRandom.current().nextDouble() < Config.STORM_CHANCE && com.extremedimensions.events.WorldEvents.free(p, now)) {
				begin(p, now);
			}
		}
	}

	public static void begin(ServerPlayer p, int now) {
		LAST_STORM.put(p.getUUID(), (long) now);
		STORMS.put(p.getUUID(), Math.max(10, Config.STORM_SECONDS) * 20);
		WorldFx.fx(p, Fx.STORM, 0);
		WorldFx.sound(p, "minecraft:entity.lightning_bolt.thunder", 1.5f, 0.6f);
		WorldFx.sound(p, "extremedimensions:rift_open", 1.5f, 0.6f);
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.STORMS);
		com.extremedimensions.events.WorldEvents.mark(p, now);
		// Anchors without a soul ward near may crack
		com.extremedimensions.events.UnstableAnchors.storm(p);
	}

	/** One tick of a storm around a player: tears in the air, darkness, leaking creatures, flickering anchors. */
	private static void storm(ServerPlayer p, int left) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		ServerLevel level = (ServerLevel) p.level();
		if (left % 4 == 0) {
			double a = rnd.nextDouble() * Math.PI * 2, r = 6 + rnd.nextDouble() * 18;
			double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r;
			double y = p.getY() + 4 + rnd.nextDouble() * 10;
			WorldFx.particleAt("minecraft:reverse_portal", x, y, z, 0.3, 1.5, 0.3, 0.3, 20);
			WorldFx.particleAt("extremedimensions:lurker_wisp", x, y, z, 0.4, 1.2, 0.4, 0.0, 4);
		}
		if (left % 140 == 0) {
			WorldFx.effect(p, MobEffects.DARKNESS, 3, 0);
			WorldFx.sound(p, "minecraft:entity.lightning_bolt.thunder", 0.8f, 0.5f + rnd.nextFloat() * 0.3f);
		}
		if (left % 300 == 210) {
			// where the next things will slip through is chosen three seconds before (a Wound Lens can see it)
			BREACHES.put(p.getUUID(), breaches(level, p));
		}
		if (left % 300 == 150) {
			leak(level, p);
		}
		if (left % 40 == 0) {
			for (Anchor an : DataStore.anchors().values()) {
				if (an.distSq(p.getX(), p.getY(), p.getZ()) < 32 * 32) {
					WorldFx.particleAt("minecraft:large_smoke", an.x + 0.5, an.y + 1.0, an.z + 0.5, 0.2, 0.2, 0.2, 0.02, 4);
					WorldFx.soundAt("minecraft:block.respawn_anchor.deplete", an.x, an.y, an.z, 0.4f, 1.4f);
				}
			}
		}
	}

	/** Where the next things from beyond will slip through around a player, chosen ahead (behind and beside them, out of sight). */
	private static final java.util.Map<UUID, java.util.List<BlockPos>> BREACHES = new java.util.HashMap<>();

	private static java.util.List<BlockPos> breaches(ServerLevel level, ServerPlayer p) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		int n = 1 + rnd.nextInt(2);
		for (int i = 0; i < n; i++) {
			double a = Math.toRadians(p.getYRot()) + Math.PI / 2 + (rnd.nextDouble() - 0.5) * 2.0;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * (14 + rnd.nextInt(8)));
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * (14 + rnd.nextInt(8)));
			if (level.isLoaded(new BlockPos(x, (int) p.getY(), z))) {
				BlockPos b = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				// nothing breaks through near a Veil Lantern
				if (!com.extremedimensions.anchor.SoulWards.veiled(level, b)) {
					out.add(b);
				}
			}
		}
		return out;
	}

	/** The breach points waiting around a player in a storm (empty when none), for the Wound Lens. */
	public static java.util.List<BlockPos> breaches(UUID id) {
		return STORMS.containsKey(id) ? BREACHES.getOrDefault(id, java.util.List.of()) : java.util.List.of();
	}

	/** Something from beyond slips through the tear, out of sight, where the breach was chosen. */
	private static void leak(ServerLevel level, ServerPlayer p) {
		java.util.List<BlockPos> at = BREACHES.remove(p.getUUID());
		if (Rules.peaceful(level)) {
			return;
		}
		if (at == null) {
			at = breaches(level, p);
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		String[] beyond = {"minecraft:blaze", "minecraft:magma_cube", "minecraft:enderman", "minecraft:wither_skeleton",
				"minecraft:endermite", "minecraft:ghast", "minecraft:piglin_brute"};
		for (BlockPos b : at) {
			int x = b.getX(), y = b.getY(), z = b.getZ();
			String type = beyond[rnd.nextInt(beyond.length)];
			if (type.endsWith("ghast")) {
				y += 12;
			}
			WorldFx.particleAt("minecraft:reverse_portal", x + 0.5, y + 1, z + 0.5, 0.4, 1.0, 0.4, 0.2, 40);
			com.extremedimensions.penalty.AtonementGuard.markWild(WorldFx.summon(level, type, x, y, z));
		}
	}
}
