package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.extremedimensions.config.Config;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * The ground breaks (3.41): every Elder's ultimate shakes up its whole arena. While the ultimate builds the ground
 * trembles harder and harder, glowing cracks run out across it from the Elder with a few blocks burning in them, and
 * blocks of the floor tear loose and rise into the air, hovering and turning round the Elder while grit flies. As it
 * breaks the arena is torn apart over two or three seconds, from the Elder out to its edge: tilted slabs of cliff burst
 * up in a ring round the arena and here and there over it, spikes stand up in clusters, a chasm or two splits it,
 * craters open with broken rims, the floor heaves in low bumps, the Matriarch's roots tear out of the ground, arc over
 * and dive back in, and what hovered slams down. Whoever stands where the ground erupts is thrown up and hurt a little
 * ({@code elder_upheaval_damage}). Each Elder breaks the ground in its own stone (see {@link Look}).
 *
 * <p>All of it is temporary: every block it changes is remembered with what was there, and once the fight is over
 * (slain, gone) the raised ground sinks back into the ground a layer at a time (its tops going down with it) and the
 * holes fill from their floor up, the arena's own floor among it before the arena comes apart. Ground not loaded then
 * waits for it, but whatever is left once {@code elder_upheaval_linger_seconds} have passed is put back at once,
 * loaded or not; so is everything on stopping.
 *
 * <p>Blocks change only where the world may be torn up ({@link ElderTerrain#griefing}); otherwise the ultimate shakes,
 * glows, throws and hurls displays that drop back down, and the world stays as it was. It never touches a block that
 * holds data, nor one a Wither cannot break, nor what another Elder raised for a while; it builds only into air and
 * plants, and opens a hole only where solid ground stays under it, beside no water or lava and under nothing an arena
 * built (many arenas float). Of an arena it changes only its floor, and only as the arena's own blocks break
 * ({@code elder_arena_breakable}); those blocks stay the arena's all the while, and are the arena's again once put back.
 * What it changed drops nothing, and no Elder's blow tears it up. It changes at most {@code elder_upheaval_budget}
 * blocks a tick (it and its sinking back, all upheavals together), never more than {@code elder_upheaval_blocks} in one
 * ultimate, never loads the world to change or look at a block while the fight goes on, and its block displays are few
 * ({@code elder_upheaval_displays}).
 */
public final class ElderUpheaval {
	private ElderUpheaval() {}

	// ------------------------------------------------------------------ what it keeps

	/** Where an upheaval is: the ultimate building, the ground erupting, the broken ground standing, sinking back. */
	private static final int CHARGE = 0, ERUPT = 1, STAND = 2, SINK = 3;
	/** What a change of a block is: built up into air, a plant cleared, a block torn out, a block turned to another. */
	private static final int RAISE = 0, CLEAR = 1, CUT = 2, SET = 3;
	/** What breaks out of the ground. */
	private static final int CLIFF = 0, SPIKES = 1, CHASM = 2, CRATER = 3, BUMP = 4, ROOT = 5;
	/** A column's ground not looked at yet, and a column with no ground to break (not loaded, water, nothing near). */
	private static final int UNKNOWN = Integer.MIN_VALUE, NONE = Integer.MIN_VALUE + 1;
	/** How far the ground breaks round an Elder with no arena, in blocks (times {@code elder_upheaval_scale}). */
	private static final double OPEN_RADIUS = 28;
	/** Ticks the eruption takes to run out from the Elder to the edge of its arena. */
	private static final int WAVE = 40;
	/** Ticks a layer of raised ground takes to sink back, and a layer of a hole to fill. */
	private static final int SINK_LAYER = 2;
	/** Most bits of grit in the air at once, and most blocks burning in the cracks. */
	private static final int SHARDS = 18, CRACKS = 160;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	/** One block the ground changed: what was there, what it is now, whose floor it was, and the ground of its column. */
	private static final class Change {
		final Upheaval owner;
		final BlockPos pos;
		BlockState original;
		BlockState placed;
		/** The arena's own block that stood here (its piece), or null. */
		final ElderArena.Piece piece;
		final int ground;
		/** Grass under raised ground, only watched: covered, it turns to dirt, and is put back as grass. */
		final boolean watch;
		/** When it goes back as the ground settles (ticks into the sinking). */
		int when;

		Change(Upheaval owner, BlockPos pos, BlockState original, BlockState placed, ElderArena.Piece piece, int ground, boolean watch) {
			this.owner = owner;
			this.pos = pos;
			this.original = original;
			this.placed = placed;
			this.piece = piece;
			this.ground = ground;
			this.watch = watch;
		}
	}

	/** A change of a block still to come, at a tick of the eruption (in the order it was planned within a tick). */
	private record Op(BlockPos pos, BlockState to, int kind, int due, int ground, int tag, long seq) {}

	/** A crack running out from the Elder: a point every block, the ground under each once it is reached. */
	private static final class Fissure {
		final double[] xs, zs;
		final int[] ys;
		/** Points into the crack it branched from at which it starts. */
		final int from;
		int shown;

		Fissure(List<double[]> pts, int from) {
			this.xs = new double[pts.size()];
			this.zs = new double[pts.size()];
			this.ys = new int[pts.size()];
			for (int i = 0; i < pts.size(); i++) {
				xs[i] = pts.get(i)[0];
				zs[i] = pts.get(i)[1];
				ys[i] = UNKNOWN;
			}
			this.from = from;
		}
	}

	/** A block of the floor torn loose, hovering round the Elder (a display standing over the place it came from). */
	private static final class Hover {
		final Display.BlockDisplay d;
		final int bx, by, bz;
		final BlockState state;
		final Quaternionf spin = new Quaternionf();
		Vector3f axis;
		float spinRate;
		double ang, rad, hgt, w;
		int age;
		/** Its fall: when it sets off (ticks into the eruption), when it lands, where, and whether it lies there as a block. */
		int slam = -1, lands = -1;
		int tx, ty, tz;
		boolean onGround;
		/** Dropped back into its place (the ultimate cut short): the tick it is gone. */
		int dropAt = -1;

		Hover(Display.BlockDisplay d, int bx, int by, int bz, BlockState state) {
			this.d = d;
			this.bx = bx;
			this.by = by;
			this.bz = bz;
			this.state = state;
		}
	}

	/** A bit of grit flung out of a crack (a small display on a short arc). */
	private static final class Shard {
		final Display.BlockDisplay d;
		final float size;
		final Quaternionf spin;
		double dx, dz, up;
		int age;

		Shard(Display.BlockDisplay d, float size, Quaternionf spin) {
			this.d = d;
			this.size = size;
			this.spin = spin;
		}
	}

	/** Something the ground throws up: its kind, when (ticks into the eruption), where, and its lie. */
	private record Feature(int kind, int start, double x, double z, double angle, double size, boolean across) {}

	/** One Elder's ultimate breaking the ground. */
	private static final class Upheaval {
		final UUID boss;
		final ServerLevel level;
		final int style;
		ElderArena.Arena arena;
		/** The middle of what breaks (its arena's, or where the Elder stood), its ground, and how far it reaches. */
		double cx, cz, radius;
		int gy;
		/** Where the Elder stands (the cracks run out from here) and its ground; what is kept clear round its body. */
		double ex, ez, clear;
		int ey, bodyLow, bodyHigh;
		/** How high things rise, as a share (a small upheaval raises lower cliffs). */
		double lift = 1;
		/** Which way what hovers turns round the Elder. */
		int turn = 1;
		int phase = CHARGE;
		int age, clock, length = 180, missing;
		/** Driven by a test, not by the server's tick. */
		boolean manual, done;
		boolean grief;
		/** Ticks left before whatever is left is put back at once (once the fight is over). */
		int linger = -1;
		final List<Change> changes = new ArrayList<>();
		final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
		final List<Fissure> fissures = new ArrayList<>();
		final List<Hover> hovers = new ArrayList<>();
		final List<Shard> shards = new ArrayList<>();
		final List<Feature> features = new ArrayList<>();
		int next;
		final PriorityQueue<Op> ops = new PriorityQueue<>(Comparator.comparingInt(Op::due).thenComparingLong(Op::seq));
		long seq;
		/** The players near now: every body (nothing is built into one), and who can be thrown. */
		final List<ServerPlayer> bodies = new ArrayList<>(), targets = new ArrayList<>();
		final Map<UUID, Integer> hit = new HashMap<>();
		/** Blocks changed in all (for the cap), blocks laid in the cracks, and the chasms that opened. */
		int changed, cracks;
		final java.util.Set<Integer> opened = new java.util.HashSet<>();
		int chasms;
		/** Sinking back: the changes in the order they go back, how far along, and those whose ground is not loaded. */
		List<Change> order = new ArrayList<>();
		int back;
		final List<Change> waiting = new ArrayList<>();

		Upheaval(UUID boss, ServerLevel level, int style) {
			this.boss = boss;
			this.level = level;
			this.style = style;
			tops.defaultReturnValue(UNKNOWN);
		}

		/** Where the Elder stands now: the cracks run from it, nothing is built into its body. */
		void elder(ElderBoss b) {
			double s = ElderFx.surface(level, b.getX(), b.getY() + 2, b.getZ(), 6, 24);
			ex = b.getX();
			ez = b.getZ();
			ey = Double.isNaN(s) ? (int) Math.floor(b.getY()) - 1 : (int) Math.floor(s) - 1;
			clear = b.getBbWidth() / 2 + 2;
			bodyLow = ey - 3;
			bodyHigh = ey + (int) Math.ceil(b.getBbHeight()) + 8;
		}
	}

	private static final List<Upheaval> ALL = new ArrayList<>();
	/** Every block an upheaval has changed, by level and position. */
	private static final Map<ServerLevel, Long2ObjectOpenHashMap<Change>> HELD = new HashMap<>();
	/** Blocks still to be changed (or looked at for a change) this tick, by every upheaval together. */
	private static int budget;

	// ------------------------------------------------------------------ queries

	/** Whether the ground changed this block in an Elder's ultimate: it goes back after the fight, and drops nothing. */
	public static boolean holds(Level level, BlockPos pos) {
		if (HELD.isEmpty() || !(level instanceof ServerLevel sl)) {
			return false;
		}
		Long2ObjectOpenHashMap<Change> m = HELD.get(sl);
		return m != null && m.containsKey(pos.asLong());
	}

	private static Change held(ServerLevel level, BlockPos pos) {
		Long2ObjectOpenHashMap<Change> m = HELD.get(level);
		return m == null ? null : m.get(pos.asLong());
	}

	private static Upheaval of(UUID boss) {
		for (Upheaval u : ALL) {
			if (!u.done && u.boss.equals(boss)) {
				return u;
			}
		}
		return null;
	}

	/** Upheavals under way, sinking back included (for tests). */
	public static int count() {
		int n = 0;
		for (Upheaval u : ALL) {
			n += u.done ? 0 : 1;
		}
		return n;
	}

	/**
	 * What an Elder's upheaval has done so far (for tests): {blocks raised, blocks torn out, chasms opened, displays in
	 * the air, blocks changed in all}; zeros if it has none.
	 */
	public static int[] tally(Entity boss) {
		Upheaval u = of(boss.getUUID());
		if (u == null) {
			return new int[5];
		}
		int raised = 0, removed = 0, all = 0;
		for (Change c : u.changes) {
			if (c.watch || held(u.level, c.pos) != c) {
				continue;
			}
			all++;
			if (open(c.original) && !c.placed.isAir()) {
				raised++;
			} else if (solid(c.original) && open(c.placed)) {
				removed++;
			}
		}
		return new int[] {raised, removed, u.opened.size(), u.hovers.size() + u.shards.size(), all};
	}

	// ------------------------------------------------------------------ the ultimate

	/** The ultimate begins: the ground starts to shake and crack round the Elder (see {@link ElderFight#beginUltimate}). */
	static void charge(ElderBoss b) {
		if (Config.ELDER_UPHEAVAL <= 0 || !(b.level() instanceof ServerLevel)) {
			return;
		}
		Upheaval u = of(b.getUUID());
		if (u != null && (u.phase == ERUPT || u.phase == SINK)) {
			return;
		}
		if (u == null) {
			u = open(b, null, 0);
			ALL.add(u);
		}
		u.phase = CHARGE;
		u.age = 0;
		u.length = Math.max(20, ElderUltimate.length() - 10);
		u.grief = ElderTerrain.griefing(u.level);
		u.elder(b);
		u.fissures.clear();
		fissures(u, ThreadLocalRandom.current());
	}

	/** The ultimate breaks: the ground erupts (see {@link ElderFight#breakUltimate}). */
	static void erupt(ElderBoss b) {
		if (Config.ELDER_UPHEAVAL <= 0 || !(b.level() instanceof ServerLevel)) {
			return;
		}
		Upheaval u = of(b.getUUID());
		if (u == null) {
			u = open(b, null, 0);
			ALL.add(u);
		}
		if (u.phase == ERUPT || u.phase == SINK) {
			return;
		}
		start(u, b);
	}

	/** The ultimate is over (see {@link ElderFight#closeUltimate}): if it never broke, what hovered drops back down. */
	static void close(ElderBoss b) {
		Upheaval u = of(b.getUUID());
		if (u == null || u.phase != CHARGE) {
			return;
		}
		for (Hover h : u.hovers) {
			h.dropAt = h.age + 8;
			h.slam = -2;
			show(h, h.bx + 0.5, h.by + 0.5, h.bz + 0.5, 8, true);
		}
		u.phase = STAND;
		u.age = 0;
	}

	/**
	 * The ground breaks round {@code centre} for an Elder at once, as its ultimate would break it, {@code radius} blocks
	 * across (for tests and operators: no ultimate, nothing building up to it). It erupts only as {@link #advance} drives
	 * it, until {@link #release} and {@link #finish}.
	 */
	public static void eruptAt(ElderBoss b, BlockPos centre, double radius) {
		Upheaval u = of(b.getUUID());
		if (u != null) {
			finish(b);
		}
		u = open(b, centre, radius);
		u.manual = true;
		ALL.add(u);
		start(u, b);
	}

	/** Drives an Elder's upheaval on by {@code ticks} ticks now (for tests: each tick with the whole budget). */
	public static void advance(Entity boss, int ticks) {
		Upheaval u = of(boss.getUUID());
		for (int i = 0; i < ticks && u != null && !u.done; i++) {
			budget = Math.max(1, Config.ELDER_UPHEAVAL_BUDGET);
			if (step(u)) {
				u.done = true;
			}
		}
		ALL.removeIf(x -> x.done);
	}

	/** This Elder's fight is over (slain or gone): the broken ground sinks back (see {@link ElderArena#release}). */
	public static void release(Entity boss) {
		Upheaval u = of(boss.getUUID());
		if (u != null) {
			sink(u);
		}
	}

	/** Everything an Elder's upheaval changed is put back at once (for tests). */
	public static void finish(Entity boss) {
		if (boss.level() instanceof ServerLevel level) {
			finish(level, boss.getUUID());
		}
		ALL.removeIf(x -> x.done);
	}

	/**
	 * Everything an Elder's upheaval changed is put back now, before its arena comes apart or is taken down (see
	 * {@link ElderArena}). What lies in a part of the world not loaded now waits for it; the arena's own floor among it
	 * is put back then as the arena would leave it (the ground from before the arena), since the arena is going.
	 */
	static void finish(ServerLevel level, UUID boss) {
		Upheaval u = of(boss);
		if (u == null || u.level != level) {
			return;
		}
		sink(u);
		for (int i = u.back; i < u.order.size(); i++) {
			u.waiting.add(u.order.get(i));
		}
		u.back = u.order.size();
		Iterator<Change> it = u.waiting.iterator();
		while (it.hasNext()) {
			Change c = it.next();
			if (Loaded.ready(level, c.pos)) {
				put(u, c);
				it.remove();
			} else if (c.piece != null) {
				c.original = c.piece.original == null ? AIR : c.piece.original;
			}
		}
		if (u.waiting.isEmpty()) {
			u.done = true;
		}
	}

	/** On stopping: everything any upheaval changed is put back at once, before the world is saved. */
	public static void clear() {
		for (Upheaval u : ALL) {
			drop(u);
			for (Change c : u.changes) {
				if (held(u.level, c.pos) == c) {
					put(u, c);
				}
			}
		}
		ALL.clear();
		HELD.clear();
	}

	// ------------------------------------------------------------------ every tick

	public static void tick(MinecraftServer s) {
		budget = Math.max(1, Config.ELDER_UPHEAVAL_BUDGET);
		if (ALL.isEmpty()) {
			return;
		}
		for (Upheaval u : List.copyOf(ALL)) {
			if (u.done) {
				continue;
			}
			if (u.manual) {
				// (a test's: it is driven by the test; if its Elder is long gone, the test is over and it sinks back by itself)
				if (u.level.getEntity(u.boss) == null && ++u.missing > 100) {
					u.manual = false;
					sink(u);
				}
				continue;
			}
			if (step(u)) {
				u.done = true;
			}
		}
		ALL.removeIf(u -> u.done);
	}

	/** One tick of an upheaval; true once it is over. */
	private static boolean step(Upheaval u) {
		u.clock++;
		Entity e = u.level.getEntity(u.boss);
		ElderBoss b = e instanceof ElderBoss eb && eb.isAlive() && !eb.isRemoved() ? eb : null;
		if (u.phase != SINK && !u.manual && u.clock % 5 == 0) {
			// Its fight over (slain, gone, let go): the ground settles; an Elder not found a while (a chunk still loading) has gone
			if (b != null && ElderFight.of(b) != null) {
				u.missing = 0;
			} else if (e != null || (u.missing += 5) > 60) {
				sink(u);
			}
		}
		shards(u);
		switch (u.phase) {
			case CHARGE -> charging(u, b);
			case ERUPT -> erupting(u, b);
			case STAND -> standing(u);
			default -> {
				return sinking(u);
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ building up

	/** An upheaval for an Elder: round its arena's floor, round {@code at}, or round where the Elder stands. */
	private static Upheaval open(ElderBoss b, BlockPos at, double radius) {
		ServerLevel level = (ServerLevel) b.level();
		Upheaval u = new Upheaval(b.getUUID(), level, ElderFx.index(b));
		for (ElderArena.Arena a : ElderArena.arenas()) {
			// (its own arena, if it stands on it: one it has wandered far from is left be, and the ground breaks round it)
			if (a.boss.equals(u.boss) && a.level == level && !a.dissolving && a.bp != null
					&& sq(b.getX() - a.cx) + sq(b.getZ() - a.cz) <= sq(a.bp.floorR * 1.1 + 4)) {
				u.arena = a;
			}
		}
		u.elder(b);
		double scale = Math.max(0.1, Math.min(4, Config.ELDER_UPHEAVAL_SCALE));
		if (at != null) {
			u.cx = at.getX() + 0.5;
			u.cz = at.getZ() + 0.5;
			int g = scan(level, at.getX(), at.getZ(), at.getY());
			u.gy = g > NONE ? g : at.getY() - 1;
			u.radius = Math.max(2, radius);
		} else {
			if (u.arena != null) {
				u.cx = u.arena.cx;
				u.cz = u.arena.cz;
				u.gy = u.arena.bp.gy;
				u.radius = Math.max(8, u.arena.bp.floorR * scale);
			} else {
				u.cx = u.ex;
				u.cz = u.ez;
				u.gy = u.ey;
				u.radius = Math.max(8, OPEN_RADIUS * scale);
			}
			// (never beyond the reach of the ultimate itself)
			u.radius = Math.min(u.radius, Math.max(16, Config.ELDER_ULTIMATE_RADIUS));
		}
		u.lift = Math.max(0.3, Math.min(1, u.radius / OPEN_RADIUS));
		u.turn = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
		return u;
	}

	/** The cracks it will run out along, from the Elder towards the edge, a few of them forking. */
	private static void fissures(Upheaval u, ThreadLocalRandom rnd) {
		int n = (int) Math.max(4, Math.min(14, Math.round(5 + u.radius / 12)));
		double turn = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double a = turn + i * Math.PI * 2 / n + rnd.nextDouble(-0.25, 0.25);
			double len = u.radius * rnd.nextDouble(0.55, 1.0);
			Fissure f = path(u, u.ex + Math.cos(a) * u.clear, u.ez + Math.sin(a) * u.clear, a, len, 0, rnd);
			if (f.xs.length == 0) {
				continue;
			}
			u.fissures.add(f);
			if (f.xs.length > 8 && rnd.nextDouble() < 0.4) {
				int at = (int) (f.xs.length * rnd.nextDouble(0.3, 0.7));
				double b = a + (rnd.nextBoolean() ? 1 : -1) * rnd.nextDouble(0.5, 0.9);
				Fissure fork = path(u, f.xs[at], f.zs[at], b, len * rnd.nextDouble(0.25, 0.45), at, rnd);
				if (fork.xs.length > 0) {
					u.fissures.add(fork);
				}
			}
		}
	}

	/** A jagged crack from a point, kinking every few blocks about its way; it stops at the edge. */
	private static Fissure path(Upheaval u, double x, double z, double heading, double len, int from, ThreadLocalRandom rnd) {
		List<double[]> pts = new ArrayList<>();
		double h = heading;
		int kink = 0;
		for (int i = 0; i < len; i++) {
			if (--kink <= 0) {
				h = heading + (h - heading) * 0.4 + rnd.nextDouble(-0.65, 0.65);
				kink = 2 + rnd.nextInt(4);
			}
			x += Math.cos(h);
			z += Math.sin(h);
			if (sq(x - u.cx) + sq(z - u.cz) > u.radius * u.radius) {
				break;
			}
			pts.add(new double[] {x, z});
		}
		return new Fissure(pts, from);
	}

	/** A tick of the ultimate building: the tremor, the cracks running out and glowing, blocks tearing loose, grit. */
	private static void charging(Upheaval u, ElderBoss b) {
		ServerLevel level = u.level;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int t = u.age++;
		double k = Math.min(1, t / (double) u.length);
		if (t % 20 == 0) {
			String dim = level.dimension().identifier().toString();
			if (b != null) {
				ElderFx.shake(b, u.ex, u.ez, u.radius + 24, (float) (0.08 + 0.55 * k), 28);
			}
			WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", u.ex, u.ey + 1, u.ez, 2f + 3f * (float) k, 0.4f + 0.2f * (float) k);
			if (t % 40 == 0) {
				WorldFx.soundIn(dim, "minecraft:entity.warden.heartbeat", "hostile", u.ex, u.ey + 1, u.ez, 2f + 2f * (float) k, 0.5f);
			}
		}
		// The cracks run out faster than it builds (they reach their ends a little past half of it)
		int run = (int) Math.ceil(u.radius * Math.min(1, 0.05 + k * 1.6));
		for (Fissure f : u.fissures) {
			int want = Math.max(0, Math.min(f.xs.length, run - f.from));
			while (f.shown < want) {
				reveal(u, f, f.shown++, k, rnd);
			}
		}
		if (t % 2 == 0 && watched(level, u.ex, u.ey, u.ez, u.radius + 48)) {
			glow(u, rnd, k);
		}
		int most = Math.max(0, Config.ELDER_UPHEAVAL_DISPLAYS);
		int every = Math.max(2, (int) (u.length * 0.8 / Math.max(1, most)));
		if (t >= 10 && t % every == 0 && u.hovers.size() < most) {
			tear(u, rnd, k);
		}
		if (t % 2 == 1 && k > 0.1 && u.shards.size() < SHARDS) {
			shard(u, rnd, k);
		}
		orbit(u);
	}

	/** A crack reaches a point: its ground is found, and now and then a block of the crack burns in it. */
	private static void reveal(Upheaval u, Fissure f, int i, double k, ThreadLocalRandom rnd) {
		int x = (int) Math.floor(f.xs[i]), z = (int) Math.floor(f.zs[i]);
		int y = top(u, x, z, u.ey);
		f.ys[i] = y;
		if (y <= NONE) {
			return;
		}
		if (i % 3 == 0) {
			WorldFx.particleIn(u.level, look(u.style).glow2, f.xs[i], y + 1.1, f.zs[i], 0.2, 0.05, 0.2, 0.02, 2);
		}
		// (only once it is well under way: an ultimate cut short at once leaves the ground as it was)
		if (u.grief && k >= 0.15 && u.cracks < CRACKS && rnd.nextDouble() < 0.16 && u.changed < Math.max(0, Config.ELDER_UPHEAVAL_BLOCKS)
				&& set(u, new BlockPos(x, y, z), look(u.style).crack, y)) {
			u.cracks++;
		}
	}

	/** The cracks glow along all their length so far, more and more. */
	private static void glow(Upheaval u, ThreadLocalRandom rnd, double k) {
		if (u.fissures.isEmpty()) {
			return;
		}
		Look look = look(u.style);
		int n = 4 + (int) (14 * k);
		for (int j = 0; j < n; j++) {
			Fissure f = u.fissures.get(rnd.nextInt(u.fissures.size()));
			if (f.shown == 0) {
				continue;
			}
			int i = rnd.nextInt(f.shown);
			if (f.ys[i] <= NONE) {
				continue;
			}
			WorldFx.particleIn(u.level, j % 3 == 0 ? look.glow2 : look.glow, f.xs[i], f.ys[i] + 1.05, f.zs[i], 0.25, 0.05, 0.25, 0.01, 1 + (int) (2 * k));
		}
		// dust where the cracks are splitting the ground now
		if (u.age % 4 == 0) {
			int puffs = 0;
			for (Fissure f : u.fissures) {
				if (f.shown > 0 && f.shown < f.xs.length && f.ys[f.shown - 1] > NONE && puffs++ < 6) {
					WorldFx.particleIn(u.level, "minecraft:campfire_cosy_smoke", f.xs[f.shown - 1], f.ys[f.shown - 1] + 1.1, f.zs[f.shown - 1], 0.3, 0.1, 0.3, 0.01, 2);
				}
			}
		}
	}

	/** A block of the floor tears loose and rises, to hover round the Elder (with griefing, out of the ground itself). */
	private static void tear(Upheaval u, ThreadLocalRandom rnd, double k) {
		ServerLevel level = u.level;
		int x, z;
		Fissure f = u.fissures.isEmpty() ? null : u.fissures.get(rnd.nextInt(u.fissures.size()));
		if (f != null && f.shown > 0 && rnd.nextDouble() < 0.7) {
			int i = rnd.nextInt(f.shown);
			x = (int) Math.floor(f.xs[i] + rnd.nextDouble(-1.5, 1.5));
			z = (int) Math.floor(f.zs[i] + rnd.nextDouble(-1.5, 1.5));
		} else {
			double a = rnd.nextDouble() * Math.PI * 2, r = u.clear + rnd.nextDouble() * Math.max(1, u.radius * Math.max(0.2, k) - u.clear);
			x = (int) Math.floor(u.ex + Math.cos(a) * r);
			z = (int) Math.floor(u.ez + Math.sin(a) * r);
		}
		int y = top(u, x, z, u.ey);
		if (y <= NONE) {
			return;
		}
		BlockPos pos = new BlockPos(x, y, z);
		if (!inside(u, pos) || !watched(level, x, y, z, 96)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!solid(state) || state.hasBlockEntity() || holds(level, pos)) {
			return;
		}
		if (u.grief && k >= 0.15 && u.changed < Math.max(0, Config.ELDER_UPHEAVAL_BLOCKS)) {
			// (and whatever grows on it goes with it)
			for (int j = 1; j <= 2; j++) {
				clearPlant(u, pos.above(j), y);
			}
			cut(u, pos, AIR, y, -1);
		}
		Display.BlockDisplay d = BlockFlight.spawn(level, x, y + 1, z, state, BlockFlight.pose(0, 0, 0, new Quaternionf(), 1F));
		if (d == null) {
			return;
		}
		Hover h = new Hover(d, x, y, z, state);
		double ox = x + 0.5 - u.ex, oz = z + 0.5 - u.ez;
		h.rad = Math.max(u.clear + 2, Math.min(u.radius, Math.sqrt(ox * ox + oz * oz) * rnd.nextDouble(0.55, 0.9)));
		h.ang = Math.atan2(oz, ox);
		h.hgt = 4 + rnd.nextDouble() * (5 + 8 * k);
		h.w = u.turn * rnd.nextDouble(0.012, 0.03);
		h.axis = BlockFlight.randomAxis(rnd);
		h.spinRate = (float) rnd.nextDouble(0.02, 0.08);
		u.hovers.add(h);
		WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", x + 0.5, y + 1.1, z + 0.5, 0.3, 0.1, 0.3, 0.02, 3);
		if (rnd.nextInt(3) == 0) {
			WorldFx.soundIn(level.dimension().identifier().toString(), look(u.style).rise, "hostile", x + 0.5, y + 1, z + 0.5, 1.5f,
					(float) rnd.nextDouble(0.6, 0.9));
		}
	}

	/** What hovers rises, then turns round the Elder, bobbing and tumbling slowly. */
	private static void orbit(Upheaval u) {
		Iterator<Hover> it = u.hovers.iterator();
		while (it.hasNext()) {
			Hover h = it.next();
			if (h.d.isRemoved()) {
				BlockFlight.forget(h.d);
				it.remove();
				continue;
			}
			h.age++;
			if (h.dropAt >= 0) {
				if (h.age >= h.dropAt) {
					back(u, h);
					BlockFlight.drop(h.d);
					it.remove();
				}
				continue;
			}
			if (h.slam >= 0) {
				continue;
			}
			if (h.age == 2) {
				move(u, h, 24);
			} else if (h.age > 26 && (h.age - 26) % 10 == 0) {
				move(u, h, 10);
			}
		}
	}

	private static void move(Upheaval u, Hover h, int ticks) {
		h.ang += h.w * ticks;
		h.spin.rotateAxis(h.spinRate * ticks, h.axis);
		double x = u.ex + Math.cos(h.ang) * h.rad, z = u.ez + Math.sin(h.ang) * h.rad;
		double y = u.ey + 1 + h.hgt + Math.sin((h.age + ticks) * 0.07) * 0.5;
		show(h, x, y, z, ticks, false);
	}

	/** A hovering block shown with its middle at (x, y, z), easing there over {@code ticks}; upright if {@code square}. */
	private static void show(Hover h, double x, double y, double z, int ticks, boolean square) {
		DisplayAccess acc = (DisplayAccess) h.d;
		acc.extremedimensions$setTransformation(BlockFlight.pose(x - h.bx - 0.5, y - h.by - 0.5, z - h.bz - 0.5, square ? new Quaternionf() : h.spin, 1F));
		acc.extremedimensions$setInterpolationDuration(ticks);
		acc.extremedimensions$setInterpolationDelay(0);
	}

	/** A block dropped back into its place: what was torn out there is put back (it never landed anywhere else). */
	private static void back(Upheaval u, Hover h) {
		BlockPos at = new BlockPos(h.bx, h.by, h.bz);
		Change c = held(u.level, at);
		if (c != null && c.owner == u && !c.watch && Loaded.ready(u.level, at) && u.level.getBlockState(at).isAir()) {
			put(u, c);
		}
	}

	/** Grit flung out of a crack, near its head. */
	private static void shard(Upheaval u, ThreadLocalRandom rnd, double k) {
		if (u.fissures.isEmpty()) {
			return;
		}
		Fissure f = u.fissures.get(rnd.nextInt(u.fissures.size()));
		if (f.shown == 0) {
			return;
		}
		int i = f.shown - 1 - rnd.nextInt(Math.min(f.shown, 6));
		int y = f.ys[i];
		if (y <= NONE) {
			return;
		}
		int x = (int) Math.floor(f.xs[i]), z = (int) Math.floor(f.zs[i]);
		BlockPos pos = new BlockPos(x, y, z);
		if (!Loaded.ready(u.level, pos) || !watched(u.level, x, y, z, 48)) {
			return;
		}
		Look look = look(u.style);
		BlockState state = rnd.nextBoolean() ? u.level.getBlockState(pos) : look.rubble[rnd.nextInt(look.rubble.length)];
		if (state.isAir() || state.hasBlockEntity()) {
			return;
		}
		float size = (float) rnd.nextDouble(0.22, 0.5);
		Quaternionf spin = new Quaternionf().rotateAxis((float) rnd.nextDouble(0, 3), BlockFlight.randomAxis(rnd));
		Display.BlockDisplay d = BlockFlight.spawn(u.level, x, y + 1, z, state, BlockFlight.pose(0, 0, 0, spin, size));
		if (d == null) {
			return;
		}
		Shard s = new Shard(d, size, spin);
		double a = rnd.nextDouble() * Math.PI * 2, out = rnd.nextDouble(1.5, 4.5);
		s.dx = Math.cos(a) * out;
		s.dz = Math.sin(a) * out;
		s.up = rnd.nextDouble(2, 4.5) * (0.7 + 0.5 * k);
		u.shards.add(s);
	}

	/** Grit on its arc: up out of the crack, over, down beside it, and gone in a puff. */
	private static void shards(Upheaval u) {
		if (u.shards.isEmpty()) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		Iterator<Shard> it = u.shards.iterator();
		while (it.hasNext()) {
			Shard s = it.next();
			if (s.d.isRemoved()) {
				BlockFlight.forget(s.d);
				it.remove();
				continue;
			}
			s.age++;
			DisplayAccess acc = (DisplayAccess) s.d;
			if (s.age == 2 || s.age == 8) {
				boolean high = s.age == 2;
				s.spin.rotateAxis((float) rnd.nextDouble(1, 2.5), BlockFlight.randomAxis(rnd));
				acc.extremedimensions$setTransformation(BlockFlight.pose(s.dx * (high ? 0.5 : 1), high ? s.up : 0.5 + s.size / 2, s.dz * (high ? 0.5 : 1), s.spin, s.size));
				acc.extremedimensions$setInterpolationDuration(6);
				acc.extremedimensions$setInterpolationDelay(0);
			} else if (s.age >= 14) {
				WorldFx.particleIn(u.level, "minecraft:poof", s.d.getX() + 0.5 + s.dx, s.d.getY() - 0.4, s.d.getZ() + 0.5 + s.dz, 0.1, 0.05, 0.1, 0.01, 2);
				BlockFlight.drop(s.d);
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------ the eruption

	/** The ground breaks: what it will throw up is laid out (each piece looks over its ground once its time comes). */
	private static void start(Upheaval u, ElderBoss b) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		u.phase = ERUPT;
		u.age = 0;
		u.grief = ElderTerrain.griefing(u.level);
		u.elder(b);
		plan(u, rnd);
		slams(u, rnd);
		Look look = look(u.style);
		String dim = u.level.dimension().identifier().toString();
		WorldFx.soundIn(dim, look.big, "hostile", u.ex, u.ey + 2, u.ez, 6f, 0.8f);
		WorldFx.soundIn(dim, "minecraft:entity.wither.break_block", "hostile", u.ex, u.ey + 2, u.ez, 6f, 0.4f);
		WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", u.ex, u.ey + 2, u.ez, 5f, 0.5f);
		WorldFx.particleIn(u.level, "minecraft:large_smoke", u.ex, u.ey + 1.5, u.ez, 3, 0.6, 3, 0.08, 40);
		AttackFx.wave(u.level, u.style, false, u.ex, u.ey + 1.1, u.ez, u.clear, u.radius / WAVE, u.radius);
	}

	/** The pieces the ground breaks into, each at its time: the chasms first, then outwards from the Elder. */
	private static void plan(Upheaval u, ThreadLocalRandom rnd) {
		double r = u.radius;
		double grow = Math.pow(sq(r / 24), 0.6);
		List<Feature> out = u.features;
		out.clear();
		u.next = 0;
		u.chasms = 0;
		int chasms = 1 + (r >= 14 && rnd.nextDouble() < 0.6 ? 1 : 0);
		double ca = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < chasms; i++) {
			boolean across = i == 0 && rnd.nextBoolean();
			out.add(new Feature(CHASM, rnd.nextInt(3) + i * 4, u.cx, u.cz, ca + i * Math.PI * rnd.nextDouble(0.6, 1.4), r, across));
		}
		// the ring of cliffs round the arena
		int ring = (int) Math.max(3, Math.min(48, Math.round(Math.PI * 2 * r * 0.86 / 11)));
		double ra = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < ring; i++) {
			double a = ra + (i + rnd.nextDouble(-0.12, 0.12)) * Math.PI * 2 / ring;
			double d = r * rnd.nextDouble(0.8, 0.93);
			double x = u.cx + Math.cos(a) * d, z = u.cz + Math.sin(a) * d;
			// (most lean out, away from the middle)
			out.add(new Feature(CLIFF, startAt(u, x, z, rnd) + 4, x, z, a + Math.PI / 2 + rnd.nextDouble(-0.3, 0.3),
					(rnd.nextDouble() < 0.7 ? 1 : -1) * rnd.nextDouble(0.25, 0.7), false));
		}
		strew(u, rnd, CLIFF, (int) Math.max(1, Math.min(24, Math.round(2 + 1.5 * grow))), 0.25, 0.8);
		strew(u, rnd, SPIKES, (int) Math.max(2, Math.min(30, Math.round(3 + 2 * grow))), 0.2, 0.85);
		strew(u, rnd, CRATER, (int) Math.max(1, Math.min(16, Math.round(1 + grow))), 0.2, 0.8);
		strew(u, rnd, BUMP, (int) Math.max(3, Math.min(40, Math.round(4 + 3 * grow))), 0.15, 0.9);
		if (u.style == 0) {
			// the Matriarch's roots
			strew(u, rnd, ROOT, (int) Math.max(2, Math.min(10, Math.round(2 + Math.sqrt(sq(r / 24))))), 0.2, 0.75);
		}
		out.sort(Comparator.comparingInt(Feature::start));
	}

	/** Pieces of a kind strewn over the arena between {@code lo} and {@code hi} of its reach from its middle. */
	private static void strew(Upheaval u, ThreadLocalRandom rnd, int kind, int n, double lo, double hi) {
		for (int i = 0; i < n; i++) {
			for (int tries = 0; tries < 6; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, d = u.radius * Math.sqrt(rnd.nextDouble(lo * lo, hi * hi));
				double x = u.cx + Math.cos(a) * d, z = u.cz + Math.sin(a) * d;
				if (u.gy + 1 >= u.bodyLow && u.gy + 1 <= u.bodyHigh && sq(x - u.ex) + sq(z - u.ez) <= sq(u.clear + 3)) {
					// (not at the Elder's feet)
					continue;
				}
				double lean = kind == CLIFF ? (rnd.nextBoolean() ? 1 : -1) * rnd.nextDouble(0.2, 0.6) : 0;
				u.features.add(new Feature(kind, startAt(u, x, z, rnd) + (kind == ROOT ? 8 : 0), x, z, rnd.nextDouble() * Math.PI * 2, lean, false));
				break;
			}
		}
	}

	/** When a piece at (x, z) breaks out: as the eruption runs out from the Elder to the edge. */
	private static int startAt(Upheaval u, double x, double z, ThreadLocalRandom rnd) {
		double d = Math.sqrt(sq(x - u.ex) + sq(z - u.ez));
		return (int) Math.round(Math.min(1.2, d / u.radius) * WAVE) + rnd.nextInt(6);
	}

	/** Where each hovering block will slam down: by someone, or anywhere (without griefing, back into its place). */
	private static void slams(Upheaval u, ThreadLocalRandom rnd) {
		bodies(u);
		for (Hover h : u.hovers) {
			if (h.dropAt >= 0) {
				continue;
			}
			h.slam = rnd.nextInt(10);
			h.onGround = false;
			if (u.grief) {
				double x, z;
				if (!u.targets.isEmpty() && rnd.nextDouble() < 0.6) {
					ServerPlayer p = u.targets.get(rnd.nextInt(u.targets.size()));
					x = p.getX() + rnd.nextGaussian() * 3;
					z = p.getZ() + rnd.nextGaussian() * 3;
				} else {
					double a = rnd.nextDouble() * Math.PI * 2, d = u.radius * Math.sqrt(rnd.nextDouble());
					x = u.cx + Math.cos(a) * d;
					z = u.cz + Math.sin(a) * d;
				}
				int tx = (int) Math.floor(x), tz = (int) Math.floor(z);
				int ty = top(u, tx, tz, u.gy);
				if (ty > NONE && inside(u, new BlockPos(tx, ty + 1, tz))) {
					h.tx = tx;
					h.ty = ty;
					h.tz = tz;
					h.onGround = true;
				}
			}
			if (!h.onGround) {
				// (back into its own place: the block under it is what it rests on)
				h.tx = h.bx;
				h.ty = h.by - 1;
				h.tz = h.bz;
			}
		}
	}

	/** A tick of the eruption: the pieces whose time has come break out, what hovers slams down, blocks change. */
	private static void erupting(Upheaval u, ElderBoss b) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int t = u.age;
		bodies(u);
		// (a few pieces a tick, each looking over its ground once: never long, whatever the size of the arena)
		int begun = 0;
		long until = System.nanoTime() + 4_000_000L;
		while (u.next < u.features.size() && u.features.get(u.next).start() <= t && begun < 8 && (begun == 0 || System.nanoTime() < until)) {
			begin(u, b, u.features.get(u.next++), rnd);
			begun++;
		}
		landing(u, b);
		apply(u, t, rnd);
		if (t % 12 == 6 && b != null) {
			ElderFx.shake(b, u.ex, u.ez, u.radius + 32, (float) Math.max(0.25, 0.9 - t * 0.01), 28);
		}
		u.age++;
		if (u.next >= u.features.size() && u.ops.isEmpty() && u.hovers.isEmpty()) {
			u.phase = STAND;
			u.age = 0;
		}
	}

	/** The broken ground stands until the fight is over: dust and the glow of the chasms now and then. */
	private static void standing(Upheaval u) {
		orbit(u);
		if (u.age++ % 10 != 0 || u.changes.isEmpty()) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		Look look = look(u.style);
		for (int i = 0; i < 3; i++) {
			Change c = u.changes.get(rnd.nextInt(u.changes.size()));
			if (c.watch || !watched(u.level, c.pos.getX(), c.pos.getY(), c.pos.getZ(), 64)) {
				continue;
			}
			boolean hole = solid(c.original) && open(c.placed);
			WorldFx.particleIn(u.level, hole ? look.deep : "minecraft:white_ash", c.pos.getX() + 0.5, c.pos.getY() + (hole ? 0.2 : 1.1), c.pos.getZ() + 0.5,
					0.3, 0.1, 0.3, 0.01, hole ? 2 : 1);
		}
	}

	/** Who is near now: nothing is built into anyone, and those in the way are thrown. */
	private static void bodies(Upheaval u) {
		u.bodies.clear();
		u.targets.clear();
		double r = u.radius + 8;
		for (ServerPlayer p : u.level.players()) {
			if (p.isSpectator() || sq(p.getX() - u.cx) + sq(p.getZ() - u.cz) > r * r) {
				continue;
			}
			u.bodies.add(p);
			if (p.isAlive() && !p.isCreative()) {
				u.targets.add(p);
			}
		}
	}

	/** Whoever stands where the ground erupts round (x, y, z) within {@code reach} is thrown up, and hurt a little. */
	private static void toss(Upheaval u, ElderBoss b, double x, double y, double z, double reach, double up) {
		for (ServerPlayer p : u.targets) {
			if (sq(p.getX() - x) + sq(p.getZ() - z) > reach * reach || Math.abs(p.getY() - y) > 4) {
				continue;
			}
			Integer last = u.hit.get(p.getUUID());
			if (last != null && u.clock - last < 12) {
				continue;
			}
			u.hit.put(p.getUUID(), u.clock);
			double damage = Math.max(0, Config.ELDER_UPHEAVAL_DAMAGE);
			if (b != null && damage > 0) {
				p.hurtServer(u.level, ElderPower.great(u.level, b), (float) damage);
			}
			ElderFx.knock(p, x, z, 0.3, Math.max(0, Config.ELDER_UPHEAVAL_THROW) * up);
			WorldFx.particleIn(u.level, "minecraft:cloud", p.getX(), p.getY() + 0.2, p.getZ(), 0.4, 0.2, 0.4, 0.05, 8);
		}
	}

	/** What hovers sets off and slams down, each at its time; where it lands it lies as a block (with griefing). */
	private static void landing(Upheaval u, ElderBoss b) {
		Iterator<Hover> it = u.hovers.iterator();
		while (it.hasNext()) {
			Hover h = it.next();
			if (h.d.isRemoved()) {
				BlockFlight.forget(h.d);
				it.remove();
				continue;
			}
			if (h.dropAt >= 0) {
				h.age++;
				if (h.age >= h.dropAt) {
					back(u, h);
					BlockFlight.drop(h.d);
					it.remove();
				}
				continue;
			}
			if (u.age == h.slam) {
				int dur = 6 + h.slam % 3;
				h.lands = h.slam + dur;
				// it turns upright as it falls, to lie square where it lands
				show(h, h.tx + 0.5, h.ty + 1.5, h.tz + 0.5, dur, true);
			} else if (h.lands >= 0 && u.age >= h.lands) {
				land(u, b, h);
				BlockFlight.drop(h.d);
				it.remove();
			}
		}
	}

	private static void land(Upheaval u, ElderBoss b, Hover h) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double x = h.tx + 0.5, y = h.ty + 1, z = h.tz + 0.5;
		WorldFx.particleIn(u.level, "minecraft:explosion", x, y + 0.3, z, 0.4, 0.2, 0.4, 0, 1);
		WorldFx.particleIn(u.level, "minecraft:campfire_cosy_smoke", x, y + 0.2, z, 0.6, 0.1, 0.6, 0.02, 4);
		if (rnd.nextInt(2) == 0) {
			WorldFx.soundIn(u.level.dimension().identifier().toString(), "minecraft:block.anvil.land", "hostile", x, y, z, 1.6f,
					(float) rnd.nextDouble(0.4, 0.6));
		}
		if (h.onGround) {
			if (u.grief && u.changed < Math.max(0, Config.ELDER_UPHEAVAL_BLOCKS)) {
				raise(u, new BlockPos(h.tx, h.ty + 1, h.tz), stable(h.state) ? h.state : look(u.style).rubble[0], h.ty);
			}
			toss(u, b, x, y, z, 1.8, 0.6);
		} else {
			back(u, h);
		}
	}

	/** The block changes due by tick {@code t}, as many as this tick's budget allows (the rest a tick later). */
	private static void apply(Upheaval u, int t, ThreadLocalRandom rnd) {
		int cap = Math.max(0, Config.ELDER_UPHEAVAL_BLOCKS);
		int puffs = 0, sounds = 0;
		String dim = null;
		while (!u.ops.isEmpty() && u.ops.peek().due() <= t && budget > 0) {
			if (u.changed >= cap) {
				u.ops.clear();
				break;
			}
			Op op = u.ops.poll();
			budget--;
			boolean done = switch (op.kind()) {
				case RAISE -> raise(u, op.pos(), op.to(), op.ground());
				case CLEAR -> clearPlant(u, op.pos(), op.ground());
				case CUT -> cut(u, op.pos(), op.to(), op.ground(), op.tag());
				default -> set(u, op.pos(), op.to(), op.ground());
			};
			if (!done || rnd.nextInt(9) != 0) {
				continue;
			}
			BlockPos p = op.pos();
			if (puffs < 8 && watched(u.level, p.getX(), p.getY(), p.getZ(), 64)) {
				puffs++;
				boolean torn = op.kind() == CUT;
				WorldFx.particleIn(u.level, torn ? "minecraft:large_smoke" : "minecraft:campfire_cosy_smoke", p.getX() + 0.5, p.getY() + (torn ? 1.2 : 0.5),
						p.getZ() + 0.5, 0.4, 0.3, 0.4, 0.03, 2);
				if (rnd.nextInt(3) == 0) {
					WorldFx.particleIn(u.level, look(u.style).glow, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 0.4, 0.4, 0.4, 0.04, 2);
				}
			}
			if (sounds < 2 && rnd.nextInt(5) == 0) {
				sounds++;
				dim = dim != null ? dim : u.level.dimension().identifier().toString();
				WorldFx.soundIn(dim, look(u.style).rise, "hostile", p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2.5f, (float) rnd.nextDouble(0.5, 0.8));
			}
		}
	}

	/** A block change to come, {@code due} ticks from now (only where the world may be torn up). */
	private static void op(Upheaval u, int kind, int x, int y, int z, BlockState to, int due, int ground, int tag) {
		if (u.grief && to != null) {
			u.ops.add(new Op(new BlockPos(x, y, z), to, kind, u.age + Math.max(0, due), ground, tag, u.seq++));
		}
	}

	/** A piece breaks out: its sound and dust, whoever stands on it thrown, and the blocks it will change. */
	private static void begin(Upheaval u, ElderBoss b, Feature f, ThreadLocalRandom rnd) {
		int y = top(u, (int) Math.floor(f.x()), (int) Math.floor(f.z()), u.gy);
		if (y <= NONE && f.kind() != CHASM && f.kind() != ROOT) {
			// (its ground is not loaded, or under water: nothing there to break)
			return;
		}
		switch (f.kind()) {
			case CLIFF -> cliff(u, b, f, y, rnd);
			case SPIKES -> spikes(u, b, f, rnd);
			case CHASM -> chasm(u, f, rnd);
			case CRATER -> crater(u, b, f, y, rnd);
			case BUMP -> bump(u, b, f, y, rnd);
			default -> root(u, b, f, rnd);
		}
	}

	/** A tilted slab of cliff bursts up, layer on layer, leaning across its face, jagged at its top. */
	private static void cliff(Upheaval u, ElderBoss b, Feature f, int base, ThreadLocalRandom rnd) {
		Look look = look(u.style);
		int high = Math.max(2, (int) Math.round(rnd.nextDouble(6, 16) * u.lift));
		double half = Math.max(1.0, rnd.nextDouble(2.0, 4.5) * Math.sqrt(u.lift));
		double thick = rnd.nextDouble(0.8, 1.5);
		double tx = Math.cos(f.angle()), tz = Math.sin(f.angle());
		double nx = -tz, nz = tx;
		if (nx * (f.x() - u.cx) + nz * (f.z() - u.cz) < 0) {
			// (its lean is measured outwards, away from the middle)
			nx = -nx;
			nz = -nz;
		}
		double lean = f.size() * Math.sqrt(u.lift);
		boolean crag = rnd.nextDouble() < look.cragChance;
		boolean veined = look.vein != null && rnd.nextDouble() < 0.6;
		int phase = rnd.nextInt(7);
		LongOpenHashSet below = new LongOpenHashSet();
		for (int k = 0; k < high; k++) {
			double hw = half * (1 - 0.5 * k / high), ht = thick * (1 - 0.35 * k / high);
			double sx = f.x() + nx * lean * k, sz = f.z() + nz * lean * k;
			LongOpenHashSet here = new LongOpenHashSet();
			for (double a = -hw; a <= hw + 1e-6; a += 0.5) {
				for (double c = -ht; c <= ht + 1e-6; c += 0.5) {
					int bx = (int) Math.floor(sx + tx * a + nx * c), bz = (int) Math.floor(sz + tz * a + nz * c);
					double edge = Math.abs(a) / half;
					int colH = (int) Math.round(high * (1 - 0.5 * edge * edge) * (0.82 + 0.3 * noise(bx, bz)));
					if (k >= Math.max(1, colH) || !here.add(col(bx, bz))) {
						continue;
					}
					BlockState s = crag ? (noise(bx * 3 + k, bz) < 0.12 ? look.fleck : look.crag)
							: veined && Math.floorMod((int) Math.round(a * 2) + k + phase, 7) == 0 ? look.vein
							: k + 1 >= colH ? look.top : look.body(k, bx, bz);
					op(u, RAISE, bx, base + 1 + k, bz, s, k, base, -1);
					if (k >= 2 && look.hang != null && !below.contains(col(bx, bz)) && noise(bx + 7, bz - 3) < (look.hangMax > 1 ? 0.22 : 0.35)) {
						// under its overhang: chains, roots
						int n = 1 + (int) (noise(bx - 5, bz + 2) * look.hangMax);
						for (int j = 1; j <= n; j++) {
							op(u, RAISE, bx, base + 1 + k - j, bz, look.hang, k + j, base, -1);
						}
					}
				}
			}
			below = here;
		}
		double y = base + 1;
		String dim = u.level.dimension().identifier().toString();
		WorldFx.particleIn(u.level, "minecraft:explosion", f.x(), y + 0.5, f.z(), half * 0.6, 0.3, half * 0.6, 0, 2);
		WorldFx.particleIn(u.level, "minecraft:campfire_cosy_smoke", f.x(), y + 0.3, f.z(), half, 0.2, half, 0.03, 8);
		WorldFx.particleIn(u.level, look.glow, f.x(), y + 0.5, f.z(), half, 0.5, half, 0.05, 8);
		WorldFx.soundIn(dim, look.rise, "hostile", f.x(), y, f.z(), 3f, (float) rnd.nextDouble(0.45, 0.7));
		AttackFx.impact(u.level, u.style, AttackFx.IMPACT_BLAST, f.x(), y, f.z(), half);
		toss(u, b, f.x(), y, f.z(), half + 1, 1.0);
	}

	/** A cluster of spikes stands up out of the ground, two layers a tick, the middle one tallest. */
	private static void spikes(Upheaval u, ElderBoss b, Feature f, ThreadLocalRandom rnd) {
		Look look = look(u.style);
		int n = Math.max(2, (int) Math.round(rnd.nextInt(3, 8) * Math.sqrt(u.lift)));
		double spread = rnd.nextDouble(1.5, 3.5) * Math.sqrt(u.lift);
		boolean crag = rnd.nextDouble() < look.cragChance;
		LongOpenHashSet done = new LongOpenHashSet();
		int y0 = Integer.MIN_VALUE;
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = i == 0 ? 0 : spread * Math.sqrt(rnd.nextDouble());
			double sx = f.x() + Math.cos(a) * d, sz = f.z() + Math.sin(a) * d;
			int base = top(u, (int) Math.floor(sx), (int) Math.floor(sz), u.gy);
			if (base <= NONE) {
				continue;
			}
			y0 = Math.max(y0, base);
			double rb = rnd.nextDouble(0.6, 1.6) * (i == 0 ? 1.3 : 1);
			int high = Math.max(2, (int) Math.round(rnd.nextDouble(3, 10) * u.lift * (i == 0 ? 1.3 : 1)));
			double lx = rnd.nextGaussian() * 0.15, lz = rnd.nextGaussian() * 0.15;
			int delay = i * 2;
			for (int k = 0; k < high; k++) {
				double rr = rb * Math.pow(1 - k / (double) high, 0.9);
				double cx = sx + lx * k, cz = sz + lz * k;
				int fx = (int) Math.floor(cx), fz = (int) Math.floor(cz);
				int ri = (int) Math.ceil(rr);
				for (int dx = -ri; dx <= ri; dx++) {
					for (int dz = -ri; dz <= ri; dz++) {
						double px = fx + dx + 0.5 - cx, pz = fz + dz + 0.5 - cz;
						boolean middle = dx == 0 && dz == 0;
						if (!middle && px * px + pz * pz > rr * rr) {
							continue;
						}
						int y = base + 1 + k;
						if (!done.add(new BlockPos(fx + dx, y, fz + dz).asLong())) {
							continue;
						}
						BlockState s = crag ? look.crag : middle && look.core != null ? look.core : look.spike;
						op(u, RAISE, fx + dx, y, fz + dz, s, delay + k / 2, base, -1);
					}
				}
			}
			if (look.tip != null && !crag) {
				op(u, RAISE, (int) Math.floor(sx + lx * high), base + 1 + high, (int) Math.floor(sz + lz * high), look.tip, delay + high / 2 + 1, base, -1);
			}
		}
		if (y0 == Integer.MIN_VALUE) {
			return;
		}
		WorldFx.particleIn(u.level, "minecraft:crit", f.x(), y0 + 1.5, f.z(), spread, 0.6, spread, 0.3, 12);
		WorldFx.particleIn(u.level, look.glow, f.x(), y0 + 1, f.z(), spread, 0.4, spread, 0.05, 6);
		WorldFx.soundIn(u.level.dimension().identifier().toString(), look.rise, "hostile", f.x(), y0 + 1, f.z(), 2.5f, (float) rnd.nextDouble(0.8, 1.1));
		toss(u, b, f.x(), y0 + 1, f.z(), spread + 1.5, 1.1);
	}

	/**
	 * A chasm splits the ground, two to four blocks wide and jagged, right across the middle or out from the Elder to
	 * the edge, racing along; each column opens only as deep as solid ground goes on under it, a block of it at least
	 * left under the chasm's floor.
	 */
	private static void chasm(Upheaval u, Feature f, ThreadLocalRandom rnd) {
		Look look = look(u.style);
		ServerLevel level = u.level;
		double dx = Math.cos(f.angle()), dz = Math.sin(f.angle());
		// (where the Elder stands in the middle, it splits round its feet)
		double x0 = f.across() ? u.cx - dx * u.radius : u.cx, z0 = f.across() ? u.cz - dz * u.radius : u.cz;
		double len = f.across() ? 2 * u.radius : u.radius;
		double wide = Math.max(2, rnd.nextDouble(2, 4) * (0.7 + 0.3 * u.lift));
		double deep = rnd.nextDouble(2, 5) * (0.6 + 0.4 * u.lift);
		double nx = -dz, nz = dx;
		// (it races along, across the greatest arena within two and a half seconds)
		double speed = Math.max(2.5, len / 50);
		int tag = u.chasms++;
		Long2IntOpenHashMap depth = new Long2IntOpenHashMap(), when = new Long2IntOpenHashMap();
		LongOpenHashSet lips = new LongOpenHashSet();
		double side = 0, drift = 0;
		int seg = 0;
		for (double s = 0; s <= len; s += 0.5) {
			if (s >= seg * 3) {
				seg++;
				drift = drift * 0.5 + rnd.nextGaussian() * 0.6;
			}
			side = Math.max(-u.radius * 0.15, Math.min(u.radius * 0.15, side + drift * 0.25));
			double px = x0 + dx * s + nx * side, pz = z0 + dz * s + nz * side;
			double k = s / len;
			int fx = (int) Math.floor(px), fz = (int) Math.floor(pz);
			double hw = Math.max(0.9, wide * (0.45 + 0.55 * Math.sin(Math.PI * Math.min(1, k * 1.1))) / 2 + (noise(fx, fz) - 0.5) * 0.6);
			int d = Math.max(1, (int) Math.round(deep * (0.5 + 0.5 * Math.sin(Math.PI * k))));
			int ri = (int) Math.ceil(hw + 1);
			for (int ix = -ri; ix <= ri; ix++) {
				for (int iz = -ri; iz <= ri; iz++) {
					int bx = fx + ix, bz = fz + iz;
					double q = Math.sqrt(sq(bx + 0.5 - px) + sq(bz + 0.5 - pz));
					long key = col(bx, bz);
					if (q <= hw) {
						if (depth.get(key) < d) {
							depth.put(key, d);
						}
						if (!when.containsKey(key)) {
							when.put(key, (int) (s / speed));
						}
					} else if (q <= hw + 1 && noise(bx, bz) < 0.22) {
						lips.add(key);
					}
				}
			}
		}
		for (long key : depth.keySet()) {
			int bx = (int) (key >> 32), bz = (int) key;
			int top = top(u, bx, bz, u.gy);
			if (top <= NONE) {
				continue;
			}
			int at = when.get(key);
			int d = Math.min(depth.get(key), run(level, bx, top, bz, depth.get(key) + 1) - 1);
			if (d <= 0) {
				continue;
			}
			// what grows on it goes first; then it opens from the top down
			for (int j = 1; j <= 2; j++) {
				if (!level.getBlockState(new BlockPos(bx, top + j, bz)).isAir()) {
					op(u, CLEAR, bx, top + j, bz, AIR, at, top, -1);
				}
			}
			// its floor glows (the Ferryman's burns: its soul soil goes in before the fire over it, or the fire goes out)
			boolean fire = look.fire != null && noise(bx, bz + 5) < 0.4;
			if (fire) {
				op(u, SET, bx, top - d, bz, look.floor[0], at + d - 1, top, -1);
			} else if (look.floor.length > 0 && noise(bx - 3, bz) < look.floorShare) {
				op(u, SET, bx, top - d, bz, look.floor[(int) (noise(bx, bz - 9) * look.floor.length)], at + d - 1, top, -1);
			}
			for (int j = 0; j < d; j++) {
				op(u, CUT, bx, top - j, bz, j == d - 1 && fire ? look.fire : AIR, at + j, top, tag);
			}
		}
		// its lips, thrown up and broken
		for (long key : lips) {
			if (depth.containsKey(key)) {
				continue;
			}
			int bx = (int) (key >> 32), bz = (int) key;
			int top = top(u, bx, bz, u.gy);
			if (top > NONE) {
				int at = (int) (Math.sqrt(sq(bx - x0) + sq(bz - z0)) / speed);
				op(u, RAISE, bx, top + 1, bz, look.rubble[(int) (noise(bx + 1, bz) * look.rubble.length)], at + 1, top, -1);
			}
		}
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "minecraft:entity.wither.break_block", "hostile", x0, u.gy + 1, z0, 4f, 0.5f);
		WorldFx.soundIn(dim, look.rise, "hostile", x0 + dx * len / 2, u.gy + 1, z0 + dz * len / 2, 4f, 0.4f);
	}

	/** A crater blows open: a bowl torn out of the ground, its rim thrown up and broken. */
	private static void crater(Upheaval u, ElderBoss b, Feature f, int y0, ThreadLocalRandom rnd) {
		Look look = look(u.style);
		ServerLevel level = u.level;
		double rc = rnd.nextDouble(2.5, 6) * Math.sqrt(u.lift);
		int dc = Math.max(1, (int) Math.round(rc * 0.55));
		int cx = (int) Math.floor(f.x()), cz = (int) Math.floor(f.z());
		int ri = (int) Math.ceil(rc + 2);
		for (int ix = -ri; ix <= ri; ix++) {
			for (int iz = -ri; iz <= ri; iz++) {
				int bx = cx + ix, bz = cz + iz;
				double r = Math.sqrt(sq(bx + 0.5 - f.x()) + sq(bz + 0.5 - f.z()));
				if (r > rc + 1.7) {
					continue;
				}
				int top = top(u, bx, bz, u.gy);
				if (top <= NONE) {
					continue;
				}
				if (r <= rc) {
					int want = (int) Math.round(dc * (1 - sq(r / rc)) + 0.3);
					int d = want <= 0 ? 0 : Math.min(want, run(level, bx, top, bz, want + 1) - 1);
					if (d <= 0) {
						continue;
					}
					for (int j = 1; j <= 2; j++) {
						if (!level.getBlockState(new BlockPos(bx, top + j, bz)).isAir()) {
							op(u, CLEAR, bx, top + j, bz, AIR, 0, top, -1);
						}
					}
					for (int j = 0; j < d; j++) {
						op(u, CUT, bx, top - j, bz, AIR, j, top, -1);
					}
					if (look.scorch != null && noise(bx, bz) < 0.3) {
						op(u, SET, bx, top - d, bz, look.scorch, d, top, -1);
					}
				} else if (noise(bx, bz) > 0.28) {
					int h = 1 + (int) Math.round((1 - (r - rc) / 1.7) * rnd.nextInt(0, 3));
					for (int j = 1; j <= h; j++) {
						op(u, RAISE, bx, top + j, bz, look.rubble[rnd.nextInt(look.rubble.length)], j, top, -1);
					}
				}
			}
		}
		double y = y0 + 1;
		String dim = level.dimension().identifier().toString();
		WorldFx.particleIn(level, "minecraft:explosion", f.x(), y + 0.5, f.z(), rc * 0.5, 0.4, rc * 0.5, 0, 3);
		WorldFx.particleIn(level, "minecraft:large_smoke", f.x(), y + 0.5, f.z(), rc * 0.5, 0.5, rc * 0.5, 0.05, 12);
		WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", f.x(), y, f.z(), 3f, (float) rnd.nextDouble(0.5, 0.7));
		AttackFx.impact(level, u.style, AttackFx.IMPACT_BLAST, f.x(), y, f.z(), rc);
		toss(u, b, f.x(), y, f.z(), rc + 1.7, 1.0);
	}

	/** The ground heaves in a low bump (of its own stone where it is plain stone or earth). */
	private static void bump(Upheaval u, ElderBoss b, Feature f, int y0, ThreadLocalRandom rnd) {
		Look look = look(u.style);
		ServerLevel level = u.level;
		double rb = rnd.nextDouble(2, 4.5) * Math.sqrt(u.lift);
		double hb = rnd.nextDouble(1, 2.6) * (0.6 + 0.4 * u.lift);
		int cx = (int) Math.floor(f.x()), cz = (int) Math.floor(f.z());
		int ri = (int) Math.ceil(rb);
		for (int ix = -ri; ix <= ri; ix++) {
			for (int iz = -ri; iz <= ri; iz++) {
				int bx = cx + ix, bz = cz + iz;
				double r = Math.sqrt(sq(bx + 0.5 - f.x()) + sq(bz + 0.5 - f.z()));
				int h = r > rb ? 0 : (int) Math.round(hb * (1 - sq(r / rb)) + 0.25);
				if (h <= 0) {
					continue;
				}
				int top = top(u, bx, bz, u.gy);
				if (top <= NONE) {
					continue;
				}
				BlockState ground = level.getBlockState(new BlockPos(bx, top, bz));
				BlockState s = stable(ground) ? ground : look.relief;
				for (int j = 1; j <= h; j++) {
					BlockState put = j < h && s.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : s;
					op(u, RAISE, bx, top + j, bz, put, (j - 1) * 2 + (int) r, top, -1);
				}
			}
		}
		WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", f.x(), y0 + 1.2, f.z(), rb * 0.6, 0.1, rb * 0.6, 0.02, 5);
		if (rnd.nextInt(2) == 0) {
			WorldFx.soundIn(level.dimension().identifier().toString(), look.rise, "hostile", f.x(), y0 + 1, f.z(), 1.8f, (float) rnd.nextDouble(0.4, 0.6));
		}
		toss(u, b, f.x(), y0 + 1, f.z(), rb, 0.6);
	}

	/** One of the Matriarch's roots tears out of the ground, arcs over and dives back in, its feet tearing the ground. */
	private static void root(Upheaval u, ElderBoss b, Feature f, ThreadLocalRandom rnd) {
		Look look = look(u.style);
		ServerLevel level = u.level;
		double span = rnd.nextDouble(10, 24) * Math.sqrt(u.lift);
		double dx = Math.cos(f.angle()), dz = Math.sin(f.angle());
		double ax = f.x(), az = f.z(), bx = ax + dx * span, bz = az + dz * span;
		int ga = top(u, (int) Math.floor(ax), (int) Math.floor(az), u.gy), gb = top(u, (int) Math.floor(bx), (int) Math.floor(bz), u.gy);
		if (ga <= NONE || gb <= NONE) {
			return;
		}
		double apex = span * rnd.nextDouble(0.3, 0.45);
		double thick = Math.max(0.8, rnd.nextDouble(1.1, 1.9) * Math.sqrt(u.lift));
		BlockState wood = axis(look.root, Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z);
		Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
		for (double s = -0.06; s <= 1.06; s += 0.5 / span) {
			double c = Math.max(0, Math.min(1, s));
			double px = ax + dx * span * s, pz = az + dz * span * s;
			double py = ga + (gb - ga) * s + 1 + apex * 4 * s * (1 - s);
			double r = thick * (0.65 + 0.35 * Math.sin(Math.PI * c));
			int due = (int) Math.round(c * 18);
			int fx = (int) Math.floor(px), fy = (int) Math.floor(py), fz = (int) Math.floor(pz);
			int ri = (int) Math.ceil(r);
			for (int ix = -ri; ix <= ri; ix++) {
				for (int iy = -ri; iy <= ri; iy++) {
					for (int iz = -ri; iz <= ri; iz++) {
						int x = fx + ix, y = fy + iy, z = fz + iz;
						double qy = y + 0.5 - py, q2 = sq(x + 0.5 - px) + sq(qy) + sq(z + 0.5 - pz);
						long key = new BlockPos(x, y, z).asLong();
						if (q2 > r * r || cells.containsKey(key)) {
							continue;
						}
						int g = top(u, x, z, u.gy);
						if (g <= NONE || y < g) {
							continue;
						}
						cells.put(key, due);
						if (y == g) {
							// the ground it tears through
							op(u, SET, x, y, z, look.rubble[0], due, g, -1);
						} else {
							boolean shell = q2 > sq(Math.max(0, r - 0.8));
							BlockState st = !shell ? wood : qy > r * 0.4 && noise(x + y, z) < 0.45 ? look.moss : noise(x, z + y) < 0.35 ? look.shell : wood;
							op(u, RAISE, x, y, z, st, due, g, -1);
						}
					}
				}
			}
		}
		// roots hang under the arc
		if (look.hanging != null) {
			for (long key : cells.keySet()) {
				BlockPos p = BlockPos.of(key);
				if (!cells.containsKey(p.below().asLong()) && noise(p.getX(), p.getZ() + p.getY()) < 0.35) {
					int g = top(u, p.getX(), p.getZ(), u.gy);
					if (g > NONE && p.getY() - 1 > g + 1) {
						op(u, RAISE, p.getX(), p.getY() - 1, p.getZ(), look.hanging, cells.get(key) + 2, g, -1);
					}
				}
			}
		}
		String dim = level.dimension().identifier().toString();
		for (double[] foot : new double[][] {{ax, az, ga}, {bx, bz, gb}}) {
			WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", foot[0], foot[2] + 1.2, foot[1], thick, 0.2, thick, 0.03, 8);
			WorldFx.soundIn(dim, look.rise, "hostile", foot[0], foot[2] + 1, foot[1], 4f, 0.5f);
		}
		WorldFx.soundIn(dim, "minecraft:block.mangrove_roots.place", "hostile", ax, ga + 1, az, 4f, 0.5f);
		toss(u, b, ax, ga + 1, az, thick + 1.5, 1.1);
		toss(u, b, bx, gb + 1, bz, thick + 1.5, 1.1);
	}

	// ------------------------------------------------------------------ changing a block

	/** A block built up into air (or plants): never into a body, onto nothing, or over what the ground already changed. */
	private static boolean raise(Upheaval u, BlockPos pos, BlockState to, int ground) {
		ServerLevel level = u.level;
		if (!Loaded.ready(level, pos) || !inside(u, pos)) {
			return false;
		}
		BlockState now = level.getBlockState(pos);
		// (where two pieces meet, the first stays; never into one of its own holes)
		if (held(level, pos) != null || !open(now) || ElderArena.holds(level, pos)) {
			return false;
		}
		if (pos.getY() <= ground + 1 && !solid(level.getBlockState(pos.below()))) {
			return false;
		}
		if (occupied(u, pos)) {
			return false;
		}
		level.setBlock(pos, to, 2 | 16);
		record(u, pos, now, level.getBlockState(pos), null, ground, false);
		u.tops.remove(col(pos.getX(), pos.getZ()));
		if (pos.getY() == ground + 1) {
			watch(u, pos.below());
		}
		return true;
	}

	/** A plant over ground about to open is cleared away. */
	private static boolean clearPlant(Upheaval u, BlockPos pos, int ground) {
		ServerLevel level = u.level;
		if (!Loaded.ready(level, pos) || !inside(u, pos)) {
			return false;
		}
		BlockState now = level.getBlockState(pos);
		if (now.isAir() || !open(now) || held(level, pos) != null || ElderArena.holds(level, pos)) {
			return false;
		}
		level.setBlock(pos, AIR, 2 | 16);
		record(u, pos, now, AIR, null, ground, false);
		return true;
	}

	/**
	 * A block torn out (to air, or soul fire): only with solid ground left under it, open air or a plant over it,
	 * no water or lava beside it, and nothing an arena built over or beside it.
	 */
	private static boolean cut(Upheaval u, BlockPos pos, BlockState to, int ground, int tag) {
		ServerLevel level = u.level;
		if (!Loaded.ready(level, pos) || !inside(u, pos)) {
			return false;
		}
		BlockState now = level.getBlockState(pos);
		Change c = held(level, pos);
		ElderArena.Piece piece = null;
		if (c != null) {
			// (a block of its own laid in a crack opens further; never what it raised, nor a hole twice)
			if (c.owner != u || c.watch || !solid(c.original) || !solid(now) || now != c.placed) {
				return false;
			}
		} else {
			if (!solid(now)) {
				return false;
			}
			piece = ElderArena.standing(level, pos);
			if (piece != null ? !floor(u, piece) || Config.ELDER_ARENA_BREAKABLE <= 0 || !ElderTerrain.tearable(level, pos, now)
					: !ElderTerrain.breakable(level, pos, now)) {
				return false;
			}
		}
		if (!solid(level.getBlockState(pos.below()))) {
			return false;
		}
		BlockPos up = pos.above();
		BlockState over = level.getBlockState(up);
		if (!over.isAir() && (!open(over) || ElderArena.holds(level, up))) {
			return false;
		}
		ElderArena.Piece q = ElderArena.standing(level, up);
		if (q != null && !floor(u, q)) {
			return false;
		}
		for (Direction d : SIDES) {
			BlockPos n = pos.relative(d);
			if (!Loaded.ready(level, n)) {
				return false;
			}
			// (nor beside what would run or fall into it, or hangs on it: water, sand, a torch, a ladder)
			BlockState side = level.getBlockState(n);
			if (!side.getFluidState().isEmpty() || !side.isAir() && !solid(side) || side.is(BlockTags.SAND) || side.is(Blocks.GRAVEL)) {
				return false;
			}
			q = ElderArena.standing(level, n);
			if (q != null && !floor(u, q)) {
				return false;
			}
		}
		level.setBlock(pos, to, 2 | 16);
		BlockState placed = level.getBlockState(pos);
		if (c == null) {
			record(u, pos, now, placed, piece, ground, false);
		} else {
			c.placed = placed;
		}
		u.tops.remove(col(pos.getX(), pos.getZ()));
		if (tag >= 0) {
			u.opened.add(tag);
		}
		return true;
	}

	/** A block of the ground turned to another (a crack burning, a chasm's floor, the ground a root tears through). */
	private static boolean set(Upheaval u, BlockPos pos, BlockState to, int ground) {
		ServerLevel level = u.level;
		if (!Loaded.ready(level, pos) || !inside(u, pos)) {
			return false;
		}
		BlockState now = level.getBlockState(pos);
		Change c = held(level, pos);
		ElderArena.Piece piece = null;
		if (c != null) {
			if (c.owner != u || c.watch || !solid(now) || now != c.placed) {
				return false;
			}
		} else {
			if (!solid(now) || now == to) {
				return false;
			}
			piece = ElderArena.standing(level, pos);
			if (piece != null ? !floor(u, piece) || Config.ELDER_ARENA_BREAKABLE <= 0 || !ElderTerrain.tearable(level, pos, now)
					: !ElderTerrain.breakable(level, pos, now)) {
				return false;
			}
		}
		level.setBlock(pos, to, 2 | 16);
		BlockState placed = level.getBlockState(pos);
		if (c == null) {
			record(u, pos, now, placed, piece, ground, false);
		} else {
			c.placed = placed;
		}
		return true;
	}

	/** Grass under raised ground is watched: covered, it may turn to dirt, and goes back to grass afterwards. */
	private static void watch(Upheaval u, BlockPos pos) {
		BlockState g = u.level.getBlockState(pos);
		if (g.is(Blocks.GRASS_BLOCK) && held(u.level, pos) == null && !ElderArena.holds(u.level, pos)) {
			record(u, pos, g, g, null, pos.getY(), true);
		}
	}

	private static Change record(Upheaval u, BlockPos pos, BlockState original, BlockState placed, ElderArena.Piece piece, int ground, boolean watch) {
		Change c = new Change(u, pos.immutable(), original, placed, piece, ground, watch);
		u.changes.add(c);
		HELD.computeIfAbsent(u.level, k -> new Long2ObjectOpenHashMap<>()).put(c.pos.asLong(), c);
		if (!watch) {
			u.changed++;
		}
		return c;
	}

	/** Whether a block is within what breaks, and clear of the Elder's body. */
	private static boolean inside(Upheaval u, BlockPos pos) {
		if (sq(pos.getX() + 0.5 - u.cx) + sq(pos.getZ() + 0.5 - u.cz) > sq(u.radius + 0.5)) {
			return false;
		}
		return sq(pos.getX() + 0.5 - u.ex) + sq(pos.getZ() + 0.5 - u.ez) > u.clear * u.clear || pos.getY() < u.bodyLow || pos.getY() > u.bodyHigh;
	}

	/** Whether a player stands in this block. */
	private static boolean occupied(Upheaval u, BlockPos pos) {
		for (ServerPlayer p : u.bodies) {
			AABB box = p.getBoundingBox();
			if (box.maxX > pos.getX() && box.minX < pos.getX() + 1 && box.maxY > pos.getY() && box.minY < pos.getY() + 1 && box.maxZ > pos.getZ()
					&& box.minZ < pos.getZ() + 1) {
				return true;
			}
		}
		return false;
	}

	/** Whether a block of an arena is of this upheaval's arena's floor (the only part of an arena it changes). */
	private static boolean floor(Upheaval u, ElderArena.Piece p) {
		ElderArena.Arena a = u.arena;
		return a != null && !a.dissolving && p.group >= 0 && p.group < a.bp.kinds.size() && a.claimed.contains(p.pos.asLong())
				&& a.bp.kinds.get(p.group) == ArenaDesign.FLOOR;
	}

	// ------------------------------------------------------------------ the ground settles

	/** The fight is over: the broken ground begins to sink back, its raised ground from the top, its holes from the floor. */
	private static void sink(Upheaval u) {
		if (u.phase == SINK) {
			return;
		}
		u.phase = SINK;
		u.age = 0;
		u.linger = Math.max(20, Config.ELDER_UPHEAVAL_LINGER_SECONDS * 20);
		u.ops.clear();
		u.features.clear();
		u.next = 0;
		drop(u);
		int high = 0, deep = 0;
		List<Change> left = new ArrayList<>();
		for (Change c : u.changes) {
			if (held(u.level, c.pos) != c) {
				continue;
			}
			left.add(c);
			int y = c.pos.getY() - c.ground;
			if (!c.watch && open(c.original) && !c.placed.isAir()) {
				high = Math.max(high, y);
			} else if (!c.watch && solid(c.original) && open(c.placed)) {
				deep = Math.max(deep, -y);
			}
		}
		for (Change c : left) {
			int y = c.pos.getY() - c.ground;
			if (c.watch) {
				c.when = (Math.max(high, deep) + 2) * SINK_LAYER;
			} else if (open(c.original) && !c.placed.isAir()) {
				c.when = (high - y) * SINK_LAYER;
			} else if (solid(c.original) && open(c.placed)) {
				c.when = (deep + y) * SINK_LAYER;
			} else if (open(c.original)) {
				// a plant cleared off ground that opened: once its ground is back
				c.when = (deep + 1) * SINK_LAYER + y;
			} else {
				c.when = 0;
			}
		}
		left.sort(Comparator.comparingInt(c -> c.when));
		u.order = left;
		u.back = 0;
		if (!left.isEmpty()) {
			Change c = left.get(left.size() / 2);
			WorldFx.soundIn(u.level.dimension().identifier().toString(), "minecraft:block.deepslate.break", "hostile", c.pos.getX() + 0.5, c.pos.getY(),
					c.pos.getZ() + 0.5, 4f, 0.4f);
		}
	}

	/** A tick of the ground sinking back; true once all of it is back. */
	private static boolean sinking(Upheaval u) {
		ServerLevel level = u.level;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int t = u.age++;
		int puffs = 0;
		while (u.back < u.order.size() && budget > 0) {
			Change c = u.order.get(u.back);
			if (c.when > t) {
				break;
			}
			u.back++;
			if (held(level, c.pos) != c) {
				continue;
			}
			if (!Loaded.ready(level, c.pos)) {
				// its part of the world is not loaded: it goes back once it is (or once the time runs out)
				u.waiting.add(c);
				continue;
			}
			budget--;
			settle(u, c);
			if (puffs < 4 && rnd.nextInt(10) == 0 && watched(level, c.pos.getX(), c.pos.getY(), c.pos.getZ(), 64)) {
				puffs++;
				WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", c.pos.getX() + 0.5, c.pos.getY() + 0.6, c.pos.getZ() + 0.5, 0.4, 0.2, 0.4, 0.01, 2);
			}
			if (t % 8 == 0 && puffs == 1) {
				WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.deepslate.break", "hostile", c.pos.getX() + 0.5, c.pos.getY(),
						c.pos.getZ() + 0.5, 2f, 0.5f);
			}
		}
		if (t % 20 == 0 && !u.waiting.isEmpty()) {
			Iterator<Change> it = u.waiting.iterator();
			while (it.hasNext() && budget > 0) {
				Change c = it.next();
				if (held(level, c.pos) != c) {
					it.remove();
				} else if (Loaded.ready(level, c.pos)) {
					budget--;
					put(u, c);
					it.remove();
				}
			}
		}
		if (--u.linger <= 0) {
			// Its time is up: whatever is left goes back now, wherever it is
			for (int i = u.back; i < u.order.size(); i++) {
				Change c = u.order.get(i);
				if (held(level, c.pos) == c) {
					put(u, c);
				}
			}
			for (Change c : u.waiting) {
				if (held(level, c.pos) == c) {
					put(u, c);
				}
			}
			u.back = u.order.size();
			u.waiting.clear();
			return true;
		}
		return u.back >= u.order.size() && u.waiting.isEmpty();
	}

	/**
	 * A layer of raised ground sinks: its block goes back, and the top of the column (a cliff's moss or cap, a spike's tip)
	 * goes down into the layer under it, so the whole of it sinks into the ground rather than crumbling from the top.
	 */
	private static void settle(Upheaval u, Change c) {
		ServerLevel level = u.level;
		if (!c.watch && open(c.original) && !c.placed.isAir() && budget > 0) {
			BlockPos under = c.pos.below();
			Change d = held(level, under);
			BlockState now = level.getBlockState(c.pos);
			if (d != null && d.owner == u && !d.watch && open(d.original) && !d.placed.isAir() && now == c.placed && now != d.placed
					&& level.getBlockState(under) == d.placed) {
				level.setBlock(under, now, 2 | 16);
				d.placed = level.getBlockState(under);
				budget--;
			}
		}
		put(u, c);
	}

	/**
	 * A block goes back as it was: where it is still what the ground made it (or air, or a plant, or water that ran in)
	 * the block from before is put back; where an arena's rubble has come to rest since, the arena puts it back as it
	 * goes; anything else built there since is left as it is.
	 */
	private static void put(Upheaval u, Change c) {
		ServerLevel level = u.level;
		Long2ObjectOpenHashMap<Change> m = HELD.get(level);
		if (m != null && m.get(c.pos.asLong()) == c) {
			m.remove(c.pos.asLong());
			if (m.isEmpty()) {
				HELD.remove(level);
			}
		}
		BlockState now = level.getBlockState(c.pos);
		if (c.watch) {
			if (now != c.original && (now.is(Blocks.DIRT) || now.isAir())) {
				level.setBlock(c.pos, c.original, 2 | 16);
			}
			return;
		}
		if (now == c.original || now.hasBlockEntity()) {
			return;
		}
		// (earth it raised may have grown grass meanwhile, or its grass turned to dirt under cover: still its own)
		if (now == c.placed || !c.placed.isAir() && now.is(c.placed.getBlock()) || now.isAir() || now.canBeReplaced()
				|| now.is(BlockTags.DIRT) && c.placed.is(BlockTags.DIRT)) {
			level.setBlock(c.pos, c.original, 2 | 16);
			return;
		}
		ElderArena.Piece p = ElderArena.standing(level, c.pos);
		if (c.piece == null && p != null && p.placed && now == p.state) {
			p.original = c.original;
		}
	}

	/** Every display it has in the air goes. */
	private static void drop(Upheaval u) {
		for (Hover h : u.hovers) {
			if (!h.d.isRemoved()) {
				WorldFx.particleIn(u.level, "minecraft:poof", h.d.getX() + 0.5, h.d.getY(), h.d.getZ() + 0.5, 0.2, 0.2, 0.2, 0.01, 2);
			}
			BlockFlight.drop(h.d);
		}
		u.hovers.clear();
		for (Shard s : u.shards) {
			BlockFlight.drop(s.d);
		}
		u.shards.clear();
	}

	// ------------------------------------------------------------------ the ground

	/** The top block of the ground in a column (cached), or {@link #NONE}. */
	private static int top(Upheaval u, int x, int z, int near) {
		long key = col(x, z);
		int y = u.tops.get(key);
		if (y == UNKNOWN) {
			y = scan(u.level, x, z, near);
			u.tops.put(key, y);
		}
		return y;
	}

	/**
	 * The top block of the ground in a column near a height: the first solid block going down from a little above it
	 * with open air (or a plant) over it, leaves passed through; {@link #NONE} if its chunk is not loaded, water or lava
	 * lies over it, or there is none near.
	 */
	private static int scan(ServerLevel level, int x, int z, int near) {
		if (!Loaded.chunk(level, x >> 4, z >> 4)) {
			return NONE;
		}
		BlockPos start = new BlockPos(x, near + 9, z);
		boolean clear = open(level.getBlockState(start)) || raised(level, start);
		for (int y = near + 8; y >= near - 12; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState s = level.getBlockState(pos);
			if (!s.getFluidState().isEmpty()) {
				return NONE;
			}
			// (what an upheaval raised is seen through: the ground is the ground under it)
			boolean mine = raised(level, pos);
			if (solid(s) && !s.is(BlockTags.LEAVES) && !mine) {
				return clear ? y : NONE;
			}
			clear = open(s) || mine;
		}
		return NONE;
	}

	/** Whether an upheaval raised this block (out of air or plants). */
	private static boolean raised(ServerLevel level, BlockPos pos) {
		Change c = HELD.isEmpty() ? null : held(level, pos);
		return c != null && !c.watch && open(c.original);
	}

	/** How many solid blocks go down from a column's top, at most {@code most}. */
	private static int run(ServerLevel level, int x, int top, int z, int most) {
		int n = 0;
		while (n < most && solid(level.getBlockState(new BlockPos(x, top - n, z)))) {
			n++;
		}
		return n;
	}

	/** Solid ground: not air, nor liquid, nor a plant, nor holding water. */
	private static boolean solid(BlockState s) {
		return !s.isAir() && !s.liquid() && !s.canBeReplaced() && s.getFluidState().isEmpty();
	}

	/** Open: air, or a plant that gives way (no water, nothing holding data, nothing a Wither cannot break). */
	private static boolean open(BlockState s) {
		return s.isAir() || s.canBeReplaced() && !s.liquid() && s.getFluidState().isEmpty() && !s.hasBlockEntity() && !s.is(BlockTags.WITHER_IMMUNE);
	}

	/**
	 * Earth or plain stone, which stays as it is wherever it is put (no sand to fall, no farmland to dry out): what the
	 * ground may be heaved up as, or a torn block land as.
	 */
	private static boolean stable(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.NETHERRACK) || s.is(Blocks.END_STONE) || s.is(Blocks.ANDESITE)
				|| s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.TUFF) || s.is(Blocks.BLACKSTONE);
	}

	/** Whether a player is near enough to see something here. */
	private static boolean watched(ServerLevel level, double x, double y, double z, double range) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(x, y, z) < range * range) {
				return true;
			}
		}
		return false;
	}

	private static long col(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static double sq(double v) {
		return v * v;
	}

	/** The same rough noise for a column every time, in [0, 1). */
	private static double noise(int x, int z) {
		long h = x * 341873128712L + z * 132897987541L;
		h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
		h ^= h >>> 17;
		return ((h >>> 20) & 0xFFFF) / 65536.0;
	}

	// ------------------------------------------------------------------ each Elder's stone

	/** What an Elder's ground breaks into: its cliffs, spikes, rubble, chasm floors and roots, and its light and sound. */
	private static final class Look {
		BlockState[] body;
		/** Its cliffs in bands of stone, layer by layer (the Weaver's), rather than mixed. */
		boolean strata;
		BlockState vein, top, crag, fleck, spike, core, tip, crack, hang, relief, scorch, fire, root, shell, moss, hanging;
		BlockState[] rubble, floor;
		/** How many of its cliffs and spikes are crags of its harder stone, and how much of a chasm's floor glows. */
		double cragChance, floorShare;
		int hangMax = 1;
		String glow, glow2, deep, rise, big;

		BlockState body(int k, int x, int z) {
			if (strata) {
				return body[Math.floorMod(k + (int) (noise(x, z) * 1.6), body.length)];
			}
			return body[(int) (noise(x, z + k * 31) * body.length)];
		}
	}

	private static Look[] looks;

	private static Look look(int style) {
		if (looks == null) {
			looks = new Look[] {matriarch(), ferryman(), weaver(), mason()};
		}
		return looks[Math.max(0, Math.min(3, style))];
	}

	/** The Matriarch's: earth and roots, dark oak and mangrove, moss over all of it. */
	private static Look matriarch() {
		Look l = new Look();
		BlockState rooted = mc("rooted_dirt", Blocks.DIRT.defaultBlockState()), coarse = mc("coarse_dirt", Blocks.DIRT.defaultBlockState());
		BlockState wood = axis(mc("dark_oak_wood", mc("dark_oak_log", Blocks.DIRT.defaultBlockState())), Direction.Axis.Y);
		BlockState moss = mc("moss_block", rooted), mangrove = mc("mangrove_roots", wood);
		l.body = new BlockState[] {rooted, coarse, rooted, Blocks.DIRT.defaultBlockState()};
		l.vein = wood;
		l.top = moss;
		l.crag = mangrove;
		l.fleck = wood;
		l.cragChance = 0.25;
		l.spike = mangrove;
		l.core = wood;
		l.tip = mangrove;
		l.crack = rooted;
		l.hang = mc("hanging_roots", null);
		l.relief = moss;
		l.rubble = new BlockState[] {rooted, coarse, moss, wood};
		l.floor = new BlockState[] {moss, rooted};
		l.floorShare = 0.45;
		l.scorch = moss;
		l.root = wood;
		l.shell = mangrove;
		l.moss = moss;
		l.hanging = l.hang;
		l.glow = "extremedimensions:lurker_wisp";
		l.glow2 = "minecraft:spore_blossom_air";
		l.deep = "minecraft:spore_blossom_air";
		l.rise = "minecraft:block.rooted_dirt.break";
		l.big = "minecraft:entity.warden.emerge";
		return l;
	}

	/** The Ferryman's: basalt and blackstone, magma in its seams, chains hanging off it, soul fire in its chasms. */
	private static Look ferryman() {
		Look l = new Look();
		BlockState basalt = axis(mc("basalt", Blocks.BLACKSTONE.defaultBlockState()), Direction.Axis.Y);
		BlockState polished = axis(mc("polished_basalt", basalt), Direction.Axis.Y), black = mc("blackstone", basalt);
		BlockState magma = mc("magma_block", black), soil = mc("soul_soil", black);
		l.body = new BlockState[] {basalt, black, basalt, polished};
		l.vein = magma;
		l.top = black;
		l.crag = black;
		l.fleck = magma;
		l.cragChance = 0.2;
		l.spike = basalt;
		l.core = polished;
		l.crack = magma;
		l.hang = axis(mc("iron_chain", mc("chain", null)), Direction.Axis.Y);
		l.hangMax = 3;
		l.relief = black;
		l.rubble = new BlockState[] {black, basalt, soil, magma};
		l.floor = new BlockState[] {soil, soil, mc("soul_sand", soil)};
		l.floorShare = 0.8;
		l.fire = mc("soul_fire", Blocks.SOUL_FIRE.defaultBlockState());
		l.scorch = soil;
		l.glow = "minecraft:soul_fire_flame";
		l.glow2 = "minecraft:lava";
		l.deep = "minecraft:soul_fire_flame";
		l.rise = "minecraft:block.deepslate.break";
		l.big = "minecraft:block.bell.use";
		return l;
	}

	/** The Last Weaver's (and the Knot Mother's): deepslate and tuff in bands, calcite, amethyst spikes and veins. */
	private static Look weaver() {
		Look l = new Look();
		BlockState deepslate = mc("deepslate", Blocks.STONE.defaultBlockState()), cobbled = mc("cobbled_deepslate", deepslate);
		BlockState tuff = mc("tuff", deepslate), calcite = mc("calcite", tuff), amethyst = mc("amethyst_block", calcite);
		l.body = new BlockState[] {deepslate, cobbled, tuff, deepslate, mc("polished_deepslate", deepslate), calcite};
		l.strata = true;
		l.vein = amethyst;
		l.top = tuff;
		l.crag = tuff;
		l.fleck = calcite;
		l.cragChance = 0.15;
		l.spike = amethyst;
		l.core = amethyst;
		l.tip = facing(mc("amethyst_cluster", amethyst), Direction.UP);
		l.crack = amethyst;
		l.relief = tuff;
		l.rubble = new BlockState[] {cobbled, tuff, calcite, deepslate};
		l.floor = new BlockState[] {amethyst, calcite, tuff};
		l.floorShare = 0.4;
		l.scorch = calcite;
		l.glow = "minecraft:end_rod";
		l.glow2 = "extremedimensions:soul_mote";
		l.deep = "minecraft:end_rod";
		l.rise = "minecraft:block.amethyst_block.break";
		l.big = "minecraft:block.amethyst_block.resonate";
		return l;
	}

	/** The Pale Mason's: end stone and its bricks, gold veins, purpur spikes, crags of obsidian, crying obsidian below. */
	private static Look mason() {
		Look l = new Look();
		BlockState end = mc("end_stone", Blocks.STONE.defaultBlockState()), bricks = mc("end_stone_bricks", end);
		BlockState obsidian = mc("obsidian", end), crying = mc("crying_obsidian", obsidian), purpur = mc("purpur_block", bricks);
		l.body = new BlockState[] {end, end, bricks, end};
		l.vein = mc("gold_block", bricks);
		l.top = bricks;
		l.crag = obsidian;
		l.fleck = crying;
		l.cragChance = 0.3;
		l.spike = purpur;
		l.core = axis(mc("purpur_pillar", purpur), Direction.Axis.Y);
		l.tip = facing(mc("end_rod", purpur), Direction.UP);
		l.crack = crying;
		l.relief = end;
		l.rubble = new BlockState[] {end, bricks, obsidian, purpur};
		l.floor = new BlockState[] {crying, obsidian, crying};
		l.floorShare = 0.5;
		l.scorch = crying;
		l.glow = "minecraft:portal";
		l.glow2 = "minecraft:reverse_portal";
		l.deep = "minecraft:falling_obsidian_tear";
		l.rise = "minecraft:block.deepslate.break";
		l.big = "extremedimensions:elder.pale_mason.upheaval";
		return l;
	}

	/** A vanilla block by id, looked up at runtime (block constants come and go between versions). */
	private static BlockState mc(String id, BlockState fallback) {
		BlockState s = BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", id)).map(h -> h.value().defaultBlockState())
				.orElse(Blocks.AIR.defaultBlockState());
		return s.isAir() ? fallback : s;
	}

	private static BlockState facing(BlockState s, Direction d) {
		return s != null && s.hasProperty(BlockStateProperties.FACING) ? s.setValue(BlockStateProperties.FACING, d) : s;
	}

	private static BlockState axis(BlockState s, Direction.Axis a) {
		return s != null && s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
	}
}
