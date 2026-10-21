package com.extremedimensions.mending;

import com.extremedimensions.hollow.Keeper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Starved One: what came through the first wound, and what the tribe has hidden from ever since. A gaunt,
 * towering thing in the Lurker's shape, driven by {@link MendingService}.
 */
public class StarvedOne extends Keeper {
	/** Its health with one player at the Heart (more come, more it has: see MendingService.healthFor). */
	public static final double BASE_HEALTH = 500.0;

	public StarvedOne(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 300;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BASE_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.ARMOR, 8.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.tickCount > 40 && !MendingService.isDriven(this)) {
			MendingService.fade(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return super.hurtServer(level, source, amount * MendingService.damageFactor(this));
	}

	@Override
	public float getVoicePitch() {
		return 0.45F + this.random.nextFloat() * 0.1F;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}
}
