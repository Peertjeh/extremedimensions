package net.battlehub.core.ai;

/**
 * What a player can perceive, as rules a bot follows too:
 * <ul>
 * <li><b>sight</b> covers what is in front of the eyes: within {@link #VIEW} degrees of where the head points
 * (about what a player sees on screen with a common field of view), and only with nothing in between;</li>
 * <li>a <b>name tag</b> shows through walls, but only on screen as well, and not while someone sneaks;</li>
 * <li><b>hearing</b> picks up footsteps of someone close who walks or runs (not who sneaks or stands still), and
 * the swing and sound of a hit taken tell where the attacker is.</li>
 * </ul>
 * So a bot that turns its back loses track of you, and finds you again by turning round.
 */
public final class Senses {
	private Senses() {}

	/** Half the field of view, in degrees from where the head points. */
	public static final double VIEW = 65;
	/** How far footsteps carry, in blocks. */
	public static final double HEARING = 12;
	/** For how long a hit taken tells where the attacker is, in ticks. */
	public static final int HIT_MEMORY = 30;

	/** Is {@code at} within the field of view of an eye at {@code eye} looking along yaw/pitch (Minecraft angles)? */
	public static boolean inView(V3 eye, float yaw, float pitch, V3 at) {
		double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
		double lx = -Math.sin(ry) * Math.cos(rp), ly = -Math.sin(rp), lz = Math.cos(ry) * Math.cos(rp);
		V3 to = at.sub(eye);
		double len = Math.sqrt(to.x() * to.x() + to.y() * to.y() + to.z() * to.z());
		if (len < 1e-6) return true;
		double cos = (lx * to.x() + ly * to.y() + lz * to.z()) / len;
		return cos >= Math.cos(Math.toRadians(VIEW));
	}

	/** Can the steps (or the hit) of someone at this distance be heard? */
	public static boolean heard(double dist, boolean sneaking, double speed, int ticksSinceTheyHitMe) {
		if (ticksSinceTheyHitMe <= HIT_MEMORY) return true;
		return dist <= HEARING && !sneaking && speed > 0.05;
	}
}
