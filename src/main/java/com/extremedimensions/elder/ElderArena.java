package com.extremedimensions.elder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import com.extremedimensions.config.Config;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * An Elder's arena. As an Elder takes shape the ground round it is remade for the fight: blocks appear out of nothing
 * ten to fifteen blocks away and fly to their places, inlaying a sigil into the ground, raising towers with lanterns
 * round it and hanging broken islands over it, each Elder's in its own stone and light (see {@link ArenaDesign}). The
 * Matriarch's blocks come up out of the earth, the Ferryman's are swept in on a whirl, the Weaver's are lowered from
 * high above. Chains and threads strung between the towers are drawn out once the towers stand. When the fight is over
 * the arena stands a while ({@code elder_arena_linger_seconds}), then comes apart the way it came, block by block
 * flying off into nothing, and the ground is as it was.
 *
 * <p>Blocks are only built into air and plants; the ground is only inlaid where the world may be torn up
 * ({@code elder_griefing} and the mobGriefing rule), and never where a block holds data. A block of an arena a player
 * breaks drops nothing, and nothing an Elder tears out of one is thrown about. On stopping, everything is put back at
 * once. The blocks fly as {@link BlockFlight}s, as many at once as {@code elder_arena_fly_max} allows and only where a
 * player is near enough to see them; the rest simply appear (and vanish) in their places. A block whose part of the
 * world is not loaded when its turn comes (no one near; a great arena reaches past the view) waits until it is, so the
 * far towers and islands go up as someone comes near them; the world is never loaded just to put a block up or take
 * one down.
 */
public final class ElderArena {
	private ElderArena() {}

	/** How a piece goes in: into air (and plants), or inlaid into the ground in place of its top block. */
	static final int AIR = 0;
	static final int GROUND = 1;

	/** One block of an arena: where it goes, what it is, how it goes in, and what was there. */
	static final class Piece {
		final BlockPos pos;
		final BlockState state;
		final int kind;
		/** For an inlaid piece, the ground block it replaces (it goes in only while that is still there). */
		final BlockState expect;
		BlockState original;
		boolean placed;
		/** The structure it belongs to (a tower, an island, a core...), or -1. */
		int group = -1;

		Piece(BlockPos pos, BlockState state, int kind, BlockState expect) {
			this.pos = pos;
			this.state = state;
			this.kind = kind;
			this.expect = expect;
		}
	}

	/** A chain or thread being drawn out. */
	private static final class Strand {
		Display.BlockDisplay display;
		Transformation to;
		int age;
	}

	static final class Arena {
		UUID boss;
		ServerLevel level;
		int style;
		double cx, cy, cz;
		List<Piece> pieces;
		List<ArenaDesign.Line> lines;
		int next, out;
		double credit;
		boolean strung, dissolving;
		int linger = -1;
		/** Ticks the boss has not been found (a chunk still loading, or gone). */
		int missing;
		final List<BlockFlight.Flight> flights = new ArrayList<>();
		final List<Strand> strands = new ArrayList<>();
		/** Every block it means to build, so a second arena near never builds over it. */
		final Set<Long> claimed = new HashSet<>();
		/** Its design: the lie of its floor, what each group of blocks is, its islands and cores. */
		ArenaDesign.Blueprint bp;
		/** Its blocks by group. */
		final List<List<Piece>> groups = new ArrayList<>();
		/** Groups something has broken into, to see whether what is left still stands. */
		final Set<Integer> damaged = new HashSet<>();
		/** What happens in its fight: its phases, cores and falling islands. */
		final ElderDomain.State domain = new ElderDomain.State();
		/** Blocks whose part of the world was not loaded when their turn came, by chunk: built once it is. */
		final Map<Long, ArrayDeque<Piece>> waiting = new HashMap<>();
		/** Blocks it put down whose part of the world was not loaded when they were to come down, by chunk: taken down once it is. */
		final Map<Long, ArrayDeque<Piece>> leftover = new HashMap<>();
		int clock;
	}

