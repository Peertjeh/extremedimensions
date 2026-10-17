package com.extremedimensions.seam;

import static com.extremedimensions.seam.SeamArchitect.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Seam's colossal buildings, assembled from parts so that no two are alike.
 *
 * <p>A building is laid out to one of five plans (a cathedral with its crossing tower, nave, transepts and apse; a
 * citadel with a keep inside a towered curtain wall; a cluster of towers round a great spire, joined by sky bridges;
 * a stepped ziggurat with a temple on top; or a gateway of twin towers spanned by an arch), each part sized to the
 * island it stands on. Its towers are stacks of tiers, every tier drawn from a kit: a footprint (square, octagonal or
 * round), a body (lancet windows between pilasters, an open arcade, a colonnade, banded masonry with oculi and blind
 * arches, an open belfry, or a lantern of glass between mullions), a ledge (corbels and a balustrade, or battlements)
 * and, on square tiers, corners (stepped buttresses with pinnacles, or corbelled turrets). A tower is crowned with a
 * spire, a ribbed dome with a lantern, a crown of pinnacles under a floating halo, a terrace with a hovering crystal,
 * or, in a ruin, broken walls open to the sky. And it is built in one of four schemes of material: the full ladder
 * of the hush stone from a blackstone foot to a pale crown, pale bone-white walls under dark roofs, black gloam lit by
 * silk, or the ladder worn to moss and cracks.
 */
public final class Colossus {
	private Colossus() {}

	// ------------------------------------------------------------------ footprints

	/** The plan of a tier. */
	enum Form { SQUARE, OCTAGON, ROUND }

	/** Whether a column lies within a footprint of half-size h. */
	static boolean inside(Form form, int x, int z, double h) {
		return switch (form) {
			case SQUARE -> Math.abs(x) <= h && Math.abs(z) <= h;
			case OCTAGON -> Math.abs(x) <= h && Math.abs(z) <= h && Math.abs(x) + Math.abs(z) <= h * 1.42;
			case ROUND -> x * x + z * z <= (h + 0.45) * (h + 0.45);
		};
	}

	/** Whether a column is on the edge of a footprint: inside it, with a side open to the outside. */
	static boolean rim(Form form, int x, int z, double h) {
		return inside(form, x, z, h)
				&& (!inside(form, x + 1, z, h) || !inside(form, x - 1, z, h) || !inside(form, x, z + 1, h) || !inside(form, x, z - 1, h));
	}

