package net.battlehub.games.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.core.GameMode;
import net.battlehub.core.chaos.ChaosEvent;
import net.battlehub.games.bedwars.BuildRules;
import net.battlehub.lang.Lang;
import net.battlehub.match.Kits;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Bridge: Chaos, the fan favourite. Everything of the Bridge, plus: a power-up falls onto the middle every few
 * seconds, and every few seconds an event happens to everyone. Each match plans at least twenty of the hundred
 * events (and always a pearl, low gravity, the bridge crumbling, cobwebs and double points); how many you see
 * depends on how long the match lasts. Never ranked: chance plays too big a part.
 */
public final class BridgeChaosMatch extends BridgeMatch {
	public static final List<String> POWERUPS = List.of("wind", "pearl", "apple", "snowballs", "cobweb", "arrows", "clay", "stick");

	/** Ticks of play until the next power-up and the next event (they keep counting over goals). */
	private int powerupIn = -1;
	private int eventIn = -1;
	private String lastEvent = "";
	private ChaosDirector chaos;

	public BridgeChaosMatch(int id, boolean challenge, MinecraftServer server, ServerPlayer a, ServerPlayer b) {
		super(id, GameMode.BRIDGE_CHAOS, false, challenge, server, a, b);
	}

	private int powerupTicks() {
		return (int) Math.max(3, game().setting("powerupSeconds", 12)) * 20;
	}

	private int eventTicks() {
		return (int) Math.max(3, game().setting("eventSeconds", 10)) * 20;
	}

	private int firstEventTicks() {
		return (int) Math.max(1, game().setting("firstEventSeconds", 6)) * 20;
	}

	/** The events of this match (made on first use, with at least twenty random ones). */
	public ChaosDirector chaos() {
		if (chaos == null) {
			chaos = new ChaosDirector(this, random.nextLong(), (int) Math.max(20, game().setting("randomEvents", 20)));
		}
		return chaos;
	}

	public boolean doubleGoal() {
		return chaos().goalMultiplier() > 1;
	}

	public String lastEvent() {
		return lastEvent;
	}

	@Override
	protected int goalValue() {
		return chaos().goalMultiplier();
	}

