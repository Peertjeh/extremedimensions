package net.battlehub.compat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.lang.Lang;

/**
 * Big moments posted to a Discord channel through a webhook (1.51, {@code integrations.discordWebhook}; empty: off): the
 * weekly tournament's champion, a tournament won against people, the week's clan war and long win streaks. In the
 * server's language, sent off the server thread, and while it keeps failing the log gets one warning, not one per post.
 */
public final class Discord {
	private Discord() {}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	/** A post failed and nothing has worked since (one warning until it does). */
	private static volatile boolean failing;

	/** Posts the lang text {@code key} with these arguments (names are shown as typed, not as Discord markdown). */
	public static void post(String key, Object... args) {
		String url = ConfigManager.config().integrations.discordWebhook.trim();
		if (url.isEmpty()) return;
		Object[] shown = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			shown[i] = args[i] instanceof String s ? escape(s) : args[i];
		}
		send(url, payload(Lang.plain(key, shown)));
	}

	/** The webhook's JSON: the text (cut at Discord's limit), as BattleHub, and no pings (an @everyone stays text). */
	public static String payload(String text) {
		JsonObject o = new JsonObject();
		o.addProperty("content", text.length() > 1900 ? text.substring(0, 1900) + "…" : text);
		o.addProperty("username", "BattleHub");
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new JsonArray());
		o.add("allowed_mentions", mentions);
		return o.toString();
	}

	/** Discord markdown in a name shown as typed (an underscore would make Bot_Nova italic). */
	public static String escape(String text) {
		return text.replaceAll("([\\\\*_~`|>])", "\\\\$1");
	}

	public static boolean failing() {
		return failing;
	}

	private static void send(String url, String body) {
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		} catch (IllegalArgumentException e) {
			warn("not a webhook URL: " + e.getMessage());
			return;
		}
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
			if (error != null) {
				warn(error.toString());
			} else if (response.statusCode() >= 300) {
				warn("HTTP " + response.statusCode());
			} else {
				failing = false;
			}
		});
	}

	private static void warn(String why) {
		if (failing) return;
		failing = true;
		BattleHub.LOGGER.warn("Discord webhook failed ({}); further failures are not logged until a post works again", why);
	}
}