	/** Which way is out from a column of a footprint. */
	static Direction out(int x, int z) {
		if (Math.abs(x) >= Math.abs(z)) {
			return x >= 0 ? Direction.EAST : Direction.WEST;
		}
		return z >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * Where a column of a tier's edge lies against its windows: 0 at a window's centre, -1 or 1 beside it (the window
	 * is three wide), and anything else between them. On a square, windows stand every {@code spacing} along each face,
	 * clear of the corners; on an octagon or a round tower, at even steps around it (a multiple of four, so it stays
	 * symmetric).
	 */
	static int bay(Form form, int x, int z, int h, int spacing) {
		if (form == Form.SQUARE) {
			boolean ew = Math.abs(x) >= Math.abs(z);
			int u = ew ? z : x;
			if (Math.abs(x) == Math.abs(z) || Math.abs(u) > h - 2) {
				return 9;
			}
			int c = Math.round((float) u / spacing) * spacing;
			int d = u - c;
			if (Math.abs(c) > h - 2 || Math.abs(d) > 1) {
				return 9;
			}
			// the same hand as on a round tower: positive is clockwise round the tower, seen from above
			int hand = ew ? (x > 0 ? 1 : -1) : (z > 0 ? -1 : 1);
			return d * hand;
		}
		int n = Math.max(4, (int) Math.round(2 * Math.PI * h / spacing / 4.0) * 4);
		double a = Math.atan2(z, x), step = 2 * Math.PI / n;
		double d = (a - Math.round(a / step) * step) * Math.max(1, h);
		if (Math.abs(d) <= 0.5) {
			return 0;
		}
		return Math.abs(d) <= 1.5 ? (d > 0 ? 1 : -1) : 9;
	}

	/** The direction a step of a window's arch faces: away from the window's centre, along the face. */
	static Direction aside(Direction out, int side) {
		Direction cw = out.getClockWise();
		return side > 0 ? cw : cw.getOpposite();
	}

	// ------------------------------------------------------------------ schemes of material

	/**
	 * The materials of one building: its masonry from foot to crown, trim, roof, ledges, floors, glass and accent, and
	 * the plan ({@link MaterialContext}) of where each block of the world falls on its ladders.
	 */
	static final class Scheme {
		BlockState[] wall, trim, roof, glass;
		BlockState roofFill, corbel, cap, rail, floor, floorAlt, accent, band, pier;
		/** Stairs of the dark course round the foot of every wall (the plinth's water table). */
		BlockState plinth;
		double wear;
		boolean ruin;
		MaterialContext ctx;

		/**
		 * Masonry for the block at ({@code x}, {@code y}, {@code z}) of frame {@code f}: its rung of the wall's ladder
		 * is chosen by where the block stands in the world, so every tower, hall and wall of the building shades alike
		 * at the same height and they meet without a seam; {@code bias} makes a course paler (+) or darker (-).
		 */
		BlockState masonryAt(Frame f, int x, int y, int z, double bias) {
			return ctx.masonry(wall, f.wx(x, z), f.oy + y, f.wz(x, z), bias);
		}

		/** Trim for a block of a frame, on the trim's ladder as masonry is on the wall's (never worn). */
		BlockState trimAt(Frame f, int x, int y, int z, double bias) {
			return ctx.pick(trim, f.wx(x, z), f.oy + y, f.wz(x, z), bias);
		}

		/** Trim at a fixed rung (0 the darkest, a plinth; 1 the palest, a rib), a little dither drawn from the stream. */
		BlockState trim(double t, Random rnd) {
			return grad(trim, t, rnd);
		}

		BlockState roof(Random rnd) {
			return roof[rnd.nextInt(roof.length)];
		}

		BlockState glass(int u, int ly, int top) {
			if (ly >= top - 1) {
				return glass[2];
			}
			return Math.floorMod(u + ly, 4) == 0 ? glass[1] : glass[0];
		}
	}

	public static final int TWILIGHT = 0, BONE = 1, GLOAM = 2, RUIN = 3;

	/** The materials of a building, of a given scheme ({@code id}; -1: the one its stream gives). */
	static Scheme scheme(Random rnd, int id) {
		Scheme s = new Scheme();
		BlockState purple = glass(), magenta = glass2(), blue = glass3();
		// drawn whether or not it is wanted, so a building told its scheme is otherwise the one its seed builds
		int drawn = rnd.nextInt(4);
		switch (id >= 0 ? id % 4 : drawn) {
			case 0 -> {
				// Twilight: the full ladder of the hush stone, from a blackstone foot to a pale crown
				s.wall = wallGrad();
				s.trim = trimGrad();
				s.roof = new BlockState[] {tileStairs(), tileStairs(), tileStairs(), mc("deepslate_tile_stairs", tileStairs()), bsStairs()};
				s.roofFill = tile();
				s.corbel = brickStairs();
				s.cap = tileSlab();
				s.rail = brickWall();
				s.floor = smooth();
				s.floorAlt = bsBrick();
				s.accent = vein();
				s.band = carved();
				s.glass = new BlockState[] {purple, magenta, blue};
				s.plinth = bsStairs();
				s.wear = 0.08;
			}
			case 1 -> {
				// Bone: pale walls and dark trim under slate roofs, cold glass
				s.wall = new BlockState[] {pale(), pale(), pale(), palePol(), pale(), palePol()};
				s.trim = new BlockState[] {bsPol(), smooth(), smooth()};
				s.roof = new BlockState[] {tileStairs(), mc("deepslate_tile_stairs", tileStairs()), mc("deepslate_tile_stairs", tileStairs()), bsStairs()};
				s.roofFill = mc("deepslate_tiles", tile());
				s.corbel = palePolStairs();
				s.cap = palePolSlab();
				s.rail = paleWall();
				s.floor = palePol();
				s.floorAlt = smooth();
				s.accent = silked();
				s.band = smooth();
				s.glass = new BlockState[] {mc("light_blue_stained_glass", blue), purple, mc("white_stained_glass", blue)};
				s.plinth = smoothStairs();
				s.wear = 0.05;
			}
			case 2 -> {
				// Gloam: black and dark hush stone, lit by courses of silk
				s.wall = new BlockState[] {mc("blackstone", bsBrick()), bsBrick(), bsBrick(), brick(), brick(), tile()};
				s.trim = new BlockState[] {bsPol(), bsPol(), smooth()};
				s.roof = new BlockState[] {bsStairs(), cobbleStairs(), tileStairs()};
				s.roofFill = bsBrick();
				s.corbel = bsStairs();
				s.cap = mc("polished_blackstone_slab", tileSlab());
				s.rail = bsWall();
				s.floor = bsPol();
				s.floorAlt = smooth();
				s.accent = silked();
				s.band = silked();
				s.glass = new BlockState[] {magenta, purple, crying()};
				s.plinth = bsStairs();
				s.wear = 0.07;
			}
			default -> {
				// Ruin: the ladder worn down to moss and cracks, its crowns broken
				s.wall = new BlockState[] {cobble(), brick(), mossy(), brick(), tile(), pale()};
				s.trim = trimGrad();
				s.roof = new BlockState[] {cobbleStairs(), tileStairs(), mc("mossy_cobblestone_stairs", cobbleStairs())};
				s.roofFill = cobble();
				s.corbel = brickStairs();
				s.cap = tileSlab();
				s.rail = cobbleWall();
				s.floor = cobble();
				s.floorAlt = mossy();
				s.accent = vein();
				s.band = carved();
				s.glass = new BlockState[] {purple, magenta, blue};
				s.plinth = cobbleStairs();
				s.wear = 0.28;
				s.ruin = true;
			}
		}
		s.pier = pillar();
		return s;
	}

	// ------------------------------------------------------------------ tiers

	static final int LANCET = 0, ARCADE = 1, COLONNADE = 2, BANDED = 3, BELFRY = 4, LANTERN = 5;
	static final int NO_CORNERS = 0, BUTTRESSES = 1, TURRETS = 2;
	static final int SPIRE = 0, DOME = 1, CROWN = 2, TERRACE = 3, BROKEN = 4;

	/** One tier of a tower: its footprint, half-size, base, height, body, corners and parapet. */
	static final class Tier {
		Form form;
		int half, y0, height, body, corners;
		boolean merlons;
	}

	/** A tower being built: its frame (centred on it), scheme and random stream. */
	static final class Tower {
		Frame f;
		Scheme sc;
		Random rnd;
		/** Where the tower's top tier ends, and its footprint there, once built. */
		int topY, topHalf;
		Form topForm;
		/** Height of every tier's floor (for bridges to land on). */
		final List<int[]> floors = new ArrayList<>();
	}

	/** A tier's floor, reaching out over the ledge below; above the ground it leaves the stair well open. */
	private static void floor(Tower w, Form form, int half, int y, boolean ground, boolean well) {
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				if (!inside(form, x, z, half) || (!ground && well && x * x + z * z <= 20)) {
					continue;
				}
				w.f.set(x, y, z, (x + z & 1) == 0 ? w.sc.floor : w.sc.floorAlt);
			}
		}
	}

	/** Draws a tier's walls and whatever its body is, its corners and its ledge. */
	static void tier(Tower w, Tier tr, boolean door) {
		Frame f = w.f;
		Scheme sc = w.sc;
		Random rnd = w.rnd;
		int h = tr.half, y0 = tr.y0, H = tr.height;
		w.floors.add(new int[] {y0, h, tr.form.ordinal()});
		switch (tr.body) {
			case ARCADE -> arcade(w, tr, door);
			case COLONNADE -> colonnade(w, tr, door);
			case BANDED -> banded(w, tr, door);
			case BELFRY -> belfry(w, tr);
			case LANTERN -> glasshouse(w, tr, door);
			default -> lancet(w, tr, h, door);
		}
		if (tr.form == Form.SQUARE && tr.corners == BUTTRESSES) {
			buttresses(w, tr);
		} else if (tr.form != Form.ROUND && tr.corners == TURRETS && h >= 6) {
			turrets(w, tr);
		}
		// Corbels under the ledge, every other column, and the slab of the ledge over them
		int y1 = y0 + H;
		for (int x = -h - 1; x <= h + 1; x++) {
			for (int z = -h - 1; z <= h + 1; z++) {
				if (!rim(tr.form, x, z, h + 1) || inside(tr.form, x, z, h)) {
					continue;
				}
				Direction o = out(x, z);
				if ((x + z & 1) == 0 && !f.has(x, y1, z)) {
					f.set(x, y1, z, stairs(sc.corbel, o.getOpposite(), true));
				}
				// lanterns and chains hang from the ledge here and there, vines in a ruin
				if ((x * 7 + z * 13 & 15) == 3 && rnd.nextInt(3) > 0) {
					int len = 1 + rnd.nextInt(3);
					for (int k = 1; k <= len; k++) {
						f.soft(x, y1 + 1 - k, z, chain());
					}
					f.soft(x, y1 - len, z, hanging(rnd.nextInt(3) == 0 ? soulLantern() : lantern()));
				} else if ((sc.ruin || rnd.nextInt(5) == 0) && rnd.nextInt(4) == 0) {
					BlockState vines = mc("cave_vines");
					int len = 2 + rnd.nextInt(sc.ruin ? 10 : 6);
					for (int k = 0; k < len && vines != null; k++) {
						f.soft(x, y1 - k, z, vines);
					}
				}
			}
		}
		// Warm light inside, so the windows glow
		int in = Math.max(1, h - 2);
		for (int[] q : new int[][] {{in, in}, {-in, in}, {in, -in}, {-in, -in}}) {
			if (inside(tr.form, q[0], q[1], h - 1) && f.has(q[0], y0, q[1]) && !f.has(q[0], y0 + 1, q[1])) {
				f.set(q[0], y0 + 1, q[1], lantern());
			}
		}
	}

	/** The parapet round a terrace left where a tier steps in: a balustrade, or battlements. */
	static void parapet(Tower w, Form form, int half, int y, boolean merlons) {
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				if (!rim(form, x, z, half)) {
					continue;
				}
				if (merlons) {
					w.f.soft(x, y, z, w.sc.masonryAt(w.f, x, y, z, 0));
					if ((x + z & 1) == 0) {
						w.f.soft(x, y + 1, z, w.sc.masonryAt(w.f, x, y + 1, z, 0));
					}
				} else {
					w.f.soft(x, y, z, w.sc.rail);
				}
			}
		}
	}

	/** Lancet windows, recessed behind their openings under arched heads, with sills, and pilasters between them. */
	private static void lancet(Tower w, Tier tr, int h, boolean door) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int H = tr.height, y0 = tr.y0;
		int sp = h >= 9 ? 5 : 4;
		int top = H - 3;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!rim(tr.form, x, z, h)) {
					continue;
				}
				Direction o = out(x, z);
				int b = bay(tr.form, x, z, h, sp);
				boolean win = b != 9 && top >= 6;
				int u = Math.abs(x) >= Math.abs(z) ? z : x;
				for (int ly = 1; ly <= H; ly++) {
					int y = y0 + ly;
					if (door && o == Direction.SOUTH && Math.abs(x) <= 1 && ly <= (x == 0 ? 6 : 5)) {
						f.clear(x, y, z);
						continue;
					}
					if (win && ly >= 3 && ly <= (b == 0 ? top : top - 1)) {
						f.clear(x, y, z);
						f.set(x - o.getStepX(), y, z - o.getStepZ(), ly % 7 == 0 ? sc.accent : sc.glass(u, ly, top));
						continue;
					}
					if (win && b != 0 && ly == top) {
						f.set(x, y, z, stairs(sc.corbel, aside(o, b), true));
						continue;
					}
					if (win && b == 0 && ly == top + 1) {
						f.set(x, y, z, sc.band);
						continue;
					}
					f.set(x, y, z, ly == 1 ? sc.trim(0, w.rnd) : sc.masonryAt(f, x, y, z, 0));
				}
				if (win && !(door && o == Direction.SOUTH)) {
					// the sill, standing out under the window
					f.soft(x + o.getStepX(), y0 + 2, z + o.getStepZ(), stairs(sc.corbel, o.getOpposite(), true));
				}
				// pilasters midway between the windows of a square, standing out from the wall
				boolean mid = tr.form == Form.SQUARE && !win && Math.abs(u) % sp == sp / 2 && Math.abs(u) < h - 1 && Math.abs(x) != Math.abs(z);
				if (mid && !(door && o == Direction.SOUTH && Math.abs(x) <= 3)) {
					int px = x + o.getStepX(), pz = z + o.getStepZ();
					for (int ly = 1; ly < H; ly++) {
						f.set(px, y0 + ly, pz, ly == H - 1 ? sc.band : sc.pier);
					}
					f.set(px + o.getStepX(), y0 + 1, pz + o.getStepZ(), stairs(sc.corbel, o.getOpposite(), false));
				}
			}
		}
	}

	/** An open arcade round the tier: piers and arches outside, a walk behind them, and a wall with doors within. */
	private static void arcade(Tower w, Tier tr, boolean door) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int h = tr.half, H = tr.height, y0 = tr.y0;
		int ah = Math.min(H - 3, 7);
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!rim(tr.form, x, z, h)) {
					continue;
				}
				Direction o = out(x, z);
				int b = bay(tr.form, x, z, h, 4);
				for (int ly = 1; ly <= H; ly++) {
					int y = y0 + ly;
					if (b != 9 && ly <= ah - (b == 0 ? 0 : 1)) {
						f.clear(x, y, z);
					} else if (b != 9 && b != 0 && ly == ah) {
						f.set(x, y, z, stairs(sc.corbel, aside(o, b), true));
					} else if (b == 0 && ly == ah + 1) {
						f.set(x, y, z, sc.band);
					} else if (b == 9 && ly < ah) {
						f.set(x, y, z, sc.pier);
					} else {
						f.set(x, y, z, sc.masonryAt(f, x, y, z, 0));
					}
				}
			}
		}
		// The wall within, two blocks back, with a door on every side
		int ih = h - 2;
		if (ih >= 3) {
			Tier inner = new Tier();
			inner.form = tr.form;
			inner.half = ih;
			inner.y0 = y0;
			inner.height = H;
			lancet(w, inner, ih, false);
			for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				for (int k = -1; k <= 1; k++) {
					int x = d.getStepX() * ih + (d.getStepX() == 0 ? k : 0), z = d.getStepZ() * ih + (d.getStepZ() == 0 ? k : 0);
					for (int ly = 1; ly <= (k == 0 ? 5 : 4); ly++) {
						f.clear(x, y0 + ly, z);
						f.clear(x - d.getStepX(), y0 + ly, z - d.getStepZ());
					}
				}
			}
		}
	}

	/** A colonnade of pillars round the tier under an architrave, with tall windows in the wall behind it. */
	private static void colonnade(Tower w, Tier tr, boolean door) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int h = tr.half, H = tr.height, y0 = tr.y0;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!rim(tr.form, x, z, h)) {
					continue;
				}
				boolean column = tr.form == Form.SQUARE ? ((Math.abs(x) >= Math.abs(z) ? z : x) & 1) == 0 || Math.abs(x) == Math.abs(z)
						: bay(tr.form, x, z, h, 3) == 0;
				for (int ly = 1; ly <= H; ly++) {
					if (ly == H) {
						f.set(x, y0 + ly, z, sc.band);
					} else if (ly == H - 1) {
						f.set(x, y0 + ly, z, sc.trimAt(f, x, y0 + ly, z, 0));
					} else if (column) {
						f.set(x, y0 + ly, z, ly == 1 ? sc.trim(0, w.rnd) : sc.pier);
					} else {
						f.clear(x, y0 + ly, z);
					}
				}
			}
		}
		int ih = h - 2;
		if (ih >= 3) {
			Tier inner = new Tier();
			inner.form = tr.form;
			inner.half = ih;
			inner.y0 = y0;
			inner.height = H;
			lancet(w, inner, ih, door);
		}
	}

	/** Banded masonry, pale and dark courses in turn, with blind arches below and round windows above. */
	private static void banded(Tower w, Tier tr, boolean door) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int h = tr.half, H = tr.height, y0 = tr.y0;
		int oc = Math.max(4, H * 2 / 3);
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!rim(tr.form, x, z, h)) {
					continue;
				}
				Direction o = out(x, z);
				int b = bay(tr.form, x, z, h, 4);
				int bo = bay(tr.form, x, z, h, 8);
				int u = Math.abs(x) >= Math.abs(z) ? z : x;
				for (int ly = 1; ly <= H; ly++) {
					int y = y0 + ly;
					if (door && o == Direction.SOUTH && Math.abs(x) <= 1 && ly <= (x == 0 ? 6 : 5)) {
						f.clear(x, y, z);
						continue;
					}
					boolean pale = (ly / 2 & 1) == 0;
					BlockState s = sc.masonryAt(f, x, y, z, pale ? 0.35 : -0.25);
					// blind arches: recessed panels, three wide, darker within
					if (b != 9 && ly >= 2 && ly <= oc - 3 && !(b != 0 && ly == oc - 3)) {
						f.clear(x, y, z);
						f.set(x - o.getStepX(), y, z - o.getStepZ(), sc.trim(0, w.rnd));
						continue;
					}
					if (b != 9 && b != 0 && ly == oc - 3) {
						s = stairs(sc.corbel, aside(o, b), true);
					}
					// round windows: a cross of glass recessed in the wall, ringed by the band
					int dy = ly - oc;
					if (bo == 0 && Math.abs(dy) <= 1 || Math.abs(bo) == 1 && dy == 0) {
						f.clear(x, y, z);
						f.set(x - o.getStepX(), y, z - o.getStepZ(), sc.glass(u, ly, H));
						continue;
					}
					if (bo != 9 && Math.abs(dy) <= 2 && Math.abs(bo) + Math.abs(dy) == 2) {
						s = sc.band;
					}
					f.set(x, y, z, s);
				}
			}
		}
	}

	/** An open belfry: piers at the corners, great arches on every side, a chandelier hung in the middle. */
	private static void belfry(Tower w, Tier tr) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int h = tr.half, H = tr.height, y0 = tr.y0;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!rim(tr.form, x, z, h)) {
					continue;
				}
				Direction o = out(x, z);
				int u = Math.abs(x) >= Math.abs(z) ? z : x;
				boolean pier = tr.form == Form.SQUARE ? Math.abs(u) >= h - 1 : bay(tr.form, x, z, h, Math.max(3, h)) == 0;
				for (int ly = 1; ly <= H; ly++) {
					int y = y0 + ly;
					if (pier || ly >= H - 1) {
						f.set(x, y, z, pier ? sc.pier : sc.masonryAt(f, x, y, z, 0));
					} else if (ly == H - 2 && Math.abs(u) == h - 2 && tr.form == Form.SQUARE) {
						f.set(x, y, z, stairs(sc.corbel, aside(o, u > 0 ? -1 : 1), true));
					} else {
						f.clear(x, y, z);
					}
				}
			}
		}
		int len = Math.max(2, H - 5);
		for (int k = 0; k <= len; k++) {
			f.set(0, y0 + H - k, 0, chain());
		}
		f.set(0, y0 + H - len - 1, 0, sc.accent);
		f.set(0, y0 + H - len - 2, 0, hanging(soulLantern()));
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			f.set(d.getStepX(), y0 + H - len - 1, d.getStepZ(), hanging(lantern()));
		}
	}

	/** A lantern of glass: tall panes between slender mullions, crossed by transoms. */
	private static void glasshouse(Tower w, Tier tr, boolean door) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int h = tr.half, H = tr.height, y0 = tr.y0;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!rim(tr.form, x, z, h)) {
					continue;
				}
				Direction o = out(x, z);
				int u = Math.abs(x) >= Math.abs(z) ? z : x;
				boolean mullion = tr.form == Form.SQUARE ? (u & 1) == 0 || Math.abs(x) == Math.abs(z) : bay(tr.form, x, z, h, 2) == 0;
				for (int ly = 1; ly <= H; ly++) {
					int y = y0 + ly;
					if (door && o == Direction.SOUTH && Math.abs(x) <= 1 && ly <= (x == 0 ? 6 : 5)) {
						f.clear(x, y, z);
					} else if (ly == 1 || ly == H) {
						f.set(x, y, z, sc.trimAt(f, x, y, z, 0));
					} else if (mullion || ly % 6 == 0) {
						f.set(x, y, z, mullion ? sc.pier : sc.band);
					} else {
						f.set(x, y, z, sc.glass(u, ly, H));
					}
				}
			}
		}
	}

	/** Stepped buttresses on the four corners of a square tier, each crowned with a pinnacle. */
	private static void buttresses(Tower w, Tier tr) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int h = tr.half, H = tr.height, y0 = tr.y0;
		int low = (int) (H * 0.45);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int ly = 0; ly <= H + 3; ly++) {
					int y = y0 + ly;
					for (int ax = -1; ax <= 1; ax++) {
						for (int az = -1; az <= 1; az++) {
							f.set(sx * h + ax, y, sz * h + az, ly % 7 == 6 ? sc.band : sc.trimAt(f, sx * h + ax, y, sz * h + az, 0));
						}
					}
					if (ly <= low) {
						f.set(sx * (h + 2), y, sz * h, sc.masonryAt(f, sx * (h + 2), y, sz * h, 0));
						f.set(sx * h, y, sz * (h + 2), sc.masonryAt(f, sx * h, y, sz * (h + 2), 0));
						f.set(sx * (h + 2), y, sz * (h + 1), sc.masonryAt(f, sx * (h + 2), y, sz * (h + 1), 0));
						f.set(sx * (h + 1), y, sz * (h + 2), sc.masonryAt(f, sx * (h + 1), y, sz * (h + 2), 0));
					}
				}
				Direction ix = sx > 0 ? Direction.WEST : Direction.EAST, iz = sz > 0 ? Direction.NORTH : Direction.SOUTH;
				f.set(sx * (h + 2), y0 + low + 1, sz * h, stairs(sc.corbel, ix, false));
				f.set(sx * (h + 2), y0 + low + 1, sz * (h + 1), stairs(sc.corbel, ix, false));
				f.set(sx * h, y0 + low + 1, sz * (h + 2), stairs(sc.corbel, iz, false));
				f.set(sx * (h + 1), y0 + low + 1, sz * (h + 2), stairs(sc.corbel, iz, false));
				pinnacle(w, sx * h, y0 + H + 4, sz * h);
			}
		}
	}

	/** A pinnacle: a carved block, a short shaft with a collar of stairs, and a point of light. */
	static void pinnacle(Tower w, int x, int y, int z) {
		Frame f = w.f;
		f.set(x, y, z, w.sc.band);
		for (int k = 1; k <= 3; k++) {
			f.set(x, y + k, z, w.sc.rail);
		}
		f.set(x, y + 4, z, endRod());
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			f.soft(x + d.getStepX(), y + 1, z + d.getStepZ(), stairs(w.sc.corbel, d.getOpposite(), false));
		}
	}

	/** Round turrets corbelled out from the corners near the top of a tier, each under a cone. */
	private static void turrets(Tower w, Tier tr) {
		Frame f = w.f;
		Scheme sc = w.sc;
		int h = tr.half, y0 = tr.y0, H = tr.height;
		int base = y0 + Math.max(2, H - 9), top = y0 + H + 3;
		int c = tr.form == Form.SQUARE ? h : (int) Math.round(h * 0.71);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int tx = sx * c, tz = sz * c;
				for (int y = base; y <= top; y++) {
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							if (!rim(Form.ROUND, dx, dz, 2)) {
								continue;
							}
							boolean slit = (y - base) % 4 == 2 && (dx == 0 || dz == 0);
							f.set(tx + dx, y, tz + dz, slit ? mc("iron_bars", sc.pier) : sc.masonryAt(f, tx + dx, y, tz + dz, 0));
						}
					}
				}
				// corbelled under its foot
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (rim(Form.ROUND, dx, dz, 2)) {
							f.soft(tx + dx, base - 1, tz + dz, stairs(sc.corbel, out(dx, dz).getOpposite(), true));
						}
					}
				}
				cone(w, Form.ROUND, 3, tx, top + 1, tz, 6, false);
			}
		}
	}

	// ------------------------------------------------------------------ crowns

	/** A cone or pyramid of roof stairs over a footprint, filled under its skin, rising {@code rise} courses. */
	static int cone(Tower w, Form form, int half, int cx, int y, int cz, int rise, boolean hips) {
		Frame f = w.f;
		Scheme sc = w.sc;
		for (int k = 0; k <= rise; k++) {
			double hk = half * (1 - (double) k / rise);
			if (hk < 0.5) {
				break;
			}
			int r = (int) Math.ceil(hk) + 1;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (!rim(form, x, z, hk)) {
						continue;
					}
					boolean corner = form == Form.SQUARE && Math.abs(x) == Math.abs(z);
					f.set(cx + x, y + k, cz + z, corner && hips ? sc.trim(1, w.rnd) : stairs(sc.roof(w.rnd), out(x, z).getOpposite(), false));
					int ix = x - Integer.signum(x), iz = z - Integer.signum(z);
					if (inside(form, ix, iz, hk) && !rim(form, ix, iz, hk)) {
						f.soft(cx + ix, y + k, cz + iz, sc.roofFill);
					}
					// crockets up the hips
					if (corner && hips && k % 3 == 1) {
						f.soft(cx + x + Integer.signum(x), y + k, cz + z + Integer.signum(z), sc.rail);
					}
				}
			}
		}
		return y + rise;
	}

	/** A needle of carved stone and light standing on a point. */
	static void needle(Tower w, int x, int y, int z, int len) {
		for (int k = 0; k < len; k++) {
			w.f.set(x, y + k, z, k < 2 ? w.sc.band : w.sc.rail);
		}
		w.f.set(x, y + len, z, w.sc.accent);
		w.f.set(x, y + len + 1, z, endRod());
	}

	/** Crowns a tower's top tier (which ends at y, its terrace at y + 1). */
	static void crown(Tower w, Form form, int h, int y, int kind) {
		Frame f = w.f;
		Scheme sc = w.sc;
		Random rnd = w.rnd;
		switch (kind) {
			case DOME -> {
				// A ribbed dome on a short drum, a lantern on its top
				int R = Math.max(3, h);
				for (int dy = 0; dy <= R; dy++) {
					double rr = Math.sqrt(Math.max(0, R * R - dy * dy)) * 1.02;
					int r = (int) Math.ceil(rr) + 1;
					for (int x = -r; x <= r; x++) {
						for (int z = -r; z <= r; z++) {
							if (!rim(Form.ROUND, x, z, rr)) {
								continue;
							}
							double a = Math.atan2(z, x);
							boolean rib = Math.abs(a - Math.round(a / (Math.PI / 4)) * (Math.PI / 4)) * Math.max(1, rr) <= 0.6;
							f.set(x, y + 1 + dy, z, rib ? sc.trim(1, rnd) : dy == R ? sc.accent : sc.roofFill);
						}
					}
				}
				int ly = y + 2 + R;
				for (int sx = -1; sx <= 1; sx += 2) {
					for (int sz = -1; sz <= 1; sz += 2) {
						for (int k = 0; k < 3; k++) {
							f.set(sx, ly + k, sz, sc.pier);
						}
					}
				}
				f.set(0, ly, 0, hanging(soulLantern()));
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						f.set(x, ly + 3, z, sc.cap);
					}
				}
				needle(w, 0, ly + 4, 0, 3);
				w.topY = ly + 9;
			}
			case CROWN -> {
				// A ring of pinnacles round a short spire, a halo turning above it
				for (int x = -h; x <= h; x++) {
					for (int z = -h; z <= h; z++) {
						if (rim(form, x, z, h) && bay(form, x, z, h, 4) == 0) {
							for (int k = 1; k <= 2; k++) {
								f.set(x, y + k, z, sc.pier);
							}
							pinnacle(w, x, y + 3, z);
						}
					}
				}
				int peak = cone(w, form, Math.max(2, h - 2), 0, y + 1, 0, Math.max(4, h * 3 / 2), form == Form.SQUARE);
				needle(w, 0, peak + 1, 0, 4);
				net.minecraft.core.BlockPos c = f.pos(0, peak + 7, 0);
				ring(f.p, rnd, c.getX(), c.getY(), c.getZ(), h + 3, 0.15 + rnd.nextDouble() * 0.3, rnd.nextDouble() * Math.PI, 1);
				w.topY = peak + 10;
			}
			case TERRACE -> {
				// A terrace with a crystal hovering over it, lamps at its corners
				parapet(w, form, h + 1, y + 1, false);
				net.minecraft.core.BlockPos c = f.pos(0, y + 9 + rnd.nextInt(4), 0);
				crystal(f.p, rnd, c.getX(), c.getY(), c.getZ(), h >= 6 ? 4 : 3);
				f.p.falls.add(new int[] {c.getX(), c.getY() - 3, c.getZ(), 8});
				w.topY = y + 16;
			}
			case BROKEN -> {
				// Walls broken off at ragged heights, open to the sky, the fallen stone lying on the floor
				for (int x = -h; x <= h; x++) {
					for (int z = -h; z <= h; z++) {
						if (rim(form, x, z, h)) {
							int up = (int) Math.round(noise(x * 31L + z, x * 0.4, z * 0.4) * 8) - 2;
							for (int k = 1; k <= up; k++) {
								f.set(x, y + 1 + k, z, sc.masonryAt(f, x, y + 1 + k, z, 0));
							}
						} else if (inside(form, x, z, h) && rnd.nextInt(7) == 0) {
							f.soft(x, y + 1, z, rnd.nextBoolean() ? cobble() : mossy());
						}
					}
				}
				w.topY = y + 8;
			}
			default -> {
				// A spire: a steep pyramid (or cone) of roof stairs with crockets on its hips, a crowned needle
				int rise = h * 2 + rnd.nextInt(Math.max(1, h));
				int peak = cone(w, form, h + 1, 0, y + 1, 0, rise, form == Form.SQUARE);
				needle(w, 0, peak + 1, 0, 5);
				for (int a = 0; a < 16; a++) {
					double an = a * Math.PI / 8;
					f.set((int) Math.round(Math.cos(an) * 3), peak + 4, (int) Math.round(Math.sin(an) * 3), a % 4 == 0 ? sc.accent : sc.trim(1, rnd));
				}
				w.topY = peak + 8;
			}
		}
	}

	// ------------------------------------------------------------------ towers

	/**
	 * A tower of stacked tiers at local (ox, oz) of the base frame, each tier stepping in from the last; a great tower
	 * gets a glowing core with a spiral stair up through it, a reliquary on its last floor, and four or five tiers.
	 */
	static Tower tower(Frame base, Scheme sc, Random rnd, int ox, int oz, int half, int maxH, boolean great, int crown, Form form0) {
		return tower(base, sc, rnd, ox, oz, half, maxH, great, crown, form0, -1);
	}

	/**
	 * As {@link #tower(Frame, Scheme, Random, int, int, int, int, boolean, int, Form)}, its first tier {@code firstTier}
	 * blocks tall (-1: as its stream gives): so the tower's first ledge lines up with the eaves of the halls beside it, or
	 * its first stage rises past the ridges of the roofs that run into it.
	 */
	static Tower tower(Frame base, Scheme sc, Random rnd, int ox, int oz, int half, int maxH, boolean great, int crown, Form form0, int firstTier) {
		Tower w = new Tower();
		w.f = new Frame(base.p, base.wx(ox, oz), base.oy, base.wz(ox, oz), base.rot);
		w.sc = sc;
		w.rnd = rnd;
		int tiers = great ? 3 + rnd.nextInt(2) : 2 + rnd.nextInt(2);
		// Heights first, so the stack and its crown fit the height they are given
		int[] heights = new int[tiers];
		int sum = 0;
		for (int i = 0; i < tiers; i++) {
			heights[i] = great ? (i == 0 ? 20 + rnd.nextInt(8) : 13 + rnd.nextInt(6) - i) : (i == 0 ? 12 + rnd.nextInt(8) : 9 + rnd.nextInt(5));
			if (i > 0 || firstTier <= 0) {
				sum += heights[i] + 1;
			}
		}
		double crownShare = switch (crown) {
			case SPIRE -> 3.2 * half + 10;
			case DOME -> half + 12;
			case CROWN -> 1.5 * half + 16;
			default -> 16;
		};
		double scale = Math.min(1, (maxH - crownShare - (firstTier > 0 ? firstTier + 1 : 0)) / Math.max(1, sum));
		int y = 0, h = half;
		Form form = form0;
		int prevHalf = -1;
		Form prevForm = form0;
		int wellTop = 0;
		for (int i = 0; i < tiers && h >= 3; i++) {
			Tier tr = new Tier();
			tr.form = form;
			tr.half = h;
			tr.y0 = y;
			tr.height = i == 0 && firstTier > 0 ? firstTier : Math.max(great ? 9 : 7, (int) Math.round(heights[i] * scale));
			boolean last = i == tiers - 1;
			int roll = rnd.nextInt(10);
			if (i == 0) {
				tr.body = roll < 5 ? LANCET : roll < 7 ? ARCADE : roll < 9 ? BANDED : COLONNADE;
			} else if (last && h <= 6) {
				tr.body = roll < 4 ? BELFRY : roll < 7 ? LANCET : LANTERN;
			} else {
				tr.body = roll < 4 ? LANCET : roll < 6 ? COLONNADE : roll < 8 ? BANDED : LANTERN;
			}
			// (a lantern of glass is for a slender tier: on a broad one it is a flat wall of panes)
			if (tr.body == ARCADE && h < 7 || tr.body == COLONNADE && h < 6 || tr.body == LANTERN && h > 6) {
				tr.body = LANCET;
			}
			tr.corners = form == Form.ROUND ? NO_CORNERS : rnd.nextInt(3);
			tr.merlons = sc.ruin || rnd.nextInt(3) == 0;
			// The floor: the ground, or the terrace the tier below leaves, its middle open where the stair climbs through
			boolean well = great && h >= 5;
			if (i == 0) {
				floor(w, form, h, 0, true, false);
			} else {
				floor(w, prevForm, prevHalf + 1, y, false, great && prevHalf >= 5);
			}
			tier(w, tr, i == 0);
			int y1 = y + tr.height;
			if (well) {
				wellTop = y1;
			}
			if (!last) {
				parapet(w, form, h + 1, y1 + 2, tr.merlons);
			}
			w.topY = y1;
			w.topHalf = h;
			w.topForm = form;
			prevHalf = h;
			prevForm = form;
			y = y1 + 1;
			// the next tier steps in (a slender tower by one, or not at all), and often turns from square to octagon,
			// octagon to round
			h -= h >= 8 ? (rnd.nextInt(3) == 0 ? 3 : 2) : h >= 6 ? 2 : rnd.nextInt(2);
			if (form == Form.SQUARE && rnd.nextInt(3) == 0) {
				form = Form.OCTAGON;
			} else if (form == Form.OCTAGON && rnd.nextInt(3) == 0) {
				form = Form.ROUND;
			}
			if (last) {
				break;
			}
		}
		int top = w.topY;
		// the top tier's roof, then the crown on it
		floor(w, w.topForm, w.topHalf + 1, top + 1, false, false);
		if (great && wellTop > 0) {
			core(w, wellTop);
			// the reliquary, on the head of the core where the stair ends
			w.f.then(0, wellTop, 0, loot(w.f.pos(0, wellTop, 0), 3));
		}
		crown(w, w.topForm, w.topHalf, top + 1, sc.ruin && crown != DOME ? BROKEN : crown);
		return w;
	}

	/** The half-size of a tower at a height (that of the tier standing there), or -1 above its top. */
	static int halfAt(Tower w, int y) {
		int h = -1;
		for (int[] fl : w.floors) {
			if (fl[0] <= y) {
				h = fl[1];
			}
		}
		return y > w.topY ? -1 : h;
	}

	/** The footprint of a tower at a height. */
	static Form formAt(Tower w, int y) {
		Form form = Form.SQUARE;
		for (int[] fl : w.floors) {
			if (fl[0] <= y) {
				form = Form.values()[fl[2]];
			}
		}
		return form;
	}

	/** A doorway three wide through a wall at local z of a frame (and {@code depth} blocks in behind it), at height y. */
	static void doorway(Frame g, int x, int z, int depth, int y) {
		for (int lx = -1; lx <= 1; lx++) {
			for (int ly = 1; ly <= (lx == 0 ? 5 : 4); ly++) {
				for (int d = 0; d < depth; d++) {
					g.clear(x + lx, y + ly, z - d);
				}
			}
		}
	}

	/**
	 * A landing inside a tower at a height, so a bridge that comes in there has a floor to step onto (in a great tower
	 * a ring round the stair well, which it leaves open).
	 */
	static void landing(Tower w, int y, boolean well) {
		int h = halfAt(w, y);
		Form form = formAt(w, y);
		for (int x = -h + 1; x <= h - 1; x++) {
			for (int z = -h + 1; z <= h - 1; z++) {
				if (inside(form, x, z, h - 1) && (well ? x * x + z * z > 20 : Math.abs(x) > 1 || Math.abs(z) > 1)) {
					w.f.soft(x, y, z, w.sc.floor);
				}
			}
		}
	}

	/** The core of a great tower: silk inside violet glass, and a spiral stair winding up round it to {@code top}. */
	private static void core(Tower w, int top) {
		Frame f = w.f;
		for (int ly = 1; ly < top; ly++) {
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					f.set(ax, ly, az, ax == 0 && az == 0 ? (ly % 6 == 0 ? crying() : vein()) : w.sc.glass[0]);
				}
			}
		}
		for (int ly = 1; ly < top; ly++) {
			double a = ly * Math.PI / 8;
			for (double rr = 2.2; rr <= 4.4; rr += 0.7) {
				for (double da = 0; da < 0.4; da += 0.2) {
					int sx = (int) Math.round(Math.cos(a + da) * rr), sz = (int) Math.round(Math.sin(a + da) * rr);
					if (Math.abs(sx) <= 1 && Math.abs(sz) <= 1) {
						continue;
					}
					f.set(sx, ly, sz, w.sc.floor);
					for (int hh = 1; hh <= 3; hh++) {
						if (Math.abs(sx) < 5 && Math.abs(sz) < 5) {
							f.clear(sx, ly + hh, sz);
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ halls

	/**
	 * A hall running out along local +z of its frame from z0 to z1: buttressed walls with recessed windows, a steep
	 * roof, and at its far end a layered portal (or a closed gable with a round window). With aisles, the middle rises
	 * above lean-to roofs on either side, its clerestory held by flying buttresses.
	 */
	static void hall(Frame f, Scheme sc, Random rnd, int z0, int z1, int w, int h, boolean portal, boolean aisles) {
		int aw = aisles ? 3 : 0;
		int ah = h - 6;
		for (int lz = z0; lz <= z1; lz++) {
			boolean bay = (lz - z0) % 3 == 1;
			for (int lx = -w - aw; lx <= w + aw; lx++) {
				f.set(lx, 0, lz, (lx + lz & 1) == 0 ? sc.floor : sc.floorAlt);
				boolean nave = Math.abs(lx) <= w;
				boolean sideWall = Math.abs(lx) == w;
				boolean aisleWall = aisles && Math.abs(lx) == w + aw;
				boolean endWall = lz == z1;
				int wallTop = nave ? h : ah;
				for (int ly = 1; ly <= wallTop; ly++) {
					double t = ly / 48.0;
					boolean inner = !(sideWall || aisleWall || endWall);
					if (inner) {
						f.clear(lx, ly, lz);
						continue;
					}
					if (endWall && portal && Math.abs(lx) <= 1 && ly <= (lx == 0 ? 6 : 5)) {
						f.clear(lx, ly, lz);
						continue;
					}
					// the nave's wall opens into the aisles in arches below the clerestory
					if (aisles && sideWall && !endWall && ly <= ah - 1) {
						if (bay || (lz - z0) % 3 == 2) {
							if (ly <= ah - 3 || bay && ly == ah - 2) {
								f.clear(lx, ly, lz);
								continue;
							}
						}
					}
					int wl = sideWall && aisles ? ah + 1 : 3;
					int wh = sideWall && aisles ? h - 2 : (aisleWall ? ah - 2 : h - 4);
					boolean window = (sideWall || aisleWall) && !endWall && bay && ly >= wl && ly <= wh;
					if (window) {
						f.clear(lx, ly, lz);
						f.set(lx - Integer.signum(lx), ly, lz, ly == wh ? sc.accent : sc.glass(lz, ly, wh));
					} else {
						f.set(lx, ly, lz, ly == 1 ? sc.trim(0, rnd) : sc.masonryAt(f, lx, ly, lz, 0));
					}
				}
				if ((sideWall || aisleWall) && bay) {
					f.set(lx, aisleWall ? ah - 1 : h - 1, lz, sc.band);
				}
			}
			// Buttresses between the bays, flying over the aisles to piers beyond them
			if ((lz - z0) % 3 == 0 && lz < z1) {
				for (int s = -1; s <= 1; s += 2) {
					int px = s * (w + aw + 1);
					int top = aisles ? ah + 2 : h - 3;
					for (int ly = 1; ly <= top; ly++) {
						f.set(px, ly, lz, sc.trimAt(f, px, ly, lz, 0));
					}
					f.set(px + s, 1, lz, stairs(sc.corbel, s > 0 ? Direction.WEST : Direction.EAST, false));
					if (aisles) {
						// the flyer: from the pier's head over the aisle roof up to the clerestory wall
						for (int k = 1; k <= aw; k++) {
							int fx = px - s * k;
							int fy = top + k;
							f.set(fx, fy, lz, sc.masonryAt(f, fx, fy, lz, 0));
							f.set(fx, fy - 1, lz, stairs(sc.corbel, s > 0 ? Direction.EAST : Direction.WEST, true));
						}
						pinnacleAt(f, sc, px, top + 1, lz);
					} else {
						f.set(px, top + 1, lz, stairs(sc.corbel, s > 0 ? Direction.WEST : Direction.EAST, false));
					}
				}
			}
			// Roofs: the nave's steep and ridged, the aisles' lean-to against the clerestory
			for (int k = 0; k <= w + 1; k++) {
				int half = w + 1 - k;
				f.set(half, h + 1 + k, lz, stairs(sc.roof(rnd), Direction.WEST, false));
				f.set(-half, h + 1 + k, lz, stairs(sc.roof(rnd), Direction.EAST, false));
				if (half == 0) {
					f.set(0, h + 1 + k, lz, sc.roofFill);
				}
			}
			f.set(w + 1, h, lz, stairs(sc.corbel, Direction.WEST, true));
			f.set(-w - 1, h, lz, stairs(sc.corbel, Direction.EAST, true));
			if (aisles) {
				for (int k = 0; k < aw; k++) {
					f.set(w + aw - k, ah + 1 + k, lz, stairs(sc.roof(rnd), Direction.WEST, false));
					f.set(-(w + aw - k), ah + 1 + k, lz, stairs(sc.roof(rnd), Direction.EAST, false));
				}
			}
			if ((lz - z0) % 3 == 2) {
				f.set(0, h, lz, chain());
				f.set(0, h - 1, lz, chain());
				f.set(0, h - 2, lz, hanging(lantern()));
			}
		}
		// The gable filled in, a round window in it, a pinnacle on its point
		for (int k = 0; k <= w; k++) {
			int half = w - k;
			for (int lx = -half; lx <= half; lx++) {
				int m = Math.abs(lx) + Math.abs(k - 2);
				f.set(lx, h + 1 + k, z1, m <= 1 ? (lx == 0 && k == 2 ? crying() : sc.glass[1]) : sc.masonryAt(f, lx, h + 1 + k, z1, 0));
			}
		}
		needleAt(f, sc, 0, h + w + 3, z1, 2);
		// The near end closed too (it stands against a tower or a wing): its gable filled, plain
		for (int k = 0; k <= w; k++) {
			int half = w - k;
			for (int lx = -half; lx <= half; lx++) {
				f.soft(lx, h + 1 + k, z0, sc.masonryAt(f, lx, h + 1 + k, z0, 0));
			}
		}
		if (portal) {
			portal(f, sc, rnd, z1);
		}
	}

	private static void pinnacleAt(Frame f, Scheme sc, int x, int y, int z) {
		f.set(x, y, z, sc.band);
		f.set(x, y + 1, z, sc.rail);
		f.set(x, y + 2, z, sc.rail);
		f.set(x, y + 3, z, endRod());
	}

	private static void needleAt(Frame f, Scheme sc, int x, int y, int z, int len) {
		for (int k = 0; k < len; k++) {
			f.set(x, y + k, z, sc.rail);
		}
		f.set(x, y + len, z, endRod());
	}

	/** A portal of three receding orders of arches before a door at z1, banners beside it, steps and lamps before it. */
	static void portal(Frame f, Scheme sc, Random rnd, int z1) {
		for (int order = 1; order <= 3; order++) {
			int lz = z1 + order;
			int hw = 1 + order, ah = 5 + order;
			for (int ly = 0; ly <= ah + 1; ly++) {
				for (int lx = -hw - 1; lx <= hw + 1; lx++) {
					boolean jamb = Math.abs(lx) == hw + 1 && ly <= ah;
					boolean head = ly == ah + 1;
					if (!jamb && !head) {
						continue;
					}
					f.set(lx, ly, lz, ly == 0 ? sc.floorAlt : jamb ? sc.pier : sc.masonryAt(f, lx, ly, lz, 0.15));
				}
				f.set(-hw, ah, lz, stairs(sc.corbel, Direction.WEST, true));
				f.set(hw, ah, lz, stairs(sc.corbel, Direction.EAST, true));
			}
			f.set(0, ah + 1, lz, sc.band);
			f.set(-hw - 1, ah + 2, lz, slab(sc.cap, false));
			f.set(hw + 1, ah + 2, lz, slab(sc.cap, false));
		}
		for (int side = -1; side <= 1; side += 2) {
			int uc = side * 6;
			for (int k = 0; k < 9; k++) {
				for (int du = -1; du <= 1; du++) {
					if (k == 8 && du != 0) {
						continue;
					}
					int m = Math.abs(du) + Math.abs(k - 4);
					f.soft(uc + du, 11 - k, z1 + 1, k == 0 ? woolDark() : m == 1 ? wool2() : m == 0 ? sc.accent : wool());
				}
			}
		}
		for (int lx = -3; lx <= 3; lx++) {
			f.set(lx, 0, z1 + 4, stairs(sc.corbel, Direction.NORTH, false));
		}
		lamp(f.p, rnd, f.wx(-6, z1 + 5), f.oy, f.wz(-6, z1 + 5));
		lamp(f.p, rnd, f.wx(6, z1 + 5), f.oy, f.wz(6, z1 + 5));
	}

	/** A half-round apse behind z = 0 of its frame (out along +z), lancets round it, a half-cone roof over it. */
	static void apse(Frame f, Scheme sc, Random rnd, int z0, int r, int h) {
		for (int x = -r; x <= r; x++) {
			for (int dz = 0; dz <= r; dz++) {
				if (!inside(Form.ROUND, x, dz, r)) {
					continue;
				}
				int z = z0 + dz;
				f.set(x, 0, z, (x + z & 1) == 0 ? sc.floor : sc.floorAlt);
				if (!rim(Form.ROUND, x, dz, r) || dz == 0) {
					for (int ly = 1; ly <= h; ly++) {
						f.clear(x, ly, z);
					}
					continue;
				}
				Direction o = out(x, dz);
				int b = bay(Form.ROUND, x, dz, r, 4);
				for (int ly = 1; ly <= h; ly++) {
					if (b != 9 && ly >= 3 && ly <= h - 3 - (b == 0 ? 0 : 1)) {
						f.clear(x, ly, z);
						f.set(x - o.getStepX(), ly, z - o.getStepZ(), sc.glass(x, ly, h - 3));
					} else {
						f.set(x, ly, z, ly == 1 ? sc.trim(0, rnd) : sc.masonryAt(f, x, ly, z, 0));
					}
				}
			}
		}
		for (int k = 0; k <= r; k++) {
			double hk = r + 1 - k;
			int rr = (int) Math.ceil(hk) + 1;
			for (int x = -rr; x <= rr; x++) {
				for (int dz = 0; dz <= rr; dz++) {
					if (rim(Form.ROUND, x, dz, hk) && (dz > 0 || Math.abs(x) >= hk - 0.5)) {
						f.set(x, h + 1 + k, z0 + dz, stairs(sc.roof(rnd), out(x, dz).getOpposite(), false));
					}
				}
			}
		}
	}

	/** A sky bridge from z0 to z1 along local +z at height y, three wide, railed, its underside corbelled, lanterns under it. */
	static void bridge(Frame f, Scheme sc, int x, int y, int z0, int z1) {
		for (int lz = Math.min(z0, z1); lz <= Math.max(z0, z1); lz++) {
			for (int lx = -1; lx <= 1; lx++) {
				f.soft(x + lx, y, lz, lx == 0 ? sc.floor : sc.floorAlt);
				for (int k = 1; k <= 3; k++) {
					f.clear(x + lx, y + k, lz);
				}
			}
			f.soft(x - 2, y, lz, sc.trim[sc.trim.length / 2]);
			f.soft(x + 2, y, lz, sc.trim[sc.trim.length / 2]);
			f.soft(x - 2, y + 1, lz, sc.rail);
			f.soft(x + 2, y + 1, lz, sc.rail);
			f.soft(x - 2, y - 1, lz, stairs(sc.corbel, Direction.EAST, true));
			f.soft(x + 2, y - 1, lz, stairs(sc.corbel, Direction.WEST, true));
			if (Math.floorMod(lz, 4) == 2) {
				f.soft(x, y - 1, lz, chain());
				f.soft(x, y - 2, lz, hanging(lantern()));
			}
		}
	}

	/** A paved forecourt before the building's front at local z, narrowing as it runs out toward the street. */
	static void forecourt(Plan p, Frame base, Random rnd, int top, int z, int len, int width) {
		BlockState[] path = pathGrad();
		for (int lz = z; lz <= z + len; lz++) {
			int hw = lz <= z + len / 2 ? width : Math.max(2, width - (lz - z - len / 2) * 2);
			for (int lx = -hw; lx <= hw; lx++) {
				double t = Math.min(1, (Math.abs(lx) / (width + 1.0) + (double) (lz - z) / len) / 1.4);
				pave(p, base.wx(lx, lz), top, base.wz(lx, lz), weather(grad(path, t, rnd), rnd, 0.1));
			}
		}
	}

	// ------------------------------------------------------------------ plans

	public static final int CATHEDRAL = 0, CITADEL = 1, CLUSTER = 2, ZIGGURAT = 3, GATEWAY = 4;

	/** What was built, for tests and logs: the plan, the scheme, the crown of the great tower. */
	public record Summary(int layout, int scheme, int crown, int towers, int height) {}

	private static int crownFor(Random rnd) {
		int k = rnd.nextInt(10);
		return k < 4 ? SPIRE : k < 6 ? DOME : k < 8 ? CROWN : TERRACE;
	}

	private static Form formFor(Random rnd, boolean great) {
		int k = rnd.nextInt(10);
		if (great) {
			return k < 5 ? Form.SQUARE : k < 8 ? Form.OCTAGON : Form.ROUND;
		}
		return k < 3 ? Form.SQUARE : k < 6 ? Form.OCTAGON : Form.ROUND;
	}

	/**
	 * Builds a colossal building on an island of radius r centred at (cx, cz) with its surface at top: everything stays
	 * within r - 4 of the centre, and below the build limit.
	 */
	public static Summary build(Plan p, Random rnd, int cx, int top, int cz, int r) {
		return build(p, rnd, cx, top, cz, r, -1);
	}

	/** As {@link #build(Plan, Random, int, int, int, int)}, to a given plan (-1: one drawn at random). */
	public static Summary build(Plan p, Random rnd, int cx, int top, int cz, int r, int plan) {
		return build(p, rnd, cx, top, cz, r, plan, -1);
	}

	/**
	 * As {@link #build(Plan, Random, int, int, int, int, int)}, in a given scheme of materials too ({@link #TWILIGHT},
	 * {@link #BONE}, {@link #GLOAM} or {@link #RUIN}; -1: the one drawn).
	 */
	public static Summary build(Plan p, Random rnd, int cx, int top, int cz, int r, int plan, int scheme) {
		Scheme sc = scheme(rnd, scheme);
		int schemeId = sc.ruin ? 3 : sc.band == silked() ? 2 : sc.floor == palePol() ? 1 : 0;
		Frame base = new Frame(p, cx, top + 1, cz, rnd.nextInt(4));
		int R = Math.max(20, r - 5);
		int maxH = Math.min(128, 236 - top);
		int layout = plan >= 0 ? plan % 5 : rnd.nextInt(5);
		// Its materials are planned over its own height, from a stream of their own (so a change to how stone is
		// chosen moves no tower), drawn from where it stands
		long matSeed = cx * 341873128712L ^ cz * 132897987541L ^ top * 0x9E3779B97F4A7C15L;
		sc.ctx = MaterialContext.of(matSeed, top + 1, top + 1 + (layout == ZIGGURAT ? (int) (maxH * 0.6) : maxH), sc.wear);
		int crown = crownFor(rnd);
		int[] towers = {0};
		int height = switch (layout) {
			case CATHEDRAL -> cathedral(base, sc, rnd, R, maxH, crown, towers, top);
			case CITADEL -> citadel(base, sc, rnd, R, maxH, crown, towers, top);
			case CLUSTER -> cluster(base, sc, rnd, R, maxH, crown, towers, top);
			case ZIGGURAT -> ziggurat(base, sc, rnd, R, maxH, crown, towers, top);
			default -> gateway(base, sc, rnd, R, maxH, crown, towers, top);
		};
		socle(p, sc, top + 1);
		// What time has done to it, drawn from a stream of its own (from where it stands, like its stone)
		Random age = new Random(matSeed ^ 0x5EA5_0A6EL);
		Ruin.apply(p, age, Ruin.condition(age, sc.ruin), top);
		return new Summary(layout, schemeId, crown, towers[0], height);
	}

	/**
	 * The plinth round the foot of every wall: a course of stairs a block out, sloping away from the wall, wherever a
	 * wall (five blocks high at least, so not a lamp post) stands on the ground with open ground before it. Every part
	 * of the building, towers, halls, podiums and piers, gets the same, so its foot reads as one.
	 */
	static void socle(Plan p, Scheme sc, int y) {
		List<int[]> steps = new ArrayList<>();
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		for (long k : p.blocks.keySet()) {
			if (net.minecraft.core.BlockPos.getY(k) != y) {
				continue;
			}
			int x = net.minecraft.core.BlockPos.getX(k), z = net.minecraft.core.BlockPos.getZ(k);
			if (!p.has(x, y + 1, z) || !p.has(x, y + 2, z) || !p.has(x, y + 3, z) || !p.has(x, y + 4, z)) {
				continue;
			}
			for (Direction d : dirs) {
				int nx = x + d.getStepX(), nz = z + d.getStepZ();
				if (!p.has(nx, y, nz) && !p.has(nx, y + 1, nz) && !p.has(nx, y + 2, nz)) {
					steps.add(new int[] {nx, nz, d.getOpposite().get2DDataValue()});
				}
			}
		}
		for (int[] st : steps) {
			p.soft(st[0], y, st[1], stairs(sc.plinth, Direction.from2DDataValue(st[2]), false));
		}
	}

	/**
	 * A cathedral: a great crossing tower, a nave before it ending in a portal, transepts either side, an apse behind.
	 * They make one building: the crossing tower is square where they meet it, and its first stage rises past their
	 * ridges, so every roof runs into its walls; towers at the west front stand engaged in it, their first ledge on the
	 * line of the nave's eaves (a pair, a pair of two heights, or one alone).
	 */
	private static int cathedral(Frame b, Scheme sc, Random rnd, int R, int maxH, int crown, int[] n, int top) {
		int H = 8 + rnd.nextInt(3);
		int w = 5 + rnd.nextInt(2);
		boolean aisles = rnd.nextBoolean() && w + 4 + 3 < R;
		int hallH = 15 + rnd.nextInt(5);
		int z1 = Math.min(R - 5, H + 12 + rnd.nextInt(6));
		// (its form is drawn all the same, so the rest of it is what its seed gives)
		formFor(rnd, true);
		Tower t = tower(b, sc, rnd, 0, 0, H, maxH, true, crown, Form.SQUARE, hallH + w + 3);
		n[0]++;
		hall(b, sc, rnd, H + 1, z1, w, hallH, true, aisles);
		// transepts, left and right, ending in closed gables
		int tl = Math.min(R - 3, H + 6 + rnd.nextInt(4));
		for (int side = 1; side <= 3; side += 2) {
			Frame g = new Frame(b.p, b.wx(0, 0), b.oy, b.wz(0, 0), b.rot + side);
			hall(g, sc, rnd, H + 1, tl, w - 1, hallH - 2, false, false);
			doorway(g, 0, H, 3, 0);
		}
		// the apse behind the tower
		Frame back = new Frame(b.p, b.wx(0, 0), b.oy, b.wz(0, 0), b.rot + 2);
		apse(back, sc, rnd, H + 1, Math.min(w + 1, R - H - 3), hallH - 3);
		doorway(back, 0, H, 3, 0);
		// towers at the west front, often
		if (rnd.nextInt(3) > 0) {
			int th = 3 + rnd.nextInt(2);
			int aw = aisles ? 3 : 0;
			int tx = w + aw + th + 1, tz = z1 - th;
			int small = rnd.nextInt(10) < 6 ? SPIRE : crownFor(rnd);
			formFor(rnd, false);
			int tallness = Math.min(maxH - 10, 40 + rnd.nextInt(20));
			// the pair grown from one seed, so they match
			long seed = rnd.nextLong();
			int kind = rnd.nextInt(4), lone = rnd.nextBoolean() ? 1 : -1;
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				if (kind == 0 && sgn != lone) {
					continue;
				}
				int tall = kind == 1 && sgn > 0 ? tallness * 3 / 4 : tallness;
				tower(b, sc, new Random(seed), sgn * tx, tz, th, tall, false, small, Form.SQUARE, hallH);
				n[0]++;
			}
		}
		forecourt(b.p, b, rnd, top, z1 + 5, Math.max(4, R - z1 - 3), 7);
		return t.topY;
	}

	/** A citadel: a keep inside a curtain wall, towers along the wall, a gatehouse at the front, bridges to the walk. */
	private static int citadel(Frame b, Scheme sc, Random rnd, int R, int maxH, int crown, int[] n, int top) {
		Form ring = rnd.nextBoolean() ? Form.OCTAGON : Form.ROUND;
		int Rc = (int) ((R - 4) / (ring == Form.OCTAGON ? 1.1 : 1.0));
		int hw = 9 + rnd.nextInt(4);
		Tower keep = tower(b, sc, rnd, 0, 0, 7 + rnd.nextInt(3), maxH, true, crown, formFor(rnd, true));
		n[0]++;
		for (int x = -Rc; x <= Rc; x++) {
			for (int z = -Rc; z <= Rc; z++) {
				boolean outer = rim(ring, x, z, Rc), wall = outer || rim(ring, x, z, Rc - 1);
				if (!wall) {
					continue;
				}
				boolean gate = z > 0 && Math.abs(x) <= 1;
				for (int ly = 0; ly <= hw; ly++) {
					if (gate && ly >= 1 && ly <= 5) {
						if (ly == 5 && outer) {
							b.set(x, ly, z, mc("iron_bars", sc.pier));
						} else {
							b.clear(x, ly, z);
						}
						continue;
					}
					b.set(x, ly, z, ly == 0 ? sc.floorAlt : ly == hw ? sc.floor : sc.masonryAt(b, x, ly, z, 0));
				}
				if (outer) {
					// battlements on the outer edge; the inner edge is the walk
					b.set(x, hw + 1, z, sc.masonryAt(b, x, hw + 1, z, 0));
					if ((x + z & 1) == 0) {
						b.set(x, hw + 2, z, sc.masonryAt(b, x, hw + 2, z, 0));
					}
				}
			}
		}
		// Towers on the wall: on the axes and (often) between them
		int count = rnd.nextBoolean() ? 8 : 4;
		Form tf = formFor(rnd, false);
		int tc = crownFor(rnd);
		long seed = rnd.nextLong();
		for (int i = 0; i < count; i++) {
			double a = i * Math.PI * 2 / count + Math.PI / 2;
			int tx = (int) Math.round(Math.cos(a) * Rc), tz = (int) Math.round(Math.sin(a) * Rc);
			if (i == 0) {
				// the front: a gatehouse, two towers with the gate between them
				for (int s = -1; s <= 1; s += 2) {
					tower(b, sc, new Random(seed + 7), s * 5, tz, 3, hw + 18, false, SPIRE, Form.ROUND);
					n[0]++;
				}
				continue;
			}
			tower(b, sc, new Random(seed + i), tx, tz, 3 + (count == 4 ? 1 : 0), hw + 14 + rnd.nextInt(10), false, tc, tf);
			n[0]++;
		}
		// Bridges from the keep out to the wall walk, on two sides
		int kh = halfAt(keep, hw);
		if (kh > 0) {
			landing(keep, hw, true);
			for (int side = 1; side <= 3; side += 2) {
				Frame g = new Frame(b.p, b.wx(0, 0), b.oy, b.wz(0, 0), b.rot + side);
				bridge(g, sc, 0, hw, kh + 1, Rc - 2);
				doorway(g, 0, kh, 2, hw);
			}
		}
		// The courtyard, paved between keep and wall
		BlockState[] path = pathGrad();
		for (int x = -Rc + 2; x <= Rc - 2; x++) {
			for (int z = -Rc + 2; z <= Rc - 2; z++) {
				if (inside(ring, x, z, Rc - 2) && !b.has(x, 0, z)) {
					double tt = Math.sqrt(x * x + z * z) / Rc;
					b.set(x, 0, z, weather(grad(path, tt, rnd), rnd, 0.12));
				}
			}
		}
		return keep.topY;
	}

	/**
	 * A cluster: a great spire, and round it towers of every height, all on one podium. Low wings on the podium join
	 * each tower to the spire (their roofs under the towers' first ledges), sky bridges join them higher up, and a stair
	 * climbs to the podium at the front.
	 */
	private static int cluster(Frame b, Scheme sc, Random rnd, int R, int maxH, int crown, int[] n, int top) {
		int H = 8 + rnd.nextInt(3);
		int ph = 2 + rnd.nextInt(2);
		int sats = 3 + rnd.nextInt(3);
		// to the sides and behind, leaving the front to the stair; a fourth and fifth beside the first two
		int[] sides = {1, 3, 2, 1, 3};
		// where each tower stands, in the frame turned to its side: its side, across, out, half-size and height
		int[][] sat = new int[sats][];
		for (int i = 0; i < sats; i++) {
			int th = 3 + rnd.nextInt(2);
			int off = i >= 3 ? (i == 3 ? 1 : -1) * (H - 1) : rnd.nextInt(5) - 2;
			int d = Math.min(R - th - 4, H + 7 + rnd.nextInt(6));
			int tall = Math.min(maxH - 10 - ph, 22 + rnd.nextInt(30));
			sat[i] = new int[] {sides[i], off, d, th, tall};
		}
		int front = podium(b, sc, ph, H, sat);
		Frame up = new Frame(b.p, b.wx(0, 0), b.oy + ph, b.wz(0, 0), b.rot);
		formFor(rnd, true);
		Tower main = tower(up, sc, rnd, 0, 0, H, maxH - ph, true, crown, Form.SQUARE);
		n[0]++;
		for (int[] t : sat) {
			Frame g = new Frame(b.p, up.wx(0, 0), up.oy, up.wz(0, 0), up.rot + t[0]);
			int off = t[1], d = t[2], th = t[3];
			int wingH = 6 + rnd.nextInt(3);
			boolean wing = Math.abs(off) <= H - 3 && d - th - 1 > H + 2;
			if (wing) {
				Frame wf = new Frame(b.p, g.wx(off, 0), g.oy, g.wz(off, 0), g.rot);
				hall(wf, sc, rnd, H + 1, d - th - 1, 2, wingH, false, false);
			}
			formFor(rnd, false);
			Tower s = tower(g, sc, rnd, off, d, th, t[4], false, crownFor(rnd), Form.SQUARE, wingH + 5);
			n[0]++;
			if (wing) {
				// doors from the wing into the spire and into the tower
				doorway(g, off, H, 2, 0);
				doorway(g, off, d - th + 1, 3, 0);
			}
			// a bridge at one of the spire's floors that the tower still reaches
			int by = -1;
			for (int[] fl : main.floors) {
				if (fl[0] >= 8 && fl[0] < s.topY - 4 && halfAt(s, fl[0]) >= 2 && fl[0] > wingH + 6) {
					by = fl[0];
				}
			}
			if (by < 0) {
				continue;
			}
			int mh = halfAt(main, by), sh = halfAt(s, by);
			if (Math.abs(off) > mh - 2) {
				continue;
			}
			bridge(g, sc, off, by, mh + 1, d - sh - 1);
			doorway(g, off, mh, 2, by);
			doorway(g, off, d - sh + 1, 2, by);
			landing(s, by, false);
		}
		// the stair up to the podium at the front
		for (int k = 0; k < ph; k++) {
			int z = front + ph - k;
			for (int x = -2; x <= 2; x++) {
				b.set(x, k, z, stairs(sc.corbel, Direction.NORTH, false));
				for (int below = k - 1; below >= 0; below--) {
					b.set(x, below, z, sc.masonryAt(b, x, below, z, 0));
				}
			}
		}
		forecourt(b.p, b, rnd, top, front + ph + 1, Math.max(4, R - front - ph - 2), 5);
		return ph + main.topY;
	}

	/** Where a point of a frame turned {@code r} quarter turns from a base frame lies in the base frame: across, then out. */
	private static int[] turned(int r, int x, int z) {
		return switch (r & 3) {
			case 1 -> new int[] {-z, x};
			case 2 -> new int[] {-x, -z};
			case 3 -> new int[] {z, -x};
			default -> new int[] {x, z};
		};
	}

	/**
	 * The podium of a cluster, {@code ph} high: round the spire (half-size {@code h}) and each tower ({@code sat}: side,
	 * across, out, half-size), and in a band from the spire out to each, its edge coped in trim. Returns how far it
	 * reaches out at the front, on the axis.
	 */
	private static int podium(Frame b, Scheme sc, int ph, int h, int[][] sat) {
		int reach = h + 4;
		for (int[] t : sat) {
			reach = Math.max(reach, t[2] + t[3] + 4);
		}
		boolean[][] in = new boolean[reach * 2 + 1][reach * 2 + 1];
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				boolean inside = Math.max(Math.abs(x), Math.abs(z)) <= h + 3;
				for (int[] t : sat) {
					int[] c = turned(t[0], t[1], t[2]);
					inside |= Math.max(Math.abs(x - c[0]), Math.abs(z - c[1])) <= t[3] + 2;
					// the band out to it, in the tower's own frame
					int[] l = turned(4 - t[0], x, z);
					inside |= l[1] >= 0 && l[1] <= t[2] && Math.abs(l[0] - t[1]) <= 4;
				}
				in[x + reach][z + reach] = inside;
			}
		}
		int front = h + 3;
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (!in[x + reach][z + reach]) {
					continue;
				}
				if (x == 0) {
					front = Math.max(front, z);
				}
				boolean edge = x == -reach || x == reach || z == -reach || z == reach || !in[x + reach + 1][z + reach] || !in[x + reach - 1][z + reach]
						|| !in[x + reach][z + reach + 1] || !in[x + reach][z + reach - 1];
				for (int y = 0; y < ph; y++) {
					BlockState s;
					if (y == ph - 1) {
						s = edge ? sc.trimAt(b, x, y, z, 0) : (x + z & 1) == 0 ? sc.floor : sc.floorAlt;
					} else {
						s = sc.masonryAt(b, x, y, z, -0.1);
					}
					b.set(x, y, z, s);
				}
			}
		}
		return front;
	}

	/** A ziggurat: stages of banded masonry stepping in, a great stair up its front, a temple tower on the top. */
	private static int ziggurat(Frame b, Scheme sc, Random rnd, int R, int maxH, int crown, int[] n, int top) {
		Form form = rnd.nextBoolean() ? Form.SQUARE : Form.OCTAGON;
		int z0 = (int) ((R - 2) / (form == Form.SQUARE ? 1.42 : 1.1));
		int stages = 3 + rnd.nextInt(2);
		int step = Math.max(3, Math.min(4, (z0 - 8) / stages));
		Tower w = new Tower();
		w.f = b;
		w.sc = sc;
		w.rnd = rnd;
		int y = 0, h = z0, lastHalf = z0;
		for (int i = 0; i < stages && h >= 7; i++) {
			// as tall as it steps back, so the stair up the front climbs one block for every block it runs
			int sh = step - 1 + rnd.nextInt(2);
			for (int x = -h; x <= h; x++) {
				for (int z = -h; z <= h; z++) {
					if (!inside(form, x, z, h)) {
						continue;
					}
					boolean edge = rim(form, x, z, h);
					for (int ly = 0; ly <= sh; ly++) {
						int yy = y + ly;
						if (edge) {
							boolean pale = (yy / 2 & 1) == 0;
							b.set(x, yy, z, ly == sh ? sc.band : sc.masonryAt(b, x, yy, z, pale ? 0.3 : -0.2));
						} else if (ly == sh) {
							b.set(x, yy, z, (x + z & 1) == 0 ? sc.floor : sc.floorAlt);
						} else if (rim(form, x, z, h - 1)) {
							b.set(x, yy, z, sc.masonryAt(b, x, yy, z, 0));
						}
					}
				}
			}
			// lamps on the stage's corners
			int c = form == Form.SQUARE ? h - 1 : (int) Math.round(h * 0.68);
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					b.set(sx * c, y + sh + 1, sz * c, sc.pier);
					b.set(sx * c, y + sh + 2, sz * c, sc.pier);
					b.set(sx * c, y + sh + 3, sz * c, rnd.nextBoolean() ? lantern() : soulLantern());
				}
			}
			lastHalf = h;
			y += sh + 1;
			h -= step;
		}
		int topY = y;
		// The great stair: it starts out in front and climbs one in one to the top stage's edge, walled either side
		int zs = Math.min(R - 1, lastHalf + topY);
		for (int ly = 1; ly <= topY; ly++) {
			int z = zs - (ly - 1);
			for (int x = -2; x <= 2; x++) {
				b.set(x, ly - 1, z, stairs(sc.corbel, Direction.NORTH, false));
				for (int k = 0; k <= 3; k++) {
					b.clear(x, ly + k, z);
				}
				for (int below = ly - 2; below >= 0 && !b.has(x, below, z); below--) {
					b.set(x, below, z, sc.masonryAt(b, x, below, z, 0));
				}
			}
			for (int s = -1; s <= 1; s += 2) {
				for (int below = ly - 1; below >= 0 && !b.has(s * 3, below, z); below--) {
					b.set(s * 3, below, z, sc.masonryAt(b, s * 3, below, z, 0));
				}
				b.set(s * 3, ly, z, sc.rail);
			}
		}
		// the temple on the top: a tower of its own
		Frame temple = new Frame(b.p, b.wx(0, 0), b.oy + topY, b.wz(0, 0), b.rot);
		Tower t = tower(temple, sc, rnd, 0, 0, Math.max(5, Math.min(8, lastHalf - 3)), maxH - topY, true, crown, formFor(rnd, false));
		n[0]++;
		forecourt(b.p, b, rnd, top, zs + 1, Math.max(2, R - zs - 1), 4);
		return topY + t.topY;
	}

	/** A gateway: twin towers on a podium, spanned high up by an arch with a deck over it; sometimes a third behind. */
	private static int gateway(Frame b, Scheme sc, Random rnd, int R, int maxH, int crown, int[] n, int top) {
		int ph = 3;
		int pw = (int) ((R - 2) / 1.42);
		// the podium, in three steps
		for (int step = 0; step < ph; step++) {
			int h = pw - step * 2;
			for (int x = -h; x <= h; x++) {
				for (int z = -h; z <= h; z++) {
					boolean edge = Math.abs(x) == h || Math.abs(z) == h;
					if (edge && !(Math.abs(x) == h && Math.abs(z) == h)) {
						b.set(x, step, z, stairs(sc.corbel, out(x, z).getOpposite(), false));
					} else {
						b.set(x, step, z, edge ? sc.trim(0, rnd) : (x + z & 1) == 0 ? sc.floor : sc.floorAlt);
					}
				}
			}
		}
		Frame up = new Frame(b.p, b.wx(0, 0), b.oy + ph, b.wz(0, 0), b.rot);
		// slender towers well apart, so the arch between them reads as a gate
		int th = 4 + rnd.nextInt(2);
		int D = Math.min(pw - th - 2, 12 + rnd.nextInt(3));
		int tallness = Math.min(maxH - ph, 60 + rnd.nextInt(30));
		long seed = rnd.nextLong();
		Form tf = formFor(rnd, true);
		Tower left = tower(up, sc, new Random(seed), -D, 0, th, tallness, true, crown, tf);
		tower(up, sc, new Random(seed), D, 0, th, tallness, true, crown, tf);
		n[0] += 2;
		// The arch: a deck between the towers at half their height, its underside a half-ellipse
		int deck = Math.max(14, left.topY / 2);
		int span = D - th - 1;
		int rise = Math.max(4, Math.min(span, 9));
		for (int x = -span; x <= span; x++) {
			double e = Math.sqrt(Math.max(0, 1 - (double) (x * x) / ((span + 0.5) * (span + 0.5))));
			int under = deck - 3 - (int) Math.round(rise * (1 - e));
			for (int z = -2; z <= 2; z++) {
				for (int y = under; y <= deck; y++) {
					up.set(x, y, z, y == deck ? (Math.abs(z) == 2 ? sc.trim(0.6, rnd) : sc.floor) : sc.masonryAt(up, x, y, z, 0));
				}
				up.set(x, under - 1, z, stairs(sc.corbel, x < 0 ? Direction.WEST : Direction.EAST, true));
				if (Math.abs(z) == 2) {
					up.set(x, deck + 1, z, sc.rail);
				}
			}
			if (x % 4 == 0) {
				up.set(x, under - 2, 0, chain());
				up.set(x, under - 3, 0, hanging(lantern()));
			}
		}
		// a halo turning in the arch, a crystal at its heart
		int hr = Math.min(span - 2, Math.min(6, (deck - 6) / 2 - 1));
		if (hr >= 3) {
			net.minecraft.core.BlockPos c = up.pos(0, deck - 5 - hr, 0);
			ring(b.p, rnd, c.getX(), c.getY(), c.getZ(), hr, Math.PI / 2, b.rot * Math.PI / 2 + (rnd.nextDouble() - 0.5) * 0.2, 1);
			crystal(b.p, rnd, c.getX(), c.getY(), c.getZ(), 2);
		}
		// sometimes a third tower behind, taller, joined to the deck
		if (rnd.nextBoolean()) {
			Tower back = tower(up, sc, rnd, 0, -D, th + 1, Math.min(maxH - ph, tallness + 15), true, crownFor(rnd), formFor(rnd, true));
			n[0]++;
			int bh = halfAt(back, deck);
			if (bh > 0) {
				bridge(up, sc, 0, deck, -D + bh + 1, -3);
				Frame toward = new Frame(b.p, up.wx(0, -D), up.oy, up.wz(0, -D), up.rot);
				doorway(toward, 0, bh, 2, deck);
				landing(back, deck, true);
			}
			forecourt(b.p, b, rnd, top, pw + 1, Math.max(3, R - pw - 2), 4);
			return ph + Math.max(left.topY, back.topY);
		}
		forecourt(b.p, b, rnd, top, pw + 1, Math.max(3, R - pw - 2), 4);
		return ph + left.topY;
	}
}
