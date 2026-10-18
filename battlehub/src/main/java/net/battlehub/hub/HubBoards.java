package net.battlehub.hub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.BoardStats;
import net.battlehub.core.GameMode;
import net.battlehub.core.Leaderboard;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Ranks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * The leaderboards above the wall of the lobby. Every board is a floating text that exists only on the clients:
 * it is sent to each player in the hub world in their own language, with their own place at the bottom, and is
 * never saved in the world. The boards on one wall show the same mode and move on to the next mode together.
 */
public final class HubBoards {
	private HubBoards() {}

	/** Above the middle of the low wall around the plaza, a little higher than its slabs. */
	private static final double RADIUS = HubLayout.RADIUS - 0.6;
	private static final double HEIGHT = 2.6;
	/** Room left at both ends of a wall, in degrees, so no board hangs over a gateway or its towers. */
	private static final double MARGIN = 14;
	private static final int MAX_BOARDS = 8;
	private static final int LINE_WIDTH = 260;
	/** A dark backing behind the text (alpha 0x80): enough contrast against a night sky, light enough by day. */
	private static final int BACKGROUND = 0x80000000;
	/** Stats change after matches; the order is worked out again this often. */
	private static final int RANK_TICKS = 100;

	private record Board(int entityId, UUID uuid, BoardStats.Stat stat, Vec3 pos, float yaw) {}

	private record Ranking(List<Leaderboard.Row<PlayerProfile>> rows, Map<String, Leaderboard.Row<PlayerProfile>> byId) {}

	private static final class Wall {
		final boolean ranked;
		final List<BoardStats.Scope> scopes = new ArrayList<>();
		final List<Board> boards = new ArrayList<>();
		final Map<BoardStats.Stat, Ranking> rankings = new HashMap<>();
		int scope = -1;

		Wall(boolean ranked) {
			this.ranked = ranked;
		}
	}

	private static final List<Wall> WALLS = new ArrayList<>();
	private static final HubViewers VIEWERS = new HubViewers();
	private static HubConfig builtFor;
	private static int ticks;

	public static void tick(MinecraftServer server) {
		ticks++;
		HubConfig cfg = ConfigManager.config();
		ServerLevel hub = server.getLevel(HubWorld.KEY);
		if (cfg != builtFor) {
			// New config (/battlehub reload): take the old boards away and place them again
			VIEWERS.clear(HubBoards::remove);
			build(cfg, hub);
		}
		boolean active = hub != null && HubWorld.ready() && !WALLS.isEmpty();
		List<HubViewers.Viewer> fresh = VIEWERS.update(server, hub, active, HubBoards::remove);
		boolean second = ticks % 20 == 0;
		if (!active || VIEWERS.isEmpty() || (!second && fresh.isEmpty())) {
			return;
		}
		int cycle = cfg.leaderboards.cycleSeconds * 20;
		for (Wall w : WALLS) {
			int scope = (int) ((ticks / cycle) % w.scopes.size());
			if (scope != w.scope || w.rankings.isEmpty() || ticks % RANK_TICKS == 0) {
				w.scope = scope;
				rank(w, cfg);
			}
		}
		for (HubViewers.Viewer v : VIEWERS.all()) {
			boolean isNew = fresh.contains(v);
			if (isNew) {
				spawn(v.player, cfg);
			}
			if (isNew || second) {
				update(v, cfg);
			}
		}
	}

	/** Places the boards again on the next tick (after a config change in code, or a lobby rebuild). */
	public static void rebuild() {
		builtFor = null;
	}

	public static void clear() {
		WALLS.clear();
		VIEWERS.forget();
		builtFor = null;
	}

	private static EntityType<?> textDisplay() {
		return ClientEntities.type("minecraft:text_display");
	}

