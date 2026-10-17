package net.battlehub.arch.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.arch.Props;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.mat.Catalog;

/**
 * Pictures of generated blocks without the game: a ray tracer through a voxel grid with the real block shapes
 * (stairs, slabs, walls, panes, small decorations), sunlight with shadows, ambient occlusion, see-through glass
 * and water, glowing lights, a sky and distance haze. Colours come from the catalog's average texture colours.
 * Used for local design work and for the pictures in the self-test log.
 */
public final class Render {
	/** A block's look: boxes inside its cell (0..1), colour, and how it treats light. */
	static final class Look {
		final List<double[]> boxes = new ArrayList<>();
		int rgb;
		boolean full;
		boolean clear;
		boolean glow;
		double alpha = 1;
	}

	private final int x0, y0, z0, sx, sy, sz;
	private final short[] grid;
	private final List<Look> looks = new ArrayList<>();
	private final Map<String, Short> ids = new HashMap<>();
	private double[] sun = norm(new double[] {-0.45, 0.78, -0.35});

	public Render(Voxels v) {
		this(v, v.minX(), v.minY(), v.minZ(), v.maxX(), v.maxY(), v.maxZ());
	}

	public Render(Voxels v, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		x0 = minX - 1;
		y0 = minY - 1;
		z0 = minZ - 1;
		sx = maxX - minX + 3;
		sy = maxY - minY + 3;
		sz = maxZ - minZ + 3;
		grid = new short[sx * sy * sz];
		looks.add(null);
		v.forEach((x, y, z, id) -> {
			if (id.equals("air")) return;
			if (x <= x0 || y <= y0 || z <= z0 || x >= x0 + sx - 1 || y >= y0 + sy - 1 || z >= z0 + sz - 1) return;
			grid[idx(x - x0, y - y0, z - z0)] = id(id);
		});
	}

	public Render sun(double x, double y, double z) {
		sun = norm(new double[] {x, y, z});
		return this;
	}

	private int idx(int x, int y, int z) {
		return (y * sz + z) * sx + x;
	}

	private short id(String name) {
		Short s = ids.get(name);
		if (s != null) return s;
		short n = (short) looks.size();
		looks.add(look(name));
		ids.put(name, n);
		return n;
	}

