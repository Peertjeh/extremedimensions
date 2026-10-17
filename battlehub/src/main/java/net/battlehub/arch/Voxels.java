package net.battlehub.arch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finished blocks by world position, as block names ("stone_brick_stairs[facing=north,half=bottom]"): what a
 * building or the whole lobby comes out as, ready to be written into the game or drawn in a preview.
 */
public final class Voxels {
	public interface Visitor {
		void at(int x, int y, int z, String id);
	}

	private final LongIntMap map;
	private final List<String> names = new ArrayList<>();
	private final Map<String, Integer> index = new HashMap<>();
	private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

	public Voxels() {
		this(1 << 14);
	}

	public Voxels(int capacity) {
		map = new LongIntMap(capacity);
	}

	private int intern(String id) {
		Integer i = index.get(id);
		if (i != null) return i;
		int n = names.size();
		names.add(id);
		index.put(id, n);
		return n;
	}

	public void set(int x, int y, int z, String id) {
		if (map.put(Plan.key(x, y, z), intern(id))) {
			if (x < minX) minX = x;
			if (y < minY) minY = y;
			if (z < minZ) minZ = z;
			if (x > maxX) maxX = x;
			if (y > maxY) maxY = y;
			if (z > maxZ) maxZ = z;
		}
	}

	/** The block here, or null when nothing is set. */
	public String get(int x, int y, int z) {
		int v = map.get(Plan.key(x, y, z), -1);
		return v < 0 ? null : names.get(v);
	}

	/** Set and not air. */
	public boolean filled(int x, int y, int z) {
		String s = get(x, y, z);
		return s != null && !s.equals("air");
	}

	public void remove(int x, int y, int z) {
		map.remove(Plan.key(x, y, z));
	}

	public int size() {
		return map.size();
	}

	public void forEach(Visitor v) {
		map.forEach((k, id) -> v.at(Plan.kx(k), Plan.ky(k), Plan.kz(k), names.get(id)));
	}

	/** The distinct block names used. */
	public List<String> names() {
		return List.copyOf(names);
	}

	/** How many blocks of each name (for checks and statistics). */
	public Map<String, Integer> counts() {
		int[] n = new int[names.size()];
		map.forEach((k, id) -> n[id]++);
		Map<String, Integer> out = new HashMap<>();
		for (int i = 0; i < n.length; i++) {
			if (n[i] > 0) out.merge(names.get(i), n[i], Integer::sum);
		}
		return out;
	}

	public int minX() {
		return minX;
	}

	public int minY() {
		return minY;
	}

	public int minZ() {
		return minZ;
	}

	public int maxX() {
		return maxX;
	}

	public int maxY() {
		return maxY;
	}

	public int maxZ() {
		return maxZ;
	}
}
