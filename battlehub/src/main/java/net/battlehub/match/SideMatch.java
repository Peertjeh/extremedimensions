package net.battlehub.match;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.DuelLayout;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Two sides with a duel kit, in the kit's duel arena (bigger for more players), side 0 starting west and side 1
 * east. A side wins a round when nobody of the other side stands; everybody is back for the next round. No
 * friendly fire. Used by tournament matches (teams, best of 3) and 1v2 / 1v3.
 */
public abstract class SideMatch extends Match {
	protected final GameMode kitMode;
	private final List<List<UUID>> sides = List.of(new ArrayList<>(), new ArrayList<>());
	private final int[] roundWins = new int[2];
	private int round = 1;
	private int winnerSide = -1;

	protected SideMatch(int id, GameMode mode, GameMode kitMode, MinecraftServer server, List<ServerPlayer> a, List<ServerPlayer> b) {
		super(id, mode, false, server, both(a, b));
		this.kitMode = kitMode;
		for (ServerPlayer p : a) sides.get(0).add(p.getUUID());
		for (ServerPlayer p : b) sides.get(1).add(p.getUUID());
	}

	private static List<ServerPlayer> both(List<ServerPlayer> a, List<ServerPlayer> b) {
		List<ServerPlayer> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	/** Rounds a side needs to win the match. */
	protected abstract int roundsToWin();

	/** A side as shown in messages. */
	public abstract Component sideName(ServerPlayer viewer, int side);

	public int side(UUID id) {
		return sides.get(0).contains(id) ? 0 : 1;
	}

	/** 0 or 1 once the match is decided, -1 before (or when it was stopped). */
	public int winnerSide() {
		return winnerSide;
	}

	public int round() {
		return round;
	}

	public int roundWins(int side) {
		return roundWins[side];
	}

	/** The players of one side. */
	public List<UUID> members(int side) {
		return List.copyOf(sides.get(side));
	}

	public GameMode kitMode() {
		return kitMode;
	}

	@Override
	protected String kit() {
		return ConfigManager.config().game(kitMode).kit;
	}

	@Override
	protected String themeMode() {
		return kitMode.id();
	}

	protected int perSide() {
		return Math.max(1, Math.max(sides.get(0).size(), sides.get(1).size()));
	}

	/** The kit's duel arena, bigger for more players. */
	@Override
	protected Blueprint design(Theme theme) {
		int n = perSide();
		double teamScale = n <= 1 ? 1.0 : n == 2 ? 1.15 : n <= 4 ? 1.35 : 1.6;
		double scale = Math.min(2.4, ConfigManager.config().game(kitMode).setting("arenaScale", 1.0) * teamScale);
		return DuelLayout.design(theme, random, kitMode.isRanged(), scale, n);
	}

	/** A smaller side starts in the middle of its row. */
	@Override
	protected Arena.Point spawnPoint(UUID id, int index) {
		int side = side(id);
		int n = perSide();
		int offset = (n - sides.get(side).size()) / 2;
		return arena.spawn(side * n + Math.min(n - 1, offset + Math.max(0, sides.get(side).indexOf(id))));
	}

	@Override
	protected int countdownSeconds() {
		var t = ConfigManager.config().timings;
		return round == 1 ? t.duelCountdownSeconds : Math.max(1, t.roundCountdownSeconds);
	}

	protected Component score(ServerPlayer viewer) {
		return Lang.tr(viewer, "match.round_score", sideName(viewer, 0), roundWins[0], roundWins[1], sideName(viewer, 1));
	}

	@Override
	protected Component countdownSubtitle(ServerPlayer p) {
		if (roundsToWin() <= 1) {
			return super.countdownSubtitle(p);
		}
		return Lang.tr(p, "match.round_sub", round, score(p));
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return attacker == null || attacker == victim || side(attacker.getUUID()) != side(victim.getUUID());
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		eliminate(victim);
	}

	@Override
	protected void onGo() {
		// A side that never arrived loses at once
		checkEnd();
	}

	@Override
	protected void onLeft(UUID id) {
		checkEnd();
	}

	private int standing(int side) {
		int n = 0;
		for (UUID id : sides.get(side)) {
			if (alive.contains(id)) n++;
		}
		return n;
	}

	private boolean gone(int side) {
		return left.containsAll(sides.get(side));
	}

	private float health(int side) {
		float h = 0;
		for (UUID id : sides.get(side)) {
			ServerPlayer p = player(id);
			if (p != null && alive.contains(id)) h += p.getHealth();
		}
		return h;
	}

	@Override
	protected void checkEnd() {
		if (phase != Phase.RUNNING && phase != Phase.COUNTDOWN) {
			return;
		}
		for (int s = 0; s < 2; s++) {
			if (gone(s)) {
				winnerSide = 1 - s;
				finish(new ArrayList<>(sides.get(1 - s)));
				return;
			}
		}
		if (phase != Phase.RUNNING) {
			return;
		}
		int a = standing(0);
		int b = standing(1);
		if (a == 0 && b == 0) {
			roundWon(random.nextInt(2));
		} else if (a == 0) {
			roundWon(1);
		} else if (b == 0) {
			roundWon(0);
		}
	}

	/** Enough rounds win the match; otherwise everybody still in the match is back for the next round. */
	private void roundWon(int side) {
		roundWins[side]++;
		if (roundWins[side] >= Math.max(1, roundsToWin())) {
			winnerSide = side;
			finish(new ArrayList<>(sides.get(side)));
			return;
		}
		forEveryone(p -> {
			Msg.send(p, "match.side_round_won", sideName(p, side), round, score(p));
			Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, side(p.getUUID()) == side ? 1.6f : 0.8f);
		});
		round++;
		for (UUID id : participants) {
			if (!left.contains(id) && player(id) != null) {
				alive.add(id);
			}
		}
		restartRound();
	}

	/** Time is up for this round: more players standing win it, then more health, then a coin flip. */
	@Override
	protected void onTimeUp() {
		int a = standing(0);
		int b = standing(1);
		if (a != b) {
			roundWon(a > b ? 0 : 1);
			return;
		}
		float ha = health(0);
		float hb = health(1);
		roundWon(ha == hb ? random.nextInt(2) : (ha > hb ? 0 : 1));
	}
}
