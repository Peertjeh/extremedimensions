package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Threadbiters' minds. In the Seam a pack of them (two to {@code threadbiter_pack}) comes scurrying over the
 * islands for a visitor, more often once the visitor's thread runs thin:
 *
 * <ul>
 *   <li><b>SKITTER</b>: they run at their prey, glowing the blue of a thief.</li>
 *   <li><b>BITE</b>: close by, each gnaws at the visitor's thread: every bite takes {@code threadbiter_bite} seconds
 *       of the time that holds them in the Seam (a strand of gold drawn out of them into its mouth), but never below
 *       {@code threadbiter_floor} seconds.</li>
 *   <li><b>FLEE</b>: after {@code threadbiter_bites} bites (or struck) it runs off with what it ate, trailing gold, and
 *       when it has run {@code threadbiter_flee_seconds} it is gone, and the time with it.</li>
 *   <li><b>LEAVE</b>: with their prey gone from the Seam, or after a while, they sink into the stone.</li>
 * </ul>
 *
 * Slain, a Threadbiter gives back every second it ate to the one it ate them from. They never hurt anyone otherwise.
 * Threadbiters are never saved.
 */
public final class ThreadbiterService {
	private ThreadbiterService() {}

	enum Mode { SKITTER, BITE, FLEE, LEAVE }

	/** How near (centre to centre, across) it must be to bite, and how often it bites (ticks). */
	private static final double REACH = 1.7;
	private static final int BITE_EVERY = 20;
	/** Seconds it runs after its prey without getting at them before it gives up. */
	private static final int GIVE_UP_SECONDS = 25;
	/** Seconds a pack stays at most. */
	private static final int STAY_SECONDS = 120;
	/** The thread's gold, drawn out of the prey into its mouth. */
	private static final String GOLD = "minecraft:dust{color:[1.0,0.82,0.32],scale:0.7}";

	static final class State {
		Mode mode = Mode.SKITTER;
		UUID target;
		int ticks;
		int age;
		int limit;
		int chasing;
		int bites;
		boolean anywhere;
		boolean told;
		/** Seconds of the thread it ate. */
		double eaten;
		Vec3 away;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return STATES.containsKey(e.getUUID());
	}

	public static void clear() {
		STATES.clear();
		LEVELS.clear();
		LAST.clear();
	}

	// ------------------------------------------------------------------ coming

	/** Whether a pack may come for this visitor now: in the Seam, on their own thread, not on Peaceful, not kin. */
	public static boolean fits(ServerPlayer p) {
		if (Config.THREADBITER_ENABLED <= 0 || !Dims.SEAM.equals(Dims.of(p)) || p.isSpectator() || p.isCreative() || !p.isAlive()
				|| Rules.peaceful(p.level()) || com.extremedimensions.tribe.Standing.of(p) == com.extremedimensions.tribe.Standing.Level.KIN) {
			return false;
		}
		PlayerData d = DataStore.get(p.getUUID());
		return d.inSeam && d.seamPull <= 0 && d.seamLeft > Math.max(1, Config.THREADBITER_FLOOR) + Config.THREADBITER_BITE
				&& !(com.extremedimensions.mending.MendingService.active() && com.extremedimensions.mending.MendingService.inArena(p));
	}

	public static void tick(MinecraftServer s) {
		drive();
		if (Config.THREADBITER_ENABLED <= 0 || (s.getTickCount() + 150) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.THREADBITER_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			// A thin thread draws them: twice as likely then
			double chance = Config.THREADBITER_SPAWN_CHANCE * (com.extremedimensions.seam.SeamFauna.thin(p) ? 2 : 1);
			if (rnd.nextDouble() >= chance) {
				continue;
			}
			if (!come(p, false).isEmpty()) {
				LAST.put(p.getUUID(), (long) s.getTickCount());
			}
		}
	}

