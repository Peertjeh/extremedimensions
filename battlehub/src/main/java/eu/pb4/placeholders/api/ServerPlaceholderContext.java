package eu.pb4.placeholders.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Stand-in, see package-info (the real one extends PlaceholderContext and has more). */
public interface ServerPlaceholderContext {
	MinecraftServer server();

	ServerPlayer serverPlayer();

	static ServerPlaceholderContext of(ServerPlayer player) {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}
}
