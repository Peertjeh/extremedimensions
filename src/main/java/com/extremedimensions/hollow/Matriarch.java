package com.extremedimensions.hollow;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Matriarch: the oldest of the Keepers, who comes for those who have slain her children's keepers. She is driven
 * like any Keeper by {@link HollowService}, with tricks of her own: she calls her brood and wails.
 */
public class Matriarch extends Keeper {
	public Matriarch(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 150;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 320.0)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_DAMAGE, 11.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.2)
				.add(Attributes.ARMOR, 4.0);
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F + this.random.nextFloat() * 0.08F;
	}
}
