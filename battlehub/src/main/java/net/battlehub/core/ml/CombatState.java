package net.battlehub.core.ml;

/**
 * What a bot knows at one moment, as the melee model needs it: the same fields as a CombatState in the Python
 * simulation (ml/pvp_sim/obs.py, and for spec v2 ml/pvp_sim2/obs.py; the v2 fields' defaults leave v1 as it was). Minecraft's angles: yaw 0 looks along +z, 90 along -x; pitch positive looks down.
 * The opponent's position is where the bot last knew it; what it can only see (velocity, health, ground, sprint,
 * hurt, swing) may be filled in anyway, the observation masks it when {@code oppVisible} is false.
 * Rays: index 0 straight ahead, then every 45 degrees to the left (counter-clockwise seen from above).
 */
public final class CombatState {
	public double selfX, selfY, selfZ, selfVx, selfVy, selfVz, selfYaw, selfPitch;
	public boolean selfOnGround, selfSprinting;
	/** 0..1, the game's attack strength scale. */
	public double selfCharge;
	/** Ticks it cannot be hurt again in full (0..10): the game's hurtTime. */
	public double selfHurtTicks;
	public double selfHealth, selfMaxHealth = 20;

	public double oppX, oppY, oppZ, oppVx, oppVy, oppVz, oppYaw;
	public boolean oppOnGround, oppSprinting, oppVisible;
	public double oppSinceSwing = 100;
	public double oppHurtTicks;
	public double oppHealth = 20, oppMaxHealth = 20;

	/** Ground before a drop in each of the bot's directions (up to the spec's ray_max). */
	public final double[] edges = new double[8];
	/** Free room before a wall at body height in each of the bot's directions. */
	public final double[] walls = new double[8];

	// ---- spec v2 ----
	/** Golden hearts, food level, saturation, health back a second, how far it has been falling. */
	public double selfAbsorption, selfFood = 20, selfSaturation = 5, selfRegen, selfFall;
	/** Size (1 normal) and reach. */
	public double selfScale = 1, selfReach = 3;
	/** The weapon's full damage (with strength), sharpness bonus, ticks to charge, knockback level. */
	public double selfMeleeDamage = 1, selfMeleeBonus, selfCooldown = 5, selfKb;
	public double selfArmor, selfToughness, selfResistance;
	public double selfWalkSpeed = 0.1, selfJumpPower = 0.42, selfGravity = 0.08;
	/** What it carries (arrows only with a bow), the cooldowns, what it holds (index in the spec's hands). */
	public double selfArrows, selfFoodItems, selfGapples, selfPearls, selfWinds;
	public boolean selfShield;
	public double selfPearlCd, selfWindCd, selfShieldCd;
	public int selfHand;
	/** Ticks it has been using what it holds (drawing, eating, blocking); its shield is up and blocks. */
	public double selfUseTicks;
	public boolean selfBlocking;

	/** What it last saw of the target: absorption, shield up, how long it has been drawing, eating, what it holds. */
	public double oppAbsorption, oppDraw;
	public boolean oppBlocking, oppEating, oppHoldsBow, oppHoldsFood, oppHoldsThrowable;
	/** The target's weapon (as last seen) and armor, its resistance level and size. */
	public double oppMeleeDamage = 1, oppMeleeBonus, oppCooldown = 5, oppKb, oppArmor, oppToughness, oppResistance, oppScale = 1;

	/** The nearest other enemy, if any. */
	public boolean e2Present, e2Visible, e2HoldsBow;
	public double e2X, e2Y, e2Z, e2Yaw, e2SinceSwing = 100;

	/** The projectile coming at it, if any. */
	public boolean threatPresent;
	public double threatX, threatY, threatZ, threatVx, threatVy, threatVz;

	/** Whether hits do damage (not in sumo and boxing), and how many enemies it knows within the spec's near_enemies. */
	public boolean damageOn = true;
	public double enemiesNear;

	/** Terrain ({@link TerrainRays}): how far the ground is below it and below the target, ground behind the target. */
	public double selfBelow, oppBelow, oppDropBehind = 4, oppVoidBehind = 4;
	public final double[] voids = new double[8];
	public final double[] drops = new double[8];
	public final double[] steps = new double[8];

	public CombatState() {
		java.util.Arrays.fill(edges, 4.0);
		java.util.Arrays.fill(walls, 4.0);
		java.util.Arrays.fill(voids, 4.0);
		java.util.Arrays.fill(drops, 4.0);
		java.util.Arrays.fill(steps, 4.0);
	}
}
