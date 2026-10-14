package net.battlehub.games.prophunt;

import java.util.Collection;
import java.util.UUID;

import org.joml.Vector3f;

import net.battlehub.games.Visibility;
import net.battlehub.mixin.BlockDisplayAccessor;
import net.battlehub.mixin.DisplayAccessor;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What hunters see of a prop: a block that follows the (hidden) player, plus an invisible 1x1 hitbox to hit it.
 * When the prop stands still long enough it becomes "solid": the floating block is replaced by a real-looking
 * block on the grid (sent only to the other players), indistinguishable from the scenery. Also used for decoys,
 * which never move.
 */
public final class Disguise {
	private final ServerLevel level;
	public final UUID owner;
	private BlockState state;
	private Display.BlockDisplay display;
	private Interaction hitbox;
	private boolean locked;
	private float yaw;
	private BlockPos solid;
	private Vec3 last;
	private int still;

	public Disguise(ServerLevel level, UUID owner, BlockState state, Vec3 pos, float yaw, boolean hideHitboxFromOwner) {
		this.level = level;
		this.owner = owner;
		this.state = state;
		this.yaw = snap(yaw);
		this.last = pos;
		spawnDisplay(pos);
		hitbox = create(level, "minecraft:interaction", Interaction.class);
		if (hitbox != null) {
			hitbox.snapTo(pos.x, pos.y, pos.z);
			hitbox.addTag("battlehub_prop");
			if (hideHitboxFromOwner && owner != null) {
				// The prop's own clicks go through its hitbox
				Visibility.hideFrom(hitbox, owner);
			}
			level.addFreshEntity(hitbox);
		}
	}

	private static <T extends Entity> T create(ServerLevel level, String id, Class<T> type) {
		EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id(id));
		Entity e = t == null ? null : t.create(level, EntitySpawnReason.COMMAND);
		return type.isInstance(e) ? type.cast(e) : null;
	}

	private void spawnDisplay(Vec3 pos) {
		display = create(level, "minecraft:block_display", Display.BlockDisplay.class);
		if (display == null) {
			return;
		}
		((BlockDisplayAccessor) display).battlehub$setBlockState(state);
		// Centre the block on the entity (a display draws from its corner) and glide between positions
		display.getEntityData().set(DisplayAccessor.battlehub$translation(), new Vector3f(-0.5f, 0f, -0.5f));
		display.getEntityData().set(DisplayAccessor.battlehub$posRotDuration(), 2);
		display.snapTo(pos.x, pos.y, pos.z);
		display.setYRot(yaw);
		display.addTag("battlehub_prop");
		level.addFreshEntity(display);
	}

	private static float snap(float yaw) {
		return Math.round(yaw / 90f) * 90f;
	}

	public BlockState state() {
		return state;
	}

	public boolean locked() {
		return locked;
	}

	/** Lock the block's facing (looking around no longer turns it), or unlock it. */
	public boolean toggleLock() {
		locked = !locked;
		return locked;
	}

	public boolean isSolid() {
		return solid != null;
	}

	public BlockPos solidPos() {
		return solid;
	}

	/** Ticks the prop has been standing still. */
	public int stillTicks() {
		return still;
	}

	/** The invisible box hunters hit (tests). */
	public Entity hitbox() {
		return hitbox;
	}

	public boolean isHitbox(Entity e) {
		return e != null && (e == hitbox || e == display);
	}

	public void setState(BlockState s, Collection<ServerPlayer> viewers) {
		state = s;
		if (display != null) {
			((BlockDisplayAccessor) display).battlehub$setBlockState(s);
		}
		if (solid != null) {
			sendFake(viewers);
		}
	}

	/**
	 * Follows the prop. After {@code solidifyTicks} standing still on the ground (with an empty block where it
	 * stands) it turns solid; moving breaks that.
	 */
	public void follow(ServerPlayer p, int solidifyTicks, Collection<ServerPlayer> viewers) {
		Vec3 pos = p.position();
		boolean moved = pos.distanceToSqr(last) > 0.0004;
		last = pos;
		if (moved) {
			still = 0;
			if (solid != null) unsolidify(viewers);
		} else {
			still++;
		}
		if (solid == null) {
			if (!locked) {
				yaw = snap(p.getYRot());
			}
			if (display != null) {
				display.teleportTo(pos.x, pos.y, pos.z);
				display.setYRot(yaw);
			}
			if (hitbox != null) {
				hitbox.teleportTo(pos.x, pos.y, pos.z);
			}
			BlockPos at = BlockPos.containing(pos.x, pos.y + 0.01, pos.z);
			if (still >= solidifyTicks && p.onGround() && level.getBlockState(at).isAir()) {
				solidify(at, viewers);
			}
		}
	}

	/** Snaps onto the grid as a block only the other players see (the hitbox moves along). */
	public void solidify(BlockPos at, Collection<ServerPlayer> viewers) {
		solid = at.immutable();
		if (display != null) {
			display.discard();
			display = null;
		}
		if (hitbox != null) {
			hitbox.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		}
		sendFake(viewers);
	}

	public void unsolidify(Collection<ServerPlayer> viewers) {
		if (solid == null) {
			return;
		}
		BlockPos was = solid;
		solid = null;
		ClientboundBlockUpdatePacket real = new ClientboundBlockUpdatePacket(was, level.getBlockState(was));
		for (ServerPlayer v : viewers) {
			v.connection.send(real);
		}
		spawnDisplay(last);
	}

	/** (Re)sends the fake block to these players (new viewers, or after a client reset it). */
	public void sendFake(Collection<ServerPlayer> viewers) {
		if (solid == null) {
			return;
		}
		ClientboundBlockUpdatePacket fake = new ClientboundBlockUpdatePacket(solid, state);
		for (ServerPlayer v : viewers) {
			if (!v.getUUID().equals(owner)) v.connection.send(fake);
		}
	}

	/** Gone: entities removed, the real block shown again. */
	public void remove(Collection<ServerPlayer> viewers) {
		unsolidify(viewers);
		if (display != null) {
			display.discard();
			display = null;
		}
		if (hitbox != null) {
			Visibility.release(hitbox);
			hitbox.discard();
			hitbox = null;
		}
	}

	public Vec3 position() {
		return solid != null ? Vec3.atBottomCenterOf(solid) : last;
	}
}
