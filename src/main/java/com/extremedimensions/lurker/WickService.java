package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Wicks' minds. On a dark night, where a player keeps a good deal of light about them (a lit house, a torch-lined
 * path), one to three Wicks come out of the dark and eat it:
 *
 * <ul>
 *   <li><b>SEEK</b>: each walks to the nearest light it can eat (a torch, a lantern, a lit candle) and that no soul fire
 *       guards. The cold blue light of soul torches, soul lanterns and soul campfires frightens it.</li>
 *   <li><b>EAT</b>: a moment's nibbling, flame and smoke, and the light is gone: a torch or a lantern eaten whole
 *       (where the world lets creatures change blocks, and {@code wick_eats} is on), a candle snuffed out. Fuller, it
 *       glows hotter.</li>
 *   <li><b>FLEE</b>: soul fire near it, or a blow, sends it running; struck, it does not come back.</li>
 *   <li><b>LEAVE</b>: full ({@code wick_meals}), at daybreak, after a while, or with nothing left to eat, it sinks into
 *       the ground with what it ate.</li>
 * </ul>
 *
 * Slain, it gives back the torches and lanterns it ate (and, full of fire, it bursts and singes whoever stands close).
 * It never hurts anyone otherwise; it only leaves them in the dark. Wicks are never saved.
 */
public final class WickService {
	private WickService() {}

	enum Mode { SEEK, EAT, FLEE, LEAVE }

	/** Ticks a meal takes, and how often it looks round again for light (ticks). */
	private static final int EAT_TICKS = 30, LOOK_EVERY = 40;
	/** How far round itself it looks for light, across and up or down. */
	private static final int LOOK = 12, LOOK_UP = 4;
	/** How near (centre to centre, across) it must be to a light to eat it. */
	private static final double REACH = 1.6;
	/** Seconds it goes on finding nothing to eat before it leaves. */
	private static final int HUNGRY_GIVE_UP = 15;

	static final class State {
		Mode mode = Mode.SEEK;
		UUID target;
		int ticks;
		int age;
		int limit;
		BlockPos light;
		int lightTicks;
		int idle;
		int fleeing;
		boolean struck;
		boolean anywhere;
		Vec3 away;
		/** What it ate whole (given back if it is slain), and how many lights it put out in all. */
		final List<ItemStack> eaten = new ArrayList<>();
		int meals;
		/** Lights it could not get at: never tried again. */
		final Set<BlockPos> cannot = new HashSet<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	/** Server tick each player last had Wicks come (cooldown). */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return STATES.containsKey(e.getUUID());
	}

	public static void clear() {
		STATES.clear();
		LEVELS.clear();
		LAST.clear();
	}

	// ------------------------------------------------------------------ what it eats and what it fears

