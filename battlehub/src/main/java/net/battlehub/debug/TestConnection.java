package net.battlehub.debug;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
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
			for (int id : rem.getEntityIds()) {
				entities.remove(id);
			}
		} else if (msg instanceof ClientboundRespawnPacket) {
			entities.clear();
		}
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
