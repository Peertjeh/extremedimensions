package net.battlehub.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.core.GameMode;
import net.battlehub.core.RankTiers;
import net.battlehub.core.RewardSettings;

/** {@code config/battlehub/config.json}. Every field has a default; missing keys are filled in on load. */
public final class HubConfig {
	public static final int CURRENT_VERSION = 3;

	public int configVersion = CURRENT_VERSION;

	/** Fallback language file (config/battlehub/lang/<code>.json or the built-in en_us). */
	public String language = "en_us";
	/** Use the language of each player's client when a matching file exists. */
	public boolean perPlayerLanguage = true;

	public Hub hub = new Hub();
	public Lobby lobby = new Lobby();
	public Compass compass = new Compass();
	public Protection protection = new Protection();
	public Timings timings = new Timings();
	public Rewards rewards = new Rewards();
	public Elo elo = new Elo();
	public Levels levels = new Levels();
	public Streaks streaks = new Streaks();
	public Titles titles = new Titles();
	public Arenas arenas = new Arenas();
	public Leaderboards leaderboards = new Leaderboards();
	public Map<String, Game> games = defaultGames();

	/** BattleHub's own world. Every other dimension is normal gameplay that BattleHub leaves alone. */
	public static final class Hub {
		/** Put players in BattleHub when they join the server (for servers that are only BattleHub). */
		public boolean sendToHubOnJoin = false;
		/** Tell players in the normal world how to get to BattleHub when they join. */
		public boolean joinHint = true;
		/** Game mode in the hub: adventure (default) or survival. */
		public String gameMode = "adventure";
		/** Show the BattleHub level and progress on the XP bar while in BattleHub. */
		public boolean levelOnXpBar = true;
		/** The door in the last hotbar slot that takes you back to the normal world. */
		public boolean exitItem = true;
	}

	public static final class Lobby {
		/** Set by /battlehub setlobby (in the hub world). When not set, the spawn of the built lobby is used. */
		public boolean set = false;
		public String dimension = "battlehub:hub";
		public double x;
		public double y = 101;
		public double z;
		public float yaw;
		public float pitch;
		/** Put players at the lobby spawn when they join the server while in the hub world. */
		public boolean teleportOnJoin = true;
		/** Keep health and hunger full while in the lobby. */
		public boolean keepFed = true;
		/** Falling below this Y in the hub world sends you back to the lobby spawn. */
		public int voidY = 60;
	}

	public static final class Compass {
		/** Hotbar slot 0-8. */
		public int slot = 4;
		public String item = "minecraft:compass";
		public boolean glint = true;
	}

	public static final class Protection {
		/** Players outside an active fight take no damage at all. */
		public boolean noDamageOutsideMatches = true;
		/** Players outside matches cannot die (falls, /kill, ...): they are healed and sent to the lobby. */
		public boolean preventDeathOutsideMatches = true;
		/** No block breaking or placing in the lobby area, except for operators in creative mode. */
		public boolean protectLobbyBlocks = true;
		/** Nobody can drop items while in a match or in the lobby area. */
		public boolean noItemDrops = true;
	}

	public static final class Timings {
		public int duelCountdownSeconds = 5;
		public int partyCountdownSeconds = 30;
		public int partyFullCountdownSeconds = 10;
		public int matchStartCountdownSeconds = 5;
		/** Pause after a match ends before everyone goes back to the lobby. */
		public int endDelaySeconds = 4;
		public int challengeExpireSeconds = 60;
		/** Duels end in a draw after this long. */
		public int duelTimeLimitSeconds = 600;
		/** How often (ticks) open menus such as Spectate refresh. */
		public int menuRefreshTicks = 20;
		/** Respawn delay in Capture The Flag and King of the Hill. */
		public int respawnDelaySeconds = 4;
		/** Countdown before round 2 and 3 of a best-of-3 duel. */
		public int roundCountdownSeconds = 3;
		/** How long the "play again" button and item stay after a match. */
		public int playAgainSeconds = 30;
		/** Wait for an arena build at most this long before giving up. */
		public int arenaBuildTimeoutSeconds = 30;
	}

	public static final class Rewards {
		public RewardSettings duel = new RewardSettings(50, 10, 5, 100, 30, 10);
		public RewardSettings ranked = new RewardSettings(80, 15, 5, 150, 40, 10);
		public RewardSettings party = new RewardSettings(75, 15, 5, 120, 40, 8);
	}

