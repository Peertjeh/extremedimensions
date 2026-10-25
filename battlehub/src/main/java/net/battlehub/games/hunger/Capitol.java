package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.battlehub.core.Loot;
import net.battlehub.core.hunger.HgSchedule;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Everything that makes a Hunger Games match the real thing, for one match: the tributes and their nights, the
 * Gamemakers, the sponsors, the locked loot and the power-ups. The match hands its moments over (the start, every
 * tick, a hit, a kill, a click on a block, the use of an item, the end).
 */
public final class Capitol {
	private final HungerHost host;
	private final Tributes tributes;
	private final Sponsors sponsors;
	private final Vaults vaults;
	private final Gamemakers gamemakers;
	private final PowerUps powers;

	public Capitol(HungerHost host, long seed, int graceTicks, int limitTicks, int feastTick) {
		this.host = host;
		tributes = new Tributes(host);
		sponsors = new Sponsors(host);
		vaults = new Vaults(host, this);
		gamemakers = new Gamemakers(host, this, HgSchedule.plan(seed, graceTicks, limitTicks, feastTick));
		powers = new PowerUps(host, gamemakers);
	}

	public Tributes tributes() {
		return tributes;
	}

	public Sponsors sponsors() {
		return sponsors;
	}

	public Vaults vaults() {
		return vaults;
	}

	public Gamemakers gamemakers() {
		return gamemakers;
	}

	public PowerUps powers() {
		return powers;
	}

	// ------------------------------------------------------------------ the match's moments

	/** The city is built and its chests filled: pick and fill the vaults, crates and the Capitol's chest. */
	public void arenaReady() {
		vaults.setUp();
	}

	/**
	 * The countdown begins: the reaping while the tributes stand on their plates, and the opening music, timed so that
	 * it ends as the countdown does (a shorter countdown starts it part way).
	 */
	public void opening(List<ServerPlayer> players, int countdownTicks) {
		tributes.reap(players, countdownTicks);
		int len = net.battlehub.fx.Music.length(net.battlehub.fx.Music.OPENING);
		if (countdownTicks >= len) {
			net.battlehub.util.Tasks.later(countdownTicks - len, () -> {
				if (host.live() && host.runTicks() == 0) net.battlehub.fx.Music.play(host.audience(), net.battlehub.fx.Music.OPENING);
			});
		} else {
			for (ServerPlayer p : host.audience()) net.battlehub.fx.Music.play(p, net.battlehub.fx.Music.OPENING, len - countdownTicks);
		}
	}

	/** Zero: the gong, the games begin, and the bloodbath. */
	public void go() {
		for (ServerPlayer p : host.audience()) {
			net.battlehub.util.Msg.title(p, net.battlehub.lang.Lang.tr(p, "hg.begin", tributes.number()), net.battlehub.lang.Lang.tr(p, "hg.begin.sub"),
					0, 50, 15);
		}
		net.battlehub.fx.Music.play(host.audience(), net.battlehub.fx.Music.GONG);
		net.battlehub.util.Tasks.later(50, () -> {
			if (host.live()) net.battlehub.fx.Music.play(host.audience(), net.battlehub.fx.Music.BLOODBATH);
		});
	}

	/** The match is over: the victor's fanfare for everyone (nothing after a draw). */
	public void finished(List<java.util.UUID> winners) {
		if (!winners.isEmpty()) net.battlehub.fx.Music.play(host.audience(), net.battlehub.fx.Music.VICTORY);
	}

	public void tick() {
		// the night's tribute plays high over the plaza; the game goes on meanwhile
		tributes.ceremony().tick();
		gamemakers.tick();
		sponsors.tick();
		vaults.tick();
		powers.tick();
		tributes.tick();
	}

	/** Whether the night's tribute is going on (over the plaza; the game goes on). */
	public boolean honouring() {
		return tributes.honouring();
	}

	public void hit(ServerPlayer attacker, float amount) {
		sponsors.hit(attacker.getUUID(), amount);
	}

	/** A tribute fell: the killer wins favour and a vault key, and the fallen will shine in the sky. */
	public void killed(ServerPlayer victim, ServerPlayer killer) {
		tributes.fell(victim.getUUID());
		if (killer != null && killer != victim) {
			sponsors.killed(killer.getUUID());
			ItemStack key = PowerUps.stack(host.level().getServer(), "key", 1);
			if (!killer.getInventory().add(key)) {
				var drop = new net.minecraft.world.entity.item.ItemEntity(host.level(), killer.getX(), killer.getY() + 0.5, killer.getZ(), key);
				host.level().addFreshEntity(drop);
			}
			net.battlehub.util.Msg.actionBar(killer, "hg.key.from_kill");
		}
	}

