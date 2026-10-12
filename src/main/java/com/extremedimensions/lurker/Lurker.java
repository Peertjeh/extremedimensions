package com.extremedimensions.lurker;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Lurker: a member of an ancient tribe bound to the shadows between dimensions.
 *
 * <p>Its body only carries a pose (watching, stalking, taking, draining) that the client animates; everything it
 * decides is done by {@link LurkerService}.
 */
public class Lurker extends Monster {
	public static final int POSE_WATCH = 0;
	public static final int POSE_STALK = 1;
	public static final int POSE_TAKE = 2;
	public static final int POSE_DRAIN = 3;
	public static final int POSE_RUN = 4;

	private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Lurker.class, EntityDataSerializers.INT);

	public Lurker(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 60.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.ATTACK_DAMAGE, 6.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(POSE, POSE_WATCH);
	}

	public int getLurkerPose() {
		return this.entityData.get(POSE);
	}

	public void setLurkerPose(int pose) {
		this.entityData.set(POSE, pose);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		// Lurkers not driven by the service (left over after a restart) dissolve
		if (this.tickCount > 40 && !LurkerService.isDriven(this)) {
			LurkerService.vanish(this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive()) {
			LurkerService.onHurt(this);
		}
		return hurt;
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
	protected void playStepSound(BlockPos pos, BlockState state) {
		int pose = getLurkerPose();
		float vol = 0.8F;
		float pitch = 0.85F + this.random.nextFloat() * 0.2F;
		if (pose == POSE_STALK) {
			// Soft while it creeps in, heavier the closer it gets
			var near = this.level().getNearestPlayer(this, 64.0);
			double d = near == null ? 64.0 : Math.sqrt(near.distanceToSqr(this));
			vol = 0.3F + 0.5F * (float) Math.max(0.0, 1.0 - d / 40.0);
		} else if (pose == POSE_RUN) {
			vol = 1.0F;
			pitch += 0.12F;
		}
		this.playSound(ModSounds.LURKER_STEP, vol, pitch);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 320;
	}
}
