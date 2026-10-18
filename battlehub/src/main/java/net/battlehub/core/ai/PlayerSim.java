package net.battlehub.core.ai;

/**
 * A player's movement, tick by tick, with the game's own formulas (no Minecraft classes): on the ground the input
 * accelerates by {@code speed * 0.98 * 0.216 / friction^3} and the speed keeps {@code friction * 0.91} each tick;
 * in the air the input adds 0.02 (0.026 sprinting) and the speed keeps 0.91; a jump sets 0.42 upwards and, when
 * sprinting, adds 0.2 in the facing direction; gravity takes 0.08 and the vertical speed keeps 0.98. The input
 * always pushes along one fixed horizontal direction (a straight run-up and jump).
 */
public final class PlayerSim {
	public static final double WALK = 0.1;
	public static final double SPRINT = 0.13;
	public static final double JUMP = 0.42;
	public static final double SPRINT_JUMP_BOOST = 0.2;
	public static final double GRAVITY = 0.08;
	public static final double DRAG_Y = 0.98;
	public static final double AIR_DRAG = 0.91;
	public static final double NORMAL_FRICTION = 0.6;

	public double x, y, z;
	public double vx, vy, vz;
	public boolean onGround = true;
	/** Friction of the block under the player (0.6 normal, 0.98 ice). */
	public double friction = NORMAL_FRICTION;

	public PlayerSim(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * One tick with forward input along (dx, dz) (a unit vector, or zero for no input), sprinting or not, jumping or
	 * not. Standing on the ground the player stays at its height; whether there still is ground under it is for the
	 * caller to check ({@link #walkOff()}, {@link #land}).
	 */
	public void tick(double dx, double dz, boolean sprint, boolean jump) {
		boolean grounded = onGround;
		if (jump && grounded) {
			vy = JUMP;
			if (sprint) {
				vx += dx * SPRINT_JUMP_BOOST;
				vz += dz * SPRINT_JUMP_BOOST;
			}
		}
		double accel = grounded ? (sprint ? SPRINT : WALK) * (0.21600002 / (friction * friction * friction)) : sprint ? 0.025999999 : 0.02;
		// the input is scaled by 0.98 before it is used
		vx += dx * accel * 0.98;
		vz += dz * accel * 0.98;
		x += vx;
		z += vz;
		boolean resting = grounded && vy == 0;
		if (!resting) y += vy;
		// the friction of the tick's start: on the jump tick the ground still slows the player once
		double keep = grounded ? friction * 0.91 : AIR_DRAG;
		vx *= keep;
		vz *= keep;
		if (!resting) {
			vy = (vy - GRAVITY) * DRAG_Y;
			onGround = false;
		}
	}

	/** The ground ended under the player: it starts to fall. */
	public void walkOff() {
		onGround = false;
	}

	/** Comes down on a surface at height {@code surface}: back on the ground there. */
	public void land(double surface, double surfaceFriction) {
		y = surface;
		vy = 0;
		onGround = true;
		friction = surfaceFriction;
	}

	public double speed() {
		return Math.hypot(vx, vz);
	}
}
