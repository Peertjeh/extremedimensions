package net.battlehub.arch.town;

/**
 * Which columns of the lobby are taken (plaza, roads, buildings with a margin), so nothing is placed on top of
 * anything else; streets are remembered separately so houses can face them.
 */
public final class Occupancy {
	private final int min, size;
	private final byte[] cells;

	public Occupancy(int radius) {
		this.min = -radius;
		this.size = 2 * radius + 1;
		this.cells = new byte[size * size];
	}

	private int idx(int x, int z) {
		int ix = x - min, iz = z - min;
		if (ix < 0 || iz < 0 || ix >= size || iz >= size) return -1;
		return iz * size + ix;
	}

	public boolean used(int x, int z) {
		int i = idx(x, z);
		return i < 0 || cells[i] != 0;
	}

	public boolean road(int x, int z) {
		int i = idx(x, z);
		return i >= 0 && (cells[i] & 2) != 0;
	}

	public boolean street(int x, int z) {
		int i = idx(x, z);
		return i >= 0 && (cells[i] & 4) != 0;
	}

	public void street(int x, int z, boolean on) {
		int i = idx(x, z);
		if (i >= 0) cells[i] |= on ? 6 : 0;
	}

	public void mark(int x, int z) {
		int i = idx(x, z);
		if (i >= 0) cells[i] |= 2;
	}

	public void rect(int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int i = idx(x, z);
				if (i >= 0) cells[i] |= 1;
			}
		}
	}

	public void disc(int cx, int cz, double r) {
		int ir = (int) Math.ceil(r);
		for (int x = cx - ir; x <= cx + ir; x++) {
			for (int z = cz - ir; z <= cz + ir; z++) {
				if (Math.hypot(x - cx, z - cz) <= r) rect(x, z, x, z);
			}
		}
	}

	public boolean free(int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (used(x, z)) return false;
			}
		}
		return true;
	}
}
