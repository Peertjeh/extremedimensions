package com.extremedimensions.hollow;

import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Keeper: an old Lurker with a bone-white mask that guards a hoard. Touch what it keeps and it hunts you, with
 * a boss bar, blinks through the shadows and a hushing screech. Driven by {@link HollowService}.
 */
public class Keeper extends Lurker {
	public Keeper(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 140.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// Not Lurker's: a Keeper is driven by the hoard it guards
		step();
		if (this.tickCount > 40 && !HollowService.isDriven(this)) {
			HollowService.fade(this);
		}
	}

	@Override
	public float getVoicePitch() {
		return 0.65F + this.random.nextFloat() * 0.1F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.LURKER_AMBIENT;
	}
}
