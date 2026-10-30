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
	/** Whether the client shows an entity glowing, from the last flags it got for it (absent: never told). */
	final java.util.Map<Integer, Boolean> glowing = new ConcurrentHashMap<>();
	/** The players' chat messages the client got, as the packets print themselves (their text is in there). */
	final java.util.List<String> chat = new java.util.concurrent.CopyOnWriteArrayList<>();

	TestConnection() {
		super(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel();
		channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
			@Override
			public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
				seen(msg);
				Wire.check(msg);
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
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
				|| msg instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket) {
			// signed chat, and unsigned chat (a command run for a player, a server without chat signing)
			chat.add(msg.toString());
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket held) {
			heldSlot = held.slot();
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundAnimatePacket anim) {
			int[] ia = idAndAction(anim);
			if (ia != null && ia[1] == 0) swings.merge(ia[0], 1, Integer::sum);
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket data) {
			int brightnessId = net.battlehub.mixin.DisplayAccessor.battlehub$brightness().id();
			for (net.minecraft.network.syncher.SynchedEntityData.DataValue<?> v : data.packedItems()) {
				if (v.id() == brightnessId && v.value() instanceof Integer b) brightness.put(data.id(), b);
				// an entity's shared flags (id 0): bit 0x40 is glowing
				if (v.id() == 0 && v.value() instanceof Byte f) glowing.put(data.id(), (f & 0x40) != 0);
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

	/**
	 * Every packet a test player gets is also written as the game writes it for a real client, with the play protocol's
	 * codec (1.51.1). A packet the mod builds that cannot be encoded (a wrong type in entity data, something a client
	 * cannot be sent) would make the server drop a real player with "Internal Exception"; here it fails the self-test
	 * instead, with what it was. The protocol classes are found by reflection (their shape changes between versions);
	 * without them the check says so and does not count.
	 */
	static final class Wire {
		private Wire() {}

		static final java.util.concurrent.atomic.AtomicLong ENCODED = new java.util.concurrent.atomic.AtomicLong();
		static final java.util.concurrent.atomic.AtomicLong FAILED = new java.util.concurrent.atomic.AtomicLong();
		private static final Set<String> SEEN_FAILURES = ConcurrentHashMap.newKeySet();
		private static volatile Object codec;
		private static volatile Method encode;
		private static volatile String unavailable;
		/** The chunks and their light: vanilla's own and big; writing them all would slow the test down for nothing. */
		private static final Set<String> SKIPPED = Set.of("ClientboundLevelChunkWithLightPacket", "ClientboundLightUpdatePacket",
				"ClientboundChunksBiomesPacket", "ClientboundForgetLevelChunkPacket");

		static boolean available() {
			return bind();
		}

		static String unavailable() {
			return unavailable;
		}

		static void check(Object msg) {
			if (msg instanceof ClientboundBundlePacket bundle) {
				for (Packet<?> p : bundle.subPackets()) check(p);
				return;
			}
			if (!(msg instanceof Packet<?>) || SKIPPED.contains(msg.getClass().getSimpleName()) || !bind()) return;
			io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
			try {
				encode.invoke(codec, buf, msg);
				ENCODED.incrementAndGet();
			} catch (ReflectiveOperationException | RuntimeException e) {
				Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
				FAILED.incrementAndGet();
				if (SEEN_FAILURES.add(msg.getClass().getName() + ": " + cause)) {
					StackTraceElement[] st = cause.getStackTrace();
					net.battlehub.BattleHub.LOGGER.error("SELFTEST packet {} cannot be encoded for a real client: {} at {}", msg.getClass().getSimpleName(), cause,
							java.util.Arrays.toString(java.util.Arrays.copyOf(st, Math.min(10, st.length))));
				}
			} finally {
				buf.release();
			}
		}

		/** The public methods of a class as declared by its public interfaces and superclasses (callable by reflection). */
		private static java.util.List<Method> publicMethods(Class<?> type) {
			java.util.List<Method> out = new java.util.ArrayList<>();
			java.util.Deque<Class<?>> todo = new java.util.ArrayDeque<>(java.util.List.of(type));
			Set<Class<?>> seen = new java.util.HashSet<>();
			while (!todo.isEmpty()) {
				Class<?> c = todo.pop();
				if (!seen.add(c)) continue;
				if (java.lang.reflect.Modifier.isPublic(c.getModifiers())) {
					for (Method m : c.getMethods()) out.add(m);
				}
				todo.addAll(java.util.List.of(c.getInterfaces()));
				if (c.getSuperclass() != null) todo.push(c.getSuperclass());
			}
			return out;
		}

		/** The play protocol's clientbound codec, bound to the server's registries, once. */
		private static synchronized boolean bind() {
			if (codec != null) return true;
			if (unavailable != null) return false;
			net.minecraft.server.MinecraftServer server = net.battlehub.BattleHub.server();
			if (server == null) return false;
			try {
				Class<?> protocols = Class.forName("net.minecraft.network.protocol.game.GameProtocols");
				Object template = protocols.getField("CLIENTBOUND_TEMPLATE").get(null);
				Method decorator = null;
				for (Method m : net.minecraft.network.RegistryFriendlyByteBuf.class.getMethods()) {
					if (m.getName().equals("decorator") && m.getParameterCount() == 1 && java.lang.reflect.Modifier.isStatic(m.getModifiers())) decorator = m;
				}
				if (decorator == null) throw new NoSuchMethodException("RegistryFriendlyByteBuf.decorator");
				Object decorate = decorator.invoke(null, server.registryAccess());
				// through the public interfaces: the objects themselves are of hidden (anonymous) classes
				Object info = null;
				for (Method m : publicMethods(template.getClass())) {
					if (m.getName().equals("bind") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(decorate)) {
						info = m.invoke(template, decorate);
					}
				}
				if (info == null) throw new NoSuchMethodException("CLIENTBOUND_TEMPLATE.bind(decorator)");
				Object c = ProtocolInfo.class.getMethod("codec").invoke(info);
				Method e = net.minecraft.network.codec.StreamEncoder.class.getMethod("encode", Object.class, Object.class);
				encode = e;
				codec = c;
				net.battlehub.BattleHub.LOGGER.info("SELFTEST packets to test players are encoded with {}", c.getClass().getName());
				return true;
			} catch (ReflectiveOperationException | RuntimeException e) {
				unavailable = e.toString();
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST the play protocol's codec was not found ({}): packets are not encoded", unavailable);
				return false;
			}
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
