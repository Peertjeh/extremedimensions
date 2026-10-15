package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Daily and weekly quests: every player gets their own few each day and week (picked from the pool with a seed
 * from their id and the date, so they stay the same all day), and matches move them forward.
 */
public final class Quests {
	private Quests() {}

	/** What happened in one match for one player. */
	public record Match(GameMode mode, boolean won, boolean ranked, int kills, double hearts, boolean featured, boolean mvp) {}

	/** The quests for this player on this day (or in this week: pass the week number). */
	public static List<QuestDef> pick(List<QuestDef> pool, boolean weekly, UUID player, long period, int count) {
		List<QuestDef> candidates = new ArrayList<>();
		for (QuestDef q : pool) {
			if (q != null && q.weekly() == weekly) candidates.add(q);
		}
		long seed = player.getMostSignificantBits() ^ player.getLeastSignificantBits() ^ (period * 0x9E3779B97F4A7C15L) ^ (weekly ? 7 : 3);
		java.util.Collections.shuffle(candidates, new Random(seed));
		return new ArrayList<>(candidates.subList(0, Math.min(Math.max(0, count), candidates.size())));
	}

	/** How much a match moves this quest forward. */
	public static int amount(QuestDef q, Match m) {
		if (!q.mode.isEmpty()) {
			GameMode qm = GameMode.byId(q.mode);
			if (qm == null || qm != m.mode()) return 0;
		}
		return switch (q.goal.toLowerCase(java.util.Locale.ROOT)) {
			case "play" -> 1;
			case "win" -> m.won() ? 1 : 0;
			case "kill" -> Math.max(0, m.kills());
			case "damage" -> (int) Math.floor(Math.max(0, m.hearts()));
			case "ranked_win" -> m.ranked() && m.won() ? 1 : 0;
			case "featured" -> m.featured() ? 1 : 0;
			case "mvp" -> m.mvp() ? 1 : 0;
			default -> 0;
		};
	}

	/** Moves the progress forward; true when the quest was just completed. */
	public static boolean advance(QuestProgress p, QuestDef q, Match m) {
		if (p.done) {
			return false;
		}
		int add = amount(q, m);
		if (add <= 0) {
			return false;
		}
		p.progress = Math.min(q.target, p.progress + add);
		if (p.progress >= q.target) {
			p.done = true;
			return true;
		}
		return false;
	}

	/** ISO-like week number counted from 1970 (weeks start on Monday). */
	public static long week(long epochDay) {
		// 1970-01-01 was a Thursday; shift so weeks start on Monday
		return Math.floorDiv(epochDay + 3, 7);
	}
}