	private static boolean after(ServerPlayer p) {
		for (State st : STATES.values()) {
			if (p.getUUID().equals(st.target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A pack comes for this visitor: out of their sight, 8-16 blocks off on ground they can run to them over.
	 * {@code anywhere} (an operator's summons) lets them come closer and in sight.
	 */
	public static List<Threadbiter> come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int want = 2 + rnd.nextInt(Math.max(1, Config.THREADBITER_PACK - 1));
		List<Threadbiter> out = new ArrayList<>();
		Vec3 eye = p.getEyePosition();
		for (int i = 0; i < 48 && out.size() < want; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = (anywhere ? 3 : 8) + rnd.nextDouble() * (anywhere ? 4 : 8);
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			BlockPos feet = null;
			for (int dy = 3; dy >= -4 && feet == null; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(p.getY()) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, ModEntities.THREADBITER.getWidth(), ModEntities.THREADBITER.getHeight())
						&& level.getFluidState(q).isEmpty()) {
					feet = q;
				}
			}
			if (feet == null) {
				continue;
			}
			if (!anywhere) {
				Vec3 body = Vec3.atBottomCenterOf(feet).add(0, 0.4, 0);
				if (level.clip(new ClipContext(eye, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS
						&& p.getViewVector(1.0f).dot(body.subtract(eye).normalize()) > 0.3) {
					continue;
				}
			}
			Threadbiter b = ModEntities.THREADBITER.spawn(level, feet, EntitySpawnReason.EVENT);
			if (b == null) {
				continue;
			}
			// (only where they can run to their prey: not on another island)
			if (!anywhere && !TribeMoves.canWalkTo(b, p)) {
				b.discard();
				continue;
			}
			drive(level, b, p, anywhere);
			WorldFx.particleIn(level, "extremedimensions:void_mote", b.getX(), b.getY() + 0.3, b.getZ(), 0.2, 0.2, 0.2, 0.0, 8);
			out.add(b);
		}
		if (!out.isEmpty()) {
			Threadbiter b = out.get(0);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.silverfish.ambient", "hostile", b.getX(), b.getY(), b.getZ(),
					1.2f, 0.7f);
		}
		return out;
	}

	private static State drive(ServerLevel level, Threadbiter b, ServerPlayer p, boolean anywhere) {
		b.setPersistenceRequired();
		State st = new State();
		st.target = p.getUUID();
		st.anywhere = anywhere;
		st.limit = STAY_SECONDS * 20;
		STATES.put(b.getUUID(), st);
		LEVELS.put(b.getUUID(), level);
		b.setLurkerPose(Lurker.POSE_STALK);
		b.setLurkerMood(LurkerBody.MOOD_THIEF);
		return st;
	}

	/** Operator command: a pack comes for the player, near and in sight. */
	public static boolean summon(ServerPlayer p) {
		return Dims.ours(p) && !come(p, true).isEmpty();
	}

	// ------------------------------------------------------------------ what they do

	private static void drive() {
		if (STATES.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, State> e = it.next();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof Threadbiter b) || !b.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			st.age++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			boolean gone = p == null || p.level() != b.level() || p.distanceToSqr(b) > 64 * 64 || p.isSpectator() || p.isDeadOrDying()
					|| (!st.anywhere && !DataStore.get(p.getUUID()).inSeam);
			if ((gone || st.age > st.limit) && st.mode != Mode.LEAVE && st.mode != Mode.FLEE) {
				if (st.eaten > 0 && !gone) {
					startFlee(b, st, p.position());
				} else {
					startLeave(b, st);
				}
			}
			switch (st.mode) {
				case SKITTER -> skitter(b, st, p);
				case BITE -> bite(level, b, st, p);
				case FLEE -> flee(b, st, later);
				case LEAVE -> leave(b, st, later);
			}
		}
		later.forEach(Runnable::run);
	}

	private static boolean close(Threadbiter b, ServerPlayer p, double reach) {
		double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ();
		return dx * dx + dz * dz <= reach * reach && Math.abs(p.getY() - b.getY()) < 1.8;
	}

	/** It runs at its prey; close enough, it bites; never getting at them, it gives up. */
	private static void skitter(Threadbiter b, State st, ServerPlayer p) {
		if (p == null) {
			return;
		}
		b.setLurkerPose(Lurker.POSE_STALK);
		if (close(b, p, REACH)) {
			st.mode = Mode.BITE;
			st.ticks = BITE_EVERY - 6;
			st.chasing = 0;
			return;
		}
		if (++st.chasing > GIVE_UP_SECONDS * 20) {
			if (st.eaten > 0) {
				startFlee(b, st, p.position());
			} else {
				startLeave(b, st);
			}
			return;
		}
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(b, p, Config.THREADBITER_SPEED);
			TribeMoves.trimBehind(b);
		}
	}

	/** At its prey's heels, it gnaws at their thread: a strand of gold drawn out of them, seconds of their time gone. */
	private static void bite(ServerLevel level, Threadbiter b, State st, ServerPlayer p) {
		if (p == null) {
			return;
		}
		if (!close(b, p, REACH + 0.8)) {
			st.mode = Mode.SKITTER;
			st.ticks = 0;
			return;
		}
		b.setLurkerPose(Lurker.POSE_TAKE);
		b.getLookControl().setLookAt(p, 40, 40);
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(b, p, Config.THREADBITER_SPEED);
		}
		if (st.ticks % BITE_EVERY != 0) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		double floor = Math.max(1, Config.THREADBITER_FLOOR);
		double take = (st.anywhere || d.inSeam) ? Math.min(Math.max(0, Config.THREADBITER_BITE), d.seamLeft - floor) : 0;
		if (take <= 0) {
			// Nothing left it may take: off it goes with what it has
			startFlee(b, st, p.position());
			return;
		}
		d.seamLeft -= take;
		st.eaten += take;
		st.bites++;
		TribeMoves.reachLine(b, p, GOLD);
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "minecraft:block.wool.break", "hostile", p.getX(), p.getY() + 1, p.getZ(), 0.9f, 1.4f);
		WorldFx.soundIn(dim, "minecraft:entity.generic.eat", "hostile", b.getX(), b.getY() + 0.4, b.getZ(), 0.6f, 1.7f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.threadbiter.bite", (int) Math.round(take)).withStyle(net.minecraft.ChatFormatting.GOLD), true);
		if (!st.told) {
			st.told = true;
			com.extremedimensions.journal.Journal.unlock(p, d, com.extremedimensions.journal.JournalEntry.TRIBE);
		}
		Sync.state(p, d);
		DataStore.markDirty();
		if (st.bites >= Math.max(1, Config.THREADBITER_BITES)) {
			startFlee(b, st, p.position());
		}
	}

