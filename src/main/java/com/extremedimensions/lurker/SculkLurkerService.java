package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * The Sculk Lurker's mind. It comes to a player in the Deep Dark, near the shriekers that can call up the Warden, and
 * never strikes them. It works for the Warden:
 *
 * <ul>
 *   <li><b>BECKON</b>: it keeps the player company at a little distance and now and then scurries to a shrieker near
 *       them and rattles it: the shrieker shrieks as if the player had set it off, and the Warden draws nearer to waking
 *       (the game's own warnings, and their own pause between them, still hold).</li>
 *   <li><b>LURE</b>: once a Warden is up near the player, it fetches it. It goes to the Warden and leads it to the
 *       player, clicking all the way, the Warden following where it hears it; it waits for the Warden when it falls
 *       behind. Once both are near the player, it sets the Warden's anger on them, and sinks into the sculk.</li>
 * </ul>
 *
 * The Warden spares it (see {@code WardenSparesMixin}): it never targets it, never hears it and does it no harm. A
 * player can kill it; nothing else stops it. It walks as the tribe does, steadily and without turning on the spot.
 * Sculk Lurkers are never saved: one not driven from here dissolves.
 */
public final class SculkLurkerService {
	private SculkLurkerService() {}

	enum Mode { BECKON, LURE }

	/** It keeps the player company between these distances (it closes in past the far one, to the middle). */
	private static final double KEEP_NEAR = 6, KEEP_FAR = 14;
	/** Within this distance of the player (and the Warden near them) it sets the Warden on them. */
	private static final double DELIVER = 6, DELIVER_WARDEN = 16;
	/** It fetches a Warden from this near, and waits for it when it falls this far behind. */
	private static final double FETCHED = 5, BEHIND = 12;
	/** Ticks it scratches at a shrieker before it shrieks, and it tries to reach one before it gives that one up. */
	private static final int SCRATCH = 12, REACH_GIVE_UP = 200;
	/** Anger it sets on the player in the Warden (the game's own "angry" begins at 80). */
	private static final int ANGER = 100;
	/** The shriekers near a player it looks for when it comes, a little further than those it rattles. */
	private static final int COME_RANGE = 24;

	static final class State {
		Mode mode = Mode.BECKON;
		UUID target;
		int ticks;
		int limit;
		int rattleIn = 40;
		/** The shrieker it is going to, the spot beside it, and since when (its age then). */
		BlockPos shrieker;
		BlockPos beside;
		int goingSince;
		int scratch;
		int rattles;
		boolean closing;
		UUID warden;
		boolean leading;
		boolean delivered;
		int goneIn = -1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	/** Server tick each player last had a Sculk Lurker come (cooldown). */
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

	/** Whether one may come to this player: in the Deep Dark of the Overworld, shriekers that can call the Warden near. */
	public static boolean fits(ServerPlayer p) {
		if (Config.SCULK_LURKER_ENABLED <= 0 || !Dims.OVERWORLD.equals(Dims.of(p)) || !Dims.ours(p) || p.isSpectator() || p.isCreative()
				|| !p.isAlive() || Rules.peaceful(p.level()) || !(p.level() instanceof ServerLevel level)) {
			return false;
		}
		return level.getBiome(p.blockPosition()).is(Biomes.DEEP_DARK) && !shriekers(level, p.blockPosition(), COME_RANGE).isEmpty();
	}

	/** The shriekers that can call up the Warden within this range, nearest first (only in chunks already loaded). */
	public static List<BlockPos> shriekers(ServerLevel level, BlockPos around, int range) {
		List<BlockPos> out = new ArrayList<>();
		int reach = (range >> 4) + 1;
		int cx = around.getX() >> 4, cz = around.getZ() >> 4;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockState s = be.getBlockState();
					if (be instanceof SculkShriekerBlockEntity && be.getBlockPos().distSqr(around) <= (double) range * range
							&& s.hasProperty(SculkShriekerBlock.CAN_SUMMON) && s.getValue(SculkShriekerBlock.CAN_SUMMON)) {
						out.add(be.getBlockPos());
					}
				}
			}
		}
		out.sort(Comparator.comparingDouble(q -> q.distSqr(around)));
		return out;
	}

	public static void tick(MinecraftServer s) {
		drive();
		// (a quarter of a cycle off the Lurker's look, and the Cave Lurker's)
		if (Config.SCULK_LURKER_ENABLED <= 0 || (s.getTickCount() + 100) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (after(p) || !fits(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.SCULK_LURKER_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.SCULK_LURKER_SPAWN_CHANCE) {
				continue;
			}
			if (come(p) != null) {
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

	/** One comes to this player, some way off on ground it fits and from where it can walk to them. Null if none could. */
	public static SculkLurker come(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int tried = 0;
		for (int i = 0; i < 40 && tried < 3; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = 8 + rnd.nextDouble() * 8;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			BlockPos feet = null;
			for (int dy = 3; dy >= -4 && feet == null; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(p.getY()) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, ModEntities.SCULK_LURKER.getWidth(), ModEntities.SCULK_LURKER.getHeight())) {
					feet = q;
				}
			}
			if (feet == null) {
				continue;
			}
			tried++;
			SculkLurker sl = setDown(level, feet, p);
			if (sl != null) {
				return sl;
			}
		}
		return null;
	}

	/**
	 * A Sculk Lurker set down on these feet to serve near this player, if it could walk to them from there (else it is
	 * taken away again and this is null).
	 */
	public static SculkLurker setDown(ServerLevel level, BlockPos feet, ServerPlayer p) {
		SculkLurker sl = ModEntities.SCULK_LURKER.spawn(level, feet, EntitySpawnReason.EVENT);
		if (sl == null) {
			return null;
		}
		if (!TribeMoves.canWalkTo(sl, p)) {
			sl.discard();
			return null;
		}
		take(level, sl, p, "beckon");
		WorldFx.particleIn(level, "minecraft:sculk_soul", sl.getX(), sl.getY() + 0.5, sl.getZ(), 0.3, 0.3, 0.3, 0.02, 8);
		return sl;
	}

	/** Operator command: one comes near the player, wherever they are in a world of the mod. */
	public static boolean summon(ServerPlayer p) {
		return Dims.ours(p) && come(p) != null;
	}

	/** From now on driven from here, for this player, in this mode. */
	private static State take(ServerLevel level, SculkLurker sl, ServerPlayer p, String mode) {
		State st = new State();
		st.target = p.getUUID();
		st.limit = Math.max(20, Config.SCULK_LURKER_SECONDS) * 20;
		STATES.put(sl.getUUID(), st);
		LEVELS.put(sl.getUUID(), level);
		sl.setPersistenceRequired();
		sl.setLurkerPose(Lurker.POSE_WATCH);
		sl.setLurkerMood(LurkerBody.MOOD_CALM);
		if ("lure".equals(mode)) {
			st.mode = Mode.LURE;
		}
		return st;
	}

	// ------------------------------------------------------------------ what it does

	private static void drive() {
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, State> e = it.next();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof SculkLurker sl) || !sl.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != sl.level() || p.distanceToSqr(sl) > 80 * 80 || p.isDeadOrDying() || p.isCreative() || p.isSpectator()
					|| st.ticks > st.limit) {
				later.add(() -> vanish(sl));
				continue;
			}
			if (st.goneIn >= 0) {
				// its work done, it lingers a moment and sinks into the sculk
				TribeMoves.stand(sl);
				if (st.goneIn-- == 0) {
					later.add(() -> vanish(sl));
				}
				continue;
			}
			Warden w = warden(level, p, st);
			if (w != null && st.mode == Mode.BECKON) {
				st.mode = Mode.LURE;
				st.warden = w.getUUID();
				st.leading = false;
				st.shrieker = null;
				WorldFx.soundIn(Dims.fullId(p), "extremedimensions:sculk_lurker.call", "hostile", sl.getX(), sl.getY() + 0.6, sl.getZ(), 1.6f, 1.0f);
			} else if (w == null && st.mode == Mode.LURE) {
				// the Warden went back down: it calls it up again
				st.mode = Mode.BECKON;
				st.rattleIn = 40;
			}
			if (st.mode == Mode.LURE && w != null) {
				if (st.ticks % 20 == 0) {
					com.extremedimensions.journal.Journal.unlock(p, com.extremedimensions.data.DataStore.get(p.getUUID()),
							com.extremedimensions.journal.JournalEntry.HERALD);
				}
				lure(sl, st, p, w);
			} else {
				beckon(level, sl, st, p);
			}
		}
		later.forEach(Runnable::run);
	}

	/** The Warden near this player it works for (the one it fetched, while it lasts; else the nearest), or null. */
	private static Warden warden(ServerLevel level, ServerPlayer p, State st) {
		if (st.warden != null && level.getEntity(st.warden) instanceof Warden w && w.isAlive() && w.distanceToSqr(p) <= sq(Config.SCULK_LURKER_LURE_RANGE)) {
			return w;
		}
		double r = Math.max(8, Config.SCULK_LURKER_LURE_RANGE);
		return level.getEntitiesOfClass(Warden.class, p.getBoundingBox().inflate(r), w -> w.isAlive() && w.distanceToSqr(p) <= r * r).stream()
				.min(Comparator.comparingDouble(w -> w.distanceToSqr(p))).orElse(null);
	}

	private static double sq(double d) {
		return d * d;
	}

	/**
	 * It keeps the player company, a little way off, and now and then scurries to a shrieker near them and rattles it.
	 * It never strikes them.
	 */
	private static void beckon(ServerLevel level, SculkLurker sl, State st, ServerPlayer p) {
		sl.setLurkerMood(LurkerBody.MOOD_CALM);
		if (st.ticks % 70 == 0) {
			// a tell: the dry clicks of something small on the sculk
			WorldFx.soundIn(Dims.fullId(p), "extremedimensions:sculk_lurker.chirp", "hostile", sl.getX(), sl.getY() + 0.6, sl.getZ(), 0.8f, 1.0f);
		}
		if (st.shrieker != null) {
			BlockState s = level.getBlockState(st.shrieker);
			if (!(level.getBlockEntity(st.shrieker) instanceof SculkShriekerBlockEntity) || sl.tickCount - st.goingSince > REACH_GIVE_UP) {
				// gone, or out of its way: another, later
				st.shrieker = null;
				st.rattleIn = 100;
				return;
			}
			Vec3 spot = Vec3.atBottomCenterOf(st.beside);
			double dx = spot.x - sl.getX(), dz = spot.z - sl.getZ();
			double flat = Math.sqrt(dx * dx + dz * dz);
			if (flat <= 1.0 && Math.abs(spot.y - sl.getY()) < 1.2) {
				// there: it scratches at the shrieker, then it shrieks
				TribeMoves.stand(sl);
				sl.getLookControl().setLookAt(st.shrieker.getX() + 0.5, st.shrieker.getY() + 0.5, st.shrieker.getZ() + 0.5, 30, 30);
				sl.setLurkerPose(Lurker.POSE_TAKE);
				if (st.scratch % 4 == 0) {
					WorldFx.particleIn(level, "minecraft:sculk_soul", st.shrieker.getX() + 0.5, st.shrieker.getY() + 0.7, st.shrieker.getZ() + 0.5,
							0.2, 0.1, 0.2, 0.01, 2);
				}
				if (st.scratch == 0) {
					// claws on the shrieker's bone, a dry rattle
					WorldFx.soundIn(Dims.fullId(p), "extremedimensions:sculk_lurker.rattle", "hostile", st.shrieker.getX() + 0.5, st.shrieker.getY() + 0.6,
							st.shrieker.getZ() + 0.5, 1.2f, 1.0f);
				}
				if (++st.scratch >= SCRATCH) {
					if (level.getBlockEntity(st.shrieker) instanceof SculkShriekerBlockEntity shrieker && s.getValue(SculkShriekerBlock.CAN_SUMMON)) {
						shrieker.tryShriek(level, p);
						st.rattles++;
						// the page on the Warden's herald
						com.extremedimensions.journal.Journal.unlock(p, com.extremedimensions.data.DataStore.get(p.getUUID()),
								com.extremedimensions.journal.JournalEntry.HERALD);
					}
					sl.setLurkerPose(Lurker.POSE_WATCH);
					st.shrieker = null;
					st.rattleIn = Math.max(3, Config.SCULK_LURKER_RATTLE_SECONDS) * 20;
				}
				return;
			}
			sl.setLurkerPose(Lurker.POSE_RUN);
			if (flat < 2.5 && TribeMoves.clear(level, sl, spot)) {
				// the last steps straight onto the spot (a route ends a block short of where it was asked to go)
				sl.getNavigation().stop();
				if (flat > 0.3) {
					sl.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
				}
			} else if (st.ticks % 5 == 0) {
				TribeMoves.go(sl, spot.x, spot.y, spot.z, 1.2);
				TribeMoves.trimBehind(sl);
			}
			return;
		}
		if (--st.rattleIn <= 0) {
			if (!pickShrieker(level, sl, st, p)) {
				st.rattleIn = 100;
			}
			return;
		}
		// Company: it closes in when the player is far, to the middle of its distance, and otherwise stands and watches
		double d = sl.distanceTo(p);
		if (d > KEEP_FAR) {
			st.closing = true;
		} else if (d < (KEEP_NEAR + KEEP_FAR) / 2) {
			st.closing = false;
		}
		if (st.closing) {
			sl.setLurkerPose(Lurker.POSE_STALK);
			if (st.ticks % 10 == 0) {
				TribeMoves.go(sl, p.getX(), p.getY(), p.getZ(), 1.0);
				TribeMoves.trimBehind(sl);
			}
		} else {
			sl.setLurkerPose(Lurker.POSE_WATCH);
			TribeMoves.stand(sl);
			sl.getLookControl().setLookAt(p, 10, 10);
		}
	}

	/** The nearest shrieker near the player, not shrieking now, with a spot beside it its body fits. */
	private static boolean pickShrieker(ServerLevel level, SculkLurker sl, State st, ServerPlayer p) {
		for (BlockPos q : shriekers(level, p.blockPosition(), Math.max(4, Config.SCULK_LURKER_SHRIEKER_RANGE))) {
			BlockState s = level.getBlockState(q);
			if (s.hasProperty(SculkShriekerBlock.SHRIEKING) && s.getValue(SculkShriekerBlock.SHRIEKING)) {
				continue;
			}
			// beside it on one of its four sides (on its level or a step down), the side nearest the lurker
			BlockPos best = null;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				for (int dy = 0; dy >= -1; dy--) {
					BlockPos b = q.relative(dir).above(dy);
					if (TribeMoves.roomFor(level, b, sl)) {
						if (best == null || b.distSqr(sl.blockPosition()) < best.distSqr(sl.blockPosition())) {
							best = b;
						}
						break;
					}
				}
			}
			if (best != null) {
				st.shrieker = q;
				st.beside = best;
				st.goingSince = sl.tickCount;
				st.scratch = 0;
				return true;
			}
		}
		return false;
	}

	/**
	 * It fetches the Warden and leads it to the player: to the Warden first; then towards the player, setting the
	 * Warden's ear on itself as it goes (the Warden walks to where it heard something), and waiting when the Warden falls
	 * behind. Near the player, with the Warden near too, it sets the Warden's anger on them.
	 */
	private static void lure(SculkLurker sl, State st, ServerPlayer p, Warden w) {
		sl.setLurkerMood(LurkerBody.MOOD_THIEF);
		if (w.getEntityAngryAt().filter(e -> e == p).isPresent()) {
			// it is on them already: its work is done
			done(sl, st, p);
			return;
		}
		double toPlayer = sl.distanceTo(p), wardenToPlayer = w.distanceTo(p), toWarden = sl.distanceTo(w);
		if (toPlayer <= DELIVER && wardenToPlayer <= DELIVER_WARDEN) {
			w.increaseAngerAt(p, ANGER, true);
			st.delivered = true;
			done(sl, st, p);
			return;
		}
		if (!st.leading) {
			if (toWarden <= FETCHED) {
				st.leading = true;
			} else {
				sl.setLurkerPose(Lurker.POSE_RUN);
				if (st.ticks % 5 == 0) {
					TribeMoves.go(sl, w.getX(), w.getY(), w.getZ(), 1.25);
					TribeMoves.trimBehind(sl);
				}
				return;
			}
		}
		// Leading: the Warden follows where it hears it
		if (st.ticks % 10 == 0) {
			WardenAi.setDisturbanceLocation(w, sl.blockPosition());
			WorldFx.soundIn(Dims.fullId(p), "extremedimensions:sculk_lurker.pulse", "hostile", sl.getX(), sl.getY() + 0.6, sl.getZ(), 1.0f, 1.0f);
		}
		if (toWarden > BEHIND) {
			// too far ahead: it waits, looking back
			sl.setLurkerPose(Lurker.POSE_WATCH);
			TribeMoves.stand(sl);
			sl.getLookControl().setLookAt(w, 20, 20);
			return;
		}
		sl.setLurkerPose(Lurker.POSE_RUN);
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(sl, p, 1.1);
			TribeMoves.trimBehind(sl);
		}
	}

	private static void done(SculkLurker sl, State st, ServerPlayer p) {
		st.goneIn = 30;
		sl.setLurkerPose(Lurker.POSE_WATCH);
		TribeMoves.stand(sl);
		WorldFx.soundIn(Dims.fullId(p), "minecraft:block.sculk_catalyst.bloom", "hostile", sl.getX(), sl.getY() + 0.4, sl.getZ(), 1.0f, 1.2f);
	}

	/** It sinks into the sculk: a bloom of souls, and it is gone. */
	public static void vanish(SculkLurker sl) {
		STATES.remove(sl.getUUID());
		LEVELS.remove(sl.getUUID());
		if (sl.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:sculk_soul", sl.getX(), sl.getY() + 0.5, sl.getZ(), 0.3, 0.4, 0.3, 0.03, 14);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.sculk.spread", "hostile", sl.getX(), sl.getY(), sl.getZ(), 1.0f, 0.8f);
		}
		sl.discard();
	}

	public static void onDeath(SculkLurker sl) {
		STATES.remove(sl.getUUID());
		LEVELS.remove(sl.getUUID());
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives one already in the world for this player: "beckon" (rattles the shriekers) or "lure" (fetches the Warden). */
	public static void adopt(ServerLevel level, SculkLurker sl, ServerPlayer p, String mode) {
		take(level, sl, p, mode);
	}

	public static String mode(SculkLurker sl) {
		State st = STATES.get(sl.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** How many shriekers it rattled, and whether it set a Warden on its player. */
	public static int rattles(SculkLurker sl) {
		State st = STATES.get(sl.getUUID());
		return st == null ? 0 : st.rattles;
	}

	public static boolean delivered(SculkLurker sl) {
		State st = STATES.get(sl.getUUID());
		return st != null && st.delivered;
	}
}
