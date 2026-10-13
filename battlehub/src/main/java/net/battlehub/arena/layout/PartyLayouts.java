package net.battlehub.arena.layout;

import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.battlehub.core.ParkourCourse;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Arena shapes for the party games, each dressed in one of the five themes. */
public final class PartyLayouts {
	private PartyLayouts() {}

	/** Bow Spleef: one round floor over the void; arrows break it. Floor is y=0. */
	public static Blueprint spleef(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int r = 15;
		Build.disc(bp, 0, 0, 0, r, () -> Decor.breakable(t, rnd));
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (bp.has(x, 0, z)) bp.mark("floor", x, 0, z);
			}
		}
		Decor.ring(bp, t, rnd, r + 6, r + 13, -3);
		bp.spawnRing(16, 10, 1);
		bp.spectator(0.5, 18, -(r + 6) + 0.5, 0, 40);
		bp.voidY(-6);
		bp.radius(r + 18);
		return bp;
	}

	/** TNT Run: {@code layers} floors 7 blocks apart, a breakable top block on TNT. Top floor is y=0. */
	public static Blueprint tntRun(Theme t, RandomSource rnd, int layers) {
		Blueprint bp = new Blueprint();
		int r = 13;
		BlockState tnt = Build.s("tnt");
		for (int l = 0; l < layers; l++) {
			int y = -7 * l;
			Build.disc(bp, 0, y, 0, r, () -> Decor.breakable(t, rnd));
			Build.disc(bp, 0, y - 1, 0, r, () -> tnt);
			bp.mark("layer", 0, y, 0);
		}
		Decor.ring(bp, t, rnd, r + 6, r + 13, -3);
		bp.spawnRing(16, 8, 1);
		bp.spectator(0.5, 14, -(r + 7) + 0.5, 0, 35);
		bp.voidY(-7 * (layers - 1) - 7);
		bp.radius(r + 18);
		return bp;
	}

	/** Sumo: a small round platform; knock everyone off. Platform top is y=0. */
	public static Blueprint sumo(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int r = 7;
		Build.island(bp, 0, 0, 0, r, 6, () -> t.floor(rnd), t.under(), t.rough(), rnd);
		Build.ring(bp, 0, 0, 0, r - 1, r, t::trim);
		Build.disc(bp, 0, 0, 0, 1.2, t::accent);
		Decor.ring(bp, t, rnd, r + 7, r + 14, -8);
		bp.spawnRing(16, 4.5, 1);
		bp.spectator(0.5, 9, -(r + 8) + 0.5, 0, 30);
		bp.voidY(-4);
		bp.radius(r + 18);
		return bp;
	}

	/** King of the Hill: walled round arena with a stepped hill; the zone is the 5x5 top (y=3). */
	public static Blueprint koth(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int r = 23;
		Build.island(bp, 0, 0, 0, r + 2, 5, () -> t.floor(rnd), t.under(), t.rough(), rnd);
		for (int y = 1; y <= 4; y++) {
			Build.ring(bp, 0, y, 0, r, r + 2, t::wall);
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			bp.set((int) Math.round(Math.cos(a) * (r + 1)), 5, (int) Math.round(Math.sin(a) * (r + 1)), t.light());
		}
		Build.disc(bp, 0, 1, 0, 8, t::wall);
		Build.disc(bp, 0, 2, 0, 5.5, t::rough);
		Build.disc(bp, 0, 3, 0, 3, t::marker);
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				bp.set(x, 3, z, t.marker());
			}
		}
		bp.mark("zone", 0, 4, 0);
		// Cover around the hill
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + Math.PI / 6;
			int x = (int) Math.round(Math.cos(a) * 14);
			int z = (int) Math.round(Math.sin(a) * 14);
			Build.pillar(bp, x, 1, z, 3, t.pillar());
			bp.set(x, 4, z, t.light());
		}
		bp.spawnRing(16, 19, 1);
		bp.spectator(0.5, 16, -(r + 6) + 0.5, 0, 35);
		bp.voidY(-10);
		bp.radius(r + 18);
		return bp;
	}

	/** Capture The Flag: a long walled field with a red base (west) and a blue base (east). */
	public static Blueprint ctf(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int hx = 34;
		int hz = 15;
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				bp.set(x, 0, z, t.floor(rnd));
				bp.set(x, -1, z, t.under());
				if (Math.abs(x) < hx - 2 && Math.abs(z) < hz - 2) bp.set(x, -2, z, t.rough());
			}
		}
		for (int y = 1; y <= 4; y++) {
			for (int x = -hx; x <= hx; x++) {
				bp.set(x, y, -hz, t.wall());
				bp.set(x, y, hz, t.wall());
			}
			for (int z = -hz; z <= hz; z++) {
				bp.set(-hx, y, z, t.wall());
				bp.set(hx, y, z, t.wall());
			}
		}
		for (int x = -hx; x <= hx; x += 6) {
			bp.set(x, 5, -hz, t.light());
			bp.set(x, 5, hz, t.light());
		}
		base(bp, t, -28, "red");
		base(bp, t, 28, "blue");
		// Middle: cover walls and pillars
		for (int z = -3; z <= 3; z++) {
			bp.set(0, 1, z, t.wall());
			bp.set(0, 2, z, t.wall());
		}
		int[][] cover = {{-12, 7}, {-12, -7}, {12, 7}, {12, -7}, {-6, 0}, {6, 0}};
		for (int[] c : cover) {
			for (int dx = -1; dx <= 1; dx++) {
				bp.set(c[0] + dx, 1, c[1], t.pillar());
				bp.set(c[0] + dx, 2, c[1], t.pillar());
			}
			bp.set(c[0], 3, c[1], t.light());
		}
		for (int i = 0; i < 8; i++) {
			bp.mark("spawn_red", -24 + (i % 2) * 2, 1, -6 + (i / 2) * 4);
			bp.mark("spawn_blue", 24 - (i % 2) * 2, 1, -6 + (i / 2) * 4);
		}
		bp.spawn(-24.5, 1, 0.5, -90);
		bp.spawn(24.5, 1, 0.5, 90);
		bp.spectator(0.5, 22, -(hz + 10) + 0.5, 0, 40);
		bp.voidY(-10);
		bp.radius(hx + 20);
		return bp;
	}

	private static void base(Blueprint bp, Theme t, int cx, String color) {
		BlockState wool = Build.s(color + "_wool");
		BlockState concrete = Build.s(color + "_concrete");
		for (int x = cx - 4; x <= cx + 4; x++) {
			for (int z = -4; z <= 4; z++) {
				boolean edge = Math.abs(x - cx) == 4 || Math.abs(z) == 4;
				bp.set(x, 0, z, edge ? wool : concrete);
			}
		}
		int dir = cx < 0 ? -1 : 1;
		int fx = cx + dir * 2;
		bp.set(fx, 1, 0, t.accent());
		bp.mark("flag_" + color, fx, 2, 0);
		bp.mark("base_" + color, fx, 2, 0);
		bp.set(cx - 4, 1, -4, t.light());
		bp.set(cx + 4, 1, 4, t.light());
		bp.set(cx - 4, 1, 4, t.light());
		bp.set(cx + 4, 1, -4, t.light());
	}

	/** Parkour Race: the generated course, centred on the origin along the X axis. */
	public static Blueprint parkour(Theme t, RandomSource rnd, List<ParkourCourse.Platform> course) {
		Blueprint bp = new Blueprint();
		int shift = -(course.get(course.size() - 1).x() / 2);
		int minY = 0;
		for (ParkourCourse.Platform p : course) {
			int px = p.x() + shift;
			BlockState top = switch (p.kind()) {
				case START -> t.trim();
				case CHECKPOINT -> Build.s("gold_block");
				case FINISH -> Build.s("emerald_block");
				default -> rnd.nextInt(4) == 0 ? t.accent() : t.floor(rnd);
			};
			int h = p.half();
			for (int dx = -h; dx <= h; dx++) {
				for (int dz = -h; dz <= h; dz++) {
					bp.set(px + dx, p.y(), p.z() + dz, top);
					if (p.size() > 1) bp.set(px + dx, p.y() - 1, p.z() + dz, t.under());
				}
			}
			if (p.kind() == ParkourCourse.Kind.CHECKPOINT || p.kind() == ParkourCourse.Kind.FINISH) {
				bp.set(px + h, p.y() + 1, p.z() + h, t.light());
				bp.set(px - h, p.y() + 1, p.z() - h, t.light());
			}
			minY = Math.min(minY, p.y());
		}
		// Start platform spawns (everyone starts together)
		ParkourCourse.Platform start = course.get(0);
		int sx = start.x() + shift;
		for (int i = 0; i < 16; i++) {
			int dx = (i % 4) - 2;
			int dz = (i / 4) - 2;
			bp.spawn(sx + dx + 0.5, start.y() + 1, start.z() + dz + 0.5, -90);
		}
		// Scenery far below the course
		int len = course.get(course.size() - 1).x();
		for (int i = 0; i < len / 12 + 2; i++) {
			int x = shift + rnd.nextInt(len + 1);
			int z = rnd.nextInt(30) - 15;
			if (Math.abs(z) < 6) z += z < 0 ? -8 : 8;
			Build.island(bp, x, minY - 12 - rnd.nextInt(6), z, 2 + rnd.nextDouble() * 2.5, 4, () -> t.floor(rnd), t.under(), t.rough(), rnd);
		}
		bp.spectator(sx + 0.5, start.y() + 12, start.z() - 12 + 0.5, -45, 25);
		bp.voidY(minY - 6);
		bp.radius(len / 2 + 30);
		return bp;
	}

	/** Last Man Standing: a square field with scattered cover; the border shrinks around it. */
	public static Blueprint lms(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int h = 32;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				bp.set(x, 0, z, t.floor(rnd));
				bp.set(x, -1, z, t.under());
				if (Math.abs(x) < h - 3 && Math.abs(z) < h - 3) bp.set(x, -2, z, t.rough());
			}
		}
		for (int i = 0; i < 26; i++) {
			int x = rnd.nextInt(2 * h - 6) - (h - 3);
			int z = rnd.nextInt(2 * h - 6) - (h - 3);
			double d = Build.dist(x, z);
			if (d < 5 || Math.abs(d - 24) < 2.5) {
				continue;
			}
			switch (t) {
				case FOREST -> Build.tree(bp, x, 1, z, 4 + rnd.nextInt(3), Build.s("oak_log"), Build.leaves("oak_leaves"), rnd);
				case CASTLE -> {
					boolean alongX = rnd.nextBoolean();
					int len = 3 + rnd.nextInt(4);
					for (int k = 0; k < len; k++) {
						int wx = alongX ? x + k : x;
						int wz = alongX ? z : z + k;
						int wh = 1 + rnd.nextInt(3);
						Build.pillar(bp, wx, 1, wz, wh, rnd.nextInt(3) == 0 ? Build.s("mossy_stone_bricks") : t.wall());
					}
				}
				case NETHER -> {
					int ph = 2 + rnd.nextInt(4);
					Build.pillar(bp, x, 1, z, ph, Build.s(rnd.nextBoolean() ? "basalt" : "blackstone"));
					bp.set(x, 1 + ph, z, Build.s("shroomlight"));
				}
				case ICE -> Build.spike(bp, x, 1, z, 3 + rnd.nextInt(5), Build.s("packed_ice"));
				case SKY -> {
					Build.disc(bp, x, 3 + rnd.nextInt(3), z, 2, () -> t.floor(rnd));
					Build.pillar(bp, x, 1, z, 3, t.pillar());
				}
			}
		}
		bp.spawnRing(16, 24, 1);
		bp.spectator(0.5, 26, -(h + 8) + 0.5, 0, 45);
		bp.voidY(-10);
		bp.radius(h + 20);
		return bp;
	}
}
