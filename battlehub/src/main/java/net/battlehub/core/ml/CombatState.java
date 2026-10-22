package net.battlehub.core.ml;

/**
 * What a bot knows at one moment, as the melee model needs it: the same fields as a CombatState in the Python
 * simulation (ml/pvp_sim/obs.py). Minecraft's angles: yaw 0 looks along +z, 90 along -x; pitch positive looks down.
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

	public CombatState() {
		java.util.Arrays.fill(edges, 4.0);
		java.util.Arrays.fill(walls, 4.0);
	}
}
