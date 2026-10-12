package com.extremedimensions.seam;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Veil Ray: a great manta of the Seam, wings six blocks wide and seamed with light, gliding in slow circles between
 * the islands and singing low. It means no harm; struck, it climbs away into the fog.
 *
 * <p>Its flight is its own (no pathfinding in the void): it circles an anchor that {@link SeamFauna} gives it, rising
 * and falling on a long swell, and turns its body into the curve.
 */
public class VeilRay extends PathfinderMob {
	private double ax, ay, az;
	private double radius = 24;
	private double orbit;
	private double speed = 0.006;
	private int flee;

	public VeilRay(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.orbit = this.random.nextDouble() * Math.PI * 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 28.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.4);
	}

	/** Gives it a circle to fly: centre and radius; the direction and pace are its own. */
	public void anchor(double x, double y, double z, double r) {
		this.ax = x;
		this.ay = y;
		this.az = z;
		this.radius = r;
		this.speed = (0.004 + this.random.nextDouble() * 0.004) * (this.random.nextBoolean() ? 1 : -1);
	}

	public boolean hasAnchor() {
		return this.ax != 0 || this.az != 0;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount > 40 && !SeamFauna.isDriven(this)) {
			SeamFauna.fade(this);
			return;
		}
		if (!hasAnchor()) {
			anchor(getX() - Math.cos(orbit) * radius, getY(), getZ() - Math.sin(orbit) * radius, radius);
		}
		orbit += speed * (flee > 0 ? 2.5 : 1);
		double swell = Math.sin(orbit * 2.3 + this.getId()) * 6 + Math.sin(orbit * 0.7) * 3;
		double rise = flee > 0 ? (60 - flee) * 0.6 : 0;
		Vec3 target = new Vec3(ax + Math.cos(orbit) * radius, ay + swell + rise, az + Math.sin(orbit) * radius * 0.85);
		Vec3 to = target.subtract(this.position());
		double len = to.length();
		Vec3 want = len < 1e-4 ? Vec3.ZERO : to.scale(Math.min(flee > 0 ? 0.7 : 0.32, len * 0.06) / len);
		Vec3 v = this.getDeltaMovement().lerp(want, 0.06);
		this.setDeltaMovement(v);
		if (v.horizontalDistanceSqr() > 1e-5) {
			float yaw = (float) (Mth.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0F;
			float turned = Mth.approachDegrees(this.getYRot(), yaw, 3.0F);
			this.setYRot(turned);
			this.yBodyRot = turned;
			this.yHeadRot = turned;
			this.setXRot((float) (-Mth.atan2(v.y, Math.sqrt(v.horizontalDistanceSqr())) * (180.0 / Math.PI)) * 0.6F);
		}
		if (flee > 0) {
			flee--;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.flee = 60;
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