	public static final class Elo {
		public int start = 1000;
		public int kFactor = 32;
		public int floor = 0;
		public Map<String, Integer> tiers = RankTiers.defaults();
		/** Ranked matchmaking window: start difference, growth per second waited, maximum. */
		public int windowBase = 100;
		public int windowPerSecond = 10;
		public int windowMax = 600;
	}

	public static final class Levels {
		/** XP from level L to L+1: (base + increment*(L-1)) * growth^(L-1). */
		public int baseXp = 100;
		public int increment = 50;
		public double growth = 1.0;
		public int maxLevel = 100;
		/** Every N-th level pays a coin bonus. */
		public int bonusEvery = 5;
		public int bonusCoins = 100;
	}

	public static final class Streaks {
		public List<Integer> milestones = new ArrayList<>(List.of(5, 10, 25, 50));
	}

	public static final class Titles {
		/** Show the selected title in chat, above the head and in the tab list via scoreboard teams. */
		public boolean useScoreboardTeams = true;
	}

	public static final class Arenas {
		/** Concurrent matches. */
		public int maxSlots = 48;
		/** Distance between slot centres in the arena dimension. */
		public int slotSpacing = 1024;
		/** Floor height of arenas. */
		public int baseY = 100;
		/** Blocks placed per tick while building or clearing arenas. */
		public int blocksPerTick = 40000;
		/** Arena themes per mode (forest, castle, nether, ice, sky); one is picked at random per match. */
		public Map<String, List<String>> themes = defaultThemes();
	}

	/** Floating leaderboards above the wall of the lobby BattleHub builds (not at a lobby set with /battlehub setlobby). */
	public static final class Leaderboards {
		public boolean enabled = true;
		/** Seconds before every board on a wall moves on to the next mode. */
		public int cycleSeconds = 8;
		/** Players per board. */
		public int rows = 10;
		/** Players with fewer kills are not on the K/D boards. */
		public int minKillsForKd = 10;
		/** Text size; 1 is the size of a name tag. */
		public double scale = 1.25;
		public List<BoardWall> walls = defaultWalls();
	}

	/** A row of boards on one side of the lobby wall. */
	public static final class BoardWall {
		/** northwest, northeast, southwest or southeast. */
		public String side = "northwest";
		/** Count ranked matches only (ranked duels). */
		public boolean ranked;
		/** One board each, left to right as seen from the plaza: kills, deaths, kd, coins, wins, elo. */
		public List<String> stats = new ArrayList<>();
		/** The modes the boards show in turn: all, pvp (FFA, duels, 1v2, 1v3) or a mode id such as sword or bed_wars. */
		public List<String> modes = new ArrayList<>();

		public BoardWall() {}

		BoardWall(String side, boolean ranked, List<String> stats, List<String> modes) {
			this.side = side;
			this.ranked = ranked;
			this.stats = new ArrayList<>(stats);
			this.modes = new ArrayList<>(modes);
		}
	}

	/** Total kills and deaths over every game on the north-west wall, the best parkour runner on the south-west wall. */
	static List<BoardWall> defaultWalls() {
		List<BoardWall> l = new ArrayList<>();
		l.add(new BoardWall("northwest", false, List.of("kills", "deaths"), List.of("all")));
		l.add(new BoardWall("southwest", false, List.of("wins"), List.of("parkour")));
		return l;
	}

	/** What 1.6.0 wrote by default (PvP and Ranked walls); replaced by the new default on upgrade. */
	public static List<BoardWall> wallsOf160() {
		List<BoardWall> l = new ArrayList<>();
		l.add(new BoardWall("northwest", false, List.of("kills", "deaths", "kd", "coins"),
				List.of("pvp", "ffa", "sword", "axe", "mace", "bow", "crossbow", "1v2", "1v3")));
		l.add(new BoardWall("southwest", true, List.of("elo", "kills", "deaths", "kd", "coins"),
				List.of("all", "sword", "axe", "mace", "bow", "crossbow")));
		return l;
	}

	private static boolean sameWalls(List<BoardWall> a, List<BoardWall> b) {
		if (a == null || a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			BoardWall x = a.get(i);
			BoardWall y = b.get(i);
			if (x == null || x.ranked != y.ranked || !y.side.equals(x.side) || !y.stats.equals(x.stats) || !y.modes.equals(x.modes)) {
				return false;
			}
		}
		return true;
	}

	/** Settings for one mode. Only the keys that matter for that mode are used. */
	public static final class Game {
		public boolean enabled = true;
		public int minPlayers = 2;
		public int maxPlayers = 16;
		/** 0 = no limit. */
		public int timeLimitSeconds = 300;
		public String kit = "";
		public Map<String, Double> settings = new LinkedHashMap<>();

		public Game() {}

