package com.extremedimensions.lurker;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Cave Lurker: the Lurker's kin of the deep, pale and low enough for a miner's tunnel (it fits one block wide and
 * two high). It hunts by ear. Everything it decides is done by {@link CaveLurkerService}; its body only carries the
 * Lurker's pose and mood, and turns steadily, a few tens of degrees a tick at most.
 */
public class CaveLurker extends Lurker {
	/** The most it turns in a tick (degrees). */
	private static final float TURN = 40.0F;

	public CaveLurker(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.moveControl = new SteadyMoveControl(this, TURN);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.CAVE_LURKER_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				// a block's step taken without a jump: a jump in a two-high tunnel only knocks its head
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// Not the Lurker's: a Cave Lurker is driven by its own mind, and one nobody drives (after a restart) dissolves
		step();
		if (this.tickCount > 40 && !CaveLurkerService.isDriven(this)) {
			CaveLurkerService.vanish(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (TribeMoves.shrugs(source)) {
			return false;
		}
		// (Monster's, not the Lurker's: what it does when struck is the Cave Lurker's own)
		boolean hurt = this.hurtWithoutLurker(level, source, amount);
		if (hurt && this.isAlive()) {
			CaveLurkerService.onHurt(this, source);
		}
		return hurt;
	}

	/** Higher than its big kin: it is smaller. */
	@Override
	public float getVoicePitch() {
		return 1.35F + this.random.nextFloat() * 0.15F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// claws ticking on stone, on all fours; louder when it runs
		boolean running = getLurkerPose() == POSE_RUN;
		this.playSound(ModSounds.CAVE_LURKER_STEP, running ? 0.6F : 0.3F, 0.9F + this.random.nextFloat() * 0.2F);
	}

	/** A wet rasp of breath in the dark (its clicks, as it listens, come from its service). */
	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return ModSounds.CAVE_LURKER_BREATH;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.CAVE_LURKER_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return ModSounds.CAVE_LURKER_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 480;
	}

	@Override
	public int getMaxFallDistance() {
		return 5;
	}
}
