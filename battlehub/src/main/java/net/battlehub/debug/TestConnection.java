package net.battlehub.debug;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.IntCollection;
import net.battlehub.mixin.ConnectionAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

/**
 * A network connection for simulated test players: always "connected", every outgoing packet is dropped. It
 * remembers which entities the client would have, like a client does: added, removed, all gone after a respawn.
 */
final class TestConnection extends Connection {
	final Set<Integer> entities = ConcurrentHashMap.newKeySet();
	/** The brightness override each display entity got (absent: never set). */
	final java.util.Map<Integer, Integer> brightness = new ConcurrentHashMap<>();
	/** The hotbar slot the client holds, as far as the server told it (-1: never told). */
	volatile int heldSlot = -1;
	/** Arm swings (main hand) the client was shown, per entity id. */
	final java.util.Map<Integer, Integer> swings = new ConcurrentHashMap<>();

	TestConnection() {
		super(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel();
		channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
			@Override
			public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
				seen(msg);
				ReferenceCountUtil.release(msg);
				promise.setSuccess();
			}
		});
		((ConnectionAccessor) (Object) this).battlehub$setChannel(channel);
	}

	private void seen(Object msg) {
		if (msg instanceof ClientboundBundlePacket bundle) {
			for (Packet<?> p : bundle.subPackets()) {
				seen(p);
			}
		} else if (msg instanceof ClientboundAddEntityPacket add) {
			entities.add(add.getId());
		} else if (msg instanceof ClientboundRemoveEntitiesPacket rem) {
			for (int id : removedIds(rem)) {
				entities.remove(id);
			}
		} else if (msg instanceof ClientboundRespawnPacket) {
			entities.clear();
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket held) {
			heldSlot = held.slot();
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundAnimatePacket anim) {
			int[] ia = idAndAction(anim);
			if (ia != null && ia[1] == 0) swings.merge(ia[0], 1, Integer::sum);
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket data) {
			int brightnessId = net.battlehub.mixin.DisplayAccessor.battlehub$brightness().id();
			for (net.minecraft.network.syncher.SynchedEntityData.DataValue<?> v : data.packedItems()) {
				if (v.id() == brightnessId && v.value() instanceof Integer b) brightness.put(data.id(), b);
			}
		}
	}

	/** The entity id and the action (0: swing the main hand) of an animation packet, whatever their getters are called. */
	private static int[] idAndAction(Object packet) {
		Integer id = null, action = null;
		for (Method m : packet.getClass().getMethods()) {
			if (m.getParameterCount() != 0 || m.getReturnType() != int.class) continue;
			String n = m.getName().toLowerCase(java.util.Locale.ROOT);
			try {
				if (n.contains("action")) action = (Integer) m.invoke(packet);
				else if (n.contains("id")) id = (Integer) m.invoke(packet);
			} catch (ReflectiveOperationException e) {
				return null;
			}
		}
		return id == null || action == null ? null : new int[] {id, action};
	}

	/** The ids in a remove packet, whatever its getter is called in this version. */
	private static int[] removedIds(ClientboundRemoveEntitiesPacket packet) {
		for (Method m : packet.getClass().getMethods()) {
			if (m.getParameterCount() == 0 && IntCollection.class.isAssignableFrom(m.getReturnType())) {
				try {
					return ((IntCollection) m.invoke(packet)).toIntArray();
				} catch (ReflectiveOperationException e) {
					return new int[0];
				}
			}
		}
		return new int[0];
	}

	@Override
	public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) {
	}

	@Override
	public void setupOutboundProtocol(ProtocolInfo<?> info) {
	}

	@Override
	public void setReadOnly() {
	}

	@Override
	public void handleDisconnection() {
	}
}
