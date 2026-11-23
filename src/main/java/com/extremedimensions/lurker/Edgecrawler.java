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
 * The Edgecrawler: one of the Seam's own, long in the arm and grey as the undersides of the islands it clings to, its
 * mask set with small glowing eyes. It hangs under an island's rim, climbs up behind whoever stands near the edge, and
 * drags them to it and over. Everything it decides is done by {@link EdgecrawlerService}.
 */
public class Edgecrawler extends Lurker {
	public Edgecrawler(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, 45.0F);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.EDGECRAWLER_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		step();
		if (this.tickCount > 40 && !EdgecrawlerService.isDriven(this)) {
			EdgecrawlerService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			EdgecrawlerService.onHurt(this, source);
		}
		return hurt;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F + this.random.nextFloat() * 0.1F;
	}

	/** The click and rasp of long limbs on stone. */
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.45F, 0.6F + this.random.nextFloat() * 0.15F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	/** It climbs: a drop does not keep it from its prey. */
	@Override
	public int getMaxFallDistance() {
		return 6;
	}
}
