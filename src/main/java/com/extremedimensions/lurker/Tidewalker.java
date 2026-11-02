package com.extremedimensions.lurker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Tidewalker: the tribe's kin of the deep water, lean and long-limbed, its cloak gone to kelp, a mask grown over
 * with barnacles. It lies just under the surface of seas and rivers where someone swims or rows, throws them out of
 * their boat and drags them down. It breathes water and swims as it likes; it is flown through the water by
 * {@link TidewalkerService}, which decides everything it does.
 */
public class Tidewalker extends Lurker {
	public Tidewalker(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.ATTACK_DAMAGE, 4.0);
	}

	/** No goals of its own (and none to float it to the top): the water is its home. */
	@Override
	protected void registerGoals() {
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.tickCount > 40 && !TidewalkerService.isDriven(this)) {
			TidewalkerService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			TidewalkerService.onHurt(this, source);
		}
		return hurt;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	/** Currents do not carry it: it swims where it wants. */
	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isInWater() ? SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.isInWater() ? SoundEvents.DROWNED_HURT_WATER : SoundEvents.DROWNED_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.isInWater() ? SoundEvents.DROWNED_DEATH_WATER : SoundEvents.DROWNED_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F + this.random.nextFloat() * 0.1F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}
}
