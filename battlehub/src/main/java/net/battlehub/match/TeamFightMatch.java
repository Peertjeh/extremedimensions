package net.battlehub.match;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 1v2 and 1v3: one player (side 0) against a team (side 1) with a sword, axe or mace kit. */
public final class TeamFightMatch extends SideMatch {
	public TeamFightMatch(int id, GameMode mode, GameMode kit, MinecraftServer server, ServerPlayer solo, List<ServerPlayer> team) {
		super(id, mode, kit, server, List.of(solo), team);
	}

	@Override
	protected int roundsToWin() {
		return (int) Math.max(1, game().setting("roundsToWin", 1));
	}

	private String teamNames() {
		List<String> n = new ArrayList<>();
		for (UUID id : members(1)) n.add(name(id));
		return String.join(", ", n);
	}

	@Override
	public Component sideName(ServerPlayer viewer, int side) {
		return side == 0 ? Lang.format("&c" + name(members(0).get(0))) : Lang.tr(viewer, "teamfight.team", teamNames());
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		Component kit = Lang.tr(p, Icons.modeKey(kitMode));
		if (side(p.getUUID()) == 0) {
			Msg.send(p, "teamfight.start_solo", members(1).size(), kit);
		} else {
			Msg.send(p, "teamfight.start_team", name(members(0).get(0)), kit);
		}
	}

	@Override
	public Component describe(ServerPlayer viewer) {
		return Lang.tr(viewer, "teamfight.describe", sideName(viewer, 0), sideName(viewer, 1), Lang.tr(viewer, Icons.modeKey(mode)));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "teamfight.status", Lang.tr(viewer, Icons.modeKey(kitMode)), roundWins(0), roundWins(1));
	}
}
