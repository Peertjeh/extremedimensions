package net.battlehub.core.ai;

/**
 * What the game wants from an NPC right now, besides fighting, in four kinds:
 * <ul>
 * <li>{@link Kind#ATTACK}: get to a point (the enemy flag, bed or goal) and fight only what is in the way;</li>
 * <li>{@link Kind#HOLD}: stand inside a zone (a hill) and fight there, not chase people out of it;</li>
 * <li>{@link Kind#DEFEND}: stay near something (an own bed, flag or goal) and fight who comes close to it;</li>
 * <li>{@link Kind#CARRY}: bring something home (a flag): avoid fights, only hit who blocks the way.</li>
 * </ul>
 *
 * @param radius how close counts as there (inside the zone, at the post)
 * @param leash  for HOLD and DEFEND: enemies further than this from the point are left alone
 * @param what   a short name for explanations ("the hill", "the enemy bed")
 */
public record Objective(Kind kind, V3 point, double radius, double leash, String what) {
	public enum Kind { ATTACK, HOLD, DEFEND, CARRY }

	public static Objective attack(V3 point, String what) {
		return new Objective(Kind.ATTACK, point, 1.5, 0, what);
	}

	public static Objective hold(V3 point, double radius, String what) {
		return new Objective(Kind.HOLD, point, radius, radius + 4, what);
	}

	public static Objective defend(V3 point, double radius, double leash, String what) {
		return new Objective(Kind.DEFEND, point, radius, leash, what);
	}

	public static Objective carry(V3 point, String what) {
		return new Objective(Kind.CARRY, point, 1.5, 0, what);
	}

	/** Horizontally within the radius, and not far above or below. */
	public boolean contains(V3 pos) {
		return pos.flatDistance(point) <= radius && Math.abs(pos.y() - point.y()) <= 3;
	}

	/** For HOLD and DEFEND: is this position too far from the point to go after? */
	public boolean beyondLeash(V3 pos) {
		return (kind == Kind.HOLD || kind == Kind.DEFEND) && pos.flatDistance(point) > leash;
	}
}
