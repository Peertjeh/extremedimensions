package com.extremedimensions.elder;

import com.extremedimensions.hollow.Keeper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Last Weaver: one of those who wove themselves into the seal and did not quite finish. It wakes in the villages
 * of the Thinned, its own people, and unpicks the thread of whoever it strikes. Driven by {@link WeaverService}.
 */
public class Weaver extends Keeper {
	public Weaver(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 120;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 240.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.ATTACK_KNOCKBACK, 0.4)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.tickCount > 40 && !WeaverService.isDriven(this)) {
			WeaverService.fade(this);
		}
	}

	@Override
	public float getVoicePitch() {
		return 1.2F + this.random.nextFloat() * 0.1F;
	}
}
