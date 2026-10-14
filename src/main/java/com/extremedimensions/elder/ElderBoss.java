package com.extremedimensions.elder;

import com.extremedimensions.registry.ModSounds;

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
 * (nothing that size could path through a forest); it glides, sails or floats where it is put. Its body carries one
 * number for the client, the action under way, which the model animates.
 */
public abstract class ElderBoss extends Monster {
	public static final int IDLE = 0;

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ElderBoss.class, EntityDataSerializers.INT);

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
	}

	public int getAction() {
		return this.entityData.get(ACTION);
	}

	public void setAction(int action) {
		this.entityData.set(ACTION, action);
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
