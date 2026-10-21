package com.extremedimensions.seam;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModSounds;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Veil Ray: a great manta of the Seam, wings ten blocks wide and seamed with light, soaring over the islands
 * and singing low. It means no harm; struck, it climbs away into the fog.
 *
 * <p>Its flight is its own (no pathfinding in the void). It glides from waypoint to waypoint in open sky above the
 * place {@link SeamFauna} gives it, turning no faster than a glider can, and it looks ahead along its whole wingspan:
 * when a spire, an island or a chain lies on its line it picks the clearest way around, preferring to climb over
 * rather than dive under, and banks hard only when it must.
 *
 * <p>Down in the Fray they are not the same. The Frayed fly faster, their light gone sick and green, and they hunt:
 * one climbs over a visitor, folds its wings and dives, and its sting poisons. Then it pulls away, circles, and comes
 * again. Struck, a Frayed Ray does not flee; it turns on whoever struck it.
 */
public class VeilRay extends PathfinderMob {
	private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(VeilRay.class, EntityDataSerializers.BYTE);
	private static final byte FRAYED = 1, DIVING = 2;
	/** The hunt of the Frayed: circling, climbing over its prey, diving at it, pulling away after. */
	private static final int CIRCLE = 0, CLIMB = 1, DIVE = 2, AWAY = 3;
	/** How far the Frayed look for prey, and how close a dive must pass to strike (from its middle to the prey; its wings reach further). */
	private static final double SIGHT = 36, STRIKE = 2.8;
	/** How near its prey a Frayed Ray must be to begin a hunt (it climbs, then dives, from there). */
	private static final double HUNT = 28;

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
	private int phase = CIRCLE, phaseTicks, rest = 60, strikes;
	/** How close the dive under way has come to its prey. */
	private double closest;
	private UUID prey;
	/** Hunts only its prey, never another (for the tests, whose players stand side by side). */
	private boolean only;
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	public VeilRay(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MODE, (byte) 0);
	}

	/** One of the Frayed: a ray of the Fray, that hunts. */
	public boolean isFrayed() {
		return (this.entityData.get(MODE) & FRAYED) != 0;
	}

	public void setFrayed(boolean frayed) {
		byte m = this.entityData.get(MODE);
		this.entityData.set(MODE, (byte) (frayed ? m | FRAYED : m & ~FRAYED));
	}

	/** Diving at its prey (the client folds its wings). */
	public boolean isDiving() {
		return (this.entityData.get(MODE) & DIVING) != 0;
	}

	/** Set by its hunt (and by the screenshots, to see the dive). */
	public void setDiving(boolean diving) {
		byte m = this.entityData.get(MODE);
		if (diving != ((m & DIVING) != 0)) {
			this.entityData.set(MODE, (byte) (diving ? m | DIVING : m & ~DIVING));
		}
	}

	/** How many times it has struck a visitor (for the tests). */
	public int strikes() {
		return strikes;
	}

	/** Ready to hunt at once (for the tests and a fresh grudge). */
	public void rouse() {
		rest = 0;
	}

	/** What it makes of a player as prey, and where its hunt stands (for the tests, when one fails). */
	public String huntState(ServerPlayer p) {
		ServerLevel level = (ServerLevel) this.level();
		return String.format(java.util.Locale.ROOT,
				"phase %s rest %s strikes %s alive %s at %.1f,%.1f,%.1f; prey at %.1f,%.1f,%.1f same level %s listed %s fair %s sight %s difficulty %s fallen %s",
				phase, rest, strikes, isAlive(), getX(), getY(), getZ(), p.getX(), p.getY(), p.getZ(), p.level() == level,
				level.getPlayerByUUID(p.getUUID()) == p, fair(level, p), this.hasLineOfSight(p), level.getDifficulty(),
				com.extremedimensions.coop.FallenService.fallen(p));
	}

	/** Hunts this one and no other (for the tests). */
	public void huntOnly(ServerPlayer p) {
		prey = p.getUUID();
		only = true;
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
		if (isFrayed() && hunt(level, pos)) {
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
		if (isFrayed() && pos.y > Config.SEAM_FRAY_TOP - 3) {
			// the Frayed never leave the dark under the islands
			lift -= 0.5;
		}
		Vec3 goal = new Vec3(to.x, 0, to.z).normalize().add(0, Mth.clamp(to.y / Math.max(8, to.horizontalDistance()), -0.4, 0.5) + lift, 0).normalize();
		if (desired == null || this.tickCount % 2 == 0) {
			steer(level, pos, goal);
		}
		turn(desired, urgent ? TURN_HARD : TURN, -0.5, 0.62);
		// cruising speed swells with the wingbeat; it slows into a hard turn (the Frayed fly faster in everything)
		double target = (flee > 0 ? 0.62 : urgent ? 0.2 : 0.27 + 0.04 * Math.sin(this.tickCount * 0.09)) * pace();
		speed += (target - speed) * (urgent ? 0.12 : 0.05);
		fly(urgent ? 7.0F : 4.0F, 0.6F);
		if (flee > 0) {
			flee--;
		}
	}

	/** How much faster than a calm ray it flies. */
	private double pace() {
		return isFrayed() ? Mth.clamp(Config.SEAM_FRAY_RAY_SPEED, 0.5, 4.0) : 1.0;
	}

	/** Moves along its heading at its speed, and turns its body (and its nose, by {@code pitch}) to match. */
	private void fly(float yawRate, float pitch) {
		Vec3 v = heading.scale(speed);
		this.setDeltaMovement(v);
		float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
		float turned = Mth.approachDegrees(this.getYRot(), yaw, yawRate);
		this.setYRot(turned);
		this.yBodyRot = turned;
		this.yHeadRot = turned;
		this.setXRot((float) (-Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) * pitch);
	}

	// ------------------------------------------------------------------ the hunt of the Frayed

	/**
	 * A Frayed Ray's hunt, each tick: false while it only circles (and looks for prey), true while it climbs, dives or
	 * pulls away, flying itself.
	 */
	private boolean hunt(ServerLevel level, Vec3 pos) {
		if (rest > 0) {
			rest--;
		}
		phaseTicks++;
		Player found = prey == null ? null : level.getPlayerByUUID(prey);
		ServerPlayer target = found instanceof ServerPlayer sp && fair(level, sp) ? sp : null;
		if (phase == CIRCLE) {
			setDiving(false);
			if (rest <= 0 && (this.tickCount + this.getId()) % 10 == 0) {
				// It comes back over its prey before it hunts again: a climb begun from far off would end in a dive at nothing
				target = target != null && this.hasLineOfSight(target) && target.distanceToSqr(this) < HUNT * HUNT ? target : only ? null : pick(level, pos);
				if (target != null) {
					prey = target.getUUID();
					begin(CLIMB);
					// a cry before it climbs: the warning
					WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.phantom.swoop", "hostile", getX(), getY(), getZ(), 2.2F, 0.55F);
				}
			}
			return false;
		}
		if (target == null && phase != AWAY) {
			begin(AWAY);
		}
		Vec3 goal;
		switch (phase) {
			case CLIMB -> {
				// Up and over its prey, a little to the side it came from, to fall on it
				Vec3 at = target.position();
				Vec3 from = new Vec3(pos.x - at.x, 0, pos.z - at.z);
				from = from.lengthSqr() < 1e-4 ? heading.multiply(-1, 0, -1) : from.normalize();
				Vec3 over = at.add(from.scale(9)).add(0, 9, 0);
				goal = over.subtract(pos);
				if (phaseTicks > 130) {
					// could not get over it: it gives up for now
					begin(AWAY);
					return true;
				}
				if (goal.length() < 4 || phaseTicks > 50 && pos.distanceTo(at) < 20) {
					begin(DIVE);
					WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.phantom.swoop", "hostile", getX(), getY(), getZ(), 2.6F, 0.75F);
					return true;
				}
				goal = goal.normalize();
				if (clearance(level, pos, goal, 8) < 3) {
					begin(AWAY);
					return true;
				}
				turn(goal, TURN_HARD * 1.3, -0.5, 0.8);
				speed += (0.36 * pace() - speed) * 0.12;
				fly(9.0F, 0.7F);
			}
			case DIVE -> {
				setDiving(true);
				// Straight at it, leading it a little
				Vec3 at = target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(5));
				Vec3 to = at.subtract(pos);
				double dist = to.length();
				if (reach(target.getBoundingBox(), pos) < STRIKE) {
					strike(level, target);
					begin(AWAY);
					return true;
				}
				goal = to.scale(1 / Math.max(1e-4, dist));
				closest = Math.min(closest, dist);
				// Missed (it came close and is drawing off again, or was too long at it), or about to fly into the knot
				// its prey stands on
				boolean past = closest < 8 && dist > closest + 2;
				Vec3 nose = pos.add(heading.scale(2.6));
				if (past || phaseTicks > 70 || dist > STRIKE + 2 && solid(level, nose.x, nose.y, nose.z)) {
					begin(AWAY);
					return true;
				}
				// it turns harder the closer it comes
				turn(goal, dist < 10 ? 0.28 : 0.18, -0.95, 0.8);
				speed += (Math.min(1.4, 0.42 * pace() + 0.2) - speed) * 0.2;
				fly(14.0F, 1.0F);
			}
			default -> {
				// Pulling away: up and on, then it circles until it is ready again
				setDiving(false);
				goal = new Vec3(heading.x, 0, heading.z);
				goal = (goal.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : goal.normalize()).add(0, 0.75, 0).normalize();
				turn(goal, TURN_HARD, -0.5, 0.75);
				speed += (0.32 * pace() - speed) * 0.1;
				fly(8.0F, 0.6F);
				if (phaseTicks > 30) {
					begin(CIRCLE);
					rest = Math.max(1, Config.SEAM_FRAY_RAY_COOLDOWN) * 20 + this.getRandom().nextInt(60);
					waypoint = null;
					desired = null;
					if (target != null) {
						// it circles over its prey, to come again
						anchor(target.getX(), Mth.clamp(target.getY() + 9, 14, Config.SEAM_FRAY_TOP - 4), target.getZ(), Math.min(roam, 30));
					}
				}
			}
		}
		return true;
	}

	private void begin(int next) {
		phase = next;
		phaseTicks = 0;
		closest = Double.MAX_VALUE;
	}

	/** Prey it may hunt: a visitor down in the Fray, alive, in survival, not fallen, and the world not at peace. */
	private boolean fair(ServerLevel level, ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative() && p.level() == level && level.getDifficulty() != Difficulty.PEACEFUL
				&& p.getY() < Config.SEAM_FRAY_TOP + 6 && p.distanceToSqr(this) < (SIGHT + 16) * (SIGHT + 16)
				&& !com.extremedimensions.coop.FallenService.fallen(p);
	}

	/** The nearest fair prey it can see. */
	private ServerPlayer pick(ServerLevel level, Vec3 pos) {
		ServerPlayer best = null;
		double bd = HUNT * HUNT;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(pos);
			if (d < bd && fair(level, p) && this.hasLineOfSight(p)) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** How far a point is from a box (0 inside it). */
	private static double reach(AABB box, Vec3 p) {
		double dx = Math.max(0, Math.max(box.minX - p.x, p.x - box.maxX));
		double dy = Math.max(0, Math.max(box.minY - p.y, p.y - box.maxY));
		double dz = Math.max(0, Math.max(box.minZ - p.z, p.z - box.maxZ));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The sting: a blow, poison, and a shove along the line of the dive; a shield raised against it stops all three. */
	private void strike(ServerLevel level, ServerPlayer p) {
		strikes++;
		boolean landed = p.hurtServer(level, this.damageSources().mobAttack(this), (float) Math.max(0, Config.SEAM_FRAY_RAY_DAMAGE));
		if (!landed && Config.SEAM_FRAY_RAY_DAMAGE > 0) {
			// blocked (the game plays the shield's sound itself), or the player is still reeling from the last blow
			return;
		}
		if (Config.SEAM_FRAY_RAY_POISON > 0) {
			WorldFx.effect(p, MobEffects.POISON, Config.SEAM_FRAY_RAY_POISON, 0);
		}
		Vec3 along = new Vec3(heading.x, 0, heading.z);
		along = along.lengthSqr() < 1e-4 ? Vec3.ZERO : along.normalize();
		p.setDeltaMovement(p.getDeltaMovement().add(along.x * 0.55, 0.32, along.z * 0.55));
		p.needsSync = true;
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "minecraft:entity.phantom.bite", "hostile", p.getX(), p.getY() + 1, p.getZ(), 1.4F, 0.6F);
		WorldFx.particleIn(dim, "minecraft:item_slime", p.getX(), p.getY() + 1, p.getZ(), 0.4, 0.5, 0.4, 0.05, 14);
		com.extremedimensions.journal.Journal.unlock(p, com.extremedimensions.data.DataStore.get(p.getUUID()),
				com.extremedimensions.journal.JournalEntry.FRAYED_RAY);
		com.extremedimensions.journal.Bestiary.seen(p, com.extremedimensions.journal.Bestiary.Beast.FRAYED_RAY);
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
		boolean frayed = isFrayed();
		// The next waypoint lies well away, but within its own sky: a small sky asks for shorter legs
		double leg = Math.min(26, roam * 0.75);
		for (int i = 0; i < 16; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = roam * (0.35 + 0.65 * r.nextDouble());
			double y = Math.min(232, hy - 6 + r.nextDouble() * 16 + (i / 4) * 10);
			if (frayed) {
				// the Frayed keep to the dark under the islands
				y = Mth.clamp(y, 12, Config.SEAM_FRAY_TOP - 3);
			}
			Vec3 w = new Vec3(hx + Math.cos(a) * d, y, hz + Math.sin(a) * d);
			if (w.distanceToSqr(pos) > leg * leg && open(level, w, 6)) {
				return w;
			}
		}
		// Nowhere open to go: back towards the middle of its sky if it strayed out of it, on along its way if not
		double dx = hx - pos.x, dz = hz - pos.z;
		if (dx * dx + dz * dz > roam * roam) {
			return new Vec3(hx, frayed ? Mth.clamp(hy, 12, Config.SEAM_FRAY_TOP - 3) : hy, hz);
		}
		return pos.add(heading.x * 24, frayed ? 2 : 16, heading.z * 24);
	}

	/** Turns the heading towards a direction, no faster than the given rate, its slope kept between lo and hi. */
	private void turn(Vec3 to, double rate, double lo, double hi) {
		double ang = Math.acos(Mth.clamp(heading.dot(to), -1, 1));
		if (ang < 1e-4) {
			heading = to;
		} else {
			double t = Math.min(1, rate / ang);
			Vec3 h = heading.scale(1 - t).add(to.scale(t));
			heading = h.lengthSqr() < 1e-6 ? heading.add(0, 0.3, 0).normalize() : h.normalize();
		}
		// never a nose-dive or a stall (but a dive at prey)
		if (heading.y < lo || heading.y > hi) {
			double y = Mth.clamp(heading.y, lo, hi);
			double hor = Math.sqrt(1 - y * y);
			double h = heading.horizontalDistance();
			heading = h < 1e-4 ? new Vec3(hor, y, 0) : new Vec3(heading.x / h * hor, y, heading.z / h * hor);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && isFrayed()) {
			// The Frayed do not flee: they turn on whoever struck them
			if (source.getEntity() instanceof ServerPlayer p && phase == CIRCLE) {
				prey = p.getUUID();
				rest = Math.min(rest, 10);
			}
		} else if (hurt) {
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
	protected net.minecraft.network.chat.Component getTypeName() {
		return isFrayed() ? net.minecraft.network.chat.Component.translatable("entity.extremedimensions.frayed_ray") : super.getTypeName();
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

	@Override
	public float getVoicePitch() {
		// the Frayed sing lower
		return super.getVoicePitch() * (isFrayed() ? 0.7F : 1.0F);
	}
}
