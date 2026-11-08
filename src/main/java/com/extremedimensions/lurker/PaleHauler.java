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
 * The Pale Hauler (3.39): one of the Endermen of the Pale Reach, gone further than the rest. Lanky and quick, its dark
 * body hung with a hood and a ragged mantle of pale end stone, it hauls blocks for the anchors the Endermen build to
 * find their way home, and the blocks it likes best are the ones a visitor laid out over the void: it pulls them out
 * of a bridge from under the next step and carries them off over its head. Where there is nothing to haul it leaps at
 * whoever is near. They come in twos and threes. Look one in the eye and it lets go and steps away. Everything it
 * decides is done by {@link PaleHaulerService}.
 */
public class PaleHauler extends Lurker {
	public PaleHauler(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, 70.0F);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.PALE_HAULER_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.36)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		step();
		if (this.tickCount > 40 && !PaleHaulerService.isDriven(this)) {
			PaleHaulerService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			PaleHaulerService.onHurt(this, source);
		}
		return hurt;
	}

	@Override
	public float getVoicePitch() {
		return 1.25F + this.random.nextFloat() * 0.2F;
	}

	/** An Enderman's voice, higher: something smaller that has been out here longer. */
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENDERMAN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENDERMAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENDERMAN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.STONE_STEP, 0.25F, 1.3F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 140;
	}

	/** It does not throw itself off a bridge after its prey. */
	@Override
	public int getMaxFallDistance() {
		return 3;
	}
}
