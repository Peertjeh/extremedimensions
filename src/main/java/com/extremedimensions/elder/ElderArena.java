package com.extremedimensions.elder;

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
 * once. The blocks fly as {@link BlockFlight}s.
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

	private static final class Arena {
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
	}

	private static final List<Arena> ARENAS = new ArrayList<>();
	/** Every block an arena has put down, by level and position. */
	private static final Map<ServerLevel, Map<Long, Piece>> STANDING = new HashMap<>();

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
		Arena a = new Arena();
		a.boss = b.getUUID();
		a.level = level;
		a.style = style;
		a.cx = centre.getX() + 0.5;
		a.cy = centre.getY();
		a.cz = centre.getZ() + 0.5;
		a.pieces = bp.pieces;
		a.lines = bp.lines;
		for (Piece p : bp.pieces) {
			a.claimed.add(p.pos.asLong());
		}
		ARENAS.add(a);
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
		// Nothing more comes in; what is in the air still lands
		a.next = a.pieces.size();
		a.linger = Config.ELDER_ARENA_LINGER_SECONDS < 0 ? Integer.MAX_VALUE : Config.ELDER_ARENA_LINGER_SECONDS * 20;
	}

	public static void tick(MinecraftServer s) {
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
		if (!a.dissolving && a.next < a.pieces.size()) {
			a.credit += a.pieces.size() / Math.max(1.0, Config.ELDER_ARENA_BUILD_SECONDS * 20);
			while (a.credit >= 1 && a.next < a.pieces.size()) {
				a.credit--;
				launch(a, a.pieces.get(a.next++));
			}
		}
		a.flights.removeIf(BlockFlight::step);
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
			return a.out <= 0 && a.flights.isEmpty();
		}
		return false;
	}

	// ------------------------------------------------------------------ flights

	/** A block appears out of nothing ten to fifteen blocks away and sets off towards its place. */
	private static void launch(Arena a, Piece p) {
		ServerLevel level = a.level;
		if (!level.isLoaded(p.pos) || !fits(level, p)) {
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
						place(a, p);
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
		if (!whole || !level.isLoaded(p.pos)) {
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

	/** The block lands: it is put down for real. */
	private static void place(Arena a, Piece p) {
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
			double dx = line.x1() - line.x0(), dy = line.y1() - line.y0(), dz = line.z1() - line.z0();
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			int n = Math.max(2, (int) Math.ceil(len / 1.6));
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
		return false;
	}

	/** On stopping: every arena is taken down at once and the ground put back, before the world is saved. */
	public static void clear() {
		for (Arena a : ARENAS) {
			a.flights.forEach(BlockFlight::cancel);
			a.strands.forEach(s -> BlockFlight.drop(s.display));
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
			}
		}
		ARENAS.clear();
		STANDING.clear();
	}
}
