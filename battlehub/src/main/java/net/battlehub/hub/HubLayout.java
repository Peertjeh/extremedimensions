package net.battlehub.hub;

import net.battlehub.arch.town.Plaza;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The lobby BattleHub builds in its own world, in the style of a big server hub: a marble plaza with a compass
 * rose and a tiered fountain under a beacon beam, a balustrade with four gateways (towers, an arch and a coloured
 * beacon each), and bridges to four grand buildings: a colosseum (north), a castle (east), a temple of champions
 * (south, behind the spawn) and an arcane tower (west), with floating islands round it all. Walls, floors, roofs
 * and undersides fade through colour gradients.
 * Floor at y=0, centre at 0,0; players spawn south of the fountain, looking at it.
 */
final class HubLayout {
	private HubLayout() {}

	/** The plaza: the leaderboards hang above its balustrade. */
	static final int RADIUS = Plaza.RADIUS;
	static final double SPAWN_X = 0.5;
	static final double SPAWN_Y = 1;
	static final double SPAWN_Z = 9.5;
	static final float SPAWN_YAW = 180;
	/** Half the width of a gateway. */
	static final int GATE = 3;
	/** Centre of the four district islands, and their radius. */
	static final int DISTRICT = 74;
	static final int ISLAND = 20;

	/** North, east, south, west as (dx, dz); the lateral axis of a direction is (-dz, dx). */
	private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final String[] BEAMS = {"red_stained_glass", "yellow_stained_glass", "lime_stained_glass", "light_blue_stained_glass"};
	private static final String[] BANNERS = {"red", "yellow", "lime", "light_blue"};

	private static final String[] MARBLE = {"smooth_quartz", "quartz_bricks", "calcite", "polished_diorite", "diorite", "polished_andesite"};
	private static final String[] UNDER = {"polished_blackstone_bricks", "stone_bricks", "andesite", "tuff", "deepslate", "cobbled_deepslate", "blackstone"};
	private static final String[] TOWER = {"polished_blackstone_bricks", "deepslate_bricks", "deepslate_tiles", "stone_bricks", "quartz_bricks", "smooth_quartz"};

	static Blueprint design(long seed) {
		Blueprint bp = new Blueprint();
		HubKit k = new HubKit(bp, seed);
		RandomSource rnd = RandomSource.create(seed);
		plaza(k);
		fountain(k);
		for (int i = 0; i < 4; i++) {
			gate(k, i);
			bridge(k, i);
		}
		planters(k);
		colosseum(k, 0, -DISTRICT);
		castle(k, DISTRICT, 0);
		temple(k, 0, DISTRICT);
		tower(k, -DISTRICT, 0);
		skyIslands(k, rnd);
		bp.spawn(SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_YAW);
		bp.spectator(SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_YAW, 0);
		bp.radius(110);
		bp.voidY(-60);
		return bp;
	}

	/** World x of (along, lateral) in direction i. */
	private static int wx(int i, int along, int lateral) {
		return DIRS[i][0] * along - DIRS[i][1] * lateral;
	}

	private static int wz(int i, int along, int lateral) {
		return DIRS[i][1] * along + DIRS[i][0] * lateral;
	}

	private static String face(int i) {
		return switch (i) {
			case 0 -> "north";
			case 1 -> "east";
			case 2 -> "south";
			default -> "west";
		};
	}

	private static String back(int i) {
		return face((i + 2) % 4);
	}

	/** In a gateway opening (the plaza's four axes)? */
	private static boolean inGateway(int x, int z) {
		return Math.abs(x) <= GATE || Math.abs(z) <= GATE;
	}

	// ------------------------------------------------------------------ the plaza

