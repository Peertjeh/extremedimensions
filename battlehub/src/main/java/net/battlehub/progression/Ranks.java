package net.battlehub.progression;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.LevelCurve;
import net.battlehub.core.RankTiers;
import net.battlehub.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Rank tiers and the level curve, rebuilt from the config on every reload. */
public final class Ranks {
	private Ranks() {}

	private static RankTiers tiers;
	private static LevelCurve curve;

	public static void reload() {
		HubConfig c = ConfigManager.config();
		tiers = new RankTiers(c.elo.tiers);
		curve = new LevelCurve(c.levels.baseXp, c.levels.increment, c.levels.growth, c.levels.maxLevel);
	}

	public static RankTiers tiers() {
		if (tiers == null) reload();
		return tiers;
	}

	public static LevelCurve curve() {
		if (curve == null) reload();
		return curve;
	}

	/** "Gold" in the tier colour, from lang key tier.<id>. */
	public static Component tierName(ServerPlayer p, int elo) {
		return Lang.tr(p, "tier." + tiers().tierFor(elo).id());
	}

	public static int startElo() {
		return ConfigManager.config().elo.start;
	}
}
