package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

import com.extremedimensions.config.Config;
import com.extremedimensions.mending.Binding;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Heart's arena round the Starved One's fight (3.48), built and taken down by the Elders' engine (see
 * {@link ElderArena}): its blocks come up out of the dark under the islands and fly to their places as it rises, and fly
 * off again the moment its fight is over, however it ends.
 *
 * <p>It is the Seam's own stone: the hush masonry, crying obsidian, amethyst and soul lanterns, a little Tetherite ore in
 * its wall. A ring wall stands round the rim of the Heart's island with a gate where each strand runs out to its pillar,
 * and a spire at each of the four points between them, chained to its neighbours and to the crown; a sigil is inlaid into
 * the Heart's floor (where the world may be torn up), its spokes pointing the way to the four pillars; a crown of
 * tines and lanterns hangs high over the altar; broken islands hang round and over the Heart in three layers. None of it
 * is built where the pillars, their nodes, the strands or the altar are, nor in the wedge of sky between the Heart and
 * each pillar, so every node is in plain sight (and in an arrow's reach) from the Heart and from its strand.
 *
 * <p>Its fight changes twice as the Starved One weakens ({@code starved_phase_one}, {@code starved_phase_two}): spikes
 * of crying obsidian and hush stone burst up out of the Heart's floor in a wave from its middle, throwing up and hurting
 * whoever stands where they come ({@code starved_upheaval_damage}), and {@code starved_phase_isles} of the islands come
 * loose one after another over those who fight it: their shadow gathers on the ground, then they come down, as the
 * Elders' do. The Starved One caught under one loses a share of its health and hangs dazed a while.
 */
public final class HeartArena {
	private HeartArena() {}

	/** About how many blocks the Heart's arena holds (its design's table is made that size). */
	static final int ROOM = 24_000;
	/** Where the ring wall stands, out from the Heart's middle (its rim is {@link MendingService#HR}). */
	private static final double WALL_IN = 21.0, WALL_OUT = 23.0;
	/** How far either side of each strand's line the gate through the wall is open, and how high the wall stands. */
	private static final double GATE = 6.0;
	private static final int WALL_HIGH = 6;
	/** How far from each pillar's middle nothing is built, and how far either side of the way out to it. */
	private static final double PILLAR_CLEAR = 26.0, STRAND_CLEAR = 7.0;
	/** How high over the floor the crown hangs, and its breadth. */
	private static final int CROWN_UP = 28;
	private static final double CROWN_R = 8.6;
	/** The islands, in their layers: low round the Heart, over its rim, and high round the crown. */
	private static final int LOW = 12, MID = 8, HIGH = 4, ISLES = LOW + MID + HIGH;
	/** Where the Starved One breaks out of the Heart, from its middle (kept clear of the floor's sigil). */
	private static final int EX = 0, EZ = -8;
	private static final double TAU = Math.PI * 2;

	// ------------------------------------------------------------------ its stone

	private static BlockState mc(String id) {
		return ArenaDesign.mc(id, ModBlocks.HUSH_STONE.defaultBlockState());
	}

	private static BlockState up(BlockState s, Direction d) {
		return s.hasProperty(BlockStateProperties.FACING) ? s.setValue(BlockStateProperties.FACING, d) : s;
	}

	private static BlockState axis(BlockState s) {
		return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, Direction.Axis.Y) : s;
	}

	private static BlockState hanging(BlockState s, boolean hang) {
		return s.hasProperty(BlockStateProperties.HANGING) ? s.setValue(BlockStateProperties.HANGING, hang) : s;
	}

	private static BlockState chain() {
		return axis(ArenaDesign.mc("iron_chain", ArenaDesign.mc("chain", Blocks.COBWEB.defaultBlockState())));
	}

	private static BlockState lantern(boolean hang) {
		return hanging(ArenaDesign.mc("soul_lantern", Blocks.SOUL_LANTERN.defaultBlockState()), hang);
	}

	/** A number between 0 and 1 for a spot, the same every time. */
	private static double noise(int x, int z) {
		long h = x * 341873128712L + z * 132897987541L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFFL) / (double) 0x1000000;
	}

	/** How far a way round the Heart is from the nearest of the four ways to the pillars (radians, 0 to a quarter turn). */
	static double fromStrand(double angle) {
		double t = ((angle - Math.PI / 4) % (Math.PI / 2) + Math.PI / 2) % (Math.PI / 2);
		return Math.min(t, Math.PI / 2 - t);
	}

	/**
	 * Whether a spot ({@code reach} blocks across from it) is kept free for the fight: near a pillar (its island, its
	 * shaft and its node), or near the way from the Heart out to one, where its strand runs and the arrows fly.
	 */
	static boolean kept(int cx, int cz, double x, double z, double reach) {
		for (int i = 0; i < Binding.PILLARS; i++) {
			int[] c = Binding.pillar(i);
			double px = x - c[0], pz = z - c[1];
			if (px * px + pz * pz < (PILLAR_CLEAR + reach) * (PILLAR_CLEAR + reach)) {
				return true;
			}
			double ux = c[0] - cx, uz = c[1] - cz, len = Math.max(1e-3, Math.sqrt(ux * ux + uz * uz));
			ux /= len;
			uz /= len;
			double along = (x - cx) * ux + (z - cz) * uz, side = Math.abs((x - cx) * uz - (z - cz) * ux);
			if (along > -4 && along < len + 4 && side < STRAND_CLEAR + reach) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ the design

	/**
	 * The Heart's design, a structure a step (see {@link ArenaDesign.Draft}): its floor's sigil, the ring wall a stretch at
	 * a time, the spires, the crown, each island, and then the whole of it. {@code centre} is the first air over the
	 * altar.
	 */
	static void design(ArenaDesign.Plan plan, ArenaDesign.Blueprint bp, BlockPos centre, BiConsumer<String, Runnable> step) {
		int cx = centre.getX(), gy = centre.getY() - 1, cz = centre.getZ();
		List<double[]> tops = new ArrayList<>();
		step.accept("heart floor", () -> floor(plan, cx, gy, cz));
		for (int k = 0; k < 8; k++) {
			int part = k;
			step.accept("heart wall", () -> wall(plan, cx, gy, cz, part));
		}
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2;
			step.accept("heart spire", () -> {
				double[] top = spire(plan, (int) Math.round(cx + Math.cos(a) * 22), gy, (int) Math.round(cz + Math.sin(a) * 22));
				if (top != null) {
					tops.add(top);
				}
			});
		}
		step.accept("heart crown", () -> crown(plan, cx, gy, cz));
		for (int n = 0; n < ISLES; n++) {
			int i = n;
			step.accept("heart island", () -> isle(plan, cx, gy, cz, i));
		}
		step.accept("heart whole", () -> whole(plan, bp, cx, gy, cz, tops));
	}

	/**
	 * The sigil inlaid into the Heart's floor: a ring of crying obsidian round the altar, a ring of pale stone and silk
	 * further out with amethyst beyond it, and four spokes of glowing silk pointing the way to the pillars. The altar
	 * and where the Starved One breaks out are left as they are.
	 */
	private static void floor(ArenaDesign.Plan plan, int cx, int gy, int cz) {
		plan.begin();
		plan.kind = ArenaDesign.FLOOR;
		BlockState crying = mc("crying_obsidian"), pale = ModBlocks.POLISHED_PALE_HUSH_STONE.defaultBlockState();
		BlockState vein = ModBlocks.SEAM_VEIN.defaultBlockState(), silk = ModBlocks.SILKED_HUSH_BRICKS.defaultBlockState();
		BlockState gem = mc("amethyst_block");
		int r = MendingService.HR - 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < 2.5 || d > r - 0.5 || (dx - EX) * (dx - EX) + (dz - EZ) * (dz - EZ) < 20) {
					continue;
				}
				double ang = Math.atan2(dz, dx);
				BlockState s = null;
				if (Math.abs(d - 7) < 0.5) {
					s = crying;
				} else if (Math.abs(d - 13) < 0.5) {
					s = ((int) Math.floor((ang + Math.PI) * 13)) % 5 == 0 ? vein : pale;
				} else if (Math.abs(d - 16.5) < 0.5 && ((int) Math.floor((ang + Math.PI) * 16.5)) % 6 == 0) {
					s = gem;
				} else if (d > 3.5 && d * Math.sin(fromStrand(ang)) < 0.75) {
					s = silk;
				}
				if (s != null) {
					plan.inlay(new BlockPos(cx + dx, gy, cz + dz), s);
				}
			}
		}
		plan.commit(0.0);
	}

	/**
	 * One stretch of the ring wall round the Heart's rim (the eighth of it from {@code part} eighths of a turn): hush
	 * bricks on a footing of chiseled stone, worn down here and there, with windows, bands of crying obsidian and glowing
	 * silk on its inner face, a little Tetherite ore, crenellations with amethyst on them and soul lanterns along its top.
	 * It stands on the island and hangs on past its edge; through the gates towards the pillars it is open.
	 */
	private static void wall(ArenaDesign.Plan plan, int cx, int gy, int cz, int part) {
		plan.begin();
		BlockState bricks = ModBlocks.HUSH_BRICKS.defaultBlockState(), cracked = ModBlocks.CRACKED_HUSH_BRICKS.defaultBlockState();
		BlockState mossy = ModBlocks.MOSSY_HUSH_BRICKS.defaultBlockState(), foot = ModBlocks.CHISELED_HUSH_BRICKS.defaultBlockState();
		BlockState cap = ModBlocks.POLISHED_HUSH_STONE.defaultBlockState(), crying = mc("crying_obsidian");
		BlockState silk = ModBlocks.SILKED_HUSH_BRICKS.defaultBlockState(), ore = ModBlocks.TETHERITE_ORE.defaultBlockState();
		BlockState battlement = ModBlocks.HUSH_BRICK_WALL.defaultBlockState(), cluster = up(mc("amethyst_cluster"), Direction.UP);
		int out = (int) Math.ceil(WALL_OUT);
		for (int dx = -out; dx <= out; dx++) {
			for (int dz = -out; dz <= out; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < WALL_IN || d >= WALL_OUT) {
					continue;
				}
				double ang = ((Math.atan2(dz, dx)) % TAU + TAU) % TAU;
				if ((int) (ang / TAU * 8) != part) {
					continue;
				}
				// (the gate where the strand runs out to its pillar)
				if (d * Math.sin(fromStrand(ang)) < GATE) {
					continue;
				}
				int x = cx + dx, z = cz + dz;
				if (!plan.loaded(x, gy, z)) {
					continue;
				}
				int along = (int) Math.floor(ang * 22);
				boolean inner = d < WALL_IN + 1;
				boolean ground = !plan.level.getBlockState(new BlockPos(x, gy, z)).isAir();
				int bottom = ground ? gy + 1 : gy - 2;
				double worn = noise(along / 4, 77);
				int top = gy + WALL_HIGH - (worn > 0.78 ? 1 + (int) ((worn - 0.78) * 14) : 0);
				for (int y = bottom; y <= top; y++) {
					// windows through it, in its upper half
					if (along % 9 < 2 && y >= gy + 3 && y <= gy + 4) {
						continue;
					}
					BlockState s;
					if (y <= gy + 1) {
						s = foot;
					} else if (y == top) {
						s = cap;
					} else if (inner && y == gy + 3 && along % 12 == 6) {
						s = crying;
					} else if (inner && y == gy + 2 && along % 12 == 0) {
						s = silk;
					} else if (!inner && y == gy + 4 && along % 23 == 11) {
						s = ore;
					} else {
						double n = noise(x * 3 + y, z * 3 - y);
						s = n > 0.86 ? cracked : n > 0.78 ? mossy : bricks;
					}
					plan.air(x, y, z, s);
				}
				if (top == gy + WALL_HIGH) {
					if (!inner && (along & 1) == 0) {
						plan.air(x, top + 1, z, along % 10 == 0 ? cluster : battlement);
					} else if (inner && along % 10 == 5) {
						plan.air(x, top + 1, z, lantern(false));
					}
				}
			}
		}
		plan.commit(0.5);
	}

	/** A spire on the wall: a shaft of hush pillars banded with crying obsidian, capped, with a soul lantern on top; its top. */
	private static double[] spire(ArenaDesign.Plan plan, int x, int gy, int z) {
		if (!plan.loaded(x, gy, z)) {
			return null;
		}
		plan.begin();
		BlockState shaft = axis(ModBlocks.HUSH_PILLAR.defaultBlockState()), corner = ModBlocks.POLISHED_PALE_HUSH_STONE.defaultBlockState();
		BlockState crying = mc("crying_obsidian"), cap = ModBlocks.CHISELED_HUSH_BRICKS.defaultBlockState(), vein = ModBlocks.SEAM_VEIN.defaultBlockState();
		BlockState cluster = up(mc("amethyst_cluster"), Direction.UP);
		int high = 16;
		boolean ground = !plan.level.getBlockState(new BlockPos(x, gy, z)).isAir();
		int bottom = ground ? gy + 1 : gy - 3;
		for (int y = bottom; y <= gy + high; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean edge = dx != 0 && dz != 0;
					BlockState s = y == gy + 10 ? crying : y == gy + 5 && !edge ? vein : edge ? corner : shaft;
					plan.air(x + dx, y, z + dz, s);
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				plan.air(x + dx, gy + high + 1, z + dz, cap);
				if (dx != 0 && dz != 0) {
					plan.air(x + dx, gy + high + 2, z + dz, cluster);
				}
			}
		}
		plan.air(x, gy + high + 2, z, lantern(false));
		return plan.commit(0.7) ? new double[] {x + 0.5, gy + high + 1.5, z + 0.5} : null;
	}

	/**
	 * The crown over the altar: a band of polished stone and tiles with crying obsidian set round it, tines of hush
	 * pillar and amethyst rising from it, soul lanterns hanging under it on chains, struts to an inner ring, and a heart
	 * of crying obsidian and silk hanging in its middle.
	 */
	private static void crown(ArenaDesign.Plan plan, int cx, int gy, int cz) {
		plan.begin();
		int y0 = gy + CROWN_UP;
		BlockState band = ModBlocks.POLISHED_HUSH_STONE.defaultBlockState(), tiles = ModBlocks.HUSH_TILES.defaultBlockState();
		BlockState crying = mc("crying_obsidian"), shaft = axis(ModBlocks.HUSH_PILLAR.defaultBlockState()), pale = ModBlocks.PALE_HUSH_BRICKS.defaultBlockState();
		BlockState gem = mc("amethyst_block"), cluster = up(mc("amethyst_cluster"), Direction.UP), vein = ModBlocks.SEAM_VEIN.defaultBlockState();
		BlockState bricks = ModBlocks.HUSH_BRICKS.defaultBlockState(), ore = ModBlocks.TETHERITE_ORE.defaultBlockState();
		int out = (int) Math.ceil(CROWN_R + 1);
		for (int dx = -out; dx <= out; dx++) {
			for (int dz = -out; dz <= out; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				double ang = (Math.atan2(dz, dx) % TAU + TAU) % TAU;
				// (crying obsidian set into the band at eight places round it)
				boolean set = ((int) Math.floor(ang / TAU * 32)) % 4 == 0;
				if (d >= CROWN_R - 0.6 && d < CROWN_R + 0.6) {
					plan.air(cx + dx, y0, cz + dz, set ? crying : band);
					plan.air(cx + dx, y0 + 1, cz + dz, set ? ore : tiles);
				} else if (d >= 4 && d < 5) {
					plan.air(cx + dx, y0 - 2, cz + dz, ((int) Math.floor(ang * 5)) % 3 == 0 ? vein : bricks);
				} else if (d >= 5 && d < CROWN_R - 0.6 && Math.abs(Math.sin(2 * ang)) < 0.12) {
					// struts from the inner ring up to the band
					plan.air(cx + dx, y0 - 2 + (int) Math.round((d - 5) / (CROWN_R - 5.6) * 2), cz + dz, pale);
				}
			}
		}
		for (int k = 0; k < 8; k++) {
			double a = k * TAU / 8;
			int x = cx + (int) Math.round(Math.cos(a) * CROWN_R), z = cz + (int) Math.round(Math.sin(a) * CROWN_R);
			// tines, the greater and the lesser by turns
			int h = k % 2 == 0 ? 7 : 4;
			for (int y = 1; y <= h; y++) {
				plan.air(x, y0 + 1 + y, z, y == h ? cluster : y == h - 1 ? gem : y % 3 == 0 ? pale : shaft);
			}
			// and a soul lantern hung under each, between them
			double b = a + TAU / 16;
			int lx = cx + (int) Math.round(Math.cos(b) * CROWN_R), lz = cz + (int) Math.round(Math.sin(b) * CROWN_R);
			int len = 2 + (k % 3);
			for (int c = 1; c <= len; c++) {
				plan.air(lx, y0 - c, lz, chain());
			}
			plan.air(lx, y0 - len - 1, lz, lantern(true));
		}
		// The heart hanging in its middle
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 2) {
						plan.air(cx + dx, y0 - 6 + dy, cz + dz, dx == 0 && dz == 0 ? vein : crying);
					}
				}
			}
		}
		for (int c = 0; c < 2; c++) {
			plan.air(cx, y0 - 4 + c, cz, chain());
		}
		plan.air(cx, y0 - 8, cz, up(mc("amethyst_cluster"), Direction.DOWN));
		plan.commit(0.6);
	}

	/**
	 * Island {@code i} of the three layers, hung in the quarters between the ways to the pillars: low round the Heart,
	 * larger ones over its rim, the greatest high round the crown. A broken cone of hush stone veined with silk and crying
	 * obsidian, tiles and moss on top, amethyst under it, a soul lantern on chains below some and on top of others.
	 */
	private static void isle(ArenaDesign.Plan plan, int cx, int gy, int cz, int i) {
		Random rnd = plan.rnd;
		int layer = i < LOW ? 0 : i < LOW + MID ? 1 : 2;
		int j = layer == 0 ? i : layer == 1 ? i - LOW : i - LOW - MID;
		double quarter = (j % 4) * Math.PI / 2;
		int slot = j / 4;
		double off, r, top;
		int big;
		if (layer == 0) {
			off = (slot - 1) * 0.24;
			r = 30 + rnd.nextDouble() * 15;
			top = gy - 8 + rnd.nextInt(12);
			big = 3 + rnd.nextInt(3);
		} else if (layer == 1) {
			off = (slot == 0 ? -1 : 1) * 0.17;
			r = 24 + rnd.nextDouble() * 12;
			top = gy + 14 + rnd.nextInt(11);
			big = 3 + rnd.nextInt(3);
		} else {
			off = 0;
			r = 18 + rnd.nextDouble() * 8;
			top = gy + 36 + rnd.nextInt(9);
			big = 4 + rnd.nextInt(3);
		}
		double a = quarter + off + (rnd.nextDouble() - 0.5) * 0.12;
		int x = (int) Math.round(cx + Math.cos(a) * r), z = (int) Math.round(cz + Math.sin(a) * r), y = (int) top;
		boolean lit = rnd.nextBoolean();
		if (kept(cx, cz, x + 0.5, z + 0.5, big + 1) || !plan.loaded(x, y, z)) {
			return;
		}
		plan.begin();
		plan.kind = ArenaDesign.ISLE;
		BlockState stone = ModBlocks.HUSH_STONE.defaultBlockState(), cobbled = ModBlocks.COBBLED_HUSH_STONE.defaultBlockState();
		BlockState tiles = ModBlocks.HUSH_TILES.defaultBlockState(), mossy = ModBlocks.MOSSY_HUSH_BRICKS.defaultBlockState();
		BlockState vein = ModBlocks.SEAM_VEIN.defaultBlockState(), crying = mc("crying_obsidian");
		int depth = (int) Math.round(big * 1.4) + 2;
		for (int k = 0; k <= depth; k++) {
			double rk = big * Math.pow(1 - k / (double) (depth + 1), 0.75);
			int ir = (int) Math.ceil(rk);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz) + (noise(x + dx, z + dz) - 0.5) * 1.2;
					if (d > rk) {
						continue;
					}
					double n = noise((x + dx) * 7 + k, (z + dz) * 5 - k);
					BlockState s;
					if (k == 0) {
						s = n > 0.7 ? mossy : n > 0.35 ? tiles : cobbled;
					} else if (n > 0.93) {
						s = crying;
					} else if (n > 0.85) {
						s = vein;
					} else {
						s = k < 2 || n > 0.5 ? stone : cobbled;
					}
					plan.air(x + dx, y - k, z + dz, s);
				}
			}
		}
		plan.air(x, y - depth - 1, z, up(mc("amethyst_cluster"), Direction.DOWN));
		if (lit) {
			int len = 2 + rnd.nextInt(3);
			for (int c = 1; c <= len; c++) {
				plan.air(x + 1, y - depth + 1 - c, z, chain());
			}
			plan.air(x + 1, y - depth - len, z, lantern(true));
		} else {
			plan.air(x, y + 1, z, lantern(false));
			plan.air(x - 1, y + 1, z + 1, up(mc("small_amethyst_bud"), Direction.UP));
		}
		if (plan.commit(0.9)) {
			plan.isles.add(new ArenaDesign.Isle(x, y, z, big, plan.lastGroup));
		}
	}

	/** The whole of it: chains from spire to spire and from each spire up to the crown, and the lie of its floor. */
	private static void whole(ArenaDesign.Plan plan, ArenaDesign.Blueprint bp, int cx, int gy, int cz, List<double[]> tops) {
		for (int k = 0; k < tops.size(); k++) {
			double[] a = tops.get(k), b = tops.get((k + 1) % tops.size());
			if (tops.size() > 1 && a != b) {
				bp.lines.add(new ArenaDesign.Line(a[0], a[1], a[2], b[0], b[1], b[2], 3.0));
			}
			double ux = a[0] - (cx + 0.5), uz = a[2] - (cz + 0.5), len = Math.max(1e-3, Math.sqrt(ux * ux + uz * uz));
			bp.lines.add(new ArenaDesign.Line(a[0], a[1], a[2], cx + 0.5 + ux / len * CROWN_R, gy + CROWN_UP + 0.5, cz + 0.5 + uz / len * CROWN_R, 1.5));
		}
		bp.kinds.addAll(plan.kinds);
		bp.isles.addAll(plan.isles);
		bp.cores.addAll(plan.cores);
		bp.arches.addAll(plan.arches);
		bp.style = ArenaDesign.HEART;
		bp.cx = cx;
		bp.gy = gy;
		bp.cz = cz;
		bp.floorR = MendingService.HR;
		bp.turn = 0;
		bp.size = 1;
		bp.wide = 1;
		((ArrayList<ElderArena.Piece>) bp.pieces).ensureCapacity(plan.pieces.size());
		bp.pieces.addAll(plan.pieces.values());
	}

	// ------------------------------------------------------------------ its fight

	/** A spike's block bursting up at its tick of the upheaval. */
	private record Burst(BlockPos pos, BlockState state, int due, boolean base) {}

	/** An island on its way down: its shadow first, then its blocks falling, then where it strikes. */
	private static final class Fall {
		final ArenaDesign.Isle isle;
		final double x, z;
		final int ground;
		int start, warn;
		final List<FallingBlockEntity> blocks = new ArrayList<>();
		boolean loose;
		int age;

		Fall(ArenaDesign.Isle isle, int ground, int start, int warn) {
			this.isle = isle;
			this.x = isle.x() + 0.5;
			this.z = isle.z() + 0.5;
			this.ground = ground;
			this.start = start;
			this.warn = warn;
		}
	}

	/** The fight on the Heart's arena: its clock, its upheavals' spikes on their way, its islands falling and fallen. */
	private static final class Fight {
		int clock, group = -1;
		final List<Burst> bursts = new ArrayList<>();
		final List<Fall> falls = new ArrayList<>();
		final Set<Integer> fallen = new HashSet<>();
		/** Islands still to come loose in this change of the fight, and when the next does. */
		int loosen, next;
		/** When each player may be thrown by a spike again. */
		final Map<UUID, Integer> thrown = new HashMap<>();
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

	private static Fight fight(Entity so) {
		return FIGHTS.computeIfAbsent(so.getUUID(), k -> new Fight());
	}

	/** The Heart's arena begins to rise round the altar for the Starved One (unless {@code starved_arena} is off). */
	public static void raise(LivingEntity so) {
		FIGHTS.remove(so.getUUID());
		if (Config.STARVED_ARENA <= 0 || !(so.level() instanceof ServerLevel)) {
			return;
		}
		fight(so);
		ElderArena.raise(so, ArenaDesign.HEART, new BlockPos(MendingService.HX, MendingService.HY + 1, MendingService.HZ));
	}

	/** Its fight is over, however it ended: the arena comes apart now, block by block flying off, and what fell with it. */
	public static void release(Entity so) {
		FIGHTS.remove(so.getUUID());
		ElderArena.dissolveNow(so);
	}

	/** On stopping (the arenas themselves are taken down by {@link ElderArena#clear}). */
	public static void clear() {
		FIGHTS.clear();
	}

	/** Whether the Starved One has an arena, standing or on its way (for tests). */
	public static boolean has(Entity so) {
		return ElderArena.has(so);
	}

	/** Every tick of its fight: the spikes of an upheaval bursting up in their turn, and the islands coming down. */
	public static void tick(LivingEntity so, List<ServerPlayer> fighters) {
		Fight f = FIGHTS.get(so.getUUID());
		ElderArena.Arena a = ElderArena.of(so);
		if (f == null) {
			return;
		}
		f.clock++;
		if (!f.bursts.isEmpty()) {
			bursts(so, f, a, fighters);
		}
		if (a == null || a.bp == null || a.dissolving || a.linger >= 0) {
			return;
		}
		if (f.loosen > 0 && f.clock >= f.next) {
			f.loosen--;
			f.next = f.clock + 30;
			loosen(so, f, a, fighters);
		}
		for (Iterator<Fall> it = f.falls.iterator(); it.hasNext();) {
			if (fall(so, f, a, it.next(), fighters)) {
				it.remove();
			}
		}
	}

	/**
	 * Its fight changes ({@code stage} 1 or 2): an upheaval throws spikes up out of the Heart's floor in a wave from its
	 * middle, and islands come loose one after another over those who fight it.
	 */
	public static void change(LivingEntity so, int stage, List<ServerPlayer> fighters) {
		if (!(so.level() instanceof ServerLevel level)) {
			return;
		}
		Fight f = fight(so);
		ElderArena.Arena a = ElderArena.of(so);
		String dim = level.dimension().identifier().toString();
		double cx = MendingService.HX + 0.5, cz = MendingService.HZ + 0.5, fy = MendingService.HY + 1;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		// The floor cracks out from the middle, glowing, and a wave of soul fire runs out over it
		AttackFx.wave(level, AttackFx.STARVED, false, cx, fy + 0.1, cz, 2, 0.9, MendingService.HR + 6);
		AttackFx.burst(level, AttackFx.STARVED, cx, fy, cz, 6);
		WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.upheaval", "hostile", cx, fy + 2, cz, 6f, 1f);
		WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", cx, fy, cz, 5f, 0.4f);
		ElderFx.rumble(level, MendingService::inArena, cx, cz, MendingService.ARENA + 16, 0.9f, 40);
		for (int k = 0; k < 10; k++) {
			double ang = rnd.nextDouble() * TAU;
			ElderFx.line(dim, "minecraft:soul_fire_flame", cx + Math.cos(ang) * 2, fy + 0.15, cz + Math.sin(ang) * 2, cx + Math.cos(ang) * 18, fy + 0.15,
					cz + Math.sin(ang) * 18, 24);
		}
		if (a != null && a.bp != null && !a.dissolving && a.linger < 0) {
			if (f.group < 0) {
				f.group = ElderArena.group(a, ArenaDesign.GROWN);
			}
			spikes(f, a, stage, rnd);
			f.loosen += Math.max(0, Config.STARVED_PHASE_ISLES);
			f.next = f.clock + 40;
		}
	}

	/** The spikes of an upheaval, planned: clusters round the floor, tilted outwards, each rising from its foot. */
	private static void spikes(Fight f, ElderArena.Arena a, int stage, ThreadLocalRandom rnd) {
		int cx = MendingService.HX, cz = MendingService.HZ, fy = MendingService.HY + 1;
		BlockState crying = mc("crying_obsidian"), stone = ModBlocks.HUSH_STONE.defaultBlockState(), cobbled = ModBlocks.COBBLED_HUSH_STONE.defaultBlockState();
		BlockState vein = ModBlocks.SEAM_VEIN.defaultBlockState(), tip = up(mc("amethyst_cluster"), Direction.UP);
		int n = 9 + 5 * stage;
		for (int s = 0; s < n; s++) {
			for (int tries = 0; tries < 8; tries++) {
				double ang = rnd.nextDouble() * TAU, r = 6 + rnd.nextDouble() * 13;
				// (never on the way to a strand, at the altar, or where it broke out)
				if (r * Math.sin(fromStrand(ang)) < 3.5 || Math.hypot(Math.cos(ang) * r - EX, Math.sin(ang) * r - EZ) < 4.5) {
					continue;
				}
				double ux = Math.cos(ang), uz = Math.sin(ang);
				int h = 3 + rnd.nextInt(3 + 2 * stage);
				double lean = 0.25 + rnd.nextDouble() * 0.35;
				int foot = r > 12 && rnd.nextBoolean() ? 2 : 1;
				int start = (int) Math.round(r * 1.1) + rnd.nextInt(4);
				for (int y = 0; y < h; y++) {
					double sx = cx + 0.5 + ux * r + ux * lean * y, sz = cz + 0.5 + uz * r + uz * lean * y;
					int w = y < h / 2 ? foot : 1;
					for (int dx = 0; dx < w; dx++) {
						for (int dz = 0; dz < w; dz++) {
							BlockState st = y == h - 1 ? tip : y == 0 ? crying : rnd.nextInt(5) == 0 ? vein : rnd.nextBoolean() ? stone : cobbled;
							f.bursts.add(new Burst(BlockPos.containing(sx + dx, fy + y, sz + dz), st, f.clock + start + y, y == 0));
						}
					}
				}
				break;
			}
		}
	}

	/** The spikes' blocks due now burst up, throwing whoever stands at a spike's foot. */
	private static void bursts(LivingEntity so, Fight f, ElderArena.Arena a, List<ServerPlayer> fighters) {
		ServerLevel level = (ServerLevel) so.level();
		String dim = level.dimension().identifier().toString();
		int sounds = 0;
		for (Iterator<Burst> it = f.bursts.iterator(); it.hasNext();) {
			Burst b = it.next();
			if (b.due() > f.clock) {
				continue;
			}
			it.remove();
			double x = b.pos().getX() + 0.5, y = b.pos().getY(), z = b.pos().getZ() + 0.5;
			if (b.base()) {
				for (ServerPlayer p : fighters) {
					double dx = p.getX() - x, dz = p.getZ() - z;
					Integer last = f.thrown.get(p.getUUID());
					if (dx * dx + dz * dz < 2.6 * 2.6 && Math.abs(p.getY() - y) < 3 && (last == null || f.clock - last > 15)) {
						f.thrown.put(p.getUUID(), f.clock);
						double damage = Math.max(0, Config.STARVED_UPHEAVAL_DAMAGE);
						if (damage > 0) {
							p.hurtServer(level, level.damageSources().mobAttack(so), (float) damage);
						}
						ElderFx.knock(p, x, z, 0.35, Math.max(0, Config.ELDER_UPHEAVAL_THROW));
					}
				}
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", x, y + 0.2, z, 0.5, 0.1, 0.5, 0.05, 6);
				WorldFx.particleIn(level, "minecraft:large_smoke", x, y + 0.3, z, 0.4, 0.2, 0.4, 0.02, 3);
				if (sounds++ < 3) {
					WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", x, y, z, 2f, 0.5f);
				}
			}
			if (a != null && a.bp != null && f.group >= 0) {
				ElderArena.burst(a, f.group, new ElderArena.Piece(b.pos(), b.state(), ElderArena.AIR, null));
			}
		}
	}

	// ------------------------------------------------------------------ falling islands

	/** An island over someone who fights it (or over the Starved One) starts to come loose; with none over them, the nearest. */
	private static void loosen(LivingEntity so, Fight f, ElderArena.Arena a, List<ServerPlayer> fighters) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<double[]> spots = new ArrayList<>();
		if (!fighters.isEmpty()) {
			ServerPlayer p = fighters.get(rnd.nextInt(fighters.size()));
			spots.add(new double[] {p.getX(), p.getY(), p.getZ()});
		}
		spots.add(new double[] {so.getX(), so.getY(), so.getZ()});
		for (int pass = 0; pass < 2; pass++) {
			for (double[] at : spots) {
				ArenaDesign.Isle best = null;
				double bestD = pass == 0 ? 28 * 28 : Double.MAX_VALUE;
				for (ArenaDesign.Isle isle : a.bp.isles) {
					if (f.fallen.contains(isle.group()) || isle.top() - at[1] < 6) {
						continue;
					}
					double dx = isle.x() + 0.5 - at[0], dz = isle.z() + 0.5 - at[2];
					int ground = ground(a, isle);
					if (dx * dx + dz * dz < bestD && ground != Integer.MIN_VALUE && standing(a, isle) >= 0.6) {
						best = isle;
						bestD = dx * dx + dz * dz;
					}
				}
				if (best != null) {
					f.fallen.add(best.group());
					f.falls.add(new Fall(best, ground(a, best), f.clock, Math.max(10, (int) Math.round(Config.ELDER_ISLE_WARN_SECONDS * 20))));
					WorldFx.soundIn(a.level.dimension().identifier().toString(), "minecraft:block.deepslate.break", "hostile", best.x() + 0.5, best.top(),
							best.z() + 0.5, 6f, 0.4f);
					return;
				}
			}
		}
	}

	/** Share of an island's blocks still standing (a sample of them). */
	private static double standing(ElderArena.Arena a, ArenaDesign.Isle isle) {
		if (isle.group() < 0 || isle.group() >= a.groups.size()) {
			return 0;
		}
		List<ElderArena.Piece> group = a.groups.get(isle.group());
		if (group.isEmpty()) {
			return 0;
		}
		int step = Math.max(1, group.size() / 40), seen = 0, up = 0;
		for (int i = 0; i < group.size(); i += step) {
			ElderArena.Piece p = group.get(i);
			seen++;
			if (p.placed && Loaded.ready(a.level, p.pos) && a.level.getBlockState(p.pos) == p.state) {
				up++;
			}
		}
		return seen == 0 ? 0 : up / (double) seen;
	}

	/** Where an island would strike: the first solid ground under its middle within 64 blocks that is not the island itself. */
	private static int ground(ElderArena.Arena a, ArenaDesign.Isle isle) {
		ServerLevel level = a.level;
		int x = isle.x(), z = isle.z();
		for (int y = isle.top() - 2; y > isle.top() - 64 && y > level.getMinY(); y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!Loaded.ready(level, pos)) {
				break;
			}
			ElderArena.Piece p = ElderArena.standing(level, pos);
			if (p != null && p.group == isle.group()) {
				continue;
			}
			BlockState s = level.getBlockState(pos);
			if (!s.isAir() && !s.canBeReplaced()) {
				return y + 1;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** One tick of a falling island: its shadow gathering, then its blocks loosed, then where it strikes. True once done. */
	private static boolean fall(LivingEntity so, Fight f, ElderArena.Arena a, Fall fall, List<ServerPlayer> fighters) {
		ServerLevel level = a.level;
		String dim = level.dimension().identifier().toString();
		fall.age++;
		if (fall.warn > 0) {
			if (fall.warn % 4 == 0) {
				double r = fall.isle.r() + 1;
				int n = (int) Math.max(12, r * 6);
				for (int i = 0; i < n; i++) {
					double t = TAU * i / n;
					WorldFx.particleIn(level, "minecraft:squid_ink", fall.x + Math.cos(t) * r, fall.ground + 0.1, fall.z + Math.sin(t) * r, 0.05, 0.0, 0.05, 0.0, 1);
				}
				WorldFx.particleIn(level, "minecraft:ash", fall.x, fall.isle.top() - 2, fall.z, fall.isle.r(), 1.5, fall.isle.r(), 0.0, 12);
			}
			if (fall.warn % 20 == 0) {
				AttackFx.ring(level, AttackFx.STARVED, fall.x, fall.ground + 0.05, fall.z, Math.max(1, Config.ELDER_ISLE_RADIUS), 22);
				WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", fall.x, fall.isle.top(), fall.z, 4f, 0.4f);
			}
			fall.warn--;
			return false;
		}
		if (!fall.loose) {
			fall.loose = true;
			List<ElderArena.Piece> pieces = new ArrayList<>();
			if (fall.isle.group() >= 0 && fall.isle.group() < a.groups.size()) {
				for (ElderArena.Piece p : a.groups.get(fall.isle.group())) {
					if (p.placed && Loaded.ready(level, p.pos) && level.getBlockState(p.pos) == p.state) {
						pieces.add(p);
					}
				}
			}
			pieces.sort((p, q) -> Integer.compare(p.pos.getY(), q.pos.getY()));
			int room = 150, every = Math.max(1, pieces.size() / room);
			for (int i = 0; i < pieces.size(); i++) {
				ElderArena.Piece p = pieces.get(i);
				ElderArena.fell(a, p);
				if (i % every == 0 && fall.blocks.size() < room) {
					fall.blocks.add(ArenaRuin.rubble(a, p.pos, p.state, 0));
				} else {
					level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
			WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", fall.x, fall.isle.top(), fall.z, 4f, 0.5f);
			return fall.blocks.isEmpty();
		}
		boolean down = fall.age > 240;
		for (FallingBlockEntity e : fall.blocks) {
			down |= e.isRemoved();
		}
		if (down) {
			strike(so, a, fall, fighters);
		}
		return down;
	}

	/** Where an island strikes: everyone near hurt and thrown, and the Starved One under it badly shaken. */
	private static void strike(LivingEntity so, ElderArena.Arena a, Fall fall, List<ServerPlayer> fighters) {
		ServerLevel level = a.level;
		String dim = level.dimension().identifier().toString();
		double r = Math.max(1, Config.ELDER_ISLE_RADIUS);
		WorldFx.particleIn(level, "minecraft:explosion_emitter", fall.x, fall.ground + 0.5, fall.z, 0.5, 0.2, 0.5, 0.0, 1);
		WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", fall.x, fall.ground + 0.5, fall.z, r * 0.6, 0.4, r * 0.6, 0.02, 20);
		AttackFx.wave(level, AttackFx.STARVED, false, fall.x, fall.ground + 0.1, fall.z, 1, 0.8, r * 2);
		WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", fall.x, fall.ground, fall.z, 6f, 0.6f);
		WorldFx.soundIn(dim, "minecraft:block.anvil.land", "hostile", fall.x, fall.ground, fall.z, 4f, 0.5f);
		WorldFx.punch(level, so, fall.x, fall.z, r * 6, 0.8f, 20);
		for (ServerPlayer p : fighters) {
			double dx = p.getX() - fall.x, dy = p.getY() - fall.ground, dz = p.getZ() - fall.z;
			if (dx * dx + dz * dz <= r * r && Math.abs(dy) < 6) {
				p.hurtServer(level, level.damageSources().mobAttack(so), (float) Config.ELDER_ISLE_DAMAGE);
				ElderFx.shove(so, p, 0.8, 0.5);
			}
		}
		double reach = r + so.getBbWidth() / 2;
		double dx = so.getX() - fall.x, dz = so.getZ() - fall.z;
		if (dx * dx + dz * dz <= reach * reach && Math.abs(so.getY() - fall.ground) < so.getBbHeight() + 6) {
			so.setHealth(Math.max(1, so.getHealth() - (float) (Math.max(0, Config.ELDER_ISLE_BOSS_SHARE) * so.getMaxHealth())));
			MendingService.daze((int) Math.round(Config.ELDER_ISLE_DAZE_SECONDS * 20));
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.hurt", "hostile", so.getX(), so.getY() + 4, so.getZ(), 5f, 0.8f);
			WorldFx.particleIn(level, "minecraft:damage_indicator", so.getX(), so.getY() + so.getBbHeight() * 0.7, so.getZ(), 1.5, 1.5, 1.5, 0.2, 20);
			for (ServerPlayer p : fighters) {
				com.extremedimensions.progression.Advancements.grant(p, com.extremedimensions.progression.Advancements.ISLAND_FELL);
			}
		}
	}

	/** Islands of its arena on their way down now (for tests). */
	public static int falling(Entity so) {
		Fight f = FIGHTS.get(so.getUUID());
		return f == null ? 0 : f.falls.size() + f.loosen;
	}

	// ------------------------------------------------------------------ for its drinking of time

	/** Where the soul lanterns of its arena stand now (their light is what it drinks). */
	public static List<BlockPos> lanterns(Entity so) {
		List<BlockPos> out = new ArrayList<>();
		ElderArena.Arena a = ElderArena.of(so);
		if (a == null) {
			return out;
		}
		BlockState lit = ArenaDesign.mc("soul_lantern", Blocks.SOUL_LANTERN.defaultBlockState());
		for (ElderArena.Piece p : a.pieces) {
			if (p.placed && p.state.is(lit.getBlock())) {
				out.add(p.pos);
			}
		}
		return out;
	}
}
