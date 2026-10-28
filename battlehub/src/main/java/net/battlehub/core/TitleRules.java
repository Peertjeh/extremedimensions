package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Decides which titles a profile has unlocked and handles buying titles with coins. */
public final class TitleRules {
	private TitleRules() {}

	public static boolean unlocked(TitleDef t, PlayerProfile p, int startElo) {
		String req = t.requirement == null ? "default" : t.requirement.toLowerCase(Locale.ROOT);
		GameMode mode = GameMode.byId(t.mode);
		return switch (req) {
			case "default" -> true;
			case "level" -> p.level >= t.value;
			case "wins" -> p.overall.wins >= t.value;
			case "duel_wins" -> p.duelWins() >= t.value;
			case "mode_wins" -> mode != null && p.peek(mode).wins >= t.value;
			case "streak" -> p.overall.bestStreak >= t.value;
			case "mode_streak" -> mode != null && p.peek(mode).bestStreak >= t.value;
			case "kills" -> p.overall.kills >= t.value;
			case "elo" -> mode != null ? p.elo(mode, startElo) >= t.value : p.bestElo(startElo) >= t.value;
			case "coins", "placeholder" -> p.ownedTitles.contains(t.id);
			case "season" -> p.bestSeasonTier >= t.value;
			case "pass" -> p.bestPassTier >= t.value;
			case "prestige" -> p.prestige >= t.value;
			case "weekly_wins" -> p.weeklyWins >= t.value;
			default -> false;
		};
	}

	/** Titles that are unlocked now but were not announced yet; marks them as known. */
	public static List<TitleDef> newlyUnlocked(List<TitleDef> all, PlayerProfile p, int startElo) {
		List<TitleDef> out = new ArrayList<>();
		for (TitleDef t : all) {
			if (!p.knownTitles.contains(t.id) && unlocked(t, p, startElo)) {
				p.knownTitles.add(t.id);
				if (!"default".equalsIgnoreCase(t.requirement)) {
					out.add(t);
				}
			}
		}
		return out;
	}

	/**
	 * Whether a placeholder's value earns a "placeholder" title (1.51): the same text, any case, when the title names
	 * one; otherwise a number of at least its value. Nothing (the mod or placeholder-api missing) earns nothing.
	 */
	public static boolean meets(TitleDef t, String value) {
		if (value == null || value.isBlank()) return false;
		String v = value.trim();
		if (t.text != null && !t.text.isEmpty()) return v.equalsIgnoreCase(t.text.trim());
		try {
			return Double.parseDouble(v) >= t.value;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** The mod a "placeholder" title's placeholder belongs to ("extremedimensions"), or "" without one. */
	public static String namespace(TitleDef t) {
		String ph = t.placeholder == null ? "" : t.placeholder;
		int colon = ph.indexOf(':');
		return colon <= 0 ? "" : ph.substring(0, colon);
	}

	public enum BuyResult { BOUGHT, ALREADY_OWNED, NOT_FOR_SALE, TOO_EXPENSIVE }

	public static BuyResult buy(TitleDef t, PlayerProfile p) {
		if (!t.purchasable()) {
			return BuyResult.NOT_FOR_SALE;
		}
		if (p.ownedTitles.contains(t.id)) {
			return BuyResult.ALREADY_OWNED;
		}
		if (p.coins < t.value) {
			return BuyResult.TOO_EXPENSIVE;
		}
		p.coins -= t.value;
		p.ownedTitles.add(t.id);
		return BuyResult.BOUGHT;
	}

	/** Selects a title if unlocked; an empty id clears the title. */
	public static boolean select(TitleDef t, PlayerProfile p, int startElo) {
		if (t == null) {
			p.title = "";
			return true;
		}
		if (!unlocked(t, p, startElo)) {
			return false;
		}
		p.title = t.id;
		return true;
	}
}