	/** Whether a Wick can eat this light: a torch, a lantern, a lit candle (never soul fire, never redstone). */
	public static boolean edible(BlockState s) {
		if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN)) {
			return true;
		}
		return (s.is(BlockTags.CANDLES) || s.is(BlockTags.CANDLE_CAKES)) && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
	}

	/** Whether this is the cold blue light that frightens it. */
	public static boolean soul(BlockState s) {
		if (s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.SOUL_LANTERN) || s.is(Blocks.SOUL_FIRE)) {
			return true;
		}
		return s.is(Blocks.SOUL_CAMPFIRE) && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
	}

	/** Whether soul fire burns within {@code range} of this spot. */
	public static boolean guarded(ServerLevel level, BlockPos at, int range) {
		if (range <= 0) {
			return false;
		}
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					q.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (dx * dx + dz * dz <= range * range && Loaded.ready(level, q) && soul(level.getBlockState(q))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** How many lights a Wick could eat lie within this range of a spot (up to {@code enough}, then it stops counting). */
	public static int lights(ServerLevel level, BlockPos at, int range, int enough) {
		int n = 0;
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int dx = -range; dx <= range && n < enough; dx++) {
			for (int dz = -range; dz <= range && n < enough; dz++) {
				for (int dy = -4; dy <= 4; dy++) {
					q.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (Loaded.ready(level, q) && edible(level.getBlockState(q)) && ++n >= enough) {
						break;
					}
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ coming

	/** Whether Wicks may come to this player now: a dark night in the Overworld, dry, not on Peaceful. */
	public static boolean fits(ServerPlayer p) {
		return Config.WICK_ENABLED > 0 && Dims.OVERWORLD.equals(Dims.of(p)) && Dims.ours(p) && !p.isSpectator() && !p.isCreative()
				&& p.isAlive() && !Rules.peaceful(p.level()) && p.level().isDarkOutside() && !p.level().isRaining();
	}

	public static void tick(MinecraftServer s) {
		drive();
		// (three quarters of the way round the Lurker's own look)
		if (Config.WICK_ENABLED <= 0 || (s.getTickCount() + 300) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.WICK_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.WICK_SPAWN_CHANCE) {
				continue;
			}
			// Only where there is a good deal of light to eat
			if (lights((ServerLevel) p.level(), p.blockPosition(), 12, Math.max(1, Config.WICK_LIGHTS)) < Math.max(1, Config.WICK_LIGHTS)) {
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
	 * One to {@code wick_pack} Wicks come to eat this player's light: out of the dark (where the light is low), out of
	 * their sight, a little way off, on dry ground. {@code anywhere} (an operator's summons) lets them come in sight, in
	 * the light and by day.
	 */
	public static List<Wick> come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int want = 1 + rnd.nextInt(Math.max(1, Config.WICK_PACK));
		List<Wick> out = new ArrayList<>();
		Vec3 eye = p.getEyePosition();
		for (int i = 0; i < 40 && out.size() < want; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = (anywhere ? 4 : 8) + rnd.nextDouble() * 8;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			BlockPos feet = null;
			for (int dy = 3; dy >= -4 && feet == null; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(p.getY()) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, ModEntities.WICK.getWidth(), ModEntities.WICK.getHeight())
						&& level.getFluidState(q).isEmpty()) {
					feet = q;
				}
			}
			if (feet == null) {
				continue;
			}
			if (!anywhere) {
				if (level.getBrightness(LightLayer.BLOCK, feet) > 7) {
					continue;
				}
				Vec3 body = Vec3.atBottomCenterOf(feet).add(0, 0.4, 0);
				if (level.clip(new ClipContext(eye, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) {
					continue;
				}
			}
			Wick w = ModEntities.WICK.spawn(level, feet, EntitySpawnReason.EVENT);
			if (w != null) {
				drive(level, w, p, anywhere);
				WorldFx.particleIn(level, "minecraft:smoke", w.getX(), w.getY() + 0.2, w.getZ(), 0.2, 0.1, 0.2, 0.01, 6);
				out.add(w);
			}
		}
		return out;
	}

	private static State drive(ServerLevel level, Wick w, ServerPlayer p, boolean anywhere) {
		w.setPersistenceRequired();
		State st = new State();
		st.target = p.getUUID();
		st.anywhere = anywhere;
		st.limit = Math.max(20, Config.WICK_SECONDS) * 20;
		STATES.put(w.getUUID(), st);
		LEVELS.put(w.getUUID(), level);
		w.setLurkerPose(Lurker.POSE_WATCH);
		w.setLurkerMood(LurkerBody.MOOD_CALM);
		return st;
	}

	/** Operator command: Wicks come to eat the light near the player, wherever and whenever. */
	public static boolean summon(ServerPlayer p) {
		return Dims.ours(p) && !come(p, true).isEmpty();
	}

	// ------------------------------------------------------------------ what it does

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
			if (!(ent instanceof Wick w) || !w.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			st.age++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			boolean over = p == null || p.level() != w.level() || p.distanceToSqr(w) > 64 * 64 || st.age > st.limit
					|| (!st.anywhere && !level.isDarkOutside());
			if (over && st.mode != Mode.LEAVE) {
				startLeave(w, st);
			}
			switch (st.mode) {
				case SEEK -> seek(level, w, st, later);
				case EAT -> eat(level, w, st);
				case FLEE -> flee(w, st, p, later);
				case LEAVE -> leave(w, st, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** It goes to the nearest light it can eat; soul fire near it sends it off; with nothing left to eat, it leaves. */
	private static void seek(ServerLevel level, Wick w, State st, List<Runnable> later) {
		BlockPos fear = st.ticks % 10 == 0 ? soulNear(level, w.blockPosition(), Math.max(0, Config.WICK_FEAR_RANGE)) : null;
		if (fear != null) {
			startFlee(w, st, Vec3.atCenterOf(fear));
			return;
		}
		if (st.light != null && (!edible(level.getBlockState(st.light)) || guarded(level, st.light, Config.WICK_FEAR_RANGE))) {
			st.light = null;
		}
		if (st.light == null && st.ticks % LOOK_EVERY == 1) {
			st.light = nearest(level, w, st);
			st.lightTicks = 0;
		}
		if (st.light == null) {
			w.setLurkerPose(Lurker.POSE_WATCH);
			TribeMoves.stand(w);
			if (++st.idle > HUNGRY_GIVE_UP * 20) {
				startLeave(w, st);
			}
			return;
		}
		st.idle = 0;
		Vec3 at = Vec3.atCenterOf(st.light);
		double dx = at.x - w.getX(), dz = at.z - w.getZ();
		double up = at.y - w.getY();
		if (dx * dx + dz * dz <= REACH * REACH && up > -1.2 && up < 2.4) {
			st.mode = Mode.EAT;
			st.ticks = 0;
			w.setLurkerPose(Lurker.POSE_TAKE);
			TribeMoves.stand(w);
			return;
		}
		// A light it cannot get to in a good while: it leaves that one be
		if (++st.lightTicks > 200) {
			st.cannot.add(st.light);
			st.light = null;
			return;
		}
		w.setLurkerPose(Lurker.POSE_STALK);
		if (st.ticks % 5 == 1) {
			TribeMoves.go(w, at.x, st.light.getY(), at.z, Config.WICK_SPEED);
			TribeMoves.trimBehind(w);
		}
	}

	/** The nearest light it can eat that no soul fire guards and it has not already failed to reach, or null. */
	private static BlockPos nearest(ServerLevel level, Wick w, State st) {
		BlockPos at = w.blockPosition(), best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int dx = -LOOK; dx <= LOOK; dx++) {
			for (int dz = -LOOK; dz <= LOOK; dz++) {
				for (int dy = -LOOK_UP; dy <= LOOK_UP; dy++) {
					q.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					double d = q.distSqr(at);
					if (d >= bestD || !Loaded.ready(level, q) || !edible(level.getBlockState(q)) || st.cannot.contains(q)) {
						continue;
					}
					BlockPos cand = q.immutable();
					if (guarded(level, cand, Config.WICK_FEAR_RANGE)) {
						continue;
					}
					best = cand;
					bestD = d;
				}
			}
		}
		return best;
	}

	/** The nearest soul fire within this range, or null. */
	private static BlockPos soulNear(ServerLevel level, BlockPos at, int range) {
		if (range <= 0) {
			return null;
		}
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					q.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (dx * dx + dz * dz <= range * range && Loaded.ready(level, q) && soul(level.getBlockState(q))) {
						return q.immutable();
					}
				}
			}
		}
		return null;
	}

	/** It nibbles at the flame a moment, then the light is gone. */
	private static void eat(ServerLevel level, Wick w, State st) {
		BlockState s = level.getBlockState(st.light);
		if (!edible(s)) {
			st.mode = Mode.SEEK;
			st.light = null;
			return;
		}
		Vec3 at = Vec3.atCenterOf(st.light);
		w.getLookControl().setLookAt(at.x, at.y, at.z, 30, 30);
		String dim = level.dimension().identifier().toString();
		if (st.ticks % 4 == 0) {
			WorldFx.particleIn(dim, "minecraft:flame", at.x, at.y + 0.2, at.z, 0.08, 0.08, 0.08, 0.01, 2);
			WorldFx.particleIn(dim, "minecraft:smoke", at.x, at.y + 0.3, at.z, 0.1, 0.1, 0.1, 0.01, 2);
		}
		if (st.ticks % 8 == 0) {
			WorldFx.soundIn(dim, "minecraft:entity.generic.eat", "hostile", w.getX(), w.getY() + 0.4, w.getZ(), 0.5f, 1.8f);
		}
		if (st.ticks < EAT_TICKS) {
			return;
		}
		boolean candle = s.is(BlockTags.CANDLES) || s.is(BlockTags.CANDLE_CAKES);
		boolean ate = false;
		if (candle) {
			// a candle it snuffs out (its wax is no meal)
			level.setBlockAndUpdate(st.light, s.setValue(BlockStateProperties.LIT, false));
			ate = true;
		} else if (Config.WICK_EATS > 0 && TribeMoves.griefing(level)) {
			st.eaten.add(new ItemStack(s.is(Blocks.LANTERN) ? Items.LANTERN : Items.TORCH));
			level.removeBlock(st.light, false);
			ate = true;
		}
		if (ate) {
			st.meals++;
			WorldFx.soundIn(dim, "minecraft:block.candle.extinguish", "hostile", at.x, at.y, at.z, 1.0f, 0.8f);
			WorldFx.particleIn(dim, "minecraft:large_smoke", at.x, at.y + 0.2, at.z, 0.15, 0.15, 0.15, 0.01, 4);
			// Fuller, it glows hotter
			if (st.meals * 2 >= Math.max(1, Config.WICK_MEALS)) {
				w.setLurkerMood(LurkerBody.MOOD_HUNTER);
			}
		} else {
			// (it may not take the light away: it spits and leaves that one be)
			st.cannot.add(st.light);
			WorldFx.soundIn(dim, "minecraft:block.fire.extinguish", "hostile", at.x, at.y, at.z, 0.4f, 1.6f);
		}
		st.light = null;
		st.mode = Mode.SEEK;
		st.ticks = 0;
		w.setLurkerPose(Lurker.POSE_WATCH);
		if (st.meals >= Math.max(1, Config.WICK_MEALS)) {
			startLeave(w, st);
		}
	}

	private static void startFlee(Wick w, State st, Vec3 from) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.fleeing = 50;
		st.light = null;
		st.away = TribeMoves.away(w, from, 10);
		w.setLurkerPose(Lurker.POSE_RUN);
		w.getNavigation().stop();
		WorldFx.soundIn(w.level().dimension().identifier().toString(), "minecraft:block.fire.extinguish", "hostile", w.getX(), w.getY() + 0.3, w.getZ(),
				0.5f, 1.9f);
	}

	/** It scurries off; frightened by soul fire, it comes back to eat when it has run a while; struck, it leaves. */
	private static void flee(Wick w, State st, ServerPlayer p, List<Runnable> later) {
		if (st.away != null && st.ticks % 6 == 1) {
			TribeMoves.go(w, st.away.x, st.away.y, st.away.z, Config.WICK_SPEED * 1.4);
		}
		if (--st.fleeing > 0 && (st.away == null || w.position().distanceToSqr(st.away) > 1.5 * 1.5)) {
			return;
		}
		if (st.struck) {
			startLeave(w, st);
			return;
		}
		st.mode = Mode.SEEK;
		st.ticks = 0;
		w.setLurkerPose(Lurker.POSE_WATCH);
	}

	private static void startLeave(Wick w, State st) {
		st.mode = Mode.LEAVE;
		st.ticks = 0;
		w.getNavigation().stop();
		w.setLurkerPose(Lurker.POSE_WATCH);
	}

	/** It sinks into the ground, a curl of smoke, gone (with what it ate). */
	private static void leave(Wick w, State st, List<Runnable> later) {
		TribeMoves.stand(w);
		if (st.ticks % 4 == 0) {
			WorldFx.particleIn(w.level(), "minecraft:smoke", w.getX(), w.getY() + 0.2, w.getZ(), 0.15, 0.1, 0.15, 0.01, 3);
		}
		if (st.ticks >= 20) {
			later.add(() -> vanish(w));
		}
	}

	/** Gone: a last wisp of smoke where it stood. What it ate goes with it. */
	public static void vanish(Wick w) {
		STATES.remove(w.getUUID());
		LEVELS.remove(w.getUUID());
		if (w.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:large_smoke", w.getX(), w.getY() + 0.3, w.getZ(), 0.15, 0.2, 0.15, 0.01, 6);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.candle.extinguish", "hostile", w.getX(), w.getY(), w.getZ(), 0.8f, 0.6f);
		}
		w.discard();
	}

	/** Struck: it squeals and runs, and does not come back. */
	public static void onHurt(Wick w, DamageSource source) {
		State st = STATES.get(w.getUUID());
		if (st == null || !TribeMoves.struck(source) || st.mode == Mode.LEAVE) {
			return;
		}
		st.struck = true;
		startFlee(w, st, source.getEntity() != null ? source.getEntity().position() : w.position());
	}

	/**
	 * Slain: the torches and lanterns it ate fall where it dies, and one full of fire bursts, singeing whoever stands
	 * close.
	 */
	public static void onDeath(Wick w, Entity killer) {
		State st = STATES.remove(w.getUUID());
		LEVELS.remove(w.getUUID());
		if (st == null || !(w.level() instanceof ServerLevel level)) {
			return;
		}
		for (ItemStack stack : st.eaten) {
			w.spawnAtLocation(level, stack);
		}
		st.eaten.clear();
		if (st.meals >= 3 && Config.WICK_BURST_SECONDS > 0) {
			WorldFx.particleIn(level, "minecraft:flame", w.getX(), w.getY() + 0.3, w.getZ(), 0.4, 0.3, 0.4, 0.05, 24);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:item.firecharge.use", "hostile", w.getX(), w.getY(), w.getZ(), 0.8f, 1.4f);
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(w) <= 2.5 * 2.5) {
					p.igniteForSeconds(Config.WICK_BURST_SECONDS);
				}
			}
		}
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives a Wick already in the world near this player (it eats whenever, day or night). */
	public static void adopt(ServerLevel level, Wick w, ServerPlayer p) {
		drive(level, w, p, true);
	}

	/** The mode a driven Wick is in ("" if it is not driven). */
	public static String mode(Wick w) {
		State st = STATES.get(w.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** How many lights it has put out. */
	public static int meals(Wick w) {
		State st = STATES.get(w.getUUID());
		return st == null ? 0 : st.meals;
	}
}