	private static void plaza(HubKit k) {
		int r = RADIUS;
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = HubKit.dist(x, z);
				if (d > r + 0.6) continue;
				k.set(x, 0, z, floor(k, x, z, d));
				// Balustrade: dark foot, marble rail, a slab on top (the leaderboards hang just above it)
				if (d > r - 1.0 && !inGateway(x, z)) {
					double a = Math.toDegrees(Math.atan2(z, x));
					boolean post = Math.abs(((a % 15) + 15) % 15 - 7.5) > 6.5;
					k.set(x, 1, z, post ? "chiseled_quartz_block" : "quartz_bricks");
					k.set(x, 2, z, "smooth_quartz_slab");
				}
			}
		}
		k.underside(0, 0, 0, r + 0.5, 26, UNDER);
		// Glowing roots under the edge of the plaza
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24 + 0.13;
			int x = (int) Math.round(Math.cos(a) * (r - 3));
			int z = (int) Math.round(Math.sin(a) * (r - 3));
			int bottom = lowest(k, x, z);
			if (bottom < 0) {
				k.set(x, bottom - 1, z, i % 3 == 0 ? "shroomlight" : "hanging_roots");
			}
		}
	}

	/** The lowest block of the column under the plaza (0 when there is none). */
	private static int lowest(HubKit k, int x, int z) {
		int y = 0;
		while (k.bp.has(x, y - 1, z)) y--;
		return y;
	}

	/** Marble fading outwards, a compass rose, two rings and lights in the floor. */
	private static BlockState floor(HubKit k, int x, int z, double d) {
		if (d > RADIUS - 2.2) {
			return Build.s("polished_blackstone_bricks");
		}
		double a = Math.atan2(z, x);
		// Compass rose: four long points on the axes, four short ones on the diagonals
		double toAxis = angleTo(a, 0, Math.PI / 2);
		double toDiag = angleTo(a, Math.PI / 4, Math.PI / 2);
		double wLong = 2.8 * Plaza.width() * (1 - (d - 7) / (Plaza.ARM - 7));
		double wShort = 1.9 * Plaza.width() * (1 - (d - 7) / (Plaza.at(17) - 7));
		if (d > 7 && d < Plaza.ARM && Math.abs(toAxis) * d < wLong) {
			if (d > Plaza.at(21.5)) return Build.s("gold_block");
			return Build.s(toAxis > 0 ? "polished_blackstone" : "polished_deepslate");
		}
		if (d > 7 && d < Plaza.at(17) && Math.abs(toDiag) * d < wShort) {
			return Build.s(toDiag > 0 ? "waxed_cut_copper" : "waxed_exposed_cut_copper");
		}
		// Lights between the points, in the outer ring
		if (Math.abs(d - Plaza.at(19)) < 0.6 && Math.abs(angleTo(a, Math.PI / 8, Math.PI / 4)) * d < 0.6) {
			return Build.s("sea_lantern");
		}
		if (Math.abs(d - Plaza.at(13)) < 0.5) return Build.s("polished_blackstone_bricks");
		if (Math.abs(d - Plaza.at(19)) < 0.5) return Build.s("waxed_cut_copper");
		if (Math.abs(d - 7.4) < 0.6) return Build.s("polished_blackstone_bricks");
		double t = Math.max(0, (d - 7) / (Plaza.ARM - 7));
		return k.grad(MARBLE, t, x, 0, z, 0.22);
	}

	/** Signed angle from {@code a} to the nearest of the angles offset + n·step. */
	private static double angleTo(double a, double offset, double step) {
		double v = (a - offset) / step;
		return (v - Math.round(v)) * step;
	}

	// ------------------------------------------------------------------ the fountain

	private static void fountain(HubKit k) {
		// Basin: water over a prismarine mosaic with lights, held in by a marble rim you can sit on
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double d = HubKit.dist(x, z);
				if (d <= 5.6) {
					k.set(x, 0, z, "water");
					boolean light = Math.abs(d - 4.4) < 0.5 && Math.abs(angleTo(Math.atan2(z, x), 0, Math.PI / 4)) * d < 0.5;
					k.set(x, -1, z, light ? "sea_lantern" : ((int) Math.floor(d) % 2 == 0 ? "dark_prismarine" : "prismarine_bricks"));
					k.set(x, -2, z, "prismarine_bricks");
				} else if (d <= 6.6) {
					k.set(x, 1, z, "quartz_bricks");
					k.set(x, 2, z, "smooth_quartz_slab");
				}
			}
		}
		// The pedestal and the upper bowl, with four waterfalls into the basin
		k.column(0, 0, 3, 0, 1.5, (x, y, z) -> Build.s(y == 3 ? "chiseled_quartz_block" : "quartz_pillar"));
		k.disc(0, 4, 0, 3.4, (x, y, z) -> Build.s("smooth_quartz"));
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				double d = HubKit.dist(x, z);
				boolean spout = Math.abs(x) == 0 || Math.abs(z) == 0;
				if (d > 2.5 && d <= 3.4) {
					k.set(x, 5, z, spout ? "water" : "quartz_bricks");
				} else if (d <= 2.5) {
					k.set(x, 5, z, "water");
				}
			}
		}
		// The spire, gold on top, with a beacon shining straight up
		k.box(-1, 5, -1, 1, 7, 1, (x, y, z) -> Build.s(y == 7 ? "chiseled_quartz_block" : "quartz_bricks"));
		for (int y = 8; y <= 13; y++) {
			k.set(0, y, 0, "quartz_pillar");
		}
		k.box(-1, 14, -1, 1, 14, 1, (x, y, z) -> Build.s("gold_block"));
		k.set(0, 15, 0, "beacon");
		for (int[] c : new int[][] {{-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
			k.set(c[0], 15, c[1], "lantern");
		}
	}

	// ------------------------------------------------------------------ gateways and bridges

	private static void gate(HubKit k, int i) {
		int h = 15;
		for (int side = -1; side <= 1; side += 2) {
			for (int along = RADIUS - 1; along <= RADIUS + 1; along++) {
				for (int lat = GATE + 1; lat <= GATE + 3; lat++) {
					int l = side * lat;
					int x = wx(i, along, l);
					int z = wz(i, along, l);
					boolean corner = (along == RADIUS - 1 || along == RADIUS + 1) && (lat == GATE + 1 || lat == GATE + 3);
					for (int y = 0; y <= h; y++) {
						BlockState st = corner && y >= 2 ? Build.s("quartz_pillar") : k.grad(TOWER, (double) y / h, x, y, z, 0.2);
						boolean window = !corner && (y == 6 || y == 7 || y == 11 || y == 12) && (lat == GATE + 2 || along == RADIUS);
						k.set(x, y, z, window ? Build.s("tinted_glass") : st);
					}
					k.set(x, h + 1, z, corner ? "chiseled_quartz_block" : "smooth_quartz_slab");
					if (corner) k.set(x, h + 2, z, "lantern");
				}
			}
			int cx = wx(i, RADIUS, side * (GATE + 2));
			int cz = wz(i, RADIUS, side * (GATE + 2));
			k.set(cx, h + 1, cz, "quartz_pillar");
			k.set(cx, h + 2, cz, "end_rod");
			// A banner on the face towards the plaza
			k.set(wx(i, RADIUS - 2, side * (GATE + 2)), 8, wz(i, RADIUS - 2, side * (GATE + 2)), BANNERS[i] + "_wall_banner[facing=" + back(i) + "]");
		}
		// The arch over the opening, with a keystone and a lantern hanging in it
		for (int along = RADIUS - 1; along <= RADIUS + 1; along++) {
			for (int l = -GATE; l <= GATE; l++) {
				int foot = (int) Math.ceil(6 + Math.sqrt(Math.max(0, 3.7 * 3.7 - l * l)));
				for (int y = foot; y <= 11; y++) {
					k.set(wx(i, along, l), y, wz(i, along, l), l == 0 && y == foot ? "chiseled_quartz_block" : "quartz_bricks");
				}
			}
		}
		k.hangingLantern(wx(i, RADIUS, 0), 9, wz(i, RADIUS, 0), 2);
		// A coloured beacon on top of the arch
		for (int along = RADIUS - 1; along <= RADIUS + 1; along++) {
			for (int l = -1; l <= 1; l++) {
				k.set(wx(i, along, l), 12, wz(i, along, l), "iron_block");
			}
		}
		k.set(wx(i, RADIUS, 0), 13, wz(i, RADIUS, 0), "beacon");
		k.set(wx(i, RADIUS, 0), 14, wz(i, RADIUS, 0), BEAMS[i]);
		for (int l : new int[] {-3, -2, 2, 3}) {
			k.set(wx(i, RADIUS - 1, l), 12, wz(i, RADIUS - 1, l), "smooth_quartz_slab");
			k.set(wx(i, RADIUS + 1, l), 12, wz(i, RADIUS + 1, l), "smooth_quartz_slab");
		}
		// Lamp posts on both sides of the way in
		for (int side = -1; side <= 1; side += 2) {
			k.lampPost(wx(i, RADIUS - 4, side * (GATE + 1)), 1, wz(i, RADIUS - 4, side * (GATE + 1)), 3, "polished_blackstone_wall");
		}
	}

	private static void bridge(HubKit k, int i) {
		int end = DISTRICT - ISLAND + 2;
		for (int along = RADIUS + 1; along <= end; along++) {
			for (int l = -GATE; l <= GATE; l++) {
				int x = wx(i, along, l);
				int z = wz(i, along, l);
				int al = Math.abs(l);
				k.set(x, 0, z, al <= 1 ? "smooth_quartz" : al == 2 ? "polished_andesite" : "polished_blackstone_bricks");
				k.set(x, -1, z, "polished_blackstone_bricks");
				if (al == GATE) {
					if (along % 4 == 0) {
						k.set(x, 1, z, "chiseled_quartz_block");
						if (along % 8 == 0) k.set(x, 2, z, "lantern");
					} else {
						k.set(x, 1, z, "smooth_quartz_slab");
					}
				}
			}
			// Arches under the deck every eight blocks
			if (along % 8 == 4) {
				for (int d = 1; d <= 6; d++) {
					int w = GATE - d / 2;
					for (int l = -w; l <= w; l++) {
						k.set(wx(i, along, l), -1 - d, wz(i, along, l), d == 6 ? "chiseled_deepslate" : "deepslate_bricks");
					}
				}
			}
			if (along % 8 == 0) {
				for (int side = -1; side <= 1; side += 2) {
					k.hangingLantern(wx(i, along, side * GATE), -2, wz(i, along, side * GATE), 1);
				}
			}
		}
	}

	/** Low flower beds on the diagonals (low, so they never hide the leaderboards). */
	private static void planters(HubKit k) {
		for (int q = 0; q < 4; q++) {
			double a = Math.PI / 4 + q * Math.PI / 2;
			int cx = (int) Math.round(Math.cos(a) * Plaza.PLANTERS);
			int cz = (int) Math.round(Math.sin(a) * Plaza.PLANTERS);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					double d = HubKit.dist(x, z);
					if (d > 2.6) continue;
					if (d > 1.7) {
						k.set(cx + x, 1, cz + z, "polished_blackstone_brick_slab");
					} else {
						k.set(cx + x, 0, cz + z, "moss_block");
						k.set(cx + x, 1, cz + z, (x + z) % 2 == 0 ? "flowering_azalea" : "azalea");
					}
				}
			}
			k.set(cx, 1, cz, "pink_petals");
		}
	}

	// ------------------------------------------------------------------ the district islands

	/** A round island for a district: its floor through {@code shader}, its underside fading down. */
	private static void island(HubKit k, int cx, int cz, String[] top) {
		k.disc(cx, 0, cz, ISLAND, (x, y, z) -> k.grad(top, Math.min(1, HubKit.dist(x - cx, z - cz) / ISLAND), x, 0, z, 0.3));
		k.underside(cx, 0, cz, ISLAND + 0.5, 20, UNDER);
	}

	/** North: a colosseum, the home of the duels. */
	private static void colosseum(HubKit k, int cx, int cz) {
		island(k, cx, cz, new String[] {"smooth_sandstone", "sandstone", "cut_sandstone", "smooth_sandstone", "sandstone"});
		String[] wall = {"sandstone", "cut_sandstone", "smooth_sandstone", "smooth_sandstone", "chiseled_sandstone"};
		int h = 12;
		for (int x = -15; x <= 15; x++) {
			for (int z = -15; z <= 15; z++) {
				double d = HubKit.dist(x, z);
				double deg = Math.toDegrees(Math.atan2(z, x));
				double bay = ((deg % 15) + 15) % 15 - 7.5;
				boolean entrance = z > 0 && Math.abs(x) <= 2;
				int wx = cx + x;
				int wz = cz + z;
				if (d >= 13.5 && d <= 14.6) {
					// Outer wall: two rows of arches
					for (int y = 1; y <= h; y++) {
						boolean arch = (y >= 1 && y <= 3 || y >= 6 && y <= 8) && Math.abs(bay) <= 4.6
								|| (y == 4 || y == 9) && Math.abs(bay) <= 2;
						if (entrance && y <= 5) continue;
						if (arch) continue;
						String band = y == 5 || y == 10 ? "cut_sandstone" : null;
						k.set(wx, y, wz, band != null ? Build.s(band) : k.grad(wall, (double) y / h, wx, y, wz, 0.2));
					}
					if (Math.abs(bay) > 4) {
						k.set(wx, h + 1, wz, "chiseled_sandstone");
						if (Math.abs(bay) > 6.5) k.set(wx, h + 2, wz, "lantern");
					}
				} else if (d >= 8.5 && d < 13.5 && !entrance) {
					// Seats rising outwards
					int seat = 2 + (int) Math.floor(d - 8.5);
					for (int y = 1; y < seat; y++) {
						k.set(wx, y, wz, "smooth_sandstone");
					}
					k.set(wx, seat, wz, HubKit.stair("sandstone_stairs", HubKit.facing(x, z), false));
				} else if (d >= 7.5 && d < 8.5 && !entrance) {
					k.set(wx, 1, wz, "smooth_sandstone");
					k.set(wx, 2, wz, "smooth_sandstone_slab");
				} else if (d < 7.5) {
					// The sand of the arena with a red mosaic
					String f = Math.abs(d - 3) < 0.5 || Math.abs(d - 6) < 0.5 ? "cut_red_sandstone" : (d < 1 ? "chiseled_red_sandstone" : "smooth_sandstone");
					k.set(wx, 0, wz, f);
				}
			}
		}
		// A golden trophy in the middle
		k.box(cx - 1, 1, cz - 1, cx + 1, 1, cz + 1, (x, y, z) -> Build.s("polished_blackstone_bricks"));
		k.set(cx, 2, cz, "chiseled_polished_blackstone");
		k.set(cx, 3, cz, "gold_block");
		for (int[] c : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			k.set(cx + c[0], 4, cz + c[1], "gold_block");
		}
		k.set(cx, 4, cz, "raw_gold_block");
	}

	/** East: a castle with a keep, four round towers and a gatehouse towards the plaza. */
	private static void castle(HubKit k, int cx, int cz) {
		island(k, cx, cz, new String[] {"moss_block", "grass_block", "grass_block", "coarse_dirt", "grass_block"});
		String[] stone = {"mossy_cobblestone", "cobblestone", "stone_bricks", "stone_bricks", "polished_andesite"};
		int w = 12;
		int h = 7;
		// Curtain wall
		for (int x = -w; x <= w; x++) {
			for (int z = -w; z <= w; z++) {
				int m = Math.max(Math.abs(x), Math.abs(z));
				if (m < w - 1) continue;
				boolean gate = x < 0 && Math.abs(z) <= 2;
				for (int y = 1; y <= h; y++) {
					if (gate && (y <= 4 || y == 5 && Math.abs(z) <= 1)) continue;
					k.set(cx + x, y, cz + z, k.grad(stone, (double) y / h, cx + x, y, cz + z, 0.25));
				}
				if (m == w && (x + z) % 2 == 0 && !gate) {
					k.set(cx + x, h + 1, cz + z, "stone_bricks");
				}
			}
		}
		// Portcullis, raised
		for (int z = -2; z <= 2; z++) {
			k.set(cx - w, 4, cz + z, "iron_bars");
		}
		// Gatehouse towers and the four corner towers with pointed roofs
		for (int side = -1; side <= 1; side += 2) {
			k.box(cx - w - 2, 1, cz + side * 4 - 1, cx - w, 10, cz + side * 4 + 1, (x, y, z) -> k.grad(stone, y / 10.0, x, y, z, 0.25));
			k.set(cx - w - 1, 11, cz + side * 4, "lantern");
		}
		for (int[] c : new int[][] {{-w, -w}, {w, w}, {-w, w}, {w, -w}}) {
			int tx = cx + c[0];
			int tz = cz + c[1];
			k.column(tx, 1, 13, tz, 3.2, (x, y, z) -> k.grad(stone, y / 13.0, x, y, z, 0.25));
			k.cone(tx, 14, tz, 4.0, 8, (x, y, z) -> k.grad(new String[] {"deepslate_tiles", "deepslate_tiles", "polished_deepslate", "polished_blackstone"},
					(y - 14) / 8.0, x, y, z, 0.15));
			k.set(tx, 22, tz, "lightning_rod");
		}
		// The keep: hollow, with windows, a door towards the gate and a flag on top
		int kw = 5;
		int kh = 18;
		k.walls(cx - kw, 1, cz - kw, cx + kw, kh, cz + kw, (x, y, z) -> {
			boolean window = (y % 5 == 4 || y % 5 == 0) && y > 2 && (Math.abs(x - cx) == 1 || Math.abs(z - cz) == 1)
					&& (Math.abs(x - cx) == kw || Math.abs(z - cz) == kw);
			boolean corner = Math.abs(x - cx) == kw && Math.abs(z - cz) == kw;
			if (window) return Build.s("glass");
			return corner ? Build.s("polished_andesite") : k.grad(stone, (double) y / kh, x, y, z, 0.25);
		});
		k.clear(cx - kw, 1, cz - 1, cx - kw, 3, cz + 1);
		for (int x = -kw; x <= kw; x++) {
			for (int z = -kw; z <= kw; z++) {
				k.set(cx + x, kh, cz + z, "stone_bricks");
				if ((Math.abs(x) == kw || Math.abs(z) == kw) && (x + z) % 2 == 0) k.set(cx + x, kh + 1, cz + z, "stone_bricks");
			}
		}
		k.set(cx, 1, cz, "lantern");
		for (int y = kh + 1; y <= kh + 6; y++) {
			k.set(cx, y, cz, "oak_fence");
		}
		for (int y = kh + 4; y <= kh + 6; y++) {
			for (int z = 1; z <= 3; z++) k.set(cx, y, cz + z, "red_wool");
		}
		// A path from the gate to the keep
		for (int x = -w; x < -kw; x++) {
			for (int z = -1; z <= 1; z++) k.set(cx + x, 0, cz + z, "dirt_path");
		}
	}

	/** South (behind the spawn): a temple of champions with a podium inside. */
	private static void temple(HubKit k, int cx, int cz) {
		island(k, cx, cz, new String[] {"calcite", "smooth_quartz", "polished_diorite", "calcite", "grass_block"});
		int hx = 9;
		int hz = 13;
		// Three steps up to the floor
		for (int step = 0; step < 3; step++) {
			int ex = hx + 2 - step;
			int ez = hz + 2 - step;
			k.box(cx - ex, step + 1, cz - ez, cx + ex, step + 1, cz + ez, (x, y, z) -> Build.s(y == 3 ? "smooth_quartz" : "quartz_bricks"));
		}
		int floor = 3;
		// Columns all round, with a wider gap at the front
		int[] frontX = {-8, -5, -2, 2, 5, 8};
		for (int x : frontX) {
			column(k, cx + x, floor + 1, cz - hz + 1);
			column(k, cx + x, floor + 1, cz + hz - 1);
		}
		for (int z = -hz + 4; z <= hz - 4; z += 3) {
			column(k, cx - hx + 1, floor + 1, cz + z);
			column(k, cx + hx - 1, floor + 1, cz + z);
		}
		// Entablature with a frieze
		k.box(cx - hx, 13, cz - hz, cx + hx, 13, cz + hz, (x, y, z) -> Build.s("smooth_quartz"));
		k.walls(cx - hx, 14, cz - hz, cx + hx, 14, cz + hz, (x, y, z) -> Build.s((x + z) % 3 == 0 ? "chiseled_quartz_block" : "quartz_bricks"));
		// A gable roof along the temple, with pediments at both ends
		for (int i = 0; i <= hx; i++) {
			int y = 15 + i;
			int half = hx - i;
			for (int z = -hz; z <= hz; z++) {
				if (half == 0) {
					k.set(cx, y, cz + z, "smooth_quartz_slab");
				} else {
					k.set(cx - half, y, cz + z, HubKit.stair("quartz_stairs", "east", false));
					k.set(cx + half, y, cz + z, HubKit.stair("quartz_stairs", "west", false));
				}
			}
			for (int x = -half + 1; x <= half - 1; x++) {
				k.set(cx + x, y, cz - hz, "smooth_quartz");
				k.set(cx + x, y, cz + hz, "smooth_quartz");
			}
		}
		k.set(cx, 17, cz - hz, "gold_block");
		k.set(cx, 17, cz + hz, "gold_block");
		k.set(cx, 25, cz - hz, "gold_block");
		k.set(cx, 25, cz + hz, "gold_block");
		// The cella and the champions' podium
		k.walls(cx - 5, floor + 1, cz - 8, cx + 5, 12, cz + 10, (x, y, z) -> k.grad(new String[] {"polished_diorite", "calcite", "smooth_quartz"},
				(y - floor - 1) / 8.0, x, y, z, 0.2));
		k.clear(cx - 1, floor + 1, cz - 8, cx + 1, floor + 4, cz - 8);
		for (int y = floor + 1; y <= floor + 3; y++) k.set(cx, y, cz + 7, "gold_block");
		for (int y = floor + 1; y <= floor + 2; y++) k.set(cx - 2, y, cz + 7, "iron_block");
		k.set(cx + 2, floor + 1, cz + 7, "waxed_copper_block");
		k.hangingLantern(cx, 12, cz + 1, 2);
		// Fires on the front corners
		for (int side = -1; side <= 1; side += 2) {
			k.set(cx + side * (hx + 2), 2, cz - hz - 2, "chiseled_quartz_block");
			k.set(cx + side * (hx + 2), 3, cz - hz - 2, "campfire");
		}
	}

	private static void column(HubKit k, int x, int y0, int z) {
		k.set(x, y0, z, "chiseled_quartz_block");
		for (int y = y0 + 1; y <= 11; y++) k.set(x, y, z, "quartz_pillar");
		k.set(x, 12, z, "chiseled_quartz_block");
	}

	/** West: an arcane tower with a copper spire, amethyst bands and crystals floating round it. */
	private static void tower(HubKit k, int cx, int cz) {
		island(k, cx, cz, new String[] {"polished_deepslate", "end_stone_bricks", "end_stone", "end_stone_bricks", "purpur_block"});
		k.ring(cx, 0, cz, 8.5, 9.5, (x, y, z) -> Build.s("amethyst_block"));
		String[] body = {"polished_deepslate", "deepslate_bricks", "tuff_bricks", "polished_tuff", "calcite", "calcite"};
		int h = 34;
		for (int y = 1; y <= h; y++) {
			int yy = y;
			k.ring(cx, y, cz, 5.5, 6.6, (x, y2, z) -> {
				double deg = Math.toDegrees(Math.atan2(z - cz, x - cx));
				double spiral = ((deg - yy * 24) % 360 + 360) % 360;
				boolean window = yy % 4 != 0 && (spiral < 7 || spiral > 353);
				if (window) return Build.s("glass");
				if (yy % 9 == 0) return Build.s("amethyst_block");
				return k.grad(body, (double) yy / h, x, yy, z, 0.2);
			});
		}
		// A door towards the plaza
		k.clear(cx + 6, 1, cz - 1, cx + 7, 4, cz + 1);
		// A balcony and the copper spire
		k.ring(cx, 24, cz, 6.6, 8.2, (x, y, z) -> Build.s("polished_deepslate_slab[type=top]"));
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			k.set(cx + (int) Math.round(Math.cos(a) * 8), 25, cz + (int) Math.round(Math.sin(a) * 8), "lantern");
		}
		k.disc(cx, h + 1, cz, 6.6, (x, y, z) -> Build.s("polished_deepslate"));
		k.cone(cx, h + 2, cz, 8.0, 16, (x, y, z) -> k.grad(new String[] {"waxed_oxidized_copper", "waxed_weathered_copper", "waxed_exposed_copper", "waxed_copper_block"},
				(y - h - 2) / 16.0, x, y, z, 0.2));
		for (int y = h + 18; y <= h + 20; y++) k.set(cx, y, cz, "end_rod");
		// Crystals floating round the tower
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + 0.4;
			int x = cx + (int) Math.round(Math.cos(a) * 12);
			int z = cz + (int) Math.round(Math.sin(a) * 12);
			int y = 10 + (i * 5) % 22;
			k.set(x, y, z, "amethyst_block");
			k.set(x, y + 1, z, "amethyst_cluster");
			k.set(x, y - 1, z, "amethyst_cluster[facing=down]");
		}
	}

	/** Small floating islands with trees between the districts. */
	private static void skyIslands(HubKit k, RandomSource rnd) {
		String[][] trees = {{"cherry_log", "cherry_leaves"}, {"oak_log", "azalea_leaves"}, {"birch_log", "flowering_azalea_leaves"}, {"spruce_log", "spruce_leaves"}};
		for (int q = 0; q < 4; q++) {
			for (int j = 0; j < 3; j++) {
				double a = Math.PI / 4 + q * Math.PI / 2 + (j - 1) * 0.32;
				double dist = 44 + j * 7 + rnd.nextInt(5);
				int x = (int) Math.round(Math.cos(a) * dist);
				int z = (int) Math.round(Math.sin(a) * dist);
				int y = -6 + rnd.nextInt(20);
				double r = 3 + rnd.nextDouble() * 2.5;
				k.disc(x, y, z, r, (px, py, pz) -> k.mix(px, py, pz, "grass_block", 0.25, "moss_block", "coarse_dirt"));
				k.underside(x, y, z, r + 0.4, 5 + rnd.nextInt(4), new String[] {"dirt", "coarse_dirt", "stone", "andesite", "tuff"});
				String[] t = trees[(q + j) % trees.length];
				k.tree(x, y + 1, z, 3 + rnd.nextInt(2), 2.2 + rnd.nextDouble(), t[0], t[1]);
				k.set(x + 1, y + 1, z + 1, "lantern");
			}
		}
	}
}
