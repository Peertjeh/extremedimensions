package net.battlehub.core;

/** Coins and XP earned in a match. Everyone gets the participation amount, winners get the win amount on top. */
public final class Rewards {
	private Rewards() {}

	public record Reward(int coins, int xp) {
		public Reward plus(Reward o) {
			return new Reward(coins + o.coins, xp + o.xp);
		}
	}

	public static Reward compute(RewardSettings s, Outcome outcome, int kills, double multiplier) {
		int k = Math.max(0, kills);
		double coins = s.participationCoins + (long) s.killCoins * k;
		double xp = s.participationXp + (long) s.killXp * k;
		if (outcome == Outcome.WIN) {
			coins += s.winCoins;
			xp += s.winXp;
		}
		double m = multiplier <= 0 ? 1.0 : multiplier;
		return new Reward((int) Math.round(coins * m), (int) Math.round(xp * m));
	}
}
