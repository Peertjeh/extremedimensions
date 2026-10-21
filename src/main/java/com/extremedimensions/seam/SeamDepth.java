package com.extremedimensions.seam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fray: what lies beneath the islands of the Seam. Far below them, in the dark, hang knots of frayed thread as
 * big as houses, wound round stone, strung together by strands that sag from knot to knot across the nothing. Some
 * islands have a well at their edge, a ring of stone over the drop: jump in and the Seam lets you down gently onto
 * the knot below; crouch on the knot's glowing pad and its updraft carries you back up.
 *
 * <p>Time runs out faster in the Fray, the Thinned that walk its knots hunt from the start, the Frayed Rays dive at
 * whoever comes down, and its snarls (barrels of what the Seam has swallowed) are richer than anything above. Now and
 * then the dark closes in, and the Frayed come with it; and the strands between the knots give way under a visitor who
 * stands still on them (they are woven back a few minutes later).
 */
public final class SeamDepth {
	private SeamDepth() {}

	/** A cell's knot: where it hangs, how wide it is, and whether a well comes down to it (and from how high). */
	public record Knot(int x, int y, int z, int rx, boolean well, int top, double ang) {}

	/** A well, as the world data remembers it: its balcony (x, top, z) and the knot's surface below. */
	public record Well(int x, int top, int z, int knotY) {}

