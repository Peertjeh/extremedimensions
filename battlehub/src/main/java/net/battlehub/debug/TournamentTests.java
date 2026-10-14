package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.data.PlayerStore;
import net.battlehub.match.Match;
import net.battlehub.queue.Parties;
import net.battlehub.session.Sessions;
import net.battlehub.tournament.TeamDuelMatch;
import net.battlehub.tournament.Tournament;
import net.battlehub.tournament.Tournaments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Tournaments with four test players: solo (semi-finals played out, then a finalist leaves and the other goes
 * through) and teams of two (a final of three rounds: no friendly fire, a round ends only when a whole team is down).
 */
final class TournamentTests {
	private TournamentTests() {}

	private static final String[] NAMES = {"BHTestA", "BHTestB", "BHTestC", "BHTestD"};

	private static Tournament tournament() {
		return (Tournament) VARS.get("tournament");
	}

	private static ServerPlayer byId(UUID id) {
		return net.battlehub.BattleHub.server().getPlayerList().getPlayer(id);
	}

	/** Everyone of {@code side} still standing in this match is hit hard by someone of the other side. */
	private static void downSide(TeamDuelMatch m, int side) {
		ServerPlayer attacker = null;
		for (UUID id : m.members(1 - side)) {
			if (m.alive().contains(id)) attacker = byId(id);
		}
		for (UUID id : m.members(side)) {
			ServerPlayer v = byId(id);
			if (v != null && m.alive().contains(id)) {
				v.hurtServer(v.level(), v.damageSources().playerAttack(attacker), 1000f);
			}
		}
	}

	private static boolean allRunning(int count, int round) {
		List<TeamDuelMatch> ms = tournament().matches();
		if (ms.size() != count) return false;
		for (TeamDuelMatch m : ms) {
			if (m.phase() != Match.Phase.RUNNING || m.round() != round) return false;
		}
		return true;
	}

	private static void lobby(MinecraftServer server, Script s, String tag, String[] votes) {
		SelfTest.reset(s, tag);
		s.run(tag + ": four players vote and join", () -> {
			for (String n : NAMES) SelfTest.join(server, n);
			for (int i = 0; i < NAMES.length; i++) Parties.join(p(NAMES[i]), GameMode.TOURNAMENT, votes[i]);
		});
		s.waitFor(tag + ": the tournament starts", 200, () -> {
			Tournament t = Tournaments.of(p("BHTestA").getUUID());
			if (t == null) return false;
			VARS.put("tournament", t);
			return true;
		});
	}

	static void all(MinecraftServer server, Script s) {
		solo(server, s);
		teams(server, s);
	}

	private static void solo(MinecraftServer server, Script s) {
		String tag = "tournament solo";
		lobby(server, s, tag, new String[] {"teams:1;kit:sword", "teams:1;kit:sword", "teams:1", "kit:sword"});
		s.check(tag + ": four teams of one with the sword kit, everybody waiting", () -> {
			Tournament t = tournament();
			for (String n : NAMES) {
				if (Sessions.state(p(n).getUUID()) != Sessions.State.QUEUED) return false;
			}
			return t.teams().size() == 4 && t.teamSize == 1 && !t.namedTeams() && t.kit == GameMode.SWORD;
		});
		s.check(tag + ": no other queue while in a tournament", () -> !net.battlehub.queue.Queues.join(p("BHTestA"), GameMode.AXE, false)
				&& Tournaments.isIn(p("BHTestA").getUUID()));
		s.waitFor(tag + ": both semi-finals are played side by side", 600, () -> allRunning(2, 1));
		s.run(tag + ": side 0 takes round 1 in both", () -> {
			for (TeamDuelMatch m : tournament().matches()) downSide(m, 1);
		});
		s.waitFor(tag + ": round 2 in both", 200, () -> allRunning(2, 2));
		s.run(tag + ": and round 2", () -> {
			List<UUID> winners = new ArrayList<>();
			List<UUID> losers = new ArrayList<>();
			for (TeamDuelMatch m : tournament().matches()) {
				winners.addAll(m.members(0));
				losers.addAll(m.members(1));
				downSide(m, 1);
			}
			VARS.put("finalists", winners);
			VARS.put("losers", losers);
		});
		s.waitFor(tag + ": the losers are out and free, the winners wait for the final", 300, () -> {
			@SuppressWarnings("unchecked") List<UUID> losers = (List<UUID>) VARS.get("losers");
			@SuppressWarnings("unchecked") List<UUID> finalists = (List<UUID>) VARS.get("finalists");
			for (UUID id : losers) {
				if (Tournaments.isIn(id) || Sessions.state(id) != Sessions.State.LOBBY) return false;
			}
			for (UUID id : finalists) {
				if (!Tournaments.isIn(id) || Sessions.state(id) != Sessions.State.QUEUED) return false;
			}
			return tournament().matches().isEmpty() && tournament().bracket().teamsLeft().size() == 2;
		});
		s.run(tag + ": a finalist leaves with /hub before the final", () -> {
			@SuppressWarnings("unchecked") List<UUID> finalists = (List<UUID>) VARS.get("finalists");
			UUID champ = finalists.get(1);
			VARS.put("champion", champ);
			VARS.put("champWins", PlayerStore.get(champ).tournamentsWon);
			VARS.put("champCoins", PlayerStore.get(champ).coins);
			net.battlehub.command.HubCommands.hub(byId(finalists.get(0)));
		});
		s.waitFor(tag + ": the other finalist wins the tournament", 400, () -> {
			UUID champ = (UUID) VARS.get("champion");
			return tournament().isDone() && PlayerStore.get(champ).tournamentsWon == (int) VARS.get("champWins") + 1
					&& PlayerStore.get(champ).coins >= (long) VARS.get("champCoins") + 500 && !Tournaments.isIn(champ)
					&& Sessions.state(champ) == Sessions.State.LOBBY;
		});
	}

