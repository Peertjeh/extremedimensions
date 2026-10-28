package eu.pb4.placeholders.api;

import net.minecraft.resources.Identifier;

/** Stand-in, see package-info (only its handler is used). */
public record Placeholder<Ctx, T>(Identifier identifier) {
	@FunctionalInterface
	public interface Handler<Ctx, ArgType> {
		PlaceholderResult onPlaceholderRequest(Ctx context, ArgType argument);
	}
}