	static Look look(String id) {
		Look l = new Look();
		String b = Props.base(id);
		Catalog.Kind k = Catalog.kind(b);
		l.rgb = k == null ? 0x8a8a8a : k.rgb();
		l.glow = k != null && k.glow();
		if (b.contains("glass") || b.equals("water") || b.endsWith("_pane") || b.endsWith("_bars")) {
			l.clear = true;
			l.alpha = b.equals("water") ? 0.55 : b.contains("stained") ? 0.6 : b.contains("tinted") ? 0.85 : 0.25;
		}
		if (b.endsWith("_stairs")) {
			boolean top = "top".equals(Props.get(id, "half"));
			String f = Props.get(id, "facing");
			l.boxes.add(top ? box(0, 0.5, 0, 1, 1, 1) : box(0, 0, 0, 1, 0.5, 1));
			double y1 = top ? 0 : 0.5, y2 = top ? 0.5 : 1;
			switch (f == null ? "north" : f) {
				case "north" -> l.boxes.add(box(0, y1, 0, 1, y2, 0.5));
				case "south" -> l.boxes.add(box(0, y1, 0.5, 1, y2, 1));
				case "east" -> l.boxes.add(box(0.5, y1, 0, 1, y2, 1));
				default -> l.boxes.add(box(0, y1, 0, 0.5, y2, 1));
			}
		} else if (b.endsWith("_slab")) {
			String t = Props.get(id, "type");
			if ("double".equals(t)) {
				l.full = true;
			} else {
				l.boxes.add("top".equals(t) ? box(0, 0.5, 0, 1, 1, 1) : box(0, 0, 0, 1, 0.5, 1));
			}
		} else if (b.endsWith("_wall") && !b.contains("wall_")) {
			l.boxes.add(box(0.25, 0, 0.25, 0.75, 1, 0.75));
			arms(l, id, 0.3125, 0.6875, 0.875, "none");
		} else if (b.endsWith("_fence")) {
			l.boxes.add(box(0.375, 0, 0.375, 0.625, 1, 0.625));
			arms(l, id, 0.4375, 0.5625, 0.9, "false");
		} else if (b.endsWith("_pane") || b.endsWith("_bars")) {
			l.boxes.add(box(0.4375, 0, 0.4375, 0.5625, 1, 0.5625));
			arms(l, id, 0.4375, 0.5625, 1, "false");
		} else if (b.endsWith("_carpet") || b.equals("moss_carpet") || b.equals("pale_moss_carpet") || b.equals("pink_petals") || b.equals("leaf_litter")
				|| b.equals("wildflowers")) {
			l.boxes.add(box(0, 0, 0, 1, 0.0625, 1));
		} else if (b.endsWith("_door")) {
			String f = Props.get(id, "facing");
			l.boxes.add("north".equals(f) || "south".equals(f) ? box(0, 0, 0.42, 1, 1, 0.58) : box(0.42, 0, 0, 0.58, 1, 1));
		} else if (b.endsWith("_trapdoor")) {
			boolean open = "true".equals(Props.get(id, "open"));
			String f = Props.get(id, "facing");
			if (!open) {
				l.boxes.add("top".equals(Props.get(id, "half")) ? box(0, 0.8125, 0, 1, 1, 1) : box(0, 0, 0, 1, 0.1875, 1));
			} else {
				switch (f == null ? "north" : f) {
					case "north" -> l.boxes.add(box(0, 0, 0.8125, 1, 1, 1));
					case "south" -> l.boxes.add(box(0, 0, 0, 1, 1, 0.1875));
					case "east" -> l.boxes.add(box(0, 0, 0, 0.1875, 1, 1));
					default -> l.boxes.add(box(0.8125, 0, 0, 1, 1, 1));
				}
			}
		} else if (b.endsWith("_wall_banner")) {
			String f = Props.get(id, "facing");
			switch (f == null ? "south" : f) {
				case "south" -> l.boxes.add(box(0.05, 0, 0, 0.95, 0.95, 0.1));
				case "north" -> l.boxes.add(box(0.05, 0, 0.9, 0.95, 0.95, 1));
				case "east" -> l.boxes.add(box(0, 0, 0.05, 0.1, 0.95, 0.95));
				default -> l.boxes.add(box(0.9, 0, 0.05, 1, 0.95, 0.95));
			}
		} else if (b.equals("iron_chain") || b.equals("end_rod") || b.equals("lightning_rod")) {
			l.boxes.add(box(0.4, 0, 0.4, 0.6, 1, 0.6));
		} else if (b.contains("lantern") && !b.equals("sea_lantern") && !b.equals("jack_o_lantern")) {
			l.boxes.add(box(0.3, 0.05, 0.3, 0.7, 0.6, 0.7));
		} else if (k != null && k.small() || b.startsWith("potted_") || b.endsWith("_bed")) {
			l.boxes.add(b.endsWith("_bed") ? box(0, 0, 0, 1, 0.56, 1) : box(0.25, 0, 0.25, 0.75, 0.7, 0.75));
		} else {
			l.full = true;
		}
		if (l.full && l.boxes.isEmpty()) l.boxes.add(box(0, 0, 0, 1, 1, 1));
		if (b.startsWith("potted_")) l.rgb = 0x7c4536;
		return l;
	}

	private static void arms(Look l, String id, double a, double b, double h, String none) {
		String n = Props.get(id, "north"), s = Props.get(id, "south"), e = Props.get(id, "east"), w = Props.get(id, "west");
		if (n != null && !n.equals(none)) l.boxes.add(box(a, 0, 0, b, h, 0.5));
		if (s != null && !s.equals(none)) l.boxes.add(box(a, 0, 0.5, b, h, 1));
		if (e != null && !e.equals(none)) l.boxes.add(box(0.5, 0, a, 1, h, b));
		if (w != null && !w.equals(none)) l.boxes.add(box(0, 0, a, 0.5, h, b));
	}

	private static double[] box(double ax, double ay, double az, double bx, double by, double bz) {
		return new double[] {ax, ay, az, bx, by, bz};
	}

	private static double[] norm(double[] v) {
		double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new double[] {v[0] / l, v[1] / l, v[2] / l};
	}

