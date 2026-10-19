package net.battlehub.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.core.GameMode;
import net.battlehub.games.bedwars.BuildRules;
import net.battlehub.lang.Lang;
import net.battlehub.match.Kits;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Bridge for more players: 2v2 (red against blue, two on each island) or 1v1v1v1 (everyone on their own
 * island, red, blue, lime and yellow). Build across with clay and jump into any other team's goal to score for
 * your team; after a goal everyone goes back home. Dying only sends you home. First team to the goal target wins;
 * when time is up, the team with the most goals.
 */
public final class BridgePartyMatch extends PartyMatch {
	private static final int[] COLORS = {0xB02E26, 0x3C44AA, 0x80C71F, 0xFED83D};

	private final List<List<UUID>> teams = new ArrayList<>();
	private final Map<UUID, Integer> teamOf = new HashMap<>();
	private final int[] goals;
	private BuildRules build;
	private int goalsScored;

	public BridgePartyMatch(int id, GameMode mode, MinecraftServer server, List<ServerPlayer> players) {
		super(id, mode, server, players);
		int count = mode == GameMode.BRIDGE_DUOS ? 2 : Math.max(2, Math.min(4, participants.size()));
		List<List<UUID>> split = mode == GameMode.BRIDGE_DUOS
				? net.battlehub.core.TeamSplit.split(participants, 2, net.battlehub.social.Groups.among(participants), new java.util.Random(random.nextLong()))
				: one(participants);
		for (int i = 0; i < count; i++) {
			List<UUID> t = i < split.size() ? new ArrayList<>(split.get(i)) : new ArrayList<>();
			teams.add(t);
			for (UUID u : t) teamOf.put(u, i);
		}
		goals = new int[count];
	}

	/** Everyone a team of their own. */
	private static List<List<UUID>> one(List<UUID> players) {
		List<List<UUID>> out = new ArrayList<>();
		for (UUID u : players) out.add(new ArrayList<>(List.of(u)));
		return out;
	}

	private int goalsToWin() {
		return (int) Math.max(1, game().setting("goalsToWin", 5));
	}

	public int teamCount() {
		return teams.size();
	}

	public int team(UUID id) {
		return teamOf.getOrDefault(id, 0);
	}

	public int goals(int team) {
		return team >= 0 && team < goals.length ? goals[team] : 0;
	}

	private int perTeam() {
		int most = 1;
		for (List<UUID> t : teams) most = Math.max(most, t.size());
		return most;
	}

	@Override
	protected Blueprint design(Theme theme) {
		return NewLayouts.bridgeRing(theme, random, teams.size(), perTeam());
	}

	@Override
	protected Arena.Point spawnPoint(UUID id, int index) {
		int t = team(id);
		int k = Math.max(0, teams.get(t).indexOf(id));
		return arena.spawn(t * perTeam() + k);
	}

	@Override
	protected int leatherColor(UUID id) {
		return COLORS[team(id) % COLORS.length];
	}

	private String color(int team) {
		return NewLayouts.BRIDGE_COLORS[team % NewLayouts.BRIDGE_COLORS.length];
	}

