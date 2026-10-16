package net.battlehub.arena.gen;

import java.util.List;

import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.battlehub.core.FloorPattern;
import net.battlehub.core.Noise;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The look of one generated arena: which of the theme's colour gradients the floor and walls use, the pattern the
 * floor follows, its size and direction, and how much the borders fray. Chosen at random, so every arena of a
 * theme looks a little different.
 */
public final class Style {
	public final Theme theme;
	private final BlockState[] floor;
	private final BlockState[] wall;
	private final FloorPattern pattern;
	private final Noise noise;
	private final double angle;
	private final double scale;
	private final double fray;
	private final int radius;

	private Style(Theme theme, BlockState[] floor, BlockState[] wall, FloorPattern pattern, long seed, double angle, double scale,
			double fray, int radius) {
		this.theme = theme;
		this.floor = floor;
		this.wall = wall;
		this.pattern = pattern;
		this.noise = new Noise(seed);
		this.angle = angle;
		this.scale = scale;
		this.fray = fray;
		this.radius = radius;
	}

	/** A random look for an arena of about this radius. */
	public static Style of(Theme t, RandomSource rnd, int radius) {
		List<String[]> floors = Palettes.floors(t);
		List<String[]> walls = Palettes.walls(t);
		BlockState[] floor = floors.isEmpty() ? null : states(floors.get(rnd.nextInt(floors.size())));
		BlockState[] wall = walls.isEmpty() ? null : states(walls.get(rnd.nextInt(walls.size())));
		// Some arenas run the gradient the other way round
		if (floor != null && rnd.nextBoolean()) reverse(floor);
		FloorPattern[] patterns = FloorPattern.values();
		FloorPattern pattern = patterns[rnd.nextInt(patterns.length)];
		double scale = 5 + rnd.nextDouble() * 9;
		return new Style(t, floor, wall, pattern, rnd.nextLong(), rnd.nextDouble() * Math.PI, scale, 0.1 + rnd.nextDouble() * 0.25,
				radius);
	}

	private static BlockState[] states(String[] ids) {
		BlockState[] out = new BlockState[ids.length];
		for (int i = 0; i < ids.length; i++) {
			out[i] = Build.s(ids[i]);
		}
		return out;
	}

	private static void reverse(BlockState[] a) {
		for (int i = 0, j = a.length - 1; i < j; i++, j--) {
			BlockState tmp = a[i];
			a[i] = a[j];
			a[j] = tmp;
		}
	}

	public FloorPattern pattern() {
		return pattern;
	}

	/** The floor block of column (x, z): its place in the pattern, frayed a little. */
	public BlockState floor(int x, int z, RandomSource rnd) {
		if (floor == null) {
			return theme.floor(rnd);
		}
		double t = pattern.t(noise, x, z, radius, angle, scale);
		return floor[FloorPattern.step(t, floor.length, noise.hash(x, z), fray)];
	}

	/** A floor block by a place on the gradient (0..1), e.g. for an inlay in the middle. */
	public BlockState floorAt(double t) {
		return floor == null ? theme.accent() : floor[FloorPattern.step(t, floor.length, 0.5, 0)];
	}

	/** The wall block {@code above} blocks over its foot, for a wall {@code height} high; null when the theme keeps its own. */
	public BlockState wall(int above, int height, int x, int z) {
		if (wall == null) {
			return null;
		}
		double t = (above + 1.0) / (height + 2.0);
		return wall[FloorPattern.step(t, wall.length, noise.hash(x * 3 + above, z), 0.3)];
	}
}