		Game(int min, int max, int timeLimit, String kit) {
			this.minPlayers = min;
			this.maxPlayers = max;
			this.timeLimitSeconds = timeLimit;
			this.kit = kit;
		}

		Game with(String key, double value) {
			settings.put(key, value);
			return this;
		}

		public double setting(String key, double fallback) {
			Double v = settings == null ? null : settings.get(key);
			return v == null ? fallback : v;
		}
	}

	public Game game(GameMode mode) {
		Game g = games.get(mode.id());
		if (g == null) {
			g = defaultGames().get(mode.id());
			games.put(mode.id(), g);
		}
		return g;
	}

	static Map<String, Game> defaultGames() {
		Map<String, Game> m = new LinkedHashMap<>();
		m.put("sword", new Game(2, 2, 600, "sword"));
		m.put("axe", new Game(2, 2, 600, "axe"));
		m.put("mace", new Game(2, 2, 600, "mace").with("arenaScale", 1.8));
		m.put("bow", new Game(2, 2, 600, "bow"));
		m.put("crossbow", new Game(2, 2, 600, "crossbow"));
		m.put("bow_spleef", new Game(2, 12, 300, "bow_spleef"));
		m.put("tnt_run", new Game(2, 16, 300, "tnt_run").with("decayTicks", 8).with("layers", 3));
		m.put("sumo", new Game(2, 10, 240, "sumo"));
		m.put("koth", new Game(2, 12, 300, "koth").with("targetPoints", 90).with("pointsPerSecond", 1));
		m.put("ctf", new Game(2, 16, 600, "ctf").with("capturesToWin", 3).with("flagReturnSeconds", 30));
		m.put("parkour", new Game(1, 16, 480, "parkour").with("jumpsEasy", 30).with("jumpsMedium", 50).with("jumpsHard", 70)
				.with("jumpsExtreme", 90).with("countdownSeconds", 15)
				.with("coinsEasy", 0).with("coinsMedium", 0).with("coinsHard", 1).with("coinsExtreme", 1));
		m.put("lms", new Game(2, 16, 420, "lms").with("borderStart", 80).with("borderEnd", 8)
				.with("borderDelaySeconds", 45).with("borderShrinkSeconds", 180).with("borderDamagePerSecond", 2));
		m.put("ffa", new Game(2, 16, 300, "ffa").with("killsToWin", 15).with("spawnProtectionSeconds", 3));
		// The time limit is per round; the kit is voted (a duel mode's kit)
		// 1v2 and 1v3: the kit is chosen in the menu (sword, axe or mace)
		m.put("1v2", new Game(3, 3, 300, "").with("roundsToWin", 1));
		m.put("1v3", new Game(4, 4, 300, "").with("roundsToWin", 1));
		m.put("tournament", new Game(4, 16, 180, "").with("winnerCoins", 500).with("finalistCoins", 150)
				.with("breakSeconds", 10));
		// Bed Wars and Prop Hunt keep their own settings in bedwars.json and prophunt.json
		m.put("bed_wars", new Game(2, 16, 1800, "bedwars"));
		m.put("prop_hunt", new Game(3, 16, 0, "prophunt_hunter"));
		return m;
	}

	static Map<String, List<String>> defaultThemes() {
		Map<String, List<String>> m = new LinkedHashMap<>();
		List<String> all = new ArrayList<>();
		for (net.battlehub.arena.Theme t : net.battlehub.arena.Theme.values()) {
			all.add(t.id());
		}
		for (GameMode mode : GameMode.values()) {
			m.put(mode.id(), new ArrayList<>(all));
		}
		// Mace needs height: the open themes (floating islands) suit it best.
		m.put("mace", new ArrayList<>(List.of("sky", "end")));
		return m;
	}

	/** The theme lists 1.0 wrote by default; such a list is replaced by the new default on upgrade. */
	private static final List<String> OLD_DEFAULT_THEMES = List.of("forest", "castle", "nether", "ice", "sky");

