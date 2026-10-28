package eu.pb4.placeholders.api;

import net.minecraft.resources.Identifier;

/** Stand-in, see package-info. */
public final class Placeholders {
	private Placeholders() {}

	public static <T> void registerServer(Identifier identifier, Placeholder.Handler<ServerPlaceholderContext, String> handler) {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}

	public static PlaceholderResult parseServerPlaceholder(Identifier identifier, String argument, ServerPlaceholderContext context) {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}
}
