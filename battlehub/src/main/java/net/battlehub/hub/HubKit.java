package net.battlehub.hub;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.core.FloorPattern;
import net.battlehub.core.Noise;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drawing tools for the lobby: blocks by name (with properties), colour gradients with frayed borders, and shapes
 * (discs, cylinders, cones, domes, boxes, arches). Everything writes into a blueprint.
 */
final class HubKit {
	final Blueprint bp;
	final Noise noise;

	HubKit(Blueprint bp, long seed) {
		this.bp = bp;
		this.noise = new Noise(seed);
	}

	interface Shader {
		BlockState at(int x, int y, int z);
	}

	void set(int x, int y, int z, String id) {
		bp.set(x, y, z, Build.s(id));
	}

	void set(int x, int y, int z, BlockState st) {
		bp.set(x, y, z, st);
	}

	void setIfEmpty(int x, int y, int z, String id) {
		bp.setIfEmpty(x, y, z, Build.s(id));
	}

	/** The block of a gradient at place {@code t} (0..1), frayed a little per block so the steps blend. */
	BlockState grad(String[] g, double t, int x, int y, int z, double fray) {
		double jitter = noise.hash(x * 7 + y * 131, z * 13 - y * 17);
		return Build.s(g[FloorPattern.step(t, g.length, jitter, fray)]);
	}

	/** Mostly the first block, now and then one of the others (texture, like a builder breaks up a flat wall). */
	BlockState mix(int x, int y, int z, String main, double share, String... others) {
		double h = noise.hash(x * 3 + y * 101, z * 5 - y * 7);
		if (h >= share || others.length == 0) {
			return Build.s(main);
		}
		return Build.s(others[(int) (h / share * others.length) % others.length]);
	}

	static double dist(double x, double z) {
		return Math.sqrt(x * x + z * z);
	}

	/** A filled disc at height y. */
	void disc(int cx, int y, int cz, double r, Shader s) {
		int ir = (int) Math.ceil(r);
		for (int x = -ir; x <= ir; x++) {
			for (int z = -ir; z <= ir; z++) {
				if (x * x + z * z <= r * r + r * 0.6) {
					set(cx + x, y, cz + z, s.at(cx + x, y, cz + z));
				}
			}
		}
	}

	/** A ring between two radii at height y. */
	void ring(int cx, int y, int cz, double rIn, double rOut, Shader s) {
		int ir = (int) Math.ceil(rOut);
		for (int x = -ir; x <= ir; x++) {
			for (int z = -ir; z <= ir; z++) {
				double d = dist(x, z);
				if (d >= rIn && d <= rOut) {
					set(cx + x, y, cz + z, s.at(cx + x, y, cz + z));
				}
			}
		}
	}

	/** A round wall (one block thick shell at radius r) from y0 up to y1. */
	void cylinder(int cx, int y0, int y1, int cz, double r, Shader s) {
		for (int y = y0; y <= y1; y++) {
			ring(cx, y, cz, r - 0.5, r + 0.5, s);
		}
	}

	/** A solid round column. */
	void column(int cx, int y0, int y1, int cz, double r, Shader s) {
		for (int y = y0; y <= y1; y++) {
			disc(cx, y, cz, r, s);
		}
	}

	/** A cone roof: radius r at y0 narrowing to a point h higher. */
	void cone(int cx, int y0, int cz, double r, int h, Shader s) {
		for (int i = 0; i < h; i++) {
			double rr = r * (1 - (double) i / h);
			if (rr < 0.4) {
				set(cx, y0 + i, cz, s.at(cx, y0 + i, cz));
				continue;
			}
			disc(cx, y0 + i, cz, rr, s);
		}
	}

	/** A dome (half a sphere shell) on a circle of radius r at y0. */
	void dome(int cx, int y0, int cz, double r, Shader s) {
		int ir = (int) Math.ceil(r);
		for (int x = -ir; x <= ir; x++) {
			for (int y = 0; y <= ir; y++) {
				for (int z = -ir; z <= ir; z++) {
					double d = Math.sqrt(x * x + y * y + z * z);
					if (d <= r + 0.3 && d >= r - 0.9) {
						set(cx + x, y0 + y, cz + z, s.at(cx + x, y0 + y, cz + z));
					}
				}
			}
		}
	}

	/** A box (inclusive corners). */
	void box(int x1, int y1, int z1, int x2, int y2, int z2, Shader s) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, s.at(x, y, z));
				}
			}
		}
	}

	/** Only the walls of a box (no floor or ceiling). */
	void walls(int x1, int y1, int z1, int x2, int y2, int z2, Shader s) {
		for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
				set(x, y, z1, s.at(x, y, z1));
				set(x, y, z2, s.at(x, y, z2));
			}
			for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
				set(x1, y, z, s.at(x1, y, z));
				set(x2, y, z, s.at(x2, y, z));
			}
		}
	}

	void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					bp.remove(x, y, z);
				}
			}
		}
	}

	/**
	 * The underside of a floating island: an upside-down, wobbly cone below a disc of radius r at y0, fading
	 * through the gradient from top to bottom.
	 */
	void underside(int cx, int y0, int cz, double r, int depth, String[] gradient) {
		for (int d = 1; d <= depth; d++) {
			double f = (double) d / (depth + 1);
			int ir = (int) Math.ceil(r);
			for (int x = -ir; x <= ir; x++) {
				for (int z = -ir; z <= ir; z++) {
					double wob = (noise.at(cx + x, cz + z, 6, 2) - 0.5) * 0.35;
					double rr = r * (1 - Math.pow(f + wob * f, 1.15));
					if (x * x + z * z <= rr * rr) {
						set(cx + x, y0 - d, cz + z, grad(gradient, f, cx + x, y0 - d, cz + z, 0.25));
					}
				}
			}
		}
	}

	/** A lamp post: a pillar with a lantern on top (or hanging from a little arm). */
	void lampPost(int x, int y, int z, int h, String pillar) {
		for (int i = 0; i < h; i++) {
			set(x, y + i, z, pillar);
		}
		set(x, y + h, z, "lantern");
	}

	/** A hanging lantern on a chain of {@code links} below (x, y, z). */
	void hangingLantern(int x, int y, int z, int links) {
		for (int i = 0; i < links; i++) {
			set(x, y - i, z, "iron_chain");
		}
		set(x, y - links, z, "lantern[hanging=true]");
	}

	/** A round tree of leaves on a trunk (persistent leaves). */
	void tree(int x, int y, int z, int trunk, double crown, String log, String leaves) {
		for (int i = 0; i < trunk; i++) {
			set(x, y + i, z, log);
		}
		int top = y + trunk;
		int ir = (int) Math.ceil(crown);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= ir; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz) + (noise.hash(x + dx, z + dz * 3 + dy) - 0.5) * 0.8;
					if (d <= crown) {
						setIfEmpty(x + dx, top + dy, z + dz, leaves + "[persistent=true]");
					}
				}
			}
		}
	}

	/** Stairs facing a direction, upside down or not. */
	static String stair(String id, String facing, boolean top) {
		return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
	}

	/** The compass name of the direction (dx, dz) points, horizontally. */
	static String facing(double dx, double dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? "east" : "west";
		}
		return dz > 0 ? "south" : "north";
	}
}
