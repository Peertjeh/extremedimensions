package net.battlehub.arch.arena;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Architect;
import net.battlehub.arch.bldg.Brief;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Library;

/**
 * The world round an arena, out of play: landmarks of the theme (a castle, a mage tower, a temple ruin...) on
 * big rocks in the distance, small islands with trees and lamps, clouds in the sky theme. It tells players where
 * they are without getting in the way: everything stays outside the box players build in.
 */
public final class Backdrop {
	private Backdrop() {}

	/** A disc that is taken (centre and radius), so islands do not run into each other. */
	record Taken(double x, double z, double r) {}

	/** Landmarks and islands round a Bed Wars map whose team islands are {@code teamRadius} out. */
	static void bedwars(ArenaPlan out, Dress d, Library lib, long seed, int teamRadius, int buildDepth, int detail) {
		Rng rng = new Rng(seed);
		List<Taken> taken = new ArrayList<>();
		// everything stays out of the square players build in, with some air round it
		double keep = teamRadius + BedWars.BUILD_MARGIN + 5;
		double base = rng.range(0, Math.PI * 2);
		int count = 3;
		for (int i = 0; i < count; i++) {
			double a = base + i * Math.PI * 2 / count + rng.range(-0.3, 0.3);
			String arch = d.landmarks.get(i % d.landmarks.size());
			landmark(out, d, lib, arch, rng.stream("landmark", i).seed(), a, keep, true, rng.range(-14, -2), detail, taken);
		}
		int islets = 12;
		for (int k = 0; k < islets; k++) {
			double a = k * Math.PI * 2 / islets + rng.range(-0.2, 0.2);
			double r = rng.range(2.5, 4.8);
			double dist = past(a, keep, r + rng.range(1, 14));
			int x = (int) Math.round(Math.cos(a) * dist), z = (int) Math.round(Math.sin(a) * dist);
			if (clash(taken, x, z, r + 3)) continue;
			taken.add(new Taken(x, z, r));
			islet(out, d, lib, rng.stream("islet", k), x, rng.range(-buildDepth - 10, 8), z, r, detail);
		}
		if (d.feature.equals("cloud")) {
			for (int k = 0; k < 7; k++) {
				double a = rng.range(0, Math.PI * 2);
				double dist = past(a, keep, 12 + rng.range(0, 24));
				cloud(out, rng.stream("cloud", k), (int) Math.round(Math.cos(a) * dist), rng.range(26, 38), (int) Math.round(Math.sin(a) * dist));
			}
		}
	}

	/** How far out along angle {@code a} a disc of radius {@code r} must be to stay {@code gap} clear of the square. */
	static double past(double a, double half, double r) {
		double c = Math.max(Math.abs(Math.cos(a)), 1e-6), s = Math.max(Math.abs(Math.sin(a)), 1e-6);
		// the distance to the square's edge along the ray, then on until the disc is clear of the corner too
		double d = Math.min(half / c, half / s);
		while (outside(Math.cos(a) * d, Math.sin(a) * d, half) < r) d += 1;
		return d;
	}

	/** The distance from a point to the square [-half, half] x [-half, half] (0 inside). */
	static double outside(double x, double z, double half) {
		return Math.hypot(Math.max(0, Math.abs(x) - half), Math.max(0, Math.abs(z) - half));
	}

	static boolean clash(List<Taken> taken, double x, double z, double r) {
		for (Taken t : taken) {
			if (Math.hypot(t.x - x, t.z - z) < t.r + r) return true;
		}
		return false;
	}

