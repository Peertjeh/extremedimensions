package com.extremedimensions.elder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How a giant moves: with weight. It gathers speed and loses it again instead of starting and stopping dead, turns
 * with momentum (the turn eases in and out), and rides the ground with an eased height measured over its whole
 * footprint, so a single block, a hole or a tree under it does not make it jump.
 */
final class ElderMotion {
	double vx, vz, vy;
	float turnRate;

	/** Its speed over the ground, in blocks a tick. */
	double speed() {
		return Math.sqrt(vx * vx + vz * vz);
	}

	/** Steers towards (tx, tz) at up to {@code speed} blocks a tick, slowing as it arrives. */
	void toward(ElderBoss b, double tx, double tz, double speed, double accel, double hover) {
		double dx = tx - b.getX(), dz = tz - b.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		double want = Math.min(speed, dist * 0.1);
		approach(dist > 1e-4 ? dx / dist * want : 0, dist > 1e-4 ? dz / dist * want : 0, accel);
		move(b, hover);
	}

	/** Moves at a velocity (blocks a tick) it eases into. */
	void drive(ElderBoss b, double wx, double wz, double accel, double hover) {
		approach(wx, wz, accel);
		move(b, hover);
	}

	/** Comes to a halt, gently. */
	void brake(ElderBoss b, double accel, double hover) {
		approach(0, 0, accel);
		move(b, hover);
	}

	private void approach(double wx, double wz, double accel) {
		double ex = wx - vx, ez = wz - vz;
		double e = Math.sqrt(ex * ex + ez * ez);
		if (e > accel) {
			ex *= accel / e;
			ez *= accel / e;
		}
		vx += ex;
		vz += ez;
	}

	private void move(ElderBoss b, double hover) {
		ServerLevel level = (ServerLevel) b.level();
		double nx = b.getX() + vx, nz = b.getZ() + vz;
		// The ground under its whole footprint, not one column
		double reach = Math.max(1.0, b.getBbWidth() * 0.45);
		double sum = 0;
		int n = 0;
		double[][] at = {{0, 0}, {reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}};
		for (double[] o : at) {
			double g = ElderFx.surface(level, nx + o[0], b.getY() - hover, nz + o[1], 8, 24);
			if (!Double.isNaN(g)) {
				sum += g;
				n++;
			}
		}
		double ny = b.getY();
		if (n > 0) {
			double err = sum / n + hover - b.getY();
			vy = vy * 0.72 + err * 0.07;
		} else {
			vy *= 0.8;
		}
		vy = Mth.clamp(vy, -0.6, 0.6);
		ny += vy;
		b.setPos(nx, ny, nz);
		b.setDeltaMovement(Vec3.ZERO);
	}

	/** Lifts or lowers it by itself (a giant rising out of the ground, a boat lifted on its ultimate). */
	void heave(ElderBoss b, double dy) {
		vy = 0;
		b.setPos(b.getX(), b.getY() + dy, b.getZ());
	}

	/** Turns body and head towards a yaw with momentum, never faster than {@code max} degrees a tick. */
	void turn(ElderBoss b, float yaw, float max) {
		float err = Mth.wrapDegrees(yaw - b.getYRot());
		float want = Mth.clamp(err * 0.2F, -max, max);
		turnRate += Mth.clamp(want - turnRate, -max * 0.3F, max * 0.3F);
		float next = b.getYRot() + turnRate;
		b.setYRot(next);
		b.setYBodyRot(next);
		b.setYHeadRot(next);
	}

	/** Stops everything at once (a blow that pins it in place). */
	void halt() {
		vx = 0;
		vz = 0;
		vy = 0;
		turnRate = 0;
	}
}