	private static void startFlee(Threadbiter b, State st, Vec3 from) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.away = TribeMoves.away(b, from, 16);
		b.setLurkerPose(Lurker.POSE_RUN);
		b.getNavigation().stop();
		WorldFx.soundIn(b.level().dimension().identifier().toString(), "minecraft:entity.silverfish.hurt", "hostile", b.getX(), b.getY() + 0.3, b.getZ(),
				0.8f, 1.6f);
	}

	/** It runs, trailing the gold it ate; when it has run long enough it is gone, and the time with it. */
	private static void flee(Threadbiter b, State st, List<Runnable> later) {
		if (st.away != null && st.ticks % 6 == 1) {
			TribeMoves.go(b, st.away.x, st.away.y, st.away.z, Config.THREADBITER_SPEED * 1.25);
			if (b.position().distanceToSqr(st.away) < 2.0 * 2.0) {
				st.away = TribeMoves.away(b, b.position().add(b.getLookAngle()), 12);
			}
		}
		if (st.eaten > 0 && st.ticks % 3 == 0) {
			WorldFx.particleIn(b.level(), GOLD, b.getX(), b.getY() + 0.4, b.getZ(), 0.1, 0.1, 0.1, 0.0, 1);
		}
		if (st.ticks >= Math.max(3, Config.THREADBITER_FLEE_SECONDS) * 20) {
			later.add(() -> vanish(b));
		}
	}

	private static void startLeave(Threadbiter b, State st) {
		st.mode = Mode.LEAVE;
		st.ticks = 0;
		b.getNavigation().stop();
		b.setLurkerPose(Lurker.POSE_WATCH);
		b.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	/** It sinks into the stone. */
	private static void leave(Threadbiter b, State st, List<Runnable> later) {
		TribeMoves.stand(b);
		if (st.ticks % 4 == 0) {
			WorldFx.particleIn(b.level(), "extremedimensions:void_mote", b.getX(), b.getY() + 0.2, b.getZ(), 0.15, 0.1, 0.15, 0.0, 2);
		}
		if (st.ticks >= 20) {
			later.add(() -> vanish(b));
		}
	}

	/** Gone into the stone, with whatever it ate. */
	public static void vanish(Threadbiter b) {
		STATES.remove(b.getUUID());
		LEVELS.remove(b.getUUID());
		if (b.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "extremedimensions:void_mote", b.getX(), b.getY() + 0.3, b.getZ(), 0.2, 0.2, 0.2, 0.0, 10);
			WorldFx.soundIn(level.dimension().identifier().toString(), "extremedimensions:lurker_vanish", "hostile", b.getX(), b.getY(), b.getZ(), 0.6f, 1.6f);
		}
		b.discard();
	}

	/** Struck: it hops back from the blow and, with anything in it, bolts. */
	public static void onHurt(Threadbiter b, DamageSource source) {
		State st = STATES.get(b.getUUID());
		if (st == null || !TribeMoves.struck(source) || st.mode == Mode.LEAVE || st.mode == Mode.FLEE) {
			return;
		}
		Vec3 from = source.getEntity() != null ? source.getEntity().position() : b.position();
		if (st.eaten > 0) {
			startFlee(b, st, from);
		}
	}

	/** Slain: every second it ate goes back to the one it ate them from. */
	public static void onDeath(Threadbiter b, Entity killer) {
		State st = STATES.remove(b.getUUID());
		LEVELS.remove(b.getUUID());
		if (st == null || st.eaten <= 0 || !(b.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		WorldFx.particleIn(level, GOLD, b.getX(), b.getY() + 0.4, b.getZ(), 0.3, 0.3, 0.3, 0.0, 16);
		if (p == null) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		if (!st.anywhere && !d.inSeam) {
			return;
		}
		d.seamLeft += st.eaten;
		d.seamMax = Math.max(d.seamMax, d.seamLeft);
		if (p.level() == level && p.distanceToSqr(b) < 48 * 48) {
			TribeMoves.reachLine(b, p, GOLD);
		}
		WorldFx.soundFor(p, "minecraft:block.amethyst_block.chime", "players", p.getX(), p.getY() + 1, p.getZ(), 1.0f, 1.3f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.threadbiter.mended", (int) Math.round(st.eaten))
				.withStyle(net.minecraft.ChatFormatting.GOLD), true);
		st.eaten = 0;
		Sync.state(p, d);
		DataStore.markDirty();
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives a Threadbiter already in the world after this player (it bites whether they are in the Seam or not). */
	public static void adopt(ServerLevel level, Threadbiter b, ServerPlayer p) {
		drive(level, b, p, true);
	}

	public static String mode(Threadbiter b) {
		State st = STATES.get(b.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** Seconds of the thread it has eaten. */
	public static double eaten(Threadbiter b) {
		State st = STATES.get(b.getUUID());
		return st == null ? 0 : st.eaten;
	}
}
