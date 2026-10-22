package net.battlehub.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.Loot;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SkyWars: everyone starts on their own island with two chests; the smaller middle islands have better loot and the
 * super big island in the middle the best. Build, break, fight; whoever dies drops everything. The last one alive
 * wins; when time is up, the most kills among the survivors wins.
 */
public final class SkyWarsMatch extends PartyMatch {
	private final Random loot = new Random();
	private int chestsFilled;

	public SkyWarsMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.SKYWARS, server, players);
	}

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.MELEE;
	}

	/** The map of this match: its islands and its size (1.5 to 2.5 times as wide as the map of 1.29). */
	private net.battlehub.core.SkyWarsPlan plan;
	private double factor;

	@Override
	protected Blueprint design(Theme theme) {
		factor = net.battlehub.core.SkyWarsPlan.factor(random.nextDouble());
		plan = net.battlehub.core.SkyWarsPlan.of(participants.size(), factor);
		net.battlehub.BattleHub.LOGGER.info("SkyWars map for {} players: {} times as wide as before, {} blocks from the middle", participants.size(),
				String.format(java.util.Locale.ROOT, "%.2f", factor), Math.round(plan.outer));
		return NewLayouts.skywars(theme, random, plan);
	}

	public net.battlehub.core.SkyWarsPlan plan() {
		return plan;
	}

	/** How much wider than the map of 1.29 this one is. */
	public double sizeFactor() {
		return factor;
	}


	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		p.setGameMode(GameType.SURVIVAL);
	}

	@Override
	protected void onArenaReady() {
		int islandRolls = (int) Math.max(1, game().setting("islandChestRolls", 7));
		int midRolls = (int) Math.max(1, game().setting("midChestRolls", 8));
		int centerRolls = (int) Math.max(1, game().setting("centerChestRolls", 9));
		// enough blocks to get off: the two chests of a spawn island hold half as much again as the bridge to the
		// nearest middle island is long, those of a middle island as much for the bridge to the middle
		int toMid = 24, toCentre = 12;
		if (plan != null) {
			double nearest = Double.MAX_VALUE;
			for (net.battlehub.core.SkyWarsPlan.Island m : plan.mids) nearest = Math.min(nearest, plan.spawns.get(0).gap(m));
			toMid = (int) Math.ceil(nearest * 0.75);
			toCentre = (int) Math.ceil(plan.mids.get(0).gap(plan.center) * 0.75);
		}
		for (BlockPos pos : arena.marks("chest")) {
			fill(pos, ConfigManager.skywars().island(), islandRolls, Math.max(16, toMid));
		}
		for (BlockPos pos : arena.marks("mid_chest")) {
			fill(pos, ConfigManager.skywars().mid(), midRolls, Math.max(8, toCentre));
		}
		for (BlockPos pos : arena.marks("center_chest")) {
			fill(pos, ConfigManager.skywars().center(), centerRolls, 0);
		}
	}

	/**
	 * Puts loot in random slots of the chest at {@code pos}, and at least {@code blocks} blocks to build with: the
	 * islands are far apart, without blocks nobody gets off. Short of that, stacks of the list's blocks are added.
	 */
	private void fill(BlockPos pos, List<Loot.Entry> table, int rolls, int blocks) {
		BlockEntity be = arena.level().getBlockEntity(pos);
		if (!(be instanceof Container c)) {
			return;
		}
		c.clearContent();
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) free.add(i);
		java.util.Collections.shuffle(free, loot);
		int n = 0;
		int gotBlocks = 0;
		for (Loot.Pick pick : Loot.roll(table, rolls, loot)) {
			Item item = Ids.item(pick.item());
			if (item == Items.AIR || n >= free.size()) continue;
			ItemStack st = new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), pick.count()));
			if (net.battlehub.bot.Bots.buildBlock(st)) gotBlocks += st.getCount();
			c.setItem(free.get(n++), st);
		}
		if (gotBlocks < blocks) {
			List<Loot.Entry> blockLines = new ArrayList<>();
			for (Loot.Entry e : table) {
				if (net.battlehub.bot.Bots.buildBlock(new ItemStack(Ids.item(e.item())))) blockLines.add(e);
			}
			if (blockLines.isEmpty()) blockLines.add(Loot.Entry.parse("minecraft:oak_planks 32-64"));
			for (int tries = 0; tries < 6 && gotBlocks < blocks && n < free.size(); tries++) {
				for (Loot.Pick pick : Loot.roll(blockLines, 1, loot)) {
					Item item = Ids.item(pick.item());
					int count = Math.min(item.getDefaultMaxStackSize(), Math.max(pick.count(), Math.min(64, blocks - gotBlocks)));
					c.setItem(free.get(n++), new ItemStack(item, count));
					gotBlocks += count;
				}
			}
		}
		c.setChanged();
		chestsFilled++;
	}

	public int chestsFilled() {
		return chestsFilled;
	}

	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		return phase == Phase.RUNNING && active(p.getUUID()) && arena.bounds().contains(Vec3.atCenterOf(pos));
	}

	@Override
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		return phase == Phase.RUNNING && active(p.getUUID()) && arena.bounds().contains(Vec3.atCenterOf(target)) && arena.inPlayArea(target)
				&& (held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem);
	}

	// ------------------------------------------------------------------ bots

	/** Chests a bot has emptied: it does not walk back to them. */
	private final java.util.Map<UUID, java.util.Set<BlockPos>> botLooted = new java.util.HashMap<>();
	private int botLoots;

	/**
	 * A bot loots as a player does: first the chests on its own island, then, with blocks to build with, it bridges
	 * to the nearest chest further in (a middle island, then the middle). Once nothing is left within reach it just
	 * fights (and bridges to whoever it goes after).
	 */
	@Override
	public net.battlehub.core.ai.Objective botGoal(ServerPlayer bot) {
		BlockPos chest = botChest(bot);
		if (chest == null) return null;
		// the spot in front of the chest (its front faces the middle of the island), not the chest itself: walking
		// into a chest makes a bot climb onto it, and the chests on a spawn island stand near its edge
		BlockState st = arena.level().getBlockState(chest);
		BlockPos front = st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
				? chest.relative(st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) : chest;
		return net.battlehub.core.ai.Objective.attack(v3(Vec3.atBottomCenterOf(front)), "a chest");
	}

	/** The nearest full chest it has not emptied yet: on its own island, or anywhere once it has blocks to bridge. */
	private BlockPos botChest(ServerPlayer bot) {
		if (arena == null) return null;
		boolean canBridge = botBlocks(bot) >= 8;
		java.util.Set<BlockPos> done = botLooted.getOrDefault(bot.getUUID(), java.util.Set.of());
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int mine = islandOf(bot.getX(), bot.getZ());
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (done.contains(pos) || !hasLoot(pos)) continue;
				double dx = pos.getX() + 0.5 - bot.getX(), dz = pos.getZ() + 0.5 - bot.getZ();
				double flat = Math.sqrt(dx * dx + dz * dz);
				boolean here = mine >= 0 ? islandOf(pos.getX() + 0.5, pos.getZ() + 0.5) == mine : flat <= 7 && Math.abs(pos.getY() - bot.getY()) <= 3;
				if (!here && !canBridge) continue;
				if (flat < bestD) {
					bestD = flat;
					best = pos;
				}
			}
		}
		return best;
	}

	private static final String[] CHESTS = {"chest", "mid_chest", "center_chest"};

	/** Which island of the plan (spawn islands, then middle islands, then the middle) a world spot is on, or -1. */
	private int islandOf(double x, double z) {
		if (plan == null) return -1;
		double rx = x - arena.origin().getX(), rz = z - arena.origin().getZ();
		java.util.List<net.battlehub.core.SkyWarsPlan.Island> all = new java.util.ArrayList<>(plan.spawns);
		all.addAll(plan.mids);
		all.add(plan.center);
		for (int i = 0; i < all.size(); i++) {
			net.battlehub.core.SkyWarsPlan.Island is = all.get(i);
			if (Math.hypot(rx - Math.round(is.x()), rz - Math.round(is.z())) <= is.r() + 1.5) return i;
		}
		return -1;
	}

	private boolean hasLoot(BlockPos pos) {
		return arena.level().getBlockEntity(pos) instanceof Container c && !c.isEmpty();
	}

	private static int botBlocks(ServerPlayer bot) {
		int n = 0;
		for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
			ItemStack st = bot.getInventory().getItem(i);
			if (net.battlehub.bot.Bots.buildBlock(st)) n += st.getCount();
		}
		return n;
	}

	/**
	 * At a chest (within reach) it opens it and loots it as a player does, a stack at a time, and puts on better
	 * armor afterwards (bot/Looting).
	 */
	@Override
	public void botTick(ServerPlayer bot) {
		if (runTicks % 5 != 0 || arena == null || phase != Phase.RUNNING || net.battlehub.bot.Looting.busy(bot)) return;
		java.util.Set<BlockPos> done = botLooted.computeIfAbsent(bot.getUUID(), k -> new java.util.HashSet<>());
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (done.contains(pos) || bot.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 3.5 * 3.5) continue;
				if (!hasLoot(pos)) {
					done.add(pos);
					continue;
				}
				net.battlehub.bot.Looting.start(bot, pos, () -> {
					done.add(pos);
					botLoots++;
				});
				return;
			}
		}
	}

	/** Chests emptied by bots this match (self-test, inspect). */
	public int botLoots() {
		return botLoots;
	}

	/** Bots bridge anywhere a player may build. */
	@Override
	public boolean botMayBuild(BlockPos pos) {
		return phase == Phase.RUNNING && arena.bounds().contains(Vec3.atCenterOf(pos)) && arena.inPlayArea(pos);
	}

	@Override
	public boolean allowsDrops(ServerPlayer p) {
		return true;
	}

	/** Out, and everything you carried falls where you died. */
	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		victim.getInventory().dropAll();
		eliminate(victim);
	}

	@Override
	protected void onTimeUp() {
		UUID best = null;
		int most = -1;
		boolean tie = false;
		for (UUID id : alive) {
			int k = kills(id);
			if (k > most) {
				most = k;
				best = id;
				tie = false;
			} else if (k == most) {
				tie = true;
			}
		}
		finish(best == null || tie ? List.of() : List.of(best));
	}
}