	/** A click on a block: locked loot, Feast chests. PASS when the game may go on as usual. */
	public InteractionResult useBlock(ServerPlayer p, BlockPos pos, ItemStack held) {
		InteractionResult r = vaults.use(p, pos, held);
		if (r != InteractionResult.PASS) return r;
		return InteractionResult.PASS;
	}

	/** A right-click with an item: power-ups. True when it was used up here. */
	public boolean useItem(ServerPlayer p, ItemStack held) {
		return powers.use(p, held);
	}

	/** A block put down: a landmine is armed. */
	public void placed(ServerPlayer p, BlockPos pos, ItemStack held) {
		if ("mine".equals(PowerUps.id(held))) powers.placed(pos, p.getUUID());
	}

	public void broken(BlockPos pos) {
		powers.broken(pos);
	}

	public void end() {
		gamemakers.clear();
		sponsors.clear();
		vaults.clear();
		powers.clear();
		tributes.clear();
	}

	// ------------------------------------------------------------------ loot

	/**
	 * Items for a chest from a loot table: plain items, and "power:&lt;id&gt;" for a power-up ("power:key" a vault key).
	 * {@code server} may be null (no enchanted spear then).
	 */
	public static List<ItemStack> roll(MinecraftServer server, List<Loot.Entry> table, int rolls, Random rng) {
		List<ItemStack> out = new ArrayList<>();
		for (Loot.Pick pick : Loot.roll(table, rolls, rng)) {
			if (pick.item().startsWith("power:")) {
				ItemStack st = PowerUps.stack(server, pick.item().substring(6), pick.count());
				if (!st.isEmpty()) out.add(st);
				continue;
			}
			Item item = Ids.item(pick.item());
			if (item == Items.AIR) continue;
			out.add(new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), pick.count())));
		}
		return out;
	}

	// ------------------------------------------------------------------ bots

	/** Where a bot should go for the Capitol's gifts: its own parachute, the Feast, a vault it holds a key for. */
	public BlockPos botTarget(ServerPlayer bot) {
		BlockPos best = null;
		double bd = 40;
		for (var e : sponsors.landed().entrySet()) {
			UUID who = e.getValue();
			if (who != null && !who.equals(bot.getUUID())) continue;
			if (!(host.level().getBlockEntity(e.getKey()) instanceof net.minecraft.world.Container c) || c.isEmpty()) continue;
			double d = Math.sqrt(e.getKey().distToCenterSqr(bot.position()));
			if (d < bd) {
				bd = d;
				best = e.getKey();
			}
		}
		if (best != null) return best;
		List<BlockPos> feast = vaults.feastChests();
		for (BlockPos pos : feast) {
			double d = Math.sqrt(pos.distToCenterSqr(bot.position()));
			if (d < 60 && (best == null || d < bd)) {
				bd = d;
				best = pos;
			}
		}
		if (best != null) return best;
		if (hasKey(bot)) return vaults.nearestVault(bot.position(), 50);
		return null;
	}

	static boolean hasKey(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (PowerUps.isKey(p.getInventory().getItem(i))) return true;
		}
		return false;
	}

	/** A bot with a key at a locked vault opens it (the key used up); true when it did. */
	public boolean botUnlock(ServerPlayer bot, BlockPos pos) {
		if (!vaults.isVault(pos) || !vaults.locked(pos)) return false;
		for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
			ItemStack st = bot.getInventory().getItem(i);
			if (PowerUps.isKey(st)) {
				st.shrink(1);
				vaults.unlockVault(pos, bot);
				return true;
			}
		}
		return false;
	}

	/** A hurt bot drinks a salve, a hungry one eats District 11 bread (true when it used one). */
	public boolean botUsePower(ServerPlayer bot) {
		boolean low = bot.getHealth() < bot.getMaxHealth() * 0.4, hungry = bot.getFoodData().getFoodLevel() < 10;
		for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
			ItemStack st = bot.getInventory().getItem(i);
			String id = PowerUps.id(st);
			if (id == null) continue;
			if ((low && "salve".equals(id)) || (hungry && "bread".equals(id)) || (low && "camo".equals(id))) {
				return powers.use(bot, st);
			}
		}
		return false;
	}
}
