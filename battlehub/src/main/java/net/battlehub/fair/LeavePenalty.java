package net.battlehub.fair;

import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.Penalties;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.match.Match;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;

/**
 * Leaving a match before it ends (disconnect or /hub) leaves the others without an opponent. Each time within a
 * day the wait before queueing again grows. Matches with bots do not count.
 */
public final class LeavePenalty {
	private LeavePenalty() {}

	private static HubConfig.Fairness cfg() {
		return ConfigManager.config().fairness;
	}

	/** A player left a match early. */
	public static void left(UUID id, Match m) {
		if (!cfg().leavePenalty || Bots.isBot(id)) {
			return;
		}
		int people = 0;
		for (UUID p : m.participants()) {
			if (Bots.isBot(p)) return;
			people++;
		}
		if (people < 2) {
			return;
		}
		PlayerProfile prof = PlayerStore.get(id);
		long now = System.currentTimeMillis();
		prof.leaves = Penalties.stillCounting(prof.leaves, prof.lastLeave, now) + 1;
		prof.lastLeave = now;
		int secs = Penalties.cooldownSeconds(prof.leaves, cfg().leaveSteps);
		prof.queueBlockedUntil = Math.max(prof.queueBlockedUntil, now + secs * 1000L);
		ServerPlayer p = m.player(id);
		if (p != null && secs > 0) {
			Msg.send(p, "fair.left", secs);
		}
	}

	/** Seconds this player still has to wait before queueing (0: free to play). */
	public static int secondsLeft(UUID id) {
		if (!cfg().leavePenalty) {
			return 0;
		}
		long left = PlayerStore.get(id).queueBlockedUntil - System.currentTimeMillis();
		return left <= 0 ? 0 : (int) ((left + 999) / 1000);
	}

	/** True (with a message) when the player may not queue yet. */
	public static boolean blocks(ServerPlayer p) {
		int secs = secondsLeft(p.getUUID());
		if (secs > 0) {
			Msg.send(p, "fair.blocked", secs);
			return true;
		}
		return false;
	}
}