	/** Repairs nulls and nonsense after loading user edits. */
	public HubConfig normalize() {
		if (hub == null) hub = new Hub();
		if (lobby == null) lobby = new Lobby();
		int from = configVersion;
		if (configVersion < 2) {
			// 1.0 used a lobby in the normal world; 1.1 has its own world, so the old position no longer applies.
			lobby.set = false;
			lobby.dimension = "battlehub:hub";
			lobby.voidY = 60;
			// 1.1: parkour can be played alone, and races are longer
			if (games != null && games.get("parkour") != null) {
				Game pk = games.get("parkour");
				if (pk.minPlayers == 2) pk.minPlayers = 1;
				if (pk.timeLimitSeconds == 420) pk.timeLimitSeconds = 480;
			}
			// Unchanged 1.0 theme lists get the new themes
			if (arenas != null && arenas.themes != null) {
				Map<String, List<String>> defs = defaultThemes();
				for (Map.Entry<String, List<String>> e : arenas.themes.entrySet()) {
					List<String> v = e.getValue();
					if (v != null && (v.equals(OLD_DEFAULT_THEMES) || v.equals(List.of("sky"))) && defs.containsKey(e.getKey())) {
						e.setValue(defs.get(e.getKey()));
					}
				}
			}
		}
		configVersion = CURRENT_VERSION;
		if (compass == null) compass = new Compass();
		if (protection == null) protection = new Protection();
		if (timings == null) timings = new Timings();
		if (rewards == null) rewards = new Rewards();
		if (rewards.duel == null) rewards.duel = new RewardSettings();
		if (rewards.ranked == null) rewards.ranked = new RewardSettings();
		if (rewards.party == null) rewards.party = new RewardSettings();
		if (elo == null) elo = new Elo();
		if (elo.tiers == null || elo.tiers.isEmpty()) elo.tiers = RankTiers.defaults();
		if (levels == null) levels = new Levels();
		if (streaks == null) streaks = new Streaks();
		if (streaks.milestones == null) streaks.milestones = new ArrayList<>();
		if (titles == null) titles = new Titles();
		if (arenas == null) arenas = new Arenas();
		if (leaderboards == null) leaderboards = new Leaderboards();
		if (from < 3 && sameWalls(leaderboards.walls, wallsOf160())) {
			// 1.6.1: three boards instead of nine (kills, deaths, parkour), a little bigger
			leaderboards.walls = defaultWalls();
			if (leaderboards.scale == 1.0) leaderboards.scale = 1.25;
		}
		if (leaderboards.walls == null) leaderboards.walls = defaultWalls();
		leaderboards.walls.removeIf(w -> w == null);
		for (BoardWall w : leaderboards.walls) {
			if (w.side == null) w.side = "northwest";
			if (w.stats == null) w.stats = new ArrayList<>();
			if (w.modes == null || w.modes.isEmpty()) w.modes = new ArrayList<>(List.of(w.ranked ? "all" : "pvp"));
		}
		leaderboards.cycleSeconds = Math.max(3, Math.min(600, leaderboards.cycleSeconds));
		leaderboards.rows = Math.max(3, Math.min(15, leaderboards.rows));
		leaderboards.minKillsForKd = Math.max(1, leaderboards.minKillsForKd);
		if (!(leaderboards.scale >= 0.5)) leaderboards.scale = 1.25;
		leaderboards.scale = Math.min(3.0, leaderboards.scale);
		if (arenas.themes == null) arenas.themes = defaultThemes();
		Map<String, List<String>> defThemes = defaultThemes();
		for (GameMode mode : GameMode.values()) {
			List<String> t = arenas.themes.get(mode.id());
			if (t == null || t.isEmpty()) {
				arenas.themes.put(mode.id(), defThemes.get(mode.id()));
			}
		}
		if (games == null) games = new LinkedHashMap<>();
		Map<String, Game> defs = defaultGames();
		for (Map.Entry<String, Game> e : defs.entrySet()) {
			Game g = games.get(e.getKey());
			if (g == null) {
				games.put(e.getKey(), e.getValue());
			} else {
				if (g.settings == null) g.settings = new LinkedHashMap<>();
				e.getValue().settings.forEach(g.settings::putIfAbsent);
				if (g.kit == null || g.kit.isBlank()) g.kit = e.getValue().kit;
			}
		}
		for (GameMode mode : GameMode.values()) {
			Game g = games.get(mode.id());
			if (mode.isDuel()) {
				g.minPlayers = 2;
				g.maxPlayers = 2;
			} else {
				// Parkour can be played alone (practice against your own record)
				g.minPlayers = Math.max(mode == GameMode.PARKOUR ? 1 : 2, Math.min(16, g.minPlayers));
				g.maxPlayers = Math.max(g.minPlayers, Math.min(16, g.maxPlayers));
			}
		}
		compass.slot = Math.max(0, Math.min(8, compass.slot));
		arenas.maxSlots = Math.max(1, Math.min(1024, arenas.maxSlots));
		arenas.slotSpacing = Math.max(256, arenas.slotSpacing);
		arenas.blocksPerTick = Math.max(500, arenas.blocksPerTick);
		levels.maxLevel = Math.max(1, levels.maxLevel);
		return this;
	}
}
