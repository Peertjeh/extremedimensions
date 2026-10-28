package eu.pb4.placeholders.api;

import net.minecraft.network.chat.Component;

/** Stand-in, see package-info. */
public final class PlaceholderResult {
	private PlaceholderResult() {}

	public static PlaceholderResult invalid(String reason) {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}

	public static PlaceholderResult invalid() {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}

	public static PlaceholderResult value(Component component) {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}

	public static PlaceholderResult value(String component) {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}

	public Component component() {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}

	public boolean isValid() {
		throw new UnsupportedOperationException("placeholder-api is not installed");
	}
}
