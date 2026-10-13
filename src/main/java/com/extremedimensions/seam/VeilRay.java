package com.extremedimensions.seam;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Veil Ray: a great manta of the Seam, wings ten blocks wide and seamed with light, soaring over the islands
 * and singing low. It means no harm; struck, it climbs away into the fog.
 *
 * <p>Its flight is its own (no pathfinding in the void). It glides from waypoint to waypoint in open sky above the
 * place {@link SeamFauna} gives it, turning no faster than a glider can, and it looks ahead along its whole wingspan:
 * when a spire, an island or a chain lies on its line it picks the clearest way around, preferring to climb over
 * rather than dive under, and banks hard only when it must.
 */
public class VeilRay extends PathfinderMob {
	/** How far ahead it looks for obstacles, in blocks. */
	private static final double LOOK = 28;
	/** Probe points across the body, as (sideways, up) offsets: the nose, both wings to the tips, above and below. */
	private static final double[][] SPAN = {{0, 0}, {3.0, 0}, {-3.0, 0}, {5.6, 0.4}, {-5.6, 0.4}, {0, 2.4}, {0, -2.2}};
	/** Turn rates (radians per tick): a lazy glide, and a hard bank when something is close. */
	private static final double TURN = 0.035, TURN_HARD = 0.11;