	@Override
	void scored(UUID scorer) {
		super.scored(scorer);
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
		if (powerupIn < 0) powerupIn = powerupTicks() / 2;
		if (eventIn < 0) eventIn = firstEventTicks();
		if (--powerupIn <= 0) {
			powerupIn = powerupTicks();
			dropPowerup(POWERUPS.get(random.nextInt(POWERUPS.size())));
		}
		if (--eventIn <= 0) {
			eventIn = eventTicks();
			start(chaos().next());
		}
		if (runTicks % 10 == 0) {
			Vec3 mid = Vec3.atBottomCenterOf(arena.origin()).add(0, 1.2, 0);
			arena.level().sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 2, 0.6, 0.3, 0.6, 0.01);
		}
	}

	/** The item of a power-up. */
	ItemStack powerupItem(String kind) {
		return switch (kind) {
			case "wind" -> new ItemStack(Ids.item("minecraft:wind_charge"), 2);
			case "pearl" -> new ItemStack(Ids.item("minecraft:ender_pearl"), 1);
			case "apple" -> new ItemStack(Ids.item("minecraft:golden_apple"), 1);
			case "snowballs" -> new ItemStack(Ids.item("minecraft:snowball"), 8);
			case "cobweb" -> new ItemStack(Ids.item("minecraft:cobweb"), 2);
			case "arrows" -> new ItemStack(Ids.item("minecraft:arrow"), 8);
			case "clay" -> new ItemStack(Ids.item("minecraft:white_terracotta"), 32);
			default -> {
				ItemStack stick = new ItemStack(Ids.item("minecraft:stick"));
				Ids.enchantment(server, "minecraft:knockback").ifPresent(h -> stick.enchant(h, 2));
				yield stick;
			}
		};
	}

	/** A power-up falls onto the middle of the bridge, glowing. */
	public void dropPowerup(String kind) {
		ServerLevel level = arena.level();
		Vec3 mid = Vec3.atBottomCenterOf(arena.origin()).add(0, 3, 0);
		ItemEntity item = new ItemEntity(level, mid.x, mid.y, mid.z, powerupItem(kind));
		item.setDeltaMovement(Vec3.ZERO);
		item.setGlowingTag(true);
		level.addFreshEntity(item);
		level.sendParticles(ParticleTypes.FIREWORK, mid.x, mid.y, mid.z, 20, 0.3, 0.3, 0.3, 0.1);
		forEveryone(o -> {
			Msg.actionBar(o, "bridge_chaos.powerup", Lang.tr(o, "bridge_chaos.powerup." + kind));
			Msg.sound(o, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.6f);
		});
	}

	/** An event by id, right now (tests, admins). */
	public void event(String id) {
		start(net.battlehub.core.chaos.ChaosCatalog.get(id));
	}

	/** Something happens to everyone: shown as a title and a line in chat with how many events this match had. */
	private void start(ChaosEvent e) {
		lastEvent = e.id();
		chaos().start(e);
		int n = chaos().plan().count();
		int planned = chaos().plan().planned();
		forEveryone(o -> {
			Msg.title(o, Lang.tr(o, e.langKey()), Lang.tr(o, e.langKey() + ".sub", e.seconds()), 5, 35, 10);
			Msg.send(o, "bridge_chaos.event_line", Lang.tr(o, e.langKey()), Lang.tr(o, e.langKey() + ".sub", e.seconds()),
					Math.max(1, n), Math.max(n, planned));
			Msg.sound(o, SoundEvents.FIRECHARGE_USE, 0.7f, 1.4f);
		});
	}

	// ------------------------------------------------------------------ for the director

	List<ServerPlayer> inPlay() {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p != null) out.add(p);
		}
		return out;
	}

	ServerLevel level() {
		return arena.level();
	}

	BuildRules buildRules() {
		return build;
	}

	BlockPos middle() {
		return arena.origin();
	}

	RandomSource rng() {
		return random;
	}

	MinecraftServer srv() {
		return server;
	}

	void everyone(java.util.function.Consumer<ServerPlayer> action) {
		forEveryone(action);
	}

	/** Along the line between the goals: does the bridge run along x (or z)? */
	private boolean alongX() {
		BlockPos r = arena.mark("goal_red"), b = arena.mark("goal_blue");
		return Math.abs(b.getX() - r.getX()) >= Math.abs(b.getZ() - r.getZ());
	}

	/** How far a spot is from the middle, along the bridge. */
	int fromMiddle(BlockPos p) {
		BlockPos o = arena.origin();
		return alongX() ? Math.abs(p.getX() - o.getX()) : Math.abs(p.getZ() - o.getZ());
	}

	/** The straight line from goal to goal at the height of the islands (where a glass or ice bridge goes). */
	List<BlockPos> bridgeLine() {
		List<BlockPos> out = new ArrayList<>();
		if (arena.spawnCount() == 0) return out;
		BlockPos r = arena.mark("goal_red"), b = arena.mark("goal_blue");
		int y = (int) Math.floor(arena.spawn(0).pos().y) - 1;
		boolean x = alongX();
		int from = x ? Math.min(r.getX(), b.getX()) : Math.min(r.getZ(), b.getZ());
		int to = x ? Math.max(r.getX(), b.getX()) : Math.max(r.getZ(), b.getZ());
		for (int i = from; i <= to; i++) {
			double t = to == from ? 0 : (i - from) / (double) (to - from);
			boolean redFirst = x ? r.getX() <= b.getX() : r.getZ() <= b.getZ();
			BlockPos start = redFirst ? r : b, end = redFirst ? b : r;
			int side = (int) Math.round(x ? start.getZ() + (end.getZ() - start.getZ()) * t : start.getX() + (end.getX() - start.getX()) * t);
			out.add(x ? new BlockPos(i, y, side) : new BlockPos(side, y, i));
		}
		return out;
	}

	void sendHome(ServerPlayer p) {
		Arena.Point sp = spawnOf.get(p.getUUID());
		if (sp != null) teleport(p, sp);
	}

	void restock(ServerPlayer p) {
		Kits.give(p, kit(), leatherColor(p.getUUID()), kitTeam(p.getUUID()));
		afterKit(p);
	}
}
