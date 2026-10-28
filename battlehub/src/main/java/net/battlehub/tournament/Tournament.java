package net.battlehub.tournament;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.Bracket;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.Progression;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.Icons;
import net.battlehub.gui.Menus;
import net.battlehub.hub.HubWorld;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.Cosmetics;
import net.battlehub.session.Sessions;
import net.battlehub.spectate.Spectators;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * A single-elimination tournament: random teams of the voted size, a bracket (byes in round one when the number of
 * teams is not a power of two), every match best of 3 rounds with the voted duel kit. Matches of a round run side
 * by side; between rounds everyone waits in the hub (and may watch the other matches). Leaving the hub, joining
 * something else or giving up a match leaves the tournament; a team with nobody left loses its match.
 */
public final class Tournament {
	/** Session queue name while waiting between matches. */
	public static final String QUEUE = "tournament";
	private static final String[] COLORS = {"red", "blue", "green", "yellow", "aqua", "pink", "gold", "white",
			"dark_red", "dark_blue", "dark_green", "dark_aqua", "dark_purple", "gray", "dark_gray", "black"};
	private static final String[] CODES = {"&c", "&9", "&a", "&e", "&b", "&d", "&6", "&f", "&4", "&1", "&2", "&3", "&5", "&7", "&8", "&0"};

	/** One team: who started in it and who is still in. */
	public final class Team {
		public final int index;
		private final List<UUID> members;
		private final Set<UUID> present;
		private boolean out;

		Team(int index, List<UUID> members) {
			this.index = index;
			this.members = List.copyOf(members);
			this.present = new LinkedHashSet<>(members);
		}

		public List<UUID> members() {
			return members;
		}

		public Set<UUID> present() {
			return present;
		}

		public boolean out() {
			return out;
		}

		/** "Red team" in the viewer's language, or the player's name for teams of one. */
		public Component name(ServerPlayer viewer) {
			if (!namedTeams) {
				return Lang.format(CODES[index % CODES.length] + names.get(members.get(0)));
			}
			return Lang.tr(viewer, "tournament.team." + COLORS[index % COLORS.length]);
		}

		String memberNames() {
			List<String> n = new ArrayList<>();
			for (UUID id : members) n.add(names.get(id));
			return String.join(", ", n);
		}
	}

	/** A match of the current round. */
	private final class Slot {
		final int index;
		final Bracket.Pairing pairing;
		TeamDuelMatch match;
		boolean decided;
		boolean toldWaiting;

		Slot(int index, Bracket.Pairing pairing) {
			this.index = index;
			this.pairing = pairing;
		}
	}

	public final int id;
	public final GameMode kit;
	/** The weekly tournament (bigger prizes, kept in the history). */
	public boolean weekly;
	private final int startPlayers;
	public final int teamSize;
	private final MinecraftServer server;
	private final Random random;
	private final boolean namedTeams;
	private final Map<UUID, String> names = new HashMap<>();
	private final List<Team> teams = new ArrayList<>();
	private final Bracket bracket;
	private final List<Slot> slots = new ArrayList<>();
	private int breakTicks;
	private int ticks;
	private boolean done;
	private Team finalist;

	Tournament(int id, MinecraftServer server, GameMode kit, int requestedSize, List<ServerPlayer> players, Random random) {
		this.id = id;
		this.server = server;
		this.kit = kit;
		this.random = random;
		this.startPlayers = players.size();
		List<UUID> ids = new ArrayList<>();
		for (ServerPlayer p : players) {
			ids.add(p.getUUID());
			names.put(p.getUUID(), p.getGameProfile().name());
		}
		List<List<UUID>> split = Bracket.teams(ids, requestedSize, random);
		int biggest = 1;
		for (int i = 0; i < split.size(); i++) {
			teams.add(new Team(i, split.get(i)));
			biggest = Math.max(biggest, split.get(i).size());
		}
		this.teamSize = biggest;
		this.namedTeams = biggest > 1;
		this.bracket = new Bracket(teams.size());
	}

