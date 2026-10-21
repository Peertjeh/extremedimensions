package com.extremedimensions.events;

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
import com.extremedimensions.lore.Loot;
import com.extremedimensions.network.Fx;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.seam.SeamFauna;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.seam.VeilRay;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Frayed Seam. Now and then, once a visit, the island a visitor stands on frays: the fog closes in, green as the Fray's,
 * strands snap somewhere out of sight, Frayed Rays rise to it and the Thinned hunt on it, and the Seam's time runs
 * out half as fast again while you stay. Cross to another island, or stay for what it leaves: when it is over a tangle
 * surfaces on the island, as in the Fray.
 */
public final class FrayedSeam {
	private FrayedSeam() {}

	/** One island fraying. */
	public static final class Fraying {
		public final UUID player;
		public final double x, z;
		public final int y;
		public final long until;
		final List<UUID> rays = new ArrayList<>();
		final Set<UUID> told = new HashSet<>();

		Fraying(UUID player, double x, int y, double z, long until) {
			this.player = player;
			this.x = x;
			this.y = y;
			this.z = z;
			this.until = until;
		}

		/** How many Frayed Rays rose to it (for the tests). */
		public int rays() {
			return rays.size();
		}

		public boolean holds(double px, double pz) {
			double r = Math.max(8, Config.FRAYED_SEAM_RADIUS);
			return (px - x) * (px - x) + (pz - z) * (pz - z) <= r * r;
		}
	}

	private static final List<Fraying> ACTIVE = new ArrayList<>();
	/** The visit (by the player's count of visits) in which each player's island last frayed. */
	private static final Map<UUID, Integer> VISIT = new HashMap<>();

	public static void tick(MinecraftServer s) {
		long now = s.getTickCount();
		ServerLevel seam = s.getLevel(SeamService.SEAM);
		if (seam == null) {
			return;
		}
		if (now % 1200 == 900 && Config.FRAYED_SEAM > 0) {
			for (ServerPlayer p : seam.players()) {
				PlayerData d = DataStore.get(p.getUUID());
				if (d.inSeam && !p.isCreative() && !SeamDepth.inFray(p) && on(p) == null && !Integer.valueOf(d.seamVisits).equals(VISIT.get(p.getUUID()))
						&& WorldEvents.free(p, now) && ThreadLocalRandom.current().nextDouble() < Config.FRAYED_SEAM_CHANCE) {
					start(seam, p, now);
				}
			}
		}
		if (now % 20 != 13) {
			return;
		}
		for (Iterator<Fraying> it = ACTIVE.iterator(); it.hasNext();) {
			Fraying f = it.next();
			if (now >= f.until) {
				finish(seam, f);
				it.remove();
				WorldEvents.end(f.player, "frayed");
				continue;
			}
			step(seam, f, now);
		}
	}

	/** Frays the island under a player. */
	public static Fraying start(ServerLevel seam, ServerPlayer p, long now) {
		VISIT.put(p.getUUID(), DataStore.get(p.getUUID()).seamVisits);
		WorldEvents.begin(p, "frayed", now);
		return start(seam, p.getUUID(), p.getX(), (int) p.getY(), p.getZ(), now);
	}

	/** Frays the island at a spot, on a player's account (for the tests too). */
	public static Fraying start(ServerLevel seam, UUID player, double x, int y, double z, long now) {
		Fraying f = new Fraying(player, x, y, z, now + Math.max(10, Config.FRAYED_SEAM_SECONDS) * 20L);
		ACTIVE.add(f);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		// Frayed Rays rise to it out of the fog, over the island
		for (int i = 0; i < Math.max(0, Config.FRAYED_SEAM_RAYS); i++) {
			for (int tries = 0; tries < 6; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, d = 18 + rnd.nextDouble() * 14;
				Vec3 at = new Vec3(f.x + Math.cos(a) * d, f.y + 14 + rnd.nextDouble() * 10, f.z + Math.sin(a) * d);
				if (!VeilRay.open(seam, at, 5)) {
					continue;
				}
				VeilRay ray = ModEntities.VEIL_RAY.spawn(seam, BlockPos.containing(at), EntitySpawnReason.EVENT);
				if (ray != null) {
					ray.setFrayed(true);
					ray.anchor(f.x, f.y + 16, f.z, Math.max(16, Config.FRAYED_SEAM_RADIUS * 0.8));
					f.rays.add(ray.getUUID());
					seam.sendParticles(ParticleTypes.WARPED_SPORE, at.x, at.y, at.z, 30, 1.5, 0.8, 1.5, 0.0);
				}
				break;
			}
		}
		// The Thinned come up on its edge, and hunt anyone on it
		for (int i = 0; i < Math.max(0, Config.FRAYED_SEAM_THINNED); i++) {
			for (int tries = 0; tries < 8; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, d = 10 + rnd.nextDouble() * 14;
				int tx = (int) Math.floor(f.x + Math.cos(a) * d), tz = (int) Math.floor(f.z + Math.sin(a) * d);
				if (!seam.isLoaded(new BlockPos(tx, f.y, tz))) {
					continue;
				}
				int top = seam.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
				if (top < f.y - 12 || top > f.y + 12) {
					continue;
				}
				SeamFauna.summonThinned(seam, new BlockPos(tx, top, tz));
				break;
			}
		}
		tell(seam, f);
		return f;
	}

