package net.battlehub.arch.town;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Plan;
import net.battlehub.arch.Rng;

/**
 * The lobby's heart, an authored module (hand-designed, drawn with exact blocks) placed by the same assembly as
 * the generated architecture: a marble plaza with a compass rose, a tiered fountain under a beacon, a balustrade
 * that carries the leaderboards, four gateways with towers, arches and coloured beacons, and low planters.
 * The lobby characters stand in a ring between the fountain and the planters; players arrive south of the fountain.
 */
public final class Plaza {
	private Plaza() {}

	/** The plaza's radius to its balustrade (27 until 1.30: ten blocks more, so the heart of the island feels as big as the island). */
	public static final int RADIUS = 37;
	public static final int GATE = 3;
	/** Where the long points of the compass rose end (just inside the dark rim). */
	public static final double ARM = RADIUS - 3;
	/** Where the four flower beds stand on the diagonals. */
	public static final int PLANTERS = (int) Math.round(at(21));

	/**
	 * The floor pattern was drawn for a plaza of 27 with the points ending at 24; it grows with the plaza: the
	 * fountain (7) stays, everything between it and the rim moves out in proportion.
	 */
	public static double at(double drawnAt) {
		return 7 + (drawnAt - 7) * (ARM - 7) / 17;
	}

	/** How much wider the points of the rose are than they were drawn (they are longer too). */
	public static double width() {
		return Math.sqrt((ARM - 7) / 17);
	}

	private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final String[] BEAMS = {"red_stained_glass", "yellow_stained_glass", "lime_stained_glass", "light_blue_stained_glass"};
	private static final String[] BANNERS = {"red", "yellow", "lime", "light_blue"};
	private static final String[] MARBLE = {"smooth_quartz", "quartz_bricks", "calcite", "polished_diorite", "diorite", "polished_andesite"};
	private static final String[] TOWER = {"polished_blackstone_bricks", "deepslate_bricks", "deepslate_tiles", "stone_bricks", "quartz_bricks", "smooth_quartz"};

	private static final class Pen {
		final Plan p;
		final long seed;

		Pen(Plan p, long seed) {
			this.p = p;
			this.seed = seed;
		}

		void set(int x, int y, int z, String id) {
			p.force(x, y, z, Cell.literal(id, Cell.FEATURE).flag(Cell.KEEP));
		}

		String grad(String[] g, double t, int x, int y, int z, double fray) {
			double j = Rng.hash(seed, x * 7 + y * 131, y, z * 13 - y * 17);
			double v = Math.max(0, Math.min(1, t + (j - 0.5) * fray));
			return g[(int) Math.min(g.length - 1, Math.floor(v * g.length))];
		}
	}

	static int wx(int i, int along, int lateral) {
		return DIRS[i][0] * along - DIRS[i][1] * lateral;
	}

	static int wz(int i, int along, int lateral) {
		return DIRS[i][1] * along + DIRS[i][0] * lateral;
	}

	static String face(int i) {
		return switch (i) {
			case 0 -> "north";
			case 1 -> "east";
			case 2 -> "south";
			default -> "west";
		};
	}

	static boolean inGateway(int x, int z) {
		return Math.abs(x) <= GATE || Math.abs(z) <= GATE;
	}

	public static void draw(Plan plan, long seed) {
		Pen k = new Pen(plan, seed);
		floor(k);
		fountain(k);
		for (int i = 0; i < 4; i++) gate(k, i);
		planters(k);
	}

