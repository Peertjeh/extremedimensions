package net.battlehub.arena;

import java.util.List;
import java.util.Map;

import net.battlehub.arch.arena.ArenaPlan;
import net.battlehub.arch.arena.BedWars;
import net.battlehub.arch.arena.Surroundings;
import net.battlehub.arch.mod.Library;
import net.battlehub.config.ConfigManager;
import net.battlehub.hub.ArchMaterials;

/**
 * Arenas from the architecture engine ({@code net.battlehub.arch.arena}): the engine plans them as finished block
 * names with their named points; this turns a plan into a blueprint the arena builder places. Theme ids are the
 * same on both sides.
 */
public final class ArchArenas {
	private ArchArenas() {}

	private static Library library;

	static synchronized Library library() {
		if (library == null) {
			ArchMaterials.prepare(ConfigManager.config().lobby.discoverModBlocks);
			library = Library.defaults();
		}
		return library;
	}

	/** A Bed Wars map: team islands, diamond islands, the middle and the landmarks round it. */
	public static ArenaPlan bedwarsPlan(Theme t, long seed, int teams, int buildHeight, int buildDepth, boolean backdrop) {
		BedWars.Settings s = new BedWars.Settings();
		s.theme = t.id();
		s.seed = seed;
		s.teams = teams;
		s.buildHeight = buildHeight;
		s.buildDepth = buildDepth;
		s.backdrop = backdrop;
		return BedWars.generate(s, library());
	}

	public static Blueprint bedwars(Theme t, long seed, int teams, int buildHeight, int buildDepth) {
		return blueprint(bedwarsPlan(t, seed, teams, buildHeight, buildDepth, true));
	}

	/**
	 * Dresses an arena already in {@code bp}: stands round a walled arena on the ground ({@code y} at its floor),
	 * islands and landmarks round one over the void. Nothing is drawn over a column the arena uses, nothing
	 * already in the blueprint is replaced, and the arena so far is marked as where play happens.
	 */
	public static void surroundings(Blueprint bp, Theme t, long seed, int rIn, int rOut, int y) {
		java.util.Set<Long> used = new java.util.HashSet<>();
		int reach = 0;
		for (long k : bp.blocks().keySet()) {
			int x = net.minecraft.core.BlockPos.getX(k), z = net.minecraft.core.BlockPos.getZ(k);
			if (used.add(Blueprint.column(x, z))) reach = Math.max(reach, (int) Math.ceil(Math.hypot(x, z)));
		}
		if (bp.core() == null && !bp.isEmpty()) bp.core(bp.minX(), bp.minY(), bp.minZ(), bp.maxX(), bp.maxY(), bp.maxZ());
		Surroundings.Settings s = new Surroundings.Settings();
		s.theme = t.id();
		s.seed = seed;
		s.rIn = rIn;
		s.rOut = rOut;
		s.groundY = y;
		s.reach = reach;
		s.reserved = (x, z) -> used.contains(Blueprint.column(x, z));
		ArenaPlan p = Surroundings.generate(s, library());
		p.voxels.forEach((x, yy, z, id) -> {
			if (!id.equals("air")) bp.setIfEmpty(x, yy, z, Build.s(id));
		});
	}

	/**
	 * A piece of cover at (x, y, z) (y: the first block above the floor) turned {@code turns} quarters, and its
	 * copy on the other half of the arena: turned half round for point symmetry, mirrored across the z axis for
	 * mirror symmetry. Both copies are drawn from the same seed, so they are exactly alike.
	 */
	public static void cover(Blueprint bp, Theme t, long seed, String kind, int x, int y, int z, int turns, net.battlehub.arena.gen.Symmetry sym) {
		net.battlehub.arch.arena.Cover.Kind k = net.battlehub.arch.arena.Cover.Kind.valueOf(kind);
		net.battlehub.arch.Xform a = new net.battlehub.arch.Xform(turns, false, x, y, z);
		net.battlehub.arch.Xform b = sym == net.battlehub.arena.gen.Symmetry.POINT ? new net.battlehub.arch.Xform(turns + 2, false, -x, y, -z)
				: new net.battlehub.arch.Xform((4 - turns) % 4, true, -x, y, z);
		for (net.battlehub.arch.Xform f : new net.battlehub.arch.Xform[] {a, b}) {
			net.battlehub.arch.arena.Cover.piece(t.id(), seed, k, f, library()).forEach((px, py, pz, id) -> {
				if (!id.equals("air")) bp.set(px, py, pz, Build.s(id));
			});
		}
	}

	/** The plan as a blueprint: every block, the marks, spawns, spectator point, void line and build box. */
	public static Blueprint blueprint(ArenaPlan p) {
		Blueprint bp = new Blueprint();
		p.voxels.forEach((x, y, z, id) -> {
			if (!id.equals("air")) bp.set(x, y, z, Build.s(id));
		});
		for (Map.Entry<String, List<int[]>> e : p.marks.entrySet()) {
			for (int[] m : e.getValue()) bp.mark(e.getKey(), m[0], m[1], m[2]);
		}
		for (ArenaPlan.Spawn s : p.spawns) bp.spawn(s.x(), s.y(), s.z(), s.yaw());
		bp.spectator(p.spectator[0], p.spectator[1], p.spectator[2], (float) p.spectator[3], (float) p.spectator[4]);
		bp.voidY(p.voidY);
		bp.radius(p.radius);
		if (p.buildMin != null) {
			bp.include(p.buildMin[0], p.buildMin[1], p.buildMin[2]);
			bp.include(p.buildMax[0], p.buildMax[1], p.buildMax[2]);
			bp.core(p.buildMin[0], p.buildMin[1], p.buildMin[2], p.buildMax[0], p.buildMax[1], p.buildMax[2]);
		}
		return bp;
	}
}
