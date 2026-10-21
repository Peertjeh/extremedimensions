package com.extremedimensions.data;

/** A Lurker hoard: a hidden nest with a barrel of stolen things, guarded by a Keeper. */
public class Hoard {
	public String dim;
	public int x, y, z;
	/** UUID of the player whose things fill it; empty for an old hoard found in the wild. */
	public String owner = "";
	public boolean keeperSlain = false;
	/** Raided and plundered: no raid starts on it again. */
	public boolean raided = false;
	/** Young killed and not yet replaced, and when (epoch ms) the next of them may come back. */
	public int lost = 0;
	public long youngBack = 0;

	public Hoard() {}

	public Hoard(String dim, int x, int y, int z, String owner) {
		this.dim = dim;
		this.x = x;
		this.y = y;
		this.z = z;
		this.owner = owner;
	}

	public String key() {
		return dim + "/" + x + "/" + y + "/" + z;
	}

	public double distSq(double ox, double oy, double oz) {
		double dx = x + 0.5 - ox, dy = y + 0.5 - oy, dz = z + 0.5 - oz;
		return dx * dx + dy * dy + dz * dz;
	}
}
