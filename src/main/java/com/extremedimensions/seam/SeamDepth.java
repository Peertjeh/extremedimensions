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
 * <p>Time runs out faster in the Fray, the Thinned that walk its knots hunt from the start, and its snarls (barrels
 * of what the Seam has swallowed) are richer than anything above.
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

	private static BlockState thread(Random rnd) {
		int k = rnd.nextInt(100);
		BlockState white = SeamArchitect.mc("white_wool", Blocks.COBWEB.defaultBlockState());
		return k < 50 ? white : k < 75 ? SeamArchitect.mc("light_gray_wool", white) : k < 88 ? SeamArchitect.mc("purple_wool", white)
				: k < 95 ? SeamArchitect.mc("magenta_wool", white) : ModBlocks.SEAM_VEIN.defaultBlockState();
	}

	private static BlockState core(Random rnd) {
		int k = rnd.nextInt(100);
		BlockState stone = ModBlocks.HUSH_STONE.defaultBlockState();
		return k < 45 ? stone : k < 70 ? SeamArchitect.mc("deepslate", stone) : k < 85 ? SeamArchitect.mc("tuff", stone)
				: k < 95 ? ModBlocks.SEAM_VEIN.defaultBlockState() : SeamArchitect.mc("crying_obsidian", stone);
	}

	private static BlockState floor(Random rnd) {
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
	private static void strand(SeamArchitect.Plan p, Random rnd, Knot a, Knot b) {
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
	private static final Set<UUID> SEEN = new HashSet<>();
	private static final Map<UUID, Integer> CROUCH = new HashMap<>();
	private static final Map<UUID, Well> RISING = new HashMap<>();

	public static void clear() {
		wells = null;
		SEEN.clear();
		CROUCH.clear();
		RISING.clear();
	}

	/** New wells were planned: read them again. */
	public static void forget() {
		wells = null;
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
		return inFray(y) ? Math.max(0, Config.SEAM_FRAY_DRAIN) : 1.0;
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
		for (Well w : wells()) {
			double dx = p.getX() - (w.x() + 0.5), dz = p.getZ() - (w.z() + 0.5);
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
		}
		if (inFray(p.getY()) && tick % 120 == 0) {
			WorldFx.particles(p, "extremedimensions:void_mote", 6.0, 3.0, 0.0, 10);
		}
		if (!inFray(p.getY()) && p.getY() > Config.SEAM_FRAY_TOP + 20) {
			SEEN.remove(id);
		}
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
