package com.extremedimensions.expedition;

import java.util.Locale;

import com.extremedimensions.data.PlayerData;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;

import net.minecraft.server.level.ServerPlayer;

/**
 * What the time left on an expedition, or on a thread in the Seam, really comes to (3.55.0): how long it lasts at the
 * pace it runs out now, and what walking back to the way home would cost of it. The timer counts seconds of charge,
 * which a drain above one spends faster than the clock; these say it in the clock's seconds and in the walk's. Nothing
 * here changes how fast anything drains: it only says so.
 */
public final class TimeLeft {
	private TimeLeft() {}

	/** Blocks a second a player is reckoned to walk home at: walking, not sprinting, for the way is seldom straight. */
	public static final double WALK_SPEED = 4.3;
	/** The timer turns amber once what is left is less than this many times what the walk back costs. */
	public static final double TIGHT = 1.5;

	/**
	 * Seconds of charge (or of thread) the walk back to the way home would cost from where the player stands: to the
	 * arrival portal on an expedition, to the rift in the Seam; -1 where there is no way home to walk to. Beyond the wound
	 * the distance's share of the drain falls away evenly on the way in, so the walk costs what half way out costs, and
	 * the anchors' leaks run on beside it.
	 */
	public static double walkBack(ServerPlayer p, PlayerData d) {
		if (d.inSeam && Dims.SEAM.equals(d.dim)) {
			double dx = p.getX() - d.riftX, dz = p.getZ() - d.riftZ;
			return Math.sqrt(dx * dx + dz * dz) / WALK_SPEED * SeamService.drain(p);
		}
		if (!d.inRun || d.collapsing || d.tetherAnchor == null || !(Dims.NETHER.equals(d.dim) || Dims.END.equals(d.dim))) {
			return -1;
		}
		double far = TetherService.arrivalDistance(p, d);
		double factor = Math.max(1, TetherService.distanceFactor(p, d));
		// the drain without the distance's share, then the distance's share as it is on average on the way in
		double rest = d.currentDrain / factor;
		double[] leak = DrainCauses.leaks(p, d);
		return far / WALK_SPEED * (rest * (1 + factor) / 2 + leak[0] + leak[1]);
	}

	/** How long what is left lasts by the clock at this drain; -1 when it does not run out at all. */
	public static double pace(double left, double drain) {
		return drain <= 0 ? -1 : left / drain;
	}

	/** Whether what is left is getting short of the walk back: under {@link #TIGHT} times what it costs. */
	public static boolean tight(double left, double walkBack) {
		return walkBack > 0 && left < TIGHT * walkBack;
	}

	/** Seconds as the timer writes them, "1:42". */
	public static String clock(double secs) {
		int s = (int) Math.ceil(Math.max(0, secs));
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}
}
