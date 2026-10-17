package net.battlehub.arch.check;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Plan;
import net.battlehub.arch.Props;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.mat.Catalog;
import net.battlehub.core.GridPath;

/**
 * Where a player can walk, for the path checks: over finished blocks ({@link #of(Voxels)}) or over a building's
 * plan ({@link #of(Plan)}). Stairs and slabs count as steps (one block up), doors as passable (players open
 * them), flowers, grass, carpets and the like as air.
 */
public final class Walk {
	private Walk() {}

	interface Kind {
		/** 0 = passable, 1 = support you can stand on, 2 = blocks the way but not standable (fences, panes). */
		int at(int x, int y, int z);
	}

	/** Small plants of the arena themes that players walk through. */
	static final java.util.Set<String> PASSABLE = java.util.Set.of("dead_bush", "crimson_roots", "warped_roots", "nether_sprouts", "crimson_fungus",
			"warped_fungus", "red_mushroom", "brown_mushroom", "short_dry_grass", "tall_dry_grass", "blue_orchid", "firefly_bush", "snow", "bush",
			"spore_blossom", "sea_pickle", "tall_grass", "large_fern", "weeping_vines");

	/** 0 = passable, 1 = support you can stand on, 2 = in the way but not standable (fences, panes, lanterns). */
	public static int kind(String id) {
		return ofName(id);
	}

	static int ofName(String id) {
		if (id == null || id.equals("air")) return 0;
		String b = Props.base(id);
		if (b.endsWith("_door")) return 0;
		if (b.endsWith("_trapdoor")) return "true".equals(Props.get(id, "open")) ? 0 : 1;
		if (b.endsWith("_fence") || b.endsWith("_wall") && !b.contains("wall_") || b.endsWith("_pane") || b.endsWith("_bars") || b.endsWith("_fence_gate")) return 2;
		if (b.equals("water") || b.equals("lava")) return 0;
		Catalog.Kind k = Catalog.kind(b);
		if (b.endsWith("_slab") || b.endsWith("_stairs")) return 1;
		if (k == null) return 1;
		if (k.small()) {
			// lanterns, chests, pots... you cannot walk through them, but carpets and plants you can
			return b.endsWith("_carpet") || b.contains("grass") || b.contains("fern") || b.contains("flower") || b.contains("tulip") || b.contains("petals")
					|| b.equals("vine") || b.equals("glow_lichen") || b.equals("poppy") || b.equals("dandelion") || b.equals("cornflower") || b.equals("allium")
					|| b.equals("azure_bluet") || b.equals("oxeye_daisy") || b.equals("lily_of_the_valley") || b.equals("leaf_litter") || b.equals("hanging_roots")
					|| b.equals("iron_chain") || b.equals("cobweb") || PASSABLE.contains(b) ? 0 : 2;
		}
		return 1;
	}

	static int ofCell(Cell c) {
		if (c == null || c.air()) return 0;
		if (c.slot() == Slot.LITERAL) return ofName(c.literal());
		if (c.shape() == Shape.DOOR) return 0;
		if (c.shape() == Shape.TRAPDOOR) return c.variant() == 1 ? 0 : 1;
		if (c.shape() == Shape.WALL || c.shape() == Shape.FENCE || c.shape() == Shape.PANE) return 2;
		if (c.slot() == Slot.WATER) return 0;
		return 1;
	}

	static GridPath.Terrain terrain(Kind k) {
		return new GridPath.Terrain() {
			@Override
			public boolean stand(int x, int y, int z) {
				return k.at(x, y, z) == 0 && k.at(x, y + 1, z) == 0 && k.at(x, y - 1, z) == 1;
			}

			@Override
			public boolean open(int x, int y, int z) {
				return k.at(x, y, z) == 0;
			}
		};
	}

	public static GridPath.Terrain of(Voxels v) {
		return terrain((x, y, z) -> ofName(v.get(x, y, z)));
	}

	public static GridPath.Terrain of(Plan p) {
		return terrain((x, y, z) -> ofCell(p.get(x, y, z)));
	}

	/** The first height at or below y + 3 where a player could stand in this column (for start and goal points). */
	public static int standY(GridPath.Terrain t, int x, int y, int z) {
		for (int yy = y + 3; yy >= y - 6; yy--) {
			if (t.stand(x, yy, z)) return yy;
		}
		return Integer.MIN_VALUE;
	}
}
