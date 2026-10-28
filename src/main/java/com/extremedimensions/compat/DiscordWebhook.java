package com.extremedimensions.compat;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;
import com.extremedimensions.tapestry.Tapestry;
import com.extremedimensions.trial.Trial;
import com.extremedimensions.trial.TrialRun;
import com.extremedimensions.trial.TrialService;

import net.minecraft.server.level.ServerPlayer;

/**
 * Posts the world's great moments to a Discord channel through a webhook ({@code discord_webhook} in the config file):
 * an Elder or the Starved One defeated and who was there, the world's ending, a panel woven into the tapestry and the
 * tapestry finished, and a gold medal in this week's Rift Trial. Nothing smaller, so the channel only hears what
 * matters. The messages are in English with the game's own names (a dedicated server has no language files of the
 * mod's) and never ping anyone. They go out on a thread of their own: nothing here waits for Discord or throws on the
 * server's thread, and a failure is logged once, not for every message.
 */
public final class DiscordWebhook {
	private DiscordWebhook() {}

	/** Discord's limit on a message's length. */
	public static final int LIMIT = 2000;
	/** Names one message lists at most; the rest are counted. */
	static final int NAMES = 15;
	/** Messages posted in one minute at most (Discord allows a webhook 30): more are a fault somewhere, and dropped. */
	static final int PER_MINUTE = 10;

	/** For the tests: when set, a message's JSON goes here instead of to Discord, and nothing goes over the network. */
	public static volatile Consumer<String> testSink = null;

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static HttpClient client;
	private static ExecutorService sender;
	/** A post failed and was logged: the next failures are not, until a post works again. */
	private static volatile boolean warned = false;
	private static long minute = -1;
	private static int inMinute = 0;

	// ------------------------------------------------------------------ the moments (on the server's thread)

	/** An Elder has fallen, with these players near. */
	public static void elder(StatsExport.Elder elder, Collection<ServerPlayer> there) {
		moment(() -> elderText(elder.title, who(there)));
	}

	/** The Starved One has fallen at the Heart, with these players in its arena. */
	public static void starvedOne(Collection<ServerPlayer> there) {
		moment(() -> starvedText(who(there)));
	}

	/** The world has its ending, chosen by this player with the Mending Thread (mended) or the Severing Shears. */
	public static void ending(boolean mended, ServerPlayer by) {
		moment(() -> endingText(mended, by.getScoreboardName()));
	}

	/** A panel was woven into the tapestry (already counted among the woven). */
	public static void panel(Tapestry.Panel panel, ServerPlayer by) {
		moment(() -> panelText(panelName(panel), by.getScoreboardName(), Tapestry.count(), Tapestry.Panel.values().length));
	}

	/** The Weaving held, with these players at the loom: the tapestry is finished. */
	public static void tapestry(Collection<ServerPlayer> there) {
		moment(() -> tapestryText(who(there)));
	}

	/** A player came home from this week's Rift Trial with gold for the first time this week. */
	public static void gold(ServerPlayer p, TrialRun run) {
		moment(() -> {
			Trial.Kind kind = Trial.Kind.byId(run.kind);
			Trial.Twist twist = Trial.Twist.byId(run.twist);
			String trial = kind == null ? "" : TabPlaceholders.trial(kind, twist != null ? twist : TrialService.twist(run.week));
			return goldText(p.getScoreboardName(), trial, run.score);
		});
	}

	/** Builds a moment's message and posts it, only when there is a webhook, and never lets a fault reach the game. */
	private static void moment(Supplier<String> text) {
		if (!on()) {
			return;
		}
		try {
			post(text.get());
		} catch (RuntimeException e) {
			warn("Could not post to the Discord webhook", e);
		}
	}

	private static List<String> who(Collection<ServerPlayer> there) {
		List<String> names = new ArrayList<>();
		for (ServerPlayer p : there) {
			names.add(p.getScoreboardName());
		}
		return names;
	}

	// ------------------------------------------------------------------ the words

	public static String elderText(String elder, List<String> names) {
		return "**" + elder + "** has been defeated" + (names.isEmpty() ? "" : " by " + list(names)) + ".";
	}

	public static String starvedText(List<String> names) {
		return "**The Starved One** has been defeated at the Heart of the Seam" + (names.isEmpty() ? "" : " by " + list(names))
				+ ". The Mending Thread and the Severing Shears lie at the altar of the Heart. Only one of the two can ever be used,"
				+ " and it decides for the whole world.";
	}

	public static String endingText(boolean mended, String by) {
		if (mended) {
			return "**The world is Mended.** " + (by.isBlank() ? "The first wound is mended." : escape(by) + " mended the first wound with the Mending Thread.");
		}
		return "**The world is Severed.** " + (by.isBlank() ? "The Tether is severed from the world."
				: escape(by) + " severed the Tether from the world with the Severing Shears.");
	}

	public static String panelText(String panel, String by, int woven, int total) {
		return (by.isBlank() ? "The **" + panel + "** panel is woven into the tapestry" : escape(by) + " wove the **" + panel + "** panel into the tapestry")
				+ " (" + woven + "/" + total + ")."
				+ (woven >= total ? " The last panel is set. The wounds converge on the loom: hold it until the weave is done!" : "");
	}

	public static String tapestryText(List<String> names) {
		return "**The weave holds.** The tapestry is finished" + (names.isEmpty() ? "" : ", held by " + list(names)) + ", and the story is yours.";
	}

