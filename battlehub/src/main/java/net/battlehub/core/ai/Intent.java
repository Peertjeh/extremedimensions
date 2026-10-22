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
 * @param launch   throw a wind charge at its own feet to fly up (a mace smash from above)
 * @param hand     the learned model's choice of what to hold (an index in its spec's hands; -1: the bot's own rules
 *                 choose, as for every scripted intent)
 * @param use      with a hand chosen: hold the right mouse button (draw, eat, block, throw on pressing)
 */
public record Intent(double forward, double strafe, boolean sprint, boolean jump, V3 look, V3 moveTo, boolean attack, boolean block,
		boolean heal, boolean bow, boolean launch, int hand, boolean use) {
	public static final Intent NOTHING = new Intent(0, 0, false, false, null, null, false, false, false, false, false);

	public Intent(double forward, double strafe, boolean sprint, boolean jump, V3 look, V3 moveTo, boolean attack, boolean block, boolean heal,
			boolean bow, boolean launch) {
		this(forward, strafe, sprint, jump, look, moveTo, attack, block, heal, bow, launch, -1, false);
	}

	/** Without the bow ({@code bow}: aim at {@code look} as an arrow flies, draw and let go when charged). */
	public Intent(double forward, double strafe, boolean sprint, boolean jump, V3 look, V3 moveTo, boolean attack, boolean block, boolean heal) {
		this(forward, strafe, sprint, jump, look, moveTo, attack, block, heal, false, false);
	}

	public Intent(double forward, double strafe, boolean sprint, boolean jump, V3 look, V3 moveTo, boolean attack, boolean block, boolean heal,
			boolean bow) {
		this(forward, strafe, sprint, jump, look, moveTo, attack, block, heal, bow, false);
	}

	public Intent withLook(V3 at) {
		return new Intent(forward, strafe, sprint, jump, at, moveTo, attack, block, heal, bow, launch, hand, use);
	}

	public Intent withLaunch() {
		return new Intent(forward, strafe, sprint, jump, look, moveTo, attack, block, heal, bow, true, hand, use);
	}

	/** The learned model chose what to hold and the right button. */
	public boolean handChosen() {
		return hand >= 0;
	}
}
