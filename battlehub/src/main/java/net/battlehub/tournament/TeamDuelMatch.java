package net.battlehub.tournament;

import java.util.List;
import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.match.SideMatch;
import net.battlehub.progression.TitleService;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** One tournament match: two teams with the voted duel kit, best of 3 rounds. */
public final class TeamDuelMatch extends SideMatch {
	private final Tournament tournament;
	private final Tournament.Team[] teams;

	TeamDuelMatch(int id, MinecraftServer server, Tournament tournament, GameMode kitMode, Tournament.Team a, List<ServerPlayer> pa,
			Tournament.Team b, List<ServerPlayer> pb) {
		super(id, GameMode.TOURNAMENT, kitMode, server, pa, pb);
		this.tournament = tournament;
		this.teams = new Tournament.Team[] {a, b};
	}

	public Tournament tournament() {
		return tournament;
	}

	public Tournament.Team team(int side) {
		return teams[side];
	}

	@Override
	protected int roundsToWin() {
		return 2;
	}

	@Override
	public Component sideName(ServerPlayer viewer, int side) {
		return teams[side].name(viewer);
	}

	@Override
	protected boolean offersPlayAgain() {
		return false;
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		int side = side(p.getUUID());
		if (tournament.namedTeams()) {
			TitleService.setOverride(p, Lang.tr(p, "tournament.prefix", teams[side].name(p)));
		}
		Msg.send(p, "tournament.match_start", tournament.roundName(p), teams[side].name(p), teams[1 - side].name(p));
	}

	@Override
	protected void onRestored(ServerPlayer p) {
		TitleService.clearOverride(p);
	}

	@Override
	protected void onLeft(UUID id) {
		// Giving up a tournament match means leaving the tournament
		Tournaments.leave(id);
		super.onLeft(id);
	}

	@Override
	public Component describe(ServerPlayer viewer) {
		return Lang.tr(viewer, "tournament.describe", teams[0].name(viewer), teams[1].name(viewer));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "tournament.status", tournament.roundName(viewer), round(), roundWins(0), roundWins(1));
	}
}