	private static long hash(long a, long b, long salt) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h & Long.MAX_VALUE;
	}

	/** The knot of a cell, out past its island's rim toward one of the cell's corners; null where there is no island. */
	public static Knot knot(int cx, int cz, SeamArchitect.Shape island, long seed) {
		if (island == null) {
			return null;
		}
		long h = hash(cx, cz, seed + 17);
		double ang = Math.PI / 4 + (h & 3) * Math.PI / 2;
		boolean well = ((h >> 8) % 1000) / 1000.0 < Config.SEAM_WELL_CHANCE;
		double dist = island.edge(ang) + 4;
		int x = island.x() + (int) Math.round(Math.cos(ang) * dist);
		int z = island.z() + (int) Math.round(Math.sin(ang) * dist);
		int top = Math.max(16, Math.min(Config.SEAM_FRAY_TOP - 14, 22 + (int) ((h >> 16) % 16)));
		int rx = 5 + (int) ((h >> 24) % 3);
		return new Knot(x, top, z, rx, well, island.top(), ang);
	}

	static BlockState thread(Random rnd) {
		int k = rnd.nextInt(100);
		BlockState white = SeamArchitect.mc("white_wool", Blocks.COBWEB.defaultBlockState());
		return k < 50 ? white : k < 75 ? SeamArchitect.mc("light_gray_wool", white) : k < 88 ? SeamArchitect.mc("purple_wool", white)
				: k < 95 ? SeamArchitect.mc("magenta_wool", white) : ModBlocks.SEAM_VEIN.defaultBlockState();
	}

	static BlockState core(Random rnd) {
		int k = rnd.nextInt(100);
		BlockState stone = ModBlocks.HUSH_STONE.defaultBlockState();
		return k < 45 ? stone : k < 70 ? SeamArchitect.mc("deepslate", stone) : k < 85 ? SeamArchitect.mc("tuff", stone)
				: k < 95 ? ModBlocks.SEAM_VEIN.defaultBlockState() : SeamArchitect.mc("crying_obsidian", stone);
	}

	static BlockState floor(Random rnd) {
		int k = rnd.nextInt(100);
		BlockState bricks = ModBlocks.HUSH_BRICKS.defaultBlockState();
		return k < 45 ? bricks : k < 70 ? ModBlocks.HUSH_STONE.defaultBlockState() : k < 88 ? SeamArchitect.mc("deepslate_tiles", bricks)
				: k < 95 ? thread(rnd) : ModBlocks.SEAM_VEIN.defaultBlockState();
	}

	/**
	 * The Fray of a cell: its knot (with its well, if its island has one) and the strands to the knots east and south.
	 * Touches no world, so it is designed on the architect's thread like the islands.
	 */
	public static SeamArchitect.Plan plan(int cx, int cz, SeamArchitect.Shape island, SeamArchitect.Shape east, SeamArchitect.Shape south, long seed) {
		Knot k = knot(cx, cz, island, seed);
		if (k == null || Config.SEAM_FRAY <= 0) {
			return null;
		}
		SeamArchitect.Plan p = new SeamArchitect.Plan();
		Random rnd = new Random(hash(cx, cz, seed + 23));
		knotBody(p, rnd, k);
		Knot e = knot(cx + 1, cz, east, seed), s = knot(cx, cz + 1, south, seed);
		if (e != null) {
			strand(p, rnd, k, e);
		}
		if (s != null) {
			strand(p, rnd, k, s);
		}
		if (k.well()) {
			well(p, rnd, k, island);
			p.marks.add("w@" + k.x() + "," + k.top() + "," + k.z() + "," + k.y());
		}
		p.marks.add("k@" + k.x() + "," + k.y() + "," + k.z());
		p.cell = "d@" + cx + "," + cz;
		p.prepare();
		return p;
	}

	/** A knot: a ball of thread wound round stone, flattened on top, banded and hung with loose ends. */
	private static void knotBody(SeamArchitect.Plan p, Random rnd, Knot k) {
		int rx = k.rx(), rz = Math.max(4, rx - rnd.nextInt(2));
		double ry = 4.5;
		double cy = k.y() - 2;
		// Bands of thread wound round it at random tilts
		double[][] bands = new double[3][];
		for (int i = 0; i < bands.length; i++) {
			double a = rnd.nextDouble() * Math.PI, b = rnd.nextDouble() * Math.PI;
			bands[i] = new double[] {Math.cos(a) * Math.sin(b), Math.cos(b), Math.sin(a) * Math.sin(b)};
		}
		for (int dx = -rx - 1; dx <= rx + 1; dx++) {
			for (int dz = -rz - 1; dz <= rz + 1; dz++) {
				for (int dy = -6; dy <= 3; dy++) {
					int y = (int) Math.round(cy) + dy;
					if (y > k.y()) {
						continue;
					}
					double ex = dx / (double) rx, ey = (y - cy) / ry, ez = dz / (double) rz;
					double d = ex * ex + ey * ey + ez * ez;
					if (d > 1.0) {
						continue;
					}
					double len = Math.sqrt(ex * ex + ey * ey + ez * ez) + 1e-6;
					boolean band = false;
					for (double[] n : bands) {
						band |= Math.abs((ex * n[0] + ey * n[1] + ez * n[2]) / len) < 0.14;
					}
					BlockState b;
					if (y == k.y()) {
						b = floor(rnd);
					} else if (d > 0.72 || band) {
						b = thread(rnd);
					} else {
						b = core(rnd);
					}
					p.set(k.x() + dx, y, k.z() + dz, b);
				}
			}
		}
		// Loose ends hanging below
		for (int i = 0; i < 3 + rnd.nextInt(3); i++) {
			int hx = k.x() + rnd.nextInt(-rx + 1, rx), hz = k.z() + rnd.nextInt(-rz + 1, rz);
			int hy = (int) Math.round(cy - ry * 0.7);
			int len = 3 + rnd.nextInt(8);
			for (int j = 0; j < len; j++) {
				p.soft(hx, hy - j, hz, j == len - 1 || rnd.nextInt(4) == 0 ? Blocks.COBWEB.defaultBlockState() : thread(rnd));
			}
		}
		if (k.well()) {
			// The landing: a pad of crying obsidian where the updraft rises, soul lanterns at its corners
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					p.set(k.x() + dx, k.y(), k.z() + dz, SeamArchitect.mc("crying_obsidian", ModBlocks.SEAM_VEIN.defaultBlockState()));
				}
			}
			for (int[] c : new int[][] {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}}) {
				p.set(k.x() + c[0], k.y() + 1, k.z() + c[1], Blocks.SOUL_LANTERN.defaultBlockState());
			}
		} else {
			SeamArchitect.lamp(p, rnd, k.x() + rnd.nextInt(-2, 3), k.y() + 1, k.z() + rnd.nextInt(-2, 3));
		}
		if (rnd.nextDouble() < Config.SEAM_SNARL_CHANCE) {
			// A snarl: a barrel of what the Seam has swallowed, tangled in the thread
			double a = rnd.nextDouble() * Math.PI * 2;
			int bx = k.x() + (int) Math.round(Math.cos(a) * (rx - 2)), bz = k.z() + (int) Math.round(Math.sin(a) * (rz - 2));
			p.then(bx, k.y() + 1, bz, SeamArchitect.loot(new BlockPos(bx, k.y() + 1, bz), 4));
			p.soft(bx, k.y() + 2, bz, Blocks.COBWEB.defaultBlockState());
		}
	}

	/** A strand: a sagging ribbon of thread three wide from one knot's edge to the other's, fringed with webs. */
	static void strand(SeamArchitect.Plan p, Random rnd, Knot a, Knot b) {
		double dx = b.x() - a.x(), dz = b.z() - a.z();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < 4) {
			return;
		}
		double ux = dx / flat, uz = dz / flat;
		double ax = a.x() + ux * (a.rx() - 1), az = a.z() + uz * (a.rx() - 1);
		double bx = b.x() - ux * (b.rx() - 1), bz = b.z() - uz * (b.rx() - 1);
		double len = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
		double sag = Math.min(14, len * 0.09);
		int steps = (int) Math.ceil(len * 1.6);
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			double px = ax + (bx - ax) * t, pz = az + (bz - az) * t;
			double py = a.y() + (b.y() - a.y()) * t - sag * 4 * t * (1 - t);
			for (int w = -1; w <= 1; w++) {
				int x = (int) Math.round(px - uz * w), z = (int) Math.round(pz + ux * w);
				int y = (int) Math.round(py);
				p.soft(x, y, z, thread(rnd));
				if (w == 0) {
					p.soft(x, y - 1, z, thread(rnd));
				}
			}
			if (rnd.nextInt(14) == 0) {
				int x = (int) Math.round(px), z = (int) Math.round(pz), y = (int) Math.round(py) - 2;
				int hang = 1 + rnd.nextInt(3);
				for (int j = 0; j < hang; j++) {
					p.soft(x, y - j, z, Blocks.COBWEB.defaultBlockState());
				}
			}
		}
	}

	/** A well at the island's edge: a ring of stone with a hole over the drop, a walk out to it, lanterns on posts. */
	private static void well(SeamArchitect.Plan p, Random rnd, Knot k, SeamArchitect.Shape island) {
		int x = k.x(), z = k.z(), top = k.top();
		BlockState bricks = ModBlocks.HUSH_BRICKS.defaultBlockState();
		BlockState tiles = SeamArchitect.mc("deepslate_tiles", bricks);
		BlockState wall = SeamArchitect.mc("polished_blackstone_brick_wall", SeamArchitect.mc("deepslate_brick_wall", bricks));
		BlockState rock = SeamArchitect.mc("cobbled_deepslate", ModBlocks.HUSH_STONE.defaultBlockState());
		double ix = -Math.cos(k.ang()), iz = -Math.sin(k.ang());
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 3.3 || Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					continue;
				}
				p.set(x + dx, top, z + dz, rnd.nextInt(3) == 0 ? tiles : bricks);
				if (d <= 2.6) {
					p.set(x + dx, top - 1, z + dz, rock);
				}
				if (d <= 2.0) {
					p.set(x + dx, top - 2, z + dz, rock);
				}
				// A low wall round the drop, open toward the island
				double toward = (dx * ix + dz * iz) / Math.max(0.1, d);
				if (d > 2.4 && toward < 0.6) {
					p.set(x + dx, top + 1, z + dz, wall);
				}
			}
		}
		// The walk out from the island, three wide, its growth cleared
		for (int s = 2; s <= 7; s++) {
			for (int w = -1; w <= 1; w++) {
				int wx = (int) Math.round(x + ix * s - iz * w), wz = (int) Math.round(z + iz * s + ix * w);
				p.set(wx, top, wz, rnd.nextInt(4) == 0 ? tiles : bricks);
				p.set(wx, top - 1, wz, rock);
				p.set(wx, top + 1, wz, Blocks.AIR.defaultBlockState());
				p.set(wx, top + 2, wz, Blocks.AIR.defaultBlockState());
			}
		}
		// Posts with soul lanterns either side of the walk, and chains down into the drop
		for (int side = -1; side <= 1; side += 2) {
			int px = (int) Math.round(x + ix * 2.6 - iz * 2.6 * side), pz = (int) Math.round(z + iz * 2.6 + ix * 2.6 * side);
			p.set(px, top + 1, pz, wall);
			p.set(px, top + 2, pz, wall);
			p.set(px, top + 3, pz, Blocks.SOUL_LANTERN.defaultBlockState());
		}
		for (int[] c : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
			int len = 3 + rnd.nextInt(6);
			for (int j = 1; j <= len; j++) {
				p.soft(x + c[0], top - 2 - j, z + c[1], SeamArchitect.mc("iron_chain", SeamArchitect.mc("chain", Blocks.COBWEB.defaultBlockState())));
			}
		}
	}

	/**
	 * Builds a well at the edge of an island at (x, top, z) of radius 12, its knot below and strands out to two more
	 * knots, right away (for the screenshots and a look around). Returns the well (x, top, z) and its knot's height.
	 */
	public static int[] showcase(net.minecraft.server.level.ServerLevel level, int x, int top, int z) {
		SeamArchitect.Shape island = new SeamArchitect.Shape(x, top, z, 12, 0, 0, 0);
		double ang = Math.PI / 4;
		double dist = island.edge(ang) + 4;
		Knot k = new Knot(x + (int) Math.round(Math.cos(ang) * dist), 30, z + (int) Math.round(Math.sin(ang) * dist), 6, true, top, ang);
		Knot e = new Knot(k.x() + 58, 36, k.z() - 6, 5, false, top, ang);
		Knot s = new Knot(k.x() - 6, 25, k.z() + 56, 6, false, top, ang);
		SeamArchitect.Plan p = new SeamArchitect.Plan();
		Random rnd = new Random(7);
		knotBody(p, rnd, k);
		knotBody(p, rnd, e);
		knotBody(p, rnd, s);
		strand(p, rnd, k, e);
		strand(p, rnd, k, s);
		well(p, rnd, k, island);
		p.placeNow(level);
		DataStore.seamCells().add("w@" + k.x() + "," + top + "," + k.z() + "," + k.y());
		for (Knot n : new Knot[] {k, e, s}) {
			DataStore.seamCells().add("k@" + n.x() + "," + n.y() + "," + n.z());
		}
		forget();
		return new int[] {k.x(), top, k.z(), k.y()};
	}

	// ------------------------------------------------------------------ in the Fray

	private static List<Well> wells;
	private static List<int[]> knots;
	private static final Set<UUID> SEEN = new HashSet<>();
	private static final Map<UUID, Integer> CROUCH = new HashMap<>();
	private static final Map<UUID, Well> RISING = new HashMap<>();
	/** When (game time) the dark next closes in on each visitor in the Fray, and when each was last told of a well. */
	private static final Map<UUID, Long> GLOOM = new HashMap<>(), HINTED = new HashMap<>();
	/** Strands giving way: where, and when (game time) they go. */
	private static final Map<Long, Long> GIVING = new HashMap<>();
	/** Strands that gave way: where, what was there, and when (game time) it is woven back. */
	private static final List<Object[]> GONE = new ArrayList<>();
	/** Ticks a strand creaks before it gives way, and ticks before what gave way is woven back. */
	static final int GIVE_TICKS = 20, REWEAVE_TICKS = 20 * 180;

	public static void clear() {
		wells = null;
		knots = null;
		SEEN.clear();
		CROUCH.clear();
		RISING.clear();
		GLOOM.clear();
		HINTED.clear();
		GIVING.clear();
		GONE.clear();
	}

	/** New wells were planned: read them again. */
	public static void forget() {
		wells = null;
		knots = null;
	}

	/** The knots of the Fray, as the world data remembers them: (x, y, z) of each one's surface. */
	public static List<int[]> knots() {
		if (knots == null) {
			List<int[]> out = new ArrayList<>();
			for (String key : DataStore.seamCells()) {
				if (key.startsWith("k@")) {
					String[] v = key.substring(2).split(",");
					try {
						out.add(new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])});
					} catch (RuntimeException e) {
						// not a knot
					}
				}
			}
			knots = out;
		}
		return knots;
	}

	/** Whether a block is part of a knot (not of a strand between knots). */
	static boolean onKnot(BlockPos at) {
		for (int[] k : knots()) {
			double dx = at.getX() - k[0], dz = at.getZ() - k[2];
			if (dx * dx + dz * dz <= 9.5 * 9.5 && at.getY() <= k[1] + 1 && at.getY() >= k[1] - 8) {
				return true;
			}
		}
		return false;
	}

	/** The thread strands are woven of (the wools of {@link #thread(Random)}, by name). */
	private static final Set<String> THREADS = Set.of("white_wool", "light_gray_wool", "purple_wool", "magenta_wool");

	public static boolean thread(BlockState s) {
		return THREADS.contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
	}

	/** A block of thread, as a strand is woven of (for the tests). */
	public static BlockState threadBlock(String wool) {
		return SeamArchitect.mc(wool, Blocks.COBWEB.defaultBlockState());
	}

	public static List<Well> wells() {
		if (wells == null) {
			List<Well> out = new ArrayList<>();
			for (String key : DataStore.seamCells()) {
				if (key.startsWith("w@")) {
					// A well is "w@x,top,z,knotY"; anything else under the prefix (a village's Weaver roll before 2.22) is not one
					String[] v = key.substring(2).split(",");
					if (v.length != 4) {
						continue;
					}
					try {
						out.add(new Well(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])));
					} catch (NumberFormatException e) {
						// not a well
					}
				}
			}
			wells = out;
		}
		return wells;
	}

	public static boolean inFray(double y) {
		return Config.SEAM_FRAY > 0 && y < Config.SEAM_FRAY_TOP;
	}

	public static boolean inFray(ServerPlayer p) {
		return SeamService.inSeam(p) && inFray(p.getY());
	}

	/** How fast a visitor's time runs out at this height. */
	public static double drain(double y) {
		return inFray(y) ? com.extremedimensions.tapestry.Tapestry.frayDrain(Math.max(0, Config.SEAM_FRAY_DRAIN)) : 1.0;
	}

	/** Whether a point is in a well's shaft (over the knot, under the balcony). */
	public static boolean inShaft(Well w, double x, double y, double z, double reach) {
		double dx = x - (w.x() + 0.5), dz = z - (w.z() + 0.5);
		return dx * dx + dz * dz <= reach * reach && y <= w.top() + 1 && y >= w.knotY();
	}

	/** Each tick for a visitor in the Seam: the drop and the updraft of the wells, and what the Fray says on arrival. */
	public static void tick(ServerPlayer p, PlayerData d, int tick) {
		if (Config.SEAM_FRAY <= 0) {
			return;
		}
		UUID id = p.getUUID();
		Well rising = RISING.get(id);
		if (rising != null) {
			rise(p, rising);
			return;
		}
		long now = p.level().getGameTime();
		for (Well w : wells()) {
			double dx = p.getX() - (w.x() + 0.5), dz = p.getZ() - (w.z() + 0.5);
			// Come up to a well the first few times: what it is for
			if (dx * dx + dz * dz < 8 * 8 && Math.abs(p.getY() - w.top()) < 4 && d.wellHints < Config.SEAM_BRIEFING
					&& now - HINTED.getOrDefault(id, -100_000L) > 20 * 120) {
				HINTED.put(id, now);
				d.wellHints++;
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.fray.well_hint").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
			if (dx * dx + dz * dz > 16) {
				continue;
			}
			// Down the drop: the Seam lets the fall go gently
			if (!p.onGround() && p.getDeltaMovement().y < -0.1 && inShaft(w, p.getX(), p.getY(), p.getZ(), 3.2) && p.getY() > w.knotY() + 1.5) {
				WorldFx.effect(p, MobEffects.SLOW_FALLING, 2, 0);
			}
			// On the pad: crouch a moment and the updraft takes you
			boolean onPad = dx * dx + dz * dz <= 1.6 * 1.6 && Math.abs(p.getY() - (w.knotY() + 1)) < 1.2;
			if (onPad && p.isShiftKeyDown()) {
				int held = CROUCH.merge(id, 1, Integer::sum);
				if (held % 5 == 1) {
					WorldFx.particles(p, "extremedimensions:soul_mote", 0.5, 0.2, 0.0, 6);
				}
				if (held >= 20) {
					CROUCH.remove(id);
					RISING.put(id, w);
					p.removeEffect(MobEffects.SLOW_FALLING);
					WorldFx.sound(p, "minecraft:block.beacon.activate", 0.9f, 1.4f);
					WorldFx.sound(p, "extremedimensions:rift_open", 0.6f, 1.6f);
				}
			} else if (onPad) {
				CROUCH.remove(id);
				if (tick % 10 == 0) {
					WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:soul_mote",
							w.x() + 0.5, w.knotY() + 1.5, w.z() + 0.5, 0.4, 0.6, 0.4, 0.0, 3);
				}
			}
		}
		if (inFray(p.getY()) && SEEN.add(id)) {
			// The first time down this visit
			d.frayDescents++;
			Journal.unlock(p, d, JournalEntry.FRAY);
			Advancements.grant(p, Advancements.FRAY_ENTERED);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.fray.entered").withStyle(ChatFormatting.DARK_PURPLE));
			WorldFx.soundSelf(p, "extremedimensions:seam_ambient", 0.8f, 0.6f);
			if (d.frayDescents <= Config.SEAM_BRIEFING) {
				// The first few descents: what the Fray is, on the screen
				WorldFx.fx(p, com.extremedimensions.network.Fx.FRAY_BRIEF, (int) Math.round(Math.max(1, Config.SEAM_FRAY_DRAIN) * 10));
			}
			GLOOM.put(id, now + nextGloom(p));
		}
		if (inFray(p.getY()) && !p.isCreative() && !p.isSpectator()) {
			gloom(p, now);
			if (tick % 20 == 0) {
				fraying(p, now);
			}
		}
		if (inFray(p.getY()) && tick % 120 == 0) {
			WorldFx.particles(p, "extremedimensions:void_mote", 6.0, 3.0, 0.0, 10);
		}
		if (!inFray(p.getY()) && p.getY() > Config.SEAM_FRAY_TOP + 20) {
			SEEN.remove(id);
		}
	}

	private static long nextGloom(ServerPlayer p) {
		int every = Math.max(10, Config.SEAM_FRAY_GLOOM_EVERY) * 20;
		return every / 2 + p.getRandom().nextInt(every);
	}

	/** Now and then the dark of the Fray closes in on a visitor, a heartbeat in it, and the Frayed come out of it. */
	private static void gloom(ServerPlayer p, long now) {
		Long at = GLOOM.get(p.getUUID());
		if (at == null) {
			GLOOM.put(p.getUUID(), now + nextGloom(p));
			return;
		}
		if (now < at) {
			return;
		}
		GLOOM.put(p.getUUID(), now + nextGloom(p));
		if (Config.SEAM_FRAY_GLOOM <= 0) {
			return;
		}
		WorldFx.effect(p, MobEffects.DARKNESS, Config.SEAM_FRAY_GLOOM + 1, 0);
		WorldFx.soundSelf(p, "minecraft:entity.warden.heartbeat", 1.0f, 0.7f);
		WorldFx.soundSelf(p, "extremedimensions:lurker_whisper", 0.7f, 0.6f);
		SeamFauna.rouse(p);
	}

	/**
	 * Each second, for a visitor on a strand (not on a knot): it may begin to give way under them. It creaks for a
	 * second, then that stretch of thread falls away; whoever is still on it falls with it.
	 */
	private static void fraying(ServerPlayer p, long now) {
		if (!p.onGround() || Config.SEAM_FRAY_UNRAVEL <= 0 || p.getRandom().nextDouble() >= Config.SEAM_FRAY_UNRAVEL * com.extremedimensions.tapestry.Tapestry.strandChance()) {
			return;
		}
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		if (!thread(p.level().getBlockState(under)) || onKnot(under) || GIVING.containsKey(under.asLong())) {
			return;
		}
		GIVING.put(under.asLong(), now + GIVE_TICKS);
		WorldFx.sound(p, "minecraft:block.wool.break", 1.2f, 0.5f);
	}

	/** Each tick, for the Seam: strands creak and give way, and what gave way is woven back when nobody is on it. */
	public static void tickWorld(net.minecraft.server.level.ServerLevel level) {
		if (GIVING.isEmpty() && GONE.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		String dim = level.dimension().identifier().toString();
		java.util.Iterator<Map.Entry<Long, Long>> it = GIVING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Long> e = it.next();
			BlockPos at = BlockPos.of(e.getKey());
			if (now % 5 == 0) {
				WorldFx.particleIn(dim, "extremedimensions:void_mote", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0.8, 0.1, 0.8, 0.0, 4);
			}
			if (now < e.getValue()) {
				continue;
			}
			it.remove();
			giveWay(level, at);
		}
		java.util.Iterator<Object[]> back = GONE.iterator();
		while (back.hasNext()) {
			Object[] g = back.next();
			BlockPos at = (BlockPos) g[0];
			if (now < (Long) g[2]) {
				continue;
			}
			if (level.getNearestPlayer(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2.5, false) != null) {
				continue; // someone stands in the gap: it waits
			}
			if (level.isLoaded(at) && level.getBlockState(at).isAir()) {
				level.setBlock(at, (BlockState) g[1], 2);
			}
			back.remove();
		}
	}

	/** A stretch of strand falls away: the thread under the feet, the thread under that, and a little round it. */
	private static void giveWay(net.minecraft.server.level.ServerLevel level, BlockPos at) {
		List<BlockPos> patch = new ArrayList<>();
		patch.add(at);
		patch.add(at.below());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && level.getRandom().nextInt(3) > 0) {
					patch.add(at.offset(dx, 0, dz));
				}
			}
		}
		long back = level.getGameTime() + REWEAVE_TICKS;
		for (BlockPos b : patch) {
			BlockState s = level.getBlockState(b);
			if (!thread(s) || onKnot(b)) {
				continue;
			}
			GONE.add(new Object[] {b.immutable(), s, back});
			level.destroyBlock(b, false);
		}
	}

	/** Weaves back at once every strand that gave way (before the world is saved, so no hole stays for good). */
	public static void reweave(net.minecraft.server.level.ServerLevel level) {
		if (level != null) {
			for (Object[] g : GONE) {
				BlockPos at = (BlockPos) g[0];
				if (level.isLoaded(at) && level.getBlockState(at).isAir()) {
					level.setBlock(at, (BlockState) g[1], 2);
				}
			}
		}
		GONE.clear();
		GIVING.clear();
	}

	/** How many stretches of strand are waiting to be woven back (for the tests). */
	public static int gaps() {
		return GONE.size();
	}

	/** Starts a strand giving way at a spot (for the tests). */
	public static void giveWayAt(BlockPos at, long when) {
		GIVING.put(at.asLong(), when);
	}

	/** Carried up the shaft by the updraft, and set down on the walk out to the island. */
	private static void rise(ServerPlayer p, Well w) {
		double dx = p.getX() - (w.x() + 0.5), dz = p.getZ() - (w.z() + 0.5);
		if (dx * dx + dz * dz > 3.5 * 3.5 || !SeamService.inSeam(p)) {
			RISING.remove(p.getUUID());
			p.removeEffect(MobEffects.LEVITATION);
			WorldFx.effect(p, MobEffects.SLOW_FALLING, 4, 0);
			return;
		}
		WorldFx.effect(p, MobEffects.LEVITATION, 1, 9);
		if (p.tickCount % 3 == 0) {
			WorldFx.particles(p, "extremedimensions:soul_mote", 0.4, 0.6, 0.0, 3);
			WorldFx.particles(p, "minecraft:reverse_portal", 0.4, 0.6, 0.05, 4);
		}
		if (p.getY() > w.top() + 1.2) {
			RISING.remove(p.getUUID());
			p.removeEffect(MobEffects.LEVITATION);
			// Out over the balcony: set down on the walk toward the island
			double[] in = inward(w);
			WorldFx.effect(p, MobEffects.SLOW_FALLING, 3, 0);
			p.teleportTo(w.x() + 0.5 + in[0] * 4.5, w.top() + 1.05, w.z() + 0.5 + in[1] * 4.5);
			WorldFx.sound(p, "extremedimensions:portal_home", 0.5f, 1.4f);
			Advancements.grant(p, Advancements.FRAY_RISEN);
		}
	}

	/** The way from a well toward its island (the walk out runs this way), from the well's corner of its cell. */
	static double[] inward(Well w) {
		// Wells stand at a cell's corner side of their island: the island lies toward the cell's centre
		int cx = Math.floorDiv(w.x(), SeamService.CELL), cz = Math.floorDiv(w.z(), SeamService.CELL);
		double mx = cx * SeamService.CELL + SeamService.CELL / 2.0 - w.x(), mz = cz * SeamService.CELL + SeamService.CELL / 2.0 - w.z();
		double len = Math.sqrt(mx * mx + mz * mz);
		return len < 0.5 ? new double[] {0, 0} : new double[] {mx / len, mz / len};
	}
}
