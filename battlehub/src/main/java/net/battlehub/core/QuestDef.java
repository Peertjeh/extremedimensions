package net.battlehub.core;

/**
 * A quest from {@code quests.json}. Goals: play, win, kill, damage (hearts dealt), ranked_win, featured (play the
 * mode of the day), mvp; with a {@code mode} only matches of that mode count.
 */
public final class QuestDef {
	public String id = "";
	/** daily or weekly. */
	public String period = "daily";
	public String goal = "play";
	public String mode = "";
	public int target = 1;
	public int coins = 50;
	public int xp = 50;
	/** Season pass XP. */
	public int passXp = 100;

	public QuestDef() {}

	public QuestDef(String id, String period, String goal, String mode, int target, int coins, int xp, int passXp) {
		this.id = id;
		this.period = period;
		this.goal = goal;
		this.mode = mode == null ? "" : mode;
		this.target = target;
		this.coins = coins;
		this.xp = xp;
		this.passXp = passXp;
	}

	public boolean weekly() {
		return "weekly".equalsIgnoreCase(period);
	}
}