	private HubConfig.Game game() {
		return ConfigManager.config().game(GameMode.TOURNAMENT);
	}

	private int breakTicks() {
		return (int) Math.max(2, game().setting("breakSeconds", 10)) * 20;
	}

	public boolean namedTeams() {
		return namedTeams;
	}

	public List<Team> teams() {
		return teams;
	}

	public boolean isDone() {
		return done;
	}

	public Bracket bracket() {
		return bracket;
	}

	public Team teamOf(UUID id) {
		for (Team t : teams) {
			if (t.members.contains(id)) return t;
		}
		return null;
	}

	/** Matches of the current round that are being played. */
	public List<TeamDuelMatch> matches() {
		List<TeamDuelMatch> out = new ArrayList<>();
		for (Slot s : slots) {
			if (s.match != null && !s.decided) out.add(s.match);
		}
		return out;
	}

	/** "Final", "Semi-finals", ... for the current (or next) round. */
	public Component roundName(ServerPlayer viewer) {
		int size = bracket.roundSize() > 0 && !slots.isEmpty() ? bracket.roundSize() : Integer.highestOneBit(bracket.teamsLeft().size() * 2 - 1);
		String key = switch (size) {
			case 2 -> "final";
			case 4 -> "semi";
			case 8 -> "quarter";
			default -> "round16";
		};
		return Lang.tr(viewer, "tournament.round." + key);
	}

	private ServerPlayer player(UUID id) {
		return server.getPlayerList().getPlayer(id);
	}

	/** Everyone still in the tournament. */
	private void tell(java.util.function.Consumer<ServerPlayer> action) {
		for (Team t : teams) {
			for (UUID id : t.present) {
				ServerPlayer p = player(id);
				if (p != null) action.accept(p);
			}
		}
	}

	// ------------------------------------------------------------------ flow

