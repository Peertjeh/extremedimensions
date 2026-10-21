package com.extremedimensions.seam;

import java.util.Random;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The First Knot: a knot the size of a hall, deep in the Fray, where the Tether was tied before anything was woven from
 * it. It hangs beside one island far out from the Heart (the same one for the whole world, found from the Seam's seed),
 * past the knot of that island's Fray, with a strand between the two; it is built when someone comes near. Inside is a
 * chamber, and at its heart a pillar of crying obsidian bound in thread: something is tied into it. Use the Plumb Line on
 * the heart to pull, and the Knot Mother wakes (an Elder of the Fray, fought on the Last Weaver's lines; see
 * {@code elder.WeaverService}).
 *
 * <p>The Plumb Line, held in the Seam, points the way: on the islands straight down (to the Fray, by a well), in the Fray
 * towards the Knot, with its distance.
 */
public final class FirstKnot {
	private FirstKnot() {}

	/** The ball's half-widths (x/z and y) and the chamber's inside it; where its middle hangs. */
	static final int RX = 23, RY = 16, IN_X = 16, IN_Y = 11, Y = 28;
	/** The chamber's floor, under the middle. */
	static final int FLOOR = Y - 9;
	/** How near (blocks, flat) a visitor comes before it is built, and how near its middle counts as reaching it. */
	static final double BUILD = 160, REACH = 14;

	/** Where it hangs (x, y, z), worked out once per Seam. */
	private static int[] at;

	/** The knot's middle: x, y, z. */
	public static int[] where() {
		if (at == null) {
			at = find(SeamService.seedOf());
		}
		return at;
	}

	/** The way to work it out: the first island outward from a point far from the Heart; the Knot hangs past its knot. */
	public static int[] find(long seed) {
		long h = seed * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL;
		double ang = ((h >>> 11) & 1023) / 1024.0 * Math.PI * 2;
		double dist = Math.max(200, Config.FIRST_KNOT_DISTANCE);
		int cx0 = (int) Math.floor(Math.cos(ang) * dist / SeamService.CELL), cz0 = (int) Math.floor(Math.sin(ang) * dist / SeamService.CELL);
		for (int ring = 0; ring <= 5; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int cx = cx0 + dx, cz = cz0 + dz;
					SeamArchitect.Shape island = SeamService.islandOf(cx, cz);
					SeamDepth.Knot k = SeamDepth.knot(cx, cz, island, seed);
					if (k == null) {
						continue;
					}
					// Out past the island's own knot, on the same bearing, a little lower than it
					double out = island.edge(k.ang()) + 4 + k.rx() + 8 + RX;
					int x = island.x() + (int) Math.round(Math.cos(k.ang()) * out);
					int z = island.z() + (int) Math.round(Math.sin(k.ang()) * out);
					return new int[] {x, Y, z, cx, cz};
				}
			}
		}
		return new int[] {cx0 * SeamService.CELL + SeamService.CELL / 2, Y, cz0 * SeamService.CELL + SeamService.CELL / 2, cx0, cz0};
	}

	public static void forget() {
		at = null;
	}

	public static void clear() {
		at = null;
	}

	private static String mark() {
		int[] w = where();
		return "fk@" + w[0] + "," + w[1] + "," + w[2];
	}

	public static boolean built() {
		return DataStore.seamCells().contains(mark());
	}

	/** Whether this is the knot's heart (the pillar the Knot Mother is tied into). */
	public static boolean isHeart(BlockPos pos) {
		int[] w = where();
		return pos.getX() == w[0] && pos.getZ() == w[2] && pos.getY() > FLOOR && pos.getY() <= FLOOR + 3 && built();
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 20 != 7 || Config.SEAM_FRAY <= 0) {
			return;
		}
		ServerLevel seam = s.getLevel(SeamService.SEAM);
		if (seam == null || seam.players().isEmpty()) {
			return;
		}
		int[] w = where();
		for (ServerPlayer p : seam.players()) {
			double dx = p.getX() - (w[0] + 0.5), dz = p.getZ() - (w[2] + 0.5);
			double flat = Math.sqrt(dx * dx + dz * dz);
			if (flat < BUILD && !built()) {
				build(seam, false);
			}
			if (!p.isSpectator() && built() && flat < REACH && Math.abs(p.getY() - Y) < RY) {
				reached(p, DataStore.get(p.getUUID()));
			}
		}
	}

	/** A visitor stands in the Knot's chamber: the page, the advancement, the end of the trail. */
	public static void reached(ServerPlayer p, PlayerData d) {
		if (d.knotReached) {
			return;
		}
		d.knotReached = true;
		DataStore.markDirty();
		Journal.unlock(p, d, JournalEntry.FIRST_KNOT);
		Advancements.grant(p, Advancements.FIRST_KNOT);
		WorldFx.soundSelf(p, "minecraft:block.sculk_shrieker.shriek", 0.4f, 0.5f);
		WorldFx.soundSelf(p, "minecraft:block.amethyst_block.resonate", 0.8f, 0.4f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.first_knot.reached"), true);
	}

	/** The Plumb Line used on the heart: the Knot Mother is pulled loose (one at a time, and after her rest). */
	public static InteractionResult wake(ServerPlayer p) {
		long now = p.level().getServer().overworld().getGameTime();
		long rest = Math.max(0, Config.KNOT_MOTHER_REST) * 1200L;
		long slain = slainAt();
		if (com.extremedimensions.elder.WeaverService.knotMotherAwake()) {
			return InteractionResult.SUCCESS;
		}
		if (slain >= 0 && now - slain < rest) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.first_knot.resting"), true);
			WorldFx.soundSelf(p, "minecraft:block.wool.place", 0.6f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		int[] w = where();
		com.extremedimensions.elder.WeaverService.summonKnotMother(p, new BlockPos(w[0], FLOOR + 1, w[2]));
		return InteractionResult.SUCCESS;
	}

	/** When the Knot Mother was last slain (game time), or -1. */
	static long slainAt() {
		long best = -1;
		for (String key : DataStore.seamCells()) {
			if (key.startsWith("km@")) {
				try {
					best = Math.max(best, Long.parseLong(key.substring(3)));
				} catch (NumberFormatException e) {
					// skip a damaged mark
				}
			}
		}
		return best;
	}

	/** The Knot Mother fell: her rest begins. */
	public static void slain(long now) {
		DataStore.seamCells().removeIf(k -> k.startsWith("km@"));
		DataStore.seamCells().add("km@" + now);
		DataStore.markDirty();
	}

	// ------------------------------------------------------------------ the Plumb Line

	/** Every half second while a visitor holds the Plumb Line: which way it pulls, on the action bar, with motes along it. */
	public static void point(ServerPlayer p) {
		if (!p.getMainHandItem().is(ModItems.PLUMB_LINE) && !p.getOffhandItem().is(ModItems.PLUMB_LINE)) {
			return;
		}
		if (!SeamService.inSeam(p)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.plumb.still"), true);
			return;
		}
		if (!SeamDepth.inFray(p)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.plumb.down"), true);
			WorldFx.particleIn(p.level(), "extremedimensions:soul_mote", p.getX(), p.getY() + 0.5, p.getZ(), 0.1, 0.6, 0.1, 0.0, 4);
			return;
		}
		int[] w = where();
		double dx = w[0] + 0.5 - p.getX(), dz = w[2] + 0.5 - p.getZ(), dy = Y - p.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < REACH) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.plumb.here"), true);
			return;
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.plumb.pull", Component.translatable("gui.extremedimensions.dir." + bearing(dx, dz)),
				(int) Math.round(flat)), true);
		// a short line of motes running off towards it
		double len = Math.sqrt(flat * flat + dy * dy);
		for (int i = 1; i <= 5; i++) {
			double t = i * 0.8 / len;
			WorldFx.particleIn(p.level(), "extremedimensions:soul_mote", p.getX() + dx * t, p.getEyeY() - 0.3 + dy * t, p.getZ() + dz * t, 0, 0, 0, 0, 1);
		}
	}

	/** One of the eight points of the compass, the way (dx, dz) runs (north is towards -z, as the game has it). */
	public static String bearing(double dx, double dz) {
		double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
		String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
		return names[(int) Math.round(deg / 45) % 8];
	}

	// ------------------------------------------------------------------ building

	/** Builds the Knot where it hangs (at once for the tests; otherwise through the Seam's build queue). */
	public static void build(ServerLevel seam, boolean now) {
		int[] w = where();
		SeamArchitect.Plan p = plan(w[0], w[1], w[2], w[3], w[4], SeamService.seedOf(), true);
		if (now) {
			p.placeNow(seam);
		} else {
			// As an island is: until its last block stands, a stop or a crash leaves it to be built again (into open air)
			p.cell = mark();
			p.rebuild = DataStore.seamCells().contains("r@" + p.cell);
			p.queue();
			DataStore.seamCells().add("u@" + p.cell);
		}
		DataStore.seamCells().add(mark());
		DataStore.markDirty();
	}

	/** A Knot built at once where it is asked for, without its strand (for the screenshots); returns its heart. */
	public static BlockPos showcase(ServerLevel level, int x, int y, int z) {
		plan(x, y, z, 0, 0, SeamService.seedOf(), false).placeNow(level);
		return new BlockPos(x, y - (Y - FLOOR) + 1, z);
	}

	/** The Knot's plan: the ball of thread round stone, its chamber and floor, the heart, a way in and its strand out. */
	static SeamArchitect.Plan plan(int x, int y, int z, int cx, int cz, long seed, boolean strand) {
		SeamArchitect.Plan p = new SeamArchitect.Plan();
		// (its floor as far under its middle as the real one's)
		int floor = y - (Y - FLOOR);
		Random rnd = new Random(seed * 31 + x * 7L + z);
		// Bands of thread wound round it at random tilts, as round the Fray's own knots but many more
		double[][] bands = new double[7][];
		for (int i = 0; i < bands.length; i++) {
			double a = rnd.nextDouble() * Math.PI, b = rnd.nextDouble() * Math.PI;
			bands[i] = new double[] {Math.cos(a) * Math.sin(b), Math.cos(b), Math.sin(a) * Math.sin(b)};
		}
		for (int dx = -RX; dx <= RX; dx++) {
			for (int dz = -RX; dz <= RX; dz++) {
				for (int dy = -RY; dy <= RY; dy++) {
					double ex = dx / (double) RX, ey = dy / (double) RY, ez = dz / (double) RX;
					double d = ex * ex + ey * ey + ez * ez;
					if (d > 1.0) {
						continue;
					}
					double ix = dx / (double) IN_X, iy = dy / (double) IN_Y, iz = dz / (double) IN_X;
					boolean inside = ix * ix + iy * iy + iz * iz < 1.0;
					int by = y + dy;
					BlockState b;
					if (inside && by > floor) {
						b = Blocks.AIR.defaultBlockState();
					} else if (inside && by == floor) {
						b = SeamDepth.floor(rnd);
					} else {
						double len = Math.sqrt(d) + 1e-6;
						boolean band = false;
						for (double[] n : bands) {
							band |= Math.abs((ex * n[0] + ey * n[1] + ez * n[2]) / len) < 0.09;
						}
						b = d > 0.8 || band ? SeamDepth.thread(rnd) : SeamDepth.core(rnd);
					}
					p.set(x + dx, by, z + dz, b);
				}
			}
		}
		// The heart: a pillar of crying obsidian wound in silk, with chains from the vault down to it
		for (int k = 1; k <= 3; k++) {
			p.set(x, floor + k, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
		}
		for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			p.set(x + o[0], floor + 1, z + o[1], ModBlocks.SEAM_VEIN.defaultBlockState());
			p.set(x + o[0], floor + 2, z + o[1], SeamArchitect.mc("purple_wool", Blocks.COBWEB.defaultBlockState()));
		}
		int vault = y + IN_Y - 1;
		for (int[] o : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {0, 5}, {0, -5}, {5, 0}, {-5, 0}}) {
			for (int cy = vault; cy > floor + 4; cy--) {
				p.set(x + o[0], cy, z + o[1], SeamArchitect.chain());
			}
		}
		// Lanterns round the floor's edge, so the chamber can be fought in
		for (int a = 0; a < 12; a++) {
			double an = a * Math.PI / 6;
			int lx = x + (int) Math.round(Math.cos(an) * (IN_X - 3)), lz = z + (int) Math.round(Math.sin(an) * (IN_X - 3));
			p.set(lx, floor + 1, lz, Blocks.SOUL_LANTERN.defaultBlockState());
		}
		// The way in, towards the island's knot, and the strand from it to that knot
		SeamArchitect.Shape island = strand ? SeamService.islandOf(cx, cz) : null;
		SeamDepth.Knot k = island == null ? null : SeamDepth.knot(cx, cz, island, seed);
		double ux = 1, uz = 0;
		if (k != null) {
			double fx = k.x() - x, fz = k.z() - z, fl = Math.sqrt(fx * fx + fz * fz);
			if (fl > 1) {
				ux = fx / fl;
				uz = fz / fl;
			}
		}
		for (int r = IN_X - 2; r <= RX + 1; r++) {
			for (int wd = -1; wd <= 1; wd++) {
				int tx = x + (int) Math.round(ux * r - uz * wd), tz = z + (int) Math.round(uz * r + ux * wd);
				p.set(tx, floor, tz, SeamDepth.floor(rnd));
				for (int hy = 1; hy <= 4; hy++) {
					p.set(tx, floor + hy, tz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		if (k != null) {
			SeamDepth.strand(p, rnd, new SeamDepth.Knot(x, floor, z, RX + 1, false, floor, Math.atan2(uz, ux)), k);
		}
		p.prepare();
		return p;
	}
}
