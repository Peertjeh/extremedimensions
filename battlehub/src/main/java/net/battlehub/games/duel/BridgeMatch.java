package net.battlehub.games.duel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.games.bedwars.BuildRules;
import net.battlehub.lang.Lang;
import net.battlehub.match.DuelMatch;
import net.battlehub.match.Kits;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Bridge: red against blue, each on an island with a goal. Build across with clay and jump into the other
 * goal to score; after a goal both go back to their island. Dying (or falling) only sends you back home. First
 * to five goals wins; when time is up, more goals wins.
 */
public class BridgeMatch extends DuelMatch {
	private static final int RED = 0xB02E26;
	private static final int BLUE = 0x3C44AA;

	private final Map<UUID, Integer> goals = new HashMap<>();
	protected BuildRules build;
	private int goalsScored;

	public BridgeMatch(int id, boolean ranked, boolean challenge, MinecraftServer server, ServerPlayer a, ServerPlayer b) {
		this(id, GameMode.BRIDGE, ranked, challenge, server, a, b);
	}

	/** For variants of the Bridge (the test mode with power-ups). */
	protected BridgeMatch(int id, GameMode mode, boolean ranked, boolean challenge, MinecraftServer server, ServerPlayer a, ServerPlayer b) {
		super(id, mode, ranked, DuelFormat.BO1, challenge, server, a, b);
	}

	/** How many goals a goal is worth right now. */
	protected int goalValue() {
		return 1;
	}

	private int goalsToWin() {
		return (int) Math.max(1, game().setting("goalsToWin", 5));
	}

	protected boolean red(UUID id) {
		return participants.get(0).equals(id);
	}

	public int goals(UUID id) {
		return goals.getOrDefault(id, 0);
	}

	@Override
	protected Blueprint design(Theme theme) {
		return NewLayouts.bridge(theme, random);
	}


	@Override
	protected int leatherColor(UUID id) {
		return red(id) ? RED : BLUE;
	}

	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		p.setGameMode(GameType.SURVIVAL);
	}

	@Override
	protected void onArenaReady() {
		build = new BuildRules(arena.level(), arena.mark("build_min"), arena.mark("build_max"), 1).area(arena::inPlayArea);
		build.protect(arena.mark("goal_red"));
		build.protect(arena.mark("goal_blue"));
		for (int i = 0; i < Math.min(2, arena.spawnCount()); i++) {
			build.protect(BlockPos.containing(arena.spawn(i).pos()));
		}
	}

	/** The kit's clay comes in the team colour (and follows the player's own kit layout). */
	@Override
	protected String kitTeam(UUID id) {
		return red(id) ? "red" : "blue";
	}

	@Override
	protected int countdownSeconds() {
		return goalsScored > 0 ? 3 : super.countdownSeconds();
	}

	@Override
	protected Component countdownSubtitle(ServerPlayer p) {
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		return goalsScored == 0 ? super.countdownSubtitle(p) : Lang.tr(p, "bridge.score", name(a), goals(a), goals(b), name(b));
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
			BlockPos g = arena.mark(red(id) ? "goal_blue" : "goal_red");
			if (Math.abs(p.getX() - (g.getX() + 0.5)) <= 1.6 && Math.abs(p.getZ() - (g.getZ() + 0.5)) <= 1.6
					&& p.getY() < g.getY() + 0.3 && p.getY() > g.getY() - 5) {
				scored(id);
			}
		}
	}

	/** A goal: everyone back home, or the match is won. */
	void scored(UUID scorer) {
		int n = goals.merge(scorer, goalValue(), Integer::sum);
		goalsScored++;
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		forEveryone(o -> {
			Msg.send(o, "bridge.goal", name(scorer), Lang.tr(o, "bridge.score", name(a), goals(a), goals(b), name(b)));
			Msg.sound(o, SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 1f);
		});
		if (n >= goalsToWin()) {
			ServerPlayer loser = player(other(scorer));
			if (loser != null) {
				eliminate(loser);
			} else {
				alive.remove(other(scorer));
			}
			checkEnd();
			return;
		}
		restartRound();
	}

	@Override
	protected void onTimeUp() {
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		if (goals(a) == goals(b)) {
			finish(List.of());
		} else {
			finish(List.of(goals(a) > goals(b) ? a : b));
		}
	}

	@Override
	public boolean botMayBuild(BlockPos pos) {
		return build != null && build.mayPlace(pos) == BuildRules.Verdict.OK;
	}

	@Override
	public boolean botMayBreak(BlockPos pos) {
		return build != null && build.isPlaced(pos);
	}

	@Override
	public net.battlehub.core.ai.Objective botGoal(ServerPlayer bot) {
		Vec3 g = botObjective(bot);
		return g == null ? null : net.battlehub.core.ai.Objective.attack(v3(g), "their goal");
	}

	/** Bots head for the other goal. */
	@Override
	public Vec3 botObjective(ServerPlayer bot) {
		if (arena == null) return null;
		BlockPos g = arena.mark(red(bot.getUUID()) ? "goal_blue" : "goal_red");
		return Vec3.atBottomCenterOf(g);
	}

	@Override
	public Component status(ServerPlayer viewer) {
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		return Lang.tr(viewer, "bridge.status", name(a), goals(a), goals(b), name(b));
	}
}
