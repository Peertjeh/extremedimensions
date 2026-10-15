package net.battlehub.core;

import java.util.Locale;

/**
 * How well a bot plays. Pure numbers, so the bot code and the menus share one definition. Reaction and aim make
 * the difference in fights; the parkour and hunting numbers in the other games.
 */
public enum BotLevel {
	/** Walks, swings late and misses a lot: for learning the controls. */
	EASY(12, 14.0, 0.0, false, false, 0.0, 6.0, 0.40, 3.0, 4.0, 0.0),
	/** Sprints, strafes and jump-crits now and then. */
	NORMAL(6, 6.0, 0.25, true, false, 0.20, 2.5, 0.18, 5.0, 8.0, 0.35),
	/** Fast reactions, accurate, w-taps and eats when low. */
	HARD(3, 2.5, 0.5, true, true, 0.45, 1.0, 0.06, 8.0, 10.0, 0.6);

	/** Ticks before the bot reacts to a new target. */
	public final int reactionTicks;
	/** Random aim error in degrees. */
	public final double aimError;
	/** Chance per second to change strafe direction (0: no strafing). */
	public final double strafe;
	public final boolean sprints;
	/** Lets go of forward for a tick after a hit, for more knockback. */
	public final boolean wTap;
	/** Chance to jump for a critical hit when closing in. */
	public final double critChance;
	/** Bow aim error in degrees. */
	public final double bowError;
	/** Chance to misjudge a parkour jump. */
	public final double parkourFail;
	/** Prop Hunt: how close a hunter must be to notice a prop. */
	public final double noticeRange;
	/** Below this health the bot eats a golden apple when it has one (0: never). */
	public final double eatBelow;
	/** Chance per second to block with a shield when an enemy is close. */
	public final double shield;

	BotLevel(int reactionTicks, double aimError, double strafe, boolean sprints, boolean wTap, double critChance,
			double bowError, double parkourFail, double noticeRange, double eatBelow, double shield) {
		this.reactionTicks = reactionTicks;
		this.aimError = aimError;
		this.strafe = strafe;
		this.sprints = sprints;
		this.wTap = wTap;
		this.critChance = critChance;
		this.bowError = bowError;
		this.parkourFail = parkourFail;
		this.noticeRange = noticeRange;
		this.eatBelow = eatBelow;
		this.shield = shield;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** easy, normal or hard (also "medium"); anything else gives {@code fallback}. */
	public static BotLevel parse(String id, BotLevel fallback) {
		if (id == null) {
			return fallback;
		}
		String s = id.trim().toLowerCase(Locale.ROOT);
		if (s.equals("medium") || s.equals("gemiddeld") || s.equals("normaal")) {
			return NORMAL;
		}
		if (s.equals("makkelijk")) {
			return EASY;
		}
		if (s.equals("moeilijk")) {
			return HARD;
		}
		for (BotLevel l : values()) {
			if (l.id().equals(s)) {
				return l;
			}
		}
		return fallback;
	}
}
