package com.extremedimensions.reach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The rites of an Unfinished Anchor (3.44), played at its fitting as the End's ending is chosen there (see
 * {@link HomeOrLock}).
 *
 * <p><b>Home</b>: the anchor is finished ({@link PaleAnchorDesign#whole}). What its builders never set in place flies
 * in out of nothing, as the anchor first flew together (only what someone could see flies; the rest is simply there):
 * its pillars rise whole and are crowned, its arches close, and its halo closes from both ends of its gap into a full
 * ring, golden at every spoke; the loose blocks hanging round it are drawn into the work. Light gathers in the ring, and
 * a door of portal light opens in it. The Endermen near ({@code pale_door_range}) come to the fitting under the door,
 * those who cannot walk there stepping closer as Endermen do, look up a moment and go home through it, one by one,
 * until it closes ({@code pale_door_seconds}). One angry at someone, or someone's (named), stays.
 *
 * <p><b>Lock</b>: the anchor is sealed. A purple flash and a deep sound, a shock running out over the floor; its chains
 * snap, and its halo, its arches and the top half of every pillar break away from the top down and fall into the void,
 * gone within seconds. The floor and the plinth stand, and the barrels; the fitting is the lock now, of crying obsidian,
 * and no beam stands over it any more.
 *
 * <p>Both are kept with the world among the Reach's marks: an anchor finished {@code ph@dim|x,y,z}, a locked one
 * {@code pl@dim|x,y,z} (by its fitting). Neither is ever built again as unfinished, and a door opens only once. A rite cut
 * short (the server stopping) ends at once: what was still to fly in is in its place, what was still to break away is
 * gone, and every display goes.
 */
public final class AnchorRites {
	private AnchorRites() {}

	private static final String LIGHT = "minecraft:end_rod", PORTAL = "minecraft:portal", VOID = "minecraft:reverse_portal";
	/** The door's radius, inside the halo's ring. */
	private static final double DOOR = PaleAnchorDesign.HALO_R - 0.9;
	/** Ticks of light gathering in the ring before the door opens, of the door opening, and of it closing. */
	private static final int GATHER = 30, OPENING = 16, CLOSING = 26;
	/** Ticks an Enderman stands at the fitting looking up at the door before it goes. */
	private static final int LAST_LOOK = 16;
	/** How many Endermen come to one door at a time. */
	private static final int WALKERS = 48;
	/** How fast what breaks off a locked anchor falls faster (blocks a tick, every tick), and how often its fall is set out. */
	private static final double GRAVITY = 0.04;
	private static final int SEGMENT = 6;
	private static final Identifier ENDERMAN = Identifier.fromNamespaceAndPath("minecraft", "enderman");

	/** An anchor being finished, and then its door. */
	private static final class Home {
		final ServerLevel level;
		final String dim;
		final BlockPos fitting, origin;
		final PaleAnchorDesign.Blueprint was, whole;
		/** The door's middle, the way along it, and the turn of its panes. */
		final double cx, cy, cz, ux, uz;
		final Quaternionf turn;
		final List<Map.Entry<Long, BlockState>> pieces;
		/** What could not go in when its turn came (someone stood there, or its part of the world was not loaded). */
		final List<Map.Entry<Long, BlockState>> later = new ArrayList<>();
		final List<BlockFlight.Flight> flights = new ArrayList<>();
		/** The loose blocks still to be drawn into the work, and those on their way. */
		final List<BlockPos> loose;
		final List<BlockFlight.Flight> drawn = new ArrayList<>();
		final double rate;
		final int doorTicks, range;
		int next, born, age;
		double credit;
		/** The tick (of the rite) it stood whole, its door began to open, and began to close; -1 until then. */
		int stood = -1, opened = -1, closing = -1;
		final List<Cell> cells = new ArrayList<>();
		final Map<UUID, Walker> walkers = new HashMap<>();
		List<BlockPos> landings = List.of();

		Home(ServerLevel level, BlockPos fitting, BlockPos origin, PaleAnchorDesign.Blueprint was, PaleAnchorDesign.Blueprint whole) {
			this.level = level;
			this.dim = level.dimension().identifier().toString();
			this.fitting = fitting;
			this.origin = origin;
			this.was = was;
			this.whole = whole;
			cx = whole.halo.getX() + 0.5;
			cy = whole.halo.getY() + 0.5;
			cz = whole.halo.getZ() + 0.5;
			ux = Math.cos(whole.plane);
			uz = Math.sin(whole.plane);
			// (a pane of portal runs east to west: turned, it runs along the halo)
			turn = new Quaternionf().rotateY((float) -whole.plane);
			pieces = missing(level, origin, whole);
			loose = loose(level, was, whole);
			rate = Math.max(1.0, pieces.size() / (Math.max(4.0, Config.PALE_ANCHOR_BUILD_SECONDS / 3.0) * 20));
			doorTicks = Math.max(1, Config.PALE_DOOR_SECONDS) * 20;
			range = Math.max(8, Config.PALE_DOOR_RANGE);
		}
	}

	/** A pane of the door: a display of portal light at (h, v) from the door's middle in its plane, r out from it. */
	private static final class Cell {
		final Display.BlockDisplay display;
		final double h, v, r;
		boolean shown;

		Cell(Display.BlockDisplay display, double h, double v) {
			this.display = display;
			this.h = h;
			this.v = v;
			this.r = Math.sqrt(h * h + v * v);
		}
	}

	/** An Enderman on its way home. */
	private static final class Walker {
		final UUID id;
		/** Ticks on its way; before it sets off; without getting nearer; and when it came to the fitting (-1: not yet). */
		int age, wait, stuck, arrived = -1;
		double best = Double.MAX_VALUE;

		Walker(UUID id, int wait) {
			this.id = id;
			this.wait = wait;
		}
	}

	/** An anchor locked and breaking apart. */
	private static final class Lock {
		final ServerLevel level;
		final String dim;
		final BlockPos fitting, origin;
		/** What breaks away, in the order it does. */
		final List<Piece> pieces;
		final List<Fall> falls = new ArrayList<>();
		int next, age;

		Lock(ServerLevel level, BlockPos fitting, BlockPos origin, List<Piece> pieces) {
			this.level = level;
			this.dim = level.dimension().identifier().toString();
			this.fitting = fitting;
			this.origin = origin;
			this.pieces = pieces;
		}
	}

	/** A block of a locked anchor, and the tick it breaks away at. */
	private static final class Piece {
		final BlockPos pos;
		final BlockState state;
		final boolean chain;
		int at;

		Piece(BlockPos pos, BlockState state, boolean chain) {
			this.pos = pos;
			this.state = state;
			this.chain = chain;
		}
	}

	/** A block falling away into the void: a display flung out from its place, falling ever faster and turning. */
	private static final class Fall {
		final Display.BlockDisplay display;
		final BlockPos pos;
		final double vx, vy, vz;
		final Vector3f axis;
		final float spin;
		final int life;
		int age;

		Fall(Display.BlockDisplay display, BlockPos pos, double vx, double vy, double vz, Vector3f axis, float spin, int life) {
			this.display = display;
			this.pos = pos;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.axis = axis;
			this.spin = spin;
			this.life = life;
		}
	}

	/** The rites under way, by their fittings. */
	private static final Map<String, Home> HOMES = new HashMap<>();
	private static final Map<String, Lock> LOCKS = new HashMap<>();
	private static int homeward;

	// ------------------------------------------------------------------ the rites

	/**
	 * Home: the anchor whose fitting is at {@code fitting} is finished (its pillars rise whole, its arches close, its
	 * halo closes into a full ring), and a door of light opens in it; the Endermen near walk home through it until it
	 * closes.
	 */
	public static void home(ServerLevel level, BlockPos fitting) {
		fitting = fitting.immutable();
		String spot = PaleReach.spot(level, fitting);
		if (!begin(spot, "ph@")) {
			return;
		}
		BlockPos origin = fitting.below();
		PaleAnchorDesign.Blueprint[] two = designs(level, origin);
		Home h = new Home(level, fitting, origin, two[0], two[1]);
		HOMES.put(spot, h);
		double x = fitting.getX() + 0.5, y = fitting.getY() + 0.5, z = fitting.getZ() + 0.5;
		WorldFx.soundIn(h.dim, "minecraft:block.beacon.power_select", "blocks", x, y, z, 2.5f, 1.2f);
		WorldFx.soundIn(h.dim, "minecraft:block.end_portal_frame.fill", "blocks", x, y, z, 2f, 1.0f);
		WorldFx.particleIn(level, LIGHT, x, y + 1, z, 0.3, 1.5, 0.3, 0.08, 60);
	}

	/**
	 * Lock: the anchor is sealed and breaks apart, its halo and pillar tops falling away into the void; the fitting
	 * becomes the lock. (The hoard is spilled by {@link HomeOrLock}.)
	 */
	public static void lock(ServerLevel level, BlockPos fitting) {
		fitting = fitting.immutable();
		String spot = PaleReach.spot(level, fitting);
		if (!begin(spot, "pl@")) {
			return;
		}
		// no longer a fitting: no beam stands over it, no word is given of what it wants
		DataStore.reach().remove("pf@" + spot);
		DataStore.markDirty();
		PaleReach.forget();
		BlockPos origin = fitting.below();
		PaleAnchorDesign.Blueprint[] two = designs(level, origin);
		Lock l = new Lock(level, fitting, origin, breaking(level, origin, two[0], two[1]));
		LOCKS.put(spot, l);
		seal(l);
	}

	/** Whether a rite may begin at this fitting (its anchor neither finished nor locked, no rite there now); if so it is marked. */
	private static boolean begin(String spot, String mark) {
		Set<String> marks = DataStore.reach();
		if (HOMES.containsKey(spot) || LOCKS.containsKey(spot) || marks.contains("ph@" + spot) || marks.contains("pl@" + spot)) {
			return false;
		}
		marks.add(mark + spot);
		DataStore.markDirty();
		return true;
	}

	/** The anchor as it was designed and as it is finished: from its seed when that is known, else from what is seen of it. */
	private static PaleAnchorDesign.Blueprint[] designs(ServerLevel level, BlockPos origin) {
		Long kept = PaleReach.seedAt(level, origin.above());
		if (kept != null) {
			long seed = kept;
			return new PaleAnchorDesign.Blueprint[] {PaleAnchorDesign.design(origin, seed), PaleAnchorDesign.whole(origin, seed)};
		}
		PaleAnchorDesign.Shape seen = PaleAnchorDesign.seen(origin, p -> Loaded.ready(level, p) ? level.getBlockState(p) : Blocks.AIR.defaultBlockState());
		PaleAnchorDesign.Blueprint w = PaleAnchorDesign.whole(origin, seen);
		return new PaleAnchorDesign.Blueprint[] {w, w};
	}

	public static void tick(MinecraftServer s) {
		if (HOMES.isEmpty() && LOCKS.isEmpty()) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		HOMES.values().removeIf(h -> step(h, rnd));
		LOCKS.values().removeIf(l -> step(l, rnd));
	}

	/** Leaving the world (or a test done): what was to fly in is in its place, what was to break away is gone, every display goes. */
	public static void clear() {
		for (Home h : HOMES.values()) {
			for (BlockFlight.Flight f : List.copyOf(h.flights)) {
				BlockFlight.rush(f);
			}
			for (BlockFlight.Flight f : h.drawn) {
				BlockFlight.cancel(f);
			}
			while (h.next < h.pieces.size()) {
				place(h, h.pieces.get(h.next++));
			}
			for (Map.Entry<Long, BlockState> e : List.copyOf(h.later)) {
				place(h, e);
			}
			for (BlockPos p : h.loose) {
				BlockState s = h.was.at(p);
				if (s != null && Loaded.ready(h.level, p) && h.level.getBlockState(p).is(s.getBlock())) {
					h.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			for (Cell c : h.cells) {
				BlockFlight.drop(c.display);
			}
			for (Walker w : h.walkers.values()) {
				if (h.level.getEntity(w.id) instanceof Mob m) {
					m.getNavigation().stop();
				}
			}
		}
		HOMES.clear();
		for (Lock l : LOCKS.values()) {
			for (Fall f : l.falls) {
				BlockFlight.drop(f.display);
			}
			while (l.next < l.pieces.size()) {
				Piece p = l.pieces.get(l.next++);
				if (Loaded.ready(l.level, p.pos) && l.level.getBlockState(p.pos).is(p.state.getBlock())) {
					l.level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		LOCKS.clear();
		homeward = 0;
	}

	/** For tests: whether a door stands open at this fitting now. */
	public static boolean doorOpen(Level level, BlockPos fitting) {
		Home h = HOMES.get(PaleReach.spot(level, fitting));
		return h != null && h.opened >= 0 && h.closing < 0;
	}

	/** For tests: how many Endermen have gone home through any door since the last clear(). */
	public static int homeward() {
		return homeward;
	}

	/** For tests: how many blocks of a locked anchor are still falling (displays/falling blocks in the air). */
	public static int falling() {
		int n = 0;
		for (Lock l : LOCKS.values()) {
			n += l.falls.size();
		}
		return n;
	}

	// ------------------------------------------------------------------ home: the anchor finished

	/** What the finished anchor lays that is not there: into open air only, never a barrel; the halo last, closing from both ends of its gap. */
	private static List<Map.Entry<Long, BlockState>> missing(ServerLevel level, BlockPos origin, PaleAnchorDesign.Blueprint whole) {
		List<Map.Entry<Long, BlockState>> body = new ArrayList<>(), ring = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : whole.ordered(origin)) {
			BlockPos pos = BlockPos.of(e.getKey());
			if (e.getValue().hasBlockEntity() || Loaded.ready(level, pos) && !level.getBlockState(pos).isAir()) {
				continue;
			}
			if (whole.ring.contains(e.getKey())) {
				ring.add(e);
			} else {
				body.add(e);
			}
		}
		ring.sort(Comparator.comparingDouble((Map.Entry<Long, BlockState> e) -> -fromGap(whole, e.getKey())));
		body.addAll(ring);
		return body;
	}

	/** How far round the halo's ring a block of it is from the middle of the gap it was left open at (radians). */
	private static double fromGap(PaleAnchorDesign.Blueprint bp, long p) {
		double ux = Math.cos(bp.plane), uz = Math.sin(bp.plane);
		double h = (BlockPos.getX(p) - bp.halo.getX()) * ux + (BlockPos.getZ(p) - bp.halo.getZ()) * uz, v = BlockPos.getY(p) - bp.halo.getY();
		double d = Math.atan2(v, h) - bp.gapMid;
		return Math.abs(Math.atan2(Math.sin(d), Math.cos(d)));
	}

	/** The loose blocks hanging round an anchor that are still there as its builders set them down. */
	private static List<BlockPos> loose(ServerLevel level, PaleAnchorDesign.Blueprint was, PaleAnchorDesign.Blueprint whole) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : new LinkedHashSet<>(was.loose)) {
			BlockState s = was.at(p);
			if (s != null && whole.at(p) == null && Loaded.ready(level, p) && level.getBlockState(p).is(s.getBlock())) {
				out.add(p);
			}
		}
		return out;
	}

	/** One tick of a finished anchor's rite; true once it is over. */
	private static boolean step(Home h, ThreadLocalRandom rnd) {
		h.age++;
		if (h.opened < 0) {
			if (h.stood < 0) {
				rise(h);
				if (h.next >= h.pieces.size() && h.loose.isEmpty() && h.flights.isEmpty() && h.drawn.isEmpty()) {
					for (Map.Entry<Long, BlockState> e : h.later) {
						place(h, e);
					}
					h.later.clear();
					stood(h);
				}
				return false;
			}
			gather(h, rnd);
			if (h.age - h.stood >= GATHER) {
				open(h);
			}
			return false;
		}
		if (h.closing < 0) {
			int t = h.age - h.opened;
			unfold(h, t);
			hum(h, rnd, t);
			if (t % 20 == 0 && t < Math.max(20, h.doorTicks - 40)) {
				call(h, rnd);
			}
			Iterator<Walker> it = h.walkers.values().iterator();
			while (it.hasNext()) {
				if (walk(h, it.next(), rnd)) {
					it.remove();
				}
			}
			if (t >= h.doorTicks) {
				close(h);
			}
			return false;
		}
		int c = h.age - h.closing;
		fold(h, c);
		if (c >= CLOSING) {
			shut(h);
			return true;
		}
		return false;
	}

	/** The missing parts fly in: as fast as the anchor first flew together, or all at once where no one is near enough to see. */
	private static void rise(Home h) {
		h.born = 0;
		boolean seen = anyone(h.level, h.cx, h.origin.getY() + 12, h.cz, Config.ELDER_ARENA_FLY_RANGE + 32);
		// the loose blocks hanging round it are drawn into the work first, two a tick
		for (int n = seen ? 2 : h.loose.size(); n > 0 && !h.loose.isEmpty(); n--) {
			draw(h, h.loose.remove(h.loose.size() - 1));
		}
		if (!seen) {
			while (h.next < h.pieces.size()) {
				launch(h, h.pieces.get(h.next++));
			}
		} else {
			h.credit += h.rate;
			while (h.credit >= 1 && h.next < h.pieces.size()) {
				h.credit--;
				launch(h, h.pieces.get(h.next++));
			}
		}
		h.flights.removeIf(BlockFlight::step);
		h.drawn.removeIf(BlockFlight::step);
		if (seen && h.age % 40 == 1) {
			// the halo hums, higher as the work nears its end
			float done = h.pieces.isEmpty() ? 1f : h.next / (float) h.pieces.size();
			WorldFx.soundIn(h.dim, "minecraft:block.beacon.ambient", "blocks", h.cx, h.cy, h.cz, 2.5f, 0.6f + 0.6f * done);
		}
	}

	private static boolean anyone(ServerLevel level, double x, double y, double z, double r) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(x, y, z) < r * r) {
				return true;
			}
		}
		return false;
	}

	/** A loose block is drawn into the work: it leaves its place and flies off into the anchor, dwindling (unseen, it is simply gone). */
	private static void draw(Home h, BlockPos pos) {
		if (!Loaded.ready(h.level, pos)) {
			return;
		}
		BlockState state = h.level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		h.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		if (h.flights.size() + h.drawn.size() < PaleReach.flyMax() && PaleReach.watched(h.level, pos)) {
			double[] to = {(h.cx - pos.getX() - 0.5) * 0.85, (h.cy - 6 - pos.getY() - 0.5) * 0.85, (h.cz - pos.getZ() - 0.5) * 0.85};
			BlockFlight.Flight f = BlockFlight.out(h.level, pos, state, to, PaleReach.flightTicks() + 10, LIGHT, 3);
			if (f != null) {
				h.drawn.add(f);
			}
		}
		WorldFx.particleIn(h.level, VOID, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.3, 0.3, 0.3, 0.02, 6);
	}

	/** A missing block sets off flying to its place, or (no one near enough to see it, or enough in the air) is simply there. */
	private static void launch(Home h, Map.Entry<Long, BlockState> e) {
		BlockPos pos = BlockPos.of(e.getKey());
		BlockState state = e.getValue();
		if (!Loaded.ready(h.level, pos)) {
			h.later.add(e);
			return;
		}
		// only ever into open air: never over what stands there
		if (!h.level.getBlockState(pos).isAir()) {
			return;
		}
		int max = PaleReach.flyMax(), ticks = PaleReach.flightTicks();
		if (h.flights.size() + h.drawn.size() < max && h.born < Math.max(1, (max + ticks - 1) / ticks) && PaleReach.watched(h.level, pos)) {
			BlockFlight.Flight f = BlockFlight.in(h.level, pos, state, from(h, pos), ticks, LIGHT, 3, new BlockFlight.Landing() {
				@Override
				public boolean fits() {
					return Loaded.ready(h.level, pos) && h.level.getBlockState(pos).isAir();
				}

				@Override
				public boolean blocked() {
					return PaleReach.occupied(h.level, pos);
				}

				@Override
				public void land() {
					h.level.setBlock(pos, state, 2);
				}

				@Override
				public void missed() {
					h.later.add(e);
				}

				@Override
				public void lost() {
					// (its display went with its part of the world: it is put in its place at the end, if it can be)
					h.later.add(e);
				}
			});
			if (f != null) {
				h.flights.add(f);
				h.born++;
				return;
			}
		}
		if (PaleReach.occupied(h.level, pos)) {
			h.later.add(e);
		} else {
			h.level.setBlock(pos, state, 2);
		}
	}

	/** A block put in its place at once, if there is open air for it and no one stands there. */
	private static void place(Home h, Map.Entry<Long, BlockState> e) {
		BlockPos pos = BlockPos.of(e.getKey());
		if (Loaded.ready(h.level, pos) && h.level.getBlockState(pos).isAir() && !PaleReach.occupied(h.level, pos)) {
			h.level.setBlock(pos, e.getValue(), 2);
		}
	}

	/** Where a block flies in from: the halo's from out round its ring, the plinth's up out of the void, the rest up from below all round. */
	private static double[] from(Home h, BlockPos pos) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (h.whole.ring.contains(pos.asLong())) {
			double along = (pos.getX() + 0.5 - h.cx) * h.ux + (pos.getZ() + 0.5 - h.cz) * h.uz, v = pos.getY() + 0.5 - h.cy;
			double phi = Math.atan2(v, along), d = 6 + rnd.nextDouble() * 6;
			return new double[] {Math.cos(phi) * h.ux * d + rnd.nextDouble(-1.5, 1.5), Math.sin(phi) * d, Math.cos(phi) * h.uz * d + rnd.nextDouble(-1.5, 1.5)};
		}
		double a = Math.atan2(pos.getZ() - h.origin.getZ(), pos.getX() - h.origin.getX()) + rnd.nextDouble(-0.7, 0.7), d = 5 + rnd.nextDouble() * 7;
		if (pos.getY() < h.origin.getY()) {
			return new double[] {Math.cos(a) * d * 0.5, -8 - rnd.nextDouble() * 8, Math.sin(a) * d * 0.5};
		}
		return new double[] {Math.cos(a) * d, -4 - rnd.nextDouble() * 8, Math.sin(a) * d};
	}

	/** The anchor stands whole: it settles with a sound, and light runs round its halo. */
	private static void stood(Home h) {
		h.stood = h.age;
		BlockPos f = h.fitting;
		WorldFx.soundIn(h.dim, "minecraft:block.end_portal_frame.fill", "blocks", f.getX() + 0.5, f.getY() + 1, f.getZ() + 0.5, 2.5f, 1.0f);
		WorldFx.soundIn(h.dim, "minecraft:block.amethyst_block.resonate", "blocks", h.cx, h.cy, h.cz, 3f, 0.8f);
		for (int i = 0; i < 360; i += 10) {
			double phi = Math.toRadians(i), along = Math.cos(phi) * PaleAnchorDesign.HALO_R, v = Math.sin(phi) * PaleAnchorDesign.HALO_R;
			WorldFx.particleIn(h.level, LIGHT, h.cx + along * h.ux, h.cy + v, h.cz + along * h.uz, 0.15, 0.15, 0.15, 0.02, 2);
		}
	}

	/** Light gathers in from the halo's ring to its middle, and chimes rise, before the door opens. */
	private static void gather(Home h, ThreadLocalRandom rnd) {
		for (int i = 0; i < 4; i++) {
			double phi = rnd.nextDouble() * Math.PI * 2, r = PaleAnchorDesign.HALO_R - 0.5;
			double x = h.cx + Math.cos(phi) * r * h.ux, y = h.cy + Math.sin(phi) * r, z = h.cz + Math.cos(phi) * r * h.uz;
			// (with no count, a particle's spread is the way it flies)
			WorldFx.particleIn(h.level, LIGHT, x, y, z, h.cx - x, h.cy - y, h.cz - z, 0.035, 0);
		}
		int t = h.age - h.stood;
		if (t % 10 == 0) {
			WorldFx.soundIn(h.dim, "minecraft:block.amethyst_block.chime", "blocks", h.cx, h.cy, h.cz, 2f, 0.6f + 0.15f * (t / 10));
		}
	}

	/** The look of the door's pane at (a, b) from its middle in its plane, at a size. */
	private static Transformation pane(Home h, double a, double b, float size) {
		return BlockFlight.pose(a * h.ux - 0.5, b + 0.5, a * h.uz - 0.5, h.turn, size);
	}

	private static void move(Display.BlockDisplay d, Transformation look, int ticks) {
		DisplayAccess acc = (DisplayAccess) d;
		acc.extremedimensions$setTransformation(look);
		acc.extremedimensions$setInterpolationDuration(ticks);
		acc.extremedimensions$setInterpolationDelay(0);
	}

	/** The door opens in the halo: panes of portal light, set out small, that spread from its middle (see {@link #unfold}). */
	private static void open(Home h) {
		h.opened = h.age;
		BlockState light = PaleAnchorDesign.mc("nether_portal", Blocks.CRYING_OBSIDIAN.defaultBlockState());
		Brightness lit = new Brightness(15, 15);
		int n = (int) Math.ceil(DOOR);
		for (int a = -n; a <= n; a++) {
			for (int b = -n; b <= n; b++) {
				if (a * a + b * b > DOOR * DOOR) {
					continue;
				}
				Display.BlockDisplay d = BlockFlight.spawn(h.level, h.cx, h.cy, h.cz, light, pane(h, a, b, 0.05F));
				if (d == null) {
					continue;
				}
				((DisplayAccess) d).extremedimensions$setBrightnessOverride(lit);
				h.cells.add(new Cell(d, a, b));
			}
		}
		h.landings = landings(h);
		WorldFx.soundIn(h.dim, "minecraft:block.end_portal.spawn", "blocks", h.cx, h.cy, h.cz, 4f, 1.2f);
		WorldFx.soundIn(h.dim, "minecraft:block.beacon.activate", "blocks", h.cx, h.cy, h.cz, 3f, 0.7f);
		WorldFx.particleIn(h.level, LIGHT, h.cx, h.cy, h.cz, 0.5, 0.5, 0.5, 0.3, 80);
		WorldFx.particleIn(h.level, VOID, h.cx, h.cy, h.cz, 2.5, 2.5, 2.5, 0.05, 80);
	}

	/** The door spreading open from its middle: each pane grows to its size as the opening reaches it. */
	private static void unfold(Home h, int t) {
		if (t < 2) {
			return;
		}
		double reach = (t - 1) * DOOR / OPENING;
		for (Cell c : h.cells) {
			if (!c.shown && c.r <= reach) {
				c.shown = true;
				move(c.display, pane(h, c.h, c.v, 1F), 6);
			}
		}
	}

	/** The open door: light drifting off it, sparks round its rim, and its sound. */
	private static void hum(Home h, ThreadLocalRandom rnd, int t) {
		if (t % 2 == 0) {
			double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * DOOR;
			double x = h.cx + Math.cos(a) * r * h.ux, y = h.cy + Math.sin(a) * r, z = h.cz + Math.cos(a) * r * h.uz;
			WorldFx.particleIn(h.level, PORTAL, x, y, z, 0.3, 0.3, 0.3, 0.4, 3);
			WorldFx.particleIn(h.level, VOID, x, y, z, 0.4, 0.4, 0.4, 0.01, 2);
		}
		if (t % 5 == 0) {
			double a = rnd.nextDouble() * Math.PI * 2, r = DOOR + 0.5;
			WorldFx.particleIn(h.level, LIGHT, h.cx + Math.cos(a) * r * h.ux, h.cy + Math.sin(a) * r, h.cz + Math.cos(a) * r * h.uz, 0.1, 0.1, 0.1, 0.01, 1);
		}
		if (t % 60 == 10) {
			WorldFx.soundIn(h.dim, "minecraft:block.portal.ambient", "blocks", h.cx, h.cy, h.cz, 2.5f, 0.7f + rnd.nextFloat() * 0.2f);
		}
		if (t % 80 == 40) {
			WorldFx.soundIn(h.dim, "minecraft:block.beacon.ambient", "blocks", h.cx, h.cy, h.cz, 2.5f, 1.3f);
		}
	}

	/** The Endermen near hear the door: each not after anyone, nor anyone's (named), sets off for the fitting under it. */
	private static void call(Home h, ThreadLocalRandom rnd) {
		double fx = h.fitting.getX() + 0.5, fy = h.fitting.getY(), fz = h.fitting.getZ() + 0.5, r2 = (double) h.range * h.range;
		for (Mob m : h.level.getEntitiesOfClass(Mob.class, new AABB(h.fitting).inflate(h.range), AnchorRites::enderman)) {
			if (h.walkers.size() >= WALKERS) {
				break;
			}
			if (h.walkers.containsKey(m.getUUID()) || !free(m) || m.distanceToSqr(fx, fy, fz) > r2) {
				continue;
			}
			// (they do not all set off at once)
			h.walkers.put(m.getUUID(), new Walker(m.getUUID(), rnd.nextInt(30)));
		}
	}

	private static boolean enderman(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(ENDERMAN);
	}

	/** Whether an Enderman may go home: alive, after no one, and not held by anything else (no mind of its own, a name, a seat). */
	private static boolean free(Mob m) {
		return m.isAlive() && m.getTarget() == null && !m.isNoAi() && !m.hasCustomName() && !m.isPassenger();
	}

	/** One tick of an Enderman on its way home; true once it has gone, or will not go after all. */
	private static boolean walk(Home h, Walker w, ThreadLocalRandom rnd) {
		if (!(h.level.getEntity(w.id) instanceof Mob m) || !m.isAlive()) {
			return true;
		}
		// angered on the way, it turns back to its own business
		if (m.getTarget() != null) {
			return true;
		}
		w.age++;
		double fx = h.fitting.getX() + 0.5, fy = h.fitting.getY(), fz = h.fitting.getZ() + 0.5;
		double dx = m.getX() - fx, dy = m.getY() - fy, dz = m.getZ() - fz;
		if (w.arrived >= 0) {
			m.getNavigation().stop();
			m.getLookControl().setLookAt(h.cx, h.cy, h.cz, 30, 30);
			if (w.age - w.arrived >= LAST_LOOK) {
				through(h, m, rnd);
				return true;
			}
			return false;
		}
		if (m.distanceToSqr(h.cx, h.cy, h.cz) <= 3 * 3 || dx * dx + dz * dz <= 3.5 * 3.5 && Math.abs(dy) <= 2.5) {
			w.arrived = w.age;
			m.getNavigation().stop();
			WorldFx.particleIn(h.level, PORTAL, m.getX(), m.getY() + 1.5, m.getZ(), 0.4, 0.9, 0.4, 0.3, 24);
			WorldFx.soundIn(h.dim, "minecraft:entity.enderman.ambient", "hostile", m.getX(), m.getY() + 2, m.getZ(), 1f, 0.6f);
			return false;
		}
		if (w.age < w.wait) {
			return false;
		}
		if (m.getY() < h.origin.getY() - 3 || w.stuck >= 100) {
			// below the anchor, or no way there: it steps closer, as Endermen go
			hop(h, m, rnd);
			w.stuck = 0;
			w.best = Double.MAX_VALUE;
			return false;
		}
		if (w.age % 10 == 0 || m.getNavigation().isDone()) {
			m.getNavigation().moveTo(fx, fy, fz, 1.0);
			if (m.getNavigation().isDone()) {
				// (no way there at all)
				w.stuck += 25;
			}
		}
		if (w.age % 20 == 0) {
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < w.best - 0.75) {
				w.best = d;
				w.stuck = 0;
			} else {
				w.stuck += 20;
			}
		}
		return false;
	}

	/** Where on the floor an Enderman may step onto: between the fitting's posts and the pillars, with room for its height. */
	private static List<BlockPos> landings(Home h) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				double d = Math.sqrt(x * x + z * z);
				BlockPos at = h.origin.offset(x, 1, z);
				if (d >= 4 && d <= 6 && room(h.level, at)) {
					out.add(at);
				}
			}
		}
		return out;
	}

	private static boolean room(ServerLevel level, BlockPos at) {
		return Loaded.ready(level, at) && !level.getBlockState(at.below()).isAir() && level.getBlockState(at).isAir()
				&& level.getBlockState(at.above()).isAir() && level.getBlockState(at.above(2)).isAir();
	}

	/** An Enderman steps closer: out of where it stands and onto the anchor's floor. */
	private static void hop(Home h, Mob m, ThreadLocalRandom rnd) {
		List<BlockPos> free = new ArrayList<>();
		for (BlockPos at : h.landings) {
			if (room(h.level, at)) {
				free.add(at);
			}
		}
		if (free.isEmpty()) {
			return;
		}
		BlockPos at = free.get(rnd.nextInt(free.size()));
		WorldFx.particleIn(h.level, PORTAL, m.getX(), m.getY() + 1.4, m.getZ(), 0.3, 0.8, 0.3, 0.3, 30);
		WorldFx.soundIn(h.dim, "minecraft:entity.enderman.teleport", "hostile", m.getX(), m.getY() + 1, m.getZ(), 1f, 1f);
		m.getNavigation().stop();
		m.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		WorldFx.particleIn(h.level, PORTAL, at.getX() + 0.5, at.getY() + 1.4, at.getZ() + 0.5, 0.3, 0.8, 0.3, 0.3, 30);
		WorldFx.soundIn(h.dim, "minecraft:entity.enderman.teleport", "hostile", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 1f, 1f);
	}

	/** An Enderman goes home: gone in a burst of the void, a thread of light running up from where it stood into the door. */
	private static void through(Home h, Mob m, ThreadLocalRandom rnd) {
		double x = m.getX(), y = m.getY() + m.getBbHeight() * 0.6, z = m.getZ();
		WorldFx.particleIn(h.level, PORTAL, x, y, z, 0.3, 0.9, 0.3, 0.8, 50);
		WorldFx.particleIn(h.level, VOID, x, y, z, 0.3, 0.9, 0.3, 0.05, 24);
		for (int i = 0; i <= 18; i++) {
			double k = i / 18.0;
			WorldFx.particleIn(h.level, LIGHT, x + (h.cx - x) * k, y + (h.cy - y) * k, z + (h.cz - z) * k, 0.05, 0.05, 0.05, 0.0, 1);
		}
		WorldFx.particleIn(h.level, LIGHT, h.cx, h.cy, h.cz, 0.8, 0.8, 0.8, 0.05, 20);
		WorldFx.soundIn(h.dim, "minecraft:entity.enderman.teleport", "hostile", x, y, z, 1.2f, 0.8f);
		WorldFx.soundIn(h.dim, "minecraft:block.amethyst_block.chime", "blocks", h.cx, h.cy, h.cz, 3f, 0.7f + rnd.nextFloat() * 0.5f);
		m.discard();
		homeward++;
	}

	/** The door closes, from its rim inwards. The Endermen still on their way stay where they are. */
	private static void close(Home h) {
		h.closing = h.age;
		for (Walker w : h.walkers.values()) {
			if (h.level.getEntity(w.id) instanceof Mob m) {
				m.getNavigation().stop();
			}
		}
		h.walkers.clear();
		WorldFx.soundIn(h.dim, "minecraft:block.beacon.deactivate", "blocks", h.cx, h.cy, h.cz, 3f, 0.9f);
		WorldFx.soundIn(h.dim, "minecraft:block.portal.ambient", "blocks", h.cx, h.cy, h.cz, 2f, 1.4f);
	}

	/** The panes go out from the rim inwards, each dwindling into its own spot. */
	private static void fold(Home h, int c) {
		double reach = DOOR * (1 - (c + 1) / (double) (CLOSING - 6));
		for (Cell cell : h.cells) {
			if (cell.shown && cell.r >= reach) {
				cell.shown = false;
				move(cell.display, pane(h, cell.h, cell.v, 0.02F), 5);
			}
		}
	}

	/** The door is shut: a last flash of light where it stood; the finished anchor stands on. */
	private static void shut(Home h) {
		for (Cell c : h.cells) {
			BlockFlight.drop(c.display);
		}
		h.cells.clear();
		WorldFx.particleIn(h.level, LIGHT, h.cx, h.cy, h.cz, 0.6, 0.6, 0.6, 0.25, 90);
		WorldFx.particleIn(h.level, VOID, h.cx, h.cy, h.cz, 2.5, 2.5, 2.5, 0.02, 60);
		WorldFx.soundIn(h.dim, "minecraft:block.end_portal_frame.fill", "blocks", h.cx, h.cy, h.cz, 2.5f, 1.4f);
		WorldFx.soundIn(h.dim, "minecraft:block.amethyst_block.resonate", "blocks", h.cx, h.cy, h.cz, 3f, 1.2f);
	}

	// ------------------------------------------------------------------ lock: the anchor sealed

	/**
	 * What of a locked anchor breaks away, and when: its chains first, then everything from the top down. That is the top
	 * half of each pillar (the core under a barrel stands, holding it up) and all of it over the floor but the fitting's
	 * posts; never the floor, the plinth or a barrel, and nothing that is not the anchor's own.
	 */
	private static List<Piece> breaking(ServerLevel level, BlockPos origin, PaleAnchorDesign.Blueprint was, PaleAnchorDesign.Blueprint whole) {
		int oy = origin.getY();
		List<BlockPos> pillars = was.pillars;
		int[] standing = new int[pillars.size()];
		boolean[] holds = new boolean[pillars.size()];
		for (int k = 0; k < pillars.size(); k++) {
			BlockPos foot = pillars.get(k);
			if (!Loaded.ready(level, foot)) {
				continue;
			}
			int h = 0;
			while (h < 64 && level.getBlockState(foot.above(h)).is(Blocks.PURPUR_PILLAR)) {
				h++;
			}
			standing[k] = h;
			holds[k] = level.getBlockState(foot.above(h)).hasBlockEntity();
		}
		Set<Long> done = new HashSet<>();
		List<Piece> out = new ArrayList<>();
		int top = oy;
		for (PaleAnchorDesign.Blueprint bp : new PaleAnchorDesign.Blueprint[] {was, whole}) {
			for (long key : bp.blocks.keySet()) {
				BlockPos pos = BlockPos.of(key);
				if (pos.getY() <= oy || !done.add(key) || !Loaded.ready(level, pos) || !falls(pos, origin, pillars, standing, holds)) {
					continue;
				}
				BlockState now = level.getBlockState(pos), a = was.at(pos), b = whole.at(pos);
				if (now.isAir() || now.hasBlockEntity() || !(a != null && now.is(a.getBlock()) || b != null && now.is(b.getBlock()))) {
					continue;
				}
				out.add(new Piece(pos, now, was.chains.contains(key) || whole.chains.contains(key)));
				top = Math.max(top, pos.getY());
			}
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (Piece p : out) {
			p.at = p.chain ? 2 + rnd.nextInt(5) : 8 + (int) ((top - p.pos.getY()) * 1.3) + rnd.nextInt(4);
		}
		out.sort(Comparator.comparingInt((Piece p) -> p.at));
		return out;
	}

	/** Whether a block of a locked anchor breaks away (see {@link #breaking}). */
	private static boolean falls(BlockPos pos, BlockPos origin, List<BlockPos> pillars, int[] standing, boolean[] holds) {
		for (int k = 0; k < pillars.size(); k++) {
			BlockPos foot = pillars.get(k);
			int dx = pos.getX() - foot.getX(), dz = pos.getZ() - foot.getZ();
			if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
				if (holds[k] && dx == 0 && dz == 0) {
					return false;
				}
				return pos.getY() > origin.getY() + Math.max(2, (standing[k] + 1) / 2);
			}
		}
		return pos.getY() >= origin.getY() + 3;
	}

	/** The anchor is sealed: a purple flash and a deep sound, a shock running out over its floor; its fitting is the lock. */
	private static void seal(Lock l) {
		BlockPos f = l.fitting;
		double x = f.getX() + 0.5, y = f.getY() + 0.5, z = f.getZ() + 0.5;
		WorldFx.soundIn(l.dim, "minecraft:block.respawn_anchor.deplete", "blocks", x, y, z, 4f, 0.5f);
		WorldFx.soundIn(l.dim, "minecraft:block.end_portal_frame.fill", "blocks", x, y, z, 3f, 0.4f);
		WorldFx.soundIn(l.dim, "minecraft:entity.wither.break_block", "hostile", x, y, z, 2.5f, 0.5f);
		WorldFx.soundIn(l.dim, "minecraft:block.beacon.deactivate", "blocks", x, y, z, 3f, 0.5f);
		WorldFx.particleIn(l.level, VOID, x, y + 1, z, 1.2, 1.2, 1.2, 1.2, 220);
		WorldFx.particleIn(l.level, "minecraft:witch", x, y + 1, z, 1.5, 1.5, 1.5, 0.3, 80);
		WorldFx.particleIn(l.level, "minecraft:sonic_boom", x, y + 0.6, z, 0, 0, 0, 0, 1);
		if (Loaded.ready(l.level, f)) {
			l.level.setBlock(f, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
		}
		pulse(l);
	}

	/** The lock pulses: tears, and the void drawn in round it. */
	private static void pulse(Lock l) {
		BlockPos f = l.fitting;
		WorldFx.particleIn(l.level, "minecraft:falling_obsidian_tear", f.getX() + 0.5, f.getY() + 0.8, f.getZ() + 0.5, 0.4, 0.3, 0.4, 0.0, 16);
		WorldFx.particleIn(l.level, VOID, f.getX() + 0.5, f.getY() + 0.5, f.getZ() + 0.5, 0.2, 0.2, 0.2, 0.25, 40);
	}

	/** The shock of the sealing running out over the floor and on past its edge. */
	private static void shock(Lock l, int t) {
		double r = 1.5 + t * 1.7, y = l.origin.getY() + 1.15, x0 = l.origin.getX() + 0.5, z0 = l.origin.getZ() + 0.5;
		int n = (int) (r * 2.5);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n, x = x0 + Math.cos(a) * r, z = z0 + Math.sin(a) * r;
			WorldFx.particleIn(l.level, VOID, x, y, z, 0.05, 0.05, 0.05, 0.0, 1);
			if (i % 2 == 0) {
				WorldFx.particleIn(l.level, "minecraft:large_smoke", x, y, z, Math.cos(a), 0.05, Math.sin(a), 0.12, 0);
			}
		}
		if (t == 1) {
			WorldFx.soundIn(l.dim, "minecraft:entity.generic.explode", "blocks", x0, y, z0, 2f, 0.5f);
		}
	}

	/** One tick of a locked anchor breaking apart; true once all of it that breaks away is gone. */
	private static boolean step(Lock l, ThreadLocalRandom rnd) {
		l.age++;
		if (l.age <= 12) {
			shock(l, l.age);
		}
		if (l.age % 30 == 0 && l.age <= 90) {
			pulse(l);
		}
		int heard = 0;
		while (l.next < l.pieces.size() && l.pieces.get(l.next).at <= l.age) {
			Piece p = l.pieces.get(l.next++);
			if (breakAway(l, p, rnd) && heard++ < 2) {
				String sound = p.chain ? "minecraft:block.chain.break" : p.state.is(Blocks.GOLD_BLOCK) ? "minecraft:block.metal.break" : "minecraft:block.stone.break";
				WorldFx.soundIn(l.dim, sound, "blocks", p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 1.6f, 0.5f + rnd.nextFloat() * 0.25f);
			}
		}
		if (l.next < l.pieces.size() && l.age > 6 && l.age % 10 == 5) {
			// the anchor groans as it goes
			Piece p = l.pieces.get(l.next);
			WorldFx.soundIn(l.dim, "minecraft:entity.wither.break_block", "hostile", p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 2f,
					0.4f + rnd.nextFloat() * 0.2f);
		}
		l.falls.removeIf(f -> tumble(l, f, rnd));
		return l.next >= l.pieces.size() && l.falls.isEmpty();
	}

	/**
	 * A block breaks away from a locked anchor: gone from its place, and (where someone could see it) falling away into the
	 * void. One with someone standing on it holds. False if it was not there to break.
	 */
	private static boolean breakAway(Lock l, Piece p, ThreadLocalRandom rnd) {
		if (!Loaded.ready(l.level, p.pos) || !l.level.getBlockState(p.pos).is(p.state.getBlock()) || PaleReach.occupied(l.level, p.pos.above())) {
			return false;
		}
		l.level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 3);
		if (l.falls.size() < PaleReach.flyMax() && PaleReach.watched(l.level, p.pos)) {
			Fall f = loosen(l, p, rnd);
			if (f != null) {
				l.falls.add(f);
			}
		}
		if (rnd.nextInt(4) == 0) {
			WorldFx.particleIn(l.level, "minecraft:large_smoke", p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 0.3, 0.3, 0.3, 0.02, 2);
		}
		return true;
	}

	/** A broken block flung out from its place, far enough out to fall clear of the floor. */
	private static Fall loosen(Lock l, Piece p, ThreadLocalRandom rnd) {
		BlockPos pos = p.pos;
		Display.BlockDisplay d = BlockFlight.spawn(l.level, pos.getX(), pos.getY() + 1, pos.getZ(), p.state, BlockFlight.pose(0, 0, 0, new Quaternionf(), 1F));
		if (d == null) {
			return null;
		}
		double dx = pos.getX() - l.origin.getX(), dz = pos.getZ() - l.origin.getZ(), out = Math.sqrt(dx * dx + dz * dz);
		double a = out < 1.5 ? rnd.nextDouble() * Math.PI * 2 : Math.atan2(dz, dx) + rnd.nextDouble(-0.35, 0.35);
		double vy = p.chain ? rnd.nextDouble(0.0, 0.05) : rnd.nextDouble(0.05, 0.22);
		// (the ticks till it is down to the floor, by when it must be past the floor's edge)
		double up = Math.max(1, pos.getY() - l.origin.getY() + 1);
		double down = (vy + Math.sqrt(vy * vy + 2 * GRAVITY * up)) / GRAVITY;
		double vh = Math.max(0.08, (PaleAnchorDesign.R + 3.5 - out) / down) * rnd.nextDouble(1.0, 1.5);
		return new Fall(d, pos, Math.cos(a) * vh, vy, Math.sin(a) * vh, BlockFlight.randomAxis(rnd), (float) rnd.nextDouble(0.04, 0.22), 50 + rnd.nextInt(40));
	}

	/** One tick of a block falling away; true once it is gone. */
	private static boolean tumble(Lock l, Fall f, ThreadLocalRandom rnd) {
		if (f.display.isRemoved()) {
			BlockFlight.forget(f.display);
			return true;
		}
		f.age++;
		if (f.age >= f.life) {
			BlockFlight.drop(f.display);
			return true;
		}
		// its fall set out a stretch at a time, the client easing along each
		if (f.age >= 2 && (f.age - 2) % SEGMENT == 0) {
			double t = f.age - 2 + SEGMENT;
			Quaternionf q = new Quaternionf().rotateAxis((float) (f.spin * t), f.axis);
			move(f.display, BlockFlight.pose(f.vx * t, f.vy * t - 0.5 * GRAVITY * t * t, f.vz * t, q, 1F), SEGMENT);
		}
		if (f.age % 5 == 0 && rnd.nextInt(3) == 0) {
			double t = Math.max(0, f.age - 2);
			WorldFx.particleIn(l.level, rnd.nextBoolean() ? "minecraft:large_smoke" : VOID, f.pos.getX() + 0.5 + f.vx * t,
					f.pos.getY() + 0.5 + f.vy * t - 0.5 * GRAVITY * t * t, f.pos.getZ() + 0.5 + f.vz * t, 0.2, 0.2, 0.2, 0.01, 1);
		}
		return false;
	}
}
