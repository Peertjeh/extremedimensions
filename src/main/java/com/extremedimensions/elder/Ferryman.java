package com.extremedimensions.elder;

import com.extremedimensions.hollow.Keeper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Ferryman: the one the Nether's people pay. He comes for travellers who stay too long beyond the wound, and
 * while he walks the Tether runs out faster. Driven by {@link FerrymanService}.
 */
public class Ferryman extends Keeper {
	public Ferryman(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 120;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 260.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.ATTACK_KNOCKBACK, 0.6)
				.add(Attributes.ARMOR, 6.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.tickCount > 40 && !FerrymanService.isDriven(this)) {
			FerrymanService.fade(this);
		}
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public float getVoicePitch() {
		return 0.8F + this.random.nextFloat() * 0.06F;
	}
}