	/**
	 * A landmark on its own rock, facing the arena: built at the origin (or taken from the cache), moved out until
	 * its rock is clear of the play area, the rock drawn under its whole footprint.
	 */
	static void landmark(ArenaPlan out, Dress d, Library lib, String arch, long seed, double angle, double keep, boolean square, int y, int detail,
			List<Taken> taken) {
		if (Architect.get(arch) == null) return;
		Dir face = Dir.of((int) Math.round(-Math.cos(angle) * 10), (int) Math.round(-Math.sin(angle) * 10));
		Built built = built(d, lib, arch, Math.floorMod(seed, VARIETY), face, Math.min(detail, 1));
		int[] box = built.box;
		double ox = (box[0] + box[3]) / 2.0, oz = (box[2] + box[5]) / 2.0;
		double half = Math.hypot(box[3] - box[0], box[5] - box[2]) / 2.0 + 2;
		double dist = square ? past(angle, keep, half) : keep + half;
		double cx = Math.cos(angle) * dist, cz = Math.sin(angle) * dist;
		if (clash(taken, cx, cz, half)) return;
		taken.add(new Taken(cx, cz, half));
		int dx = (int) Math.round(cx - ox), dz = (int) Math.round(cz - oz);
		Building rock = BedWars.container("rock:" + arch, d, lib, Xform.at((int) Math.round(cx), y - 1, (int) Math.round(cz)), seed ^ 0x5EEDL, detail);
		Ctx c = new Ctx(rock, Xform.ID, new Rng(seed).stream("rock"), new Rng(seed).stream("rock-decor"));
		Isle land = new Isle(seed, half, 0.12, 0.45, Isle.Symmetry.NONE);
		land.draw(c, BedWars.hanging(d));
		BedWars.earth(rock, seed);
		rock.emit(out.voxels);
		built.voxels.forEach((x, yy, z, id) -> out.voxels.set(x + dx, yy + y, z + dz, id));
		out.backdrop.add(rock);
		out.log.add("landmark " + arch + " at " + Math.round(cx) + "," + y + "," + Math.round(cz) + " (" + built.brief + ")");
	}

	/** How many different landmarks of one archetype a theme variant has (the rest of the variety is placement). */
	static final int VARIETY = 6;

	/** A landmark built at the origin (floor at y = 0), as finished blocks with its box and brief. */
	private record Built(Voxels voxels, int[] box, String brief) {}

	/** Landmarks built recently: building one costs tens of milliseconds, copying it almost nothing. */
	private static final java.util.Map<String, Built> CACHE = new java.util.LinkedHashMap<>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<String, Built> e) {
			return size() > 24;
		}
	};

	private static Built built(Dress d, Library lib, String arch, int variety, Dir face, int detail) {
		String key = arch + "|" + d + "|" + variety + "|" + face + "|" + detail;
		synchronized (CACHE) {
			Built b = CACHE.get(key);
			if (b != null) return b;
		}
		long seed = Rng.mix((arch + "|" + d).hashCode() * 31L + variety);
		double scale = arch.equals("castle") ? 0.8 : arch.equals("watchtower") ? 1.1 : 0.9;
		Brief br = Architect.brief(arch, seed, d.palette, scale);
		br.palette = d.palette.child("landmark:" + arch);
		br.paletteRecipe = null;
		br.detail = detail;
		if (!arch.equals("temple_ruin")) br.condition = d.condition;
		Building b = Architect.build(br, lib, Xform.facing(face, 0, 0, 0), null);
		Voxels v = new Voxels(1 << 15);
		b.emit(v);
		Built out = new Built(v, b.worldBox(), br.toString());
		synchronized (CACHE) {
			CACHE.put(key, out);
		}
		return out;
	}

	/** A small island with a tree or the theme's scenery, a lamp now and then and some plants. */
	static void islet(ArenaPlan out, Dress d, Library lib, Rng r, int x, int y, int z, double radius, int detail) {
		Building b = BedWars.container("islet", d, lib, Xform.at(x, y, z), r.seed(), detail);
		Ctx c = new Ctx(b, Xform.ID, r.stream("geo"), r.stream("decor"));
		Isle land = new Isle(r.seed(), radius, 0.15, 0.8, Isle.Symmetry.NONE);
		land.draw(c, BedWars.hanging(d));
		Rng decor = r.stream("tree");
		if (decor.chance(0.75)) {
			Kit.tree(c, d, decor, 0, 1, 0, 4 + decor.nextInt(2));
		} else {
			Kit.feature(c, d, decor, 0, 1, 0, 2);
		}
		if (radius > 3.5 && decor.chance(0.5)) Kit.post(c, d, 2, 1, -1, 3);
		BedWars.plants(c, d, land, r.stream("plants"), List.of());
		BedWars.earth(b, r.seed());
		b.emit(out.voxels);
		out.backdrop.add(b);
	}

	/** A flat, lumpy cloud. */
	static void cloud(ArenaPlan out, Rng r, int x, int y, int z) {
		int len = r.range(6, 12), wid = r.range(3, 6);
		for (int dx = -len; dx <= len; dx++) {
			for (int dz = -wid; dz <= wid; dz++) {
				double e = (double) (dx * dx) / (len * len) + (double) (dz * dz) / (wid * wid);
				if (e > 1) continue;
				out.voxels.set(x + dx, y, z + dz, "white_wool");
				if (e < 0.45 && Rng.hash(r.seed(), dx >> 1, 0, dz >> 1) < 0.6) out.voxels.set(x + dx, y + 1, z + dz, "white_wool");
			}
		}
	}
}
