package net.battlehub.core.ai;

/** What an NPC can choose to do in a fight. One is active at a time; the decider owns the switch. */
public enum Tactic {
	/** Get to the target (along a route when needed). */
	APPROACH(false),
	/** Fight in reach: hit when the attack is charged, strafe, crit, w-tap. */
	STRIKE(false),
	/** Bow or crossbow: keep a good distance, draw, lead the target and let go. */
	SHOOT(false),
	/** Hold just outside the other's reach and punish them when they step in. */
	SPACE(false),
	/** Back off (low health, losing the trade). */
	RETREAT(false),
	/** Sidestep incoming projectiles. */
	EVADE(true),
	/** Eat to heal, at a distance. */
	HEAL(false),
	/** Get away from a deadly edge first. */
	RECOVER(true),
	/** Go to where the target was last seen. */
	SEARCH(false),
	/** Work on the game's objective (a goal, a hill, a flag). */
	OBJECTIVE(false),
	/** Nothing to do. */
	IDLE(false);

	/** Safety tactics interrupt anything and are never replaced by a deliberate mistake. */
	public final boolean safety;

	Tactic(boolean safety) {
		this.safety = safety;
	}
}
