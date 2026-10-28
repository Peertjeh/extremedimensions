package net.battlehub.compat;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.TitleDef;
import net.battlehub.core.TitleRules;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Titles earned in other mods (1.51), such as Extreme Dimensions: once a minute, for every person online, the
 * "placeholder" titles they don't have yet are checked against the placeholder they name, read through Text Placeholder
 * API. One that is met is earned for good (kept like a bought title, also when the value drops again) and announced.
 */
public final class ExternalTitles {
	private ExternalTitles() {}

	public static void tick(MinecraftServer server, int tick) {
		if (tick % 1200 != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			check(p);
		}
	}

	private static boolean on() {
		return ConfigManager.config().integrations.placeholderTitles && FabricLoader.getInstance().isModLoaded("placeholder-api");
	}

	/** Checks one player now and returns the titles they earned. */
	public static List<TitleDef> check(ServerPlayer p) {
		List<TitleDef> earned = new ArrayList<>();
		if (!on() || Bots.isBot(p)) return earned;
		PlayerProfile prof = PlayerStore.get(p);
		for (TitleDef t : ConfigManager.titles().titles) {
			if (!"placeholder".equalsIgnoreCase(t.requirement) || prof.ownedTitles.contains(t.id)) continue;
			Component value = read(p, t);
			if (value != null && TitleRules.meets(t, value.getString())) {
				prof.ownedTitles.add(t.id);
				prof.knownTitles.add(t.id);
				earned.add(t);
				Msg.send(p, "titles.unlocked", Lang.colored(t.display));
			}
		}
		if (!earned.isEmpty()) PlayerStore.save(p.getUUID());
		return earned;
	}

	/**
	 * Whether the titles menu shows a "placeholder" title: when it is earned, or when its placeholder can be read now
	 * (the mod it belongs to and placeholder-api are installed).
	 */
	public static boolean available(ServerPlayer p, PlayerProfile prof, TitleDef t) {
		return prof.ownedTitles.contains(t.id) || on() && read(p, t) != null;
	}

	private static Component read(ServerPlayer p, TitleDef t) {
		String ns = TitleRules.namespace(t);
		if (ns.isEmpty()) return null;
		return PlaceholderApiHook.read(p, ns, t.placeholder.substring(ns.length() + 1).trim());
	}
}
