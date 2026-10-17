package net.battlehub.arch.mat;

import net.battlehub.arch.Dir;
import net.battlehub.arch.Props;
import net.battlehub.arch.Voxels;

/**
 * Blocks are written without neighbour updates, so the shapes the game would work out by itself are worked out
 * here: walls, fences, panes and bars join their neighbours, and stairs turn their inner and outer corners.
 */
public final class Connect {
	private Connect() {}

	private static String base(String id) {
		return id == null ? null : Props.base(id);
	}

	static boolean isWall(String b) {
		return b != null && b.endsWith("_wall") && !b.contains("wall_");
	}

	static boolean isFence(String b) {
		return b != null && b.endsWith("_fence");
	}

	static boolean isPane(String b) {
		return b != null && (b.endsWith("_pane") || b.endsWith("_bars"));
	}

	static boolean isStairs(String b) {
		return b != null && b.endsWith("_stairs");
	}

	/** A full block face to lean on (glass counts, as in the game). */
	static boolean sturdy(String id) {
		if (id == null || id.equals("air")) return false;
		String b = base(id);
		if (b.endsWith("_slab")) return id.contains("type=double");
		Catalog.Kind k = Catalog.kind(b);
		if (k == null) return false;
		Material m = Catalog.ofBlock(b);
		if (m != null && (m.is("glass") || m.is("leaves"))) return b.equals(m.cube()) && m.is("glass");
		return k.full();
	}

	public static void apply(Voxels v) {
		v.forEach((x, y, z, id) -> {
			String b = base(id);
			if (isWall(b)) {
				v.set(x, y, z, wall(v, x, y, z, b));
			} else if (isFence(b) || isPane(b)) {
				v.set(x, y, z, fenceOrPane(v, x, y, z, id, b));
			} else if (isStairs(b)) {
				String s = stairShape(v, x, y, z, id);
				if (s != null) v.set(x, y, z, Props.with(id, "shape", s));
			}
		});
	}

	private static String wall(Voxels v, int x, int y, int z, String b) {
		boolean[] side = new boolean[4];
		for (Dir d : Dir.values()) {
			String n = v.get(x + d.dx, y, z + d.dz);
			String nb = base(n);
			side[d.ordinal()] = isWall(nb) || isPane(nb) || sturdy(n) || nb != null && nb.endsWith("_fence_gate");
		}
		String above = v.get(x, y + 1, z);
		boolean tallAbove = sturdy(above) || isWall(base(above));
		boolean straightNS = side[0] && side[2] && !side[1] && !side[3];
		boolean straightEW = side[1] && side[3] && !side[0] && !side[2];
		boolean postAbove = above != null && !above.equals("air") && !isWall(base(above));
		boolean up = !(straightNS || straightEW) || postAbove && !sturdy(above);
		StringBuilder s = new StringBuilder(b).append('[');
		for (Dir d : Dir.values()) {
			String h = side[d.ordinal()] ? (tallAbove ? "tall" : "low") : "none";
			s.append(d.id()).append('=').append(h).append(',');
		}
		return s.append("up=").append(up).append(']').toString();
	}

	private static String fenceOrPane(Voxels v, int x, int y, int z, String id, String b) {
		boolean pane = isPane(b);
		String out = id;
		for (Dir d : Dir.values()) {
			String n = v.get(x + d.dx, y, z + d.dz);
			String nb = base(n);
			boolean join = pane ? isPane(nb) || isWall(nb) || sturdy(n) : isFence(nb) && sameKind(b, nb) || sturdy(n) || nb != null && nb.endsWith("_fence_gate");
			out = Props.with(out, d.id(), Boolean.toString(join));
		}
		return out;
	}

	/** Nether brick fences join each other, wooden fences join wooden fences. */
	private static boolean sameKind(String a, String b) {
		return a.startsWith("nether_brick") == b.startsWith("nether_brick");
	}

	/** The game's own rule for corner stairs. */
	private static String stairShape(Voxels v, int x, int y, int z, String id) {
		Dir f = Dir.byId(Props.get(id, "facing"));
		String half = Props.get(id, "half");
		if (f == null) return null;
		String behind = v.get(x + f.dx, y, z + f.dz);
		if (isStairs(base(behind)) && half.equals(Props.get(behind, "half"))) {
			Dir d2 = Dir.byId(Props.get(behind, "facing"));
			if (d2 != null && d2.alongX() != f.alongX() && canTake(v, x, y, z, f, half, d2.back())) {
				return d2 == f.left() ? "outer_left" : "outer_right";
			}
		}
		String front = v.get(x - f.dx, y, z - f.dz);
		if (isStairs(base(front)) && half.equals(Props.get(front, "half"))) {
			Dir d3 = Dir.byId(Props.get(front, "facing"));
			if (d3 != null && d3.alongX() != f.alongX() && canTake(v, x, y, z, f, half, d3)) {
				return d3 == f.left() ? "inner_left" : "inner_right";
			}
		}
		return "straight";
	}

	private static boolean canTake(Voxels v, int x, int y, int z, Dir f, String half, Dir side) {
		String n = v.get(x + side.dx, y, z + side.dz);
		if (!isStairs(base(n))) return true;
		return !f.id().equals(Props.get(n, "facing")) || !half.equals(Props.get(n, "half"));
	}
}
