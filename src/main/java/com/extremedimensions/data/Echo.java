package com.extremedimensions.data;

/**
 * Where someone collapsed beyond the wound: the place remembers. Some time later an echo shows there to whoever comes
 * near, the collapsed player's shape kneeling in chains; listened to, it lets go. One echo per Collapse.
 */
public class Echo {
	/** The world (short id, as {@link com.extremedimensions.server.Dims#of}) and the spot. */
	public String dim;
	public int x, y, z;
	/** Who collapsed there, and their name when they did. */
	public String owner;
	public String name = "";
	/** When (milliseconds since the epoch). */
	public long at;
	/** The atonement that death cost them: half of it comes back when the echo lets go. */
	public int toll;
	/** In a severed world: one of the things it destroyed (an item stack as JSON), given back when the echo lets go. */
	public String lost;

	// --- runtime only ---
	/** Whether its shape and chains stand there now. */
	public transient boolean shown = false;
	/** Ticks the one listening has stood by it. */
	public transient java.util.Map<String, Integer> heard = new java.util.HashMap<>();

	public Echo() {}

	public Echo(String dim, int x, int y, int z, String owner, String name, long at, int toll) {
		this.dim = dim;
		this.x = x;
		this.y = y;
		this.z = z;
		this.owner = owner;
		this.name = name;
		this.at = at;
		this.toll = toll;
	}

	public double distSq(double ox, double oy, double oz) {
		double dx = x + 0.5 - ox, dy = y - oy, dz = z + 0.5 - oz;
		return dx * dx + dy * dy + dz * dz;
	}

	/** A key that names it for display entities' tags. */
	public String tag() {
		return "ed_echo_" + Integer.toHexString((dim + x + "," + y + "," + z + at).hashCode());
	}
}
