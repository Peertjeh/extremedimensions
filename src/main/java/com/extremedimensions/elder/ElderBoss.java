package com.extremedimensions.elder;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModSounds;
import com.extremedimensions.server.WorldFx;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * An Elder: a colossal boss moved and directed entirely by its service. It has no goals and no physics of its own
 * (nothing that size could path through a forest); it glides, sails or floats where it is put, with weight (see
 * {@link ElderMotion}), and its body and head turn only as its service turns them. Its body carries two numbers for
 * the client: the action under way, which the model animates, and the tempo the action runs at. While it forms nothing
 * hurts it; while its ultimate builds it shrugs off most harm; it dies slowly, breaking apart in light.
 */
public abstract class ElderBoss extends Monster {
	public static final int IDLE = 0;
	/** The ultimate (the same for every Elder). */
	public static final int ULTIMATE = 20;
	/** Forming out of matter and light before the fight (see {@link ElderSpawn}); nothing hurts it meanwhile. */
	public static final int SPAWN = 21;
	/** Ticks its death takes (it dies as it fought its ultimate, ending in the bloom). */
	public static final int DEATH_TICKS = 100;
	private static final String[] TITLES = {"gui.extremedimensions.elder.title.matriarch", "gui.extremedimensions.elder.title.ferryman",
			"gui.extremedimensions.elder.title.weaver"};

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ElderBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> TEMPO = SynchedEntityData.defineId(ElderBoss.class, EntityDataSerializers.FLOAT);
	/** Counts every action begun, so the client also sees a blow struck twice in a row begin again. */
	private static final EntityDataAccessor<Integer> TAKE = SynchedEntityData.defineId(ElderBoss.class, EntityDataSerializers.INT);

	/** How it moves (server only). */
	final ElderMotion motion = new ElderMotion();
	/** The damage of its blows, as a multiple (how hard it fights, with the players fighting it; see {@link ElderPower}). */
	float damageFactor = 1;
	/**
	 * How many times its own health it has, as it fights (see {@link ElderPower}): every blow on it is divided by this.
	 * Its health itself is not raised, as the game holds any creature's health to 1024 at most.
	 */
	float toughness = 1;

	protected ElderBoss(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.xpReward = 250;
	}

