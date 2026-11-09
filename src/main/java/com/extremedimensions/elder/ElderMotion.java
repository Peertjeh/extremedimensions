package com.extremedimensions.elder;

import com.extremedimensions.server.BossGround;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How a giant moves: with weight. It gathers speed and loses it again instead of starting and stopping dead, and turns
 * with momentum (the turn eases in and out).
 *
 * <p>It stands on the ground under its whole footprint (see {@link BossGround}): on the highest of it, so that no part
 * of it cuts into a step, a slope or the rim of a pit. Where the ground rises under it, it is up on it at once (a step it
 * walks onto, a floor raised round it, a cliff thrown up under it), never easing up through it; where the ground falls
 * away, it comes down after it smoothly; what hangs over it with open air between (an arch, an island, a curtain of
 * roots) it never climbs onto. A wall, a cliff or a tower too high to step onto stands in its way: it goes round it if it
 * can, else it stops against it, never walking into it. One that hovers keeps its height over all of that. Trees it
 * wades through. A boat's long hull stands on the ground under all of it as well, and swings round into no wall.
 */
final class ElderMotion {
	/** How far to each side of a boat's keel the ground under its hull is looked for. */
	private static final double KEEL = 1.2;

	double vx, vz, vy;
	float turnRate;
	/** Which way it goes round what stands in its way (1 or -1): the way it went last, until that way is shut too. */
	private int side = 1;

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

	/** The highest it steps up in one stride: a quarter of its height, at least two blocks. Higher stands in its way. */
	static double step(ElderBoss b) {
		return Math.max(2.0, b.getBbHeight() * 0.25);
	}

	/**
	 * The ground under it standing at (x, z), facing {@code yaw}, its body's bottom at {@code bottom}: under its body, and
	 * under its hull where that reaches further (the Ferryman's boat is far longer than he is wide). NaN with none near.
	 */
	static double ground(ElderBoss b, double x, double z, float yaw, double bottom) {
		double half = b.getBbWidth() / 2;
		double g = BossGround.box(b.level(), x, z, half, bottom);
		double reach = b.hullReach();
		if (reach > half) {
			double h = BossGround.hull(b.level(), x, z, yaw, half, reach, KEEL, bottom);
			g = Double.isNaN(g) ? h : Double.isNaN(h) ? g : Math.max(g, h);
		}
		return g;
	}

	/**
	 * Where it comes to stand at (x, z) out of the air (a blink), looking for the ground from {@code from}: on top of the
	 * ground under the whole of it, climbing out of any it would be in; NaN with none near.
	 */
	static double landing(ElderBoss b, double x, double z, double from) {
		return ground(b, x, z, b.getYRot(), from);
	}

	private void move(ElderBoss b, double hover) {
		double x = b.getX(), y = b.getY(), z = b.getZ();
		float yaw = b.getYRot();
		double nx = x + vx, nz = z + vz;
		double g = ground(b, nx, nz, yaw, y);
		if ((vx != 0 || vz != 0) && !Double.isNaN(g) && g > y) {
			// Higher than it stands: a step it takes in its stride (one that floats rises over as much again as it floats
			// over the ground), or something it cannot step onto
			double here = ground(b, x, z, yaw, y);
			double most = Math.max(y, (Double.isNaN(here) ? y - hover : Math.max(here, y - hover)) + step(b) + hover);
			if (g > most) {
				double[] way = around(b, x, z, yaw, y, most);
				if (way == null) {
					// Nowhere to go but into it: it stands against it
					nx = x;
					nz = z;
					vx = 0;
					vz = 0;
					g = here;
				} else {
					nx = way[0];
					nz = way[1];
					g = way[2];
				}
			}
		}
		double ny;
		if (Double.isNaN(g)) {
			// Nothing under it within reach (over the void): it holds its height
			vy *= 0.8;
			ny = y + Mth.clamp(vy, -0.6, 0.6);
		} else {
			// Eased to its height over the ground, without overshooting it...
			vy = Mth.clamp(vy * 0.6 + (g + hover - y) * 0.05, -0.6, 0.6);
			ny = y + vy;
			if (ny < g) {
				// ... and never lower than the ground: up at once onto what rose under it, or out of what it is in
				ny = g;
				vy = Math.max(0, vy);
			}
		}
		b.setPos(nx, ny, nz);
		b.setDeltaMovement(Vec3.ZERO);
	}

	/**
	 * A way round what stands in its way: its stride turned aside, by half a right angle and then a whole one (slower the
	 * further it turns), first to the side it went round last; {x, z, ground there} for the first way no higher than
	 * {@code most}, or null if every way is shut.
	 */
	private double[] around(ElderBoss b, double x, double z, float yaw, double bottom, double most) {
		for (int i = 0; i < 4; i++) {
			int s = i < 2 ? side : -side;
			double a = Math.toRadians(i % 2 == 0 ? 50 : 90) * s, k = i % 2 == 0 ? 0.65 : 0.4;
			double cos = Math.cos(a), sin = Math.sin(a);
			double rx = (vx * cos - vz * sin) * k, rz = (vx * sin + vz * cos) * k;
			double g = ground(b, x + rx, z + rz, yaw, bottom);
			if (Double.isNaN(g) || g <= most) {
				side = s;
				return new double[] {x + rx, z + rz, g};
			}
		}
		return null;
	}

	/**
	 * Lifts or lowers it by itself (a giant rising out of the ground, a boat lifted on its ultimate). Lowered, it comes
	 * down onto the ground and never into it (the ground may have risen under it meanwhile); lifted, it goes where it is
	 * put, out of the ground as well (the Matriarch rises out of it as she forms).
	 */
	void heave(ElderBoss b, double dy) {
		vy = 0;
		double y = b.getY() + dy;
		if (dy < 0) {
			double g = ground(b, b.getX(), b.getZ(), b.getYRot(), y);
			if (!Double.isNaN(g)) {
				y = Math.max(y, g);
			}
		}
		b.setPos(b.getX(), y, b.getZ());
	}

	/** Turns body and head towards a yaw with momentum, never faster than {@code max} degrees a tick. */
	void turn(ElderBoss b, float yaw, float max) {
		float err = Mth.wrapDegrees(yaw - b.getYRot());
		float want = Mth.clamp(err * 0.2F, -max, max);
		turnRate += Mth.clamp(want - turnRate, -max * 0.3F, max * 0.3F);
		float next = b.getYRot() + turnRate;
		if (Math.abs(turnRate) > 1e-3 && b.hullReach() > b.getBbWidth() / 2) {
			// Its long hull swings round with it: never into a wall, and up at once onto a step it swings over
			double y = b.getY();
			double then = ground(b, b.getX(), b.getZ(), next, y);
			if (!Double.isNaN(then) && then > y) {
				double now = ground(b, b.getX(), b.getZ(), b.getYRot(), y);
				if (then > Math.max(y, (Double.isNaN(now) ? y : now) + step(b))) {
					turnRate = 0;
					return;
				}
				b.setPos(b.getX(), then, b.getZ());
				vy = Math.max(0, vy);
			}
		}
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
