package com.extremedimensions.seam;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A rift, as it is seen: a tear in the world with a burning rim, cracks running out into the air around it and a
 * tunnel of light through it, the world it leads to hazy at the far end. It is only a sight (nothing collides with it
 * and nothing is saved): the services that open rifts put one where each rift stands, turned the way it faces, keep
 * it there while the rift lasts and let it go (it closes by itself once nobody keeps it).
 *
 * <p>Its position is the floor under the rift; the ring stands on it, {@link #radius()} blocks round.
 */
public class RiftPortal extends Entity {
	/** What shows at the far end of the tunnel. */
	public static final int OVERWORLD_DAY = 0, OVERWORLD_NIGHT = 1, NETHER = 2, SEAM = 3;

	private static final EntityDataAccessor<Integer> VIEW = SynchedEntityData.defineId(RiftPortal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(RiftPortal.class, EntityDataSerializers.FLOAT);
	/** Ticks until it closes (counting down, it shrinks over the last of them), or -1 while it is kept open. */
	private static final EntityDataAccessor<Integer> CLOSING = SynchedEntityData.defineId(RiftPortal.class, EntityDataSerializers.INT);
	/** The entity id of the one player it shows to (a rift home is theirs alone), or -1 for anyone. */
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(RiftPortal.class, EntityDataSerializers.INT);

	/** The entity id of whoever is looking: set by the client to its own player's; -1 elsewhere. */
	public static java.util.function.IntSupplier viewer = () -> -1;

	/** Ticks a portal stays without being kept before it closes by itself (a visitor gone, a server stopping). */
	private static final int UNKEPT = 40;
	private int kept;

	public RiftPortal(EntityType<? extends RiftPortal> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(VIEW, OVERWORLD_DAY);
		builder.define(RADIUS, 2.0F);
		builder.define(CLOSING, -1);
		builder.define(OWNER, -1);
	}

	public void setOwner(int entityId) {
		this.entityData.set(OWNER, entityId);
	}

	/** Whether the one looking can see it (it is anyone's, or theirs). */
	public boolean seen() {
		int owner = this.entityData.get(OWNER);
		return owner < 0 || owner == viewer.getAsInt();
	}

	public int view() {
		return this.entityData.get(VIEW);
	}

	public void setView(int view) {
		if (view() != view) {
			this.entityData.set(VIEW, view);
		}
	}

	public float radius() {
		return this.entityData.get(RADIUS);
	}

	public void setRadius(float radius) {
		this.entityData.set(RADIUS, radius);
	}

	public int closing() {
		return this.entityData.get(CLOSING);
	}

	/** Closes over the next {@code ticks} ticks (shrinking at the end), then goes. */
	public void closeIn(int ticks) {
		this.entityData.set(CLOSING, Math.max(0, ticks));
	}

	/** Kept open for a while more (call every tick it should stay). */
	public void keep() {
		this.kept = this.tickCount;
	}

	/** How far open it is, 0..1: it tears open over its first ticks and shrinks shut over its last. */
	public float openness(float partialTick) {
		float age = this.tickCount + partialTick;
		float open = Mth.clamp(age / 14F, 0, 1);
		int c = closing();
		if (c >= 0) {
			open = Math.min(open, Mth.clamp((c - partialTick) / 14F, 0, 1));
		}
		return open * open * (3 - 2 * open);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel)) {
			if (seen()) {
				sparks();
			}
			return;
		}
		int c = closing();
		if (c > 0) {
			this.entityData.set(CLOSING, c - 1);
		} else if (c == 0 || this.tickCount - this.kept > UNKEPT) {
			this.discard();
		}
	}

	/** Specks of the world pulled into the tunnel round its rim, and sparks off it. */
	private void sparks() {
		float r = radius() * openness(0);
		if (r < 0.2F || this.random.nextInt(2) != 0) {
			return;
		}
		double yaw = Math.toRadians(getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
		double a = this.random.nextDouble() * Math.PI * 2, rr = r * (1.02 + this.random.nextDouble() * 0.25);
		double cy = getY() + radius() + 0.15;
		double px = getX() + sx * Math.cos(a) * rr, py = cy + Math.sin(a) * rr, pz = getZ() + sz * Math.cos(a) * rr;
		// drawn in towards the middle (the vanilla portal speck flies from where it is told back to its start)
		this.level().addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, (getX() - px) * 0.05 + fx * 0.02, (cy - py) * 0.05, (getZ() - pz) * 0.05 + fz * 0.02);
		if (this.random.nextInt(6) == 0) {
			this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0, 0, 0);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 160 * 160;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {}
}
