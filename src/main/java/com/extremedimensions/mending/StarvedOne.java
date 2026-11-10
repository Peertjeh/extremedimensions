package com.extremedimensions.mending;

import com.extremedimensions.hollow.Keeper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
 *
 * <p>It claws its way up out of the Heart before its fight (3.48): nothing hurts it while it rises, and the client is
 * told how far it has come, to pose it dragging itself up. It dies as the Elders do, slowly: its chains snap, its embers
 * go out, and it sinks into its own light (see {@link MendingService#dying}). Its voice is its own
 * ({@code elder.starved_one.*}), looked up as it is wanted.
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
	/** How far it has risen out of the Heart (0 to 1), or -1 once it is free of it. */
	private static final EntityDataAccessor<Float> RISING = SynchedEntityData.defineId(StarvedOne.class, EntityDataSerializers.FLOAT);
	/** Ticks its death takes (as an Elder's: its chains snapping, its embers going out). */
	public static final int DEATH_TICKS = com.extremedimensions.elder.ElderBoss.DEATH_TICKS;

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
		builder.define(RISING, -1.0F);
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

	/** Whether it is still rising out of the Heart (nothing hurts it meanwhile). */
	public boolean isRising() {
		return this.entityData.get(RISING) >= 0;
	}

	/** How far it has risen out of the Heart, 0 to 1 (1 once it is free of it). */
	public float rising() {
		float r = this.entityData.get(RISING);
		return r < 0 ? 1.0F : r;
	}

	/** How far it has risen (0 to 1), or a negative number once it is free of the Heart. */
	public void setRising(float share) {
		float now = share < 0 ? -1.0F : Math.min(1.0F, share);
		if (Math.abs(this.entityData.get(RISING) - now) > 1e-4) {
			this.entityData.set(RISING, now);
		}
	}

	/**
	 * One of its own sounds ({@code extremedimensions:elder.starved_one.<name>}), looked up as it is wanted; while it is
	 * missing (a resource pack without it, a build without it), the vanilla {@code fallback} in its place.
	 */
	public static SoundEvent sound(String name, String fallback) {
		SoundEvent own = lookup(com.extremedimensions.ExtremeDimensions.id("elder.starved_one." + name));
		if (own != null) {
			return own;
		}
		Identifier vanilla = Identifier.fromNamespaceAndPath("minecraft", fallback);
		SoundEvent v = lookup(vanilla);
		return v != null ? v : SoundEvent.createVariableRangeEvent(vanilla);
	}

	private static SoundEvent lookup(Identifier id) {
		return BuiltInRegistries.SOUND_EVENT.get(id).map(h -> h.value()).orElse(null);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return sound("ambient", "entity.warden.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return sound("hurt", "entity.warden.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return sound("death", "entity.warden.death");
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
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
		// One left over after a restart (nothing drives it) sinks back into the seam
		if (this.tickCount > 40 && !this.isDeadOrDying() && !MendingService.isDriven(this)) {
			MendingService.fade(this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.item.FallingBlockEntity) {
			// The rubble of its arena glances off it (a falling island is what hurts it, see HeartArena)
			return false;
		}
		if (isRising() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			// Still rising out of the Heart: blows pass through the souls it is drawn up in
			com.extremedimensions.server.WorldFx.particleIn(level, "minecraft:enchanted_hit", getX(), getY() + getBbHeight() * 0.5, getZ(),
					getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.2, 6);
			return false;
		}
		// A mace's blow glances off it as off the Elders (3.37)
		float landed = com.extremedimensions.elder.ElderPower.maceGuard(this, level, source, amount);
		return super.hurtServer(level, source, landed * MendingService.damageFactor(this));
	}

	/** It dies slowly, as an Elder does: the Heart's scene of its death plays out, then it is gone (see MendingService). */
	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.level() instanceof ServerLevel level) {
			MendingService.dying(this, level, this.deathTime);
			if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
				level.broadcastEntityEvent(this, (byte) 60);
				this.remove(RemovalReason.KILLED);
				MendingService.died(this, level);
			}
		}
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