	/** "Red", "Blue", ... in the team colour; in 1v1v1v1 the player's name in their colour. */
	public Component teamName(ServerPlayer viewer, int team) {
		String code = switch (team) {
			case 0 -> "&c";
			case 1 -> "&9";
			case 2 -> "&a";
			default -> "&e";
		};
		if (mode == GameMode.BRIDGE_FOUR && !teams.get(team).isEmpty()) {
			return Lang.colored(code + name(teams.get(team).get(0)));
		}
		return Lang.colored(code).append(Lang.tr(viewer, "bridge.team." + color(team)));
	}

	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		p.setGameMode(GameType.SURVIVAL);
	}

	@Override
	protected void onArenaReady() {
		build = new BuildRules(arena.level(), arena.mark("build_min"), arena.mark("build_max"), 1).area(arena::inPlayArea);
		for (int i = 0; i < teams.size(); i++) {
			build.protect(arena.mark("goal_" + i));
		}
		for (int i = 0; i < arena.spawnCount(); i++) {
			build.protect(BlockPos.containing(arena.spawn(i).pos()));
		}
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		Msg.send(p, "bridge.party_team", teamName(p, team(p.getUUID())), goalsToWin());
	}

	/** The kit's clay comes in the team colour (and follows the player's own kit layout). */
	@Override
	protected String kitTeam(UUID id) {
		int t = team(id);
		return t < 0 ? null : color(t);
	}

	@Override
	protected int countdownSeconds() {
		return goalsScored > 0 ? 3 : super.countdownSeconds();
	}

	@Override
	protected Component countdownSubtitle(ServerPlayer p) {
		return goalsScored == 0 ? super.countdownSubtitle(p) : score(p);
	}

	/** "Red 2 · Blue 1 · ..." */
	public Component score(ServerPlayer viewer) {
		MutableComponent c = Component.empty();
		for (int i = 0; i < teams.size(); i++) {
			if (i > 0) c.append(Lang.colored(" &8· "));
			c.append(teamName(viewer, i)).append(Lang.colored(" &f" + goals[i]));
		}
		return c;
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return attacker == null || attacker == victim || team(attacker.getUUID()) != team(victim.getUUID());
	}

	@Override
	public boolean isEnemy(UUID a, UUID b) {
		return team(a) != team(b);
	}

	@Override
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		if (phase != Phase.RUNNING || !active(p.getUUID()) || build == null || !(held.getItem() instanceof BlockItem)) {
			return false;
		}
		if (build.mayPlace(target) != BuildRules.Verdict.OK) {
			Msg.actionBar(p, "bridge.cannot_build");
			return false;
		}
		build.expect(target, runTicks);
		return true;
	}

	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		if (phase == Phase.RUNNING && build != null && build.isPlaced(pos)) {
			build.forget(pos);
			return true;
		}
		return false;
	}

	/** Dying only sends you home with a fresh kit. */
	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		Arena.Point sp = spawnOf.get(victim.getUUID());
		resetForPlay(victim);
		if (sp != null) {
			teleport(victim, sp);
		}
		Kits.give(victim, kit(), leatherColor(victim.getUUID()), kitTeam(victim.getUUID()));
		afterKit(victim);
	}

	@Override
	protected void tickGame() {
		if (build != null) {
			build.tick(runTicks);
		}
		for (UUID id : List.copyOf(alive)) {
			ServerPlayer p = player(id);
			if (p == null || phase != Phase.RUNNING) continue;
			int own = team(id);
			for (int t = 0; t < teams.size(); t++) {
				if (t == own) continue;
				BlockPos g = arena.mark("goal_" + t);
				if (Math.abs(p.getX() - (g.getX() + 0.5)) <= 1.6 && Math.abs(p.getZ() - (g.getZ() + 0.5)) <= 1.6
						&& p.getY() < g.getY() + 0.3 && p.getY() > g.getY() - 5) {
					scored(id, own);
					return;
				}
			}
		}
		if (runTicks % 20 == 0) {
			forEveryone(o -> Msg.actionBar(o, "bridge.score_bar", score(o), goalsToWin()));
		}
	}

	/** A goal for the scorer's team: everyone back home, or the match is won. */
	void scored(UUID scorer, int team) {
		goals[team]++;
		goalsScored++;
		forEveryone(o -> {
			Msg.send(o, "bridge.party_goal", name(scorer), teamName(o, team), score(o));
			Msg.sound(o, SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 1f);
		});
		if (goals[team] >= goalsToWin()) {
			finish(new ArrayList<>(teams.get(team)));
			return;
		}
		restartRound();
	}

	/** The match is over when only one team still has players. */
	@Override
	protected void checkEnd() {
		int withPlayers = 0;
		int last = -1;
		for (int t = 0; t < teams.size(); t++) {
			for (UUID u : teams.get(t)) {
				if (alive.contains(u)) {
					withPlayers++;
					last = t;
					break;
				}
			}
		}
		if (withPlayers <= 1) {
			finish(last < 0 ? List.of() : new ArrayList<>(teams.get(last)));
		}
	}

	@Override
	protected void onTimeUp() {
		int best = -1;
		boolean tie = false;
		for (int t = 0; t < teams.size(); t++) {
			if (best < 0 || goals[t] > goals[best]) {
				best = t;
				tie = false;
			} else if (goals[t] == goals[best]) {
				tie = true;
			}
		}
		finish(best < 0 || tie ? List.of() : new ArrayList<>(teams.get(best)));
	}

	@Override
	public boolean botMayBuild(BlockPos pos) {
		return build != null && build.mayPlace(pos) == BuildRules.Verdict.OK;
	}

	@Override
	public boolean botMayBreak(BlockPos pos) {
		return build != null && build.isPlaced(pos);
	}

	/** In a team of two or more one bot keeps the own goal; the others go for the nearest goal of another team. */
	@Override
	public net.battlehub.core.ai.Objective botGoal(ServerPlayer bot) {
		if (arena == null) return null;
		int own = team(bot.getUUID());
		if (own >= 0) {
			Vec3 home = Vec3.atBottomCenterOf(arena.mark("goal_" + own));
			if (botRole(bot, true, home) == net.battlehub.core.ai.Roles.Role.DEFENDER) {
				return net.battlehub.core.ai.Objective.defend(v3(home), 2.5, 9, "our goal");
			}
		}
		Vec3 g = botObjective(bot);
		return g == null ? null : net.battlehub.core.ai.Objective.attack(v3(g), "their goal");
	}

	/** Bots head for the nearest goal of another team. */
	@Override
	public Vec3 botObjective(ServerPlayer bot) {
		if (arena == null) return null;
		int own = team(bot.getUUID());
		Vec3 best = null;
		for (int t = 0; t < teams.size(); t++) {
			if (t == own) continue;
			Vec3 g = Vec3.atBottomCenterOf(arena.mark("goal_" + t));
			if (best == null || g.distanceToSqr(bot.position()) < best.distanceToSqr(bot.position())) best = g;
		}
		return best;
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return score(viewer);
	}
}
