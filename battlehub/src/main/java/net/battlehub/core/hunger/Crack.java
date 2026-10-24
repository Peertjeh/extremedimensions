package net.battlehub.core.hunger;

/**
 * Breaking the seal of a supply crate (without Minecraft): a tribute sneaks next to it for {@link #TICKS} ticks in a
 * row; standing up, walking away or being hurt starts it over. One crate, one tribute at a time.
 */
public final class Crack {
	/** Five seconds of sneaking. */
	public static final int TICKS = 100;
	/** How far from the crate's middle (blocks) the tribute may be. */
	public static final double REACH = 2.5;

	private int progress;
	private boolean open;

	/**
	 * One tick of the crate: whether its tribute sneaks within reach and was not hurt. True on the tick it opens.
	 */
	public boolean tick(boolean sneakingClose, boolean hurt) {
		if (open) return false;
		if (!sneakingClose || hurt) {
			progress = 0;
			return false;
		}
		if (++progress >= TICKS) {
			open = true;
			return true;
		}
		return false;
	}

	public int progress() {
		return progress;
	}

	/** Share done, 0..1. */
	public double share() {
		return Math.min(1.0, progress / (double) TICKS);
	}

	public boolean open() {
		return open;
	}
}
