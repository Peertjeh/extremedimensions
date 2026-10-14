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
	public static final int CURRENT_VERSION = 2;

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
		m.put("mace", new Game(2, 2, 600, "mace"));
		m.put("bow", new Game(2, 2, 600, "bow"));
		m.put("crossbow", new Game(2, 2, 600, "crossbow"));
		m.put("bow_spleef", new Game(2, 12, 300, "bow_spleef"));
		m.put("tnt_run", new Game(2, 16, 300, "tnt_run").with("decayTicks", 8).with("layers", 3));
		m.put("sumo", new Game(2, 10, 240, "sumo"));
		m.put("koth", new Game(2, 12, 300, "koth").with("targetPoints", 90).with("pointsPerSecond", 1));
		m.put("ctf", new Game(2, 16, 600, "ctf").with("capturesToWin", 3).with("flagReturnSeconds", 30));
		m.put("parkour", new Game(2, 16, 420, "parkour").with("jumps", 36).with("checkpointEvery", 9));
		m.put("lms", new Game(2, 16, 420, "lms").with("borderStart", 80).with("borderEnd", 8)
				.with("borderDelaySeconds", 45).with("borderShrinkSeconds", 180).with("borderDamagePerSecond", 2));
		return m;
	}

	static Map<String, List<String>> defaultThemes() {
		Map<String, List<String>> m = new LinkedHashMap<>();
		List<String> all = List.of("forest", "castle", "nether", "ice", "sky");
		for (GameMode mode : GameMode.values()) {
			m.put(mode.id(), new ArrayList<>(all));
		}
		// Mace needs height: the Sky Arena (floating islands) suits it best.
		m.put("mace", new ArrayList<>(List.of("sky")));
		return m;
	}

	/** Repairs nulls and nonsense after loading user edits. */
	public HubConfig normalize() {
		if (hub == null) hub = new Hub();
		if (lobby == null) lobby = new Lobby();
		if (configVersion < 2) {
			// 1.0 used a lobby in the normal world; 1.1 has its own world, so the old position no longer applies.
			lobby.set = false;
			lobby.dimension = "battlehub:hub";
			lobby.voidY = 60;
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
				g.minPlayers = Math.max(2, Math.min(16, g.minPlayers));
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
