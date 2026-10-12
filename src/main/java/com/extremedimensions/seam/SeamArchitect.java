package com.extremedimensions.seam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import com.extremedimensions.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The architecture of the Seam: colossal gothic spires, giant looms, villages of tall houses, towers hanging upside
 * down beneath their islands, tilted halos around floating crystals, and chain bridges strung between it all.
 *
 * <p>Everything is designed into a {@link Plan} first (so a design can look at what it already placed) and then built
 * a few thousand blocks per tick from a queue, bottom to top, so even the largest spire never stalls the server.
 */
public final class SeamArchitect {
	private SeamArchitect() {}

	// ------------------------------------------------------------------ blocks

	private static final Map<String, BlockState> LOOKUP = new HashMap<>();

	/** A vanilla block by id, looked up at runtime (block constants come and go between versions). */
	static BlockState mc(String id, BlockState fallback) {
		BlockState s = LOOKUP.computeIfAbsent(id, k -> BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", k))
				.map(h -> h.value().defaultBlockState()).orElse(null));
		return s != null ? s : fallback;
	}

	static BlockState mc(String id) {
		return mc(id, null);
	}

	static BlockState hanging(BlockState s) {
		return s != null && s.hasProperty(BlockStateProperties.HANGING) ? s.setValue(BlockStateProperties.HANGING, true) : s;
	}

	static BlockState axis(BlockState s, Direction.Axis a) {
		return s != null && s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
	}

	private static BlockState stone() { return ModBlocks.HUSH_STONE.defaultBlockState(); }
	private static BlockState brick() { return ModBlocks.HUSH_BRICKS.defaultBlockState(); }
	private static BlockState vein() { return ModBlocks.SEAM_VEIN.defaultBlockState(); }
	private static BlockState dsBrick() { return mc("deepslate_bricks", brick()); }
	private static BlockState dsCrack() { return mc("cracked_deepslate_bricks", dsBrick()); }
	private static BlockState dsTile() { return mc("deepslate_tiles", brick()); }
	private static BlockState dsPol() { return mc("polished_deepslate", stone()); }
	private static BlockState dsChis() { return mc("chiseled_deepslate", dsPol()); }
	private static BlockState bsBrick() { return mc("polished_blackstone_bricks", dsBrick()); }
	private static BlockState dsWall() { return mc("deepslate_brick_wall", dsBrick()); }
	private static BlockState dsSlab() { return mc("deepslate_tile_slab", dsTile()); }
	private static BlockState glass() { return mc("purple_stained_glass", vein()); }
	private static BlockState glass2() { return mc("magenta_stained_glass", glass()); }
	private static BlockState warmGlass() { return mc("orange_stained_glass", glass()); }
	private static BlockState amethyst() { return mc("amethyst_block", vein()); }
	private static BlockState crying() { return mc("crying_obsidian", vein()); }
	private static BlockState chain() { return mc("iron_chain", mc("chain", Blocks.COBWEB.defaultBlockState())); }
	private static BlockState lantern() { return mc("lantern", Blocks.SOUL_LANTERN.defaultBlockState()); }
	private static BlockState soulLantern() { return Blocks.SOUL_LANTERN.defaultBlockState(); }
	private static BlockState endRod() { return mc("end_rod", vein()); }
	private static BlockState moss() { return mc("moss_block", stone()); }
	private static BlockState wool() { return mc("purple_wool", glass()); }
	private static BlockState wool2() { return mc("magenta_wool", wool()); }
	private static BlockState woolDark() { return mc("black_wool", dsTile()); }
	private static BlockState planks() { return mc("dark_oak_planks", brick()); }
	private static BlockState log() { return mc("dark_oak_log", dsPol()); }

	// ------------------------------------------------------------------ plans and the build queue

	/** A design in progress: block positions to states, plus things to do once it stands (filling barrels). */
	public static final class Plan {
		final LinkedHashMap<Long, BlockState> blocks = new LinkedHashMap<>();
		final List<Object[]> after = new ArrayList<>();
		/** Light columns falling from island edges: x, y, z, length. */
		public final List<int[]> falls = new ArrayList<>();

		public void set(int x, int y, int z, BlockState s) {
			if (s != null && y > 1 && y < 250) {
				blocks.put(BlockPos.asLong(x, y, z), s);
			}
		}

		public void soft(int x, int y, int z, BlockState s) {
			if (!has(x, y, z)) {
				set(x, y, z, s);
			}
		}

		public void clear(int x, int y, int z) {
			blocks.remove(BlockPos.asLong(x, y, z));
		}

		public boolean has(int x, int y, int z) {
			return blocks.containsKey(BlockPos.asLong(x, y, z));
		}

		public void then(int x, int y, int z, Consumer<ServerLevel> action) {
			after.add(new Object[] {new BlockPos(x, y, z), action});
		}

		/** Builds it right away (only for small things a player is about to stand on). */
		public void placeNow(ServerLevel level) {
			for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
				level.setBlock(BlockPos.of(e.getKey()), e.getValue(), 2);
			}
			for (Object[] a : after) {
				@SuppressWarnings("unchecked") Consumer<ServerLevel> c = (Consumer<ServerLevel>) a[1];
				c.accept(level);
			}
		}

