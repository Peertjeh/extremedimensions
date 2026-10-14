package net.battlehub.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.DuelLayout;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * 1v1 with a fixed kit, as best of 1 or best of 3. Losing a round (dying, falling) gives the opponent a point;
 * between rounds both players go back to their spawn with a fresh kit. Leaving loses the whole series. A series
 * counts as one match for stats, coins and ELO.
 */
public final class DuelMatch extends Match {
	public final DuelFormat format;
	/** Started from a challenge (the "play again" offer is then a rematch). */
	public final boolean challenge;
	private final Map<UUID, Integer> roundWins = new HashMap<>();
	private int round = 1;

	public DuelMatch(int id, GameMode mode, boolean ranked, DuelFormat format, boolean challenge, MinecraftServer server,
			ServerPlayer a, ServerPlayer b) {
		super(id, mode, ranked, server, List.of(a, b));
		this.format = format;
		this.challenge = challenge;
	}

	@Override
	protected Blueprint design(Theme theme) {
		return DuelLayout.design(theme, random);
	}

	public int round() {
		return round;
	}

	public int roundWins(UUID id) {
		return roundWins.getOrDefault(id, 0);
	}

	private UUID other(UUID id) {
		return participants.get(0).equals(id) ? participants.get(1) : participants.get(0);
	}

	private Component score(ServerPlayer viewer) {
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		return Lang.tr(viewer, "match.round_score", name(a), roundWins(a), roundWins(b), name(b));
	}

	@Override
	protected int countdownSeconds() {
		return round == 1 ? super.countdownSeconds() : Math.max(1, ConfigManager.config().timings.roundCountdownSeconds);
	}

	@Override
	protected Component countdownSubtitle(ServerPlayer p) {
		if (format == DuelFormat.BO1) {
			return super.countdownSubtitle(p);
		}
		return Lang.tr(p, "match.round_sub", round, score(p));
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		roundWon(other(victim.getUUID()), victim.getUUID());
	}

	/** One round to {@code winner}. Either the series is decided (the loser is out, so the match ends) or the next round starts. */
	private void roundWon(UUID winner, UUID loser) {
		int wins = roundWins.merge(winner, 1, Integer::sum);
		if (wins >= format.roundsToWin()) {
			ServerPlayer lp = player(loser);
			if (lp != null) {
				eliminate(lp);
			} else {
				alive.remove(loser);
			}
			return;
		}
		forEveryone(p -> {
			Msg.send(p, "match.round_won", name(winner), round, score(p));
			Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, p.getUUID().equals(winner) ? 1.6f : 0.8f);
		});
		round++;
		restartRound();
	}

	/** Time is up for this round: more health wins it; equal health is a draw for the whole match. */
	@Override
	protected void onTimeUp() {
		ServerPlayer a = player(participants.get(0));
		ServerPlayer b = player(participants.get(1));
		if (a == null || b == null || a.getHealth() == b.getHealth()) {
			finish(List.of());
			return;
		}
		ServerPlayer winner = a.getHealth() > b.getHealth() ? a : b;
		roundWon(winner.getUUID(), other(winner.getUUID()));
		if (phase == Phase.RUNNING) {
			checkEnd();
		}
	}

	@Override
	public Component describe(ServerPlayer viewer) {
		return Lang.tr(viewer, "spectate.duel", name(participants.get(0)), name(participants.get(1)));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		Component modeName = Lang.tr(viewer, Icons.modeKey(mode));
		if (format == DuelFormat.BO1) {
			return Lang.tr(viewer, ranked ? "spectate.mode_ranked" : "spectate.mode", modeName);
		}
		return Lang.tr(viewer, ranked ? "spectate.mode_ranked_bo3" : "spectate.mode_bo3", modeName, round, score(viewer));
	}
}
