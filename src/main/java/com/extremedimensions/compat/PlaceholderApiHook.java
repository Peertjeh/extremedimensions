package com.extremedimensions.compat;

import com.extremedimensions.ExtremeDimensions;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Offers {@link TabPlaceholders} to Text Placeholder API (the mod {@code placeholder-api}) as
 * {@code %extremedimensions:<name>%}, which TAB, Styled Chat and others read on Fabric. Only this class touches
 * placeholder-api, and nothing loads it until placeholder-api is known to be installed.
 */
public final class PlaceholderApiHook {
	private PlaceholderApiHook() {}

	/** Offers every placeholder (called by {@link TabPlaceholders#offer} once placeholder-api is known to be installed). */
	static void register() {
		for (String name : TabPlaceholders.PLAYER) {
			Placeholders.registerServer(Identifier.fromNamespaceAndPath(ExtremeDimensions.MOD_ID, name), (ctx, arg) -> {
				ServerPlayer p = ctx.serverPlayer();
				return p == null ? PlaceholderResult.invalid("No player!") : PlaceholderResult.value(TabPlaceholders.player(p.getUUID(), name));
			});
		}
		for (String name : TabPlaceholders.WORLD) {
			Placeholders.registerServer(Identifier.fromNamespaceAndPath(ExtremeDimensions.MOD_ID, name),
					(ctx, arg) -> PlaceholderResult.value(TabPlaceholders.world(name)));
		}
	}

	/**
	 * For the tests: a placeholder read back through placeholder-api for a player, as TAB would read it; null when it
	 * says the placeholder is not valid.
	 */
	public static net.minecraft.network.chat.Component parse(ServerPlayer p, String name) {
		PlaceholderResult r = Placeholders.parseServerPlaceholder(Identifier.fromNamespaceAndPath(ExtremeDimensions.MOD_ID, name), "",
				ServerPlaceholderContext.of(p));
		return r.isValid() ? r.component() : null;
	}
}