	private static final List<Arena> ARENAS = new ArrayList<>();
	/** Every block an arena has put down, by level and position. */
	static final Map<ServerLevel, Map<Long, Piece>> STANDING = new HashMap<>();

	/** The arenas standing or on their way. */
	static List<Arena> arenas() {
		return ARENAS;
	}

	/** The arena of an Elder, or null. */
	static Arena of(Entity boss) {
		for (Arena a : ARENAS) {
			if (a.boss.equals(boss.getUUID())) {
				return a;
			}
		}
		return null;
	}

	/** The block an arena put down here, or null. */
	static Piece standing(ServerLevel level, BlockPos pos) {
		Map<Long, Piece> m = STANDING.get(level);
		return m == null ? null : m.get(pos.asLong());
	}

	/** The arena that means to build (or built) this block, or null. */
	static Arena owner(ServerLevel level, BlockPos pos) {
		long k = pos.asLong();
		for (Arena a : ARENAS) {
			if (a.level == level && a.claimed.contains(k)) {
				return a;
			}
		}
		return null;
	}

	/**
	 * More blocks for an arena in its fight (roots its floor throws up, roots hung under its arches): they go up the way
	 * its own did, as a group of their own, and come down with it.
	 */
	static void grow(Arena a, List<Piece> more) {
		if (more.isEmpty() || a.dissolving || a.linger >= 0) {
			return;
		}
		int group = a.groups.size();
		a.bp.kinds.add(ArenaDesign.GROWN);
		List<Piece> mine = new ArrayList<>();
		for (Piece p : more) {
			if (free(a.level, p.pos) && a.claimed.add(p.pos.asLong())) {
				p.group = group;
				a.pieces.add(p);
				mine.add(p);
			}
		}
		a.groups.add(mine);
	}

	/** Rubble of an arena has come down here and lies as a block: it is the arena's now, and goes when it goes. */
	static void landed(Arena a, BlockPos pos, BlockState state) {
		if (a.dissolving || ARENAS.indexOf(a) < 0) {
			a.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
			return;
		}
		// Where a block of its own was broken out, the rubble takes its place (and puts back what that one would have)
		Piece was = standing(a.level, pos);
		Piece p = new Piece(pos.immutable(), state, AIR, null);
		p.original = was != null && was.placed && was.original != null ? was.original : Blocks.AIR.defaultBlockState();
		if (was != null) {
			was.placed = false;
		}
		p.placed = true;
		a.claimed.add(p.pos.asLong());
		a.pieces.add(p);
		STANDING.computeIfAbsent(a.level, k -> new HashMap<>()).put(p.pos.asLong(), p);
	}

	/** A block of an arena that came down (fell as rubble or crumbled): it no longer stands. */
	static void fell(Arena a, Piece p) {
		p.placed = false;
		Map<Long, Piece> m = STANDING.get(a.level);
		if (m != null) {
			m.remove(p.pos.asLong());
		}
	}

	// ------------------------------------------------------------------ queries

	/** Whether this block was put there by an arena that still stands. */
	public static boolean holds(Level level, BlockPos pos) {
		Map<Long, Piece> m = level instanceof ServerLevel sl ? STANDING.get(sl) : null;
		return m != null && m.containsKey(pos.asLong());
	}

	/** Whether a block is still free for an arena (no arena stands on it or is about to). */
	static boolean free(ServerLevel level, BlockPos pos) {
		if (holds(level, pos)) {
			return false;
		}
		for (Arena a : ARENAS) {
			if (a.level == level && a.claimed.contains(pos.asLong())) {
				return false;
			}
		}
		return true;
	}

	/** A block some arena has put down in this level (for tests), or null. */
	public static BlockPos anyStanding(ServerLevel level) {
		Map<Long, Piece> m = STANDING.get(level);
		return m == null || m.isEmpty() ? null : m.values().iterator().next().pos;
	}

