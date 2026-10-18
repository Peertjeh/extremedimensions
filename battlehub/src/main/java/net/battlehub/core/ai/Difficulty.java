package net.battlehub.core.ai;

import java.util.Locale;

import net.battlehub.core.BotLevel;

/**
 * How well an NPC plays. A higher level decides and reacts better: it sees changes sooner, thinks more often,
 * predicts further, aims more precisely, makes fewer mistakes and uses more techniques. It never gets more health,
 * more damage or more information than a player.
 */
public enum Difficulty {
	//          react think  aim  mistake predict crit  strafe wtap   shield heal  turn  commit margin ready
	BEGINNER(14, 10, 14.0, 0.35, 0, 0.00, 0.10, false, 0.00, 0, 10, 30, 0.25, 1.00),
	CASUAL(10, 8, 9.0, 0.22, 2, 0.10, 0.30, false, 0.10, 5, 15, 24, 0.20, 0.97),
	INTERMEDIATE(6, 5, 5.0, 0.12, 4, 0.30, 0.60, false, 0.30, 8, 25, 16, 0.15, 0.93),
	ADVANCED(4, 4, 3.0, 0.06, 6, 0.50, 0.80, true, 0.50, 9, 35, 12, 0.12, 0.92),
	EXPERT(3, 3, 2.0, 0.03, 8, 0.70, 0.90, true, 0.60, 10, 45, 10, 0.10, 0.90),
	MASTER(2, 2, 1.2, 0.01, 10, 0.85, 1.00, true, 0.70, 11, 60, 8, 0.08, 0.90);

	/** Ticks before reacting to something new (a target that appears); 2 ticks is about the fastest human. */
	public final int reactionTicks;
	/** Ticks between decisions (steering still happens every tick). */
	public final int thinkTicks;
	/** Aim wobble in degrees. */
	public final double aimError;
	/** Chance to pick a worse (but never unsafe) option. */
	public final double mistakeRate;
	/** How many ticks ahead it predicts where a moving target will be. */
	public final int predictTicks;
	/** Skill at jump-crits (0..1). */
	public final double critSkill;
	/** Skill at strafing (0..1): how much and how cleverly it moves sideways. */
	public final double strafeSkill;
	/** Lets go of forward right after a hit, for more knockback. */
	public final boolean wTap;
	/** How readily it raises a shield when it has one (0..1). */
	public final double shieldSkill;
	/** Below this health (of 20) it eats a golden apple when it can (0: never). */
	public final double healBelow;
	/** Degrees it can turn per tick. */
	public final float turnSpeed;
	/** Ticks a decision is kept before a slightly better option may replace it. */
	public final int commitTicks;
	/** How much better another option must be to switch (stops dithering). */
	public final double switchMargin;
	/** Attack strength (0..1) it waits for before swinging. */
	public final double readyToHit;

	Difficulty(int reactionTicks, int thinkTicks, double aimError, double mistakeRate, int predictTicks, double critSkill,
			double strafeSkill, boolean wTap, double shieldSkill, double healBelow, float turnSpeed, int commitTicks,
			double switchMargin, double readyToHit) {
		this.reactionTicks = reactionTicks;
		this.thinkTicks = thinkTicks;
		this.aimError = aimError;
		this.mistakeRate = mistakeRate;
		this.predictTicks = predictTicks;
		this.critSkill = critSkill;
		this.strafeSkill = strafeSkill;
		this.wTap = wTap;
		this.shieldSkill = shieldSkill;
		this.healBelow = healBelow;
		this.turnSpeed = turnSpeed;
		this.commitTicks = commitTicks;
		this.switchMargin = switchMargin;
		this.readyToHit = readyToHit;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The old three-step level the other game styles (parkour, prop hunt) still read their numbers from. */
	public BotLevel legacy() {
		return switch (this) {
			case BEGINNER, CASUAL -> BotLevel.EASY;
			case INTERMEDIATE -> BotLevel.NORMAL;
			case ADVANCED, EXPERT, MASTER -> BotLevel.HARD;
		};
	}

	/** The level behind the three old choices: easy, normal and hard. */
	public static Difficulty of(BotLevel level) {
		return switch (level) {
			case EASY -> CASUAL;
			case NORMAL -> INTERMEDIATE;
			case HARD -> EXPERT;
		};
	}

	public static Difficulty parse(String s, Difficulty fallback) {
		if (s == null) return fallback;
		for (Difficulty d : values()) {
			if (d.id().equalsIgnoreCase(s.trim())) return d;
		}
		BotLevel l = BotLevel.parse(s, null);
		return l == null ? fallback : of(l);
	}
}
