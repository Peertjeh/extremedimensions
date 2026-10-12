package com.extremedimensions.seam;

import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Thinned: what is left of the Weavers' people, pale and stooped and worn to threads, still walking the streets
 * of their villages. They watch, and mean nothing by it, until a visitor's thread runs thin; then they come for it,
 * and they only move while nobody is looking.
 *
 * <p>Like the rest of the tribe, its mind lives in a service ({@link SeamFauna}); one that is not driven dissolves.
 */
public class Thinned extends PathfinderMob implements LurkerBody {
	private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Thinned.class, EntityDataSerializers.INT);

	public Thinned(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(POSE, Lurker.POSE_WATCH);
	}

	@Override
	public int getLurkerPose() {
		return this.entityData.get(POSE);
	}

	public void setLurkerPose(int pose) {
		this.entityData.set(POSE, pose);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.45, 160));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 20.0F));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount > 40 && !SeamFauna.isDriven(this)) {
			SeamFauna.fade(this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.LURKER_WHISPER;
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
	public float getVoicePitch() {
		return 1.35F + this.random.nextFloat() * 0.2F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}
}
