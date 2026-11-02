package com.extremedimensions.lurker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Cragback: the biggest of the Seam's own, broad and slow, its back grown over with the islands' own stone and
 * veins of Tetherite. Asleep it is a boulder like any other; dig near it and it wakes. Everything it decides is done by
 * {@link CragbackService}.
 */
public class Cragback extends Lurker {
	public Cragback(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, 25.0F);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 140.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		step();
		if (this.tickCount > 40 && !CragbackService.isDriven(this)) {
			CragbackService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			CragbackService.onHurt(this, source);
		}
		return hurt;
	}

	/** The stones of its back ride it, as many as it has (a creature otherwise carries one rider). */
	@Override
	protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
		return passenger instanceof net.minecraft.world.entity.Display.BlockDisplay || super.canAddPassenger(passenger);
	}

	/** Asleep it is stone: nothing pushes it. */
	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F + this.random.nextFloat() * 0.1F;
	}

	/** Asleep it makes no sound at all; awake, a grinding rumble. */
	@Override
	protected SoundEvent getAmbientSound() {
		return getLurkerMood() == LurkerBody.MOOD_CALM ? null : SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DEEPSLATE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 0.9F, 0.55F + this.random.nextFloat() * 0.1F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	public int getMaxFallDistance() {
		return 4;
	}
}