		/** Queues it, bottom to top, so a structure rises from its island. */
		public void queue() {
			List<Map.Entry<Long, BlockState>> list = new ArrayList<>(blocks.entrySet());
			list.sort((a, b) -> Integer.compare(BlockPos.getY(a.getKey()), BlockPos.getY(b.getKey())));
			for (Map.Entry<Long, BlockState> e : list) {
				QUEUE.add(new Object[] {e.getKey(), e.getValue()});
			}
			for (Object[] a : after) {
				QUEUE.add(new Object[] {null, a[1]});
			}
		}
	}

	private static final ArrayDeque<Object[]> QUEUE = new ArrayDeque<>();

	public static void clear() {
		QUEUE.clear();
	}

	public static int pending() {
		return QUEUE.size();
	}

	private static long built;

	/** Builds up to {@code budget} queued blocks. */
	public static void tick(ServerLevel level, int budget) {
		boolean had = !QUEUE.isEmpty();
		for (int i = 0; i < budget && !QUEUE.isEmpty(); i++) {
			built++;
			Object[] q = QUEUE.poll();
			if (q[0] == null) {
				@SuppressWarnings("unchecked") Consumer<ServerLevel> c = (Consumer<ServerLevel>) q[1];
				c.accept(level);
			} else {
				level.setBlock(BlockPos.of((Long) q[0]), (BlockState) q[1], 2);
			}
		}
		if (had && QUEUE.isEmpty()) {
			com.extremedimensions.ExtremeDimensions.LOGGER.info("Seam architect: done, {} blocks built so far", built);
		}
	}

	/** A rotated local frame on a plan: local +z is the "front" and is turned by rot quarter turns. */
	static final class Frame {
		final Plan p;
		final int ox, oy, oz, rot;

		Frame(Plan p, int ox, int oy, int oz, int rot) {
			this.p = p;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.rot = rot & 3;
		}

		int wx(int lx, int lz) {
			return ox + switch (rot) { case 1 -> -lz; case 2 -> -lx; case 3 -> lz; default -> lx; };
		}

		int wz(int lx, int lz) {
			return oz + switch (rot) { case 1 -> lx; case 2 -> -lz; case 3 -> -lx; default -> lz; };
		}

		BlockState turn(BlockState s) {
			if (s != null && (rot & 1) == 1 && s.hasProperty(BlockStateProperties.AXIS)) {
				Direction.Axis a = s.getValue(BlockStateProperties.AXIS);
				if (a == Direction.Axis.X) {
					return s.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
				}
				if (a == Direction.Axis.Z) {
					return s.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
				}
			}
			return s;
		}

		void set(int lx, int ly, int lz, BlockState s) {
			p.set(wx(lx, lz), oy + ly, wz(lx, lz), turn(s));
		}

		void soft(int lx, int ly, int lz, BlockState s) {
			p.soft(wx(lx, lz), oy + ly, wz(lx, lz), turn(s));
		}

		void clear(int lx, int ly, int lz) {
			p.clear(wx(lx, lz), oy + ly, wz(lx, lz));
		}

		boolean has(int lx, int ly, int lz) {
			return p.has(wx(lx, lz), oy + ly, wz(lx, lz));
		}

		void then(int lx, int ly, int lz, Consumer<ServerLevel> a) {
			p.then(wx(lx, lz), oy + ly, wz(lx, lz), a);
		}

		BlockPos pos(int lx, int ly, int lz) {
			return new BlockPos(wx(lx, lz), oy + ly, wz(lx, lz));
		}
	}

	// ------------------------------------------------------------------ islands

	/**
	 * A floating island: a mossy top over a deep, tapering underside of hush stone threaded with silk, hung with
	 * vines, chains and soul lanterns, with long stone fangs beneath and light pouring off its edges.
	 *
	 * @return the depth of the underside at the centre
	 */
	public static int island(Plan p, Random rnd, int cx, int top, int cz, int r, boolean mossy) {
		double p1 = rnd.nextDouble() * 6.28, p2 = rnd.nextDouble() * 6.28, p3 = rnd.nextDouble() * 6.28;
		int span = r + 3;
		int[][] depth = new int[span * 2 + 1][span * 2 + 1];
		BlockState grass = mc("short_grass"), fern = mc("fern"), allium = mc("allium"), bluet = mc("azure_bluet");
		BlockState vines = mc("cave_vines"), pale = mc("pale_hanging_moss"), roots = mc("hanging_roots");
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double ang = Math.atan2(dz, dx);
				double edge = r * (0.84 + 0.10 * Math.sin(ang * 3 + p1) + 0.06 * Math.sin(ang * 5 + p2) + 0.03 * Math.sin(ang * 11 + p3));
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > edge) {
					continue;
				}
				double f = 1 - dist / edge;
				int d = 2 + (int) Math.round(Math.pow(f, 0.62) * r * 1.15) + rnd.nextInt(2);
				depth[dx + span][dz + span] = d;
				int x = cx + dx, z = cz + dz;
				for (int y = top - d; y <= top; y++) {
					BlockState s;
					if (y == top) {
						s = mossy && rnd.nextInt(10) < 7 ? moss() : (rnd.nextInt(6) == 0 ? brick() : stone());
					} else if (y > top - 3) {
						s = stone();
					} else {
						int k = rnd.nextInt(40);
						s = k == 0 ? vein() : k < 4 ? dsTile() : stone();
					}
					p.set(x, y, z, s);
				}
				// Growth on the top
				if (mossy && f > 0.12) {
					int k = rnd.nextInt(100);
					BlockState g = k < 14 ? grass : k < 17 ? fern : k < 19 ? allium : k < 20 ? bluet : null;
					p.set(x, top + 1, z, g);
				}
				// Growth hanging from the underside near the rim
				if (f < 0.35 && rnd.nextInt(7) == 0) {
					int len = 2 + rnd.nextInt(6);
					int kind = rnd.nextInt(3);
					for (int k = 1; k <= len; k++) {
						BlockState h = kind == 0 && vines != null
								? (rnd.nextInt(4) == 0 && vines.hasProperty(BlockStateProperties.BERRIES) ? vines.setValue(BlockStateProperties.BERRIES, true) : vines)
								: kind == 1 ? pale : roots;
						p.set(x, top - d - k, z, h);
						if (kind == 2) {
							break; // roots are a single block
						}
					}
				}
			}
		}
		// Fangs of stone hanging far below
		int fangs = 2 + r / 5;
		for (int i = 0; i < fangs; i++) {
			int fx = (int) ((rnd.nextDouble() - 0.5) * r), fz = (int) ((rnd.nextDouble() - 0.5) * r);
			int base = depth[fx + span][fz + span];
			if (base == 0) {
				continue;
			}
			int len = 6 + rnd.nextInt(Math.max(2, r));
			double rad = 1.5 + rnd.nextDouble() * 2.0;
			for (int k = 0; k < len; k++) {
				double rr = rad * (1 - (double) k / len);
				int ir = (int) Math.ceil(rr);
				for (int ax = -ir; ax <= ir; ax++) {
					for (int az = -ir; az <= ir; az++) {
						if (ax * ax + az * az <= rr * rr + 0.3) {
							p.set(cx + fx + ax, top - base - k, cz + fz + az, k % 7 == 3 && rnd.nextBoolean() ? vein() : stone());
						}
					}
				}
			}
		}
		// Soul lanterns on long chains beneath
		for (int i = 0; i < 2 + rnd.nextInt(3); i++) {
			int hx = (int) ((rnd.nextDouble() - 0.5) * r * 1.2), hz = (int) ((rnd.nextDouble() - 0.5) * r * 1.2);
			int base = depth[hx + span][hz + span];
			if (base == 0) {
				continue;
			}
			int len = 2 + rnd.nextInt(7);
			for (int k = 1; k <= len; k++) {
				p.soft(cx + hx, top - base - k, cz + hz, chain());
			}
			p.soft(cx + hx, top - base - len - 1, cz + hz, hanging(soulLantern()));
		}
		// Light pouring off the edges
		for (int i = 0; i < 1 + rnd.nextInt(2); i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int ex = cx + (int) Math.round(Math.cos(a) * r * 0.8), ez = cz + (int) Math.round(Math.sin(a) * r * 0.8);
			p.falls.add(new int[] {ex, top, ez, 18 + rnd.nextInt(30)});
		}
		return depth[span][span];
	}

	// ------------------------------------------------------------------ small things

	/** A soul lantern or a warm lantern on a post, sometimes on an arm with a chain. */
	public static void lamp(Plan p, Random rnd, int x, int y, int z) {
		int h = 2 + rnd.nextInt(3);
		for (int k = 0; k < h; k++) {
			p.set(x, y + k, z, dsWall());
		}
		if (rnd.nextBoolean()) {
			p.set(x, y + h, z, rnd.nextBoolean() ? lantern() : soulLantern());
		} else {
			p.set(x, y + h, z, dsWall());
			p.set(x + 1, y + h, z, dsSlab());
			p.set(x + 1, y + h - 1, z, chain());
			p.set(x + 1, y + h - 2, z, hanging(lantern()));
		}
	}

	/** A broken gothic arch with a lantern hanging in it. */
	static void arch(Plan p, Random rnd, int cx, int y, int cz) {
		Frame f = new Frame(p, cx, y, cz, rnd.nextInt(4));
		int half = 3 + rnd.nextInt(2), h = 7 + rnd.nextInt(5);
		boolean broken = rnd.nextBoolean();
		for (int side = -1; side <= 1; side += 2) {
			int top = broken && side == 1 ? h - 3 - rnd.nextInt(3) : h;
			for (int ly = 0; ly < top; ly++) {
				f.set(side * half, ly, 0, rnd.nextInt(5) == 0 ? dsCrack() : dsBrick());
				f.set(side * half, ly, 1, ly == 0 ? bsBrick() : dsBrick());
			}
		}
		// The pointed crown
		for (int lx = -half; lx <= half; lx++) {
			int rise = (int) Math.round((half - Math.abs(lx)) * 0.9);
			if (broken && lx > 0) {
				continue;
			}
			f.set(lx, h + rise, 0, dsTile());
			f.set(lx, h + rise, 1, dsTile());
		}
		f.set(0, h + (int) Math.round(half * 0.9) - 1, 0, chain());
		f.set(0, h + (int) Math.round(half * 0.9) - 2, 0, hanging(soulLantern()));
	}

	/** A crooked dead tree hung with pale moss, like the Pale Garden reached through. */
	static void deadTree(Plan p, Random rnd, int x, int y, int z) {
		BlockState pale = mc("pale_hanging_moss");
		int h = 5 + rnd.nextInt(5);
		int tx = x, tz = z;
		for (int k = 0; k < h; k++) {
			p.set(tx, y + k, tz, log());
			if (k > 2 && rnd.nextInt(3) == 0) {
				tx += rnd.nextInt(3) - 1;
				tz += rnd.nextInt(3) - 1;
				p.set(tx, y + k, tz, log());
			}
		}
		for (int b = 0; b < 3 + rnd.nextInt(3); b++) {
			int bx = tx, bz = tz, by = y + h - 1 - rnd.nextInt(3);
			int sx = rnd.nextInt(3) - 1, sz = rnd.nextInt(3) - 1;
			int len = 2 + rnd.nextInt(3);
			for (int k = 0; k < len; k++) {
				bx += sx;
				bz += sz;
				by += rnd.nextInt(3) == 0 ? 1 : 0;
				p.set(bx, by, bz, log());
			}
			for (int k = 1; k <= 1 + rnd.nextInt(4); k++) {
				p.soft(bx, by - k, bz, pale);
			}
		}
	}

	/** A floating shard of amethyst with a weeping heart. */
	static void crystal(Plan p, Random rnd, int cx, int cy, int cz, int size) {
		for (int dx = -size; dx <= size; dx++) {
			for (int dz = -size; dz <= size; dz++) {
				for (int dy = -size * 3; dy <= size * 3; dy++) {
					double v = Math.abs(dx) + Math.abs(dz) + Math.abs(dy) / 3.0;
					if (v <= size) {
						p.set(cx + dx, cy + dy, cz + dz, v < size - 1.2 ? crying() : (rnd.nextInt(5) == 0 ? glass2() : amethyst()));
					}
				}
			}
		}
	}

	/** Barrels of the Thinned hold silk, pages and old things. */
	static Consumer<ServerLevel> loot(BlockPos pos, int richness) {
		return level -> {
			level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 3);
			SeamService.fillLoot(level, pos, richness);
		};
	}

	// ------------------------------------------------------------------ the great spire

	/**
	 * A colossal gothic spire: tiers of deepslate stepping inward, tall pointed windows of violet glass, a diamond rose
	 * window over the door, buttresses crowned with pinnacles, banners, chains and lanterns hung from every ledge, a
	 * glowing core of seam silk and a spiral stair around it up to a reliquary at the top.
	 */
	public static void spire(Plan p, Random rnd, int cx, int top, int cz, int r) {
		Frame base = new Frame(p, cx, top + 1, cz, rnd.nextInt(4));
		int b0 = Math.max(9, Math.min(12, r / 2 - 1));
		int b = b0;
		int tiers = 3 + rnd.nextInt(2);
		int[] heights = {24 + rnd.nextInt(8), 18 + rnd.nextInt(6), 14 + rnd.nextInt(5), 11 + rnd.nextInt(4)};
		BlockState vines = mc("cave_vines");
		int y = 0;
		int firstH = heights[0];
		int lastFloor = 0;
		for (int t = 0; t < tiers && b >= 5; t++) {
			int h = heights[t];
			Frame f = new Frame(p, cx, top + 1 + y, cz, base.rot);
			// Floor (a ring once off the ground: the stair well stays open), reaching out over the terrace below
			int fl = t == 0 ? b : b + 2;
			for (int lx = -fl; lx <= fl; lx++) {
				for (int lz = -fl; lz <= fl; lz++) {
					if (t == 0 || lx * lx + lz * lz > 20) {
						f.set(lx, 0, lz, t == 0 && (lx + lz & 1) == 0 ? bsBrick() : dsPol());
					}
				}
			}
			lastFloor = y;
			// Walls with pointed windows
			for (int ly = 1; ly <= h; ly++) {
				for (int u = -b; u <= b; u++) {
					for (int side = 0; side < 4; side++) {
						int lx = side == 0 ? u : side == 1 ? b : side == 2 ? -u : -b;
						int lz = side == 0 ? b : side == 1 ? -u : side == 2 ? -b : u;
						if (Math.abs(u) >= b - 1) {
							continue; // the corners belong to the buttresses
						}
						BlockState wall = ly == 1 || ly == h ? bsBrick() : rnd.nextInt(9) == 0 ? dsCrack() : dsBrick();
						boolean front = side == 0;
						int wTop = h - 4;
						boolean window = false;
						int[] centers = front ? new int[] {0} : new int[] {0, 5, -5};
						for (int k : centers) {
							int du = Math.abs(u - k);
							if (Math.abs(k) <= b - 3 && du <= 1 && ly >= 3 && ly <= (du == 0 ? wTop : wTop - 1)) {
								window = true;
							}
						}
						if (t == 0 && front && Math.abs(u) <= 1 && ly <= (u == 0 ? 6 : 5)) {
							f.clear(lx, ly, lz); // the door
							continue;
						}
						if (t > 0 && u == 0 && ly <= 2) {
							f.clear(lx, ly, lz); // out onto the terrace
							continue;
						}
						if (window) {
							f.set(lx, ly, lz, ly % 5 == 0 ? vein() : glass());
						} else {
							f.set(lx, ly, lz, wall);
						}
					}
				}
			}
			// The diamond rose window above the door, front and back
			if (h >= 13) {
				int c = h - 5;
				for (int u = -3; u <= 3; u++) {
					for (int dy = -3; dy <= 3; dy++) {
						int m = Math.abs(u) + Math.abs(dy);
						if (m > 3) {
							continue;
						}
						BlockState s = m == 3 ? vein() : m == 0 ? crying() : glass2();
						f.set(u, c + dy, b, s);
						f.set(-u, c + dy, -b, s);
					}
				}
			}
			// Corner buttresses with pinnacles
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					for (int ly = 0; ly <= h + 3; ly++) {
						for (int ax = -1; ax <= 1; ax++) {
							for (int az = -1; az <= 1; az++) {
								f.set(sx * b + ax, ly, sz * b + az, ly % 7 == 6 ? dsTile() : dsPol());
							}
						}
					}
					int px = sx * b, pz = sz * b;
					f.set(px, h + 4, pz, dsChis());
					f.set(px, h + 5, pz, dsWall());
					f.set(px, h + 6, pz, dsWall());
					f.set(px, h + 7, pz, endRod());
					// A soul lantern hung from each buttress
					f.set(px + sx * 2, h + 2, pz, dsSlab());
					f.set(px + sx * 2, h + 1, pz, chain());
					f.set(px + sx * 2, h, pz, hanging(soulLantern()));
				}
			}
			// The ledge: an overhang with chains, lanterns and vines hanging from it
			int e = b + 1;
			for (int u = -e; u <= e; u++) {
				int[][] ring = {{u, e}, {u, -e}, {e, u}, {-e, u}};
				for (int[] q : ring) {
					f.soft(q[0], h + 1, q[1], dsSlab());
					if ((u & 3) == 2 && rnd.nextInt(3) > 0) {
						int len = 1 + rnd.nextInt(3);
						for (int k = 0; k < len; k++) {
							f.soft(q[0], h - k, q[1], chain());
						}
						f.soft(q[0], h - len, q[1], hanging(rnd.nextInt(3) == 0 ? soulLantern() : lantern()));
					} else if (vines != null && rnd.nextInt(6) == 0) {
						int len = 2 + rnd.nextInt(8);
						for (int k = 0; k < len; k++) {
							f.soft(q[0], h - k, q[1], vines);
						}
					}
				}
			}
			// Warm light inside so the windows glow
			for (int[] q : new int[][] {{b - 1, b - 2}, {-b + 1, b - 2}, {b - 1, -b + 2}, {-b + 1, -b + 2}}) {
				f.set(q[0], 1, q[1], lantern());
			}
			// A parapet around the terrace above
			for (int u = -b; u <= b; u += 2) {
				f.soft(u, h + 2, b, dsWall());
				f.soft(u, h + 2, -b, dsWall());
				f.soft(b, h + 2, u, dsWall());
				f.soft(-b, h + 2, u, dsWall());
			}
			y += h + 1;
			b -= 2;
		}
		// The spire roof, tapering to a point of light
		int bb = b + 2;
		int s = 12 + rnd.nextInt(10);
		for (int k = 0; k <= s; k++) {
			int half = (int) Math.round(bb * (1 - (double) k / s));
			for (int u = -half; u <= half; u++) {
				base.set(u, y + k, half, dsTile());
				base.set(u, y + k, -half, dsTile());
				base.set(half, y + k, u, dsTile());
				base.set(-half, y + k, u, dsTile());
			}
			if (k % 4 == 2) {
				base.set(0, y + k, half, vein());
				base.set(0, y + k, -half, vein());
				base.set(half, y + k, 0, vein());
				base.set(-half, y + k, 0, vein());
			}
		}
		for (int k = 1; k <= 3; k++) {
			base.set(0, y + s + k, 0, k == 3 ? endRod() : vein());
		}
		// The core: a column of silk inside violet glass, from the ground to the roof
		for (int ly = 1; ly < y; ly++) {
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					base.set(ax, ly, az, ax == 0 && az == 0 ? (ly % 6 == 0 ? crying() : vein()) : glass());
				}
			}
		}
		// The spiral stair around it
		for (int ly = 1; ly < y; ly++) {
			double a = ly * Math.PI / 8;
			for (double rr = 2.2; rr <= 4.4; rr += 0.7) {
				for (double da = 0; da < 0.4; da += 0.2) {
					int sx = (int) Math.round(Math.cos(a + da) * rr), sz = (int) Math.round(Math.sin(a + da) * rr);
					if (Math.abs(sx) <= 1 && Math.abs(sz) <= 1) {
						continue;
					}
					base.set(sx, ly, sz, dsPol());
					for (int hh = 1; hh <= 3; hh++) {
						BlockPos above = base.pos(sx, ly + hh, sz);
						// keep headroom over each step, unless it is a wall
						if (Math.abs(sx) < 5 && Math.abs(sz) < 5) {
							p.clear(above.getX(), above.getY(), above.getZ());
						}
					}
				}
			}
		}
		// The reliquary at the top floor
		int bl = b + 2;
		base.then(-bl + 1, lastFloor + 1, -bl + 3, loot(base.pos(-bl + 1, lastFloor + 1, -bl + 3), 3));
		nave(base, rnd, b0);
		// Three satellite towers, left, right and behind, joined to the first terrace by sky bridges
		int terrace = firstH + 1;
		for (int side = 1; side < 4; side++) {
			Frame g = new Frame(p, cx, top + 1, cz, base.rot + side);
			satellite(g, rnd, b0, b0 + 9, terrace + 8 + rnd.nextInt(10), terrace);
		}
	}

	/** The nave in front of a spire: a long hall with a steep roof, a great door, banners and lamps. */
	private static void nave(Frame f, Random rnd, int b0) {
		int z0 = b0 + 1, z1 = b0 + 10, w = 4, h = 13;
		for (int lz = z0; lz <= z1; lz++) {
			for (int lx = -w; lx <= w; lx++) {
				f.set(lx, 0, lz, (lx + lz & 1) == 0 ? bsBrick() : dsPol());
				boolean wall = Math.abs(lx) == w || lz == z1;
				for (int ly = 1; ly <= h; ly++) {
					if (!wall) {
						f.clear(lx, ly, lz);
						continue;
					}
					boolean sideWindow = Math.abs(lx) == w && (lz - z0) % 3 == 1 && ly >= 3 && ly <= ((lz - z0) % 3 == 1 ? 9 : 8);
					boolean door = lz == z1 && Math.abs(lx) <= 1 && ly <= (lx == 0 ? 6 : 5);
					if (door) {
						f.clear(lx, ly, lz);
					} else if (sideWindow) {
						f.set(lx, ly, lz, ly == 9 ? vein() : glass());
					} else {
						f.set(lx, ly, lz, ly == 1 || ly == h ? bsBrick() : rnd.nextInt(10) == 0 ? dsCrack() : dsBrick());
					}
				}
			}
			// Steep roof
			for (int k = 0; k <= w + 1; k++) {
				int half = w + 1 - k;
				f.set(half, h + 1 + k, lz, dsTile());
				f.set(-half, h + 1 + k, lz, dsTile());
				if (lz == z1) {
					for (int lx = -half + 1; lx <= half - 1; lx++) {
						f.set(lx, h + 1 + k, lz, Math.abs(lx) + Math.abs(h + 3 - (h + 1 + k)) <= 1 ? vein() : dsBrick());
					}
				}
			}
			// Lanterns inside on chains
			if ((lz - z0) % 3 == 2) {
				f.set(0, h, lz, chain());
				f.set(0, h - 1, lz, chain());
				f.set(0, h - 2, lz, hanging(lantern()));
			}
		}
		f.set(0, h + w + 3, z1, dsWall());
		f.set(0, h + w + 4, z1, endRod());
		// Banners either side of the great door
		for (int side = -1; side <= 1; side += 2) {
			int uc = side * 3;
			for (int k = 0; k < 10; k++) {
				for (int du = -1; du <= 1; du++) {
					if (k == 9 && du != 0) {
						continue;
					}
					int m = Math.abs(du) + Math.abs(k - 4);
					BlockState cl = k == 0 ? woolDark() : (m == 1 ? wool2() : m == 0 ? vein() : wool());
					f.set(uc + du, 11 - k, z1 + 1, cl);
				}
			}
		}
		// Steps and lamps before the door
		for (int lx = -2; lx <= 2; lx++) {
			f.set(lx, 0, z1 + 1, dsPol());
		}
		lamp(f.p, rnd, f.wx(-4, z1 + 2), f.oy, f.wz(-4, z1 + 2));
		lamp(f.p, rnd, f.wx(4, z1 + 2), f.oy, f.wz(4, z1 + 2));
	}

	/**
	 * A satellite tower at local (0, d), with a pointed roof, and a sky bridge back to the main spire at the height of
	 * its first terrace.
	 */
	private static void satellite(Frame f, Random rnd, int b0, int d, int h, int bridgeY) {
		int w = 2;
		for (int ly = 0; ly <= h; ly++) {
			for (int lx = -w; lx <= w; lx++) {
				for (int lz = d - w; lz <= d + w; lz++) {
					boolean edgeX = Math.abs(lx) == w, edgeZ = Math.abs(lz - d) == w;
					if (ly == 0 || ly == bridgeY) {
						f.set(lx, ly, lz, dsPol());
					} else if (edgeX || edgeZ) {
						boolean window = !(edgeX && edgeZ) && (lx == 0 || lz == d) && ly % 7 >= 3 && ly % 7 <= 5;
						f.set(lx, ly, lz, edgeX && edgeZ ? dsPol() : window ? glass() : ly % 7 == 0 ? dsTile() : dsBrick());
					} else {
						f.clear(lx, ly, lz);
					}
				}
			}
		}
		for (int k = 0; k <= 9; k++) {
			int half = (int) Math.round((w + 1) * (1 - k / 9.0));
			for (int u = -half; u <= half; u++) {
				f.set(u, h + 1 + k, d + half, dsTile());
				f.set(u, h + 1 + k, d - half, dsTile());
				f.set(half, h + 1 + k, d + u, dsTile());
				f.set(-half, h + 1 + k, d + u, dsTile());
			}
		}
		f.set(0, h + 11, d, endRod());
		f.set(0, bridgeY + 3, d, hanging(soulLantern()));
		// The sky bridge from the terrace's edge, through a gap in its parapet, to a doorway in the tower
		for (int lx = -1; lx <= 1; lx++) {
			f.clear(lx, bridgeY + 1, b0);
		}
		for (int lz = b0 + 1; lz < d - w; lz++) {
			for (int lx = -1; lx <= 1; lx++) {
				f.soft(lx, bridgeY, lz, lx == 0 ? planks() : dsTile());
			}
			if (lz % 2 == 0) {
				f.soft(-2, bridgeY + 1, lz, dsWall());
				f.soft(2, bridgeY + 1, lz, dsWall());
			}
			if (lz % 6 == 3) {
				f.soft(0, bridgeY - 1, lz, chain());
				f.soft(0, bridgeY - 2, lz, hanging(lantern()));
			}
		}
		for (int lx = -1; lx <= 1; lx++) {
			for (int ly = 1; ly <= 3; ly++) {
				f.clear(lx, bridgeY + ly, d - w);
			}
		}
	}

	// ------------------------------------------------------------------ the giant loom

	/**
	 * A Weaver's loom the size of a cathedral front: two great pillars, a beam across the top, hundreds of threads of
	 * chain and silk, and a half-woven tapestry of the Weaver's sign in the middle.
	 */
	public static void loom(Plan p, Random rnd, int cx, int top, int cz, int r) {
		Frame f = new Frame(p, cx, top + 1, cz, rnd.nextInt(4));
		int sx = Math.max(7, Math.min(12, r - 5));
		int h = 26 + rnd.nextInt(14);
		for (int side = -1; side <= 1; side += 2) {
			for (int ly = 0; ly <= h + 4; ly++) {
				for (int ax = -1; ax <= 1; ax++) {
					for (int az = -1; az <= 1; az++) {
						boolean core = ax == 0 && az == 0;
						f.set(side * sx + ax, ly, az, core && ly > 2 ? vein() : ly % 8 == 7 ? dsTile() : dsPol());
					}
				}
			}
			f.set(side * sx, h + 5, 0, dsChis());
			f.set(side * sx, h + 6, 0, dsWall());
			f.set(side * sx, h + 7, 0, endRod());
		}
		// Beams
		for (int lx = -sx; lx <= sx; lx++) {
			for (int az = -1; az <= 1; az++) {
				for (int ly = h; ly <= h + 2; ly++) {
					f.set(lx, ly, az, ly == h + 1 && az == 0 ? vein() : dsBrick());
				}
				f.set(lx, 3, az, dsBrick());
			}
		}
		// Threads and the tapestry
		int mid = (h + 4) / 2, woven = 8 + rnd.nextInt(Math.max(1, h - 14));
		int halfW = sx - 3;
		for (int lx = -sx + 2; lx <= sx - 2; lx++) {
			for (int ly = 4; ly < h; ly++) {
				boolean cloth = Math.abs(lx) <= halfW && ly >= 6 && ly < 6 + woven;
				if (cloth) {
					int m = Math.abs(lx) + Math.abs(ly - mid);
					BlockState s = Math.abs(lx) == halfW || ly == 6 ? woolDark()
							: m % 6 == 0 ? vein() : m < halfW - 1 && m % 6 < 2 ? wool2() : wool();
					f.set(lx, ly, 0, s);
				} else {
					int k = rnd.nextInt(14);
					f.set(lx, ly, 0, k == 0 ? Blocks.COBWEB.defaultBlockState() : ly % 6 == 0 && (lx & 1) == 0 ? vein() : chain());
				}
			}
		}
		// Lanterns from the beam
		for (int lx = -sx + 1; lx <= sx - 1; lx += 4) {
			for (int az = -2; az <= 2; az += 4) {
				int len = 1 + rnd.nextInt(4);
				for (int k = 1; k <= len; k++) {
					f.set(lx, h - k + 1, az, chain());
				}
				f.set(lx, h - len, az, hanging(lantern()));
			}
		}
		// A great spool on its side, and silk on the ground
		for (int lx = -3; lx <= 3; lx++) {
			for (int a = 0; a < 16; a++) {
				double an = a * Math.PI / 8;
				f.set(lx, 3 + (int) Math.round(Math.sin(an) * 2.5) - 2, 6 + (int) Math.round(Math.cos(an) * 2.5), Math.abs(lx) == 3 ? log() : wool());
			}
		}
		for (int i = 0; i < 10; i++) {
			f.soft(rnd.nextInt(-r / 2, r / 2 + 1), 0, rnd.nextInt(-r / 2, r / 2 + 1), Blocks.COBWEB.defaultBlockState());
		}
		f.then(sx - 3, 0, -3, loot(f.pos(sx - 3, 0, -3), 1));
	}

	// ------------------------------------------------------------------ villages

	/**
	 * A village of the Thinned: tall, narrow houses with steep roofs and warm windows around a plaza, a bell spire in
	 * the middle and a field of graves.
	 */
	public static void village(Plan p, Random rnd, int cx, int top, int cz, int r) {
		int houses = 5 + rnd.nextInt(3);
		double a0 = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < houses; i++) {
			double a = a0 + i * Math.PI * 2 / houses;
			int hx = cx + (int) Math.round(Math.cos(a) * r * 0.58), hz = cz + (int) Math.round(Math.sin(a) * r * 0.58);
			// Face the plaza: pick the quarter turn whose front (+z) points back to the centre
			double back = Math.atan2(cz - hz, cx - hx);
			int rot = Math.floorMod((int) Math.round((back - Math.PI / 2) / (Math.PI / 2)), 4);
			house(p, rnd, hx, top + 1, hz, rot, i == 0);
			if (i % 2 == 1) {
				double la = a + Math.PI / houses;
				lamp(p, rnd, cx + (int) Math.round(Math.cos(la) * r * 0.42), top + 1, cz + (int) Math.round(Math.sin(la) * r * 0.42));
			}
		}
		// Plaza
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				if (dx * dx + dz * dz <= 26) {
					p.set(cx + dx, top, cz + dz, (dx + dz & 1) == 0 ? brick() : dsTile());
					p.clear(cx + dx, top + 1, cz + dz);
				}
			}
		}
		// The bell spire
		int h = 16 + rnd.nextInt(10);
		for (int ly = 1; ly <= h; ly++) {
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					boolean open = ly > h - 4 && ly < h && (ax == 0 || az == 0) && !(ax == 0 && az == 0);
					if (ly > h - 4 && ly < h && ax == 0 && az == 0) {
						continue;
					}
					if (!open) {
						p.set(cx + ax, top + ly, cz + az, ly % 5 == 0 ? dsTile() : dsBrick());
					}
				}
			}
		}
		p.set(cx, top + h - 1, cz, mc("bell", lantern()));
		for (int k = 1; k <= 6; k++) {
			int half = k < 3 ? 1 : 0;
			for (int ax = -half; ax <= half; ax++) {
				for (int az = -half; az <= half; az++) {
					p.set(cx + ax, top + h + k, cz + az, k == 6 ? endRod() : dsTile());
				}
			}
		}
		// Graves
		BlockState candle = mc("candle");
		for (int i = 0; i < 5 + rnd.nextInt(4); i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int x = cx + (int) Math.round(Math.cos(a) * 3.5), z = cz + (int) Math.round(Math.sin(a) * 3.5);
			if (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1) {
				continue;
			}
			p.set(x, top + 1, z, stone());
			p.set(x, top + 2, z, Blocks.SKELETON_SKULL.defaultBlockState());
			p.soft(x + 1, top + 1, z, candle);
		}
	}

	private static void house(Plan p, Random rnd, int x, int y, int z, int rot, boolean withLore) {
		Frame f = new Frame(p, x, y, z, rot);
		int w = 2 + rnd.nextInt(2), d = 3, h = 6 + rnd.nextInt(5);
		for (int lx = -w; lx <= w; lx++) {
			for (int lz = -d; lz <= d; lz++) {
				f.set(lx, -1, lz, dsPol());
				boolean edgeX = Math.abs(lx) == w, edgeZ = Math.abs(lz) == d;
				for (int ly = 0; ly <= h; ly++) {
					if (!edgeX && !edgeZ) {
						f.clear(lx, ly, lz);
						continue;
					}
					BlockState s = edgeX && edgeZ ? log() : ly == 0 ? dsTile() : ly == h ? planks() : brick();
					boolean window = (ly == 2 || ly == 3 || (ly >= 5 && ly <= 6 && h >= 8))
							&& ((edgeZ && !edgeX && Math.abs(lx) == 1) || (edgeX && !edgeZ && Math.abs(lz) == 1));
					boolean door = lz == d && lx == 0 && ly <= 1;
					if (door) {
						f.clear(lx, ly, lz);
					} else {
						f.set(lx, ly, lz, window ? warmGlass() : s);
					}
				}
			}
		}
		// Upper floor and lanterns inside, so the windows glow
		f.set(-w + 1, 0, -d + 1, lantern());
		if (h >= 8) {
			for (int lx = -w + 1; lx <= w - 1; lx++) {
				for (int lz = -d + 1; lz <= d - 1; lz++) {
					if (!(lx == w - 1 && lz == -d + 1)) {
						f.set(lx, 4, lz, planks());
					}
				}
			}
			f.set(w - 1, 5, d - 1, lantern());
		}
		// The steep roof, gables front and back
		for (int k = 0; k <= w + 1; k++) {
			int half = w + 1 - k;
			for (int lz = -d - 1; lz <= d + 1; lz++) {
				f.set(half, h + 1 + k, lz, dsTile());
				f.set(-half, h + 1 + k, lz, dsTile());
				if (Math.abs(lz) == d) {
					for (int lx = -half + 1; lx <= half - 1; lx++) {
						f.set(lx, h + 1 + k, lz, brick());
					}
				}
			}
		}
		f.set(0, h + w + 3, d, dsWall());
		f.set(0, h + w + 4, d, endRod());
		// A lantern on a chain by the door
		f.set(1, 3, d + 1, dsSlab());
		f.set(1, 2, d + 1, hanging(lantern()));
		if (withLore) {
			f.then(-w + 1, 0, d - 1, loot(f.pos(-w + 1, 0, d - 1), 2));
		}
	}

	// ------------------------------------------------------------------ hanging towers, halos

	/** A tower hanging upside down beneath its island, its point dripping light into the void. */
	public static void hangingTower(Plan p, Random rnd, int cx, int bottomY, int cz) {
		int b = 4 + rnd.nextInt(2);
		int len = 22 + rnd.nextInt(16);
		for (int k = 0; k < len; k++) {
			int y = bottomY - k;
			for (int u = -b; u <= b; u++) {
				for (int side = 0; side < 4; side++) {
					int lx = side == 0 ? u : side == 1 ? b : side == 2 ? -u : -b;
					int lz = side == 0 ? b : side == 1 ? -u : side == 2 ? -b : u;
					boolean window = Math.abs(u) == 1 && k % 8 >= 3 && k % 8 <= 5;
					p.set(cx + lx, y, cz + lz, window ? glass() : Math.abs(u) == b ? dsPol() : k % 8 == 0 ? bsBrick() : dsBrick());
				}
			}
			if (k % 8 == 0) {
				for (int lx = -b + 1; lx <= b - 1; lx++) {
					for (int lz = -b + 1; lz <= b - 1; lz++) {
						p.set(cx + lx, y, cz + lz, dsPol());
					}
				}
				p.set(cx, y - 1, cz, hanging(lantern()));
			}
		}
		// The inverted roof
		int y0 = bottomY - len;
		for (int k = 0; k <= b + 6; k++) {
			int half = (int) Math.round((b + 1) * (1 - (double) k / (b + 6)));
			for (int u = -half; u <= half; u++) {
				p.set(cx + u, y0 - k, cz + half, dsTile());
				p.set(cx + u, y0 - k, cz - half, dsTile());
				p.set(cx + half, y0 - k, cz + u, dsTile());
				p.set(cx - half, y0 - k, cz + u, dsTile());
			}
		}
		int tip = y0 - b - 7;
		p.set(cx, tip, cz, vein());
		for (int k = 1; k <= 5; k++) {
			p.set(cx, tip - k, cz, chain());
		}
		p.set(cx, tip - 6, cz, hanging(soulLantern()));
		p.falls.add(new int[] {cx, tip - 6, cz, 30});
	}

	/** A tilted halo of stone turning around a floating crystal, with motes of rock about it. */
	public static void halo(Plan p, Random rnd, int cx, int top, int cz, int r) {
		int radius = Math.max(9, r - 1);
		int cy = top + radius + 6;
		double tilt = 0.35 + rnd.nextDouble() * 0.5, yaw = rnd.nextDouble() * Math.PI;
		double ux = Math.cos(yaw), uz = Math.sin(yaw);
		double vx = -Math.sin(yaw) * Math.cos(tilt), vy = Math.sin(tilt), vz = Math.cos(yaw) * Math.cos(tilt);
		int steps = radius * 12;
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			double px = cx + radius * (Math.cos(a) * ux + Math.sin(a) * vx);
			double py = cy + radius * (Math.sin(a) * vy);
			double pz = cz + radius * (Math.cos(a) * uz + Math.sin(a) * vz);
			for (int t = 0; t < 2; t++) {
				for (int q = 0; q < 2; q++) {
					p.set((int) Math.floor(px) + t, (int) Math.floor(py) + q, (int) Math.floor(pz), i % 24 < 2 ? vein() : (i + q) % 9 == 0 ? dsTile() : brick());
				}
			}
		}
		crystal(p, rnd, cx, cy, cz, 3);
		for (int i = 0; i < 6; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int rx = cx + (int) Math.round(Math.cos(a) * (radius + 6)), rz = cz + (int) Math.round(Math.sin(a) * (radius + 6));
			int ry = cy - radius + rnd.nextInt(radius * 2);
			int rr = 1 + rnd.nextInt(2);
			for (int dx = -rr; dx <= rr; dx++) {
				for (int dz = -rr; dz <= rr; dz++) {
					for (int dy = -rr; dy <= 0; dy++) {
						if (dx * dx + dz * dz + dy * dy * 2 <= rr * rr + 1) {
							p.set(rx + dx, ry + dy, rz + dz, dy == 0 ? moss() : stone());
						}
					}
				}
			}
		}
	}

	/** An ordinary island: ruins, dead trees, lamps, graves. */
	public static void plain(Plan p, Random rnd, int cx, int top, int cz, int r) {
		int n = 1 + rnd.nextInt(3);
		for (int i = 0; i < n; i++) {
			int x = cx + rnd.nextInt(-r / 2, r / 2 + 1), z = cz + rnd.nextInt(-r / 2, r / 2 + 1);
			switch (rnd.nextInt(5)) {
				case 0, 1 -> arch(p, rnd, x, top + 1, z);
				case 2 -> deadTree(p, rnd, x, top + 1, z);
				case 3 -> lamp(p, rnd, x, top + 1, z);
				default -> crystal(p, rnd, x, top + 8 + rnd.nextInt(6), z, 2);
			}
		}
	}

	// ------------------------------------------------------------------ bridges

	/**
	 * A chain bridge from the edge of one island to the edge of another: a deck of dark planks between stone kerbs,
	 * posts with lanterns, sagging chains for rails and soul lanterns swinging beneath. Some have fallen through.
	 */
	public static void bridge(Plan p, Random rnd, int[] a, int[] b) {
		double dx = b[0] - a[0], dz = b[2] - a[2];
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1) {
			return;
		}
		double nx = dx / len, nz = dz / len;
		double sx = a[0] + nx * (a[3] - 2), sz = a[2] + nz * (a[3] - 2);
		double ex = b[0] - nx * (b[3] - 2), ez = b[2] - nz * (b[3] - 2);
		double run = Math.sqrt((ex - sx) * (ex - sx) + (ez - sz) * (ez - sz));
		int steps = (int) Math.ceil(run);
		if (steps < 2) {
			return;
		}
		Direction.Axis along = Math.abs(nx) > Math.abs(nz) ? Direction.Axis.X : Direction.Axis.Z;
		int gap = rnd.nextInt(8) == 0 ? steps / 2 : -100;
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			double x = sx + (ex - sx) * t, z = sz + (ez - sz) * t;
			int y = (int) Math.round(a[1] + (b[1] - a[1]) * t);
			boolean broken = Math.abs(i - gap) <= 1;
			for (int w = -2; w <= 2; w++) {
				int bx = (int) Math.floor(x - nz * w + 0.5), bz = (int) Math.floor(z + nx * w + 0.5);
				if (!broken && Math.abs(w) <= 1) {
					p.soft(bx, y, bz, w == 0 ? planks() : dsBrick());
				}
				if (Math.abs(w) == 2 && !broken) {
					p.soft(bx, y, bz, dsBrick());
					if (i % 6 == 0) {
						p.soft(bx, y + 1, bz, dsWall());
						p.soft(bx, y + 2, bz, dsWall());
						p.soft(bx, y + 3, bz, lantern());
					} else {
						int sag = i % 6 == 3 ? 1 : 0;
						p.soft(bx, y + 2 - sag, bz, axis(chain(), along));
					}
				}
			}
			if (!broken && i % 9 == 4) {
				int bx = (int) Math.floor(x + 0.5), bz = (int) Math.floor(z + 0.5);
				int l = 1 + rnd.nextInt(3);
				for (int k = 1; k <= l; k++) {
					p.soft(bx, y - k, bz, chain());
				}
				p.soft(bx, y - l - 1, bz, hanging(soulLantern()));
			}
		}
	}

	// ------------------------------------------------------------------ the crown of the Heart

	/** Four obelisks on floating plinths around the Heart, and a great halo turning far above it. */
	public static void crown(Plan p, Random rnd, int hx, int hy, int hz) {
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			int ox = hx + (int) Math.round(Math.cos(a) * 52), oz = hz + (int) Math.round(Math.sin(a) * 52);
			int oy = hy - 4 + rnd.nextInt(8);
			island(p, rnd, ox, oy, oz, 7, true);
			int h = 40 + rnd.nextInt(16);
			for (int k = 1; k <= h; k++) {
				int half = k < h * 0.15 ? 3 : k < h * 0.75 ? 2 : k < h - 3 ? 1 : 0;
				for (int ax = -half; ax <= half; ax++) {
					for (int az = -half; az <= half; az++) {
						boolean seam = (ax == 0 || az == 0) && (Math.abs(ax) == half || Math.abs(az) == half) && k % 2 == 0;
						p.set(ox + ax, oy + k, oz + az, seam ? vein() : k % 9 == 0 ? dsTile() : dsPol());
					}
				}
			}
			p.set(ox, oy + h + 1, oz, endRod());
		}
		// The halo, level, high over the arena, with chains hanging from it
		int radius = 34, y = hy + 56;
		int steps = radius * 10;
		for (int i = 0; i < steps; i++) {
			double an = i * Math.PI * 2 / steps;
			int x = hx + (int) Math.round(Math.cos(an) * radius), z = hz + (int) Math.round(Math.sin(an) * radius);
			for (int t = -1; t <= 1; t++) {
				int x2 = hx + (int) Math.round(Math.cos(an) * (radius + t)), z2 = hz + (int) Math.round(Math.sin(an) * (radius + t));
				p.set(x2, y, z2, i % 20 < 2 ? vein() : brick());
				p.set(x2, y + 1, z2, t == 0 ? (i % 20 < 2 ? vein() : dsTile()) : null);
			}
			if (i % 17 == 0) {
				int len = 6 + rnd.nextInt(14);
				for (int k = 1; k <= len; k++) {
					p.soft(x, y - k, z, chain());
				}
				p.soft(x, y - len - 1, z, hanging(soulLantern()));
			}
		}
	}
}
