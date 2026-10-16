package com.extremedimensions.seam;

import static com.extremedimensions.seam.SeamArchitect.*;

import java.util.Random;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Return Gate: where every visitor steps out into the Seam, and where the way home waits. It borrows from
 * gateways built to be found from afar and walked through slowly: the twin battered pylons of an Egyptian temple
 * (Karnak, Edfu), lower portal between them, flag niches in their faces and a winged disc over the door; the torii of
 * Itsukushima, a frame that stands at the edge and opens onto nothing; the receding orders of a Gothic portal; and the
 * avenue of lamps that leads up to a temple. Its light is gold, a colour nothing else in the Seam gives (the soul
 * lanterns are blue, the falls and veins white and violet), so gold always means home: braziers on the pylons, an eye
 * of gold over the door, a line of gold set in the paving that leads back to it, and the column over the rift.
 *
 * <p>The rift hangs over the threshold between the pylons and wanders over its pale stones, never off them (see
 * {@link #threshold()}). The gate is built on a rock of its own, joined to the arrival island like a lobe.
 */
public final class ReturnGate {
	private ReturnGate() {}

	/** Blocks from the arrival island's centre to the gate's threshold. */
	public static final int OFFSET = 6;
	/** Radius of the rock the gate stands on, centred under the threshold. */
	public static final int ROCK = 9;
	/** How far the gate reaches from its threshold across the ground (the corners of its dais). */
	public static final int REACH = 13;

	/** Where the rift home hangs, where a visitor steps out of the gate, and the way they face (out of it). */
	public record Gate(double riftX, double riftY, double riftZ, double landX, double landY, double landZ, float yaw) {}

	/** The pale stones of the threshold: the only floor the rift home wanders over while it is leashed to its gate. */
	public static BlockState threshold() {
		return palePol();
	}

	/** The gate's gold light (froglight, a lantern where there is none). */
	static BlockState gold() {
		return mc("ochre_froglight", lantern());
	}

	/** Masonry of the gate from foot to crown: dark only at the feet, then mostly the pale bricks that catch the gold. */
	private static BlockState[] grad() {
		return new BlockState[] {bsBrick(), brick(), tile(), pale(), pale(), palePol(), palePol()};
	}

	/**
	 * Lays the gate out with its threshold at ({@code tx}, {@code tz}), on ground whose surface is at {@code top}, its
	 * front (where the visitor steps out) facing {@code front}.
	 */
	public static Gate build(Plan p, Random rnd, int tx, int top, int tz, Direction front) {
		int rot = switch (front) {
			case WEST -> 1;
			case NORTH -> 2;
			case EAST -> 3;
			default -> 0;
		};
		Frame f = new Frame(p, tx, top + 1, tz, rot);
		long seed = rnd.nextLong();
		int h = 23 + rnd.nextInt(3);
		dais(f, rnd);
		for (int s = -1; s <= 1; s += 2) {
			pylon(f, rnd, seed, s, h);
			obelisk(f, s * 7, 5);
		}
		portal(f, rnd, seed);
		way(f, p, rnd, top);
		double rx = f.wx(0, 0) + 0.5, rz = f.wz(0, 0) + 0.5;
		double lx = f.wx(0, 4) + 0.5, lz = f.wz(0, 4) + 0.5;
		return new Gate(rx, top + 2, rz, lx, top + 2, lz, front.toYRot());
	}

	// ------------------------------------------------------------------ the dais and the threshold

	/**
	 * A platform a block high with a step of slabs all round, bordered in polished stone and paved in tiles; between
	 * the pylons the threshold of pale stones, closed front and back by a row of chiseled bricks, and a line of gold
	 * through the paving on the gate's axis.
	 */
	private static void dais(Frame f, Random rnd) {
		for (int x = -10; x <= 10; x++) {
			for (int z = -7; z <= 6; z++) {
				boolean ring = Math.abs(x) == 10 || z == -7 || z == 6;
				if (ring) {
					f.set(x, 0, z, slab(smoothSlab(), false));
					continue;
				}
				boolean border = Math.abs(x) == 9 || z == -6 || z == 5;
				BlockState b;
				if (Math.abs(x) <= 2 && Math.abs(z) <= 1) {
					b = threshold();
				} else if (Math.abs(x) <= 2 && Math.abs(z) == 2) {
					b = carved();
				} else if (x == 0) {
					b = gold();
				} else if (border) {
					b = smooth();
				} else {
					b = rnd.nextInt(9) == 0 ? smooth() : tile();
				}
				f.set(x, 0, z, b);
				// whatever grew here is under the paving now
				f.clear(x, 1, z);
			}
		}
	}

	// ------------------------------------------------------------------ the pylons

	/**
	 * One pylon, on side {@code s}: solid, battered on its outer face (it steps in twice on the way up, a ledge of
	 * slabs at each step), with round mouldings up its corners, a tall niche lined with glowing silk in its front and
	 * back, a frieze near the top, a projecting cornice, lantern posts on the roof's corners and a brazier of gold
	 * fire (signal fires, whose smoke climbs high over the fog).
	 */
	private static void pylon(Frame f, Random rnd, long seed, int s, int h) {
		BlockState[] g = grad();
		for (int ly = 1; ly <= h; ly++) {
			int out = outer(ly);
			double t = (double) ly / h;
			for (int ax = 3; ax <= out; ax++) {
				int x = s * ax;
				for (int z = -4; z <= 3; z++) {
					boolean face = ax == 3 || ax == out || z == -4 || z == 3;
					if (!face) {
						f.set(x, ly, z, stone());
						continue;
					}
					boolean corner = (ax == 3 || ax == out) && (z == -4 || z == 3);
					BlockState b;
					if (corner) {
						b = axis(pillar(), Direction.Axis.Y);
					} else if (ly == h - 3) {
						b = carved();
					} else if (ly == h - 1 || ly == h || ly == 8 || ly == 16) {
						// the crown, and a string course under each step of the batter
						b = palePol();
					} else if (ly <= 2) {
						// a plinth of polished blackstone
						b = bsPol();
					} else {
						b = drip(g, t, seed, f.wx(x, z), f.wz(x, z), rnd);
						if (ly < h / 3) {
							b = weather(b, rnd, 0.08);
						}
					}
					f.set(x, ly, z, b);
				}
			}
			// The ledge where the outer face steps in
			if (out < outer(ly - 1)) {
				for (int z = -4; z <= 3; z++) {
					f.set(s * (out + 1), ly, z, slab(palePolSlab(), false));
				}
			}
		}
		// The niches, front and back: recessed, lined with silk, a sill with a gold lantern, a lintel over them
		for (int side = -1; side <= 1; side += 2) {
			int zf = side > 0 ? 3 : -4, zb = side > 0 ? 2 : -3;
			for (int ax = 5; ax <= 6; ax++) {
				int x = s * ax;
				for (int ly = 3; ly <= 15; ly++) {
					f.clear(x, ly, zf);
					f.set(x, ly, zb, silked());
				}
				f.set(x, 2, zf, palePol());
				f.set(x, 16, zf, palePol());
			}
			f.set(s * 5, 3, zf, lantern());
			f.set(s * 6, 3, zf, lantern());
		}
		// The cornice: stairs turned over, a block out from the top all round, over a flat roof
		int top = outer(h);
		for (int ax = 2; ax <= top + 1; ax++) {
			for (int z = -5; z <= 4; z++) {
				boolean edgeX = ax == 2 || ax == top + 1, edgeZ = z == -5 || z == 4;
				int x = s * ax;
				if (edgeX && edgeZ) {
					f.set(x, h + 1, z, palePol());
				} else if (edgeX) {
					// the tall side against the wall, the overhang out
					Direction d = (ax == 2) == (s > 0) ? Direction.EAST : Direction.WEST;
					f.set(x, h + 1, z, stairs(palePolStairs(), d, true));
				} else if (edgeZ) {
					f.set(x, h + 1, z, stairs(palePolStairs(), z > 0 ? Direction.NORTH : Direction.SOUTH, true));
				} else {
					f.set(x, h + 1, z, palePol());
				}
			}
		}
		// A post with a gold lantern on each corner of the roof
		for (int ax : new int[] {3, top}) {
			for (int z : new int[] {-4, 3}) {
				f.set(s * ax, h + 2, z, paleWall());
				f.set(s * ax, h + 3, z, lantern());
			}
		}
		// The brazier: four signal fires on bales (their smoke climbs far over the fog) in a bowl of slabs
		BlockState fire = mc("campfire", lantern());
		if (fire.hasProperty(BlockStateProperties.SIGNAL_FIRE)) {
			fire = fire.setValue(BlockStateProperties.SIGNAL_FIRE, true);
		}
		for (int ax = 3; ax <= top; ax++) {
			for (int z = -2; z <= 1; z++) {
				boolean core = ax >= 4 && ax <= 5 && z >= -1 && z <= 0;
				if (core) {
					f.set(s * ax, h + 1, z, mc("hay_block", palePol()));
					f.set(s * ax, h + 2, z, fire);
				} else {
					f.set(s * ax, h + 2, z, slab(palePolSlab(), false));
				}
			}
		}
	}

	/** How far from the gate's axis a pylon's outer face stands at a height (it steps in at 9 and 17). */
	private static int outer(int ly) {
		return 8 - (ly >= 17 ? 2 : ly >= 9 ? 1 : 0);
	}

	/**
	 * A gradient that runs down the wall in streaks: each column of the face moves the bands a little up or down,
	 * so they drip into one another the way stone weathers, instead of a dither speckled over the whole face.
	 */
	private static BlockState drip(BlockState[] g, double t, long seed, int x, int z, Random rnd) {
		double streak = noise(seed, x / 2.3, z / 2.3) - 0.5;
		double v = Math.max(0, Math.min(1, t)) * (g.length - 1) + streak * 2.2 + (rnd.nextDouble() - 0.5) * 0.4;
		int i = (int) Math.round(v);
		return g[Math.max(0, Math.min(g.length - 1, i))];
	}

	// ------------------------------------------------------------------ the portal

	/**
	 * The lower portal between the pylons: a pointed arch vaulted through it, its rim and a hood moulding standing
	 * out in the pylons' plane front and back, the gold eye over the door on both faces, a cornice and a parapet,
	 * and gold lanterns hanging in the passage.
	 */
	private static void portal(Frame f, Random rnd, long seed) {
		BlockState[] g = grad();
		int pb = 17;
		for (int x = -2; x <= 2; x++) {
			for (int z = -3; z <= 2; z++) {
				for (int ly = 9; ly <= pb; ly++) {
					if (inArch(x, ly)) {
						continue;
					}
					boolean face = z == -3 || z == 2;
					f.set(x, ly, z, face ? drip(g, 0.62 + (ly - 9) * 0.03, seed, f.wx(x, z), f.wz(x, z), rnd) : stone());
				}
				f.set(x, pb + 1, z, palePol());
			}
		}
		// The arch's curve through the vault and out to the rim in the pylons' plane: stairs turned over
		for (int z = -4; z <= 3; z++) {
			for (int side = -1; side <= 1; side += 2) {
				Direction outward = side > 0 ? Direction.EAST : Direction.WEST;
				f.set(side * 2, 9, z, stairs(palePolStairs(), outward, true));
				f.set(side, 10, z, stairs(palePolStairs(), outward, true));
			}
			f.set(0, 11, z, z == -4 || z == 3 ? carved() : palePol());
		}
		for (int side = -1; side <= 1; side += 2) {
			int zf = side > 0 ? 3 : -4, zw = side > 0 ? 2 : -3;
			Direction in = side > 0 ? Direction.NORTH : Direction.SOUTH;
			// The hood moulding, a course over the rim
			f.set(-2, 10, zf, palePol());
			f.set(2, 10, zf, palePol());
			f.set(-1, 11, zf, palePol());
			f.set(1, 11, zf, palePol());
			f.set(0, 12, zf, gold());
			// The eye over the door: a ring of gold round a pupil of crying obsidian, set in pale stone
			for (int x = -2; x <= 2; x++) {
				for (int ly = 13; ly <= 17; ly++) {
					int dy = ly - 15;
					int r2 = x * x + dy * dy;
					BlockState b = r2 == 0 ? crying() : r2 >= 3 && r2 <= 5 ? gold() : palePol();
					f.set(x, ly, zw, b);
				}
			}
			// The cornice over the portal, and its parapet
			for (int x = -2; x <= 2; x++) {
				f.set(x, pb + 1, zf, stairs(palePolStairs(), in, true));
				f.set(x, pb + 2, zf, paleWall());
			}
			// Gold lanterns hanging in the passage, by the walls
			for (int x = -2; x <= 2; x += 4) {
				f.set(x, 8, zw, chain());
				f.set(x, 7, zw, hanging(lantern()));
			}
		}
	}

	/** Whether a spot of the portal wall is open: the passage under the arch's springing, and the arch above it. */
	private static boolean inArch(int x, int ly) {
		int ax = Math.abs(x);
		return ly <= 8 || (ly == 9 && ax <= 1) || (ly == 10 && ax == 0);
	}

	// ------------------------------------------------------------------ before the gate

	/** An obelisk standing before a pylon: a chiseled plinth, a pillar shaft, a pale cap and a gold lantern on top. */
	private static void obelisk(Frame f, int x, int z) {
		f.set(x, 1, z, carved());
		f.set(x, 2, z, palePol());
		for (int ly = 3; ly <= 8; ly++) {
			f.set(x, ly, z, axis(pillar(), Direction.Axis.Y));
		}
		f.set(x, 9, z, paleWall());
		f.set(x, 10, z, lantern());
	}

	/**
	 * The processional way from the dais over the island: pale paving with the gold line down its middle, set into
	 * the turf, a ragged border of worn stone, and pairs of gold lamps along it; only where the island has ground.
	 */
	private static void way(Frame f, Plan p, Random rnd, int top) {
		for (int z = 7; z <= 7 + OFFSET + 8; z++) {
			for (int x = -2; x <= 2; x++) {
				int wx = f.wx(x, z), wz = f.wz(x, z);
				if (!p.has(wx, top, wz) || !p.has(wx, top - 1, wz)) {
					continue;
				}
				if (Math.abs(x) == 2 && rnd.nextInt(3) == 0) {
					continue;
				}
				BlockState b = x == 0 ? gold() : Math.abs(x) == 1 ? (rnd.nextInt(7) == 0 ? tile() : palePol())
						: weather(rnd.nextBoolean() ? smooth() : tile(), rnd, 0.3);
				p.set(wx, top, wz, b);
				p.clear(wx, top + 1, wz);
			}
			if ((z - 9) % 4 == 0) {
				for (int side = -1; side <= 1; side += 2) {
					int x = side * 3, wx = f.wx(x, z), wz = f.wz(x, z);
					if (!p.has(wx, top, wz)) {
						continue;
					}
					f.set(x, 0, z, palePol());
					f.set(x, 1, z, paleWall());
					f.set(x, 2, z, paleWall());
					f.set(x, 3, z, lantern());
				}
			}
		}
	}
}
