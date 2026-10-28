package net.battlehub.debug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import net.battlehub.compat.Discord;
import net.battlehub.config.ConfigManager;
import net.minecraft.server.MinecraftServer;

/**
 * Discord posts (1.51): a webhook on this machine gets a win streak as Discord wants it (the name as typed, no pings),
 * and a webhook that is no URL breaks nothing and is reported once.
 */
final class DiscordTests {
	private DiscordTests() {}

	private static final List<String> BODIES = new CopyOnWriteArrayList<>();
	private static final HttpServer[] HOOK = new HttpServer[1];

	static void all(MinecraftServer server, Script s) {
		String tag = "discord";
		s.run(tag + ": a webhook on this machine; a win streak is posted", () -> {
			try {
				HttpServer hook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
				hook.createContext("/hook", exchange -> {
					BODIES.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
					exchange.sendResponseHeaders(204, -1);
					exchange.close();
				});
				hook.start();
				HOOK[0] = hook;
				ConfigManager.config().integrations.discordWebhook = "http://127.0.0.1:" + hook.getAddress().getPort() + "/hook";
				Discord.post("discord.streak", "Bot_Test", 10);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		s.waitFor(tag + ": the webhook got the text, the name as typed and no pings", 200, () -> {
			for (String body : BODIES) {
				// in the JSON the name's backslash is doubled
				if (body.contains("Bot\\\\_Test") && body.contains("10") && body.contains("\"allowed_mentions\":{\"parse\":[]}")) {
					return !Discord.failing();
				}
			}
			return false;
		});
		s.run(tag + ": a webhook that is no URL; two posts", () -> {
			ConfigManager.config().integrations.discordWebhook = "not a url";
			Discord.post("discord.streak", "A", 10);
			Discord.post("discord.streak", "A", 11);
		});
		s.check(tag + ": nothing broke, it is marked as failing", Discord::failing);
		s.run(tag + ": off again; the webhook on this machine stops", () -> {
			ConfigManager.config().integrations.discordWebhook = "";
			if (HOOK[0] != null) HOOK[0].stop(0);
		});
	}
}