	public static AttributeSupplier.Builder elderAttributes(double health, double armor) {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, health)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ARMOR, armor);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, IDLE);
		builder.define(TEMPO, 1.0F);
		builder.define(TAKE, 0);
	}

	public int getAction() {
		return this.entityData.get(ACTION);
	}

	public void setAction(int action) {
		this.entityData.set(ACTION, action);
		this.entityData.set(TAKE, getTake() + 1);
	}

	/** How many actions it has begun (see {@link #TAKE}). */
	public int getTake() {
		return this.entityData.get(TAKE);
	}

	/** How fast its actions run (1 = their own pace); the client plays them at the same pace. */
	public float getTempo() {
		return this.entityData.get(TEMPO);
	}

	public void setTempo(float tempo) {
		if (Math.abs(getTempo() - tempo) > 1e-3) {
			this.entityData.set(TEMPO, tempo);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.item.FallingBlockEntity) {
			// The rubble of its arena glances off it (a falling island is what hurts it, see ElderDomain)
			return false;
		}
		if (getAction() == SPAWN && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			// Still forming: blows pass through the light it is made of
			WorldFx.particleIn(level, "minecraft:enchanted_hit", getX(), getY() + getBbHeight() * 0.5, getZ(), getBbWidth() * 0.5, getBbHeight() * 0.3,
					getBbWidth() * 0.5, 0.2, 6);
			return false;
		}
		if (getAction() == ULTIMATE) {
			// Its ultimate builds behind a ward: most of a blow is shrugged off
			amount *= (float) Math.max(0, 1 - Math.min(1, Config.ELDER_ULTIMATE_GUARD));
			WorldFx.particleIn(level, "minecraft:enchanted_hit", getX(), getY() + getBbHeight() * 0.5, getZ(), getBbWidth() * 0.5, getBbHeight() * 0.3,
					getBbWidth() * 0.5, 0.2, 12);
		}
		// The cores of its arena still standing shield it
		float ward = ElderDomain.ward(this);
		if (ward > 0) {
			amount *= 1 - ward;
			WorldFx.particleIn(level, "minecraft:enchanted_hit", getX(), getY() + getBbHeight() * 0.5, getZ(), getBbWidth() * 0.4, getBbHeight() * 0.3,
					getBbWidth() * 0.4, 0.1, 3);
		}
		return super.hurtServer(level, source, amount / Math.max(0.1F, toughness));
	}

	/** How many times its own health it has, as it fights (for tests). */
	public float toughness() {
		return toughness;
	}

	@Override
	protected void tickHeadTurn(float yBodyRot) {
		// Body and head turn only as its service turns them
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel)) {
			// Only its yaw and head reach a client, never its body: the body follows the yaw its service turned
			this.yBodyRot = this.getYRot();
		}
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.level() instanceof ServerLevel level) {
			java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
			String dim = ElderFx.dim(this);
			double w = getBbWidth() * 0.7, h = getBbHeight();
			double k = this.deathTime / (double) DEATH_TICKS;
			if (this.deathTime == 1) {
				// The bars close in on its name, and everyone near is made to watch
				ElderFx.cinema(this, 96, com.extremedimensions.network.ModPackets.BossFxPayload.DEATH, getId(), DEATH_TICKS, TITLES[ElderFx.index(this)]);
				WorldFx.soundIn(dim, "minecraft:block.beacon.deactivate", "hostile", getX(), getY() + h * 0.6, getZ(), 5f, 0.5f);
				ElderFx.roar(this, 6f, 0.35f);
			}
			if (this.deathTime % 2 == 0) {
				ElderFx.shake(this, getX(), getZ(), 64, (float) (0.15 + 0.6 * k), 4);
			}
			if (k < 0.8) {
				// Its light is drawn in, as in its ultimate
				for (int i = 0; i < 4; i++) {
					double a = rnd.nextDouble() * Math.PI * 2;
					WorldFx.particleIn(level, "minecraft:reverse_portal", getX() + Math.cos(a) * w * 2, getY() + rnd.nextDouble(0.2, 1.0) * h,
							getZ() + Math.sin(a) * w * 2, 0.2, 0.2, 0.2, 0.4, 2);
				}
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", getX(), getY() + h * 0.6, getZ(), w * 0.4, h * 0.2, w * 0.4, 0.05, 4);
			}
			if (this.deathTime % 6 == 0) {
				// It breaks apart: bursts of light all over its body, louder towards the end
				double x = getX() + rnd.nextDouble(-w, w), y = getY() + rnd.nextDouble(0.1, 0.95) * h, z = getZ() + rnd.nextDouble(-w, w);
				WorldFx.particleIn(level, "minecraft:explosion", x, y, z, 0.5, 0.5, 0.5, 0, 1);
				WorldFx.particleIn(level, "minecraft:end_rod", x, y, z, 1, 1, 1, 0.12, 14);
				WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", x, y, z, 2.5f, 0.5f + (float) k * 0.6f);
			}
			if (this.deathTime == DEATH_TICKS - 5) {
				// The bloom
				WorldFx.particleIn(level, "minecraft:explosion_emitter", getX(), getY() + h * 0.5, getZ(), 1, 1, 1, 0, 4);
				WorldFx.particleIn(level, "extremedimensions:soul_mote", getX(), getY() + h * 0.5, getZ(), w, h * 0.4, w, 0.25, 220);
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", getX(), getY() + h * 0.5, getZ(), w, h * 0.4, w, 0.3, 160);
				for (int r = 4; r <= 24; r += 5) {
					ElderFx.ring(level, "minecraft:end_rod", getX(), getY() + 0.3, getZ(), r, 18 + r * 3, r * 0.2);
				}
				WorldFx.soundIn(dim, "minecraft:entity.wither.death", "hostile", getX(), getY() + 6, getZ(), 5f, 0.6f);
				WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", getX(), getY() + 6, getZ(), 5f, 0.4f);
				ElderFx.cinema(this, 96, com.extremedimensions.network.ModPackets.BossFxPayload.FLASH, 0, 0.9f, "");
				ElderFx.shake(this, getX(), getZ(), 80, 1.4f, 30);
			}
			if (this.deathTime == DEATH_TICKS - 1) {
				ElderFx.cinema(this, 160, com.extremedimensions.network.ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
			}
			if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
				level.broadcastEntityEvent(this, (byte) 60);
				this.remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	protected void registerGoals() {
		// Directed by its service
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// One left over after a restart (nothing drives it) sinks back where it came from
		if (this.tickCount > 40 && !driven()) {
			fadeOut();
		}
	}

	/** Whether its service is directing it. */
	protected abstract boolean driven();

	/** Leaves without a fight (and without a reward). */
	protected abstract void fadeOut();

	/** How far the model reaches past the hitbox, so it is drawn whenever any of it is in view. */
	public abstract double modelReach();

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.ELDER_AMBIENT[ElderFx.index(this)];
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.ELDER_HURT[ElderFx.index(this)];
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.ELDER_DEATH[ElderFx.index(this)];
	}

	@Override
	public int getAmbientSoundInterval() {
		return 260;
	}
}
