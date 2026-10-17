package net.battlehub.arch.arena;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.geom.ArchCurve;
import net.battlehub.arch.mat.Connect;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Library;
import net.battlehub.arch.mod.RoofField;

/**
 * Cover for arena floors, from the architecture engine: an archway to fight through, a broken colonnade, a
 * monument on a stepped plinth, a roofed well, stacked crates and barrels, a market stall. Each piece stands on
 * y = 0 of its own frame within two blocks of its origin (a five by five footprint), is dressed in the arena's
 * theme, and is built from a seed, so the copy on the other half of a symmetric arena is exactly the same.
 */
public final class Cover {
	private Cover() {}

	public enum Kind {
		ARCHWAY, COLONNADE, MONUMENT, WELL, CRATES, STALL
	}

	/** How far a piece reaches from its origin. */
	public static final int RADIUS = 2;

	/** A piece as finished blocks, placed by {@code frame} (its y = 0 is the first block above the floor). */
	public static Voxels piece(String theme, long seed, Kind kind, Xform frame, Library lib) {
		Dress d = Dress.of(theme, seed);
		Building b = BedWars.container("cover:" + kind, d, lib, frame, seed, 2);
		b.floorY = 0;
		Rng r = new Rng(seed);
		Ctx c = new Ctx(b, Xform.ID, r.stream("geo"), r.stream("decor"));
		draw(c, d, r.stream("piece"), kind);
		Voxels v = new Voxels(256);
		b.emit(v);
		Connect.apply(v);
		return v;
	}

	static void draw(Ctx c, Dress d, Rng r, Kind kind) {
		switch (kind) {
			case ARCHWAY -> {
				// two piers and a round arch, two deep; the top crumbles on one side
				int[] h = ArchCurve.ROUND.heights(3);
				for (int z = -1; z <= 0; z++) {
					for (int x = -2; x <= 2; x += 4) {
						c.set(x, 0, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
						for (int y = 1; y <= 3; y++) c.set(x, y, z, Cell.of(Slot.PILLAR, Cell.BODY));
					}
					c.archRing(-1, 2, h, 1, Slot.ARCH, z, z, Cell.BODY);
					for (int x = -2; x <= 2; x++) {
						if (x == 2 && r.chance(0.5)) continue;
						int cap = Math.abs(x) == 2 ? 4 : 2 + h[x + 1] + 1;
						c.set(x, cap, z, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
					}
				}
				c.decor(0, 1, 1, Cell.slab(Slot.RUBBLE, false, Cell.DETAIL));
			}
			case COLONNADE -> {
				// three columns under an architrave, one fallen: its drums lie in front
				int broken = r.chance(0.5) ? -2 : 2;
				for (int x = -2; x <= 2; x += 2) {
					c.set(x, 0, 0, Cell.of(Slot.TRIM, Cell.BODY));
					int top = x == broken ? 1 + r.nextInt(2) : 3;
					for (int y = 1; y <= top; y++) c.set(x, y, 0, Cell.of(Slot.PILLAR, Cell.BODY));
					if (x != broken) c.set(x, 4, 0, Cell.of(Slot.TRIM, Cell.BODY));
				}
				for (int x = -2; x <= 2; x++) {
					if (Math.abs(x - broken) <= 1) continue;
					c.set(x, 5, 0, Cell.of(Slot.BAND, Cell.BODY));
				}
				for (int z = 1; z <= 2; z++) c.set(broken - Integer.signum(broken), 0, z, Cell.log(Slot.PILLAR, 'z', Cell.DETAIL));
			}
			case MONUMENT -> {
				// a stepped plinth with a figure: a shaft, an accent and a lamp
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						int m = Math.max(Math.abs(x), Math.abs(z));
						if (m == 2) {
							Dir out = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Dir.EAST : Dir.WEST) : (z > 0 ? Dir.SOUTH : Dir.NORTH);
							c.set(x, 0, z, Cell.stairs(Slot.TRIM, out.back(), false, Cell.BODY));
						} else {
							c.set(x, 0, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
							if (m == 0) c.set(x, 1, z, Cell.of(Slot.WALL, Cell.BODY));
							else c.set(x, 1, z, Cell.slab(Slot.TRIM, false, Cell.BODY));
						}
					}
				}
				c.set(0, 2, 0, Cell.of(Slot.PILLAR, Cell.BODY));
				c.set(0, 3, 0, Cell.of(Slot.PILLAR, Cell.BODY));
				c.set(0, 4, 0, Cell.of(Slot.ACCENT, Cell.BODY));
				c.set(0, 5, 0, Cell.literal(d.lamp, Cell.DETAIL));
			}
			case WELL -> {
				// a ring of stone round water, two posts and a little roof
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						c.set(x, 0, z, x == 0 && z == 0 ? Cell.of(Slot.WATER, Cell.BODY) : Cell.of(Slot.WALL, Cell.BODY));
					}
				}
				for (int x = -1; x <= 1; x += 2) {
					for (int y = 1; y <= 2; y++) c.set(x, y, 0, Cell.shape(Slot.WOOD, Shape.FENCE, Cell.BODY));
				}
				RoofField f = new RoofField();
				f.gable(-1, -1, 1, 1, false, 3, 1, 1);
				f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
				for (int y = 2; y <= 4; y++) c.set(0, y, 0, Cell.literal("iron_chain", Cell.DETAIL));
			}
			case CRATES -> {
				// barrels, hay and a crate stacked against each other
				c.set(-1, 0, 0, Cell.literal("barrel[facing=up]", Cell.BODY));
				c.set(0, 0, 0, Cell.literal("barrel[facing=north]", Cell.BODY));
				c.set(-1, 1, 0, Cell.literal("barrel[facing=up]", Cell.BODY));
				c.set(1, 0, 0, Cell.literal("hay_block", Cell.BODY));
				c.set(1, 0, -1, Cell.literal("hay_block", Cell.BODY));
				c.set(1, 1, 0, Cell.literal("hay_block[axis=x]", Cell.BODY));
				c.set(0, 0, -1, Cell.of(Slot.WOOD, Cell.BODY));
				c.set(-2, 0, 1, Cell.literal("barrel[facing=east]", Cell.BODY));
				c.decor(0, 1, 0, Cell.literal(d.lamp, Cell.DETAIL));
			}
			case STALL -> {
				// a market stall: four posts, a striped awning in the theme's cloth, a counter of barrels
				for (int x = -2; x <= 2; x += 4) {
					for (int z = -1; z <= 1; z += 2) {
						for (int y = 0; y <= 2; y++) c.set(x, y, z, Cell.log(Slot.TIMBER, 'y', Cell.BODY));
					}
				}
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						String stripe = (x & 1) == 0 ? d.cloth + "_wool" : "white_wool";
						c.set(x, 3, z, Cell.literal(stripe, Cell.DETAIL));
					}
				}
				for (int x = -1; x <= 1; x++) c.set(x, 0, -1, Cell.literal(x == 0 ? "barrel[facing=up]" : "barrel[facing=north]", Cell.BODY));
				c.decor(-1, 1, -1, Cell.literal("decorated_pot", Cell.DETAIL));
				c.decor(1, 1, -1, Cell.literal(d.lamp, Cell.DETAIL));
			}
		}
	}
}
