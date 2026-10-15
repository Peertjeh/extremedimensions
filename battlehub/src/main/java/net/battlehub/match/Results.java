package net.battlehub.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.Elo;
import net.battlehub.core.Outcome;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.Progression;
import net.battlehub.core.RewardSettings;
import net.battlehub.core.Rewards;
import net.battlehub.core.StatsRecorder;
import net.battlehub.core.TitleDef;
import net.battlehub.core.TitleRules;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Ranks;
import net.battlehub.progression.TitleService;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Applies a finished match to every profile: stats, streaks, coins, XP, levels, ELO and title unlocks. */
public final class Results {
	private Results() {}

	public static void apply(Match m) {
		HubConfig cfg = ConfigManager.config();
		Map<UUID, int[]> elo = new HashMap<>();
		if (m.ranked && m.mode.isDuel() && m.participants().size() == 2) {
			elo = rankedElo(m, cfg);
		}
		RewardSettings settings = m.ranked ? cfg.rewards.ranked : (m.mode.isDuel() ? cfg.rewards.duel : cfg.rewards.party);
		for (UUID id : m.participants()) {
			PlayerProfile prof = PlayerStore.get(id);
			Outcome o = m.outcome(id);
			int kills = m.kills(id);
			StatsRecorder.Result streak = StatsRecorder.record(prof, m.mode, m.ranked, o, kills, m.deaths(id));
			Rewards.Reward full = Rewards.compute(settings, o, kills, 1.0);
			Rewards.Reward reward = new Rewards.Reward((int) Math.round(full.coins() * Math.max(0, m.coinFactor())), full.xp());
			Progression.addCoins(prof, reward.coins());
			StatsRecorder.recordCoins(prof, m.mode, m.ranked, reward.coins());
			Progression.LevelChange lvl = Progression.addXp(prof, reward.xp(), Ranks.curve(), cfg.levels.bonusEvery, cfg.levels.bonusCoins);
			int[] e = elo.get(id);
			if (e != null) {
				prof.setElo(m.mode, e[1]);
			}
			List<TitleDef> unlocked = TitleRules.newlyUnlocked(ConfigManager.titles().titles, prof, Ranks.startElo());
			PlayerStore.save(id);

			ServerPlayer p = m.player(id);
			boolean milestone = o == Outcome.WIN && StatsRecorder.isMilestone(streak.overallStreak(), cfg.streaks.milestones);
			if (milestone) {
				Msg.broadcast(m.server, "streak.milestone", m.name(id), streak.overallStreak());
			}
			if (p == null || m.leftPlayers().contains(id)) {
				continue;
			}
			Msg.raw(p, Lang.tr(p, "result.header", Lang.tr(p, Icons.modeKey(m.mode))));
			Msg.raw(p, Lang.tr(p, "result." + o.name().toLowerCase(java.util.Locale.ROOT)));
			Msg.raw(p, Lang.tr(p, "result.rewards", reward.coins(), reward.xp(), kills));
			if (reward.coins() < full.coins() && m.noCoinsKey() != null) {
				Msg.raw(p, Lang.tr(p, m.noCoinsKey()));
			}
			if (e != null) {
				int diff = e[1] - e[0];
				Component change = Lang.format((diff >= 0 ? "&a+" : "&c") + diff);
				Msg.raw(p, Lang.tr(p, "result.elo", e[1], change, Ranks.tierName(p, e[1])));
				if (!Ranks.tiers().tierFor(e[0]).id().equals(Ranks.tiers().tierFor(e[1]).id())) {
					Msg.raw(p, Lang.tr(p, diff > 0 ? "result.tier_up" : "result.tier_down", Ranks.tierName(p, e[1])));
				}
			}
			if (o == Outcome.WIN) {
				Msg.raw(p, Lang.tr(p, "result.streak", streak.overallStreak(), streak.modeStreak()));
			}
			if (lvl.leveledUp()) {
				Msg.title(p, Lang.tr(p, "level.up_title"), Lang.tr(p, "level.up_sub", lvl.newLevel()), 10, 50, 15);
				Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
				Msg.send(p, "level.up", lvl.newLevel());
				if (lvl.bonusCoins() > 0) {
					Msg.send(p, "level.bonus", lvl.bonusCoins());
				}
			}
			for (TitleDef t : unlocked) {
				Msg.send(p, "titles.unlocked", Lang.colored(t.display));
			}
			TitleService.apply(p);
		}
	}

	/** {before, after} rating per player. */
	private static Map<UUID, int[]> rankedElo(Match m, HubConfig cfg) {
		Map<UUID, int[]> out = new HashMap<>();
		UUID a = m.participants().get(0);
		UUID b = m.participants().get(1);
		int start = cfg.elo.start;
		int ra = PlayerStore.get(a).elo(m.mode, start);
		int rb = PlayerStore.get(b).elo(m.mode, start);
		Outcome oa = m.outcome(a);
		Outcome ob = m.outcome(b);
		int na;
		int nb;
		if (oa == Outcome.WIN && ob != Outcome.WIN) {
			int[] r = Elo.afterWin(ra, rb, cfg.elo.kFactor, cfg.elo.floor);
			na = r[0];
			nb = r[1];
		} else if (ob == Outcome.WIN && oa != Outcome.WIN) {
			int[] r = Elo.afterWin(rb, ra, cfg.elo.kFactor, cfg.elo.floor);
			nb = r[0];
			na = r[1];
		} else {
			na = Math.max(cfg.elo.floor, ra + Elo.delta(ra, rb, 0.5, cfg.elo.kFactor));
			nb = Math.max(cfg.elo.floor, rb + Elo.delta(rb, ra, 0.5, cfg.elo.kFactor));
		}
		out.put(a, new int[] {ra, na});
		out.put(b, new int[] {rb, nb});
		return out;
	}
}
