package com.extremedimensions.lurker;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;

/**
 * The Lurker: a member of an ancient tribe bound to the shadows between dimensions.
 *
 * <p>It shares the Enderman's long frame (and its animations: the opening jaw, the trembling, carrying a block in
 * its arms) but none of its instincts: no block stealing, no rage at a stare, no fear of water. Everything it does is
 * decided by {@link LurkerService}.
 */
public class Lurker extends Enderman {
	public Lurker(EntityType<? extends Enderman> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// None of the Enderman's own behaviour (daylight teleports, water). Lurkers not driven by the
		// service (left over after a restart) dissolve.
		if (this.tickCount > 40 && !LurkerService.isDriven(this)) {
			LurkerService.vanish(this);
		}
	}

	@Override
	public boolean isSensitiveToWater() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (LurkerService.onHurt(this)) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		// Never drop the anchor light it may be holding; stolen loot is returned by the service
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.LURKER_WHISPER;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}
}
