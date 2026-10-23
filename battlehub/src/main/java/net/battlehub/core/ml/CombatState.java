package net.battlehub.core.ml;

/**
 * What a bot knows at one moment, as the melee model needs it: the same fields as a CombatState in the Python
 * simulation (ml/pvp_sim/obs.py, and for specs v2 and v3 ml/pvp_sim2/obs.py; the defaults of the newer fields leave the
 * older specs as they were). Minecraft's angles: yaw 0 looks along +z, 90 along -x; pitch positive looks down.
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

	// ---- spec v3 ----
	/** The nearest team mate, if any (always known where it is; its health only when seen), and team mates near. */
	public boolean allyPresent, allyVisible;
	public double allyX, allyY, allyZ, allyHealth = 20, allyMaxHealth = 20, alliesNear;

	/** The next nearest enemy after the second, if any. */
	public boolean e3Present, e3Visible, e3HoldsBow;
	public double e3X, e3Y, e3Z, e3Yaw, e3SinceSwing = 100;

	/** Memory, in ticks: since it last saw its target, since an enemy last hurt it, since it last hurt its target. */
	public double oppUnseen = 100, selfSinceHit = 100, oppSinceHit = 100;

	public CombatState() {
		java.util.Arrays.fill(edges, 4.0);
		java.util.Arrays.fill(walls, 4.0);
		java.util.Arrays.fill(voids, 4.0);
		java.util.Arrays.fill(drops, 4.0);
		java.util.Arrays.fill(steps, 4.0);
	}

	/**
	 * The names of the values, in the simulation's order (ml/pvp_sim2/obs.py STATE2_KEYS); the four terrain rays
	 * ({@link #RAYS}) come last, eight values each. The fight logs store a state as these values.
	 */
	public static final java.util.List<String> KEYS = java.util.List.of("self_x", "self_y", "self_z", "self_vx", "self_vy", "self_vz",
			"self_yaw", "self_pitch", "self_on_ground", "self_sprinting", "self_charge", "self_hurt_ticks",
			"self_health", "self_max_health", "self_absorption", "self_food", "self_saturation", "self_regen",
			"self_fall", "self_scale", "self_reach", "self_melee_damage", "self_melee_bonus", "self_cooldown",
			"self_kb", "self_armor", "self_toughness", "self_resistance", "self_walk_speed", "self_jump_power",
			"self_gravity", "self_arrows", "self_food_items", "self_gapples", "self_pearls", "self_winds",
			"self_shield", "self_pearl_cd", "self_wind_cd", "self_shield_cd", "self_hand", "self_use_ticks",
			"self_blocking", "opp_x", "opp_y", "opp_z", "opp_vx", "opp_vy",
			"opp_vz", "opp_yaw", "opp_on_ground", "opp_sprinting", "opp_since_swing", "opp_hurt_ticks",
			"opp_health", "opp_max_health", "opp_absorption", "opp_visible", "opp_blocking", "opp_draw",
			"opp_eating", "opp_holds_bow", "opp_holds_food", "opp_holds_throwable", "opp_melee_damage", "opp_melee_bonus",
			"opp_cooldown", "opp_kb", "opp_armor", "opp_toughness", "opp_resistance", "opp_scale",
			"e2_present", "e2_x", "e2_y", "e2_z", "e2_yaw", "e2_visible",
			"e2_since_swing", "e2_holds_bow", "threat_present", "threat_x", "threat_y", "threat_z",
			"threat_vx", "threat_vy", "threat_vz", "damage_on", "enemies_near", "self_below",
			"opp_below", "opp_drop_behind", "opp_void_behind",
			"ally_present", "ally_x", "ally_y", "ally_z", "ally_visible", "ally_health", "ally_max_health",
			"e3_present", "e3_x", "e3_y", "e3_z", "e3_yaw", "e3_visible", "e3_since_swing", "e3_holds_bow",
			"allies_near", "opp_unseen", "self_since_hit", "opp_since_hit",
			"voids", "drops", "steps", "walls");
	public static final java.util.List<String> RAYS = java.util.List.of("voids", "drops", "steps", "walls");
	/** Where the rays start in {@link #values()}: after one value per other key. */
	public static final int RAYS_AT = 112;
	/** How many values {@link #values()} gives: one per key, eight per ray. */
	public static final int SIZE = RAYS_AT + 4 * 8;

	/** The state as numbers in {@link #KEYS} order (true 1, false 0; each ray its eight values). */
	public double[] values() {
		double[] v = new double[SIZE];
		v[0] = selfX;
		v[1] = selfY;
		v[2] = selfZ;
		v[3] = selfVx;
		v[4] = selfVy;
		v[5] = selfVz;
		v[6] = selfYaw;
		v[7] = selfPitch;
		v[8] = selfOnGround ? 1 : 0;
		v[9] = selfSprinting ? 1 : 0;
		v[10] = selfCharge;
		v[11] = selfHurtTicks;
		v[12] = selfHealth;
		v[13] = selfMaxHealth;
		v[14] = selfAbsorption;
		v[15] = selfFood;
		v[16] = selfSaturation;
		v[17] = selfRegen;
		v[18] = selfFall;
		v[19] = selfScale;
		v[20] = selfReach;
		v[21] = selfMeleeDamage;
		v[22] = selfMeleeBonus;
		v[23] = selfCooldown;
		v[24] = selfKb;
		v[25] = selfArmor;
		v[26] = selfToughness;
		v[27] = selfResistance;
		v[28] = selfWalkSpeed;
		v[29] = selfJumpPower;
		v[30] = selfGravity;
		v[31] = selfArrows;
		v[32] = selfFoodItems;
		v[33] = selfGapples;
		v[34] = selfPearls;
		v[35] = selfWinds;
		v[36] = selfShield ? 1 : 0;
		v[37] = selfPearlCd;
		v[38] = selfWindCd;
		v[39] = selfShieldCd;
		v[40] = selfHand;
		v[41] = selfUseTicks;
		v[42] = selfBlocking ? 1 : 0;
		v[43] = oppX;
		v[44] = oppY;
		v[45] = oppZ;
		v[46] = oppVx;
		v[47] = oppVy;
		v[48] = oppVz;
		v[49] = oppYaw;
		v[50] = oppOnGround ? 1 : 0;
		v[51] = oppSprinting ? 1 : 0;
		v[52] = oppSinceSwing;
		v[53] = oppHurtTicks;
		v[54] = oppHealth;
		v[55] = oppMaxHealth;
		v[56] = oppAbsorption;
		v[57] = oppVisible ? 1 : 0;
		v[58] = oppBlocking ? 1 : 0;
		v[59] = oppDraw;
		v[60] = oppEating ? 1 : 0;
		v[61] = oppHoldsBow ? 1 : 0;
		v[62] = oppHoldsFood ? 1 : 0;
		v[63] = oppHoldsThrowable ? 1 : 0;
		v[64] = oppMeleeDamage;
		v[65] = oppMeleeBonus;
		v[66] = oppCooldown;
		v[67] = oppKb;
		v[68] = oppArmor;
		v[69] = oppToughness;
		v[70] = oppResistance;
		v[71] = oppScale;
		v[72] = e2Present ? 1 : 0;
		v[73] = e2X;
		v[74] = e2Y;
		v[75] = e2Z;
		v[76] = e2Yaw;
		v[77] = e2Visible ? 1 : 0;
		v[78] = e2SinceSwing;
		v[79] = e2HoldsBow ? 1 : 0;
		v[80] = threatPresent ? 1 : 0;
		v[81] = threatX;
		v[82] = threatY;
		v[83] = threatZ;
		v[84] = threatVx;
		v[85] = threatVy;
		v[86] = threatVz;
		v[87] = damageOn ? 1 : 0;
		v[88] = enemiesNear;
		v[89] = selfBelow;
		v[90] = oppBelow;
		v[91] = oppDropBehind;
		v[92] = oppVoidBehind;
		double[] v3 = {allyPresent ? 1 : 0, allyX, allyY, allyZ, allyVisible ? 1 : 0, allyHealth, allyMaxHealth, e3Present ? 1 : 0, e3X, e3Y, e3Z,
				e3Yaw, e3Visible ? 1 : 0, e3SinceSwing, e3HoldsBow ? 1 : 0, alliesNear, oppUnseen, selfSinceHit, oppSinceHit};
		System.arraycopy(v3, 0, v, 93, v3.length);
		System.arraycopy(voids, 0, v, RAYS_AT, 8);
		System.arraycopy(drops, 0, v, RAYS_AT + 8, 8);
		System.arraycopy(steps, 0, v, RAYS_AT + 16, 8);
		System.arraycopy(walls, 0, v, RAYS_AT + 24, 8);
		return v;
	}

	/** A state from {@link #values()}; the {@code SIZE} values of 1.37 to 1.39 (spec v2's, 125) are read as well. */
	public static CombatState of(double[] v) {
		CombatState s = new CombatState();
		s.selfX = v[0];
		s.selfY = v[1];
		s.selfZ = v[2];
		s.selfVx = v[3];
		s.selfVy = v[4];
		s.selfVz = v[5];
		s.selfYaw = v[6];
		s.selfPitch = v[7];
		s.selfOnGround = v[8] > 0.5;
		s.selfSprinting = v[9] > 0.5;
		s.selfCharge = v[10];
		s.selfHurtTicks = v[11];
		s.selfHealth = v[12];
		s.selfMaxHealth = v[13];
		s.selfAbsorption = v[14];
		s.selfFood = v[15];
		s.selfSaturation = v[16];
		s.selfRegen = v[17];
		s.selfFall = v[18];
		s.selfScale = v[19];
		s.selfReach = v[20];
		s.selfMeleeDamage = v[21];
		s.selfMeleeBonus = v[22];
		s.selfCooldown = v[23];
		s.selfKb = v[24];
		s.selfArmor = v[25];
		s.selfToughness = v[26];
		s.selfResistance = v[27];
		s.selfWalkSpeed = v[28];
		s.selfJumpPower = v[29];
		s.selfGravity = v[30];
		s.selfArrows = v[31];
		s.selfFoodItems = v[32];
		s.selfGapples = v[33];
		s.selfPearls = v[34];
		s.selfWinds = v[35];
		s.selfShield = v[36] > 0.5;
		s.selfPearlCd = v[37];
		s.selfWindCd = v[38];
		s.selfShieldCd = v[39];
		s.selfHand = (int) Math.round(v[40]);
		s.selfUseTicks = v[41];
		s.selfBlocking = v[42] > 0.5;
		s.oppX = v[43];
		s.oppY = v[44];
		s.oppZ = v[45];
		s.oppVx = v[46];
		s.oppVy = v[47];
		s.oppVz = v[48];
		s.oppYaw = v[49];
		s.oppOnGround = v[50] > 0.5;
		s.oppSprinting = v[51] > 0.5;
		s.oppSinceSwing = v[52];
		s.oppHurtTicks = v[53];
		s.oppHealth = v[54];
		s.oppMaxHealth = v[55];
		s.oppAbsorption = v[56];
		s.oppVisible = v[57] > 0.5;
		s.oppBlocking = v[58] > 0.5;
		s.oppDraw = v[59];
		s.oppEating = v[60] > 0.5;
		s.oppHoldsBow = v[61] > 0.5;
		s.oppHoldsFood = v[62] > 0.5;
		s.oppHoldsThrowable = v[63] > 0.5;
		s.oppMeleeDamage = v[64];
		s.oppMeleeBonus = v[65];
		s.oppCooldown = v[66];
		s.oppKb = v[67];
		s.oppArmor = v[68];
		s.oppToughness = v[69];
		s.oppResistance = v[70];
		s.oppScale = v[71];
		s.e2Present = v[72] > 0.5;
		s.e2X = v[73];
		s.e2Y = v[74];
		s.e2Z = v[75];
		s.e2Yaw = v[76];
		s.e2Visible = v[77] > 0.5;
		s.e2SinceSwing = v[78];
		s.e2HoldsBow = v[79] > 0.5;
		s.threatPresent = v[80] > 0.5;
		s.threatX = v[81];
		s.threatY = v[82];
		s.threatZ = v[83];
		s.threatVx = v[84];
		s.threatVy = v[85];
		s.threatVz = v[86];
		s.damageOn = v[87] > 0.5;
		s.enemiesNear = v[88];
		s.selfBelow = v[89];
		s.oppBelow = v[90];
		s.oppDropBehind = v[91];
		s.oppVoidBehind = v[92];
		int rays = 93;
		if (v.length >= SIZE) {
			s.allyPresent = v[93] > 0.5;
			s.allyX = v[94];
			s.allyY = v[95];
			s.allyZ = v[96];
			s.allyVisible = v[97] > 0.5;
			s.allyHealth = v[98];
			s.allyMaxHealth = v[99];
			s.e3Present = v[100] > 0.5;
			s.e3X = v[101];
			s.e3Y = v[102];
			s.e3Z = v[103];
			s.e3Yaw = v[104];
			s.e3Visible = v[105] > 0.5;
			s.e3SinceSwing = v[106];
			s.e3HoldsBow = v[107] > 0.5;
			s.alliesNear = v[108];
			s.oppUnseen = v[109];
			s.selfSinceHit = v[110];
			s.oppSinceHit = v[111];
			rays = RAYS_AT;
		}
		System.arraycopy(v, rays, s.voids, 0, 8);
		System.arraycopy(v, rays + 8, s.drops, 0, 8);
		System.arraycopy(v, rays + 16, s.steps, 0, 8);
		System.arraycopy(v, rays + 24, s.walls, 0, 8);
		return s;
	}
}
