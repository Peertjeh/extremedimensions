package net.battlehub.hub;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import com.mojang.datafixers.util.Pair;

import net.battlehub.mixin.DisplayAccessor;
import net.battlehub.mixin.TextDisplayAccessor;
import net.battlehub.util.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Packets for entities that exist only on one player's client: text displays and lobby characters. */
public final class ClientEntities {
	private ClientEntities() {}

	public static final byte SHADOW = 1;
	public static final byte BILLBOARD_FIXED = 0;
	public static final byte BILLBOARD_CENTER = 3;

	/** An id and uuid no real entity uses: from an entity that is created but never added to the world. */
	public record Handle(int id, UUID uuid, Entity entity) {}

	public static EntityType<?> type(String id) {
		return BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id(id));
	}

	public static Handle handle(ServerLevel level, EntityType<?> type) {
		Entity e = type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
		return e == null ? null : new Handle(e.getId(), e.getUUID(), e);
	}

	public static void add(ServerPlayer p, Handle h, EntityType<?> type, Vec3 pos, float yaw, float headYaw) {
		p.connection.send(new ClientboundAddEntityPacket(h.id(), h.uuid(), pos.x, pos.y, pos.z, 0f, yaw, type, 0, Vec3.ZERO, headYaw));
	}

	/** Like {@link #add} with a pitch (for a camera). */
	public static void add(ServerPlayer p, Handle h, EntityType<?> type, Vec3 pos, float yaw, float pitch, float headYaw) {
		p.connection.send(new ClientboundAddEntityPacket(h.id(), h.uuid(), pos.x, pos.y, pos.z, pitch, yaw, type, 0, Vec3.ZERO, headYaw));
	}

	/** Moves a client-only entity. */
	public static void move(ServerPlayer p, int id, Vec3 pos, float yaw, float pitch) {
		p.connection.send(net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.teleport(id,
				new net.minecraft.world.entity.PositionMoveRotation(pos, Vec3.ZERO, yaw, pitch), java.util.Set.of(), false));
	}

	private static Object interpolationField;

	/**
	 * Display entities glide between positions over this many ticks instead of jumping (smooth camera flights).
	 * Read by name, so a renamed field only costs the smoothing.
	 */
	@SuppressWarnings("unchecked")
	public static void glide(ServerPlayer p, int id, int ticks) {
		if (interpolationField == null) {
			try {
				java.lang.reflect.Field f = net.minecraft.world.entity.Display.class.getDeclaredField("DATA_POS_ROT_INTERPOLATION_DURATION_ID");
				f.setAccessible(true);
				interpolationField = f.get(null);
			} catch (ReflectiveOperationException | RuntimeException e) {
				interpolationField = Boolean.FALSE;
			}
		}
		if (interpolationField instanceof net.minecraft.network.syncher.EntityDataAccessor<?> acc) {
			p.connection.send(new ClientboundSetEntityDataPacket(id, List.<SynchedEntityData.DataValue<?>>of(
					SynchedEntityData.DataValue.create((net.minecraft.network.syncher.EntityDataAccessor<Integer>) acc, ticks))));
		}
	}

	/** Look of a text display: line width, background colour, flags (shadow), size and billboard. */
	public static void textStyle(ServerPlayer p, int id, int lineWidth, int background, byte flags, float scale, byte billboard) {
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
		data.add(SynchedEntityData.DataValue.create(TextDisplayAccessor.battlehub$lineWidth(), lineWidth));
		data.add(SynchedEntityData.DataValue.create(TextDisplayAccessor.battlehub$background(), background));
		data.add(SynchedEntityData.DataValue.create(TextDisplayAccessor.battlehub$styleFlags(), flags));
		data.add(SynchedEntityData.DataValue.create(DisplayAccessor.battlehub$scale(), new Vector3f(scale, scale, scale)));
		if (billboard != BILLBOARD_FIXED) {
			data.add(SynchedEntityData.DataValue.create(DisplayAccessor.battlehub$billboard(), billboard));
		}
		p.connection.send(new ClientboundSetEntityDataPacket(id, data));
	}

	public static void text(ServerPlayer p, int id, Component text) {
		p.connection.send(new ClientboundSetEntityDataPacket(id,
				List.<SynchedEntityData.DataValue<?>>of(SynchedEntityData.DataValue.create(TextDisplayAccessor.battlehub$text(), text))));
	}

	public static void holding(ServerPlayer p, int id, ItemStack item) {
		p.connection.send(new ClientboundSetEquipmentPacket(id, List.of(Pair.of(EquipmentSlot.MAINHAND, item))));
	}

	public static void remove(ServerPlayer p, int[] ids) {
		if (ids.length > 0) {
			p.connection.send(new ClientboundRemoveEntitiesPacket(ids));
		}
	}
}
