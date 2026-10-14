package net.battlehub.arena.layout;

import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.battlehub.arena.gen.Features;
import net.battlehub.arena.gen.Field;
import net.battlehub.arena.gen.Shape;
import net.battlehub.arena.gen.Symmetry;
import net.battlehub.core.ParkourCourse;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Arena shapes for the party games, each dressed in one of the five themes. */
public final class PartyLayouts {
	private PartyLayouts() {}

	/** One floor layer of breakable blocks in the shape, at height y. */
	private static void floorLayer(Blueprint bp, Theme t, RandomSource rnd, Shape shape, int r, int y, boolean mark) {
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				if (shape.inside(x, z, r)) {
					bp.set(x, y, z, Decor.breakable(t, rnd));
					if (mark) bp.mark("floor", x, y, z);
				}
			}
		}
	}

	/** Bow Spleef: one floor over the void, in a random shape and size; arrows break it. Floor is y=0. */
	public static Blueprint spleef(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		Shape shape = Shape.roundish(rnd);
		int r = 12 + rnd.nextInt(6);
		floorLayer(bp, t, rnd, shape, r, 0, true);
		Decor.ring(bp, t, rnd, r + 6, r + 13, -3);
		bp.spawnRing(16, Math.max(5, Math.round(r * 0.6)), 1);
		bp.spectator(0.5, 18, -(r + 6) + 0.5, 0, 40);
		bp.voidY(-6);
		bp.radius(r + 18);
		return bp;
	}

	/** TNT Run: {@code layers} floors 7 blocks apart, a breakable top block on TNT, random shape. Top floor is y=0. */
	public static Blueprint tntRun(Theme t, RandomSource rnd, int layers) {
		Blueprint bp = new Blueprint();
		Shape shape = Shape.roundish(rnd);
		int r = 11 + rnd.nextInt(5);
		BlockState tnt = Build.s("tnt");
		for (int l = 0; l < layers; l++) {
			int y = -7 * l;
			floorLayer(bp, t, rnd, shape, r, y, false);
			for (int x = -r - 1; x <= r + 1; x++) {
				for (int z = -r - 1; z <= r + 1; z++) {
					if (shape.inside(x, z, r)) bp.set(x, y - 1, z, tnt);
				}
			}
			bp.mark("layer", 0, y, 0);
		}
		Decor.ring(bp, t, rnd, r + 6, r + 13, -3);
		bp.spawnRing(16, Math.max(4, Math.round(r * 0.55)), 1);
		bp.spectator(0.5, 14, -(r + 7) + 0.5, 0, 35);
		bp.voidY(-7 * (layers - 1) - 7);
		bp.radius(r + 18);
		return bp;
	}

	/** Sumo: a small platform in a random shape; knock everyone off. Platform top is y=0. */
	public static Blueprint sumo(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int r = 6 + rnd.nextInt(4);
		Field f = new Field(r, Shape.roundish(rnd), Symmetry.POINT);
		f.build(bp, t, rnd, 6);
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				boolean edge = f.inside(x, z) && (!f.inside(x + 1, z) || !f.inside(x - 1, z) || !f.inside(x, z + 1) || !f.inside(x, z - 1));
				if (edge) bp.set(x, 0, z, t.trim());
			}
		}
		Build.disc(bp, 0, 0, 0, 1.2, t::accent);
		Decor.ring(bp, t, rnd, r + 7, r + 14, -8);
		bp.spawnRing(16, Math.max(3, Math.round(r * 0.55)), 1);
		bp.spectator(0.5, 9, -(r + 8) + 0.5, 0, 30);
		bp.voidY(-4);
		bp.radius(r + 18);
		return bp;
	}

	/**
	 * King of the Hill: a walled arena with a hill in the middle (stepped, pyramid or plateau with ramps) and
	 * random cover; the zone is the 5x5 top at y=3.
	 */
	public static Blueprint koth(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int r = 22 + rnd.nextInt(4);
		Field f = new Field(r, Shape.roundish(rnd), Symmetry.POINT);
		double ring = Math.round(r * 0.8);
		f.keepBandClear(ring - 1.6, ring + 1.6);
		f.hills(rnd, 3 + rnd.nextInt(3), 1);
		int style = rnd.nextInt(3);
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				int m = Math.max(Math.abs(x), Math.abs(z));
				double d = Math.sqrt(x * x + z * z);
				int hgt = switch (style) {
					case 0 -> m <= 2 || d <= 3 ? 3 : (d <= 5.5 ? 2 : (d <= 8 ? 1 : 0));
					case 1 -> m <= 2 ? 3 : (m <= 4 ? 2 : (m <= 6 ? 1 : 0));
					default -> {
						// Plateau reached by four ramps
						if (m <= 2) yield 3;
						int along = Math.abs(z) <= 1 ? Math.abs(x) : (Math.abs(x) <= 1 ? Math.abs(z) : 99);
						yield along <= 5 ? Math.max(0, 6 - along) : 0;
					}
				};
				if (hgt > 0 || d <= 8) {
					f.setHeight(x, z, hgt);
				}
			}
		}
		f.use(0, 0, 8);
		Features.scatter(bp, f, t, rnd, 4 + rnd.nextInt(4), Features.kinds(t, false));
		f.build(bp, t, rnd, 5);
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				bp.set(x, 3, z, t.marker());
			}
		}
		bp.mark("zone", 0, 4, 0);
		f.walls(bp, t, rnd, 4);
		Features.plants(bp, f, t, rnd, 9);
		bp.spawnRing(16, ring, 1);
		bp.spectator(0.5, 18, -(r + 6) + 0.5, 0, 35);
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
		// Random cover on the red half, mirrored to the blue half
		java.util.List<int[]> placed = new java.util.ArrayList<>();
		java.util.List<Features.Kind> kinds = Features.kinds(t, false);
		int want = 5 + rnd.nextInt(4);
		for (int attempt = 0; attempt < 80 && placed.size() < want; attempt++) {
			int x = -(4 + rnd.nextInt(16));
			int z = rnd.nextInt(23) - 11;
			boolean clear = true;
			for (int[] o : placed) {
				if (Math.abs(o[0] - x) < 6 && Math.abs(o[1] - z) < 6) clear = false;
			}
			if (!clear || (Math.abs(x) <= 3 && Math.abs(z) <= 4)) continue;
			placed.add(new int[] {x, z});
			Blueprint fb = new Blueprint();
			Features.build(fb, t, rnd, kinds.get(rnd.nextInt(kinds.size())));
			Features.stamp(bp, fb, Symmetry.MIRROR_X, x, 1, z);
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

	/** Slab used for half-block parkour steps in each theme. */
	private static BlockState slab(Theme t) {
		String id = switch (t) {
			case FOREST, MUSHROOM -> "oak_slab";
			case CASTLE -> "stone_brick_slab";
			case NETHER -> "nether_brick_slab";
			case ICE, SKY -> "smooth_quartz_slab";
			case DESERT -> "sandstone_slab";
			case JUNGLE -> "jungle_slab";
			case CHERRY -> "cherry_slab";
			case END -> "end_stone_brick_slab";
			case BADLANDS -> "red_sandstone_slab";
			case DEEP_DARK -> "polished_deepslate_slab";
			case OCEAN -> "prismarine_slab";
		};
		return Build.slab(id, false);
	}

	/** Fence post for the tiny parkour landings in each theme. */
	private static BlockState fence(Theme t) {
		return Build.s(switch (t) {
			case NETHER -> "nether_brick_fence";
			case JUNGLE -> "jungle_fence";
			case CHERRY -> "cherry_fence";
			case DESERT, BADLANDS -> "acacia_fence";
			case DEEP_DARK, END -> "dark_oak_fence";
			case ICE, SKY -> "birch_fence";
			case MUSHROOM -> "mangrove_fence";
			default -> "oak_fence";
		});
	}

	/**
	 * Parkour Race: the generated course, centred on the origin along the X axis. Platforms can be full blocks,
	 * slabs, fence posts, ice or a column with a ladder on the side the course comes from.
	 */
	public static Blueprint parkour(Theme t, RandomSource rnd, List<ParkourCourse.Platform> course) {
		Blueprint bp = new Blueprint();
		int shift = -(course.get(course.size() - 1).x() / 2);
		int minY = 0;
		int maxZ = 0;
		for (ParkourCourse.Platform p : course) {
			int px = p.x() + shift;
			int h = p.half();
			maxZ = Math.max(maxZ, Math.abs(p.z()) + h);
			minY = Math.min(minY, p.y() - (p.style() == ParkourCourse.Style.LADDER ? p.climb() : 0));
			if (p.style() == ParkourCourse.Style.LADDER) {
				int bottom = p.y() - p.climb() + 1;
				for (int y = bottom; y <= p.y(); y++) {
					bp.set(px, y, p.z(), y == p.y() ? t.floor(rnd) : t.pillar());
				}
				// The ladder hangs on the side facing back along the course, so it faces the player
				int lx = px - ParkourCourse.dx(p.dir());
				int lz = p.z() - ParkourCourse.dz(p.dir());
				net.minecraft.core.Direction facing = switch (p.dir()) {
					case ParkourCourse.SOUTH -> net.minecraft.core.Direction.NORTH;
					case ParkourCourse.NORTH -> net.minecraft.core.Direction.SOUTH;
					default -> net.minecraft.core.Direction.WEST;
				};
				for (int y = bottom; y <= p.y(); y++) {
					bp.set(lx, y, lz, Build.facing("ladder", facing));
				}
				continue;
			}
			if (p.style() == ParkourCourse.Style.FENCE) {
				bp.set(px, p.y(), p.z(), fence(t));
				continue;
			}
			BlockState top = switch (p.kind()) {
				case START -> t.trim();
				case CHECKPOINT -> Build.s("gold_block");
				case FINISH -> Build.s("emerald_block");
				default -> switch (p.style()) {
					case SLAB -> slab(t);
					case ICE -> Build.s(rnd.nextBoolean() ? "blue_ice" : "packed_ice");
					default -> rnd.nextInt(4) == 0 ? t.accent() : t.floor(rnd);
				};
			};
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
			int z = rnd.nextInt(2 * maxZ + 31) - (maxZ + 15);
			Build.island(bp, x, minY - 14 - rnd.nextInt(6), z, 2 + rnd.nextDouble() * 2.5, 4, () -> t.floor(rnd), t.under(), t.rough(), rnd);
		}
		bp.spectator(sx + 0.5, start.y() + 12, start.z() - 12 + 0.5, -45, 25);
		bp.voidY(minY - 6);
		bp.radius(Math.max(len / 2, maxZ) + 30);
		return bp;
	}

	/** Last Man Standing: a generated walled field with hills and plenty of cover; the border shrinks around it. */
	/** Free for All: a wide field with plenty of cover and raised platforms; sixteen spawns on a ring. */
	public static Blueprint ffa(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int r = 28 + rnd.nextInt(6);
		Field f = new Field(r, Shape.roundish(rnd), Symmetry.POINT);
		double ring = Math.round(r * 0.72);
		f.keepBandClear(ring - 1.6, ring + 1.6);
		f.hills(rnd, 6 + rnd.nextInt(4), 2);
		Features.scatter(bp, f, t, rnd, 12 + rnd.nextInt(6), Features.kinds(t, true));
		f.build(bp, t, rnd, 5);
		f.walls(bp, t, rnd, 4);
		Features.plants(bp, f, t, rnd, 8);
		bp.spawnRing(16, ring, 1);
		bp.spectator(0.5, 30, -(r + 8) + 0.5, 0, 45);
		bp.voidY(-10);
		bp.radius(r + 20);
		return bp;
	}

	public static Blueprint lms(Theme t, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		int r = 26 + rnd.nextInt(5);
		Field f = new Field(r, Shape.roundish(rnd), Symmetry.POINT);
		double ring = Math.round(r * 0.75);
		f.keepBandClear(ring - 1.6, ring + 1.6);
		f.keepClear(0, 0, 3);
		f.hills(rnd, 5 + rnd.nextInt(4), 2);
		Features.scatter(bp, f, t, rnd, 9 + rnd.nextInt(5), Features.kinds(t, true));
		f.build(bp, t, rnd, 5);
		f.walls(bp, t, rnd, 4);
		Features.plants(bp, f, t, rnd, 8);
		bp.spawnRing(16, ring, 1);
		bp.spectator(0.5, 28, -(r + 8) + 0.5, 0, 45);
		bp.voidY(-10);
		bp.radius(r + 20);
		return bp;
	}
}