	private double hx, hy, hz, roam = 56;
	private boolean homed;
	private Vec3 waypoint;
	private int waypointAge;
	private Vec3 heading;
	private Vec3 desired;
	private boolean urgent;
	private double speed = 0.28;
	private int flee, stuck;
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	public VeilRay(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 28.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.4);
	}

	/** Gives it a stretch of sky to roam: a centre at cruising height and how far from it to wander. */
	public void anchor(double x, double y, double z, double r) {
		this.hx = x;
		this.hy = y;
		this.hz = z;
		this.roam = r;
		this.homed = true;
		this.waypoint = null;
	}

	public boolean hasAnchor() {
		return homed;
	}

	public double roam() {
		return roam;
	}

	/** True when the given point is far from the middle of its sky, so it should follow the visitor there. */
	public boolean farFromHome(double x, double z) {
		double dx = x - hx, dz = z - hz;
		return dx * dx + dz * dz > roam * roam * 0.5;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount > 40 && !SeamFauna.isDriven(this)) {
			SeamFauna.fade(this);
			return;
		}
		if (!homed) {
			anchor(getX(), getY(), getZ(), 48);
		}
		if (heading == null) {
			float yaw = this.getYRot() * Mth.DEG_TO_RAD;
			heading = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		}
		Vec3 pos = centre();
		// Scraped something last tick: turn away hard, and give up (dissolve) if it keeps happening
		if (this.horizontalCollision || this.verticalCollision) {
			stuck += 3;
			waypoint = null;
			desired = null;
		} else if (stuck > 0) {
			stuck--;
		}
		if (stuck > 60) {
			SeamFauna.fade(this);
			return;
		}
		if (waypoint == null || --waypointAge <= 0 || waypoint.distanceToSqr(pos) < 14 * 14) {
			waypoint = pickWaypoint(level, pos);
			waypointAge = 500;
		}
		Vec3 to = waypoint.subtract(pos);
		// Soaring: long, slow rises and falls along the way; back into its band of sky if it strays out of it
		double lift = Math.sin(this.tickCount * 0.021 + this.getId()) * 0.16;
		if (pos.y < hy - 14) {
			lift += 0.45;
		} else if (pos.y > hy + 26) {
			lift -= 0.35;
		}
		Vec3 goal = new Vec3(to.x, 0, to.z).normalize().add(0, Mth.clamp(to.y / Math.max(8, to.horizontalDistance()), -0.4, 0.5) + lift, 0).normalize();
		if (desired == null || this.tickCount % 2 == 0) {
			steer(level, pos, goal);
		}
		turn(desired, urgent ? TURN_HARD : TURN);
		// cruising speed swells with the wingbeat; it slows into a hard turn
		double target = flee > 0 ? 0.62 : urgent ? 0.2 : 0.27 + 0.04 * Math.sin(this.tickCount * 0.09);
		speed += (target - speed) * (urgent ? 0.12 : 0.05);
		Vec3 v = heading.scale(speed);
		this.setDeltaMovement(v);
		float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
		float turned = Mth.approachDegrees(this.getYRot(), yaw, urgent ? 7.0F : 4.0F);
		this.setYRot(turned);
		this.yBodyRot = turned;
		this.yHeadRot = turned;
		this.setXRot((float) (-Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) * 0.6F);
		if (flee > 0) {
			flee--;
		}
	}

	private Vec3 centre() {
		return this.position().add(0, this.getBbHeight() / 2, 0);
	}

	/**
	 * Chooses where to fly next: straight at the goal when the way is open, otherwise the clearest of a fan of
	 * headings around it, weighed by how far it would have to swerve (climbing costs less than diving, and holding
	 * the current line costs least, so it commits to one side of an obstacle instead of dithering).
	 */
	private void steer(ServerLevel level, Vec3 pos, Vec3 goal) {
		double free = clearance(level, pos, goal, LOOK);
		if (free >= LOOK) {
			desired = goal;
			urgent = false;
			return;
		}
		double yaw0 = Math.atan2(goal.z, goal.x), pitch0 = Math.asin(Mth.clamp(goal.y, -1, 1));
		List<double[]> fan = new ArrayList<>();
		for (double dy : new double[] {0, 20, -20, 40, -40, 65, -65, 90, -90, 125, -125, 170}) {
			for (double dp : new double[] {0, 0.32, 0.65, -0.3}) {
				double yaw = yaw0 + Math.toRadians(dy), pitch = Mth.clamp(pitch0 + dp, -0.6, 0.9);
				Vec3 d = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
				double cost = Math.abs(dy) / 180.0 * 1.2 + (dp >= 0 ? dp * 0.5 : -dp * 1.1)
						+ Math.acos(Mth.clamp(d.dot(heading), -1, 1)) / Math.PI * 0.8;
				fan.add(new double[] {d.x, d.y, d.z, cost});
			}
		}
		fan.sort((a, b) -> Double.compare(a[3], b[3]));
		Vec3 best = null;
		double bestScore = -1e9, bestFree = 0;
		for (double[] c : fan) {
			if (3.0 - c[3] <= bestScore) {
				break; // nothing cheaper is left, and nothing can be clearer than clear
			}
			Vec3 d = new Vec3(c[0], c[1], c[2]);
			double f = clearance(level, pos, d, LOOK);
			double score = 3.0 * f / LOOK - c[3];
			if (score > bestScore) {
				bestScore = score;
				best = d;
				bestFree = f;
			}
		}
		if (best == null || bestFree < 2.5) {
			// Boxed in: back off upwards
			best = heading.scale(-0.4).add(0, 1, 0).normalize();
			bestFree = 0;
		}
		desired = best;
		urgent = bestFree < 12;
	}

	/** How far it can fly along a heading before any part of it (nose, wings to the tips, back, belly) meets a block. */
	private double clearance(ServerLevel level, Vec3 pos, Vec3 dir, double max) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		for (double t = 1.5; t <= max; t += 1.6) {
			double cx = pos.x + dir.x * t, cy = pos.y + dir.y * t, cz = pos.z + dir.z * t;
			for (double[] o : SPAN) {
				if (solid(level, cx + side.x * o[0] + up.x * o[1], cy + side.y * o[0] + up.y * o[1], cz + side.z * o[0] + up.z * o[1])) {
					return Math.max(0, t - 1.5);
				}
			}
		}
		return max;
	}

	/** Anything but air is in the way (chains, lanterns and vines too); so is the edge of the loaded world. */
	private boolean solid(ServerLevel level, double x, double y, double z) {
		probe.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		if (y < 0 || y >= 256) {
			return false; // above and below the Seam's islands there is only sky
		}
		return !level.isLoaded(probe) || !level.getBlockState(probe).isAir();
	}

	/** True when a box of open air (a ray's wingspan and more) surrounds the point. */
	public static boolean open(ServerLevel level, Vec3 c, int rad) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -rad; dx <= rad; dx += rad) {
			for (int dz = -rad; dz <= rad; dz += rad) {
				for (int dy = -3; dy <= 3; dy += 3) {
					m.set(Mth.floor(c.x) + dx, Mth.floor(c.y) + dy, Mth.floor(c.z) + dz);
					if (!level.isLoaded(m) || !level.getBlockState(m).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** A point in open sky somewhere over its stretch of the Seam, far enough away for a long glide. */
	private Vec3 pickWaypoint(ServerLevel level, Vec3 pos) {
		RandomSource r = this.getRandom();
		for (int i = 0; i < 16; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = roam * (0.35 + 0.65 * r.nextDouble());
			double y = Math.min(232, hy - 6 + r.nextDouble() * 16 + (i / 4) * 10);
			Vec3 w = new Vec3(hx + Math.cos(a) * d, y, hz + Math.sin(a) * d);
			if (w.distanceToSqr(pos) > 26 * 26 && open(level, w, 6)) {
				return w;
			}
		}
		return pos.add(heading.x * 24, 16, heading.z * 24);
	}

	/** Turns the heading towards a direction, no faster than the given rate. */
	private void turn(Vec3 to, double rate) {
		double ang = Math.acos(Mth.clamp(heading.dot(to), -1, 1));
		if (ang < 1e-4) {
			heading = to;
		} else {
			double t = Math.min(1, rate / ang);
			Vec3 h = heading.scale(1 - t).add(to.scale(t));
			heading = h.lengthSqr() < 1e-6 ? heading.add(0, 0.3, 0).normalize() : h.normalize();
		}
		// never a nose-dive or a stall
		if (heading.y < -0.5 || heading.y > 0.62) {
			double y = Mth.clamp(heading.y, -0.5, 0.62);
			double hor = Math.sqrt(1 - y * y);
			double h = heading.horizontalDistance();
			heading = h < 1e-4 ? new Vec3(hor, y, 0) : new Vec3(heading.x / h * hor, y, heading.z / h * hor);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.flee = 80;
			Entity from = source.getEntity();
			Vec3 away = from == null ? heading : centre().subtract(from.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1e-4 ? heading : away.normalize();
			this.waypoint = centre().add(away.x * 34, 22, away.z * 34);
			this.waypointAge = 140;
			this.desired = null;
		}
		return hurt;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.VEIL_RAY_SONG;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 320;
	}

	@Override
	protected float getSoundVolume() {
		return 2.5F;
	}
}