	private static void teams(MinecraftServer server, Script s) {
		String tag = "tournament teams";
		lobby(server, s, tag, new String[] {"teams:2;kit:axe", "teams:2;kit:axe", "teams:2;kit:bow", "teams:4;kit:axe"});
		s.check(tag + ": two teams of two (majority vote), axe kit", () -> tournament().teams().size() == 2
				&& tournament().teamSize == 2 && tournament().namedTeams() && tournament().kit == GameMode.AXE);
		s.waitFor(tag + ": the final is played", 600, () -> {
			if (!allRunning(1, 1)) return false;
			VARS.put("final", tournament().matches().get(0));
			return true;
		});
		s.check(tag + ": no friendly fire", () -> {
			TeamDuelMatch m = (TeamDuelMatch) VARS.get("final");
			ServerPlayer a = byId(m.members(0).get(0));
			ServerPlayer mate = byId(m.members(0).get(1));
			float before = mate.getHealth();
			mate.hurtServer(mate.level(), mate.damageSources().playerAttack(a), 5f);
			return mate.getHealth() == before;
		});
		s.run(tag + ": one of side 1 goes down", () -> {
			TeamDuelMatch m = (TeamDuelMatch) VARS.get("final");
			ServerPlayer v = byId(m.members(1).get(0));
			v.hurtServer(v.level(), v.damageSources().playerAttack(byId(m.members(0).get(0))), 1000f);
		});
		s.check(tag + ": the round goes on while a teammate stands", () -> {
			TeamDuelMatch m = (TeamDuelMatch) VARS.get("final");
			return m.round() == 1 && m.phase() == Match.Phase.RUNNING && byId(m.members(1).get(0)).gameMode() == GameType.SPECTATOR;
		});
		s.run(tag + ": the other one too", () -> downSide((TeamDuelMatch) VARS.get("final"), 1));
		s.check(tag + ": round to side 0, everybody back", () -> {
			TeamDuelMatch m = (TeamDuelMatch) VARS.get("final");
			return m.roundWins(0) == 1 && m.round() == 2 && m.alive().size() == 4;
		});
		s.waitFor(tag + ": round 2", 200, () -> allRunning(1, 2));
		s.run(tag + ": side 1 takes round 2", () -> downSide((TeamDuelMatch) VARS.get("final"), 0));
		s.waitFor(tag + ": round 3", 200, () -> allRunning(1, 3));
		s.run(tag + ": side 0 takes round 3", () -> {
			TeamDuelMatch m = (TeamDuelMatch) VARS.get("final");
			VARS.put("champions", m.members(0));
			List<Integer> wins = new ArrayList<>();
			for (UUID id : m.members(0)) wins.add(PlayerStore.get(id).tournamentsWon);
			VARS.put("champWinsList", wins);
			downSide(m, 1);
		});
		s.waitFor(tag + ": side 0 wins 2-1 and the tournament", 300, () -> {
			TeamDuelMatch m = (TeamDuelMatch) VARS.get("final");
			if (!tournament().isDone() || m.winnerSide() != 0 || m.roundWins(0) != 2 || m.roundWins(1) != 1) return false;
			@SuppressWarnings("unchecked") List<UUID> champs = (List<UUID>) VARS.get("champions");
			@SuppressWarnings("unchecked") List<Integer> before = (List<Integer>) VARS.get("champWinsList");
			for (int i = 0; i < champs.size(); i++) {
				if (PlayerStore.get(champs.get(i)).tournamentsWon != before.get(i) + 1) return false;
			}
			for (String n : NAMES) {
				if (Tournaments.isIn(p(n).getUUID())) return false;
			}
			return true;
		});
	}
}