	/** Every second: strands snap out of sight, the green motes drift, and who comes onto the island sees the fog close. */
	private static void step(ServerLevel seam, Fraying f, long now) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		tell(seam, f);
		for (ServerPlayer p : seam.players()) {
			if (!f.holds(p.getX(), p.getZ())) {
				continue;
			}
			seam.sendParticles(p, ParticleTypes.WARPED_SPORE, true, false, p.getX(), p.getY() + 2, p.getZ(), 24, 8, 3, 8, 0.0);
			if (rnd.nextInt(4) == 0) {
				double a = rnd.nextDouble() * Math.PI * 2;
				WorldFx.soundFor(p, "minecraft:entity.leash_knot.break", "ambient", p.getX() + Math.cos(a) * 30, p.getY() + rnd.nextInt(-6, 10),
						p.getZ() + Math.sin(a) * 30, 2.0f, 0.5f + rnd.nextFloat() * 0.3f);
			}
		}
	}

	/** Sends the closing fog to everyone on the island who has not seen it yet. */
	private static void tell(ServerLevel seam, Fraying f) {
		long now = seam.getServer().getTickCount();
		for (ServerPlayer p : seam.players()) {
			if (f.holds(p.getX(), p.getZ()) && f.told.add(p.getUUID())) {
				WorldFx.fx(p, Fx.FRAYED_SEAM, (int) Math.max(1, (f.until - now) / 20));
				WorldFx.soundSelf(p, "minecraft:ambient.basalt_deltas.mood", 1.0f, 0.6f);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.frayed_seam.begin").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), true);
			}
		}
	}

	/** It is over: the rays sink back into the fog, and a tangle surfaces on the island. */
	private static void finish(ServerLevel seam, Fraying f) {
		for (UUID id : f.rays) {
			if (seam.getEntity(id) instanceof VeilRay ray) {
				SeamFauna.fade(ray);
			}
		}
		BlockPos at = surface(seam, f);
		if (at != null) {
			seam.setBlock(at, Blocks.BARREL.defaultBlockState(), 3);
			if (seam.getBlockEntity(at) instanceof Container box) {
				Loot.fill(seam, at, box, Loot.FRAY_TANGLE);
			}
			seam.sendParticles(ParticleTypes.WARPED_SPORE, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.0);
			WorldFx.soundIn(SeamService.SEAM.identifier().toString(), "minecraft:block.vault.open_shutter", "block", at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.2f, 0.7f);
		}
		for (ServerPlayer p : seam.players()) {
			if (f.holds(p.getX(), p.getZ())) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.frayed_seam.done").withStyle(ChatFormatting.GREEN), true);
			}
		}
	}

	/** An open spot on the island's top near the middle, for the tangle. */
	private static BlockPos surface(ServerLevel seam, Fraying f) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int tries = 0; tries < 24; tries++) {
			int x = (int) Math.floor(f.x) + rnd.nextInt(-6, 7), z = (int) Math.floor(f.z) + rnd.nextInt(-6, 7);
			BlockPos probe = new BlockPos(x, f.y, z);
			if (!seam.isLoaded(probe)) {
				continue;
			}
			int top = seam.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, top, z);
			if (Math.abs(top - f.y) <= 8 && seam.getBlockState(at).isAir() && !seam.getBlockState(at.below()).isAir()) {
				return at;
			}
		}
		return null;
	}

	/** The fraying a player stands in, or null. */
	public static Fraying on(ServerPlayer p) {
		if (!SeamService.inSeam(p)) {
			return null;
		}
		for (Fraying f : ACTIVE) {
			if (f.holds(p.getX(), p.getZ()) && Math.abs(p.getY() - f.y) < 48) {
				return f;
			}
		}
		return null;
	}

	/** How much faster the Seam's time runs out for a player (on a frayed island). */
	public static double drain(ServerPlayer p) {
		return on(p) != null ? Math.max(1, Config.FRAYED_SEAM_DRAIN) : 1.0;
	}

	/** Ends a fraying at once, with its tangle (for the tests). */
	public static void finishNow(ServerLevel seam, Fraying f) {
		if (ACTIVE.remove(f)) {
			finish(seam, f);
			WorldEvents.end(f.player, "frayed");
		}
	}

	static String force(ServerPlayer p, long now) {
		if (!SeamService.inSeam(p) || !(p.level() instanceof ServerLevel seam)) {
			return "commands.extremedimensions.event.not_seam";
		}
		if (on(p) != null) {
			return "commands.extremedimensions.event.busy";
		}
		start(seam, p, now);
		return null;
	}

	public static void clear() {
		MinecraftServer s = WorldFx.server();
		ServerLevel seam = s == null ? null : s.getLevel(SeamService.SEAM);
		for (Fraying f : ACTIVE) {
			for (UUID id : f.rays) {
				if (seam != null && seam.getEntity(id) instanceof VeilRay ray) {
					ray.discard();
				}
			}
		}
		ACTIVE.clear();
		VISIT.clear();
	}
}