	void begin() {
		for (Team t : teams) {
			for (UUID id : t.present) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				Sessions.toQueue(id, QUEUE);
				HubItems.ensure(p, true);
			}
		}
		tell(p -> {
			Msg.send(p, "tournament.started", teams.size(), Lang.tr(p, Icons.modeKey(kit)), teamSize);
			for (Team t : teams) {
				Msg.raw(p, Lang.tr(p, "tournament.team_line", t.name(p), t.memberNames()));
			}
			Msg.send(p, "tournament.first_round_in", breakTicks() / 20);
			Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
		});
		breakTicks = breakTicks();
	}

	void tick() {
		if (done) {
			return;
		}
		ticks++;
		if (breakTicks > 0) {
			breakTicks--;
			if (breakTicks == 0) {
				startRound();
			}
		} else if (!slots.isEmpty()) {
			boolean all = true;
			for (Slot s : slots) {
				if (s.decided) continue;
				if (s.match == null) {
					tryStart(s);
				} else if (s.match.phase() == Match.Phase.CLOSED) {
					resolve(s);
				}
				all &= s.decided;
			}
			if (all) {
				roundOver();
			}
		}
		if (!done && ticks % 20 == 0) {
			upkeep();
		}
	}

	private void startRound() {
		List<Bracket.Pairing> pairings = bracket.nextRound();
		slots.clear();
		for (int i = 0; i < pairings.size(); i++) {
			slots.add(new Slot(i, pairings.get(i)));
		}
		tell(p -> {
			Msg.send(p, "tournament.round_start", roundName(p));
			for (Bracket.Pairing pr : pairings) {
				Msg.raw(p, Lang.tr(p, "tournament.pairing_line", teams.get(pr.a()).name(p), teams.get(pr.b()).name(p)));
			}
			for (int b : bracket.byes()) {
				Msg.raw(p, Lang.tr(p, "tournament.bye_line", teams.get(b).name(p)));
			}
		});
		Menus.refreshAll(server);
	}

	private List<ServerPlayer> online(Team t) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : t.present) {
			ServerPlayer p = player(id);
			if (p != null && HubWorld.isBattleHub(p.level())) out.add(p);
		}
		return out;
	}

	private void tryStart(Slot s) {
		Team a = teams.get(s.pairing.a());
		Team b = teams.get(s.pairing.b());
		List<ServerPlayer> pa = online(a);
		List<ServerPlayer> pb = online(b);
		if (pa.isEmpty() || pb.isEmpty()) {
			Team w = pa.isEmpty() && pb.isEmpty() ? (random.nextBoolean() ? a : b) : (pa.isEmpty() ? b : a);
			decide(s, w, null);
			return;
		}
		if (s.toldWaiting && ticks % 20 != 0) {
			return;
		}
		for (ServerPlayer p : both(pa, pb)) {
			if (Sessions.state(p.getUUID()) == Sessions.State.SPECTATING) {
				// Watching another match: your own starts now
				Spectators.leave(p, true);
			}
		}
		TeamDuelMatch m = new TeamDuelMatch(Matches.newId(), server, this, kit, a, pa, b, pb);
		if (Matches.start(m)) {
			s.match = m;
		} else if (!s.toldWaiting) {
			s.toldWaiting = true;
			for (ServerPlayer p : both(pa, pb)) Msg.send(p, "queue.arenas_full");
		}
	}

	private static List<ServerPlayer> both(List<ServerPlayer> a, List<ServerPlayer> b) {
		List<ServerPlayer> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	private void resolve(Slot s) {
		TeamDuelMatch m = s.match;
		Team a = teams.get(s.pairing.a());
		Team b = teams.get(s.pairing.b());
		Team w;
		if (m.winnerSide() >= 0) {
			w = m.winnerSide() == 0 ? a : b;
		} else {
			// Stopped without a result: the team with more players still in goes through
			int ca = a.present.size();
			int cb = b.present.size();
			w = ca == cb ? (random.nextBoolean() ? a : b) : (ca > cb ? a : b);
		}
		decide(s, w, m);
	}

	private void decide(Slot s, Team winner, TeamDuelMatch m) {
		s.decided = true;
		bracket.result(s.index, winner.index);
		Team loser = teams.get(s.pairing.a() == winner.index ? s.pairing.b() : s.pairing.a());
		if (bracket.roundSize() == 2) {
			finalist = loser;
		}
		tell(p -> {
			if (m == null || m.winnerSide() < 0) {
				Msg.send(p, "tournament.walkover", winner.name(p), loser.name(p));
			} else {
				int ws = m.winnerSide();
				Msg.send(p, "tournament.result", winner.name(p), loser.name(p), m.roundWins(ws), m.roundWins(1 - ws));
			}
		});
		loser.out = true;
		for (UUID id : new ArrayList<>(loser.present)) {
			Tournaments.forget(id);
			ServerPlayer p = player(id);
			if (p == null) continue;
			if (Sessions.state(id) == Sessions.State.QUEUED) {
				Sessions.toLobby(id);
				HubItems.ensure(p, false);
			}
			if (bracket.roundSize() != 2) {
				Msg.send(p, "tournament.out", roundName(p));
			}
		}
		if (bracket.roundSize() != 2) {
			loser.present.clear();
		}
		Menus.refreshAll(server);
	}

	private void roundOver() {
		bracket.advance();
		slots.clear();
		if (bracket.finished()) {
			finish();
			return;
		}
		breakTicks = breakTicks();
		tell(p -> Msg.send(p, "tournament.next_round", roundName(p), breakTicks / 20));
	}

	/** Players at the start. */
	public int size() {
		return startPlayers;
	}

	private void finish() {
		done = true;
		Team champion = teams.get(bracket.champion());
		long winnerCoins = Math.round(game().setting("winnerCoins", 500));
		long finalistCoins = Math.round(game().setting("finalistCoins", 150));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (HubWorld.isBattleHub(p.level())) {
				Msg.send(p, "tournament.won", champion.name(p), champion.memberNames(), Lang.tr(p, Icons.modeKey(kit)));
			}
		}
		// prizes only for beating other people: a player alone with bots wins nothing
		java.util.List<UUID> everyone = new ArrayList<>();
		for (Team t : teams) everyone.addAll(t.members());
		boolean prizes = net.battlehub.core.BotRules.paysPrizes(net.battlehub.core.BotRules.count(everyone, net.battlehub.bot.Bots::isBot));
		if (!prizes) {
			winnerCoins = 0;
			finalistCoins = 0;
		}
		for (UUID id : champion.present) {
			PlayerProfile prof = PlayerStore.get(id);
			if (prizes) {
				prof.tournamentsWon++;
				Progression.addCoins(prof, winnerCoins);
			}
			PlayerStore.save(id);
			ServerPlayer p = player(id);
			if (p != null) {
				if (!prizes) Msg.send(p, "tournament.no_prize_bots");
				Msg.title(p, Lang.tr(p, "tournament.champion_title"), Lang.tr(p, "tournament.champion_sub", winnerCoins), 10, 70, 20);
				Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
				Cosmetics.winEffect("fireworks", p.level(), p.position(), 0);
			}
		}
		if (prizes && !weekly) {
			// the weekly one is posted with its own words by WeeklyTournament
			net.battlehub.compat.Discord.post("discord.tournament_won", champion.memberNames(), Lang.plain(Icons.modeKey(kit)));
		}
		if (weekly) {
			WeeklyTournament.finished(this, new ArrayList<>(champion.present),
					finalist == null ? List.of() : new ArrayList<>(finalist.present), champion.memberNames(), prizes);
		}
		if (finalist != null) {
			for (UUID id : finalist.present) {
				if (prizes) Progression.addCoins(PlayerStore.get(id), finalistCoins);
				PlayerStore.save(id);
				ServerPlayer p = player(id);
				if (p != null && prizes) Msg.send(p, "tournament.finalist", finalistCoins);
			}
			finalist.present.clear();
		}
		for (Team t : teams) {
			for (UUID id : t.present) {
				Tournaments.forget(id);
				ServerPlayer p = player(id);
				if (p != null && Sessions.state(id) == Sessions.State.QUEUED) {
					Sessions.toLobby(id);
					HubItems.ensure(p, false);
				}
			}
		}
		Menus.refreshAll(server);
	}

	/** Once a second: players back from a match wait in the tournament again; the action bar shows the state. */
	private void upkeep() {
		int playing = matches().size();
		for (Team t : teams) {
			if (t.out) continue;
			for (UUID id : t.present) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				Sessions.State st = Sessions.state(id);
				if (st == Sessions.State.LOBBY) {
					Sessions.toQueue(id, QUEUE);
					HubItems.ensure(p, true);
					st = Sessions.State.QUEUED;
				}
				if (st != Sessions.State.QUEUED) continue;
				if (breakTicks > 0) {
					Msg.actionBar(p, "tournament.actionbar_break", roundName(p), (breakTicks + 19) / 20);
				} else {
					Msg.actionBar(p, "tournament.actionbar_waiting", playing);
				}
			}
		}
	}

	/** The player left (gave up a match, /hub, disconnected, went back to survival). */
	void leave(UUID id) {
		Team t = teamOf(id);
		if (t == null || !t.present.remove(id)) {
			return;
		}
		String name = names.get(id);
		tell(p -> Msg.send(p, "tournament.left", name));
		ServerPlayer p = player(id);
		if (p != null) {
			Msg.send(p, "tournament.you_left");
		}
		// A team with nobody left loses its next match (or the one being played, which ends by itself)
	}
}
