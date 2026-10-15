package net.battlehub.config;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.TitleDef;

/** {@code config/battlehub/titles.json}. */
public final class TitleConfig {
	/** 0 in files from before 1.8 (they get the season titles). */
	public int configVersion;
	public List<TitleDef> titles = defaults();

	static List<TitleDef> defaults() {
		List<TitleDef> l = new ArrayList<>();
		l.add(new TitleDef("rookie", "&7Rookie", "default", 0, ""));
		l.add(new TitleDef("fighter", "&fFighter", "level", 5, ""));
		l.add(new TitleDef("veteran", "&bVeteran", "level", 20, ""));
		l.add(new TitleDef("legend", "&6&lLegend", "level", 50, ""));
		l.add(new TitleDef("duelist", "&eDuelist", "duel_wins", 25, ""));
		l.add(new TitleDef("gladiator", "&cGladiator", "wins", 100, ""));
		l.add(new TitleDef("slayer", "&4Slayer", "kills", 250, ""));
		l.add(new TitleDef("unstoppable", "&c&lUnstoppable", "streak", 10, ""));
		l.add(new TitleDef("godlike", "&d&lGodlike", "streak", 25, ""));
		l.add(new TitleDef("blade_master", "&fBlade Master", "mode_wins", 25, "sword"));
		l.add(new TitleDef("lumberjack", "&6Lumberjack", "mode_wins", 25, "axe"));
		l.add(new TitleDef("mace_master", "&8Mace Master", "mode_wins", 25, "mace"));
		l.add(new TitleDef("sharpshooter", "&aSharpshooter", "mode_wins", 25, "bow"));
		l.add(new TitleDef("bolt", "&2Bolt", "mode_wins", 25, "crossbow"));
		l.add(new TitleDef("spleefer", "&bSpleefer", "mode_wins", 10, "bow_spleef"));
		l.add(new TitleDef("runner", "&cRunner", "mode_wins", 10, "tnt_run"));
		l.add(new TitleDef("sumo_king", "&e&lSumo King", "mode_wins", 10, "sumo"));
		l.add(new TitleDef("king", "&6King", "mode_wins", 10, "koth"));
		l.add(new TitleDef("flag_bearer", "&9Flag Bearer", "mode_wins", 10, "ctf"));
		l.add(new TitleDef("free_runner", "&aFree Runner", "mode_wins", 10, "parkour"));
		l.add(new TitleDef("survivor", "&2Survivor", "mode_wins", 10, "lms"));
		l.add(new TitleDef("diamond", "&b&lDiamond", "elo", 1450, ""));
		l.add(new TitleDef("netherite", "&8&lNetherite", "elo", 1700, ""));
		l.add(new TitleDef("rich", "&6Rich", "coins", 500, ""));
		l.add(new TitleDef("champion", "&e&lChampion", "coins", 2000, ""));
		l.add(new TitleDef("mythic", "&5&l&oMythic", "coins", 7500, ""));
		l.addAll(seasonTitles());
		return l;
	}

	/** 1.8: titles for the tier reached at the end of a ranked season. */
	static List<TitleDef> seasonTitles() {
		List<TitleDef> l = new ArrayList<>();
		l.add(new TitleDef("season_veteran", "&3Season Veteran", "season", 0, ""));
		l.add(new TitleDef("season_gold", "&e✦ Gold Season", "season", 2, ""));
		l.add(new TitleDef("season_diamond", "&b✦ Diamond Season", "season", 3, ""));
		l.add(new TitleDef("season_netherite", "&8&l✦ Netherite Season", "season", 4, ""));
		return l;
	}

	public TitleConfig normalize() {
		if (titles == null || titles.isEmpty()) {
			titles = defaults();
		}
		if (configVersion < 2) {
			// The season titles are new in 1.8
			for (TitleDef t : seasonTitles()) {
				if (titles.stream().noneMatch(o -> o != null && t.id.equals(o.id))) titles.add(t);
			}
			configVersion = 2;
		}
		titles.removeIf(t -> t == null || t.id == null || t.id.isBlank());
		for (TitleDef t : titles) {
			if (t.display == null) t.display = t.id;
			if (t.requirement == null) t.requirement = "default";
			if (t.mode == null) t.mode = "";
		}
		return this;
	}

	public TitleDef byId(String id) {
		for (TitleDef t : titles) {
			if (t.id.equalsIgnoreCase(id)) {
				return t;
			}
		}
		return null;
	}
}