	private Look at(int x, int y, int z) {
		int gx = x - x0, gy = y - y0, gz = z - z0;
		if (gx < 0 || gy < 0 || gz < 0 || gx >= sx || gy >= sy || gz >= sz) return null;
		short s = grid[idx(gx, gy, gz)];
		return s == 0 ? null : looks.get(s);
	}

	private boolean occ(int x, int y, int z) {
		Look l = at(x, y, z);
		return l != null && l.full && !l.clear;
	}

	/** Where a ray hits: distance, block, face normal, colour of the hit block. */
	private static final class Hit {
		double t;
		int x, y, z;
		int nx, ny, nz;
		Look look;
		double px, py, pz;
	}

	/**
	 * Steps through the grid (Amanatides and Woo) and tests each occupied cell's boxes; transparent blocks are
	 * skipped when {@code solidOnly}, else returned too.
	 */
	private boolean trace(double ox, double oy, double oz, double dx, double dy, double dz, double maxT, boolean solidOnly, Hit h, double minT) {
		// Clip to the grid box
		double tmin = minT, tmax = maxT;
		double[] lo = {x0, y0, z0}, hi = {x0 + sx, y0 + sy, z0 + sz};
		double[] o = {ox, oy, oz}, d = {dx, dy, dz};
		for (int a = 0; a < 3; a++) {
			if (Math.abs(d[a]) < 1e-12) {
				if (o[a] < lo[a] || o[a] > hi[a]) return false;
			} else {
				double t1 = (lo[a] - o[a]) / d[a], t2 = (hi[a] - o[a]) / d[a];
				if (t1 > t2) {
					double t = t1;
					t1 = t2;
					t2 = t;
				}
				tmin = Math.max(tmin, t1);
				tmax = Math.min(tmax, t2);
			}
		}
		if (tmin > tmax) return false;
		double t = tmin + 1e-6;
		int x = (int) Math.floor(ox + dx * t), y = (int) Math.floor(oy + dy * t), z = (int) Math.floor(oz + dz * t);
		int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
		double tdx = Math.abs(1 / dx), tdy = Math.abs(1 / dy), tdz = Math.abs(1 / dz);
		double nextX = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? x + 1 : x) - ox) / dx;
		double nextY = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? y + 1 : y) - oy) / dy;
		double nextZ = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? z + 1 : z) - oz) / dz;
		int axis = -1;
		double tEnter = t;
		while (tEnter <= tmax) {
			Look l = at(x, y, z);
			if (l != null && !(solidOnly && l.clear)) {
				double tExit = Math.min(nextX, Math.min(nextY, nextZ));
				if (l.full) {
					h.t = tEnter;
					h.x = x;
					h.y = y;
					h.z = z;
					h.nx = axis == 0 ? -stepX : 0;
					h.ny = axis == 1 ? -stepY : 0;
					h.nz = axis == 2 ? -stepZ : 0;
					if (axis < 0) h.ny = 1;
					h.look = l;
					return true;
				}
				double best = Double.MAX_VALUE;
				int bn = 0;
				for (double[] bx : l.boxes) {
					double a0 = Math.max(tEnter - 1e-6, 0), a1 = tExit + 1e-6;
					int na = -1;
					double[] bl = {x + bx[0], y + bx[1], z + bx[2]}, bh = {x + bx[3], y + bx[4], z + bx[5]};
					boolean miss = false;
					for (int k = 0; k < 3; k++) {
						if (Math.abs(d[k]) < 1e-12) {
							if (o[k] < bl[k] || o[k] > bh[k]) miss = true;
							continue;
						}
						double t1 = (bl[k] - o[k]) / d[k], t2 = (bh[k] - o[k]) / d[k];
						if (t1 > t2) {
							double tt = t1;
							t1 = t2;
							t2 = tt;
						}
						if (t1 > a0) {
							a0 = t1;
							na = k;
						}
						a1 = Math.min(a1, t2);
					}
					if (miss || a0 > a1) continue;
					if (a0 < best) {
						best = a0;
						bn = na;
					}
				}
				if (best < Double.MAX_VALUE) {
					h.t = best;
					h.x = x;
					h.y = y;
					h.z = z;
					h.nx = bn == 0 ? -stepX : 0;
					h.ny = bn == 1 ? -stepY : 0;
					h.nz = bn == 2 ? -stepZ : 0;
					if (bn < 0) h.ny = 1;
					h.look = l;
					return true;
				}
			}
			if (nextX < nextY && nextX < nextZ) {
				x += stepX;
				tEnter = nextX;
				nextX += tdx;
				axis = 0;
			} else if (nextY < nextZ) {
				y += stepY;
				tEnter = nextY;
				nextY += tdy;
				axis = 1;
			} else {
				z += stepZ;
				tEnter = nextZ;
				nextZ += tdz;
				axis = 2;
			}
		}
		return false;
	}

	private int sky(double dx, double dy, double dz) {
		double t = Math.max(0, Math.min(1, dy * 1.4 + 0.2));
		int r = (int) (205 - 95 * t), g = (int) (222 - 70 * t), b = (int) (238 - 20 * t);
		return (r << 16) | (g << 8) | b;
	}

	/** One colour for a ray. */
	private int shade(double ox, double oy, double oz, double dx, double dy, double dz, int depth) {
		Hit h = new Hit();
		if (!trace(ox, oy, oz, dx, dy, dz, 4000, false, h, 0)) return sky(dx, dy, dz);
		h.px = ox + dx * h.t;
		h.py = oy + dy * h.t;
		h.pz = oz + dz * h.t;
		int base = h.look.rgb;
		// Texture: a per-block tone and a little texel grain
		double tone = 0.94 + 0.12 * Rng.hash(base, h.x, h.y, h.z);
		double u = h.nx != 0 ? h.pz : h.px, v = h.ny != 0 ? h.pz : h.py;
		double grain = 0.96 + 0.08 * Rng.hash(base * 31L, (int) Math.floor(u * 8), (int) Math.floor(v * 8), h.x * 7 + h.y * 13 + h.z);
		double r = ((base >> 16) & 255) * tone * grain, g = ((base >> 8) & 255) * tone * grain, b = (base & 255) * tone * grain;
		double light;
		if (h.look.glow) {
			light = 1.25;
		} else {
			double ndl = h.nx * sun[0] + h.ny * sun[1] + h.nz * sun[2];
			double sh = 1;
			if (ndl > 0) {
				Hit s = new Hit();
				double ex = h.px + h.nx * 1e-3, ey = h.py + h.ny * 1e-3, ez = h.pz + h.nz * 1e-3;
				if (trace(ex, ey, ez, sun[0], sun[1], sun[2], 400, true, s, 1e-4)) sh = 0;
			}
			double ao = ao(h);
			// sky light from above, a soft fill from the side opposite the sun, the sun itself
			double fill = Math.max(0, -(h.nx * sun[0] + h.nz * sun[2])) * 0.12;
			light = (0.5 + 0.14 * (h.ny > 0 ? 1 : h.ny < 0 ? -0.7 : 0.15) + fill) * ao + 0.62 * Math.max(0, ndl) * sh;
		}
		r *= light;
		g *= light;
		b *= light;
		if (h.look.clear && depth < 6) {
			// continue behind the glass, from where the ray leaves this cell
			double tx = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? h.x + 1 : h.x) - ox) / dx;
			double ty = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? h.y + 1 : h.y) - oy) / dy;
			double tz = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? h.z + 1 : h.z) - oz) / dz;
			double te = Math.min(tx, Math.min(ty, tz)) + 1e-4;
			int behind = shade(ox + dx * te, oy + dy * te, oz + dz * te, dx, dy, dz, depth + 1);
			double a = h.look.alpha;
			r = r * a + ((behind >> 16) & 255) * (1 - a);
			g = g * a + ((behind >> 8) & 255) * (1 - a);
			b = b * a + (behind & 255) * (1 - a);
		}
		// Haze with distance
		double fog = 1 - Math.exp(-h.t / 2600.0);
		int sk = sky(dx, Math.max(0.05, dy), dz);
		r = r * (1 - fog) + ((sk >> 16) & 255) * fog;
		g = g * (1 - fog) + ((sk >> 8) & 255) * fog;
		b = b * (1 - fog) + (sk & 255) * fog;
		return (tone(r) << 16) | (tone(g) << 8) | tone(b);
	}

	/** A soft shoulder instead of hard clipping, so bright stone keeps its texture. */
	private static int tone(double v) {
		double x = v / 255.0;
		if (x > 0.78) x = 0.78 + (x - 0.78) * 0.45;
		return clamp(x * 255);
	}

	/** Darkening where the hit face meets other blocks (cheap ambient occlusion). */
	private double ao(Hit h) {
		int fx = h.x + h.nx, fy = h.y + h.ny, fz = h.z + h.nz;
		int n = 0;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				int x = fx, y = fy, z = fz;
				if (h.nx != 0) {
					y += a;
					z += b;
				} else if (h.ny != 0) {
					x += a;
					z += b;
				} else {
					x += a;
					y += b;
				}
				if (occ(x, y, z)) n++;
			}
		}
		return 1 - 0.055 * n;
	}

	private static int clamp(double v) {
		return v < 0 ? 0 : v > 255 ? 255 : (int) v;
	}

	/**
	 * A perspective picture: the camera at {@code eye} looking at {@code target}, field of view in degrees.
	 * Two samples per pixel smooth the edges.
	 */
	public BufferedImage perspective(double[] eye, double[] target, double fov, int w, int hgt) {
		double[] f = norm(new double[] {target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]});
		double[] right = norm(cross(f, new double[] {0, 1, 0}));
		double[] up = cross(right, f);
		double scale = Math.tan(Math.toRadians(fov) / 2);
		BufferedImage img = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
		int[] px = new int[w * hgt];
		java.util.stream.IntStream.range(0, hgt).parallel().forEach(j -> {
			for (int i = 0; i < w; i++) {
				int rs = 0, gs = 0, bs = 0;
				for (int s = 0; s < 2; s++) {
					double ox = (s == 0 ? 0.25 : 0.75), oy = (s == 0 ? 0.25 : 0.75);
					double sxp = (2 * (i + ox) / w - 1) * scale * w / hgt;
					double syp = (1 - 2 * (j + oy) / hgt) * scale;
					double[] d = norm(new double[] {f[0] + right[0] * sxp + up[0] * syp, f[1] + right[1] * sxp + up[1] * syp, f[2] + right[2] * sxp + up[2] * syp});
					int c = shade(eye[0], eye[1], eye[2], d[0], d[1], d[2], 0);
					rs += (c >> 16) & 255;
					gs += (c >> 8) & 255;
					bs += c & 255;
				}
				px[j * w + i] = ((rs / 2) << 16) | ((gs / 2) << 8) | (bs / 2);
			}
		});
		img.setRGB(0, 0, w, hgt, px, 0, w);
		return img;
	}

	/** A view from a compass bearing (degrees, 0 = from the south) and height angle, framing the whole grid. */
	public BufferedImage view(double bearing, double elevation, double zoom, int w, int h) {
		return view(bearing, elevation, zoom, w, h, new int[] {x0, y0, z0, x0 + sx, y0 + sy, z0 + sz});
	}

	/** The same, framing a box {minX, minY, minZ, maxX, maxY, maxZ} (a building inside a bigger scene). */
	public BufferedImage view(double bearing, double elevation, double zoom, int w, int h, int[] box) {
		double bx = box[3] - box[0], by = box[4] - box[1], bz = box[5] - box[2];
		double cx = box[0] + bx / 2.0, cy = box[1] + by * 0.4, cz = box[2] + bz / 2.0;
		double radius = Math.sqrt(bx * bx + by * by + bz * bz) / 2.0;
		double dist = radius / Math.tan(Math.toRadians(20)) * zoom;
		double a = Math.toRadians(bearing), e = Math.toRadians(elevation);
		// the sun from over the viewer's left shoulder, so the faces in view are lit
		double sa = a - Math.toRadians(40);
		sun = norm(new double[] {Math.sin(sa) * 0.62, 0.78, Math.cos(sa) * 0.62});
		double[] eye = {cx + Math.sin(a) * Math.cos(e) * dist, cy + Math.sin(e) * dist, cz + Math.cos(a) * Math.cos(e) * dist};
		return perspective(eye, new double[] {cx, cy, cz}, 40, w, h);
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}
}