	private static void floor(Pen k) {
		int r = RADIUS;
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = Math.hypot(x, z);
				if (d > r + 0.6) continue;
				k.set(x, 0, z, floorBlock(k, x, z, d));
				k.set(x, -1, z, "polished_blackstone_bricks");
				if (d > r - 1.0 && !inGateway(x, z)) {
					double a = Math.toDegrees(Math.atan2(z, x));
					boolean post = Math.abs(((a % 15) + 15) % 15 - 7.5) > 6.5;
					k.set(x, 1, z, post ? "chiseled_quartz_block" : "quartz_bricks");
					k.set(x, 2, z, "smooth_quartz_slab[type=bottom]");
				}
			}
		}
	}

	private static String floorBlock(Pen k, int x, int z, double d) {
		if (d > RADIUS - 2.2) return "polished_blackstone_bricks";
		double a = Math.atan2(z, x);
		double toAxis = angleTo(a, 0, Math.PI / 2);
		double toDiag = angleTo(a, Math.PI / 4, Math.PI / 2);
		double wLong = 2.8 * width() * (1 - (d - 7) / (ARM - 7));
		double wShort = 1.9 * width() * (1 - (d - 7) / (at(17) - 7));
		if (d > 7 && d < ARM && Math.abs(toAxis) * d < wLong) {
			if (d > at(21.5)) return "gold_block";
			return toAxis > 0 ? "polished_blackstone" : "polished_deepslate";
		}
		if (d > 7 && d < at(17) && Math.abs(toDiag) * d < wShort) return toDiag > 0 ? "waxed_cut_copper" : "waxed_exposed_cut_copper";
		if (Math.abs(d - at(19)) < 0.6 && Math.abs(angleTo(a, Math.PI / 8, Math.PI / 4)) * d < 0.6) return "sea_lantern";
		if (Math.abs(d - at(13)) < 0.5) return "polished_blackstone_bricks";
		if (Math.abs(d - at(19)) < 0.5) return "waxed_cut_copper";
		if (Math.abs(d - 7.4) < 0.6) return "polished_blackstone_bricks";
		double t = Math.max(0, (d - 7) / (ARM - 7));
		return k.grad(MARBLE, t, x, 0, z, 0.22);
	}

	static double angleTo(double a, double offset, double step) {
		double v = (a - offset) / step;
		return (v - Math.round(v)) * step;
	}

	private static void fountain(Pen k) {
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double d = Math.hypot(x, z);
				if (d <= 5.6) {
					k.set(x, 0, z, "water");
					boolean light = Math.abs(d - 4.4) < 0.5 && Math.abs(angleTo(Math.atan2(z, x), 0, Math.PI / 4)) * d < 0.5;
					k.set(x, -1, z, light ? "sea_lantern" : ((int) Math.floor(d) % 2 == 0 ? "dark_prismarine" : "prismarine_bricks"));
					k.set(x, -2, z, "prismarine_bricks");
				} else if (d <= 6.6) {
					k.set(x, 1, z, "quartz_bricks");
					k.set(x, 2, z, "smooth_quartz_slab[type=bottom]");
				}
			}
		}
		for (int y = 0; y <= 3; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					if (Math.hypot(x, z) <= 1.5) k.set(x, y, z, y == 3 ? "chiseled_quartz_block" : "quartz_pillar");
				}
			}
		}
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				double d = Math.hypot(x, z);
				if (d <= 3.4) k.set(x, 4, z, "smooth_quartz");
				boolean spout = x == 0 || z == 0;
				if (d > 2.5 && d <= 3.4) {
					k.set(x, 5, z, spout ? "water" : "quartz_bricks");
				} else if (d <= 2.5) {
					k.set(x, 5, z, "water");
				}
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = 5; y <= 7; y++) k.set(x, y, z, y == 7 ? "chiseled_quartz_block" : "quartz_bricks");
				k.set(x, 14, z, "gold_block");
			}
		}
		for (int y = 8; y <= 13; y++) k.set(0, y, 0, "quartz_pillar");
		k.set(0, 15, 0, "beacon");
		for (int[] c : new int[][] {{-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) k.set(c[0], 15, c[1], "lantern");
	}

	private static void gate(Pen k, int i) {
		int h = 15;
		int r = RADIUS;
		for (int side = -1; side <= 1; side += 2) {
			for (int along = r - 1; along <= r + 1; along++) {
				for (int lat = GATE + 1; lat <= GATE + 3; lat++) {
					int l = side * lat;
					int x = wx(i, along, l), z = wz(i, along, l);
					boolean corner = (along == r - 1 || along == r + 1) && (lat == GATE + 1 || lat == GATE + 3);
					for (int y = 0; y <= h; y++) {
						String st = corner && y >= 2 ? "quartz_pillar" : k.grad(TOWER, (double) y / h, x, y, z, 0.2);
						boolean window = !corner && (y == 6 || y == 7 || y == 11 || y == 12) && (lat == GATE + 2 || along == r);
						k.set(x, y, z, window ? "tinted_glass" : st);
					}
					k.set(x, h + 1, z, corner ? "chiseled_quartz_block" : "smooth_quartz_slab[type=bottom]");
					if (corner) k.set(x, h + 2, z, "lantern");
				}
			}
			int cx = wx(i, r, side * (GATE + 2)), cz = wz(i, r, side * (GATE + 2));
			k.set(cx, h + 1, cz, "quartz_pillar");
			k.set(cx, h + 2, cz, "end_rod");
			k.set(wx(i, r - 2, side * (GATE + 2)), 8, wz(i, r - 2, side * (GATE + 2)), BANNERS[i] + "_wall_banner[facing=" + face((i + 2) % 4) + "]");
		}
		for (int along = r - 1; along <= r + 1; along++) {
			for (int l = -GATE; l <= GATE; l++) {
				int foot = (int) Math.ceil(6 + Math.sqrt(Math.max(0, 3.7 * 3.7 - l * l)));
				for (int y = foot; y <= 11; y++) k.set(wx(i, along, l), y, wz(i, along, l), l == 0 && y == foot ? "chiseled_quartz_block" : "quartz_bricks");
			}
		}
		k.set(wx(i, r, 0), 9, wz(i, r, 0), "iron_chain");
		k.set(wx(i, r, 0), 8, wz(i, r, 0), "iron_chain");
		k.set(wx(i, r, 0), 7, wz(i, r, 0), "lantern[hanging=true]");
		for (int along = r - 1; along <= r + 1; along++) {
			for (int l = -1; l <= 1; l++) k.set(wx(i, along, l), 12, wz(i, along, l), "iron_block");
		}
		k.set(wx(i, r, 0), 13, wz(i, r, 0), "beacon");
		k.set(wx(i, r, 0), 14, wz(i, r, 0), BEAMS[i]);
		for (int l : new int[] {-3, -2, 2, 3}) {
			k.set(wx(i, r - 1, l), 12, wz(i, r - 1, l), "smooth_quartz_slab[type=bottom]");
			k.set(wx(i, r + 1, l), 12, wz(i, r + 1, l), "smooth_quartz_slab[type=bottom]");
		}
		for (int side = -1; side <= 1; side += 2) {
			int x = wx(i, r - 4, side * (GATE + 1)), z = wz(i, r - 4, side * (GATE + 1));
			for (int y = 1; y <= 3; y++) k.set(x, y, z, "polished_blackstone_wall");
			k.set(x, 4, z, "lantern");
		}
	}

	private static void planters(Pen k) {
		for (int q = 0; q < 4; q++) {
			double a = Math.PI / 4 + q * Math.PI / 2;
			int cx = (int) Math.round(Math.cos(a) * PLANTERS), cz = (int) Math.round(Math.sin(a) * PLANTERS);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					double d = Math.hypot(x, z);
					if (d > 2.6) continue;
					if (d > 1.7) {
						k.set(cx + x, 1, cz + z, "polished_blackstone_brick_slab[type=bottom]");
					} else {
						k.set(cx + x, 0, cz + z, "moss_block");
						k.set(cx + x, 1, cz + z, (x + z) % 2 == 0 ? "flowering_azalea" : "azalea");
					}
				}
			}
			k.set(cx, 1, cz, "pink_petals");
		}
	}
}
