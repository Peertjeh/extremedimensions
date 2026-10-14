package com.extremedimensions.elder;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModSounds;
import com.extremedimensions.server.WorldFx;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * An Elder: a colossal boss moved and directed entirely by its service. It has no goals and no physics of its own
 * (nothing that size could path through a forest); it glides, sails or floats where it is put, with weight (see
 * {@link ElderMotion}), and its body and head turn only as its service turns them. Its body carries two numbers for
 * the client: the action under way, which the model animates, and the tempo the action runs at. While its ultimate
 * builds it shrugs off most harm; it dies slowly, breaking apart in light.
 */
public abstract class ElderBoss extends Monster {
	public static final int IDLE = 0;
	/** The ultimate (the same for every Elder). */
	public static final int ULTIMATE = 20;
	/** Ticks its death takes. */
	public static final int DEATH_TICKS = 70;

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ElderBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> TEMPO = SynchedEntityData.defineId(ElderBoss.class, EntityDataSerializers.FLOAT);

	/** How it moves (server only). */
	final ElderMotion motion = new ElderMotion();

	protected ElderBoss(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.xpReward = 250;
	}

	public static AttributeSupplier.Builder elderAttributes(double health, double armor) {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, health)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ARMOR, armor);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, IDLE);
		builder.define(TEMPO, 1.0F);
	}

	public int getAction() {
		return this.entityData.get(ACTION);
	}

	public void setAction(int action) {
		this.entityData.set(ACTION, action);
	}

	/** How fast its actions run (1 = their own pace); the client plays them at the same pace. */
	public float getTempo() {
		return this.entityData.get(TEMPO);
	}

	public void setTempo(float tempo) {
		if (Math.abs(getTempo() - tempo) > 1e-3) {
			this.entityData.set(TEMPO, tempo);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (getAction() == ULTIMATE) {
			// Its ultimate builds behind a ward: most of a blow is shrugged off
			amount *= (float) Math.max(0, 1 - Math.min(1, Config.ELDER_ULTIMATE_GUARD));
			WorldFx.particleIn(level, "minecraft:enchanted_hit", getX(), getY() + getBbHeight() * 0.5, getZ(), getBbWidth() * 0.5, getBbHeight() * 0.3,
					getBbWidth() * 0.5, 0.2, 12);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void tickHeadTurn(float yBodyRot) {
		// Body and head turn only as its service turns them
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.level() instanceof ServerLevel level) {
			java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
			double w = getBbWidth() * 0.7, h = getBbHeight();
			if (this.deathTime % 5 == 0) {
				// It breaks apart: bursts of light all over its body, louder towards the end
				double x = getX() + rnd.nextDouble(-w, w), y = getY() + rnd.nextDouble(0.1, 0.95) * h, z = getZ() + rnd.nextDouble(-w, w);
				WorldFx.particleIn(level, "minecraft:explosion", x, y, z, 0.5, 0.5, 0.5, 0, 1);
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", x, y, z, 1.2, 1.2, 1.2, 0.08, 30);
				WorldFx.particleIn(level, "minecraft:end_rod", x, y, z, 1, 1, 1, 0.12, 14);
				WorldFx.soundIn(ElderFx.dim(this), "minecraft:entity.generic.explode", "hostile", x, y, z, 2.5f, 0.5f + this.deathTime / 140f);
				ElderFx.shake(this, getX(), getZ(), 48, 0.25f + this.deathTime / 200f, 8);
			}
			if (this.deathTime == DEATH_TICKS - 2) {
				WorldFx.particleIn(level, "minecraft:explosion_emitter", getX(), getY() + h * 0.5, getZ(), 1, 1, 1, 0, 3);
				WorldFx.particleIn(level, "extremedimensions:soul_mote", getX(), getY() + h * 0.5, getZ(), w, h * 0.4, w, 0.2, 200);
				WorldFx.soundIn(ElderFx.dim(this), "minecraft:entity.wither.death", "hostile", getX(), getY() + 6, getZ(), 4f, 0.6f);
				ElderFx.cinema(this, 96, com.extremedimensions.network.ModPackets.BossFxPayload.FLASH, 0, 0.8f, "");
				ElderFx.shake(this, getX(), getZ(), 64, 1.2f, 30);
			}
			if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
				level.broadcastEntityEvent(this, (byte) 60);
				this.remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	protected void registerGoals() {
		// Directed by its service
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// One left over after a restart (nothing drives it) sinks back where it came from
		if (this.tickCount > 40 && !driven()) {
			fadeOut();
		}
	}

	/** Whether its service is directing it. */
	protected abstract boolean driven();

	/** Leaves without a fight (and without a reward). */
	protected abstract void fadeOut();

	/** How far the model reaches past the hitbox, so it is drawn whenever any of it is in view. */
	public abstract double modelReach();

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.LURKER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.LURKER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.LURKER_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 260;
	}
}