	private static void build(HubConfig cfg, ServerLevel hub) {
		WALLS.clear();
		builtFor = cfg;
		EntityType<?> type = textDisplay();
		if (hub == null || type == null || !cfg.leaderboards.enabled) {
			return;
		}
		BlockPos o = HubWorld.origin();
		for (HubConfig.BoardWall wc : cfg.leaderboards.walls) {
			double start = quadrant(wc.side);
			if (Double.isNaN(start)) {
				BattleHub.LOGGER.warn("Leaderboards: unknown side '{}' (use northwest, northeast, southwest or southeast)", wc.side);
				continue;
			}
			Wall w = new Wall(wc.ranked);
			for (String id : wc.modes) {
				BoardStats.Scope s = BoardStats.scope(id);
				if (s == null) {
					BattleHub.LOGGER.warn("Leaderboards: unknown mode '{}'", id);
				} else {
					w.scopes.add(s);
				}
			}
			List<BoardStats.Stat> stats = new ArrayList<>();
			for (String id : wc.stats) {
				BoardStats.Stat st = BoardStats.Stat.byId(id);
				if (st == null) {
					BattleHub.LOGGER.warn("Leaderboards: unknown stat '{}' (use kills, deaths, kd, coins, wins or elo)", id);
				} else if (stats.size() < MAX_BOARDS) {
					stats.add(st);
				}
			}
			if (w.scopes.isEmpty() || stats.isEmpty()) {
				continue;
			}
			// Evenly over the quarter circle between two bridges, left to right as seen from the fountain
			double step = (90 - 2 * MARGIN) / stats.size();
			for (int i = 0; i < stats.size(); i++) {
				double a = Math.toRadians(start + MARGIN + step * (i + 0.5));
				double x = o.getX() + 0.5 + Math.cos(a) * RADIUS;
				double z = o.getZ() + 0.5 + Math.sin(a) * RADIUS;
				// A fixed text faces where its yaw points: towards the fountain
				float yaw = (float) Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a)));
				// Only for a unique network id: this entity is never added to the world
				ClientEntities.Handle h = ClientEntities.handle(hub, type);
				if (h == null) {
					return;
				}
				w.boards.add(new Board(h.id(), h.uuid(), stats.get(i), new Vec3(x, o.getY() + HEIGHT, z), yaw));
			}
			WALLS.add(w);
		}
	}

	/** Start of the quarter circle in degrees, where 0 is east and 90 is south (as x = cos, z = sin). */
	static double quadrant(String side) {
		String s = side == null ? "" : side.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
		return switch (s) {
			case "southeast", "se", "zuidoost", "zo" -> 0;
			case "southwest", "sw", "zuidwest", "zw" -> 90;
			case "northwest", "nw", "noordwest" -> 180;
			case "northeast", "ne", "noordoost", "no" -> 270;
			default -> Double.NaN;
		};
	}

	private static void rank(Wall w, HubConfig cfg) {
		w.rankings.clear();
		BoardStats.Scope scope = w.scopes.get(w.scope);
		List<PlayerProfile> players = new ArrayList<>();
		for (PlayerProfile p : PlayerStore.all()) {
			if (p.name != null && !p.name.isBlank()) {
				players.add(p);
			}
		}
		for (Board b : w.boards) {
			if (w.rankings.containsKey(b.stat)) {
				continue;
			}
			Map<PlayerProfile, Long> values = new IdentityHashMap<>();
			for (PlayerProfile p : players) {
				values.put(p, BoardStats.value(p, b.stat, scope, w.ranked, cfg.leaderboards.minKillsForKd));
			}
			List<Leaderboard.Row<PlayerProfile>> rows = Leaderboard.top(players, values::get, Integer.MAX_VALUE, b.stat.floor());
			Map<String, Leaderboard.Row<PlayerProfile>> byId = new HashMap<>();
			for (Leaderboard.Row<PlayerProfile> r : rows) {
				byId.put(r.item().uuid, r);
			}
			w.rankings.put(b.stat, new Ranking(rows, byId));
		}
	}

	private static void spawn(ServerPlayer p, HubConfig cfg) {
		EntityType<?> type = textDisplay();
		float scale = (float) cfg.leaderboards.scale;
		for (Wall w : WALLS) {
			for (Board b : w.boards) {
				ClientEntities.add(p, new ClientEntities.Handle(b.entityId, b.uuid, null), type, b.pos, b.yaw, b.yaw);
				ClientEntities.textStyle(p, b.entityId, LINE_WIDTH, BACKGROUND, ClientEntities.SHADOW, scale, ClientEntities.BILLBOARD_FIXED);
			}
		}
	}

	/** Sends the boards whose text changed for this player. */
	private static void update(HubViewers.Viewer v, HubConfig cfg) {
		for (Wall w : WALLS) {
			for (Board b : w.boards) {
				Component text = text(v.player, w, b, cfg);
				if (!text.equals(v.sent.get(b.entityId))) {
					v.sent.put(b.entityId, text);
					ClientEntities.text(v.player, b.entityId, text);
				}
			}
		}
	}

	private static void remove(ServerPlayer p) {
		ClientEntities.remove(p, ids());
	}

	private static int[] ids() {
		List<Integer> l = new ArrayList<>();
		for (Wall w : WALLS) {
			for (Board b : w.boards) {
				l.add(b.entityId);
			}
		}
		return l.stream().mapToInt(Integer::intValue).toArray();
	}

	private static Component text(ServerPlayer p, Wall w, Board b, HubConfig cfg) {
		BoardStats.Scope scope = w.scopes.get(Math.max(0, w.scope));
		Ranking ranking = w.rankings.get(b.stat);
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.tr(p, key("boards.stat.", b.stat, scope)));
		lines.add(w.ranked ? Lang.tr(p, "boards.ranked", scopeName(p, scope)) : scopeName(p, scope));
		lines.add(Lang.tr(p, key("boards.desc.", b.stat, scope), cfg.leaderboards.minKillsForKd));
		lines.add(Component.empty());
		String self = p.getUUID().toString();
		for (int i = 0; i < cfg.leaderboards.rows; i++) {
			if (ranking != null && i < ranking.rows().size()) {
				Leaderboard.Row<PlayerProfile> row = ranking.rows().get(i);
				String key = self.equals(row.item().uuid) ? "boards.row_self" : "boards.row";
				lines.add(Lang.tr(p, key, place(row.rank()), row.item().name, value(p, b.stat, row.value())));
			} else {
				lines.add(Lang.tr(p, "boards.row_empty", i + 1));
			}
		}
		lines.add(Component.empty());
		Leaderboard.Row<PlayerProfile> mine = ranking == null ? null : ranking.byId().get(self);
		lines.add(mine == null ? Lang.tr(p, "boards.you_none")
				: Lang.tr(p, "boards.you", place(mine.rank()), value(p, b.stat, mine.value())));
		if (w.scopes.size() > 1) {
			lines.add(dots(w));
		}
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				out.append("\n");
			}
			out.append(lines.get(i));
		}
		return out;
	}

	/** A text for this stat in this mode when there is one ("boards.stat.wins.parkour"), else the general one. */
	private static String key(String prefix, BoardStats.Stat stat, BoardStats.Scope scope) {
		String special = prefix + stat.id() + "." + scope.id();
		return Lang.has(special) ? special : prefix + stat.id();
	}

	private static Component scopeName(ServerPlayer p, BoardStats.Scope scope) {
		GameMode m = scope.modes().size() == 1 && !scope.everything() ? scope.modes().get(0) : null;
		return m != null ? Lang.tr(p, Icons.modeKey(m)) : Lang.tr(p, "boards.scope." + scope.id());
	}

	/** #1 gold, #2 silver, #3 bronze, the rest grey. */
	private static Component place(int rank) {
		int color = switch (rank) {
			case 1 -> 0xFFD700;
			case 2 -> 0xD8D8D8;
			case 3 -> 0xCD7F32;
			default -> 0x8A8A8A;
		};
		return Component.literal("#" + rank).withStyle(s -> s.withColor(TextColor.fromRgb(color)));
	}

	private static Component value(ServerPlayer p, BoardStats.Stat stat, long v) {
		String shown = BoardStats.format(stat, v);
		if (stat == BoardStats.Stat.ELO) {
			return Lang.tr(p, "boards.elo_value", shown, Ranks.tierName(p, (int) v));
		}
		return Component.literal(shown);
	}

	/** Which mode of the wall is shown: one dot per mode. */
	private static Component dots(Wall w) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < w.scopes.size(); i++) {
			int color = i == w.scope ? 0xFFFFFF : 0x555555;
			out.append(Component.literal(i == 0 ? "●" : " ●").withStyle(s -> s.withColor(TextColor.fromRgb(color))));
		}
		return out;
	}

	// For the self-test

	public static int boardCount() {
		return ids().length;
	}

	public static Set<Integer> entityIds() {
		Set<Integer> s = new HashSet<>();
		for (int id : ids()) {
			s.add(id);
		}
		return s;
	}

	public static boolean showing(UUID viewer) {
		return VIEWERS.get(viewer) != null;
	}

	/** The text this player was sent for a board (wall and board in config order), or "" when none. */
	public static String shown(UUID viewer, int wall, int board) {
		HubViewers.Viewer v = VIEWERS.get(viewer);
		if (v == null || wall >= WALLS.size() || board >= WALLS.get(wall).boards.size()) {
			return "";
		}
		Component c = v.sent.get(WALLS.get(wall).boards.get(board).entityId);
		return c == null ? "" : c.getString();
	}

	/** Board position and yaw, for the layout test. */
	public static Vec3 position(int wall, int board) {
		return WALLS.get(wall).boards.get(board).pos;
	}

	public static float yaw(int wall, int board) {
		return WALLS.get(wall).boards.get(board).yaw;
	}
}
