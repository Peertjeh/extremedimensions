package net.battlehub.core.ai;

/**
 * What the NPC wants to do this tick, in the terms of a client's inputs. One intent per tick, made by one owner (the
 * steering), so movement and fighting never fight each other. The world layer carries it out through the normal
 * game rules and may still hold it back (an edge, a wall).
 *
 * @param forward  -1 (back) .. 1 (forward), used when {@code moveTo} is null
 * @param strafe   -1 (right) .. 1 (left)
 * @param look     where to look, or null (along the way)
 * @param moveTo   walk there along a route, or null to use forward/strafe
 * @param attack   swing at the target now (the world layer still checks reach and sight)
 * @param bow      use the bow or crossbow on the target: {@code look} is already led for the arrow's flight
 */
public record Intent(double forward, double strafe, boolean sprint, boolean jump, V3 look, V3 moveTo, boolean attack, boolean block,
		boolean heal, boolean bow) {
	public static final Intent NOTHING = new Intent(0, 0, false, false, null, null, false, false, false, false);

	/** Without the bow ({@code bow}: aim at {@code look} as an arrow flies, draw and let go when charged). */
	public Intent(double forward, double strafe, boolean sprint, boolean jump, V3 look, V3 moveTo, boolean attack, boolean block, boolean heal) {
		this(forward, strafe, sprint, jump, look, moveTo, attack, block, heal, false);
	}

	public Intent withLook(V3 at) {
		return new Intent(forward, strafe, sprint, jump, at, moveTo, attack, block, heal, bow);
	}
}
