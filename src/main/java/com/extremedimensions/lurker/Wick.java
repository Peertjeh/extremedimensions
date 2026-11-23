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
 * The Wick: one of the tribe's smallest, a knee-high thing of tallow and soot with a flame for eyes. It comes out on
 * dark nights to where the light is and eats it: torches, lanterns, candles, one after the other, until it is full.
 * It fears the cold blue light of soul fire, and water (rain too) puts it out. Everything it decides is done by
 * {@link WickService}.
 */
public class Wick extends Lurker {
	public Wick(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, 50.0F);
		this.xpReward = 3;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.WICK_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.STEP_HEIGHT, 0.6);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		step();
		if (this.tickCount > 40 && !WickService.isDriven(this)) {
			WickService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			WickService.onHurt(this, source);
		}
		return hurt;
	}

	/** Water, and rain, put it out. */
	@Override
	public boolean isSensitiveToWater() {
		return true;
	}

	/** A thing of fire: flame does it no harm. */
	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public float getVoicePitch() {
		return 1.6F + this.random.nextFloat() * 0.3F;
	}

	/** The soft sputter of a candle. */
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CANDLE_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FIRE_EXTINGUISH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.CANDLE_STEP, 0.25F, 1.4F + this.random.nextFloat() * 0.2F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	public int getMaxFallDistance() {
		return 4;
	}
}