	/** Arenas standing or on their way (for tests). */
	public static int count() {
		return ARENAS.size();
	}

	/** Blocks put down by arenas, all told (for tests). */
	public static int standing() {
		int n = 0;
		for (Map<Long, Piece> m : STANDING.values()) {
			n += m.size();
		}
		return n;
	}

	/** Blocks of every arena on their way (for tests): flying in, flying out, or still to come. */
	public static int pending() {
		int n = 0;
		for (Arena a : ARENAS) {
			n += a.flights.size() + (a.dissolving ? 0 : a.pieces.size() - a.next);
		}
		return n;
	}

	/** The middle of the arena raised last (for tests), or null if none stands. */
	public static double[] lastCentre() {
		if (ARENAS.isEmpty()) {
			return null;
		}
		Arena a = ARENAS.get(ARENAS.size() - 1);
		return new double[] {a.cx, a.cy, a.cz};
	}

	/** Blocks of every arena waiting for their part of the world to be loaded (for tests). */
	public static int waiting() {
		int n = 0;
		for (Arena a : ARENAS) {
			for (ArrayDeque<Piece> q : a.waiting.values()) {
				n += q.size();
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ the arena rises and falls

	/** The Elder's arena begins to rise round {@code centre} (the first air above the ground under the Elder). */
	public static void raise(ElderBoss b, BlockPos centre) {
		if (Config.ELDER_ARENA <= 0) {
			return;
		}
		ServerLevel level = (ServerLevel) b.level();
		int style = ElderFx.index(b);
		ArenaDesign.Blueprint bp = ArenaDesign.design(level, style, centre, ElderTerrain.griefing(level), ThreadLocalRandom.current().nextLong());
		if (bp.pieces.isEmpty()) {
			return;
		}
		open(b, level, style, centre, bp);
	}

	/** An arena of a design begins: it is the Elder's, and claims every block it means to build. */
	private static Arena open(ElderBoss b, ServerLevel level, int style, BlockPos centre, ArenaDesign.Blueprint bp) {
		Arena a = new Arena();
		a.boss = b.getUUID();
		a.level = level;
		a.style = style;
		a.cx = centre.getX() + 0.5;
		a.cy = centre.getY();
		a.cz = centre.getZ() + 0.5;
		a.pieces = bp.pieces;
		a.lines = bp.lines;
		a.bp = bp;
		for (int i = 0; i < bp.kinds.size(); i++) {
			a.groups.add(new ArrayList<>());
		}
		for (Piece p : bp.pieces) {
			a.claimed.add(p.pos.asLong());
			if (p.group >= 0 && p.group < a.groups.size()) {
				a.groups.get(p.group).add(p);
			}
		}
		ARENAS.add(a);
		return a;
	}

	/**
	 * An arena of a few given structures for an Elder, standing at once (for tests). Each structure is a list of blocks
	 * of one kind: 1 a building, 2 an island (its middle and top taken from its blocks), 3 a core (made of its Elder's
	 * light); the rest are made of {@code state}. Its floor is {@code floorR} round {@code centre}.
	 */
	public static void rig(ElderBoss b, BlockPos centre, int floorR, List<List<BlockPos>> structures, int[] kinds, BlockState state) {
		ServerLevel level = (ServerLevel) b.level();
		ArenaDesign.Blueprint bp = new ArenaDesign.Blueprint();
		bp.style = ElderFx.index(b);
		bp.cx = centre.getX();
		bp.gy = centre.getY() - 1;
		bp.cz = centre.getZ();
		bp.floorR = floorR;
		for (int g = 0; g < structures.size(); g++) {
			List<BlockPos> blocks = new ArrayList<>();
			structures.get(g).forEach(q -> blocks.add(q.immutable()));
			bp.kinds.add(kinds[g]);
			BlockState s = kinds[g] == ArenaDesign.CORE ? ArenaDesign.coreBlock(bp.style) : state;
			double x = 0, z = 0;
			int top = Integer.MIN_VALUE;
			for (BlockPos q : blocks) {
				Piece p = new Piece(q, s, AIR, null);
				p.group = g;
				bp.pieces.add(p);
				x += q.getX();
				z += q.getZ();
				top = Math.max(top, q.getY());
			}
			if (kinds[g] == ArenaDesign.CORE) {
				bp.cores.add(blocks);
			}
			if (kinds[g] == ArenaDesign.ISLE && !blocks.isEmpty()) {
				int mx = (int) Math.round(x / blocks.size()), mz = (int) Math.round(z / blocks.size());
				double r = 0;
				for (BlockPos q : blocks) {
					r = Math.max(r, Math.hypot(q.getX() - mx, q.getZ() - mz));
				}
				bp.isles.add(new ArenaDesign.Isle(mx, top, mz, r, g));
			}
		}
		Arena a = open(b, level, bp.style, centre, bp);
		for (Piece p : a.pieces) {
			p.original = level.getBlockState(p.pos);
			level.setBlock(p.pos, p.state, 2 | 16);
			p.placed = true;
			STANDING.computeIfAbsent(level, k -> new HashMap<>()).put(p.pos.asLong(), p);
		}
		a.next = a.pieces.size();
		a.strung = true;
	}

	/** An Elder's arena is taken down at once and the ground put back (for tests). */
	public static void unrig(Entity boss) {
		Iterator<Arena> it = ARENAS.iterator();
		while (it.hasNext()) {
			Arena a = it.next();
			if (a.boss.equals(boss.getUUID())) {
				takeDown(a);
				it.remove();
			}
		}
	}

	/** This Elder's fight is over (slain or gone): its arena stands a while, then comes apart. */
	public static void release(Entity boss) {
		for (Arena a : ARENAS) {
			if (a.boss.equals(boss.getUUID())) {
				end(a);
			}
		}
	}

	private static void end(Arena a) {
		if (a.linger >= 0 || a.dissolving) {
			return;
		}
		// Nothing more comes in (nor what waits for its part of the world); what is in the air still lands
		a.next = a.pieces.size();
		a.waiting.clear();
		a.linger = Config.ELDER_ARENA_LINGER_SECONDS < 0 ? Integer.MAX_VALUE : Config.ELDER_ARENA_LINGER_SECONDS * 20;
	}

	public static void tick(MinecraftServer s) {
		ArenaRuin.tick();
		if (ARENAS.isEmpty()) {
			return;
		}
		Iterator<Arena> it = ARENAS.iterator();
		while (it.hasNext()) {
			Arena a = it.next();
			if (step(a)) {
				it.remove();
			}
		}
	}

	/** One tick of an arena; true once it is gone. */
	private static boolean step(Arena a) {
		Entity boss = a.level.getEntity(a.boss);
		if (boss instanceof ElderBoss e && e.isAlive()) {
			a.missing = 0;
		} else if (boss != null || ++a.missing > 60) {
			// Slain, or gone for three seconds (not merely not yet found in a chunk that is still loading)
			end(a);
		}
		double rate = a.pieces.size() / Math.max(1.0, Config.ELDER_ARENA_BUILD_SECONDS * 20);
		if (!a.dissolving && a.next < a.pieces.size()) {
			a.credit += rate;
			while (a.credit >= 1 && a.next < a.pieces.size()) {
				a.credit--;
				launch(a, a.pieces.get(a.next++));
			}
		}
		// Twice a second, what waited for its part of the world goes up where that has come into the world (someone
		// came near), at the pace the arena is built at
		if (++a.clock % 10 == 0) {
			if (!a.dissolving) {
				settle(a, a.waiting, (int) Math.ceil(rate * 10), p -> launch(a, p));
			}
			settle(a, a.leftover, (int) Math.ceil(a.pieces.size() / Math.max(1.0, Config.ELDER_ARENA_DISSOLVE_SECONDS * 20) * 10),
					p -> unbuild(a, p));
		}
		a.flights.removeIf(BlockFlight::step);
		// What something broke into is looked at a few times a second: whatever is left without support comes down
		if (!a.damaged.isEmpty() && a.clock % 5 == 0) {
			ArenaRuin.settle(a);
		}
		// The fight on it: its domain changing, its cores, its islands falling
		if (!a.dissolving && a.linger < 0 && boss instanceof ElderBoss fighting && fighting.isAlive()) {
			ElderDomain.tick(a, fighting);
		}
		if (!a.strung && !a.dissolving && a.next >= a.pieces.size() && a.flights.isEmpty()) {
			a.strung = true;
			if (a.linger < 0) {
				string(a);
			}
		}
		Iterator<Strand> st = a.strands.iterator();
		while (st.hasNext()) {
			Strand s = st.next();
			if (s.display.isRemoved()) {
				BlockFlight.forget(s.display);
				st.remove();
				continue;
			}
			if (++s.age == 2) {
				DisplayAccess d = (DisplayAccess) s.display;
				d.extremedimensions$setTransformation(s.to);
				d.extremedimensions$setInterpolationDuration(10);
				d.extremedimensions$setInterpolationDelay(0);
			}
		}
		if (a.linger >= 0 && !a.dissolving && a.flights.isEmpty() && a.next >= a.pieces.size()) {
			if (a.linger == Integer.MAX_VALUE) {
				// It stays for good: the world keeps it, and the arena is forgotten
				keep(a);
				return true;
			}
			if (a.linger-- <= 0) {
				a.dissolving = true;
				a.out = a.pieces.size();
				a.credit = 0;
				unstring(a);
			}
		}
		if (a.dissolving) {
			a.credit += a.pieces.size() / Math.max(1.0, Config.ELDER_ARENA_DISSOLVE_SECONDS * 20);
			while (a.credit >= 1 && a.out > 0) {
				Piece p = a.pieces.get(--a.out);
				if (p.placed) {
					a.credit--;
					unbuild(a, p);
				}
			}
			return a.out <= 0 && a.flights.isEmpty() && a.leftover.isEmpty();
		}
		return false;
	}

	/** Up to {@code budget} blocks held back by chunk, taken from the chunks now loaded (in the order they were held back). */
	private static void settle(Arena a, Map<Long, ArrayDeque<Piece>> held, int budget, java.util.function.Consumer<Piece> act) {
		Iterator<ArrayDeque<Piece>> it = held.values().iterator();
		while (it.hasNext() && budget > 0) {
			ArrayDeque<Piece> q = it.next();
			if (!q.isEmpty() && !a.level.isLoaded(q.peekFirst().pos)) {
				continue;
			}
			while (!q.isEmpty() && budget-- > 0) {
				act.accept(q.pollFirst());
			}
			if (q.isEmpty()) {
				it.remove();
			}
		}
	}

	/** Holds a block back until its part of the world is loaded. */
	private static void hold(Map<Long, ArrayDeque<Piece>> held, Piece p) {
		held.computeIfAbsent(((long) (p.pos.getX() >> 4) << 32) | ((p.pos.getZ() >> 4) & 0xFFFFFFFFL), k -> new ArrayDeque<>()).add(p);
	}

	// ------------------------------------------------------------------ flights

	/**
	 * A block appears out of nothing ten to fifteen blocks away and sets off towards its place; or, with as many in the
	 * air as the settings allow ({@code elder_arena_fly_max}) or no one near enough to see it fly
	 * ({@code elder_arena_fly_range}), it simply appears in its place.
	 */
	private static void launch(Arena a, Piece p) {
		ServerLevel level = a.level;
		if (!level.isLoaded(p.pos)) {
			// Its part of the world is not loaded (no one near): it goes up once it is
			hold(a.waiting, p);
			return;
		}
		if (!fits(level, p)) {
			return;
		}
		if (a.flights.size() >= Math.max(1, Config.ELDER_ARENA_FLY_MAX) || !watched(level, p.pos)) {
			if (!blocked(level, p.pos)) {
				place(a, p, true);
			}
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockFlight.Flight f = BlockFlight.in(level, p.pos, p.state, offset(a, p, rnd, false), flightTicks(), ArenaDesign.spark(a.style), 2,
				new BlockFlight.Landing() {
					@Override
					public boolean fits() {
						return ElderArena.fits(level, p);
					}

					@Override
					public boolean blocked() {
						return ElderArena.blocked(level, p.pos);
					}

					@Override
					public void land() {
						place(a, p, false);
					}
				});
		if (f == null) {
			return;
		}
		a.flights.add(f);
		WorldFx.particleIn(level, ArenaDesign.spark(a.style), f.x(), f.y(), f.z(), 0.15, 0.15, 0.15, 0.02, 3);
		if (a.next % 7 == 0) {
			WorldFx.soundIn(level.dimension().identifier().toString(), ArenaDesign.whoosh(a.style), "hostile", f.x(), f.y(), f.z(), 0.7f,
					(float) rnd.nextDouble(0.6, 1.3));
		}
	}

	private static int flightTicks() {
		return Math.max(10, Config.ELDER_ARENA_FLIGHT_TICKS);
	}

	/** A block taken back: the ground is put back as it was and the block flies off into nothing. */
	private static void unbuild(Arena a, Piece p) {
		ServerLevel level = a.level;
		if (!level.isLoaded(p.pos)) {
			// Its part of the world is not loaded: it comes down once it is (the world is never loaded just to take it down)
			hold(a.leftover, p);
			return;
		}
		BlockState now = level.getBlockState(p.pos);
		boolean whole = now == p.state;
		if (whole || now.isAir()) {
			level.setBlock(p.pos, p.original == null ? Blocks.AIR.defaultBlockState() : p.original, 2 | 16);
		}
		p.placed = false;
		Map<Long, Piece> m = STANDING.get(level);
		if (m != null) {
			m.remove(p.pos.asLong());
		}
		if (!whole || !level.isLoaded(p.pos) || a.flights.size() >= Math.max(1, Config.ELDER_ARENA_FLY_MAX) || !watched(level, p.pos)) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockFlight.Flight f = BlockFlight.out(level, p.pos, p.state, offset(a, p, rnd, true), flightTicks(), ArenaDesign.spark(a.style), 3);
		if (f != null) {
			a.flights.add(f);
		}
		if (a.out % 6 == 0) {
			WorldFx.soundIn(level.dimension().identifier().toString(), ArenaDesign.whoosh(a.style), "hostile", p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5,
					0.6f, (float) rnd.nextDouble(0.8, 1.5));
		}
	}

	/** Whether a player is near enough to a block to see it fly. */
	private static boolean watched(ServerLevel level, BlockPos pos) {
		double r = Math.max(8, Config.ELDER_ARENA_FLY_RANGE);
		for (net.minecraft.server.level.ServerPlayer q : level.players()) {
			if (!q.isSpectator() && q.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < r * r) {
				return true;
			}
		}
		return false;
	}

	/** The block lands (or, {@code quiet}, simply appears): it is put down for real. */
	private static void place(Arena a, Piece p, boolean quiet) {
		ServerLevel level = a.level;
		BlockState was = level.getBlockState(p.pos);
		p.original = p.kind == GROUND ? was : was.isAir() ? was : Blocks.AIR.defaultBlockState();
		if (p.kind == GROUND) {
			// Grass and flowers on the ground it replaces do not stay floating over it
			for (int i = 1; i <= 2; i++) {
				BlockPos up = p.pos.above(i);
				BlockState s = level.getBlockState(up);
				if (s.isAir() || !s.canBeReplaced() || s.liquid() || holds(level, up)) {
					break;
				}
				level.setBlock(up, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
		// No neighbour updates: a lantern hung before its chain, or a wall beside air, stays as designed
		level.setBlock(p.pos, p.state, 2 | 16);
		p.placed = true;
		STANDING.computeIfAbsent(level, k -> new HashMap<>()).put(p.pos.asLong(), p);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double x = p.pos.getX() + 0.5, y = p.pos.getY() + 0.6, z = p.pos.getZ() + 0.5;
		if (quiet) {
			// A glint where it appears, now and then, and no sound (many appear at once)
			if (rnd.nextInt(4) == 0) {
				WorldFx.particleIn(level, ArenaDesign.spark(a.style), x, y, z, 0.3, 0.2, 0.3, 0.02, 1);
			}
			return;
		}
		WorldFx.particleIn(level, ArenaDesign.spark(a.style), x, y, z, 0.35, 0.2, 0.35, 0.03, 3);
		if (rnd.nextInt(3) == 0) {
			level.playSound(null, x, y, z, p.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, (float) rnd.nextDouble(0.7, 1.1));
		}
	}

	/** Whether a piece may still go in: its place is free air (or plants), or the ground it inlays is unchanged. */
	private static boolean fits(ServerLevel level, Piece p) {
		BlockState s = level.getBlockState(p.pos);
		if (holds(level, p.pos)) {
			return false;
		}
		if (p.kind == GROUND) {
			return s == p.expect;
		}
		return s.isAir() || s.canBeReplaced() && !s.liquid() && !s.hasBlockEntity();
	}

	/** Whether someone (not an Elder) stands where a block would go. */
	private static boolean blocked(ServerLevel level, BlockPos pos) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos))) {
			if (!(e instanceof ElderBoss) && e.isAlive()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Where a block starts (or, flying out, ends), from its place: ten to fifteen blocks off, out of the earth round it
	 * for the Matriarch, swept round on a whirl for the Ferryman, from high above for the Weaver (flying out, the
	 * other way: back into the earth, away on the whirl, up into the sky).
	 */
	private static double[] offset(Arena a, Piece p, ThreadLocalRandom rnd, boolean out) {
		double lo = Math.max(1, Config.ELDER_ARENA_FLIGHT_MIN), hi = Math.max(lo, Config.ELDER_ARENA_FLIGHT_MAX);
		double dist = lo + rnd.nextDouble() * (hi - lo);
		double round = Math.atan2(p.pos.getZ() + 0.5 - a.cz, p.pos.getX() + 0.5 - a.cx);
		double az, el;
		switch (a.style) {
			case 0 -> {
				az = rnd.nextDouble(Math.PI * 2);
				el = out ? rnd.nextDouble(-0.9, -0.3) : rnd.nextDouble(-0.55, 0.3);
			}
			case 1 -> {
				az = round + (out ? -1 : 1) * (Math.PI / 2 + rnd.nextDouble(0.1, 0.5));
				el = rnd.nextDouble(0.05, 0.45);
			}
			default -> {
				az = rnd.nextDouble(Math.PI * 2);
				el = rnd.nextDouble(0.85, 1.35);
			}
		}
		return new double[] {Math.cos(az) * Math.cos(el) * dist, Math.sin(el) * dist, Math.sin(az) * Math.cos(el) * dist};
	}

	// ------------------------------------------------------------------ chains and threads

	/** Chains (threads, for the Weaver) are drawn out between the towers once they stand. */
	private static void string(Arena a) {
		BlockState look = ArenaDesign.strand(a.style);
		float thick = a.style == 2 ? 0.07F : 0.6F;
		for (ArenaDesign.Line line : a.lines) {
			// Only between towers whose part of the world is loaded (one still waiting for it may never stand)
			if (!a.level.isLoaded(BlockPos.containing(line.x0(), line.y0(), line.z0())) || !a.level.isLoaded(BlockPos.containing(line.x1(), line.y1(), line.z1()))) {
				continue;
			}
			double dx = line.x1() - line.x0(), dy = line.y1() - line.y0(), dz = line.z1() - line.z0();
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// A straight thread is one piece; a sagging chain is drawn in pieces, never more than 24 (they are long in a large arena)
			int n = line.sag() < 0.01 ? 1 : Math.min(24, Math.max(2, (int) Math.ceil(len / 1.6)));
			double[] prev = null;
			for (int i = 0; i <= n; i++) {
				double k = i / (double) n;
				double[] pt = {line.x0() + dx * k, line.y0() + dy * k - line.sag() * 4 * k * (1 - k), line.z0() + dz * k};
				if (prev != null) {
					segment(a, look, thick, prev, pt);
				}
				prev = pt;
			}
		}
	}

	private static void segment(Arena a, BlockState look, float thick, double[] from, double[] to) {
		double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-3 || !a.level.isLoaded(BlockPos.containing(from[0], from[1], from[2]))) {
			return;
		}
		// Turns the block's +Y onto the segment; the centre of its (scaled) bottom face sits on the start
		Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dx, (float) dy, (float) dz).normalize());
		Vector3f c = new Vector3f(0.5F * thick, 0, 0.5F * thick);
		new Quaternionf(q).transform(c);
		Transformation full = new Transformation(new Vector3f(-c.x, -c.y, -c.z), new Quaternionf(q), new Vector3f(thick, len, thick), new Quaternionf());
		Transformation none = new Transformation(new Vector3f(-c.x, -c.y, -c.z), new Quaternionf(q), new Vector3f(thick, 0.01F, thick), new Quaternionf());
		Display.BlockDisplay d = BlockFlight.spawn(a.level, from[0], from[1], from[2], look, none);
		if (d == null) {
			return;
		}
		if (a.style == 2) {
			((DisplayAccess) d).extremedimensions$setBrightnessOverride(Brightness.FULL_BRIGHT);
		}
		Strand s = new Strand();
		s.display = d;
		s.to = full;
		a.strands.add(s);
	}

	private static void unstring(Arena a) {
		for (Strand s : a.strands) {
			BlockFlight.drop(s.display);
		}
		a.strands.clear();
	}

	/** An arena that stays for good: its blocks are the world's now. */
	private static void keep(Arena a) {
		unstring(a);
		Map<Long, Piece> m = STANDING.get(a.level);
		if (m != null) {
			for (Piece p : a.pieces) {
				m.remove(p.pos.asLong());
			}
		}
	}

	// ------------------------------------------------------------------ the rest of the world

	/** A player breaks a block of an arena: it is gone, and nothing drops. False to stop the usual breaking. */
	public static boolean broken(Level level, BlockPos pos) {
		if (!holds(level, pos)) {
			return true;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		if (level instanceof ServerLevel sl) {
			ArenaRuin.struck(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1);
		}
		return false;
	}

	/** On stopping: every arena is taken down at once and the ground put back, before the world is saved. */
	public static void clear() {
		ArenaRuin.clear();
		for (Arena a : ARENAS) {
			takeDown(a);
		}
		ARENAS.clear();
		STANDING.clear();
	}

	/** An arena is taken down at once, the ground put back. */
	private static void takeDown(Arena a) {
		a.flights.forEach(BlockFlight::cancel);
		a.strands.forEach(s -> BlockFlight.drop(s.display));
		Map<Long, Piece> m = STANDING.get(a.level);
		for (int i = a.pieces.size() - 1; i >= 0; i--) {
			Piece p = a.pieces.get(i);
			if (!p.placed) {
				continue;
			}
			BlockState now = a.level.getBlockState(p.pos);
			if (now == p.state || now.isAir()) {
				a.level.setBlock(p.pos, p.original == null ? Blocks.AIR.defaultBlockState() : p.original, 2 | 16);
			}
			p.placed = false;
			if (m != null) {
				m.remove(p.pos.asLong());
			}
		}
	}
}
