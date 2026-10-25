package net.battlehub.games.hunger;

import java.lang.reflect.Constructor;

import net.battlehub.BattleHub;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

/**
 * Night on one player's screen (1.47): the time of day the client is told, while the world keeps its own. The server
 * tells every client the time once a second; this is sent after that (BattleHub ticks at the end of the server tick),
 * every tick while it lasts; afterwards the server's own next time packet brings the day back. The packet's constructor is found by its parameters (game time, time of day, whether the
 * client runs the clock), so a game version that builds it differently only loses the dark sky.
 */
final class NightSky {
	private NightSky() {}

	/** Midnight. */
	static final long MIDNIGHT = 18000L;

	private static Constructor<?> make;
	private static boolean looked;

	static void send(ServerPlayer p, boolean night) {
		if (!looked) {
			looked = true;
			try {
				Class<?> c = Class.forName("net.minecraft.network.protocol.game.ClientboundSetTimePacket");
				for (Constructor<?> k : c.getConstructors()) {
					Class<?>[] ps = k.getParameterTypes();
					if (ps.length == 3 && ps[0] == long.class && ps[1] == long.class && ps[2] == boolean.class) make = k;
				}
				if (make == null) BattleHub.LOGGER.warn("BattleHub: no time packet of the expected form; the night's sky stays as it is");
			} catch (ReflectiveOperationException | RuntimeException e) {
				BattleHub.LOGGER.warn("BattleHub: the time packet is not there ({}); the night's sky stays as it is", e.toString());
			}
		}
		// the night ends by itself: the server's own time comes within a second
		if (!night || make == null || p.hasDisconnected()) return;
		try {
			p.connection.send((Packet<?>) make.newInstance(p.level().getGameTime(), MIDNIGHT, false));
		} catch (ReflectiveOperationException | RuntimeException e) {
			make = null;
		}
	}
}
