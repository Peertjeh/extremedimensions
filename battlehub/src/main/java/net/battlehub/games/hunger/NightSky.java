package net.battlehub.games.hunger;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import net.battlehub.BattleHub;
import net.battlehub.util.Ids;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Night on one player's screen (1.47): the time of day the client is told, while the world keeps its own. It is sent
 * after the server's own time (BattleHub ticks at the end of the server tick), every tick while it lasts, and at the
 * end the world's real time is sent back.
 *
 * <p>The packet is built by reflection, so a game version that builds it differently only loses the dark sky. Two
 * forms are known: the old one (game time, time of day, whether the client runs the clock) and the one with world
 * clocks (game time, and per clock its state: its ticks, how fast it runs). For the second the level's own clock is
 * set to midnight and held there. When neither fits, the constructors are written to the log once.
 */
public final class NightSky {
	private NightSky() {}

	static final long DAY = 24000L, MIDNIGHT = 18000L;

	private static Constructor<?> make;
	private static boolean looked, clocks;
	private static int sent;

	/** Night packets sent so far (self-test). */
	public static int sent() {
		return sent;
	}

	static void send(ServerPlayer p, boolean night) {
		look();
		// the old form: the server's own time comes back within a second
		if (make == null || p.hasDisconnected() || !night && !clocks) return;
		try {
			p.connection.send((Packet<?>) make.newInstance(args(p.level(), night)));
			if (night) sent++;
		} catch (ReflectiveOperationException | RuntimeException e) {
			BattleHub.LOGGER.warn("BattleHub: the night's sky could not be sent ({}); the sky stays as it is", e.toString());
			make = null;
		}
	}

	private static void look() {
		if (looked) return;
		looked = true;
		try {
			Class<?> c = Class.forName("net.minecraft.network.protocol.game.ClientboundSetTimePacket");
			for (Constructor<?> k : c.getConstructors()) {
				Class<?>[] ps = k.getParameterTypes();
				if (ps.length == 3 && ps[0] == long.class && ps[1] == long.class && ps[2] == boolean.class) make = k;
				if (make == null && ps.length == 2 && ps[0] == long.class && Map.class.isAssignableFrom(ps[1])) {
					make = k;
					clocks = true;
				}
			}
			if (make == null) {
				StringBuilder all = new StringBuilder();
				for (Constructor<?> k : c.getConstructors()) all.append(Arrays.toString(k.getGenericParameterTypes())).append(' ');
				BattleHub.LOGGER.warn("BattleHub: no time packet of a known form ({}); the night's sky stays as it is", all);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			BattleHub.LOGGER.warn("BattleHub: the time packet is not there ({}); the night's sky stays as it is", e.toString());
		}
	}

	private static Object[] args(ServerLevel level, boolean night) throws ReflectiveOperationException {
		if (!clocks) return new Object[] {level.getGameTime(), MIDNIGHT, false};
		long now = clockTime(level);
		long time = night ? now - Math.floorMod(now, DAY) + MIDNIGHT : now;
		Type value = ((ParameterizedType) make.getGenericParameterTypes()[1]).getActualTypeArguments()[1];
		return new Object[] {level.getGameTime(), Map.of(clock(level), state((Class<?>) raw(value), time, night))};
	}

	private static Type raw(Type t) {
		return t instanceof ParameterizedType pt ? pt.getRawType() : t;
	}

	/** The level's own clock time now (the time of day, counted from the world's first day). */
	private static long clockTime(ServerLevel level) {
		for (String name : new String[] {"getDefaultClockTime", "getOverworldClockTime", "getDayTime"}) {
			try {
				return (long) level.getClass().getMethod(name).invoke(level);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// the next name
			}
		}
		return 6000L;
	}

	/** The clock of the level's own kind (the overworld's for the lobby), as the packet names it. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object clock(ServerLevel level) throws ReflectiveOperationException {
		Object type = level.dimensionType();
		if (type.getClass().isRecord()) {
			for (RecordComponent rc : type.getClass().getRecordComponents()) {
				if (!rc.getName().equals("defaultClock")) continue;
				Object v = rc.getAccessor().invoke(type);
				if (v instanceof Optional<?> o) v = o.orElse(null);
				if (v != null) return v;
			}
		}
		ResourceKey key = (ResourceKey) Registries.class.getField("WORLD_CLOCK").get(null);
		Registry reg = (Registry) level.registryAccess().lookupOrThrow(key);
		Optional<?> holder = reg.get(ResourceKey.create(key, Ids.id("minecraft:overworld")));
		return holder.orElseThrow(() -> new NoSuchFieldException("the overworld clock"));
	}

	/**
	 * A clock's state, built from its parts by name: its ticks are {@code time}; at night it stands still, afterwards it
	 * runs at its usual speed again.
	 */
	private static Object state(Class<?> type, long time, boolean night) throws ReflectiveOperationException {
		if (!type.isRecord()) throw new NoSuchMethodException("a clock state that is not a record: " + type.getName());
		RecordComponent[] parts = type.getRecordComponents();
		Class<?>[] types = new Class<?>[parts.length];
		Object[] values = new Object[parts.length];
		for (int i = 0; i < parts.length; i++) {
			Class<?> t = parts[i].getType();
			String name = parts[i].getName().toLowerCase(java.util.Locale.ROOT);
			types[i] = t;
			boolean partial = name.contains("partial") || name.contains("fraction");
			if (t == long.class) values[i] = time;
			else if (t == int.class) values[i] = (int) time;
			else if (t == float.class) values[i] = partial ? 0f : night ? 0f : 1f;
			else if (t == double.class) values[i] = partial ? 0d : night ? 0d : 1d;
			else if (t == boolean.class) values[i] = (name.contains("paus") || name.contains("frozen") || name.contains("stop")) && night;
			else if (t == Optional.class) values[i] = Optional.empty();
			else throw new NoSuchMethodException("a clock state part " + parts[i] + " of " + type.getName());
		}
		Constructor<?> k = type.getDeclaredConstructor(types);
		k.setAccessible(true);
		return k.newInstance(values);
	}
}
