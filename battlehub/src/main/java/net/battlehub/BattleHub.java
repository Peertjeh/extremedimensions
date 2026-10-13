package net.battlehub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.battlehub.config.ConfigManager;
import net.battlehub.lang.Lang;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/** BattleHub: a server-side PvP and minigame hub. Everything is reachable through one compass. */
public class BattleHub implements ModInitializer {
	public static final String MOD_ID = "battlehub";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinecraftServer server;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static MinecraftServer server() {
		return server;
	}

	static void setServer(MinecraftServer s) {
		server = s;
	}

	@Override
	public void onInitialize() {
		ConfigManager.load();
		Lang.load();
		HubEvents.register();
		LOGGER.info("BattleHub initialised");
	}
}
