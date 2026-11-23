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
 * The Threadbiter: one of the Seam's own, a knee-high scrap of the tribe with a mouth too big for it, its cloak shot
 * through with stolen gold thread. They run in packs over the islands and gnaw at a visitor's thread, the time that
 * holds them in the Seam; slay one and what it ate comes back to you. Everything it decides is done by
 * {@link ThreadbiterService}.
 */
public class Threadbiter extends Lurker {
	public Threadbiter(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, 60.0F);
		this.xpReward = 3;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.THREADBITER_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.34)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.STEP_HEIGHT, 0.6);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		step();
		if (this.tickCount > 40 && !ThreadbiterService.isDriven(this)) {
			ThreadbiterService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			ThreadbiterService.onHurt(this, source);
		}
		return hurt;
	}

	@Override
	public float getVoicePitch() {
		return 1.5F + this.random.nextFloat() * 0.3F;
	}

	/** A dry skitter, as of something small in the walls. */
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SILVERFISH_STEP, 0.3F, 1.2F + this.random.nextFloat() * 0.3F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 120;
	}

	/** It does not throw itself off an island after its prey. */
	@Override
	public int getMaxFallDistance() {
		return 3;
	}
}
