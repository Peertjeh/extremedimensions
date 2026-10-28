package net.battlehub.compat;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.battlehub.BattleHub;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.Daily;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.Travel;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.FeaturedMode;
import net.battlehub.progression.Ranks;
import net.battlehub.progression.TitleService;
import net.battlehub.session.Sessions;
import net.battlehub.social.Clans;
import net.battlehub.tournament.WeeklyTournament;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What BattleHub tells a tablist or a chat format about each player and about the hub, as {@code %battlehub:<name>%}
 * placeholders (1.51; offered to Text Placeholder API by {@link PlaceholderApiHook}, which is how TAB and Styled Chat read
 * them on Fabric). The values are worked out on the server's thread now and then and kept, so a mod that asks from a
 * thread of its own (TAB does) reads a finished snapshot and never the live data. Texts are in the server's language
 * ({@code language}), as a tablist is the same for everyone.
 */
public final class TabPlaceholders {
	private TabPlaceholders() {}

	/** The placeholders about one player. */
	public static final List<String> PLAYER = List.of("level", "prestige", "stars", "coins", "elo", "tier", "wins", "kills",
			"streak", "clan", "title", "activity", "status");
	/** The placeholders about the hub. */
	public static final List<String> WORLD = List.of("players", "in_matches", "mode_of_the_day", "weekly");

	private static final Map<UUID, Map<String, Component>> PLAYERS = new ConcurrentHashMap<>();
	private static volatile Map<String, Component> world = Map.of();
	/** Whether placeholder-api took the placeholders (the self-test takes snapshots directly). */
	public static volatile boolean offered = false;

	/** At start-up: offers the placeholders to placeholder-api if it is installed (this class never touches it). */
	public static void offer() {
		if (offered || !ConfigManager.config().integrations.placeholders || !FabricLoader.getInstance().isModLoaded("placeholder-api")) {
			return;
		}
		try {
			PlaceholderApiHook.register();
			offered = true;
			BattleHub.LOGGER.info("Offered {} placeholders to placeholder-api as %{}:<name>%", PLAYER.size() + WORLD.size(), BattleHub.MOD_ID);
		} catch (LinkageError | RuntimeException e) {
			BattleHub.LOGGER.warn("This version of placeholder-api is not one BattleHub knows; its placeholders are not offered", e);
		}
	}

	/** A player's placeholder as last worked out (empty when switched off or not known yet). */
	public static Component player(UUID player, String name) {
		if (!ConfigManager.config().integrations.placeholders) {
			return Component.empty();
		}
		Map<String, Component> m = PLAYERS.get(player);
		Component c = m == null ? null : m.get(name);
		return c == null ? Component.empty() : c;
	}

	/** A placeholder about the hub as last worked out. */
	public static Component world(String name) {
		if (!ConfigManager.config().integrations.placeholders) {
			return Component.empty();
		}
		Component c = world.get(name);
		return c == null ? Component.empty() : c;
	}

	/** Every server tick: now and then the snapshot is made anew. */
	public static void tick(MinecraftServer server, int tick) {
		if (!offered || !ConfigManager.config().integrations.placeholders
				|| tick % Math.max(1, ConfigManager.config().integrations.placeholderRefreshTicks) != 0) {
			return;
		}
		refresh(server);
	}

