package net.battlehub.bot;

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

/**
 * The network connection of a bot: always "connected", and everything sent to it is dropped, except what a client
 * would act on by itself: a knockback. The server sends a hit player's knockback to their client and puts the
 * player's own motion back (the client moves the player); a bot has no client, so it takes that motion here.
 */
final class BotConnection extends Connection {
	private final BotPlayer bot;

	BotConnection(BotPlayer bot) {
		super(PacketFlow.SERVERBOUND);
		this.bot = bot;
		EmbeddedChannel channel = new EmbeddedChannel();
		channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
			@Override
			public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
				try {
					if (msg instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket motion) {
						knockback(motion);
					}
				} catch (RuntimeException ignored) {
					// only the knockback is lost
				}
				ReferenceCountUtil.release(msg);
				promise.setSuccess();
			}
		});
		((ConnectionAccessor) (Object) this).battlehub$setChannel(channel);
	}

	private static java.lang.reflect.Method idOf;
	private static java.lang.reflect.Method motionOf;
	private static java.lang.reflect.Method[] axes;
	private static boolean looked;

	/**
	 * The motion the server gave the bot's own player (a hit, a knockback): applied as a client applies it. The
	 * packet's accessors changed names over versions, so they are found once by what they return.
	 */
	private void knockback(net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket p) {
		if (!looked) {
			looked = true;
			java.util.List<java.lang.reflect.Method> doubles = new java.util.ArrayList<>();
			for (java.lang.reflect.Method m : p.getClass().getMethods()) {
				if (m.getParameterCount() != 0 || m.getDeclaringClass() == Object.class) continue;
				if (m.getReturnType() == int.class && (m.getName().equals("getId") || m.getName().equals("id"))) idOf = m;
				else if (m.getReturnType() == net.minecraft.world.phys.Vec3.class) motionOf = m;
				else if (m.getReturnType() == double.class && m.getName().matches("(get)?[XYZ]a|(get)?[xyz]a")) doubles.add(m);
			}
			doubles.sort(java.util.Comparator.comparing(m -> m.getName().toLowerCase(java.util.Locale.ROOT)));
			if (doubles.size() == 3) axes = doubles.toArray(new java.lang.reflect.Method[0]);
		}
		try {
			// motion of other entities the bot sees comes this way too: only its own counts
			if (idOf == null || (int) idOf.invoke(p) != bot.getId()) return;
			net.minecraft.world.phys.Vec3 v;
			if (motionOf != null) {
				v = (net.minecraft.world.phys.Vec3) motionOf.invoke(p);
			} else if (axes != null) {
				v = new net.minecraft.world.phys.Vec3((double) axes[0].invoke(p), (double) axes[1].invoke(p), (double) axes[2].invoke(p));
			} else {
				return;
			}
			bot.setDeltaMovement(v);
			bot.hurtMarked = false;
		} catch (ReflectiveOperationException e) {
			// only the knockback is lost
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
