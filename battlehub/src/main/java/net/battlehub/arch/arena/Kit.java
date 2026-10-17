package net.battlehub.arch.arena;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.geom.ArchCurve;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.RoofField;

/**
 * The arena's own parts, drawn in roles so every theme dresses them: lamps, trees and scenery, the forge over a
 * generator, the market stall of a shop, a lookout platform, the gate behind a bed, the ruined pavilion of a
 * diamond island and the colonnade in the middle. Each part is drawn round a local origin; where it goes (and
 * what has to stay free for play) is decided by the map generator.
 */
final class Kit {
	private Kit() {}

	// ------------------------------------------------------------------ lamps

	/** A lamp standing on something. */
	static String lamp(Dress d) {
		return d.lamp;
	}

	/** A lamp hanging from the block above it. */
	static String hanging(Dress d) {
		return switch (d.lamp) {
			case "soul_lantern" -> "soul_lantern[hanging=true]";
			case "end_rod" -> "end_rod[facing=down]";
			default -> "lantern[hanging=true]";
		};
	}

	/** A lamp post {@code h} high standing on y. */
	static void post(Ctx c, Dress d, int x, int y, int z, int h) {
		c.set(x, y, z, Cell.of(Slot.TRIM, Cell.DETAIL));
		for (int k = 1; k < h; k++) c.set(x, y + k, z, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
		c.set(x, y + h, z, Cell.literal(lamp(d), Cell.DETAIL));
	}

	static Cell team(String colour, String what, int prio) {
		return Cell.literal(colour + "_" + what, prio);
	}

	// ------------------------------------------------------------------ trees and scenery

	/** A tree of the theme's kind with its trunk foot at (x, y, z); scenery instead for themes without trees. */
	static void tree(Ctx c, Dress d, Rng r, int x, int y, int z, int h) {
		if (d.trees.equals("none")) {
			feature(c, d, r, x, y, z, 1);
		} else {
			tree(c, d.trees, r, x, y, z, h);
		}
	}

	static void tree(Ctx c, String kind, Rng r, int x, int y, int z, int h) {
		switch (kind) {
			case "conifer" -> {
				int top = y + h + 1;
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.log(Slot.TIMBER, 'y', Cell.DETAIL));
				for (int yy = y + 2; yy <= top; yy++) {
					int k = top - yy;
					int rad = k == 0 ? 0 : k <= 2 ? 1 : k % 2 == 1 ? 2 : 1;
					for (int dx = -rad; dx <= rad; dx++) {
						for (int dz = -rad; dz <= rad; dz++) {
							if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
							c.setIfEmpty(x + dx, yy, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL));
						}
					}
				}
				c.setIfEmpty(x, top + 1, z, Cell.of(Slot.LEAVES, Cell.DETAIL));
			}
			case "fungus" -> {
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.log(Slot.TIMBER, 'y', Cell.DETAIL));
				int cy = y + h;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int ad = Math.abs(dx) + Math.abs(dz);
						if (ad <= 3) c.setIfEmpty(x + dx, cy, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL));
						if (ad <= 1) c.setIfEmpty(x + dx, cy + 1, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL));
						if (ad == 3 && r.chance(0.5)) c.setIfEmpty(x + dx, cy - 1, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL));
					}
				}
			}
			case "mushroom" -> {
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.literal("mushroom_stem", Cell.DETAIL));
				int cy = y + h;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
						if (!corner) c.setIfEmpty(x + dx, cy, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL).variant(1));
						if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) c.setIfEmpty(x + dx, cy + 1, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL).variant(1));
					}
				}
			}
			case "palm" -> {
				// a trunk leaning one way, fronds drooping out in four directions
				int lean = r.chance(0.5) ? 1 : -1;
				int tx = x;
				for (int k = 0; k < h; k++) {
					if (k == h / 2) tx += lean;
					c.set(tx, y + k, z, Cell.log(Slot.TIMBER, 'y', Cell.DETAIL));
				}
				int cy = y + h;
				c.setIfEmpty(tx, cy, z, Cell.of(Slot.LEAVES, Cell.DETAIL));
				for (Dir dir : Dir.values()) {
					for (int k = 1; k <= 3; k++) {
						c.setIfEmpty(tx + dir.dx * k, cy - (k == 3 ? 1 : 0), z + dir.dz * k, Cell.of(Slot.LEAVES, Cell.DETAIL));
					}
					c.setIfEmpty(tx + dir.dx + dir.right().dx, cy, z + dir.dz + dir.right().dz, Cell.of(Slot.LEAVES, Cell.DETAIL));
				}
			}
			default -> {
				// round, jungle and cherry crowns: a squashed ball, wider for cherry and jungle
				boolean wide = kind.equals("cherry") || kind.equals("jungle");
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.log(Slot.TIMBER, 'y', Cell.DETAIL));
				double cr = wide ? 2.9 : 2.3;
				double squash = wide ? 2.2 : 1.6;
				int cy = y + h - (wide ? 0 : 1);
				int ir = (int) Math.ceil(cr);
				for (int dx = -ir; dx <= ir; dx++) {
					for (int dy = -2; dy <= 2; dy++) {
						for (int dz = -ir; dz <= ir; dz++) {
							double dd = Math.sqrt(dx * dx + dy * dy * squash + dz * dz);
							if (dd > cr + 0.2) continue;
							if (dd > cr - 0.8 && r.next() < 0.25) continue;
							c.setIfEmpty(x + dx, cy + dy, z + dz, Cell.of(Slot.LEAVES, Cell.DETAIL));
						}
					}
				}
			}
		}
	}

	/** A small piece of the theme's scenery standing on y ({@code size} 1 small, 2 large). */
	static void feature(Ctx c, Dress d, Rng r, int x, int y, int z, int size) {
		switch (d.feature) {
			case "basalt" -> {
				int h = 3 + size + r.nextInt(2);
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.literal("basalt", Cell.DETAIL));
				if (size > 1) {
					for (Dir dir : Dir.values()) {
						int hh = 1 + r.nextInt(h - 1);
						for (int k = 0; k < hh; k++) c.set(x + dir.dx, y + k, z + dir.dz, Cell.literal("basalt", Cell.DETAIL));
					}
				}
				c.set(x, y + h, z, Cell.literal(d.lamp, Cell.DETAIL));
			}
			case "spike" -> spike(c, x, y, z, 3 + 2 * size + r.nextInt(2), size > 1 ? 1.6 : 1.1, r);
			case "cactus" -> {
				int h = 2 + r.nextInt(2);
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.literal("cactus", Cell.DETAIL));
				if (size > 1) c.set(x + 1, y, z + 1, Cell.literal("dead_bush", Cell.DETAIL));
			}
			case "mesa" -> {
				int h = 2 + size * 2;
				for (int k = 0; k < h; k++) {
					double rad = (size > 1 ? 1.6 : 1.0) - k * 0.1;
					c.disc(x, y + k, z, rad, Cell.of(Slot.ROCK, Cell.DETAIL));
				}
			}
			case "obelisk" -> {
				int h = 3 + size * 2;
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.literal(k == h - 1 ? "crying_obsidian" : "obsidian", Cell.DETAIL));
				c.set(x, y + h, z, Cell.literal("end_rod[facing=up]", Cell.DETAIL));
			}
			case "spire" -> {
				int h = 3 + size * 2;
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.of(k < h / 2 ? Slot.WALL : Slot.TRIM, Cell.DETAIL));
				if (size > 1) {
					for (Dir dir : Dir.values()) c.set(x + dir.dx, y, z + dir.dz, Cell.stairs(Slot.TRIM, dir.back(), false, Cell.DETAIL));
				}
				c.set(x, y + h, z, Cell.literal(d.lamp, Cell.DETAIL));
			}
			case "coral" -> {
				int h = 2 + size * 2;
				for (int k = 0; k < h; k++) c.set(x, y + k, z, Cell.of(k % 3 == 2 ? Slot.WALL_ALT : Slot.WALL, Cell.DETAIL));
				c.set(x, y + h, z, Cell.literal("sea_lantern", Cell.DETAIL));
			}
			case "mushroom" -> tree(c, "mushroom", r, x, y, z, 2 + size);
			case "cloud" -> {
				c.set(x, y, z, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.set(x, y + 1, z, Cell.literal("decorated_pot", Cell.DETAIL));
			}
			default -> {
				double rr = size > 1 ? 1.7 : 1.15;
				int ir = (int) Math.ceil(rr);
				for (int dx = -ir; dx <= ir; dx++) {
					for (int dy = 0; dy <= ir; dy++) {
						for (int dz = -ir; dz <= ir; dz++) {
							if (dx * dx + dy * dy * 1.7 + dz * dz <= rr * rr) {
								c.set(x + dx, y + dy, z + dz, Cell.of(r.chance(0.3) ? Slot.RUBBLE : Slot.ROCK, Cell.DETAIL));
							}
						}
					}
				}
			}
		}
	}

	static void spike(Ctx c, int x, int y, int z, int h, double r0, Rng r) {
		for (int k = 0; k < h; k++) {
			double rad = Math.max(0, r0 * (1.0 - (double) k / h));
			int ir = (int) Math.floor(rad);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz <= rad * rad + 0.3) {
						c.set(x + dx, y + k, z + dz, Cell.literal(r.chance(0.2) ? "blue_ice" : "packed_ice", Cell.DETAIL));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ Bed Wars: the team island

	/**
	 * The forge over a team generator at (0, gz): four posts and a low canopy, an anvil and a blast furnace on its
	 * sides, lamps hanging under it. It covers the generator from arrows without closing it in.
	 */
	static void forge(Ctx c, Dress d, int gz) {
		for (int sx = -3; sx <= 3; sx += 6) {
			for (int sz = gz - 2; sz <= gz + 2; sz += 4) {
				for (int y = 1; y <= 4; y++) c.set(sx, y, sz, Cell.of(Slot.PILLAR, Cell.BODY));
			}
		}
		// a low hipped canopy: slopes on all four sides, a flat top two blocks up
		RoofField f = new RoofField();
		for (int x = -4; x <= 4; x++) {
			for (int z = gz - 3; z <= gz + 3; z++) {
				int in = Math.min(4 - Math.abs(x), 3 - Math.abs(z - gz));
				f.raise(x, z, 5 + Math.min(in, 2), 5);
			}
		}
		f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
		// a beam along each side under the eaves, from post to post
		for (int sx = -3; sx <= 3; sx += 6) {
			for (int z = gz - 1; z <= gz + 1; z++) c.set(sx, 4, z, Cell.log(Slot.TIMBER, 'z', Cell.BODY));
		}
		c.decor(-3, 1, gz, Cell.literal("anvil[facing=north]", Cell.DETAIL));
		c.decor(3, 1, gz, Cell.literal("blast_furnace[facing=west]", Cell.DETAIL));
		c.decor(-2, 6, gz, Cell.literal(hanging(d), Cell.DETAIL));
		c.decor(2, 6, gz, Cell.literal(hanging(d), Cell.DETAIL));
	}

	/**
	 * The market stall round the shopkeeper at (sx, 1, 0): a plank floor, four posts and a striped awning in the
	 * team colour, crates behind the counter, banners on the front posts.
	 */
	static void stall(Ctx c, Dress d, String colour, int sx) {
		int x0 = sx - 1, x1 = sx + 2;
		for (int x = x0; x <= x1; x++) {
			for (int z = -2; z <= 2; z++) c.set(x, 0, z, Cell.of(Slot.FLOOR, Cell.DETAIL).flag(Cell.KEEP));
		}
		for (int x = x0; x <= x1; x += x1 - x0) {
			for (int z = -2; z <= 2; z += 4) {
				for (int y = 1; y <= 3; y++) c.set(x, y, z, Cell.log(Slot.TIMBER, 'y', Cell.BODY));
			}
		}
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = -2; z <= 2; z++) {
				String stripe = (z & 1) == 0 ? colour + "_wool" : "white_wool";
				c.set(x, 4, z, Cell.literal(stripe, Cell.DETAIL));
				if (x > x0 - 1 && x < x1 + 1 && Math.abs(z) <= 1) c.set(x, 5, z, Cell.literal(stripe, Cell.DETAIL));
			}
		}
		c.decor(x1, 1, -1, Cell.literal("barrel[facing=up]", Cell.DETAIL));
		c.decor(x1, 1, 1, Cell.literal("barrel[facing=up]", Cell.DETAIL));
		c.decor(x1, 1, 0, Cell.literal("hay_block", Cell.DETAIL));
		c.decor(x1, 2, 1, Cell.literal(lamp(d), Cell.DETAIL));
		c.decor(x0 - 1, 3, -2, team(colour, "wall_banner[facing=west]", Cell.DETAIL));
		c.decor(x0 - 1, 3, 2, team(colour, "wall_banner[facing=west]", Cell.DETAIL));
	}

	/**
	 * A lookout on the other side of the island: a platform four up on posts (x0..x0+4, z -2..2) with a
	 * crenellated parapet, reached by a straight stair along its inner side. High ground over the bridges.
	 */
	static void lookout(Ctx c, Dress d, String colour, int x0) {
		int x1 = x0 + 4;
		for (int x = x0; x <= x1; x += 4) {
			for (int z = -2; z <= 2; z += 4) {
				for (int y = 1; y <= 3; y++) c.set(x, y, z, Cell.of(Slot.PILLAR, Cell.BODY));
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean edge = x == x0 || x == x1 || Math.abs(z) == 2;
				c.set(x, 4, z, Cell.of(edge ? Slot.BAND : Slot.FLOOR, Cell.BODY));
				if (!edge) continue;
				// the parapet, open where the stair arrives
				if (x == x1 && Math.abs(z) <= 1) continue;
				boolean merlon = ((x + z) & 1) == 0 || (x == x0 || x == x1) && Math.abs(z) == 2;
				c.set(x, 5, z, merlon ? Cell.of(Slot.WALL, Cell.BODY) : Cell.slab(Slot.TRIM, false, Cell.BODY));
			}
		}
		// the stair: up towards -z along x1 + 1, each step on a solid foot
		int sx = x1 + 1;
		for (int k = 0; k < 4; k++) {
			int z = 2 - k;
			for (int y = 1; y < 1 + k; y++) c.set(sx, y, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
			c.set(sx, 1 + k, z, Cell.stairs(Slot.FOUNDATION, Dir.NORTH, false, Cell.FEATURE).flag(Cell.KEEP));
			for (int y = 2 + k; y <= 4 + k; y++) c.set(sx, y, z, Cell.air(Cell.OPEN));
		}
		for (int x = x0; x <= x1; x += 4) {
			for (int z = -2; z <= 2; z += 4) c.decor(x, 6, z, Cell.literal(lamp(d), Cell.DETAIL));
		}
		c.decor(x0 - 1, 4, 0, team(colour, "wall_banner[facing=west]", Cell.DETAIL));
		c.decor(x0 + 1, 1, -1, Cell.literal("barrel[facing=up]", Cell.DETAIL));
		c.decor(x0 + 1, 1, 1, Cell.literal("barrel[facing=up]", Cell.DETAIL));
		c.decor(x0 + 1, 1, 0, Cell.literal("hay_block", Cell.DETAIL));
	}

	/**
	 * The gate behind a bed at z = gz: two pillars on bases with capitals and a round arch seven wide, a coping
	 * with lamps on the pillars and team banners hanging from it. It frames the bed seen from the spawn and leaves
	 * every side of the bed open to build round (the arch springs above the bed's free zone).
	 */
	static void bedGate(Ctx c, Dress d, String colour, int gz) {
		int w = 7, jamb = 3;
		int[] h = ArchCurve.ROUND.heights(w);
		int rise = ArchCurve.ROUND.rise(w);
		int top = 1 + jamb + rise + 1;
		c.archRing(-3, 1 + jamb, h, 1, Slot.ARCH, gz, gz, Cell.BODY);
		for (int i = 0; i < w; i++) {
			for (int y = 1 + jamb + h[i] + 1; y <= top; y++) c.set(-3 + i, y, gz, Cell.of(Slot.WALL_ALT, Cell.BODY));
			for (int y = 1; y < 1 + jamb + h[i]; y++) c.set(-3 + i, y, gz, Cell.air(Cell.OPEN));
		}
		for (int x = -4; x <= 4; x += 8) {
			c.set(x, 1, gz, Cell.of(Slot.FOUNDATION, Cell.BODY));
			for (int y = 2; y < top; y++) c.set(x, y, gz, Cell.of(Slot.PILLAR, Cell.BODY));
			c.set(x, top, gz, Cell.of(Slot.TRIM, Cell.BODY));
			c.set(x, top + 1, gz, Cell.of(Slot.TRIM, Cell.BODY));
			c.set(x, top + 2, gz, Cell.literal(lamp(d), Cell.DETAIL));
			// buttress steps on the outer side
			int out = x < 0 ? -1 : 1;
			c.set(x + out, 1, gz, Cell.stairs(Slot.FOUNDATION, out < 0 ? Dir.EAST : Dir.WEST, false, Cell.BODY));
		}
		for (int x = -3; x <= 3; x++) c.set(x, top + 1, gz, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
		c.set(0, top + 1, gz, Cell.of(Slot.ACCENT, Cell.DETAIL));
		c.decor(-2, top - 1, gz + 1, team(colour, "wall_banner[facing=south]", Cell.DETAIL));
		c.decor(2, top - 1, gz + 1, team(colour, "wall_banner[facing=south]", Cell.DETAIL));
	}

	// ------------------------------------------------------------------ Bed Wars: diamond islands and the middle

	/**
	 * The ruined pavilion of a diamond island, the same on all four sides: pillars on the corners, broken lintels,
	 * knee-high cover beside each pillar and gaps three wide in the middle of every side.
	 */
	static void pavilion(Ctx c, Dress d) {
		for (int sx = -3; sx <= 3; sx += 6) {
			for (int sz = -3; sz <= 3; sz += 6) {
				c.set(sx, 1, sz, Cell.of(Slot.TRIM, Cell.BODY));
				for (int y = 2; y <= 4; y++) c.set(sx, y, sz, Cell.of(Slot.PILLAR, Cell.BODY));
				c.set(sx, 5, sz, Cell.of(Slot.TRIM, Cell.BODY));
				c.set(sx, 6, sz, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
			}
		}
		for (int s = -3; s <= 3; s += 6) {
			for (int t = -2; t <= 2; t += 4) {
				// lintel stubs and knee-high cover next to each pillar
				c.set(t, 5, s, Cell.of(Slot.ARCH, Cell.BODY));
				c.set(s, 5, t, Cell.of(Slot.ARCH, Cell.BODY));
				c.set(t, 1, s, Cell.of(Slot.WALL, Cell.BODY));
				c.set(s, 1, t, Cell.of(Slot.WALL, Cell.BODY));
				c.decor(t, 4, s, Cell.literal(hanging(d), Cell.DETAIL));
				c.decor(s, 4, t, Cell.literal(hanging(d), Cell.DETAIL));
			}
		}
	}

	/** The eight column positions of the middle's colonnade (between the axes and the diagonals). */
	static final int[][] COLUMNS = {{7, 3}, {3, 7}, {-3, 7}, {-7, 3}, {-7, -3}, {-3, -7}, {3, -7}, {7, -3}};

	/**
	 * The middle: a ring of eight columns carrying a round entablature, a compass inlaid in the floor, pedestals
	 * with lamps as cover between the generators and the columns, and a crystal floating high above.
	 */
	static void tholos(Ctx c, Dress d) {
		for (int[] p : COLUMNS) {
			c.set(p[0], 1, p[1], Cell.of(Slot.TRIM, Cell.BODY));
			for (int y = 2; y <= 5; y++) c.set(p[0], y, p[1], Cell.of(Slot.PILLAR, Cell.BODY));
			c.set(p[0], 6, p[1], Cell.of(Slot.TRIM, Cell.BODY));
		}
		for (int x = -9; x <= 9; x++) {
			for (int z = -9; z <= 9; z++) {
				double r = Math.hypot(x, z);
				if (r >= 6.5 && r <= 8.2) c.set(x, 7, z, Cell.of(Slot.BAND, Cell.BODY));
				if (r >= 7.4 && r <= 8.2) c.set(x, 8, z, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
				if (r >= 6.4 && r < 7.4) c.set(x, 8, z, Cell.slab(Slot.ROOF_EDGE, false, Cell.DETAIL));
			}
		}
		for (Dir dir : Dir.values()) c.decor(dir.dx * 7, 6, dir.dz * 7, Cell.literal(hanging(d), Cell.DETAIL));
		for (int sx = -4; sx <= 4; sx += 8) {
			for (int sz = -4; sz <= 4; sz += 8) {
				c.set(sx, 1, sz, Cell.of(Slot.WALL, Cell.BODY));
				c.set(sx, 2, sz, Cell.of(Slot.TRIM, Cell.BODY));
				c.set(sx, 3, sz, Cell.literal(lamp(d), Cell.DETAIL));
			}
		}
		int cy = 18;
		for (int y = cy; y <= cy + 2; y++) c.set(0, y, 0, Cell.of(Slot.MAGIC, Cell.DETAIL).flag(Cell.FLOAT));
		for (Dir dir : Dir.values()) c.set(dir.dx, cy + 1, dir.dz, Cell.of(Slot.MAGIC, Cell.DETAIL).flag(Cell.FLOAT));
		c.set(0, cy + 3, 0, Cell.of(Slot.LIGHT, Cell.DETAIL).flag(Cell.FLOAT));
		c.set(0, cy - 1, 0, Cell.of(Slot.LIGHT, Cell.DETAIL).flag(Cell.FLOAT));
	}
}
