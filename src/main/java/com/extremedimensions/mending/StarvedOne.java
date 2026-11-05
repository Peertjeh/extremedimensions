package com.extremedimensions.mending;

import com.extremedimensions.hollow.Keeper;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Starved One: what came through the first wound, and what the tribe has hidden from ever since. A gaunt, hooded
 * thing some seven blocks tall with a halo of rusted spikes, that hangs in the air over the Heart and drifts through
 * stone as through smoke. It is flown by {@link MendingService}: it circles its prey, falls on it claws first, calls
 * the tribe's young to it, and is bound by the four pillars round the Heart ({@link Binding}).
 */
public class StarvedOne extends Keeper {
	/** Its own health with one player at the Heart, from its setting (more come, more it has: see MendingService.healthFor). */
	public static double baseHealth() {
		return Math.max(1, Math.min(1024, com.extremedimensions.config.Config.STARVED_HEALTH));
	}

	/** What it is doing (the client poses it to match): hanging in the air, falling on its prey, a raking blow... */
	public static final int IDLE = 0, SWOOP = 1, CLAW = 2, CALL = 3, BITE = 4, DRAG = 5, BLINK = 6;

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(StarvedOne.class, EntityDataSerializers.INT);
	/** Counts the actions begun, so the client sees an action begin again (the same one twice). */
	private static final EntityDataAccessor<Integer> TAKE = SynchedEntityData.defineId(StarvedOne.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BOUND = SynchedEntityData.defineId(StarvedOne.class, EntityDataSerializers.BOOLEAN);

	public StarvedOne(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 300;
		this.setNoGravity(true);
		// Its own health from its setting (3.32), as it is made
		var max = this.getAttribute(Attributes.MAX_HEALTH);
		if (!level.isClientSide() && max != null) {
			max.setBaseValue(baseHealth());
			this.setHealth(this.getMaxHealth());
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, com.extremedimensions.config.Config.STARVED_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.ARMOR, 8.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, IDLE);
		builder.define(TAKE, 0);
		builder.define(BOUND, false);
	}

	/** No goals of its own: it is flown by the Heart. */
	@Override
	protected void registerGoals() {
	}

	public int getAction() {
		return this.entityData.get(ACTION);
	}

	public int getTake() {
		return this.entityData.get(TAKE);
	}

	/** Begins an action (again, if it is the one under way). */
	public void act(int action) {
		this.entityData.set(ACTION, action);
		this.entityData.set(TAKE, this.entityData.get(TAKE) + 1);
	}

	/** Back to hanging in the air, if it is doing anything else. */
	public void rest() {
		if (getAction() != IDLE) {
			act(IDLE);
		}
	}

	public boolean isBound() {
		return this.entityData.get(BOUND);
	}

	public void setBound(boolean bound) {
		this.entityData.set(BOUND, bound);
	}

	/** It drifts through stone as through smoke (as a Vex does), and never falls. */
	@Override
	public void tick() {
		this.noPhysics = true;
		super.tick();
		this.noPhysics = false;
		this.setNoGravity(true);
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
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
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
