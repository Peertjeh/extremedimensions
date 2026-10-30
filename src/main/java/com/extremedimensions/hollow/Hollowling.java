package com.extremedimensions.hollow;

import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;
import com.extremedimensions.lurker.TribeMoves;
import com.extremedimensions.registry.ModSounds;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;

/**
 * A Hollowling: the young of the tribe. Small, quick and ash-grey, it giggles, freezes when looked at and steals
 * little things for its hoard. In soul light, and for a Tether Fragment, it may come to trust you.
 *
 * <p>Like the Lurker, its mind lives in a service ({@link HollowService}); one that is not driven dissolves.
 */
public class Hollowling extends PathfinderMob implements LurkerBody {
	private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Hollowling.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(Hollowling.class, EntityDataSerializers.INT);

	public Hollowling(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		TribeMoves.wary(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(POSE, Lurker.POSE_WATCH);
		builder.define(MOOD, MOOD_CALM);
	}

	@Override
	public int getLurkerPose() {
		return this.entityData.get(POSE);
	}

	public void setLurkerPose(int pose) {
		this.entityData.set(POSE, pose);
	}

	/** Its glow: its own, or red while it bites with a swarm. */
	@Override
	public int getLurkerMood() {
		return this.entityData.get(MOOD);
	}

	public void setLurkerMood(int mood) {
		this.entityData.set(MOOD, mood);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 10 == 0) {
			TribeMoves.unstick(this);
		}
		if (this.tickCount > 40 && !HollowService.isDriven(this)) {
			HollowService.fade(this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	/** It drops without harm, so its routes may take a longer drop than other creatures'. */
	@Override
	public int getMaxFallDistance() {
		return 6;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source) || HollowService.ignoreHurt(this, source)) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive()) {
			HollowService.onHollowlingHurt(this, source);
		}
		return hurt;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.HOLLOWLING_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.HOLLOWLING_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.HOLLOWLING_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}
}
