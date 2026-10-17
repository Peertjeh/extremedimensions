package net.battlehub.arch.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.arch.Voxels;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Building;

/**
 * A generated arena as finished block names plus what the game and the checks need to know: named points
 * (spawns, beds, generators, shops), spawn positions with their yaw, the spectator point, the box players may
 * build in, the islands with their frames (for the fairness check) and the zones that must stay free.
 */
public final class ArenaPlan {
	/** An island: what it is for, its index (team), centre, frame (local +z towards the middle) and radius. */
	public record Isle(String kind, int index, int cx, int cz, Xform frame, double radius) {}

	/** A box (world coordinates, inclusive) that the map must leave empty: players build or stand there. */
	public record Zone(String name, int x0, int y0, int z0, int x1, int y1, int z1) {
		public boolean contains(int x, int y, int z) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
		}
	}

	/** Where a player starts; yaw 0 faces south (+z). */
	public record Spawn(double x, double y, double z, float yaw) {}

	public final Voxels voxels = new Voxels(1 << 17);
	public final Map<String, List<int[]>> marks = new LinkedHashMap<>();
	public final List<Spawn> spawns = new ArrayList<>();
	public final List<Isle> isles = new ArrayList<>();
	public final List<Zone> zones = new ArrayList<>();
	public final List<Building> buildings = new ArrayList<>();
	/** Landmarks and islands in the distance, out of play. */
	public final List<Building> backdrop = new ArrayList<>();
	public final List<String> log = new ArrayList<>();
	public Dress dress;
	public double[] spectator = {0.5, 30, 0.5, 0, 89};
	public int voidY = -13;
	public int radius = 60;
	/** The box players may build in (inclusive), or null. */
	public int[] buildMin, buildMax;

	public void mark(String name, int x, int y, int z) {
		marks.computeIfAbsent(name, k -> new ArrayList<>()).add(new int[] {x, y, z});
	}

	public List<int[]> marks(String name) {
		return marks.getOrDefault(name, List.of());
	}

	public int[] mark(String name) {
		List<int[]> l = marks(name);
		return l.isEmpty() ? null : l.get(0);
	}

	public List<Isle> isles(String kind) {
		List<Isle> out = new ArrayList<>();
		for (Isle i : isles) {
			if (i.kind().equals(kind)) out.add(i);
		}
		return out;
	}
}
