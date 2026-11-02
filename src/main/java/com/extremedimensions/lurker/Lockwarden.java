package com.extremedimensions.lurker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Lockwarden: the Dragon was the lock on the first wound, and this one of the tribe kept watch over the lock. Tall
 * and thin, cloaked in the End's own dark purple, a mask of pale end stone with a keyhole in it. With the Dragon fallen
 * there is no lock left to keep, and it roams the End after what still opens ways: it steps from place to place as an
 * Enderman does, snatches ender pearls and sometimes leaves whoever it robbed on a lost islet. Everything it decides is
 * done by {@link LockwardenService}.
 */
public class Lockwarden extends Lurker {
	public Lockwarden(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, 40.0F);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 80.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		step();
		if (this.tickCount > 40 && !LockwardenService.isDriven(this)) {
			LockwardenService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			LockwardenService.onHurt(this, source);
		}
		return hurt;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F + this.random.nextFloat() * 0.1F;
	}

	@Override
	public int getMaxFallDistance() {
		return 16;
	}
}
