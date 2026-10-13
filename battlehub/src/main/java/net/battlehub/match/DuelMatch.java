package net.battlehub.match;

import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.DuelLayout;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 1v1 with a fixed kit. Whoever dies (or falls, or leaves) loses. Ranked duels change ELO. */
public final class DuelMatch extends Match {
	public DuelMatch(int id, GameMode mode, boolean ranked, MinecraftServer server, ServerPlayer a, ServerPlayer b) {
		super(id, mode, ranked, server, List.of(a, b));
	}

	@Override
	protected Blueprint design(Theme theme) {
		return DuelLayout.design(theme, random);
	}

	@Override
	public Component describe(ServerPlayer viewer) {
		return Lang.tr(viewer, "spectate.duel", name(participants.get(0)), name(participants.get(1)));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, ranked ? "spectate.mode_ranked" : "spectate.mode", Lang.tr(viewer, Icons.modeKey(mode)));
	}
}
