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
 * The Magpie: the tribe's lean thief, black-cloaked with a white mask and a gold glint in its eyes, a little taller than
 * a man. It never touches the player. It goes for what they keep: a chest or a barrel left alone near where they are,
 * from which it takes the brightest things and runs them home to a hoard, the loot held up for all to see. Everything it
 * decides is done by {@link MagpieService}; its body carries the Lurker's pose and mood (blue while it steals).
 */
public class Magpie extends Lurker {
	/** The most it turns in a tick (degrees). */
	private static final float TURN = 45.0F;

	public Magpie(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, TURN);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.MAGPIE_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// Driven by its own mind; one nobody drives (after a restart) is gone, and what it carried with it goes home
		step();
		if (this.tickCount > 40 && !MagpieService.isDriven(this)) {
			MagpieService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			MagpieService.onHurt(this, source);
		}
		return hurt;
	}

	@Override
	public float getVoicePitch() {
		return 1.2F + this.random.nextFloat() * 0.15F;
	}

	/** A dry chatter now and then, as a magpie's. */
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PARROT_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}
}
