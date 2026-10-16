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
	public Bots bots = new Bots();
	public FeaturedMode featured = new FeaturedMode();
	public Npcs npcs = new Npcs();
	public Seasons seasons = new Seasons();
	public Fairness fairness = new Fairness();
	public ClanSettings clans = new ClanSettings();
	public Effects effects = new Effects();
	public LobbyExtras lobbyExtras = new LobbyExtras();
	public ResourcePack resourcePack = new ResourcePack();
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
		/** Without a map vote: the chance that a hand-built map (config/battlehub/maps) is used, when there is one. */
		public double customMapChance = 0.5;
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

	/** Ranked seasons: fixed length from a start date; placements, a soft reset and rewards per tier. */
	public static final class Seasons {
		public boolean enabled = true;
		/** First day of season 1 (yyyy-mm-dd). */
		public String start = "2026-10-01";
		public int lengthDays = 56;
		/** The first ranked matches per mode in a season count double for the rating. */
		public int placements = 5;
		/** Share of the distance to the start rating that stays at a new season (0.5: halfway back). */
		public double keep = 0.5;
		/** Coins at the end of a season for the tier of your peak rating. */
		public Map<String, Integer> rewards = defaultSeasonRewards();
		/** Netherite has no ceiling: its divisions go up every this many points. */
		public int topDivisionStep = 100;
	}

	static Map<String, Integer> defaultSeasonRewards() {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("bronze", 100);
		m.put("silver", 250);
		m.put("gold", 500);
		m.put("diamond", 1000);
		m.put("netherite", 2000);
		return m;
	}

	/** Fair play: waiting after leaving matches, idle players, teaming, alerts for admins, reports. */
	public static final class Fairness {
		/** Leaving a match early (with at least two people in it) blocks queueing for a while. */
		public boolean leavePenalty = true;
		/** Seconds of waiting for the 1st, 2nd, 3rd, ... leave within a day. */
		public List<Integer> leaveSteps = new ArrayList<>(List.of(30, 120, 300, 900, 1800));
		/** Seconds without moving before an idle player is taken out of a match (0: never). */
		public int afkMatchSeconds = 90;
		/** ... or out of a queue. */
		public int afkQueueSeconds = 300;
		/** Free for All and Last Man Standing: warn two players who stay together without fighting each other. */
		public boolean antiTeaming = true;
		public int teamingSeconds = 30;
		/** Alerts for admins (never punishments). */
		public boolean antiCheat = true;
		public double maxReach = 3.6;
		public double maxCps = 20;
		public int hoverTicks = 80;
		/** Blocks per second on the ground (sprint-jumping is about 7). */
		public double maxSpeed = 13;
		public int reportCooldownSeconds = 60;
	}

	/** Clans: what it costs to start one, how many can join, and the coins for the weekly clan war. */
	public static final class ClanSettings {
		public int createCost = 1000;
		public int maxMembers = 20;
		/** Coins for every member of the clan with the most points in a week. */
		public int warReward = 500;
	}

	/** Show around matches: the intro flight over the arena, the killfeed and the victory shot. */
	public static final class Effects {
		/** At the start: the camera flies over the arena while the names are shown. */
		public boolean intro = true;
		public int introSeconds = 3;
		/** At the end: the camera circles the winner. */
		public boolean victoryCamera = true;
		/** The last kills on the right of the screen (matches without their own sidebar), and a line in chat. */
		public boolean killfeed = true;
		public int killfeedLines = 5;
	}

	/** Extras in the lobby: double jump and the training dummy. */
	public static final class LobbyExtras {
		/** Press jump twice in the air to jump again (not in matches, not while spectating). */
		public boolean doubleJump = true;
		public double doubleJumpForward = 1.1;
		public double doubleJumpUp = 0.75;
		public int doubleJumpCooldownTicks = 20;
		/** A dummy on the plaza that shows what your hits would do with a diamond sword. */
		public boolean dummy = true;
		/** Where it stands: degrees around the fountain (0 east, 90 south) and the distance. */
		public double dummyAngle = 135;
		public double dummyRadius = 8;
	}

	/**
	 * The optional resource pack: icons in the killfeed and titles, BattleHub sounds and music. Players can say no;
	 * everything works without it. Either BattleHub serves it itself ({@code port} and {@code host}, the address
	 * players use for the server), or you host the zip yourself and fill in {@code url}.
	 */
	public static final class ResourcePack {
		public boolean enabled = false;
		/** Your own address for the zip (then nothing is served); empty: BattleHub serves it. */
		public String url = "";
		/** The server's address as players see it (for the built-in server). */
		public String host = "";
		public int port = 25599;
		/** Players who say no cannot join BattleHub. */
		public boolean required = false;
		/** Music in the lobby (with the pack). */
		public boolean music = true;
		public int musicEverySeconds = 240;
	}

	/** Bots that play when there are not enough people, and for practice. */
	public static final class Bots {
		public boolean enabled = true;
		/** Fill duel queues, party lobbies, 1v2 and 1v3 with bots when a player waits too long (never ranked). */
		public boolean fillQueues = true;
		public int fillAfterSeconds = 15;
		/** Party lobbies are filled up to this many players (at least the game's minimum, at most its maximum). */
		public int partyFillTo = 4;
		/** easy, normal or hard. */
		public String fillLevel = "normal";
		public int maxBots = 12;
		/** Share of the coins and XP paid in matches with bots; such matches never count for stats or ELO. */
		public double rewardFactor = 0.5;
		/** A bot leaves this many seconds after it is back in the lobby. */
		public int idleSeconds = 4;
	}

	/** The mode of the day: another mode every day, with more coins. */
	public static final class FeaturedMode {
		public boolean enabled = true;
		public double coinMultiplier = 2.0;
		public List<String> modes = new ArrayList<>(List.of("ffa", "bed_wars", "prop_hunt", "sumo", "tnt_run", "bow_spleef",
				"koth", "ctf", "lms", "parkour", "1v2", "1v3", "spleef", "skywars", "murder_mystery"));
	}

	/** Characters on the lobby plaza that take you to a game. */
	public static final class Npcs {
		public boolean enabled = true;
		/** Distance from the fountain. */
		public double radius = 10.5;
		public List<Npc> list = defaultNpcs();
	}

	/** One lobby character. */
	public static final class Npc {
		/** Lang key part: npc.<id>. */
		public String id = "";
		/** menu:pvp, menu:ranked, menu:party, menu:practice or game:<mode id>. */
		public String action = "";
		/** The item in its hand. */
		public String item = "minecraft:diamond_sword";
		/** Where on the circle around the fountain, in degrees: 0 east, 90 south, 180 west, 270 north. */
		public double angle;

		public Npc() {}

		Npc(String id, String action, String item, double angle) {
			this.id = id;
			this.action = action;
			this.item = item;
			this.angle = angle;
		}
	}

	static List<Npc> defaultNpcs() {
		List<Npc> l = new ArrayList<>();
		l.add(new Npc("pvp", "menu:pvp", "minecraft:diamond_sword", 190));
		l.add(new Npc("ffa", "game:ffa", "minecraft:iron_axe", 210));
		l.add(new Npc("ranked", "menu:ranked", "minecraft:netherite_sword", 230));
		l.add(new Npc("practice", "menu:practice", "minecraft:target", 250));
		l.add(new Npc("bed_wars", "game:bed_wars", "minecraft:red_bed", 290));
		l.add(new Npc("prop_hunt", "game:prop_hunt", "minecraft:bookshelf", 310));
		l.add(new Npc("party", "menu:party", "minecraft:cake", 330));
		l.add(new Npc("parkour", "game:parkour", "minecraft:feather", 350));
		return l;
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
		// Phase 6 modes
		m.put("bridge", new Game(2, 2, 900, "bridge").with("goalsToWin", 5));
		m.put("boxing", new Game(2, 2, 300, "boxing").with("hitsToWin", 100));
		m.put("build_uhc", new Game(2, 2, 900, "build_uhc").with("buildHeight", 20));
		m.put("spleef", new Game(2, 16, 300, "spleef").with("layers", 2));
		m.put("skywars", new Game(2, 12, 600, "skywars").with("islandChestRolls", 7).with("centerChestRolls", 9));
		m.put("murder_mystery", new Game(4, 16, 300, "murder_mystery").with("goldToBow", 10).with("goldEverySeconds", 3)
				.with("swordAfterSeconds", 10).with("arrowCooldownSeconds", 4));
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
		if (seasons == null) seasons = new Seasons();
		if (seasons.start == null) seasons.start = "2026-10-01";
		seasons.lengthDays = Math.max(1, seasons.lengthDays);
		seasons.placements = Math.max(0, seasons.placements);
		seasons.keep = Math.max(0, Math.min(1, seasons.keep));
		seasons.topDivisionStep = Math.max(1, seasons.topDivisionStep);
		if (seasons.rewards == null) seasons.rewards = defaultSeasonRewards();
		if (clans == null) clans = new ClanSettings();
		if (effects == null) effects = new Effects();
		if (lobbyExtras == null) lobbyExtras = new LobbyExtras();
		if (resourcePack == null) resourcePack = new ResourcePack();
		effects.introSeconds = Math.max(0, Math.min(10, effects.introSeconds));
		effects.killfeedLines = Math.max(1, Math.min(10, effects.killfeedLines));
		lobbyExtras.doubleJumpCooldownTicks = Math.max(0, lobbyExtras.doubleJumpCooldownTicks);
		if (resourcePack.url == null) resourcePack.url = "";
		if (resourcePack.host == null) resourcePack.host = "";
		clans.createCost = Math.max(0, clans.createCost);
		clans.maxMembers = Math.max(2, Math.min(100, clans.maxMembers));
		clans.warReward = Math.max(0, clans.warReward);
		if (fairness == null) fairness = new Fairness();
		if (fairness.leaveSteps == null) fairness.leaveSteps = new Fairness().leaveSteps;
		fairness.leaveSteps.removeIf(v -> v == null);
		fairness.afkMatchSeconds = Math.max(0, fairness.afkMatchSeconds);
		fairness.afkQueueSeconds = Math.max(0, fairness.afkQueueSeconds);
		if (bots == null) bots = new Bots();
		bots.fillAfterSeconds = Math.max(0, bots.fillAfterSeconds);
		bots.partyFillTo = Math.max(2, Math.min(16, bots.partyFillTo));
		bots.maxBots = Math.max(0, Math.min(64, bots.maxBots));
		bots.idleSeconds = Math.max(1, bots.idleSeconds);
		if (bots.fillLevel == null) bots.fillLevel = "normal";
		if (featured == null) featured = new FeaturedMode();
		if (featured.modes == null) featured.modes = new FeaturedMode().modes;
		if (npcs == null) npcs = new Npcs();
		if (npcs.list == null) npcs.list = defaultNpcs();
		npcs.list.removeIf(n -> n == null || n.id == null || n.action == null);
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
