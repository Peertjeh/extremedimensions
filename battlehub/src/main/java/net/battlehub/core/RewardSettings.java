package net.battlehub.core;

/** Coins and XP for one category of matches. Lives in the config as plain JSON. */
public final class RewardSettings {
	public int winCoins = 50;
	public int participationCoins = 10;
	public int killCoins = 5;
	public int winXp = 100;
	public int participationXp = 30;
	public int killXp = 10;

	public RewardSettings() {}

	public RewardSettings(int winCoins, int participationCoins, int killCoins, int winXp, int participationXp, int killXp) {
		this.winCoins = winCoins;
		this.participationCoins = participationCoins;
		this.killCoins = killCoins;
		this.winXp = winXp;
		this.participationXp = participationXp;
		this.killXp = killXp;
	}
}
