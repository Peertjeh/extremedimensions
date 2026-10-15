package net.battlehub.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the lobby leaderboards rank: one statistic over a group of modes, from the casual or the ranked counters.
 * Pure data, so the numbers can be tested without Minecraft.
 */
public final class BoardStats {
	private BoardStats() {}

	public enum Stat {
		KILLS, DEATHS, KD, COINS, WINS, ELO;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Stat byId(String id) {
			if (id == null) {
				return null;
			}
			String s = id.trim().toLowerCase(Locale.ROOT).replace("/", "");
			for (Stat st : values()) {
				if (st.id().equals(s)) {
					return st;
				}
			}
			return s.equals("kdr") ? KD : s.equals("coinsearned") || s.equals("coins_earned") ? COINS : null;
		}

		/** Players at or below this value are left off the board (no kills, never played ranked, ...). */
		public long floor() {
			return this == ELO ? Long.MIN_VALUE : 0;
		}
	}

	/** The PvP tab and the ranged duels: everything where players fight each other with a kit. */
	public static final List<GameMode> PVP = List.of(GameMode.FFA, GameMode.SWORD, GameMode.AXE, GameMode.MACE,
			GameMode.BOW, GameMode.CROSSBOW, GameMode.ONE_V_TWO, GameMode.ONE_V_THREE);

	/**
	 * Modes counted together: {@code all} (every mode), {@code pvp} (see {@link #PVP}) or a single mode id.
	 * {@code everything} marks "all": the casual totals then come from the overall counters.
	 */
	public record Scope(String id, List<GameMode> modes, boolean everything) {}

	/** The scope for a config entry, or null when it names no mode. */
	public static Scope scope(String id) {
		if (id == null) {
			return null;
		}
		String s = id.trim().toLowerCase(Locale.ROOT);
		if (s.equals("all")) {
			return new Scope("all", List.of(GameMode.values()), true);
		}
		if (s.equals("pvp")) {
			return new Scope("pvp", PVP, false);
		}
		GameMode m = GameMode.byId(s);
		return m == null ? null : new Scope(m.id(), List.of(m), false);
	}

	/** The counters of every mode in the scope added up. */
	public static ModeStats sum(PlayerProfile p, Scope scope, boolean ranked) {
		if (scope.everything() && !ranked) {
			return p.overall;
		}
		Map<String, ModeStats> source = ranked ? p.ranked : p.modes;
		ModeStats out = new ModeStats();
		for (GameMode m : scope.modes()) {
			ModeStats s = source.get(m.id());
			if (s != null) {
				out.played += s.played;
				out.wins += s.wins;
				out.losses += s.losses;
				out.draws += s.draws;
				out.kills += s.kills;
				out.deaths += s.deaths;
				out.coins += s.coins;
				out.bestStreak = Math.max(out.bestStreak, s.bestStreak);
			}
		}
		return out;
	}

	/**
	 * The value a player is ranked by. K/D is stored times 100 (2.5 = 250) and needs {@code minKillsForKd} kills;
	 * ELO is the best rating over the modes of the scope.
	 */
	public static long value(PlayerProfile p, Stat stat, Scope scope, boolean ranked, int minKillsForKd) {
		if (stat == Stat.ELO) {
			long best = Long.MIN_VALUE;
			for (GameMode m : scope.modes()) {
				Integer v = p.elo.get(m.id());
				if (v != null) {
					best = Math.max(best, v);
				}
			}
			return best;
		}
		if (stat == Stat.COINS && scope.everything() && !ranked) {
			// Includes level bonuses and tournament prizes, which belong to no single mode
			return p.coinsEarned;
		}
		ModeStats s = sum(p, scope, ranked);
		return switch (stat) {
			case KILLS -> s.kills;
			case DEATHS -> s.deaths;
			case WINS -> s.wins;
			case COINS -> s.coins;
			case KD -> s.kills < Math.max(1, minKillsForKd) ? 0 : Math.round(s.kills * 100.0 / Math.max(1, s.deaths));
			case ELO -> Long.MIN_VALUE;
		};
	}

	/** K/D with two decimals, the rest as whole numbers with a comma between thousands (12,345). */
	public static String format(Stat stat, long value) {
		if (stat == Stat.KD) {
			return String.format(Locale.ROOT, "%d.%02d", value / 100, Math.abs(value % 100));
		}
		return String.format(Locale.ROOT, "%,d", value);
	}
}
