package net.battlehub.games;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.core.GameMode;
import net.battlehub.core.chaos.ChaosCatalog;
import net.battlehub.core.chaos.ChaosEvent;
import net.battlehub.core.chaos.ChaosPlan;
import net.battlehub.games.bedwars.BuildRules;
import net.battlehub.games.duel.BridgeChaosMatch;
import net.battlehub.games.duel.ChaosAnnounce;
import net.battlehub.games.duel.ChaosDirector;
import net.battlehub.games.duel.ChaosHost;
import net.battlehub.match.Kits;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Bridge 1v1v1v1: Chaos. Everyone on their own island round the middle platform, as in the 1v1v1v1, plus what
 * makes Chaos: a power-up on the middle every few seconds and an event for everyone every few seconds, the same
 * events (and their worse second and third rounds) as The Bridge: Chaos.
 */
public final class BridgeChaosPartyMatch extends BridgePartyMatch implements ChaosHost {
	private int powerupIn = -1;
	private int eventIn = -1;
	private String lastEvent = "";
	private ChaosDirector chaos;

	public BridgeChaosPartyMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.BRIDGE_FOUR_CHAOS, server, players);
	}

	private int ticks(String setting, double fallback, int least) {
		return (int) Math.max(least, game().setting(setting, fallback)) * 20;
	}

	/** The events of this match (made on first use, with at least twenty random ones). */
	public ChaosDirector chaos() {
		if (chaos == null) {
			chaos = new ChaosDirector(this, random.nextLong(), (int) Math.max(20, game().setting("randomEvents", 20)));
		}
		return chaos;
	}

	public String lastEvent() {
		return lastEvent;
	}

	@Override
	protected int goalValue() {
		return chaos().goalMultiplier();
	}

	@Override
	protected void afterGoal() {
		chaos().goalScored();
	}

	/** One wind charge on top of the Bridge kit. */
	@Override
	protected void afterKit(ServerPlayer p) {
		super.afterKit(p);
		p.getInventory().add(new ItemStack(Ids.item("minecraft:wind_charge"), 1));
		p.inventoryMenu.sendAllDataToRemote();
	}

	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		ChaosDirector.strip(p);
	}

	@Override
	protected void onRestored(ServerPlayer p) {
		super.onRestored(p);
		ChaosDirector.strip(p);
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		super.onKilled(victim, killer);
		chaos().reapply(victim);
	}

	@Override
	protected void onClosed() {
		super.onClosed();
		chaos().finishAll(true);
	}

	@Override
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		if (chaos().noBuild()) {
			Msg.actionBar(p, "bridge_chaos.no_build");
			return false;
		}
		return super.onPlaceBlock(p, target, held);
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return !(chaos().noPvp() && attacker != null && attacker != victim) && super.damageAllowed(victim, source, attacker);
	}

	@Override
	protected void tickGame() {
		super.tickGame();
		if (phase != Phase.RUNNING || arena == null) {
			return;
		}
		chaos().tick();
		if (powerupIn < 0) powerupIn = ticks("powerupSeconds", 12, 3) / 2;
		if (eventIn < 0) eventIn = ticks("firstEventSeconds", 6, 1);
		if (--powerupIn <= 0) {
			powerupIn = ticks("powerupSeconds", 12, 3);
			dropPowerup(BridgeChaosMatch.POWERUPS.get(random.nextInt(BridgeChaosMatch.POWERUPS.size())));
		}
		if (--eventIn <= 0) {
			eventIn = ticks("eventSeconds", 10, 3);
			ChaosPlan.Turn turn = chaos().next();
			start(ChaosCatalog.get(turn.id()), turn.level());
		}
		if (runTicks % 10 == 0) {
			Vec3 mid = Vec3.atBottomCenterOf(arena.origin()).add(0, 1.2, 0);
			arena.level().sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 2, 0.6, 0.3, 0.6, 0.01);
		}
	}

	public void dropPowerup(String kind) {
		BridgeChaosMatch.dropPowerup(this, kind);
	}

	/** An event by id, {@code level} times round worse, right now (tests, admins). */
	public void event(String id, int level) {
		start(ChaosCatalog.get(id), level);
	}

	private void start(ChaosEvent e, int level) {
		lastEvent = e.id();
		chaos().start(e, level);
		ChaosAnnounce.event(this::forEveryone, e, level, chaos().plan().count(), chaos().plan().planned());
	}

	// ------------------------------------------------------------------ for the director

	@Override
	public List<ServerPlayer> inPlay() {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p != null) out.add(p);
		}
		return out;
	}

	@Override
	public ServerLevel level() {
		return arena.level();
	}

	@Override
	public BuildRules buildRules() {
		return build;
	}

	@Override
	public BlockPos middle() {
		return arena.origin();
	}

	@Override
	public RandomSource rng() {
		return random;
	}

	@Override
	public MinecraftServer srv() {
		return server;
	}

	@Override
	public void everyone(java.util.function.Consumer<ServerPlayer> action) {
		forEveryone(action);
	}

	/** How far a spot is from the middle platform (the islands lie round it). */
	@Override
	public int fromMiddle(BlockPos p) {
		BlockPos o = arena.origin();
		return (int) Math.round(Math.hypot(p.getX() - o.getX(), p.getZ() - o.getZ()));
	}

	/** Straight from every goal to the middle, at the height of the islands (a cross of glass or ice bridges). */
	@Override
	public List<BlockPos> bridgeLine() {
		List<BlockPos> out = new ArrayList<>();
		if (arena.spawnCount() == 0) return out;
		int y = (int) Math.floor(arena.spawn(0).pos().y) - 1;
		BlockPos o = arena.origin();
		for (int t = 0; t < teamCount(); t++) {
			List<BlockPos> goal = arena.marks("goal_" + t);
			if (goal.isEmpty()) continue;
			BlockPos g = goal.get(0);
			int steps = Math.max(Math.abs(g.getX() - o.getX()), Math.abs(g.getZ() - o.getZ()));
			for (int i = 0; i <= steps; i++) {
				double f = steps == 0 ? 0 : i / (double) steps;
				BlockPos at = new BlockPos((int) Math.round(g.getX() + (o.getX() - g.getX()) * f), y, (int) Math.round(g.getZ() + (o.getZ() - g.getZ()) * f));
				if (!out.contains(at)) out.add(at);
			}
		}
		return out;
	}

	@Override
	public void sendHome(ServerPlayer p) {
		Arena.Point sp = spawnOf.get(p.getUUID());
		if (sp != null) teleport(p, sp);
	}

	@Override
	public void restock(ServerPlayer p) {
		Kits.give(p, kit(), leatherColor(p.getUUID()), kitTeam(p.getUUID()));
		afterKit(p);
	}
}
