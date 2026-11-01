package com.extremedimensions.lurker;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * A walk that turns at most so many degrees a tick towards where it is going: round a tunnel's corner in a few steps,
 * never a jerk to the other side and back, never a spin on the spot.
 */
public class SteadyMoveControl extends MoveControl {
	private final float turn;

	public SteadyMoveControl(Mob mob, float turn) {
		super(mob);
		this.turn = turn;
	}

	@Override
	protected float rotlerp(float from, float to, float max) {
		return super.rotlerp(from, to, Math.min(max, turn));
	}
}