	/** Makes the snapshot anew, and forgets players who have left. */
	public static void refresh(MinecraftServer server) {
		Map<UUID, Boolean> online = new LinkedHashMap<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PLAYERS.put(p.getUUID(), values(p));
			online.put(p.getUUID(), true);
		}
		PLAYERS.keySet().removeIf(id -> !online.containsKey(id));
		world = values(server);
	}

	/** The server stops: nothing is kept for the next world. */
	public static void clear() {
		PLAYERS.clear();
		world = Map.of();
		offered = false;
	}

	/** Every placeholder about one player, worked out now (on the server's thread). */
	public static Map<String, Component> values(ServerPlayer p) {
		PlayerProfile prof = PlayerStore.get(p);
		Map<String, Component> m = new LinkedHashMap<>();
		int elo = prof.bestElo(Ranks.startElo());
		m.put("level", text(Ranks.curve().progress(prof.xp).level()));
		m.put("prestige", text(prof.prestige));
		String stars = Daily.prestigeStars(prof.prestige);
		m.put("stars", stars.isEmpty() ? Component.empty() : Component.literal(stars).withStyle(s -> s.withColor(0xFFAA00)));
		m.put("coins", text(prof.coins));
		m.put("elo", text(elo));
		m.put("tier", Ranks.tierName(null, elo));
		m.put("wins", text(prof.overall.wins));
		m.put("kills", text(prof.overall.kills));
		m.put("streak", text(prof.overall.streak));
		Clans.Clan clan = Clans.of(p.getUUID());
		m.put("clan", clan == null ? Component.empty() : Component.literal(clan.tag));
		Component title = TitleService.display(prof);
		m.put("title", title == null ? Component.empty() : title);
		String activity = activity(p);
		m.put("activity", Component.literal(activity));
		m.put("status", Component.literal(status(p, activity)));
		return m;
	}

	/** Every placeholder about the hub, worked out now. */
	public static Map<String, Component> values(MinecraftServer server) {
		Map<String, Component> m = new LinkedHashMap<>();
		int here = 0, playing = 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Bots.isBot(p) || !Travel.inBattleHub(p)) continue;
			here++;
			if (Sessions.state(p.getUUID()) == Sessions.State.MATCH) playing++;
		}
		m.put("players", text(here));
		m.put("in_matches", text(playing));
		GameMode featured = FeaturedMode.today();
		m.put("mode_of_the_day", featured == null ? Component.empty() : Component.literal(modeName(featured)));
		m.put("weekly", Component.literal(weekly()));
		return m;
	}

	/**
	 * What a player is doing, as a fixed word for conditions (a TAB condition, a chat style): away (not in BattleHub),
	 * lobby, queue, match or spectating.
	 */
	static String activity(ServerPlayer p) {
		if (!Travel.inBattleHub(p)) return "away";
		return switch (Sessions.state(p.getUUID())) {
			case LOBBY -> "lobby";
			case QUEUED -> "queue";
			case MATCH -> "match";
			case SPECTATING -> "spectating";
		};
	}

	/** What a player is doing, in words ("In Bed Wars"); empty outside BattleHub. */
	static String status(ServerPlayer p, String activity) {
		return switch (activity) {
			case "lobby" -> Lang.plain("placeholder.status.lobby");
			case "queue" -> {
				// the queue's key ends in the mode: "sword", "ranked:bow", "party:sumo"
				String queue = Sessions.get(p.getUUID()).queue;
				GameMode mode = GameMode.byId(queue.substring(queue.lastIndexOf(':') + 1));
				yield mode == null ? Lang.plain("placeholder.status.lobby") : Lang.plain("placeholder.status.queue", modeName(mode));
			}
			case "match", "spectating" -> {
				Match match = Matches.get(Sessions.get(p.getUUID()).matchId);
				String mode = match == null ? "" : modeName(match.mode);
				yield Lang.plain(activity.equals("match") ? "placeholder.status.match" : "placeholder.status.spectating", mode);
			}
			default -> "";
		};
	}

	/** A mode's name without its colours, e.g. "Bed Wars". */
	public static String modeName(GameMode mode) {
		return Lang.tr("mode." + mode.id()).getString();
	}

	/** How long until the next weekly tournament ("2d 4h", "3h 20m", "15m"), or that sign-ups are open; empty when off. */
	static String weekly() {
		if (!ConfigManager.config().weekly.enabled) return "";
		if (WeeklyTournament.signupsOpen()) return Lang.plain("placeholder.weekly.open");
		ZonedDateTime next = WeeklyTournament.next();
		return duration(Duration.between(ZonedDateTime.now(next.getZone()), next).toMinutes());
	}

	/** Minutes as "2d 4h", "3h 20m" or "15m" (rounded down; never negative). */
	public static String duration(long minutes) {
		long m = Math.max(0, minutes);
		if (m >= 24 * 60) return Lang.plain("placeholder.time.days", m / (24 * 60), m / 60 % 24);
		if (m >= 60) return Lang.plain("placeholder.time.hours", m / 60, m % 60);
		return Lang.plain("placeholder.time.minutes", m);
	}

	private static Component text(long n) {
		return Component.literal(Long.toString(n));
	}
}
