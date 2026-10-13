package net.battlehub.debug;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.battlehub.mixin.ConnectionAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;

/** A network connection for simulated test players: always "connected", every outgoing packet is dropped. */
final class TestConnection extends Connection {
	TestConnection() {
		super(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel();
		channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
			@Override
			public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
				ReferenceCountUtil.release(msg);
				promise.setSuccess();
			}
		});
		((ConnectionAccessor) (Object) this).battlehub$setChannel(channel);
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
