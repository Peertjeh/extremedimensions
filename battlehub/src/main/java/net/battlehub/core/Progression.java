package net.battlehub.core;

/** Adds XP and coins to a profile and reports level-ups, including milestone bonuses. */
public final class Progression {
	private Progression() {}

	public record LevelChange(int oldLevel, int newLevel, long bonusCoins) {
		public boolean leveledUp() {
			return newLevel > oldLevel;
		}
	}

	/**
	 * @param bonusEvery    every N-th level pays a bonus (0 disables)
	 * @param bonusCoins    coins per bonus level
	 */
	public static LevelChange addXp(PlayerProfile p, long xp, LevelCurve curve, int bonusEvery, int bonusCoins) {
		int old = curve.levelFor(p.xp);
		p.xp = Math.max(0, p.xp + xp);
		int now = curve.levelFor(p.xp);
		p.level = now;
		long bonus = 0;
		if (bonusEvery > 0) {
			for (int l = old + 1; l <= now; l++) {
				if (l % bonusEvery == 0) {
					bonus += bonusCoins;
				}
			}
		}
		p.coins += bonus;
		p.coinsEarned += bonus;
		return new LevelChange(old, now, bonus);
	}

	/** Pays (or, when negative, takes) coins; payments count towards the coins earned. */
	public static void addCoins(PlayerProfile p, long coins) {
		p.coins = Math.max(0, p.coins + coins);
		if (coins > 0) {
			p.coinsEarned += coins;
		}
	}
}