	public static String goldText(String name, String trial, int score) {
		return escape(name) + " won **Gold** in this week's Rift Trial" + (trial.isBlank() ? "" : " (" + trial + ")") + " with a score of " + score + ".";
	}

	/** What {@code /tether discord_test} posts. */
	public static String testText() {
		return "Extreme Dimensions is connected: the world's great moments will be posted here.";
	}

	/** A panel's name, as the tapestry shows it. */
	public static String panelName(Tapestry.Panel panel) {
		return switch (panel) {
			case ROOTS -> "Roots";
			case TOLL -> "Toll";
			case UNRAVELLING -> "Unravelling";
			case CHAIN -> "Chain";
			case KNOT -> "Knot";
			case HEART -> "Heart";
		};
	}

	/** Names as words ("A", "A and B", "A, B and C"), each once; past {@value #NAMES} the rest are counted. */
	public static String list(List<String> names) {
		List<String> all = new ArrayList<>();
		for (String n : new LinkedHashSet<>(names)) {
			if (n != null && !n.isBlank()) {
				all.add(escape(n));
			}
		}
		List<String> parts = new ArrayList<>(all.subList(0, Math.min(all.size(), NAMES)));
		int rest = all.size() - parts.size();
		if (rest > 0) {
			parts.add(rest == 1 ? "1 other" : rest + " others");
		}
		if (parts.size() <= 1) {
			return parts.isEmpty() ? "" : parts.get(0);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	/** A name as plain text: Discord's markdown characters in it (an underscore, say) are not read as markdown. */
	public static String escape(String name) {
		StringBuilder b = new StringBuilder(name.length() + 4);
		for (char c : name.toCharArray()) {
			if ("\\*_~`|>[]()#".indexOf(c) >= 0) {
				b.append('\\');
			}
			b.append(c);
		}
		return b.toString();
	}

	/** The JSON Discord is sent: the message, cut to its limit, and no mentions at all (so no @everyone or @here). */
	public static String body(String content) {
		JsonObject o = new JsonObject();
		o.addProperty("content", clip(content));
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new JsonArray());
		o.add("allowed_mentions", mentions);
		return GSON.toJson(o);
	}

	/** A message cut to Discord's limit (never in the middle of a character). */
	public static String clip(String content) {
		if (content.length() <= LIMIT) {
			return content;
		}
		int cut = LIMIT - 1;
		if (Character.isHighSurrogate(content.charAt(cut - 1))) {
			cut--;
		}
		return content.substring(0, cut) + "…";
	}

	// ------------------------------------------------------------------ sending

	/** Whether there is a webhook to post to. */
	public static boolean on() {
		return !Config.DISCORD_WEBHOOK.isBlank();
	}

	/** The webhook's address, or null when it is not an http(s) web address with a host. */
	public static URI uri(String url) {
		try {
			URI uri = new URI(url.trim());
			String scheme = uri.getScheme();
			return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && uri.getHost() != null ? uri : null;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	/**
	 * Posts a message to the webhook from a thread of its own: Discord's answer (its status code, -1 when it could not be
	 * reached), or null when nothing was sent (no webhook, not a web address, too many this minute).
	 */
	public static CompletableFuture<Integer> post(String content) {
		String url = Config.DISCORD_WEBHOOK.trim();
		if (url.isEmpty() || content == null || content.isBlank()) {
			return null;
		}
		URI uri = uri(url);
		if (uri == null) {
			warn("discord_webhook is not a web address (it looks like https://discord.com/api/webhooks/...); nothing is posted", null);
			return null;
		}
		String body = body(content);
		Consumer<String> sink = testSink;
		if (sink != null) {
			sink.accept(body);
			return CompletableFuture.completedFuture(204);
		}
		if (!allowed(System.currentTimeMillis())) {
			warn("More than " + PER_MINUTE + " Discord messages in a minute; the rest of this minute's are dropped", null);
			return null;
		}
		return CompletableFuture.supplyAsync(() -> HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build(), sender())
				.thenCompose(request -> client().sendAsync(request, HttpResponse.BodyHandlers.discarding()))
				.handle((response, error) -> {
					int code = response == null ? -1 : response.statusCode();
					if (error != null) {
						warn("Could not reach the Discord webhook", error);
					} else if (code / 100 != 2) {
						warn("The Discord webhook answered " + code + " (a webhook deleted, or its URL mistyped?)", null);
					} else {
						warned = false;
					}
					return code;
				});
	}

	private static synchronized boolean allowed(long now) {
		long m = now / 60_000;
		if (m != minute) {
			minute = m;
			inMinute = 0;
		}
		return ++inMinute <= PER_MINUTE;
	}

	private static synchronized ExecutorService sender() {
		if (sender == null) {
			sender = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Extreme Dimensions Discord webhook");
				t.setDaemon(true);
				return t;
			});
		}
		return sender;
	}

	private static synchronized HttpClient client() {
		if (client == null) {
			client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		}
		return client;
	}

	private static void warn(String what, Throwable e) {
		if (warned) {
			return;
		}
		warned = true;
		if (e == null) {
			ExtremeDimensions.LOGGER.warn("{} (said once, until a post works again)", what);
		} else {
			Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
			ExtremeDimensions.LOGGER.warn("{} (said once, until a post works again): {}", what, cause.toString());
		}
	}
}
