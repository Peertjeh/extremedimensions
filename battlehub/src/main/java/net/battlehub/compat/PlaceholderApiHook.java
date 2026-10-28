package net.battlehub.compat;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import net.battlehub.BattleHub;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Offers {@link TabPlaceholders} to Text Placeholder API (the mod {@code placeholder-api}) as {@code %battlehub:<name>%},
 * which TAB, Styled Chat and others read on Fabric, and reads other mods' placeholders back. Only this class touches
 * placeholder-api, and nothing loads it until placeholder-api is known to be installed.
 */
public final class PlaceholderApiHook {
	private PlaceholderApiHook() {}

	/** Offers every placeholder (called by {@link TabPlaceholders#offer} once placeholder-api is known to be installed). */
	static void register() {
		for (String name : TabPlaceholders.PLAYER) {
			Placeholders.registerServer(BattleHub.id(name), (ctx, arg) -> {
				ServerPlayer p = ctx.serverPlayer();
				return p == null ? PlaceholderResult.invalid("No player!") : PlaceholderResult.value(TabPlaceholders.player(p.getUUID(), name));
			});
		}
		for (String name : TabPlaceholders.WORLD) {
			Placeholders.registerServer(BattleHub.id(name), (ctx, arg) -> PlaceholderResult.value(TabPlaceholders.world(name)));
		}
	}

	/**
	 * A placeholder of any mod read through placeholder-api for a player, as TAB would read it ({@code namespace:name},
	 * no argument); null when placeholder-api does not know it or says it is not valid for this player.
	 */
	public static Component read(ServerPlayer p, String namespace, String name) {
		PlaceholderResult r = Placeholders.parseServerPlaceholder(Identifier.fromNamespaceAndPath(namespace, name), "",
				ServerPlaceholderContext.of(p));
		return r.isValid() ? r.component() : null;
	}
}
