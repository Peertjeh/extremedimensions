package com.extremedimensions.tapestry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The Weaving: when the last panel is set, the wounds converge on the loom. For {@link Config#WEAVING_SECONDS} the
 * Thinned and the Lurkers come out of rifts torn round it in waves and make for the loom; each one that reaches it
 * tears at its ward, and whoever stands near is struck. Held to the end, the tapestry is finished (the advancement
 * <i>The Unfinished World</i>, soul light round the loom for good, and New Thread+ on offer). If the ward breaks, the
 * last panel unravels (its thread falls at the loom) and the loom will not take it again for a while.
 *
 * <p>Only one Weaving runs at a time (there is one tapestry). It is not kept over a restart: the six panels stay, and
 * using the loom begins it again.
 */
public final class Weaving {
	private Weaving() {}

	/** Players within this many blocks of the loom see its bar and are counted for the waves. */
	private static final double NEAR = 48;

	private static final class Run {
		ServerLevel level;
		BlockPos loom;
		String dim;
		int ticks;
		int length;
		double ward;
		int nextWave;
		ServerBossEvent bar;
		/** The foes of the waves: each one's next blow (ticks). */
		final Map<UUID, Integer> foes = new HashMap<>();
	}

	private static Run run;

	public static boolean active() {
		return run != null;
	}

	/** Whether this creature is one of the Weaving's (they are driven here, and nowhere else). */
	public static boolean drives(UUID id) {
		return run != null && run.foes.containsKey(id);
	}

	/** The ward left (0 to 1), for the tests; -1 when no Weaving runs. */
	public static double ward() {
		return run == null ? -1 : run.ward / Math.max(1, Config.WEAVING_WARD);
	}

	/** How many of its foes are about, for the tests. */
	public static int foes() {
		return run == null ? 0 : run.foes.size();
	}

	public static void start(ServerLevel level, BlockPos loom) {
		if (run != null) {
			return;
		}
		Run r = new Run();
		r.level = level;
		r.loom = loom.immutable();
		r.dim = level.dimension().identifier().toString();
		r.length = Math.max(30, Config.WEAVING_SECONDS) * 20;
		r.ward = Math.max(1, Config.WEAVING_WARD);
		r.nextWave = 60;
		r.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("gui.extremedimensions.weaving.bar"), BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		r.bar.setDarkenScreen(true);
		run = r;
		double x = loom.getX() + 0.5, y = loom.getY() + 1, z = loom.getZ() + 0.5;
		WorldFx.soundIn(r.dim, "minecraft:block.end_portal.spawn", "hostile", x, y, z, 1.0f, 0.5f);
		WorldFx.particleIn(r.dim, "extremedimensions:soul_mote", x, y, z, 1.5, 1.5, 1.5, 0.05, 80);
		for (ServerPlayer p : level.players()) {
			if (near(p, r)) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.weaving.begins").withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}

	private static boolean near(ServerPlayer p, Run r) {
		return !p.isSpectator() && p.distanceToSqr(r.loom.getX() + 0.5, r.loom.getY() + 0.5, r.loom.getZ() + 0.5) < NEAR * NEAR;
	}

	public static void tick(MinecraftServer s) {
		Run r = run;
		if (r == null) {
			return;
		}
		if (!Loaded.ready(r.level, r.loom)) {
			// Nobody keeps the loom's part of the world loaded: the Weaving is let go, not broken. Its last panel stays
			// woven (a thread dropped where nobody is would be lost), and the loom begins it again once the wait is over
			end(r);
			DataStore.setWeaveAgainAt(System.currentTimeMillis() + Math.max(0, Config.WEAVING_RETRY_MINUTES) * 60_000L);
			DataStore.markDirty();
			return;
		}
		if (!r.level.getBlockState(r.loom).is(com.extremedimensions.registry.ModBlocks.WEAVERS_LOOM)) {
			// The loom itself was taken away: the weave cannot hold
			fail(r);
			return;
		}
		r.ticks++;
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer p : r.level.players()) {
			if (near(p, r)) {
				near.add(p);
			}
		}
		// Off the bar first: anyone not near, and a player's old self after a respawn or a portal (the same connection
		// told of a bar it no longer shows would drop the player)
		for (ServerPlayer p : new ArrayList<>(r.bar.getPlayers())) {
			if (!near.contains(p)) {
				r.bar.removePlayer(p);
			}
		}
		for (ServerPlayer p : near) {
			r.bar.addPlayer(p);
		}
		if (--r.nextWave <= 0) {
			r.nextWave = Math.max(5, Config.WEAVING_WAVE_SECONDS) * 20;
			wave(r, near.size());
		}
		drive(r, near);
		r.bar.setProgress((float) Math.max(0, Math.min(1, r.ward / Math.max(1, Config.WEAVING_WARD))));
		int left = Math.max(0, (r.length - r.ticks) / 20);
		r.bar.setName(Component.translatable("gui.extremedimensions.weaving.bar_left", left / 60, String.format("%02d", left % 60)));
		if (r.ticks % 10 == 0) {
			double x = r.loom.getX() + 0.5, y = r.loom.getY() + 1.2, z = r.loom.getZ() + 0.5;
			WorldFx.particleIn(r.dim, "extremedimensions:soul_mote", x, y, z, 0.4, 0.6, 0.4, 0.02, 4);
		}
		if (r.ward <= 0) {
			fail(r);
		} else if (r.ticks >= r.length) {
			hold(r);
		}
	}

	/** A wave: rifts torn in a ring round the loom, each giving up a Thinned or a Lurker. */
	private static void wave(Run r, int players) {
		if (Rules.peaceful(r.level)) {
			return;
		}
		int n = Math.max(0, Config.WEAVING_WAVE) + Math.max(0, Config.WEAVING_WAVE_PER_PLAYER) * Math.max(0, players - 1);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < n; i++) {
			BlockPos at = null;
			// A ring round the loom first; where the ground does not reach that far (an island's edge), nearer in
			for (int tries = 0; tries < 12 && at == null; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, d = tries < 6 ? 14 + rnd.nextDouble() * 8 : 5 + rnd.nextDouble() * 5;
				at = ground(r, r.loom.getX() + (int) Math.round(Math.cos(a) * d), r.loom.getZ() + (int) Math.round(Math.sin(a) * d));
			}
			if (at == null) {
				continue;
			}
			int x = at.getX(), y = at.getY(), z = at.getZ();
			Mob m = i % 3 == 2 ? ModEntities.LURKER.spawn(r.level, at, EntitySpawnReason.EVENT) : ModEntities.THINNED.spawn(r.level, at, EntitySpawnReason.EVENT);
			if (m == null) {
				continue;
			}
			m.setPersistenceRequired();
			r.foes.put(m.getUUID(), 0);
			WorldFx.particleIn(r.dim, "minecraft:reverse_portal", x + 0.5, y + 1, z + 0.5, 0.4, 0.9, 0.4, 0.05, 30);
			WorldFx.soundIn(r.dim, "extremedimensions:lurker_whisper", "hostile", x + 0.5, y + 1, z + 0.5, 1.0f, 0.6f);
		}
	}

	/** Where a foe can stand at (x, z), near the loom's height: room for it on solid ground. Null when there is none. */
	private static BlockPos ground(Run r, int x, int z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = r.loom.getY() + 6; y >= r.loom.getY() - 12; y--) {
			m.set(x, y, z);
			if (!Loaded.ready(r.level, m)) {
				return null;
			}
			if (r.level.getBlockState(m.below()).isSolid() && r.level.getBlockState(m).isAir() && r.level.getBlockState(m.above()).isAir()) {
				return m.immutable();
			}
		}
		return null;
	}

	/** Each tick: the foes make for the loom, strike whoever is in their way, and tear at the ward once there. */
	private static void drive(Run r, List<ServerPlayer> near) {
		double lx = r.loom.getX() + 0.5, ly = r.loom.getY(), lz = r.loom.getZ() + 0.5;
		for (Iterator<Map.Entry<UUID, Integer>> it = r.foes.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Integer> e = it.next();
			if (!(r.level.getEntity(e.getKey()) instanceof PathfinderMob m) || !m.isAlive()) {
				it.remove();
				continue;
			}
			int cooldown = Math.max(0, e.getValue() - 1);
			ServerPlayer prey = null;
			double best = 5 * 5;
			for (ServerPlayer p : near) {
				double d = p.distanceToSqr(m);
				if (d < best && !p.isCreative()) {
					best = d;
					prey = p;
				}
			}
			if (m instanceof Lurker l) {
				l.setLurkerPose(Lurker.POSE_STALK);
			} else if (m instanceof com.extremedimensions.seam.Thinned t) {
				t.setLurkerPose(Lurker.POSE_STALK);
			}
			if (prey != null) {
				// (a path is asked for a few times a second, not every tick)
				if (m.tickCount % 8 == 0 || m.getNavigation().isDone()) {
					m.getNavigation().moveTo(prey, 1.25);
				}
				if (best < 2.4 * 2.4 && cooldown == 0) {
					cooldown = 24;
					prey.hurtServer(r.level, m.damageSources().mobAttack(m), (float) Math.max(0, Config.WEAVING_DAMAGE));
				}
			} else if (m.distanceToSqr(lx, ly, lz) > 2.6 * 2.6) {
				if (m.tickCount % 20 == 0 || m.getNavigation().isDone()) {
					m.getNavigation().moveTo(lx, ly, lz, 1.15);
				}
			} else {
				m.getNavigation().stop();
				if (cooldown == 0) {
					cooldown = 20;
					r.ward -= Math.max(0, Config.WEAVING_BITE);
					WorldFx.particleIn(r.dim, "minecraft:large_smoke", lx, ly + 1, lz, 0.3, 0.3, 0.3, 0.01, 4);
					WorldFx.soundIn(r.dim, "minecraft:block.wool.break", "hostile", lx, ly + 1, lz, 0.9f, 0.5f);
				}
			}
			e.setValue(cooldown);
		}
	}

	/** Held: the tapestry is finished. */
	private static void hold(Run r) {
		end(r);
		DataStore.setTapestryDone(true);
		double x = r.loom.getX() + 0.5, y = r.loom.getY() + 1, z = r.loom.getZ() + 0.5;
		WorldFx.particleIn(r.dim, "extremedimensions:soul_mote", x, y, z, 3, 3, 3, 0.05, 200);
		WorldFx.particleIn(r.dim, "minecraft:end_rod", x, y + 1, z, 1, 2, 1, 0.08, 60);
		WorldFx.soundIn(r.dim, "minecraft:ui.toast.challenge_complete", "master", x, y, z, 1.0f, 0.8f);
		for (ServerPlayer p : r.level.players()) {
			if (near(p, r)) {
				Advancements.grant(p, Advancements.TAPESTRY);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.weaving.held").withStyle(ChatFormatting.LIGHT_PURPLE));
				Tapestry.open(p);
			}
		}
	}

	/** The ward broke (or the loom is gone): the last panel unravels and its thread falls at the loom. */
	private static void fail(Run r) {
		end(r);
		Tapestry.Panel last = Tapestry.Panel.byId(DataStore.lastPanel());
		if (last != null && DataStore.panels().remove(last.id)) {
			DataStore.setWeaveAgainAt(System.currentTimeMillis() + Math.max(0, Config.WEAVING_RETRY_MINUTES) * 60_000L);
			Block.popResource(r.level, r.loom.above(), new ItemStack(last.thread()));
		}
		DataStore.markDirty();
		double x = r.loom.getX() + 0.5, y = r.loom.getY() + 1, z = r.loom.getZ() + 0.5;
		WorldFx.particleIn(r.dim, "minecraft:large_smoke", x, y, z, 1, 1, 1, 0.03, 60);
		WorldFx.soundIn(r.dim, "minecraft:block.beacon.deactivate", "master", x, y, z, 1.0f, 0.5f);
		for (ServerPlayer p : r.level.players()) {
			if (near(p, r)) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.weaving.failed", last == null ? Component.empty()
						: Component.translatable(last.nameKey()), Math.max(0, Config.WEAVING_RETRY_MINUTES)).withStyle(ChatFormatting.RED));
			}
		}
	}

	/** Whatever way it ended: the bar goes and its foes fade back into the rifts they came from. */
	private static void end(Run r) {
		run = null;
		r.bar.removeAllPlayers();
		for (UUID id : r.foes.keySet()) {
			if (r.level.getEntity(id) instanceof Mob m) {
				WorldFx.particleIn(r.dim, "minecraft:reverse_portal", m.getX(), m.getY() + 1, m.getZ(), 0.3, 0.8, 0.3, 0.05, 20);
				m.discard();
			}
		}
		r.foes.clear();
	}

	/** For the tests: the Weaving ends now, held or broken. */
	public static void finishNow(boolean held) {
		Run r = run;
		if (r == null) {
			return;
		}
		if (held) {
			hold(r);
		} else {
			fail(r);
		}
	}

	public static void clear() {
		if (run != null) {
			run.bar.removeAllPlayers();
		}
		run = null;
	}
}
