package net.battlehub.core;

import java.util.List;

/**
 * Login streak, season pass and prestige: the numbers behind coming back. Pure, so they are tested without
 * Minecraft.
 */
public final class Daily {
	private Daily() {}

	/** The streak after logging in today: one more after yesterday, back to 1 after a missed day, the same twice a day. */
	public static int nextStreak(int streak, long lastDay, long today) {
		if (lastDay == today) {
			return Math.max(1, streak);
		}
		return lastDay == today - 1 ? streak + 1 : 1;
	}

	/** Coins for day {@code n} of a streak (the last step repeats). */
	public static int loginCoins(int n, List<Integer> steps) {
		if (steps == null || steps.isEmpty() || n <= 0) {
			return 0;
		}
		Integer c = steps.get(Math.min(n, steps.size()) - 1);
		return c == null ? 0 : Math.max(0, c);
	}

	/** Season pass tier (0..maxTier) for this much pass XP. */
	public static int passTier(long xp, int xpPerTier, int maxTier) {
		return (int) Math.min(maxTier, Math.max(0, xp) / Math.max(1, xpPerTier));
	}

	/** Extra share of coins for each prestige (5% each, at most 50%). */
	public static double prestigeBonus(int prestige) {
		return Math.min(0.5, Math.max(0, prestige) * 0.05);
	}

	/** Stars after a name for prestige 1, 2, ... */
	public static String prestigeStars(int prestige) {
		if (prestige <= 0) return "";
		return prestige <= 5 ? "✦".repeat(prestige) : "✦" + prestige;
	}
}
