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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Sculk Lurker: a small creature of the tribe that serves what sleeps under the Ancient Cities. It never strikes a
 * player; it rattles the shriekers to wake the Warden and, once the Warden is up, leads it to them. The Warden spares
 * it (it never targets it, never hears it, and does it no harm), and its own steps make no vibration at all. Its mind
 * is {@link SculkLurkerService}; one nobody drives dissolves.
 */
public class SculkLurker extends PathfinderMob implements LurkerBody {
	private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(SculkLurker.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(SculkLurker.class, EntityDataSerializers.INT);
	/** The most it turns in a tick (degrees). */
	private static final float TURN = 45.0F;

	public SculkLurker(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		TribeMoves.wary(this);
		this.moveControl = new SteadyMoveControl(this, TURN);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 14.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
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
		if (this.tickCount > 40 && !SculkLurkerService.isDriven(this)) {
			SculkLurkerService.vanish(this);
		}
	}

	/** Its steps, as the sculk's own, make no vibration: no sensor, no shrieker and no Warden hears it walk. */
	@Override
	public boolean dampensVibrations() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public int getMaxFallDistance() {
		return 6;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// a fall or a wall does it no harm, and nor does the Warden it serves
		if (TribeMoves.shrugs(source) || source.getEntity() instanceof Warden) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public float getVoicePitch() {
		return 1.6F + this.random.nextFloat() * 0.2F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.SCULK_LURKER_CHIRP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SCULK_LURKER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.SCULK_LURKER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(ModSounds.SCULK_LURKER_STEP, 0.35F, 0.9F + this.random.nextFloat() * 0.2F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}
}
