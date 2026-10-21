package com.extremedimensions.data;

/**
 * A ruined portal in the Overworld with a warm chain lying into it. Its other end is in the Nether: the first crossing
 * through the gate raises the chamber there, once.
 */
public class WarmGate {
	/** The ruined portal, by the low corner of its structure's box ("x|y|z"): each one is looked at once, ever. */
	public String key;
	/** The foot of the frame, in front of its opening, and the way the opening faces (a horizontal direction's name). */
	public int x, y, z;
	public String facing = "north";
	/** Where the links of the chain lie (x, y, z each): embers rise from them. */
	public java.util.List<int[]> links = new java.util.ArrayList<>();
	/** The chamber at the other end, once raised: where its middle is, who raised it, and whether it held the Held's own things. */
	public boolean chamber = false;
	public int cx, cy, cz;
	public String by = "";
	public boolean full = false;

	public WarmGate() {}

	public WarmGate(String key, int x, int y, int z, String facing) {
		this.key = key;
		this.x = x;
		this.y = y;
		this.z = z;
		this.facing = facing;
	}

	public double distSq(double ox, double oy, double oz) {
		double dx = x + 0.5 - ox, dy = y + 0.5 - oy, dz = z + 0.5 - oz;
		return dx * dx + dy * dy + dz * dz;
	}
}
