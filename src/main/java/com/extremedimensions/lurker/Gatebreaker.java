package com.extremedimensions.lurker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Gatebreaker: the biggest of the tribe, woken by the Wound Storms, a slow giant of storm-grey stone and iron with
 * a riveted helm for a mask. It comes through a tear in the storm for whoever the storm rages round and breaks through
 * whatever walls stand between (where the world lets creatures break blocks), then brings its fists down. Everything
 * it decides is done by {@link GatebreakerService}.
 */
public class Gatebreaker extends Lurker {
	public Gatebreaker(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, 20.0F);
		this.xpReward = 60;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.GATEBREAKER_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		step();
		if (this.tickCount > 40 && !GatebreakerService.isDriven(this)) {
			GatebreakerService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		return this.hurtWithoutLurker(level, source, amount);
	}

	@Override
	public float getVoicePitch() {
		return 0.5F + this.random.nextFloat() * 0.1F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.4F, 0.5F + this.random.nextFloat() * 0.1F);
	}

	@Override
	public int getMaxFallDistance() {
		return 12;
	}
}
