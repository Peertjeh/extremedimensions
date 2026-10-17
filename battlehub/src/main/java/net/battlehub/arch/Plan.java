package net.battlehub.arch;

/**
 * A sparse block plan: cells by position, where drawing respects priorities (a window cut into a wall stays open
 * when more wall is drawn over it). Stored as an open-addressing map from packed positions to interned cells, so
 * even very large buildings stay small in memory.
 */
public final class Plan {
	public interface Visitor {
		void at(int x, int y, int z, Cell c);
	}

	private final LongIntMap map;
	private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

	public Plan() {
		this(1 << 12);
	}

	public Plan(int capacity) {
		map = new LongIntMap(capacity);
	}

	/** Packs a position: x and z in 24 bits each, y in 16. */
	public static long key(int x, int y, int z) {
		return ((long) (x & 0xFFFFFF) << 40) | ((long) (z & 0xFFFFFF) << 16) | (y & 0xFFFF);
	}

	public static int kx(long k) {
		return (int) (k >> 40) << 8 >> 8;
	}

	public static int kz(long k) {
		return (int) ((k >> 16) & 0xFFFFFF) << 8 >> 8;
	}

	public static int ky(long k) {
		return (short) (k & 0xFFFF);
	}

	public Cell get(int x, int y, int z) {
		int v = map.get(key(x, y, z), -1);
		return v < 0 ? null : Cell.byId(v);
	}

	public boolean has(int x, int y, int z) {
		return map.contains(key(x, y, z));
	}

	/** A cell that is there and not explicit air. */
	public boolean filled(int x, int y, int z) {
		Cell c = get(x, y, z);
		return c != null && !c.air();
	}

	public boolean solid(int x, int y, int z) {
		Cell c = get(x, y, z);
		return c != null && c.solid();
	}

	/** Draws {@code c} unless a cell with a higher priority is already there; true when drawn. */
	public boolean put(int x, int y, int z, Cell c) {
		if (c == null) return false;
		long k = key(x, y, z);
		int old = map.get(k, -1);
		if (old >= 0 && Cell.byId(old).prio() > c.prio()) return false;
		if (map.put(k, Cell.id(c))) bound(x, y, z);
		return true;
	}

	/** Draws {@code c} whatever is there. */
	public void force(int x, int y, int z, Cell c) {
		if (map.put(key(x, y, z), Cell.id(c))) bound(x, y, z);
	}

	/** Only where nothing is planned yet. */
	public boolean putIfEmpty(int x, int y, int z, Cell c) {
		if (has(x, y, z)) return false;
		return put(x, y, z, c);
	}

	public void remove(int x, int y, int z) {
		map.remove(key(x, y, z));
	}

	private void bound(int x, int y, int z) {
		if (x < minX) minX = x;
		if (y < minY) minY = y;
		if (z < minZ) minZ = z;
		if (x > maxX) maxX = x;
		if (y > maxY) maxY = y;
		if (z > maxZ) maxZ = z;
	}

	public int size() {
		return map.size();
	}

	public void forEach(Visitor v) {
		map.forEach((k, id) -> v.at(kx(k), ky(k), kz(k), Cell.byId(id)));
	}

	/** Positions of every cell (a snapshot, so the plan can be changed while going through them). */
	public long[] positions() {
		return map.keys();
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

	public boolean isEmpty() {
		return map.size() == 0;
	}

	public Plan copy() {
		Plan p = new Plan(size() + 16);
		forEach(p::force);
		return p;
	}

	/** The topmost filled y in a column, or Integer.MIN_VALUE. */
	public int top(int x, int z) {
		for (int y = maxY; y >= minY; y--) {
			if (filled(x, y, z)) return y;
		}
		return Integer.MIN_VALUE;
	}
}
